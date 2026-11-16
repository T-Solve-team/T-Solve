---
module: ticket-repository
feature: drop-legacy-solution-tables
task: 30
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 30: Drop the unused Solution tables and the `knowledge` package (KAN-137, STORY-010)

## Summary
This review covers the code merged in PR #251 (squash commit `f3f782f3`), checked at HEAD `7554caca`.

Changeset 039 (`backend/src/main/resources/db/changelog/changes/039-drop-legacy-solution-tables.sql`):
- It is a new file, included after 038. No changeset that was already merged was edited, and no later commit touches 039.
- Line 7 drops `solution_source`, `solution_approval`, `reuse_record`, `solution` and `resolved_ticket_snapshot` in one `DROP TABLE` without `CASCADE`. If any view or FK outside the five tables still pointed at them, the changeset would fail.
- The rollback (lines 8–14) recreates the five tables empty, on `legacy_workspace`. It exists only for the BUG-243 rollback test.
- `legacy_workspace` and its rows stay.

Code and docs:
- `vn.tsolve.knowledge` is gone: 6 files, including `SolutionController` at `/api/solutions`.
- No JPA entity maps a dropped table. `ddl-auto: validate` would fail otherwise, and CI is green.
- No main code, frontend code, SQL or YAML names a dropped table.
- `README.md` lost its 5 lines about the package and the endpoint.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-010.7: after migration none of the five tables exists and no `@RestController` maps `/api/solutions` |
| Completeness | pass | 1/1 Task-30 AC traced; D42 (`reuse_record`) included; README updated |
| Correctness | pass | PR #251 CI green, including `TicketDecisionMigrationIT` and `WorkspaceInfoRepositoryIT`; not re-run here (a full verify is running in this tree) |
| Integration | pass | `WorkspaceInfoRepositoryIT` (KAN-119) now checks that the legacy rows are kept and the tables are gone; the BUG-243 rollback still checks the restored FKs |

## Traceability
```
  ✓ AC-010.7  backend/src/test/java/vn/tsolve/ticket/repository/TicketDecisionMigrationIT.java
trace ticket-repository (STORY-010): 7/7 acceptance criteria have a test
```

## Issues
No critical issues. All of the following are informational, except issue 1, which is an open judgment call:

1. PR #251 body, the "Record the row counts of the five dropped tables in each deployed database" checkbox, is still unchecked after the merge. Task 30's session log lists this as a before-merge item of the risk review.
   - `SolutionController` and `SolutionService` were live at `/api/solutions` until this change. A deployed database could therefore hold `solution` rows that 039 deletes for good.
   - D38 and D42 accept the drop, but nobody recorded whether any data was lost.
   - Fix: before 039 runs on any deployed Workspace database, record its counts, or confirm that the backup policy covers them.
2. `backend/src/test/java/vn/tsolve/ticket/repository/TicketDecisionMigrationIT.java:150-158`: the endpoint check tests the class-level and the method-level paths separately.
   - A controller with `@RequestMapping("/api")` on the class and `@GetMapping("/solutions")` on a method would pass the check.
   - Fix: combine the two paths, or assert on `RequestMappingHandlerMapping` in a context test.
   - Low risk today, because nothing maps such paths.
3. `backend/AGENTS.md:53,84,90` still names `knowledge` as a module package, and gives `vn.tsolve.knowledge.service` as the naming example.
   - Fix: change the list and the example to an existing package, so no one rebuilds on the deleted package.
4. `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java:283-325` (edge 141) still uses `/api/solutions` as the sample Workspace API path.
   - The 403 comes from the session filter, so the test stays valid.
   - The path no longer exists, though. A path that is still live (for example `/api/v1/tickets`) would keep the test meaningful.

## Assumptions made
- I treat issue 1 as a process risk that does not block this task. AC-010.7 is met, and the spec (D38, D42) decides that the tables go. The user should decide whether to record the counts, or confirm the backups, before the next deploy.
- I did not run the integration tests here, by the dispatcher's instruction. This PASS relies on the green CI of PR #251 and on the full verify that is running in this tree.

## Test Report
See `task-30-drop-legacy-solution-tables-test-report.md`. Trace 7/7. AC-010.7 is covered only by an IT, which CI ran green.
