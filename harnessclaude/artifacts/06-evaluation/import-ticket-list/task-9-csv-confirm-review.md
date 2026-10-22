---
module: import-ticket-list
feature: csv-confirm
task: 9
verdict: pass
attempt: 2
date: 2026-10-23
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 9: Confirm the import — Attempt 2 (2026-10-23)

## Summary
Re-review of the fix commit `3905cdc` (range `d15e991..58e0a06`, repo `default`, branch `feat/KAN-31-import-ticket-csv`) against the spec version approved as b3015b47 (Changelog 2026-10-22: new AC-004.11 and AC-004.12, reworded AC-004.6). The attempt-1 blocker is fixed. Confirm now checks `isActive`, which gives MSG40, and then `managesActive`, which gives 403 MSG08. Both checks run before the transaction, so neither records an Import Batch. Inside the transaction the code locks the preview with `PESSIMISTIC_WRITE`, reads the Valid rows again, and returns 404 without a Failed batch when another Confirm has already taken the preview. A taken ID caught by the unique constraint now gives 409. The event is published inside the transaction and reaches `AFTER_COMMIT` listeners; the IT shows that a listener failing after commit does not undo the import. Ticket values no longer appear in the logs: I checked this in this run's `mvn verify` output. Every AC of STORY-004 has a test that checks it, and 99/99 tests pass. No CRITICAL code-review finding and no Critical/High security finding remain. **Verdict pass.** The informational findings below do not block.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-004.1–004.12 implemented. `ImportConfirmServiceImpl.java:97-102`: MSG40 first, then 403. `:151-160`: locked re-load with 404, and the Valid rows read again. `:110-112`: unique-constraint path gives 409. `:187`: event published inside the transaction. |
| Completeness | pass | Every AC of STORY-004 has a test that names it and checks the behaviour (table below). Nothing EXTRA. |
| Correctness | pass | `mvn verify`: 93 unit tests and 6 `ImportConfirmIT` tests, 0 failed or skipped. `spotless:check` passes. No CRITICAL finding. |
| Integration | pass | The `TicketsPublishedEvent` Javadoc tells listeners to use `@TransactionalEventListener(AFTER_COMMIT)` (design E4). `ImportConfirmIT` listens that way and proves delivery after commit from a separate connection (`ImportConfirmIT.java:164,176`). OpenAPI already lists 403/404/409 for `/confirm`. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-003 STORY-004` → `trace import-ticket-list (STORY-003, STORY-004): 0/16 acceptance criteria have a test` (every AC ✗ "no test under this module's paths"). This is the known harness gap: the tool does not see JUnit methods named `_AC_XXX_Y` (FEEDBACK.md). For the manual trace I grepped `backend/src/test` for `_AC_004_N` and read each test:

| AC | Tests (`ImportConfirmServiceImplTest` unless noted) | Status |
|---|---|---|
| AC-004.1 | savesEachValidRowAsAPublishedTicket_AC_004_1 | PASS |
| AC-004.2 | recordsAnApproveDecisionByTheProjectManagerPerTicket_AC_004_2 | PASS |
| AC-004.3 | recordsOneCompletedImportBatchAndLinksTheTickets_AC_004_3 | PASS |
| AC-004.4 | returnsMsg32AndDeletesThePreview_AC_004_4; ImportControllerTest.confirmReturnsTheImportResult_AC_004_4 | PASS |
| AC-004.5 | rejectsPreviewWithoutValidRows_AC_004_5 | PASS |
| AC-004.6 (reworded) | rejectsWhenTheProjectIsNoLongerActive_AC_004_6: 409 MSG40, no `saveAll`, no `recordFailed`. The rejection comes before the transaction, so no Completed batch is possible either. | PASS |
| AC-004.7 | failsWholeConfirmWhenATicketIdWasTakenMeanwhile_AC_004_7; uniqueConstraintOnTicketIdIsAlsoATakenId_AC_004_7 (:253); ImportControllerTest.confirmFailureIsMsg33_AC_004_7 | PASS |
| AC-004.8 | savingFailureImportsNothingAndRecordsAFailedBatch_AC_004_8; failureToRecordTheFailedBatchStillAnswersMsg33_AC_004_8 (:281); ImportConfirmIT.savingFailureRollsBackEveryTicketButKeepsTheFailedBatch_AC_004_8 | PASS |
| AC-004.9 | rejectsAnotherUsersPreview_AC_004_9 | PASS |
| AC-004.10 | publishesTheNewTicketIdsInsideTheImportTransaction_AC_004_10 (:297); ImportConfirmIT.eventArrivesAfterTheTicketsAreCommitted_AC_004_10, aListenerFailingAfterCommitDoesNotUndoTheImport_AC_004_10 | PASS |
| AC-004.11 (new) | rejectsCallerWhoNoLongerManagesTheProject_AC_004_11 (:314): 403 MSG08, no `saveAll`, no batch save, no `recordFailed`, no transaction. inactiveProjectIsCheckedBeforeManagement_AC_004_11 (:328): MSG40 wins. | PASS |
| AC-004.12 (new) | secondConfirmOfTheSamePreviewIsNotFoundWithoutAFailedBatch_AC_004_12 (:338); validRowsAreReadAgainUnderTheLock_AC_004_12 (:350); ImportConfirmIT.secondConfirmOfTheSamePreviewIsNotFound_AC_004_12 (:189), which uses two real threads on PostgreSQL: one "ok", one "404", exactly one COMPLETED batch, one event. | PASS |

### Attempt-1 findings
| # | Attempt-1 finding | Status |
|---|---|---|
| 1 | [High] Confirm did not re-check that the caller manages the Project | **Resolved.** The spec gained AC-004.11. `ImportConfirmServiceImpl.java:100-102` and the tests above. |
| 2 | [Medium] Unlocked read gave a double Confirm a spurious Failed batch | **Resolved** for a double Confirm (`:151-160`, `ImportPreviewRepository.lockByIdAndOwner`). **Partly resolved** for remove-rows racing a Confirm: see issue 1 below. |
| 3 | [Medium] Unique-constraint path returned 500 | **Resolved.** `:110-112` gives 409 with `TicketIdTaken: MSG33`, and this run's IT log shows `DataIntegrityViolationException (SQLState 23505)` taking this path. See issue 2 for how broad the catch is. |
| 4 | [Medium] Event published outside the transaction | **Resolved** (design E4). `:187`, with the Javadoc on `TicketsPublishedEvent` and the AFTER_COMMIT IT. |
| 5 | [Medium] Ticket content in the logs | **Resolved.** `application.yml:31-36` sets both loggers to OFF, and the service logs only the exception type and SQLState (`:126-131`). In this run's verify log, grep finds no `insert into ticket` and none of `Login fails` / `Error 500` / `Bad token`. See issue 4. |
| 6 | [Medium] `IN (...)` bind limit | **Knowingly deferred** (design E8). Still at `:165`. |
| 7 | [Low] Recorder failure hid MSG33 | **Resolved.** `:132-139`, tested at `:281`. |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportPreviewServiceImpl.java:84-87,65-72`: remove-rows does not take the preview lock that Confirm holds. A remove-rows that starts after Confirm read the Valid rows (`ImportConfirmServiceImpl.java:157`) still commits and returns 200, and Confirm then imports the row the PM has just removed (AC-003.1, "exclude them from Confirm"). The window is narrow and needs the same PM to act twice at once.
   Fix: in `removeRows`, load the preview with `previews.lockByIdAndOwner(previewId, user)`, so it waits for a running Confirm and then gets 404.
