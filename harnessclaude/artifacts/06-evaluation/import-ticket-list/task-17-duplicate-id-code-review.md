---
module: import-ticket-list
feature: duplicate-id-code
task: 17
verdict: pass
attempt: 1
date: 2026-11-03
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 17: Duplicate Ticket ID code MSG_NEW_3

## Summary
Commit `da59564` (repo `default`, range `da59564^..da59564`) changes the in-file duplicate error in `CsvRowValidator.java:105-107` from `MSG78` to `MSG_NEW_3`, with params `{ticket_id}` on field `ticket_id`, as D14 and AC-002.6 require. MSG39 stays for the resolved date (`:110+`, unchanged). The `RowError` description in the contract and the generated model were updated, and `generate:api` leaves no diff. No `MSG78` is left in the import code. The `MSG78` uses still in `features/users` belong to the other message that v3460 gave that number. The frontend catalog entry (`795acef`, fe-foundation, context only) has the text from the spec's "New messages" table. The Import Preview test shows that text for a duplicate row. All STORY-002 criteria have passing tests. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.6: every row whose trimmed `ticket_id` appears more than once gets `MSG_NEW_3` (`idCounts > 1`). Together with MSG31 for an id that already exists, both errors are listed (AC-002.1). |
| Completeness | pass | The validator, its test, the `RowError` description in the contract and the generated model are all updated. The catalog entry is fe-foundation's (AC-006.9) and present in `795acef`. Nothing EXTRA. |
| Correctness | pass | `CsvRowValidatorTest` 15/15. `mvn verify` BUILD SUCCESS (233 unit tests, 10 ITs, 0 failed). `spotless:check` passes. Frontend `catalog.test.ts` (AC-006.7, AC-006.9) and `ImportPreviewPage.test.tsx:102` (shows `msg('MSG_NEW_3', {ticket_id})`) pass within 1074/1074. |
| Integration | pass | The backend code `MSG_NEW_3` with param `ticket_id` matches the catalog text `Ticket ID {ticket_id} appears more than once in this file.` (`catalog.ts:266-269`). |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-002` → `0/11 acceptance criteria have a test`. This is the known gap for JUnit `_AC_XXX_Y` names. Manual trace:

| AC | Tests | Status |
|---|---|---|
| AC-002.1 | CsvRowValidatorTest.validRowHasNoErrors_AC_002_1, listsEveryFailedRuleOfARow_AC_002_1; CsvImportPerformanceTest.checksATenMegabyteFileWellWithinSixtySeconds_AC_002_1 | PASS |
| AC-002.2 | blankOrMissingRequiredValueIsMsg01_AC_002_2, workaroundDescriptionAuthorAndDateAreOptional_AC_002_2 | PASS |
| AC-002.3 | fieldOverItsLimitIsMsg02_AC_002_3, fieldExactlyAtItsLimitIsValid_AC_002_3, lengthCountsCharactersNotUtf16Units_AC_002_3 | PASS |
| AC-002.4 | solutionOverTenThousandCharactersInTotalIsMsg02_AC_002_4 | PASS |
| AC-002.5 | existingTicketIdIsMsg31_AC_002_5; CsvImportServiceImplTest.looksUpExistingTicketIdsOnce_AC_002_5 | PASS |
| AC-002.6 | everyRowSharingATicketIdIsMsgNew3_AC_002_6 (:158), duplicateInFileThatAlsoExistsGetsBothErrors_AC_002_6 (:169) | PASS |
| AC-002.7 | invalidOrFutureResolvedDateIsMsg39_AC_002_7, todayInVietnamIsNotInTheFuture_AC_002_7 | PASS |
| AC-002.8 | CsvImportServiceImplTest.listCutsSolutionSectionsToTwoHundredCharacters_AC_002_8; ImportPreviewServiceImplTest.listsRowsPageByPageWithSnippets_AC_002_8, capsThePageSizeAtOneHundred_AC_002_8 | PASS |
| AC-002.9 | CsvImportServiceImplTest.reportsErrorRowCountWithMsg30_AC_002_9 | PASS |
| AC-002.10 | ticketIdsAreComparedAfterTrimming_AC_002_10, ticketIdsOfReturnsTrimmedNonBlankIds_AC_002_10 | PASS |
| AC-002.11 | ImportPreviewServiceImplTest.filtersRowsByResult_AC_002_11 | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `backend/src/test/java/vn/tsolve/ticket/csv/CsvRowValidatorTest.java:163-165`: the test asserts the `MSG_NEW_3` error only on row 1. Rows 3 and 4 are checked only as "has errors" (`:163`), so a bug that gave a different code to later duplicates would still pass. Fix: assert `containsExactly(new RowError("MSG_NEW_3", …))` for results 0, 2 and 3.
2. [Low, artifact] `harnessclaude/artifacts/04-plans/import-ticket-list/design.md:18`: the architecture diagram still lists `MSG01/02/31/39/78`. The "Change 2026-11-01" section is correct. Fix: change `78` to `MSG_NEW_3` in the diagram.

## Assumptions made
- The spec's "New messages" text writes the placeholder as `[ticket_id]` and says it is "written as in SRS V.2 with braces". I read the catalog's `{ticket_id}` as matching.
- `795acef` (catalog) belongs to fe-foundation and is context only. I checked only that it matches the backend code and param name.
- The `sdd.ts trace` result (0/11) is a tool limitation. I used the manual trace instead.

## Test Report
`task-17-duplicate-id-code-test-report.md`. `mvn verify` BUILD SUCCESS at `15d346c`: 233 unit tests (0 failed, 1 skipped, pre-existing) and 10 ITs (0 failed). Spotless passes. Frontend 1074/1074, and `generate:api` leaves no diff.
