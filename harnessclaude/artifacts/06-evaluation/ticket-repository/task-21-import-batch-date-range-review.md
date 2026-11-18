---
module: ticket-repository
feature: import-batch-date-range
task: 21
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 21: Date range on Import Batches (KAN-134, STORY-007)

## Summary
This review covers the code at HEAD `7554caca`, worktree `KAN-134-pr257`. The feature was merged in PR #235 (`e98a9006`), and no later commit touches its files.

- Changeset `032-ticket-import-batch-date-range.sql` adds `date_from` and `date_to` (DATE, nullable) and has a rollback. `db.changelog-master.xml:56` includes it.
- `ImportBatch` maps both columns as `updatable = false`. `completed(…)` and `failed(…)` take them as parameters.
- Every place that creates an `ImportBatch` was checked (grep `ImportBatch.completed|failed`):
  - Jira and CSV Finish, Completed: `ImportConfirmServiceImpl.java:268-276` passes `preview.getResolvedFrom()` / `getResolvedTo()`. These are null on a CSV preview.
  - Finish, Failed (MSG33): `ImportBatchRecorder.recordFailed` (`ImportBatchRecorder.java:30-41`) passes the preview's range, in its own `REQUIRES_NEW` transaction.
  - Failed Jira pull (MSG50): `JiraImportServiceImpl.java:147` calls `recordJiraFailed(projectId, user, from, to, …)` with the request's range.
  - Manual: `ManualTicketServiceImpl.java:147` passes null, null.
- There are no other writers. No backfill was done, as D25 asks.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-007.8: the Jira Finish (Completed and Failed) and the failed pull store the range; CSV and Manual store none |
| Completeness | pass | The task's only criterion, AC-007.8, has a test |
| Correctness | pass | 67 targeted unit tests pass (ImportHistoryServiceImplTest, JiraImportServiceImplTest, ImportConfirmServiceImplTest, ManualTicketServiceImplTest); `spotless:check` is clean. `ImportHistoryDateRangeIT` was not run here (Testcontainers). |
| Integration | pass | Task 22 reads the columns (`ImportBatchRepository.java:34`) |

## Traceability
```
  ✓ AC-007.8  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportHistoryServiceImplTest.java, backend/src/test/java/vn/tsolve/ticket/service/impl/ImportHistoryDateRangeIT.java
trace ticket-repository (STORY-007): 10/10 acceptance criteria have a test
```
`ImportHistoryDateRangeIT` covers all six cases on a real DB:
- Jira Finish Completed
- Jira Finish Failed after rollback
- failed pull
- CSV Completed
- CSV Failed
- Manual

## Issues
No issues.

## Assumptions made
- I did not run `ImportHistoryDateRangeIT` here, because it needs Testcontainers and the dispatcher runs the full verify. This PASS assumes it is green.

## Test Report
See `task-21-import-batch-date-range-test-report.md`.
