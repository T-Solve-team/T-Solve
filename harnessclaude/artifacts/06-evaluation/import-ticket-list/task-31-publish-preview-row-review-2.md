---
module: import-ticket-list
feature: publish-preview-row
task: 31
verdict: pass
attempt: 2
date: 2026-11-19
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 31: Publish one preview row (STORY-017, KAN-373) — attempt 2

## Summary
I re-read the full code (`58e57127..80cece71`) and the fix (`2f785147..4e2b56cd`), then ran the unit tests, Spotless
and the import ITs myself against the spec (STORY-017, D21).

The fix changes `ImportConfirmServiceImpl.publishRow` (`ImportConfirmServiceImpl.java:183-200`). A `CodedException`
(404, MSG06, MSG33 409, all thrown inside the lock before anything is saved) passes through unchanged. A
`DataIntegrityViolationException` with SQLState `23505` becomes `ImportFailedException(409)` (MSG33). Any other
`DataIntegrityViolationException` or `RuntimeException` becomes `ImportFailedException(500)` (MSG33). None of these
paths calls the recorder, so no Failed batch is recorded. This matches D21 and the user's decision ("a save failure
during publish answers MSG33 500 and records no Failed batch"). The `catch` wraps `transaction.execute`, so it also
catches a commit-time failure. `ProjectNotActiveException` and `ProjectAccessDeniedException` are thrown before the
`try`, and both are `CodedException`s.

The new tests cover what attempt 1 found missing. `ImportConfirmIT.publishedRowLeavesThePreviewAndTheRestStillConfirms_AC_017_1_AC_017_2_AC_017_3`
runs on PostgreSQL and checks:
- one Ticket and one Completed batch (1/0/0);
- an APPROVED decision;
- the `AFTER_COMMIT` listener sees the Ticket from another connection (`ids=1 visible=1`);
- the published row is deleted and row 2 stays;
- the `validCount` drops to 1;
- a later Confirm imports only the other row, with its Solution, in a second batch.

`publishWaitsForARunningConfirmThenFindsThePreviewGone_AC_017_7` holds the preview lock from another connection and
shows that publish waits (`awaitWaitingLock`), then gets 404 and writes nothing. The two new unit tests cover the
unique-violation branch (409 MSG33, rollback, no `recordFailed`) and a non-unique save failure (500 MSG33, rollback,
no `recordFailed`). `ImportControllerTest` pins the path and the 200 body, and the 403 for a non-PM. The controller
summary now names Jira.

All tests pass. No CRITICAL or Major issue is left, so the verdict is **pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-017.1 to AC-017.7 are met. On a real database: the Ticket, decision, Completed batch, deleted row, kept other rows, counts and after-commit event (IT). |
| Completeness | pass | Endpoint, contract, generated client, CSV and Jira sources, MSG40 before 403, and no batch on any rejection. Save failures now answer MSG33 (500), and a taken ID answers MSG33 (409). |
| Correctness | pass | 417/417 unit tests (`vn.tsolve.ticket.**`), Spotless clean, 43/43 import ITs (ImportConfirmIT 14/14). Every `publishRow` branch has a test. |
| Integration | pass | The contract row matches `ImportResultResponse` (created 1, skipped 0, MSG32). `generate:api` leaves no diff. The `TicketsPublishedEvent` type is unchanged. The publish takes the same preview lock as Confirm and Finish, as the lock IT shows. |

## Traceability
```
  ✓ AC-017.1  ImportConfirmServiceImplTest.java, ImportControllerTest.java, ImportConfirmIT.java
  ✓ AC-017.2  ImportConfirmServiceImplTest.java, ImportConfirmIT.java
  ✓ AC-017.3  ImportConfirmServiceImplTest.java, ImportConfirmIT.java
  ✓ AC-017.4  ImportConfirmServiceImplTest.java
  ✓ AC-017.5  ImportConfirmServiceImplTest.java
  ✓ AC-017.6  ImportConfirmServiceImplTest.java, ImportControllerTest.java
  ✓ AC-017.7  ImportConfirmServiceImplTest.java, ImportConfirmIT.java
  ✓ AC-017.8  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ✓ AC-017.9  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ✓ AC-017.10  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ! ids in tests but not in the spec: AC-007.4
trace import-ticket-list (STORY-017): 10/10 acceptance criteria have a test
```
(Backend paths are under `backend/src/test/java/vn/tsolve/ticket/`.) The stray `AC-007.4` comes from older tests.

