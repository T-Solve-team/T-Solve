---
module: import-ticket-list
feature: csv-upload
task: 6
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 6: Upload creates the preview

## Summary
`CsvImportServiceImpl.upload` follows the design order (extension → size → Project access → parse → one existing-ID query → validate → replace the caller's preview → save rows) and returns counts, MSG30 when any row is Error, and the first page of 50 rows with 200-character Solution snippets. A rejected file keeps the previous preview. All ACs of the task have checking tests.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.1 `CsvImportServiceImpl.java:83-108`; AC-001.2 `:72-75`; AC-001.4 `:76-78`; AC-001.9 `:79-81` (stub requires managed **and** Active); AC-001.11 `:87` (bulk JPQL delete, rows by DB cascade); AC-002.9 `ImportPreviewResponse.java:26`. |
| Completeness | pass | Edge cases 8 and 10 covered. |
| Correctness | pass | 12/12 tests pass; `ImportConfirmIT` runs real uploads against PostgreSQL. |
| Integration | pass | Response matches the contract's `ImportPreviewResponse` (`rows` as `PagedModel`). |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

| AC | Test | Status |
|---|---|---|
| AC-001.1 | createsPreviewWithCountsAndFirstPage / firstPageHoldsFiftyRows `_AC_001_1` | PASS |
| AC-001.2 | rejectsFileNotEndingInCsv / acceptsUpperCaseExtension `_AC_001_2` | PASS |
| AC-001.4 | rejectsFileOverTenMegabytes_AC_001_4 | PASS |
| AC-001.9 | rejectsProjectTheCallerDoesNotManage_AC_001_9 (+ StubProjectAccessTest.inactiveProjectIsNeitherActiveNorManaged) | PASS |
| AC-001.11 | replacesTheCallersPreviousPreview / rejectedFileKeepsThePreviousPreview `_AC_001_11` | PASS (mocks only) |
| AC-002.9 | reportsErrorRowCountWithMsg30_AC_002_9 | PASS |

## Issues
CRITICAL: none.

Informational:
1. [Medium] `backend/src/main/java/vn/tsolve/ticket/service/impl/CsvImportServiceImpl.java:85` — all Ticket IDs go into one `IN (...)` (`TicketRepository.java:13`). A ≤ 10 MB file with more than 65,535 distinct IDs (short rows, ~140 bytes each) exceeds PostgreSQL's bind-parameter limit and returns 500 instead of a preview (AC-001.1 says any valid file ≤ 10 MB gets one).
   Fix: query in chunks (e.g. 1,000 IDs) or bind one array (`ticket_id = any(:ids)`); add a test with > 65,535 rows or at least a chunking unit test.
2. [Low] `CsvImportServiceImpl.java:72,89` — the client's file name goes unchecked into `import_preview.file_name VARCHAR(255)`; a longer name gives an unhandled DB error (500).
   Fix: reject (MSG27) or cut names over 255 characters.
3. [Low] `CsvImportServiceImpl.java:87-89` — two concurrent uploads by the same PM both delete, then both insert; the unique owner index makes one fail with an unhandled 500.
   Fix: map the unique-owner violation to a retry or a 409, or lock per owner.
4. [Low] AC-001.11 is proved only with mocks; no real-DB test checks the replace path against `uq_import_preview_owner` and the row cascade.
   Fix: add an `ImportConfirmIT`-style case: upload twice as one user, assert one preview and only the new rows.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.

## Test Report
Shared full run (all 13 tasks), see `task-6-csv-upload-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
