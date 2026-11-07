# Test Report: import-ticket-list — Task 24: import-type-upload

**Date:** 2026-11-07. The reviewer made the run at HEAD `6267856` (branch `feat/KAN-31-ticket-import-csv-screens`). The feature commit is `f6bb9fa`. One run is shared by tasks 24, 25 and 26.
**Commands** (Git Bash, in `backend/`): `export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; export PATH="$JAVA_HOME/bin:$PATH"; mvn -B -Dapi.version=1.44 clean verify`. This runs compile, surefire, failsafe (Testcontainers) and `spotless:check`.
**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-012 STORY-016`

## Results
- Compile (main + test): pass
- Surefire (unit): 635 run, 0 failed, 0 errors, 0 skipped
- Failsafe (integration, PostgreSQL Testcontainers): 43 run, 0 failed, 0 errors, 0 skipped
- `spotless:check`: pass (0 files need changes)
- `BUILD SUCCESS`, exit 0, 3 min 19 s

The tests this task touches, all green:

| Class | Tests |
|---|---|
| `ticket.controller.ImportControllerTest` | 21 |
| `ticket.csv.CsvTicketParserTest` | 19 |
| `ticket.csv.CsvRowValidatorTest` | 18 |
| `ticket.service.impl.CsvImportServiceImplTest` | 16 |
| `ticket.service.impl.ImportPreviewServiceImplTest` | 25 |
| `ticket.service.impl.ImportConfirmServiceImplTest` | 22 |
| `ticket.csv.CsvImportPerformanceTest` | 1 |
| `ticket.service.impl.ImportConfirmIT` (DB) | 11 |

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported | The spec is silent and the build has no coverage plugin |

## Acceptance Criteria → Tests
`sdd.ts trace` result: 8/8 acceptance criteria have a test, exit 0.

| AC | Tests (method names) | Result |
|---|---|---|
| AC-012.1 | `ImportControllerTest.uploadWithoutATypeIsAFileWithSolutions_AC_012_1`, `uploadPassesTheChosenType_AC_012_1`, `uploadWithAnUnknownTypeIsRejected_AC_012_1` | pass |
| AC-012.2 | `CsvImportServiceImplTest.templateWithoutSolutionsHasNoSolutionColumns_AC_012_2`, `ImportControllerTest.downloadsTheTemplateOfTheChosenType_AC_012_2` | pass |
| AC-012.3 | `CsvTicketParserTest.fileWithoutSolutionsNeedsOnlyTicketIdAndTitle_AC_012_3`, `fileWithoutSolutionsListsEveryMissingColumn_AC_012_3` | pass |
| AC-012.4 | `CsvTicketParserTest.fileWithoutSolutionsIgnoresSolutionColumns_AC_012_4` (edge case 40) | pass |
| AC-012.5 | `CsvRowValidatorTest.rowWithoutSolutionsPassesWithItsIdAndTitle_AC_012_5`, `rowWithoutSolutionsKeepsTheOtherRules_AC_012_5` (edge case 42), `CsvImportServiceImplTest.fileWithoutSolutionsMarksRowsNeedsSolutionOrError_AC_012_5`, `ImportConfirmIT.savesAFileWithoutSolutionsWithItsTypeAndNeedsSolutionRows_AC_012_5` | pass |
| AC-012.6 | `CsvRowValidatorTest.rowWithoutSolutionsNeedsTicketIdAndTitle_AC_012_6` | pass |
| AC-012.7 | `CsvImportServiceImplTest.fileWithoutSolutionsReturnsItsTypeAndCounts_AC_012_7`, `ImportPreviewServiceImplTest.summaryCountsNeedsSolutionRows_AC_012_7`, `listsOnlyNeedsSolutionRowsWhenFiltered_AC_012_7` | pass |
| AC-016.1 | `CsvTicketParserTest.templateWithoutSolutionsUploadedAsWithSolutionsIsMsg29_AC_016_1` (edge case 41), `CsvImportServiceImplTest.fileWithSolutionsKeepsItsRules_AC_016_1` (edge case 52), `ImportConfirmServiceImplTest.aFileWithSolutionsStillSkipsItsErrorRows_AC_016_1` | pass |

## Failures
None.

## Notes
- The first run, `mvn -B -Dapi.version=1.44 verify` without `clean`, failed before any test ran: `TestEngine with ID 'junit-jupiter' failed to discover tests`, caused by `NoClassDefFoundError: SuggestionResultRecord` in `triage.service.impl.SuggestionResultServiceImplTest`. The compiler had reported "Nothing to compile - all classes are up to date", so `target/` held stale classes from before the merges of `main`. Only build output was affected, not source. The `clean` run above is the result I judged by.
- `ImportConfirmIT` takes 12.5 s for 11 tests, mostly container start-up. No single slow test, no skipped tests, and no flaky result.
- The changeset `009-ticket-import-type.sql` applied cleanly on a fresh PostgreSQL in the integration run. `ImportConfirmIT.savesAFileWithoutSolutions…` reads back `import_type` and the 14-character `NEEDS_SOLUTION` status.
