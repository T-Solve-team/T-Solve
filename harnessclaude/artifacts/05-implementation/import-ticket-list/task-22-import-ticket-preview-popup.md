# Task 22: Import Ticket Preview popup

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-03

### Working on: import-ticket-preview-popup
- `DetailModal` in `shared/ui` (D15): Form-kind Modal Header, content, one Close button; exported from `shared/ui/index.ts`. The FE Lead reviews it with `CsvUpload`.
- `ImportTicketPreviewModal` (row number or null) on `useGetImportPreviewRow`: Ticket ID, Title, Description, Author, Resolved date, the five Solution sections in full (line breaks kept), Result as `StatusTag`, Error details; `LoadingState` and `ErrorState` (with Try again) inside the popup.
- Preview button per row (AC-009.4): a "Preview" column, named after SRS 5.2 field "Preview (button)". The page keeps the open row number; Close sets it back to null, so the selection and page stay as they were (AC-010.2).
- Mocks: the seed rows carry a Description; `handlers.ts` answers `GET /imports/previews/{id}/rows/{rowNo}` (404 for an unknown row or preview).

### Deviations
- New `F/lib/import-row.ts` (not in the task's file list): `SECTIONS` and `errorText` moved out of `ImportPreviewTable.tsx` so the table and the popup share them; a `.tsx` exporting them would trip `react-refresh/only-export-components`.
- New `ImportTicketPreviewModal.module.css` for `white-space: pre-wrap` (guideline 07 bans `style={{…}}`).
- `F/pages/ImportPreviewPage.tsx` gained the Preview wiring as planned; `F/mocks/data.ts` also changed (Description).
- AC-010.2 test checks the popup goes into antd's leave state (`ant-zoom-leave`), not that it leaves the DOM: jsdom runs no CSS animation, so antd's fade-out never ends (checked: `ConfirmDialog` behaves the same).

### Verify
- `sdd.ts trace import-ticket-list STORY-009 STORY-010`: 16/16 acceptance criteria have a test.
- `format:check`, `lint` (exit 0, 1 warning in `tickets/components/TicketListFilters.tsx`, not from this task), `typecheck`, `test` (57 files, 1074 passed), `build` (exit 0) pass; `generate:api` leaves no diff.
- Backend not touched, so `mvn verify` was not run.

### Commit
- default@1b6bef5

### Next up
All features of import-ticket-list are implemented; evaluate Tasks 15–22.
