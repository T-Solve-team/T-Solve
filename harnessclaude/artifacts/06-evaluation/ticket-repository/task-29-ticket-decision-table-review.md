---
module: ticket-repository
feature: ticket-decision-table
task: 29
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 29: One `ticket_decision` table (KAN-137, STORY-010)

## Summary
This review covers the code merged in PR #251 (squash commit `f3f782f3`), checked at HEAD `7554caca`.

Liquibase changes:
- The change adds only new files: `038-ticket-decision.sql` and two `<include>` lines in `db.changelog-master.xml`.
- No changeset that was already merged was edited (`git diff --name-status f3f782f3^ f3f782f3`: `A 038`, `A 039`, `M master`).
- No later commit touches 038.

Changeset 038 (`backend/src/main/resources/db/changelog/changes/038-ticket-decision.sql`):
- Lines 7–14 rename `review_decision` in place: the table, three columns, the PK, the CHECK and both indexes. Every review row keeps its id, its `reviewer_id` (formerly `project_manager_id`), its decision, its feedback and its time. The FKs to `ticket` and `app_user` and the `gen_random_uuid()` default stay too.
- Line 15 makes `reviewer_id` nullable. Lines 17–19 add `reviewer VARCHAR(150)` and `ck_ticket_decision_reviewer`.
- Lines 22–24 copy every `ticket_import_decision` row with its `ticket_id`, `reviewer`, `reason→feedback` and `decided_at`. `APPROVE` becomes `APPROVED`. Any other value is left for the CHECK to reject, so the changeset fails instead of relabelling the row.
- Line 25 drops the old table.
- On PostgreSQL the whole changeset runs in one transaction, so a failure leaves the database unchanged.

The rollback (lines 26–40) is there only for the BUG-243 test. It reverses the rename and moves the import rows back.

Code:
- `TicketDecision` replaces the three old entities. The review constructor sets `reviewerId`. `approvedByImport` sets `reviewer` and `APPROVED`.
- Every writer was switched: `ImportConfirmServiceImpl.java:282`, `ManualTicketServiceImpl.java:173`, and `TicketDecisionServiceImpl.java:88,128,159`.
- Reads (AC-010.5) now require `t.source = 'SOLUTION_FORM'`:
  - Review History, both queries and the count: `TicketDecisionRepository.java:21`
  - `findWriterNotification`: `TicketDecisionRepository.java:89`
  - The dashboard `decisions` query: `DashboardRepository.java:133`
  - `findJiraFeedback` and `findJiraRejection` (`TicketRepository.java:175,190`) already had this filter.
- A grep of `backend/src`, `frontend/src`, `README.md` and `docs` finds no other reader of the decision tables and no reference left to `review_decision`, `ticket_import_decision` or the old classes. The only exceptions are the pre-038 seed statements in the migration tests, which are intended.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-010.1/.2/.6 in changeset 038; AC-010.3 through `approvedByImport` (CSV, Jira, manual); AC-010.4 through the review constructor (decide, send back, unpublish/republish); AC-010.5 has the source predicate on every review read |
| Completeness | pass | 6/6 Task-29 ACs traced (AC-010.1–.6); the data migration keeps every existing row; no merged changeset edited |
| Correctness | pass | 118 targeted unit tests green; PR #251 CI green (unit, it-keycloak-a/b, it-other, which run the new `TicketDecisionMigrationIT` and the updated ITs); ITs not re-run here (a full verify is running in this tree) |
| Integration | pass | `ReviewFeedbackDecidedEvent` still carries the same ids (renamed column, same values); `ddl-auto: validate` matches the entity (CI green) |

