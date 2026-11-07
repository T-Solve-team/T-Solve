---
module: import-ticket-list
feature: import-type-upload
task: 24
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 24: Upload a file without Solutions

## Summary
Commit `f6bb9fa` (repo `default`, range `f6bb9fa^..f6bb9fa`), read at HEAD `6267856`.

- **Import type:** the new enum `ImportType` (`entity/ImportType.java:7-16`) holds each type's required columns and template columns. The upload and the template both take `importType` with `defaultValue = "WITH_SOLUTION"` (`ImportController.java:63,83`). An unknown value is a 400 from Spring's type-mismatch handling, and the service is never called.
- **Parser:** the parser checks the type's required columns (MSG29 lists every missing one). It then keeps only the type's template columns (`CsvTicketParser.java:75-82`), so the Solution columns of a file without Solutions are never read and stay null (AC-012.4).
- **Validator:** for a file without Solutions it runs the MSG01 check on `ticket_id` and `title` only (`CsvRowValidator.java:79`). The length, MSG31, `MSG_NEW_3` and MSG39 rules run unchanged. A file without Solutions has null sections, so the 10,000-character Solution total (AC-002.4) cannot fail.
- **Row status:** a row with errors is Error. A row without errors is Needs Solution for a file without Solutions and Valid otherwise (`ImportPreviewRow.java:82-87`).
- **Counts:** the upload counts rows by status (`CsvImportServiceImpl.java:101-115`). The old `rows.size() - valid` would have counted Needs Solution as Error. `summary()` adds the type and the Needs Solution count (`ImportPreviewServiceImpl.java:142-153`). The rows filter binds `PreviewRowStatus`, so `NEEDS_SOLUTION` is accepted.
- **Schema:** the new changeset `009-ticket-import-type.sql` adds `import_type VARCHAR(20) NOT NULL DEFAULT 'WITH_SOLUTION'` and widens `import_preview_row.status` to `VARCHAR(20)`. There is no CHECK constraint on status to update. No applied changeset was edited.

Every AC of STORY-012 and STORY-016 has a named test that checks it, and the clean `mvn verify` is green. I found no CRITICAL issue. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-012.1 to AC-012.7 and AC-016.1 behave as written; see the Stage 1 table below. |
| Completeness | pass | All 8 ACs are traced and none is MISSING. Design edge cases 40, 41, 42 and 52 are tested. No EXTRA behaviour: the 400 for an unknown `importType` is Spring's normal binding. |
| Correctness | pass | `mvn clean verify`: 635 unit tests and 43 integration tests passed, spotless is clean, exit 0. The DB test reads `import_type` and the 14-character status back from PostgreSQL. |
| Integration | pass | It matches the task 23 contract: optional `importType` on the template query and the upload body; `importType` and `needsSolutionCount` on every `ImportPreviewResponse`; `NEEDS_SOLUTION` as a row status and filter. Existing callers of `upload` / `template` / `parse` / `validate` were all updated. The callers of the removed `isValid()` were all replaced, and the build compiles. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-012.1 no type → file with Solutions | PASS | `ImportController.java:83` default; `ImportControllerTest.uploadWithoutATypeIsAFileWithSolutions_AC_012_1` stubs `eq(WITH_SOLUTION)` |
| 2 | AC-012.2 template without Solutions | PASS | `ImportType.java:9-16`; `CsvImportServiceImplTest.templateWithoutSolutionsHasNoSolutionColumns_AC_012_2` checks the exact header |
| 3 | AC-012.3 MSG29 for missing `ticket_id` / `title` | PASS | `CsvTicketParser.java:75-80`; `CsvTicketParserTest.fileWithoutSolutionsListsEveryMissingColumn_AC_012_3` expects `ticket_id, title` |
| 4 | AC-012.4 Solution columns ignored | PASS | `CsvTicketParser.java:82` `retainAll`; `fileWithoutSolutionsIgnoresSolutionColumns_AC_012_4`: all five sections are null, the other columns are kept |
| 5 | AC-012.5 Needs Solution / Error | PASS | `ImportPreviewRow.java:82-87`; validator, service and DB tests |
| 6 | AC-012.6 MSG01 for empty `ticket_id` / `title` | PASS | `CsvRowValidatorTest.rowWithoutSolutionsNeedsTicketIdAndTitle_AC_012_6` (blank and null) |
| 7 | AC-012.7 type + Needs Solution count + filter | PASS | upload response and `summary()`; three tests |
| 8 | AC-016.1 file with Solutions unchanged | PASS | parser MSG29 for the type 2 template uploaded as type 1; Valid / Error rows; Confirm still skips Error rows |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-012 STORY-016` → `8/8 acceptance criteria have a test`

```
✓ AC-012.1  ImportControllerTest
✓ AC-012.2  CsvImportServiceImplTest, ImportControllerTest
✓ AC-012.3  CsvTicketParserTest
✓ AC-012.4  CsvTicketParserTest
✓ AC-012.5  CsvImportServiceImplTest, CsvRowValidatorTest, ImportConfirmIT
✓ AC-012.6  CsvRowValidatorTest
✓ AC-012.7  CsvImportServiceImplTest, ImportPreviewServiceImplTest
✓ AC-016.1  CsvImportServiceImplTest, ImportConfirmServiceImplTest, CsvTicketParserTest
```

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `backend/src/test/java/vn/tsolve/ticket/controller/ImportControllerTest.java:196-207`: `uploadPassesTheChosenType_AC_012_1` sends `WITHOUT_SOLUTION` but asserts `$.importType == "WITH_SOLUTION"`, because the shared `preview()` stub is a file with Solutions. The `eq(ImportType.WITHOUT_SOLUTION)` stubbing does prove the type is passed through, but a reader sees the opposite of what the test name says. Fix: return a `WITHOUT_SOLUTION` preview from this stub and assert `"WITHOUT_SOLUTION"`.
2. [Info] AC-012.7 "accept Needs Solution as a result filter" is tested at the service level only (`ImportPreviewServiceImplTest.listsOnlyNeedsSolutionRowsWhenFiltered_AC_012_7`). `GET …/rows?status=NEEDS_SOLUTION` is not tested over HTTP. It binds the same enum as the Valid / Error / Removed filters, which are tested, so the risk is low. Fix if wanted: one `ImportControllerTest` case with `param("status", "NEEDS_SOLUTION")`.

## Assumptions made
- An unknown `importType` value gives HTTP 400 (no MSG code beyond the handler's default). The spec does not cover a wrong value; I accepted Spring's binding error.
- AC-012.5 says the AC-002.4 rule (the 10,000-character Solution total) does not apply to a file without Solutions. It cannot fail anyway, because the parser leaves the sections null.

## Test Report
`task-24-import-type-upload-test-report.md`: `mvn -B -Dapi.version=1.44 clean verify` exit 0. Surefire 635/635, failsafe 43/43, spotless clean. A first run without `clean` failed on stale `target/` classes (`NoClassDefFoundError: SuggestionResultRecord`), not on the code. No coverage tool is configured, and the spec sets no target.
