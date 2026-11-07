---
module: search
feature: project-uuid-key
task: 20
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: search — Task 20: One UUID key for the Project (KAN-291, STORY-005, AC-005.10)

## Summary

PASS_WITH_NOTES, on one condition: the separate `mvn clean verify` run must pass. The reviewer was told not to run
Maven or Docker, so no test result here comes from the reviewer (see Test Report).

The change is uncommitted in the working tree on `refactor/KAN-291-search-project-uuid`. HEAD is `46b861f`. Review
diff: `git diff HEAD -- backend` plus the untracked
`backend/src/main/resources/db/changelog/changes/017-search-project-uuid-key.sql`. The branch is one commit behind
`origin/main` (`558ded7`, KAN-77). For that reason, `git diff origin/main` also shows `558ded7` undone in
`TicketListServiceImpl` and `TicketListControllerTest`. That reversal is not part of this change (Issue 1).

By reading only, changeset 017 meets AC-005.10 clause by clause. The JPA mappings match the new column types.
`frontend/` has no diff, so the API contract is unchanged. Both `_AC_005_10` tests assert the criterion. The notes
below do not block the pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | 017 rewrites hex-form ids (`lpad(to_hex(p.project_id),32,'0')::uuid`) to `p.public_id` in `ticket`, `import_batch` and `import_preview`. It rebuilds `suggestion_result.project_id` and `department.default_project_id` as UUIDs that hold the Project's public UUID, then drops the old BIGINT PK column. That drop also removes the owned `project_project_id_seq` and `project_pkey`. It drops `uq_project_public_id`, renames `public_id` to `project_id` (`NOT NULL DEFAULT gen_random_uuid()` stays) and adds `project_pkey`. It re-adds both FKs under their original names. The Department key is untouched. The rollback is present, marked "Development databases only", and restores the BIGINT key type and `public_id`. |
| Completeness | pass | Every changeset that references `project` was checked: 002 (`suggestion_result` FK, `department` FK, `idx_suggestion_project`), 003/010 (`ticket`, `import_batch`, `import_preview`, which have no FK) and 008 (`uq_project_public_id`). No other table refers to `project`. `idx_suggestion_project` is recreated after the column swap. `idx_ticket_project_status` and `idx_import_batch_project` keep working after the value UPDATE. Code: `Project`, `ProjectRepository`, `ProjectService(Impl)`, `SuggestionResult`, `SuggestionResultRecord`, `HistoryFilter`, `ManagedProject`, `SearchTicket` (the two-form join is gone), `SolutionRetrievalRepository` (the two-form join is gone), `VisibleScope.Scope`, `ScopeTicketRepository` and `TicketSummaryMapper`. No `new UUID(0, …)` and no `to_hex` remains in `src/main/java`. |
| Correctness | pass (conditional) | The `ticket` UPDATE turns off only `trg_ticket_updated_at`. The Review trigger `ticket_record_review_queue_entry` is `BEFORE INSERT OR UPDATE OF status`, so an update of `project_id` does not fire it. The `updated_at` test proves that time is kept. Postgres runs the whole changeset in one transaction, so a failure leaves nothing half-applied. `SET NOT NULL` is safe because the old FK guarantees every row has a match. A random v4 `public_id` cannot equal a hex form (version nibble 0), so rows that already hold a public UUID are left as they are. The tests were not run by the reviewer. |
| Integration | pass | `frontend/openapi/tsolve-api.yaml` and `frontend/` show no diff. `TicketProjectRef.projectId` and `SuggestionProject.projectId` still carry the same UUID value as before (the former `public_id`). Review (`ReviewWorkspaceAccess`, `TicketDecisionServiceImpl`) and ticket (`ProjectAccess`) already used `UUID` and now compare against one id form. |

## Traceability

`bun harnessclaude/hooks/sdd.ts trace search STORY-005` reports 10/10 acceptance criteria with a test:

```
  ✓ AC-005.1  backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java
  ✓ AC-005.2  backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java
  ✓ AC-005.3  backend/src/test/java/vn/tsolve/search/SearchChangesetRollbackIT.java
  ✓ AC-005.4 … AC-005.9  (unchanged)
  ✓ AC-005.10  backend/src/test/java/vn/tsolve/search/SearchSchemaIT.java
trace search (STORY-005): 10/10 acceptance criteria have a test
```

The whole module is at 75/90. The criteria without a test are AC-007.x and AC-009.x, from other tasks and not
part of this one.