2. [Low] `ImportConfirmServiceImpl.java:110`: every `DataIntegrityViolationException` is treated as "Ticket ID taken" (409, batch error `TicketIdTaken: MSG33`). A NOT NULL, FK or length violation is a saving failure, which should give 500 (spec Interface Contracts), and the Failed batch then names the wrong cause.
   Fix: give 409 only for SQLState 23505 on constraint `uq_ticket_ticket_id` (e.g. via Hibernate `ConstraintViolationException.getConstraintName()`); otherwise use the 500 branch.
3. [Low] `ImportConfirmIT.java:240`: `uniqueTicketIdIsTheLastGuardAgainstTwoConfirmsAtOnce` checks only the code MSG33. Only the unit test (`:253`) checks the 409 status. Fix: also assert `getStatus() == CONFLICT` in the IT.
4. [Low] `backend/src/main/resources/application.yml:35-36`: `SqlExceptionHelper` is OFF for the whole application, so SQL errors in other modules are no longer logged unless their caller logs them. That is accepted by design E5. Note it for the modules that come next.
5. [Low, performance] `ImportConfirmServiceImpl.java:91-93`: the pre-check loads every Valid row entity (up to about 5,000 rows of up to 10,000 characters) only to test whether any exist, and `:157` loads them again. Fix: use an `existsByIdPreviewIdAndStatus` or count query for the pre-check.

