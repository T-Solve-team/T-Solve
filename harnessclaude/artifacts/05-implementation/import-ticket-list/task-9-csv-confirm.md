# Task 9: Confirm the import

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 20:00

### Working on: csv-confirm
- `ImportConfirmService.confirm(user, previewId)`, implemented in `ImportConfirmServiceImpl`:
  1. Load the preview by owner; otherwise `NotFoundException`, 404 (AC-004.9).
  2. Count the Error and Removed rows and load the Valid ones. No Valid row → `NothingToImportException` (409 MSG30 `{count}`, AC-004.5).
  3. `ProjectAccess.isActive` is false → `ProjectNotActiveException` (409 MSG40, AC-004.6). No Import Batch is recorded for either rejection (design B1).
  4. One transaction, through `TransactionTemplate` built from the `PlatformTransactionManager`:
     - re-check the Ticket IDs with `findExistingTicketIds`; any taken → roll back;
     - save `ImportBatch.completed`, then `saveAll` the Tickets (Published, source from the preview, writer = trimmed author or null, `resolved_date` parsed, every value trimmed);
     - `saveAll` one APPROVE `TicketReviewDecision` per ticket (reviewer = the PM);
     - `deleteById` the preview (its rows go by ON DELETE CASCADE).
  5. On failure, `ImportBatchRecorder.recordFailed` (`@Transactional(REQUIRES_NEW)`) saves a FAILED batch with the error/removed counts and `"<ExceptionType>: MSG33"`, with no row content. A taken id → `ImportFailedException(409)` (AC-004.7); anything else → `ImportFailedException(500)` (AC-004.8). The preview is kept, so the PM can try again.
  6. After commit, publish `TicketsPublishedEvent(ticketIds)`. A failing listener is logged (count only) and does not change the result (AC-004.10, edge case 25).
  7. Return `ImportResultResponse(importBatchId, created, skipped = error + removed, MSG32 {created, skipped})` (AC-004.4).
- `ImportController`: `POST /api/v1/imports/previews/{previewId}/confirm` (`operationId confirmImport`).
- `CsvRowValidator.trim` is now public, so Confirm saves values exactly as they were validated.
- Deviation from the design: the event is published by the service once `TransactionTemplate.execute` has returned, which is after commit, instead of relying on `@TransactionalEventListener(AFTER_COMMIT)` in each listener. The result is the same (published only after commit), and this works with plain `@EventListener`s too.
- Tests (TDD: red on compile, then green):
  - `ImportConfirmServiceImplTest`, 11 tests. They use a recording `PlatformTransactionManager` to check commit versus rollback, and cover AC-004.1 – AC-004.10, with the event coming after the commit.
  - `ImportControllerTest`, 3 more: confirm result, 409 MSG33, 403 for Staff.
  - The real-database proofs (rollback, after-commit, unique constraint) are Task 13.
- Local environment: Mockito cannot mock the concrete class `ImportBatchRecorder` on JDK 24 (Byte Buddy supports up to Java 23). Locally the tests run with `-DargLine="-Dnet.bytebuddy.experimental=true"`. On the project's Java 21 they need no flag. The pom is unchanged.
- Verify: `mvn spotless:apply`, then `mvn verify -DargLine="-Dnet.bytebuddy.experimental=true"` → exit 0; 83 tests, 0 failures.

- Commit: default@f77e38f (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Task 10 preview-cleanup.

## Session 2026-10-22 23:30 — fixing (evaluation attempt 1 → fix for attempt 2)

### Working on: csv-confirm
- Review `artifacts/06-evaluation/import-ticket-list/task-9-csv-confirm-review.md` (attempt 1, fail). Spec changed first, in Change Mode: new AC-004.11 and AC-004.12, AC-004.6 reworded, AC-003.4 for Task 14. The user's decisions were MSG40 before 403, a double Confirm → 404 with no batch, and the rows endpoints re-checked too. Re-approved as spec version b3015b47. Design table E1–E8.
- Fixes, in `ImportConfirmServiceImpl` unless stated:
  1. **High (AC-004.11):** after the MSG40 check, `managesActive(user, project)` → `ProjectAccessDeniedException` (403 MSG08); no batch, no transaction.
  2. **Medium (AC-004.12):** inside the transaction the preview is re-loaded with `ImportPreviewRepository.lockByIdAndOwner` (`@Lock(PESSIMISTIC_WRITE)`), then the counts and Valid rows are re-read under the lock. A second Confirm waits, finds the preview gone → `NotFoundException` (404). A remove-rows that emptied it → MSG30. Both are re-thrown without a Failed batch.
  3. **Medium:** `DataIntegrityViolationException` (the unique constraint) is handled like the pre-check: 409 MSG33, error `TicketIdTaken: MSG33`.
  4. **Medium:** the event is published inside the transaction. `TicketsPublishedEvent`'s Javadoc tells listeners to use `@TransactionalEventListener(AFTER_COMMIT)`, so they now actually fire after commit. This replaces the Task 9 "publish after execute" deviation.
  5. **Medium:** `application.yml` turns off `SqlExceptionHelper` and `org.hibernate.orm.jdbc.batch` logging, because their messages carried the inserted values. The service logs its own line (exception type + SQLState). The verify log of this run contains no `Batch entry` or `values (` lines, although AC-004.8's IT forces an insert failure.
  6. **Low:** a failing `recordFailed` is logged and the client still gets MSG33.
  - Not fixed (design E8): the `IN (...)` bind-parameter limit above 65,535 ids. It is a follow-up; the NFR file is about 5,000 rows.
- Tests (red first: the new tests did not compile):
  - `ImportConfirmServiceImplTest`, now 16 tests. New: AC-004.11 ×2 (403; MSG40 first), AC-004.12 ×2 (lock finds the preview gone → 404, no batch; Valid rows re-read under the lock), the constraint → 409, recorder failure → MSG33. The event test now checks the event comes before the commit, inside the transaction. The listener-failure unit test moved to the IT.
  - `ImportConfirmIT`, now 6 tests. The listener is `@TransactionalEventListener(AFTER_COMMIT)` and counts tickets on its own connection (sees 3 = committed). New: a listener failing after commit leaves the import Completed (edge case 25); two threads confirm the same preview → one ok, one 404, one COMPLETED batch, one event (AC-004.12).
- Verify: `mvn spotless:apply`, then `mvn verify -DargLine="-Dnet.bytebuddy.experimental=true"` → exit 0; Surefire 92 tests, 0 failures; Failsafe 6 ITs, 0 failures.

- Commit: default@3905cdc (feat/KAN-31-import-ticket-csv)

### Blockers
none

### Next up
Task 14 preview-rows-access, then evaluation attempt 2.