What the tests assert:
- `projectKeyIsTheFormerPublicId_AC_005_10`:
  - `project.project_id` values equal the earlier `public_id` values;
  - the column type is `uuid`;
  - `public_id` is gone;
  - the PK is `PRIMARY KEY (project_id)`;
  - `suggestion_result.project_id` and `department.default_project_id` are `uuid`, each with exactly one FK to
    `project(project_id)`;
  - the sequence is dropped.
- `rewritesHexProjectIds_AC_005_10`:
  - the sequence starts at 10, so the hex value `a` is covered;
  - rows in hex form and rows already in public form, in `ticket`, `import_batch`, `import_preview` and
    `suggestion_result`, all end up holding the Project UUID;
  - the department default is mapped, and a NULL stays NULL;
  - `ticket.updated_at` is kept;
  - `rollback(1)` restores a `bigint` key with correct `suggestion_result` and `department` joins, and the update
    after it runs again.
- `SearchChangesetRollbackIT` (AC-005.3, `search-only.xml` now includes 017): a rollback by count, then an update,
  passes once the `project` column has been renamed.

## Issues

None blocking. Notes, by severity:

1. **Medium (process): the branch is behind `origin/main`.** HEAD `46b861f` lacks `558ded7`
   (`fix(search): accept repeated sort parameters on the ticket list (KAN-77)`). That commit also changes
   `backend/src/test/java/vn/tsolve/search/list/service/impl/TicketListServiceImplTest.java`, which this change edits
   too (around line 42 and the imports). Fix: before committing, stash, update to `origin/main` and re-apply. Then run
   `mvn verify` again on the result.
2. **Low: `.env.example:41` gives a stale example.** It reads `pm@company.vn:00000000-0000-0000-0000-000000000001`, a
   hex-form id. After 017 no Project has an id like that. `ticket.project_id` has no FK, so an import under such a
   stub id would write tickets that no Project joins. Those tickets would not appear in the Ticket List, Search or
   suggestions. Fix (quick change): say that the value is the Project's `project_id`, read with
   `SELECT project_id FROM project`.
3. **Low: two forward statements in `017-search-project-uuid-key.sql` depend on constraint names and are not
   needed.** These are the lines `ALTER TABLE suggestion_result DROP CONSTRAINT suggestion_result_project_id_fkey;`
   and `ALTER TABLE department DROP CONSTRAINT fk_department_default_project;`. The `DROP COLUMN` lines after them
   already drop those FKs. Both names are correct for databases built by this changelog: the first is Postgres's
   default name for 002's inline `REFERENCES`, the second is named in 002. The 002 comment says a team tenant
   changeset will replace it. If that changeset names the constraints differently, 017 fails on these two lines.
   Fix (optional): remove the two forward `DROP CONSTRAINT` lines. The rollback ones must stay, because `project_pkey`
   is dropped first there.
4. **Low (test gap): no test checks that `idx_suggestion_project` is recreated.** It is recreated in both directions
   (017 lines 20 and rollback line 13). Fix: assert `to_regclass('idx_suggestion_project') IS NOT NULL` in
   `projectKeyIsTheFormerPublicId_AC_005_10`.
5. **Info: the rollback gives new BIGINT values.** `BIGSERIAL` numbers the rows in table order and does not restore
   the original keys. `ticket`, `import_batch` and `import_preview` keep the public UUIDs, not the hex form. HEAD's
   code joins on `public_id`, so both still work after a rollback. This fits "restores BIGINT keys … development
   databases only".

Over-engineering and unrelated edits: none found. `ManagedProject` loses its duplicate `publicId` field. Every test
edit replaces a BIGINT or hex Project id with a UUID, which Task 20 asks for. No new abstraction is added.

## Assumptions made

- "Restores BIGINT keys" (AC-005.10) is read as the key *type*, not the original numbers, because the criterion
  limits the rollback to development databases.
- "The foreign keys to `project` kept" is read as only the FKs that already existed (`suggestion_result`,
  `department`). The spec does not ask for new FKs on `ticket`, `import_batch` or `import_preview.project_id`.
  Whether those columns should get one is a judgment call for the user (see Issue 2 for the risk).
- The 32 IT errors of #140 that this task fixes have no row in `docs/bug-log.md`. They are treated as a spec change
  (the spec change log, 07/11), not as a bug. The user may want a bug row.
- The verdict counts as pass only if the separate `mvn clean verify` passes. If it fails, read the verdict as fail.

## Test Report

See `task-20-project-uuid-key-test-report.md`. The reviewer did not run the tests, because the dispatcher forbade
Maven and Docker while a `mvn clean verify` runs separately. Traceability was run.