## Traceability
```
  ✓ AC-010.1  backend/src/test/java/vn/tsolve/ticket/repository/TicketDecisionMigrationIT.java
  ✓ AC-010.2  backend/src/test/java/vn/tsolve/ticket/repository/TicketDecisionMigrationIT.java
  ✓ AC-010.3  backend/src/test/java/vn/tsolve/ticket/service/impl/JiraFinishTest.java, backend/src/test/java/vn/tsolve/ticket/controller/ManualTicketControllerIT.java
  ✓ AC-010.4  backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-010.5  backend/src/test/java/vn/tsolve/analytics/DashboardOutcomesIT.java
  ✓ AC-010.6  backend/src/test/java/vn/tsolve/ticket/repository/TicketDecisionMigrationIT.java
  ✓ AC-010.7  backend/src/test/java/vn/tsolve/ticket/repository/TicketDecisionMigrationIT.java
trace ticket-repository (STORY-010): 7/7 acceptance criteria have a test
```

The trace lists only one file per criterion. More tests name these criteria:
- AC-010.3: `ImportConfirmIT.java:248` (CSV)
- AC-010.4: `TicketDecisionIT.java:116` and `SendBackApiIT.java:161`
- AC-010.5: `ReviewHistoryApiIT.java:312` and `ReviewNotificationIT.java:291`

## Issues
No critical or blocking issues. All of the following are informational:

1. `backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationIT.java:291`: `emailsNoWriterForTheApproveDecisionOfAnImportedTicket_AC_010_5` cannot catch the bug it is named for.
   - It seeds an `APPROVED` decision. `ReviewNotificationServiceImpl.java:43` maps every value except REJECTED and CHANGES_REQUESTED to `Optional.empty()`, so the outbox stays empty even without the `t.source = 'SOLUTION_FORM'` join at `TicketDecisionRepository.java:89`.
   - Fix: seed a `REJECTED` (or `CHANGES_REQUESTED`) decision with a `reviewer` on a MANUAL/CSV ticket. That is the only shape the source filter guards.
   - The dashboard (`DashboardOutcomesIT.java:112`) and Review History (`ReviewHistoryApiIT.java:312`, which sets `reviewer_id` so the inner join to `app_user` does not hide the row) are real regression tests.
2. `backend/src/main/resources/db/changelog/changes/038-ticket-decision.sql:26`: the rollback does not fully restore the old table.
   - It recreates `ticket_import_decision` without `ALTER SEQUENCE … INCREMENT BY 50` (changeset 003).
   - The moved rows get new BIGSERIAL ids.
   - This affects only the test-only rollback. A deployed database is fixed forward (AGENTS.md §7), so no fix is needed unless someone ever rolls back for real.
3. `backend/src/test/java/vn/tsolve/ticket/repository/TicketDecisionMigrationIT.java:98`: AC-010.2 is tested with one review row and one import row.
   - The test does not check a NULL `reason`/feedback or several rows per ticket.
   - The SQL is a plain `INSERT … SELECT`, so the risk is low.
4. `TicketDecisionMigrationIT.java:123`: the AC-010.6 test leaves rows behind if an assertion fails halfway, because it cleans up with a trailing delete (line 138), not in a `finally`.
   - The AC-010.2 test counts every row (`isEqualTo(2)`, line 118), so a partial failure in one test could also fail the other.
   - Fix: move the delete into `finally`.

## Assumptions made
- I treat issue 1 as informational, not as a fail. AC-010.5 has real regression tests on the dashboard and Review History. The writer-email path cannot be reached by an import decision today, because import decisions are always APPROVED and are never published in `ReviewFeedbackDecidedEvent`. The user may want issue 1 raised to a blocking issue.
- I did not run the integration tests here, by the dispatcher's instruction. This PASS relies on the green CI of PR #251 and on the full verify that is running in this tree. In particular, `TicketDecisionMigrationIT`, `ReviewStorageIT` and `WorkspaceInfoRepositoryIT` (the BUG-243 rollback) are DB-only and were not exercised here.
- `spotless:check` was not run. The ratchet needs `origin/main`, which my isolated copy does not have, and the running verify covers it.

## Test Report
See `task-29-ticket-decision-table-test-report.md`. 7 unit suites, 118 tests, 0 failures. Trace: 7/7.
