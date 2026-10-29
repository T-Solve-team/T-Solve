# Task 3: CSV parser

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 17:00

### Working on: csv-parser
- `backend/pom.xml`: added `org.apache.commons:commons-csv` 1.12.0 (property `commons-csv.version`).
- `CsvTicketParser` (`@Component`):
  - strict UTF-8 decoder (`CodingErrorAction.REPORT`); `CSVFormat.RFC4180` with `ignoreEmptyLines`; the first record is the header;
  - the BOM is stripped from the first header cell; header names are trimmed and lower-cased;
  - a duplicate header name → MSG27; a missing required column → MSG29 with `column_names` in template order;
  - no records → MSG27; header only → MSG04; bad encoding or broken quoting → MSG27;
  - `rowNo` = record number − 1; a missing trailing cell → `null`.
- `CsvColumns` (template + required names), `ParsedCsv(rows)` with `Row(rowNo, ImportPreviewRow.Values)`, `CsvFileRejectedException` (400).
- Deviation: created `common/error/MessageCodeException` (status + code + params) here instead of in Task 5, because `CsvFileRejectedException` extends it. Task 5 wires it into `GlobalExceptionHandler`.
- Tests (TDD: red on compile, then green): `CsvTicketParserTest`, 15 tests, all pass. They cover AC-001.3 (non-UTF-8, unclosed quote, empty file, duplicate header), AC-001.5 (missing columns listed; `;` file), AC-001.6, AC-001.7, AC-001.8 and AC-001.12, plus edge cases 1–6 and 9: header case/spaces, record numbering, blank lines, short rows.
- Verify: `mvn spotless:apply`, then `mvn verify` → exit 0 (15 tests, 0 failures).
- `sdd.ts trace import-ticket-list STORY-001` → 0/12. This is a harness gap: trace scans neither `*Test.java` files nor the `_AC_001_3` spelling that `backend/AGENTS.md` requires. Logged in `harnessclaude/FEEDBACK.md`. Coverage was checked by hand from the test names above.

- Commit: default@37f8037 (feat/KAN-31-import-ticket-csv)

### Blockers
The trace gate cannot pass for Java tests until the harness is fixed (FEEDBACK.md, 2026-10-22).

### Next up
Task 4 csv-row-validation.
