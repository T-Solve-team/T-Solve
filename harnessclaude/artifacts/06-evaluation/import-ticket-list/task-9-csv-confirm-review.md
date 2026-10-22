---
module: import-ticket-list
feature: csv-confirm
task: 9
verdict: fail
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

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
