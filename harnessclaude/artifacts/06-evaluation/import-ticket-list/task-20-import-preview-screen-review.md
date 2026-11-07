---
module: import-ticket-list
feature: import-preview-screen
task: 20
verdict: pass
attempt: 1
date: 2026-11-03
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 20: Import Preview screen — Summary, rows and states

## Summary
Commit `84adf7e` (repo `default`, range `84adf7e^..84adf7e`). Later commits `55a1ee6` and `1b6bef5` changed the same files, so this review reads the code at HEAD (`15d346c`, whose code is the same as `1b6bef5`). `ImportPreviewPage` sends any `previewId` that is not `^[1-9]\d*$` to Import Tickets (`ImportPreviewPage.tsx:17`). It reads the Summary with `useGetImportPreview`, shows the loading state, and shows 403 / 404 as `ErrorState` with no Try again, where 404 is shown as MSG04 (`:42-53`). The Summary has the file name, Valid / Error / Removed `StatCard`s and an MSG30 `Alert` (`:83-95`). `ImportPreviewTable` lists 20 rows per page with every AC-009.2 column. Error details come from the catalog, and a code the catalog does not know shows MSG06 (`lib/import-row.ts:14-16`). Every task AC has a named test, and the shared verify run at HEAD is green (1074/1074). I found no CRITICAL issue. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-009.1: `ImportPreviewPage.tsx:80-95`. AC-009.2: `ImportPreviewTable.tsx:8-70`, with a checkbox, Ticket ID, Title, Author, Resolved date, the Solution sections as returned, Result `StatusTag` and Error details through `errorText`. Rows are paged through `useImportPreviewRows` (`hooks/useImportPreview.ts:13-15`, 0-based `page`, `PAGE_SIZE` 20 ≤ 100). AC-009.11: `:17`, `<Navigate replace>`. AC-009.12: `:43-53`. AC-009.13: the page shows `LoadingState` (`:42`), and the table uses `loading={rows.isPending}` (`ImportPreviewTable.tsx:98`). |
| Completeness | pass | All five ACs of the task are traced to tests (below). Design edge cases 33 (bad `previewId`) and 38 (unknown code → MSG06) are tested. Empty and error states of the rows come from `DataTable` (guideline 05 C9). |
| Correctness | pass | `ImportPreviewPage.test.tsx` passes in the shared run (57/57 files, 1074/1074 tests). format:check, lint (0 errors), typecheck and build pass. `generate:api` leaves no diff. |
| Integration | pass | It uses the generated `useGetImportPreview` / `useGetImportPreviewRows` from `@/shared/api` (STORY-007 and AC-002.8 endpoints). It uses only kit components (`DataTable`, `StatCard`, `SectionCard`, `ErrorState`, `LoadingState`) and has no raw antd `Table` or `Tag`. Mock handlers for every preview path are in `mocks/handlers.ts`. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-009 STORY-010` → `16/16 acceptance criteria have a test`. The ACs of this task:

| AC | Test (`frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx`) | Status |
|---|---|---|
| AC-009.1 | `:50` Summary with file name, counts and MSG30 · `:64` no MSG30 when no row is Error | PASS |
| AC-009.2 | `:74` columns and row content (Valid, MSG01, MSG_NEW_3, MSG31, Removed) · `:107` unknown code → MSG06 · `:121` 20 per page, page 2 | PASS |
| AC-009.11 | `:140` `it.each` for missing, `abc`, `0`, `-3`, `1.5` → `/import` | PASS |
| AC-009.12 | `:150` 404 → MSG04, no table · `:156` 403 → MSG08, no table | PASS |
| AC-009.13 | `:163` Summary loading · `:174` rows page loading (table spinner) | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `frontend/src/features/ticket-import/mocks/handlers.ts:60,66,78,85,98` (and the comment at `pages/ImportPreviewPage.tsx:45`): the mock answers a 404 with no code. The real backend answers 404 with code MSG04 (`backend/.../GlobalExceptionHandler.java:71-74`, `NO_RESULTS = "MSG04"`). So in mock mode the rows table and the popup show MSG06 for a preview that no longer exists, while the real app shows MSG04. The comment "404 comes without a code" is wrong, and the remap at `:49` is harmless but not needed. Fix: use `problem(404, 'MSG04')` in the mock handlers and correct the comment.

## Assumptions made
- AC-009.11 "a positive whole number": I accept that `?previewId=01` (leading zero) also opens Import Tickets. The test covers the forms named in design edge case 33.
- AC-009.2 "Solution (the sections … as returned)": a section that is empty (for example a missing Root cause on an Error row) is not shown in the cell (`ImportPreviewTable.tsx:33`). I read "as returned" as allowing this.
- I judged only by the shared verify run of the main session at HEAD `15d346c`, as I was told. I did not run the suites myself. The first shared run was invalid (environment) and I did not use it.

## Test Report
`task-20-import-preview-screen-test-report.md`: frontend verify exit 0. The run covered format:check, lint (0 errors and 1 warning in an unrelated file), typecheck, vitest 57/57 files and 1074/1074 tests, and build. `generate:api` left no diff. Coverage was not reported.
