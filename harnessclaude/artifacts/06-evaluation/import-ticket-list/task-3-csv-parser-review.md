---
module: import-ticket-list
feature: csv-parser
task: 3
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 3: CSV parser

## Summary
`CsvTicketParser` decodes strict UTF-8 (`CodingErrorAction.REPORT`), strips a BOM, parses RFC 4180 with Commons CSV 1.12.0, trims and lower-cases header names, lists every missing required column (MSG29), rejects a header-only file (MSG04) and a malformed/badly encoded/empty file (MSG27). Every AC of the task has a test that checks it.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.3 `CsvTicketParser.java:36-58`; AC-001.5 `:74-78`; AC-001.6 `:52-54`; AC-001.7 `:66-68`; AC-001.8 `:62-80` (only template columns read); AC-001.12 Commons CSV RFC 4180. |
| Completeness | pass | Edge cases 1–6 and 9 covered (`;` file → MSG29, zero-byte → MSG27, unclosed quote → MSG27). |
| Correctness | pass | 15/15 tests pass. |
| Integration | pass | Returns `ParsedCsv` rows consumed by `CsvRowValidator`/`CsvImportServiceImpl`. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

| AC | Test | Status |
|---|---|---|
| AC-001.3 | CsvTicketParserTest.rejectsNonUtf8File / rejectsUnclosedQuote / rejectsEmptyFile / rejectsDuplicateHeaderName `_AC_001_3` | PASS |
| AC-001.5 | listsEveryMissingRequiredColumn / semicolonFileFailsTheHeaderCheck `_AC_001_5` | PASS |
| AC-001.6 | rejectsHeaderWithoutDataRows_AC_001_6 | PASS |
| AC-001.7 | ignoresByteOrderMark_AC_001_7 | PASS |
| AC-001.8 | ignoresColumnsNotInTheTemplate_AC_001_8 | PASS |
| AC-001.12 | readsCommasQuotesAndLineBreaksInsideQuotedValues_AC_001_12 | PASS |

## Issues
CRITICAL: none.

Informational:
1. [Low] `backend/src/main/java/vn/tsolve/ticket/csv/CsvTicketParser.java:66` — the BOM is removed only from the parsed first header name. A BOM followed by a quoted header (`\uFEFF"ticket_id",...`) makes Commons CSV read the cell as `\uFEFF"ticket_id"` (quote not at token start), so the file fails with MSG29 instead of being accepted (AC-001.7).
   Fix: strip the BOM from the character stream before parsing (e.g. a `PushbackReader` that skips a leading `\uFEFF`), and add a test with a quoted header after a BOM.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.

## Test Report
Shared full run (all 13 tasks), see `task-3-csv-parser-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
