---
module: import-ticket-list
feature: publish-preview-row
task: 31
verdict: fail
attempt: 1
date: 2026-11-19
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 31: Publish one preview row (STORY-017, KAN-373)

## Summary
Code commit `80cece71` (range `58e57127..80cece71`) adds `POST /imports/previews/{previewId}/rows/{rowNo}/publish`
(`ImportController.publishImportPreviewRow` -> `ImportConfirmServiceImpl.publishRow`) and the contract in
`frontend/openapi/tsolve-api.yaml` with its generated client.

The production code is sound. `publishOne` takes the same `PESSIMISTIC_WRITE` preview lock as Confirm/Finish,
Fill solution and Remove. A concurrent Confirm and publish therefore serialise: the second one either finds the
preview gone (404) or finds the row deleted (Confirm does not see it). The checks and writes happen under the lock in
this order: import-type/Valid check (MSG06), taken Ticket ID (MSG33 409, no batch), one Completed batch (1/0/0), the
Ticket through the same `toTicket` as Confirm (CSV or Jira source, Jira `sourceUrl`), the approve decision,
`previewRows.delete(row)`, the `TicketsPublishedEvent` and the audit record. The event is published inside the
transaction for the `AFTER_COMMIT` listener (`TicketsPublishedListener`). The Summary counts are computed from the rows
(`countByIdPreviewIdAndStatus`), so the deleted row leaves them correct. The preview and a Draft are not touched.
MANUAL previews give 404. MSG40 is checked before 403, as in Confirm. All unit tests, the import ITs and Spotless pass.

The verdict is **fail** because of the tests, not the code. Three parts of this feature can only be shown by a real
database: the after-commit event (AC-017.3), the unique-violation path (AC-017.5, the second half), and the lock
against a concurrent Confirm. `backend/AGENTS.md` §8 says such behaviour "is tested in a `*IT` class", and none of
them has an IT. `ImportConfirmIT` already has the after-commit listener and the lock-wait helpers for Confirm. The
`DataIntegrityViolationException` branch of `publishRow` has no test at all, not even a unit test.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-017.1–AC-017.7 are met by the code (read line by line). The deleted row leaves the counts right, and Confirm/Finish no longer see it. |
| Completeness | pass | The endpoint, contract row, generated client, Jira and CSV sources, MSG40-before-403 and no-batch rejections are all present. |
| Correctness | fail | Tests pass, but the after-commit event, the unique-violation branch and the lock against Confirm are not tested against a real DB (AGENTS.md §8). The catch branch at `ImportConfirmServiceImpl.java:184-190` has no test of any kind. |
| Integration | pass | The contract row matches (`ImportResultResponse`, created 1, skipped 0, MSG32; 403/404/409). `npm run generate:api` leaves no diff (see test report). The event type is unchanged for the search listener. |

## Traceability
```
  ✓ AC-017.1  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImplTest.java
  ✓ AC-017.2  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImplTest.java
  ✓ AC-017.3  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImplTest.java
  ✓ AC-017.4  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImplTest.java
  ✓ AC-017.5  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImplTest.java
  ✓ AC-017.6  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImplTest.java
  ✓ AC-017.7  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImplTest.java
  ✓ AC-017.8  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ✓ AC-017.9  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ✓ AC-017.10  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ! ids in tests but not in the spec: AC-007.4
trace import-ticket-list (STORY-017): 10/10 acceptance criteria have a test
```
Every AC of this task has a test name, but AC-017.3 is only shown with a mocked publisher. `publishesTheTicketsPublishedEvent_AC_017_3` (`ImportConfirmServiceImplTest.java:562`) checks that `publishEvent` was called. It does not check that a listener runs only after the commit, or not at all after a rollback. The stray `AC-007.4` comes from older tests and is not part of this task.

## Issues
1. **Major — no IT for the behaviour only a real database shows** (`backend/AGENTS.md` §8).
   `backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmIT.java` was not changed. Missing:
   (a) AC-017.1/AC-017.2 against PostgreSQL: the `import_preview_row` row is really deleted, the other rows and
   their Solutions stay, `GET /previews/{id}` counts drop by one, and the Ticket, `ticket_decision` and Completed
   `import_batch` (1/0/0) rows exist;
   (b) AC-017.3: the `PublishedEvents` AFTER_COMMIT listener already in `ImportConfirmIT` receives the new
   Ticket's UUID once, and receives nothing when publish is rejected;
   (c) the lock: a publish waits for a running Confirm and then gets 404, or a Confirm after a publish imports the
   other rows only (reuse `awaitWaitingLock`).
   *Fix:* add two or three tests to `ImportConfirmIT` (no new container or context) named with AC-017.1/.2/.3.