## Assumptions made
- The `sdd.ts trace` result (0/16) is a tool limitation, not missing tests. I used the manual `_AC_XXX_Y` trace instead, as the dispatcher instructed.
- AC-004.10 says "publish … after the transaction commits". I read it as *delivered* after commit to listeners written as the event's Javadoc requires (`@TransactionalEventListener(AFTER_COMMIT)`, design E4). A plain `@EventListener` would now run before the commit. **Open judgment call for the user:** is a Javadoc rule enough, or should the spec or AGENTS.md state it?
- The `IN (...)` bind limit stays deferred (design E8) and does not block.
- `docs/bug-log.md` has no rows for the attempt-1 findings. I took findings on unmerged in-module code to be handled by the harness fixing loop rather than as logged bugs. **Open judgment call for the user:** should evaluation findings also go into the bug log (project rule "Log every bug")?
- Coverage was not measured (no JaCoCo), and the spec sets no threshold. Same open question as attempt 1.

## Test Report
`task-9-csv-confirm-test-report.md` (attempt 2): `mvn verify` exit 0, with 99 tests (93 Surefire and 6 Failsafe `ImportConfirmIT` on PostgreSQL 16), 0 failed or skipped. `mvn spotless:check` passes. Coverage not measured. Security: `security-review.md`, section "Attempt 2 (2026-10-23)".

---

# Attempt 1 (2026-10-22) — verdict fail

# Review: import-ticket-list — Task 9: Confirm the import

## Summary
Confirm imports the Valid rows all-or-nothing in one `TransactionTemplate` transaction, records an APPROVE decision per Ticket and one Completed Import Batch, deletes the preview, returns MSG32, and publishes `TicketsPublishedEvent` after commit; failures record a Failed batch in `REQUIRES_NEW` with no row content. Every listed AC has a passing test. **Verdict fail**: Confirm does not re-check that the caller still manages the preview's Project (spec NFR 3, Interface Contracts "Project not managed → 403"), a High security finding (Elevation of privilege). The spec is also incomplete here: no AC states this check at Confirm, so the fix starts with a spec change.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | AC-004.1–004.10 implemented (`ImportConfirmServiceImpl.java:81-175`), but NFR 3 is not enforced at Confirm (`:92` checks only `isActive`). |
| Completeness | fail | Spec gap: NFR 3 has no acceptance criterion for Confirm (or the row endpoints); no test. |
| Correctness | pass | 11/11 unit tests + 4/4 `ImportConfirmIT` pass. Medium findings below (race, 500 vs 409). |
| Integration | pass (with a note) | Event is published outside any transaction, so listeners written as the design prescribes (`@TransactionalEventListener(AFTER_COMMIT)`) would never run — see issue 4. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

| AC | Test | Status |
|---|---|---|
| AC-004.1 | savesEachValidRowAsAPublishedTicket_AC_004_1 | PASS |
| AC-004.2 | recordsAnApproveDecisionByTheProjectManagerPerTicket_AC_004_2 | PASS |
| AC-004.3 | recordsOneCompletedImportBatchAndLinksTheTickets_AC_004_3 | PASS |
| AC-004.4 | returnsMsg32AndDeletesThePreview_AC_004_4; ImportControllerTest.confirmReturnsTheImportResult_AC_004_4 | PASS |
| AC-004.5 | rejectsPreviewWithoutValidRows_AC_004_5 | PASS |
| AC-004.6 | rejectsWhenTheProjectIsNoLongerActive_AC_004_6 | PASS |
| AC-004.7 | failsWholeConfirmWhenATicketIdWasTakenMeanwhile_AC_004_7; ImportControllerTest.confirmFailureIsMsg33_AC_004_7 | PASS |
| AC-004.8 | savingFailureImportsNothingAndRecordsAFailedBatch_AC_004_8; ImportConfirmIT.savingFailureRollsBackEveryTicketButKeepsTheFailedBatch_AC_004_8 | PASS |
| AC-004.9 | rejectsAnotherUsersPreview_AC_004_9 | PASS |
| AC-004.10 | publishesTheNewTicketIdsAfterCommit / aFailingListenerDoesNotUndoTheImport `_AC_004_10`; ImportConfirmIT.eventArrivesAfterTheTicketsAreCommitted_AC_004_10 | PASS |
| NFR 3 at Confirm | — (no AC in the spec) | MISSING |

