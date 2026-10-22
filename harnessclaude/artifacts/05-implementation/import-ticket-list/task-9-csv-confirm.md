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
