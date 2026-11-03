# Task 20: Import Preview screen — Summary, rows and states

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-01

### Working on: import-preview-screen
- `ImportPreviewPage`: `?previewId` must match `/^[1-9]\d*$/`, else `<Navigate to={paths.importTickets} replace>` (AC-009.11, edge case 33). The inner `ImportPreview` is keyed by the id, so a new id starts with an empty selection.
- Summary: `useGetImportPreview` → file name, `StatCard` Valid / Error / Removed, `Alert` (warning) with `message` (MSG30) when the code is in the catalog. `LoadingState` while pending.
- Error state (AC-009.12): 404 → `ErrorState` with MSG04 (the backend sends 404 without a code, so `http()` would make it MSG06); 403 → the error as is (MSG08). Neither gets Try again — a retry cannot change them; other errors keep Try again.
- `ImportPreviewTable` on `useImportPreviewRows(previewId, page)` (`hooks/useImportPreview.ts`, page from 1 → API page from 0, size 20). Local page state (D11). Columns: Ticket ID, Title, Author, Resolved date (as written in the file — the API returns it so, and invalid dates must stay visible), Solution (five labelled sections, each one line with "…" and a tooltip), Result (`StatusTag`), Error details (`msg(code, params)`, unknown code → MSG06, edge case 38). Solution and Error details turn off the table's one-line ellipsis.
- Selection: `rowKey` = `rowNo` (ticket ids can repeat), checkbox `aria-label` "Select row N", Removed rows disabled, `preserveSelectedRowKeys` across pages; the selection lives in the page for Task 21's Remove.
- Mocks: `data.ts` now keeps every row (`MockPreview`) with `rowPage()` and `previewResponse()`; 25 seed rows (20 Valid, 4 Error, 1 Removed) so the list has two pages. Handlers for `GET /imports/previews/:id` and `/rows` (404 for any id but the current one); upload makes a new preview with the next id.

### Deviation
- No Preview button per row yet. The plan has it set the selected row until Task 22, but without the popup that state has no reader (and an unused variable fails lint). Task 22 adds the button with the popup (AC-009.4).

### Verify
- `ImportPreviewPage.test.tsx`: 15 tests (AC-009.1 ×2, AC-009.2 ×4, AC-009.11 ×5, AC-009.12 ×2, AC-009.13 ×2).
- `sdd.ts trace import-ticket-list STORY-009`: 5/13 — AC-009.1, .2, .11, .12, .13 (this task's); AC-009.3 – .10 belong to Task 21 / Task 22.
- `format:check`, `lint` (exit 0), `typecheck`, `test` (1060 passed), `build` (exit 0) pass; `generate:api` leaves no diff.

### Commit
- default@84adf7e

### Next up
Task 21 import-preview-actions

## Session 2026-11-04

### Working on: import-preview-screen — Low findings of review attempt 1
- Review issue 1 → BUG-107: the mocks answer a gone preview or row with 404 MSG04, like the backend; the page no longer remaps 404 itself. Shared commit with Task 21 (same page and test file).
- Verify: `mvn verify` BUILD SUCCESS (235 unit tests, 1 skipped as before; 10 ITs; spotless) with JDK 24 and `-Dapi.version=1.44` for Docker Desktop 29; frontend `format:check`, `lint` (0 errors, the old `TicketListFilters.tsx` warning), `typecheck`, `test` (58 files, 1083 passed), `build` pass; `generate:api` leaves no diff.
- Commit: default@11c35cc

### Next up
Module completion
