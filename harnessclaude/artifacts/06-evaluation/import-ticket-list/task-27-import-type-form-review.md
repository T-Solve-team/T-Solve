---
module: import-ticket-list
feature: import-type-form
task: 27
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 27: Import type on the CSV tab

## Summary
Commit `ff90aa7` (repo `default`, range `ff90aa7^..ff90aa7`). I checked it at HEAD `6267856`, where tasks 28–29 do not touch `CsvImportForm.tsx`.

- **`CsvImportForm`** adds an "Import type" `Radio.Group` inside a kit `FormField`. Its initial value is `WITH_SOLUTION`, and its labels are "Tickets with a Solution" / "Tickets without a Solution" (`frontend/src/features/ticket-import/components/CsvImportForm.tsx:21-24,93-99`).
- **Download template** reads the watched type and calls `downloadCsvTemplate({ importType })` (`:33,47-51,100-106`). The file name stays `ticket-import-template.csv` (AC-008.3).
- **Upload** sends the form values, which now include `importType`, as the multipart body (`:57-63`).
- **Mocks:** the template handler returns the 5-column header for `WITHOUT_SOLUTION` (`mocks/handlers.ts:51`). The upload handler reads `importType` and seeds the preview of that type (`:62`, `mocks/data.ts`).

The AC has three named tests, and the isolated run is green. I found no CRITICAL issue. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-015.1, part by part. The choice is shown with "Tickets with a Solution" selected (`:96`). The template of the chosen type is downloaded: the request carries `importType` (`:103`), and the test checks both the query parameter and the saved content. The chosen type is sent with the upload: `UploadCsvImportBody` is the form's value type, and the test reads `importType` from the posted FormData. Changing the type keeps the chosen file (test `:154`, design "Import type"). |
| Completeness | pass | AC-015.1 is traced to 3 tests. The default type is covered too: the AC-008.3 test (`ImportTicketsPage.test.tsx:91`) matches only the with-Solution header. Nothing outside the spec was added. |
| Correctness | pass | Isolated run: 1442/1442 tests and 80/80 files, exit 0. Lint (0 errors), typecheck, format:check and build pass. `generate:api` left no diff. |
| Integration | pass | It uses the generated `downloadCsvTemplate` and `useUploadCsvImport` with the contract's `importType` (`GET /imports/csv/template`, `POST /imports/csv`; spec Interface Contracts). Backend default `WITH_SOLUTION` (AC-012.1) and the web default agree. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-015` → `7/7 acceptance criteria have a test`. The AC of this task (`frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx`):

| AC | Test | Status |
|---|---|---|
| AC-015.1 | `:114` the radio group "Import type", with "Tickets with a Solution" checked and the other unchecked | PASS |
| AC-015.1 | `:124` "Tickets without a Solution" + Download template → `importType=WITHOUT_SOLUTION`, `ticket-import-template.csv`, the 5-column header | PASS |
| AC-015.1 | `:154` the upload posts `importType=WITHOUT_SOLUTION`; `tickets.csv` is still listed after the type changes | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block): none.

## Assumptions made
- The first full vitest run failed (6 tests, 4 suites). It ran at the same time as my lint, typecheck and build. Every failure was a 10 s hook or 15 s test timeout, plus one garbled-typing assertion, and none touched this task's tests. I re-ran the suite once alone (run-tests step 5), and it passed 1442/1442. I judge on that run. The `setup.ts` hook timeout is the open BUG-105.
- The spec gives no position for the Import type field. I accept it between Project and Download template, as in design "Web app".

## Test Report
See `task-27-import-type-form-test-report.md`. format:check, lint, typecheck and build each exit 0. Isolated vitest: 1442/1442 tests in 80/80 files, exit 0. `generate:api` left no diff. Trace: 7/7.
