---
module: import-ticket-list
feature: csv-row-validation
task: 4
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 4: Row validator

## Summary
`CsvRowValidator` reports every failed rule of a row: MSG01 for blank required values, MSG02 per over-long field and for the Solution total (code points), MSG31 for existing IDs, MSG78 for every duplicate in the file, MSG39 for a non-ISO or future date in Vietnam time (injected `Clock` with zone `Asia/Ho_Chi_Minh`). IDs are compared after trimming.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.1 `CsvRowValidator.java:53-114`; .2 `:74-78`; .3 `:79-85`; .4 `:86-99`; .5 `:101-104`; .6 `:105-107`; .7 `:109-122` + `TicketClockConfig.java:13-15`; .10 `:140-146`. |
| Completeness | pass | Edge cases 7, 12–17 covered (boundary 10,000 vs 10,001, emoji lengths, `2026-02-30`, tomorrow). |
| Correctness | pass | 15/15 tests pass; performance test validates 5,000 rows. |
| Integration | pass | `RowError(code, field, params)` stored as JSONB and returned in the contract's `RowError` shape. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

| AC | Test | Status |
|---|---|---|
| AC-002.1 | validRowHasNoErrors / listsEveryFailedRuleOfARow `_AC_002_1`; CsvImportPerformanceTest `_AC_002_1` | PASS |
| AC-002.2 | blankOrMissingRequiredValueIsMsg01 / workaroundDescriptionAuthorAndDateAreOptional `_AC_002_2` | PASS |
| AC-002.3 | fieldOverItsLimitIsMsg02 / fieldExactlyAtItsLimitIsValid / lengthCountsCharactersNotUtf16Units `_AC_002_3` | PASS |
| AC-002.4 | solutionOverTenThousandCharactersInTotalIsMsg02_AC_002_4 | PASS |
| AC-002.5 | existingTicketIdIsMsg31_AC_002_5; CsvImportServiceImplTest.looksUpExistingTicketIdsOnce_AC_002_5 | PASS |
| AC-002.6 | everyRowSharingATicketIdIsMsg78 / duplicateInFileThatAlsoExistsGetsBothErrors `_AC_002_6` | PASS |
| AC-002.7 | invalidOrFutureResolvedDateIsMsg39 / todayInVietnamIsNotInTheFuture `_AC_002_7` | PASS |
| AC-002.10 | ticketIdsAreComparedAfterTrimming / ticketIdsOfReturnsTrimmedNonBlankIds `_AC_002_10` | PASS |

## Issues
CRITICAL: none.

Informational:
1. [Low] `backend/src/main/java/vn/tsolve/ticket/csv/CsvRowValidator.java:118` — `LocalDate.parse` (ISO_LOCAL_DATE) also accepts signed years such as `-0001-01-01` or `0000-01-01`, which pass as "past" dates and are stored as BC dates. The spec asks for `yyyy-MM-dd`.
   Fix: require `\d{4}-\d{2}-\d{2}` before parsing (or a year range check) and add the case to `invalidOrFutureResolvedDateIsMsg39_AC_002_7`.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.

## Test Report
Shared full run (all 13 tasks), see `task-4-csv-row-validation-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