## Attempt 1 findings

| # | Attempt 1 finding | Status |
|---|-------------------|--------|
| 1 | Major: no IT for (a) the DB writes, (b) the after-commit event, (c) the lock against Confirm | **Resolved.** (a) and (b) are covered by `ImportConfirmIT:273`. (c) is covered by `ImportConfirmIT:304`. The "no event when rejected" half of (b) has no IT, but `publishEvent` is the last write in `publishOne`, after every check, and a rejection rolls back, so an `AFTER_COMMIT` listener cannot fire. Accepted. |
| 2 | Major: unique-violation branch untested | **Resolved.** Covered by `ImportConfirmServiceImplTest:647`. The optional IT that inserts the Ticket after the preview was made was not added (see residual note 2). |
| 3 | Minor: other save failures gave a generic 500 | **Resolved**, as the user decided: MSG33 500 with no Failed batch (`ImportConfirmServiceImpl.java:189-200`). Covered by `ImportConfirmServiceImplTest:663`. |
| 4 | Minor: no `@WebMvcTest` case for the endpoint | **Resolved.** `ImportControllerTest` has a 200 case (PM) and a 403 case (STAFF). |
| 5 | Minor: the controller summary left out Jira | **Resolved.** `ImportController.java:154-155`. |

## Issues
None that block the verdict. Residual notes (Low, optional):
1. **Low — the 500 path logs only the exception class.** `ImportConfirmServiceImpl.java:193-199` logs
   `e.getClass().getSimpleName()` without the stack trace or message, so an unexpected failure during publish is
   hard to diagnose from the logs. Confirm's `fail(...)` (`:300-317`) does the same, so this is consistent with the
   module. *Suggested fix:* pass `e` as the last argument of `log.warn` in both places. This needs a check that no
   personal data reaches the log message (AGENTS.md logging rule).
2. **Low — the commit-time `23505` is shown only with a mock.** The unit test throws from `tickets.saveAll`. On
   PostgreSQL the insert can fire at commit, inside `transaction.execute`. `JpaTransactionManager` translates that
   into a `DataIntegrityViolationException`, and the `catch` wraps the commit. If the translation ever failed, the
   request would still end as MSG33 (500), not 409, with no batch. The risk is small. *Suggested fix (optional):* an
   `ImportConfirmIT` case that inserts a Ticket with the row's ID between the preview and the publish and expects
   409 MSG33 with the row kept. That case passes the pre-check, so it needs a stub or a trigger. Not required.

## Assumptions made
- **Order of MSG06 against MSG40/403 is unspecified** (carried from attempt 1). The code checks the Project (MSG40,
  then 403) before the import type and row status (MSG06). I accepted this.
- **A save failure during publish answers MSG33 500 with no Failed batch.** The user decided this. I evaluated
  against it and did not reopen it.
- **Attempt 1 issue 1(b), "nothing when rejected", is accepted without an IT.** This is my judgment from the code
  order (`publishEvent` comes after every check). The user may still ask for the IT.

## Test Report
See `task-31-publish-preview-row-test-report-2.md`.
- `vn.tsolve.ticket.**` unit tests: 417 run, 0 failed (`ImportConfirmServiceImplTest` 35/35, `ImportControllerTest` 24/24).
- `spotless:check`: pass.
- `Import*IT,JiraFinishIT`: 43 run, 0 failed (`ImportConfirmIT` 14/14).
- `npm run generate:api`: no diff.