2. **Major — the unique-violation branch of `publishRow` is untested.**
   `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImpl.java:184-190`. This covers a Ticket
   with the same ID committed between the `findExistingTicketIds` check and the insert. It should give 409 MSG33,
   keep the row and record no batch (AC-017.5, D21), but no test exercises it. Nothing proves that
   `TransactionTemplate` turns the commit-time `23505` into a `DataIntegrityViolationException`.
   *Fix:* a unit test where `tickets.saveAll` throws
   `new DataIntegrityViolationException("x", new SQLException("dup", "23505"))`. Assert MSG33/409,
   `recorder.recordFailed` never called and `rows.delete` rolled back. Better, also add an IT that inserts the
   Ticket after the preview is made (as done for AC-004.7).
3. **Minor — any other save failure gives a generic 500, not MSG33.**
   `ImportConfirmServiceImpl.java:189` (`throw e;`) and every other `RuntimeException` from `publishOne` reach
   `GlobalExceptionHandler.handleUnexpected`. The answer is 500 `UNEXPECTED_ERROR`, but the module's rejection line
   says "MSG33 … because saving failed → 500". Confirm maps these to `ImportFailedException(INTERNAL_SERVER_ERROR)`.
   *Fix:* map the non-unique `DataIntegrityViolationException` (and other `RuntimeException` outside the
   NotFound/BusinessRule/ImportFailed types) to `new ImportFailedException(HttpStatus.INTERNAL_SERVER_ERROR)`,
   without a Failed batch (D21). Alternatively, confirm with the user that a generic 500 is acceptable (see
   Assumptions).
4. **Minor — no `@WebMvcTest` case for the new endpoint.**
   `backend/src/test/java/vn/tsolve/ticket/controller/ImportControllerTest.java` was not changed. The class-level
   `@PreAuthorize` covers the role, but no test pins the path `/rows/{rowNo}/publish` or the mapping of
   `BusinessRuleException` MSG06 -> 409 and `ProjectAccessDeniedException` -> 403 for it. AGENTS.md §8 asks for only
   "one or two cases per resource", so this is optional.
   *Fix:* one case: a non-PM gets 403, and a PM gets 200 with `created: 1`.
5. **Minor — the controller summary leaves out Jira.**
   `backend/src/main/java/vn/tsolve/ticket/controller/ImportController.java:154` says "a preview of a file without
   Solutions". The contract summary and AC-017.1 also cover a Jira preview.
   *Fix:* use the contract's summary wording.

## Assumptions made
- **Spec silent on a non-unique save failure during publish.** AC-017 lists no such case. I read the module-wide
  rejection line ("MSG33 … because saving failed → 500") as applying to publish too, and logged issue 3 as Minor.
  **Open judgment call for the user:** is a generic 500 (`UNEXPECTED_ERROR`) acceptable here?
- **Order of MSG06 against MSG40/403 is unspecified.** The code checks the Project (MSG40, then 403) before the
  import type/row status (MSG06). For a With-Solutions preview on an inactive Project it therefore answers MSG40. I
  accepted this.
- **Missing ITs count as a fail.** The ITs are a written project rule (`backend/AGENTS.md` §8) and were explicitly in
  the review brief. I treated their absence for an after-commit event, a constraint and a lock as Major and
  verdict-failing. All ACs do have tests, and the code reads correct. **Open judgment call:** the user may accept the
  unit-level evidence and pass the task.

## Test Report
See `task-31-publish-preview-row-test-report.md`. Unit tests in `vn.tsolve.ticket.**`: 413 run, 0 failed
(`ImportConfirmServiceImplTest` 33/33, 10 of them new). `spotless:check`: pass. ITs `Import*IT,JiraFinishIT`: 41 run,
0 failed (no IT covers publish). `npm run generate:api`: no diff.