## Issues
CRITICAL (blocks pass):
1. [High security, spec gap] `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImpl.java:92` — only `projects.isActive(preview.getProjectId())` is checked. Expected (spec NFR 3 "restricted … to Projects the caller manages; the backend re-checks this on every call"; Interface Contracts "Project not managed → 403 (MSG08)"): a PM who no longer manages the Project is refused. Actual: a PM removed from Project X after uploading can still publish Tickets into X with a PM-approve decision until the preview expires (24 h).
   Fix: (a) spec change first (plan skill, Change Mode): add e.g. `AC-004.11: IF the caller no longer manages the preview's Project at Confirm, THEN THE system SHALL reject Confirm with HTTP 403 (MSG08) and import nothing`, and decide its order against MSG40; (b) code: before the transaction, `if (!projects.isActive(id)) throw new ProjectNotActiveException(); if (!projects.managesActive(user, id)) throw new ProjectAccessDeniedException();`; (c) test `rejectsWhenTheCallerNoLongerManagesTheProject_AC_004_11` asserting 403, no `saveAll`, no Failed batch.

Informational (does not block):
2. [Medium] `ImportConfirmServiceImpl.java:83-98` — the preview and its Valid rows are read outside the transaction with no lock. A double click (two Confirms of the same preview) lets both proceed; the second hits the taken IDs (or the unique constraint) and records a **spurious Failed Import Batch** and MSG33, where design edge case 21 expects 404. A remove-rows racing a Confirm can import a row just removed.
   Fix: inside the transaction, load the preview with `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`findByIdAndOwner…ForUpdate`), re-read the Valid rows there, and throw `NotFoundException` when it is gone; add a same-user double-Confirm case to `ImportConfirmIT` (one ok, one 404, exactly one batch).
3. [Medium] `ImportConfirmServiceImpl.java:103-108` — when the unique constraint (not the pre-check) catches a taken Ticket ID, the result is MSG33 with **500**; the spec's Interface Contracts say "MSG33 because a Ticket ID was taken → 409". The IT `uniqueTicketIdIsTheLastGuardAgainstTwoConfirmsAtOnce` accepts either.
   Fix: map a `DataIntegrityViolationException` on `uq_ticket_ticket_id` to `ImportFailedException(CONFLICT)` with error `TicketIdTaken: MSG33`.
4. [Medium] `ImportConfirmServiceImpl.java:111,169-175` — the event is published after `transaction.execute` returns, outside any transaction. The design (Key Decisions, "Search hand-off") says listeners use `@TransactionalEventListener(AFTER_COMMIT)`; Spring skips such listeners when no transaction is active (`fallbackExecution = false`), so the search module would silently miss every import. The IT uses a plain `@EventListener`, which hides this.
   Fix: either publish inside the transaction callback and let listeners use `@TransactionalEventListener(AFTER_COMMIT)` (spec AC-004.10 still holds), or document on `TicketsPublishedEvent` that listeners must be plain `@EventListener` and update the design.
5. [Medium] Ticket row content is logged at ERROR by Hibernate/pgjdbc when an insert fails (seen in this run's `mvn verify` log: `Batch entry 0 insert into ticket … values (… 'Login fails' … 'Error 500' …)`), against AGENTS.md §9. See `security-review.md` finding 2 for the fix.
6. [Medium] `ImportConfirmServiceImpl.java:125` — same single `IN (...)` bind-parameter limit as task 6 issue 1 for previews with > 65,535 Valid rows.
7. [Low] `ImportConfirmServiceImpl.java:100-108` — if `recorder.recordFailed` itself throws, that exception replaces `ImportFailedException` and the client gets an unhandled 500 without MSG33.
   Fix: wrap the recorder call in try/catch, log (no row content) and still throw `ImportFailedException`.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.
- NFR 3 "re-checks this on every call" is read as including "the caller still manages the Project" at Confirm, and the gap is rated High (Elevation of privilege: publishing into a Project after losing it). Open judgment call for the user: confirm this reading and the error order (MSG40 vs 403) before the spec change.
- Design edge case 21 (double click → 404, no Failed batch) is not in the spec; AC-004.7 read literally allows the current behaviour. Treated as Medium, non-blocking; the user may want an AC for it.

## Test Report
Shared full run (all 13 tasks), see `task-9-csv-confirm-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
