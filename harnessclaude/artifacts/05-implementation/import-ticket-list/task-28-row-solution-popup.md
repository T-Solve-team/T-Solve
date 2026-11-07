# Task 28: Write the Solution of one row

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-07

### Working on: row-solution-popup
- `SolutionFormModal` (new) on `FormModal`, titled "Import Ticket Preview" (SRS 5.3): Ticket ID, Title, Description, Author and Resolved date read-only; the five Solution sections as fields (Workaround optional, the others `rules.required`); Apply (AC-015.2). The fields mount once the row is read, each starting from the row's saved section, so a reload shows what was written (edge case 51).
- `useFillPreviewSolution`: `handlesOwnErrors`; on success it refetches the Summary, every page of rows and each written row. A 400 with field errors goes under the fields and the popup keeps what was written (AC-015.3); other errors through `showMessage`; a 401 is left to the global MSG07 dialog.
- `ImportPreviewPage`: on a file without Solutions, Preview on a Needs Solution or Valid row opens `SolutionFormModal`; every other row opens `ImportTicketPreviewModal` as before. StatCard "Needs Solution" only on a file without Solutions (AC-015.7).
- Mocks: a file without Solutions seeds its rows without errors as `NEEDS_SOLUTION` with empty Solution columns; `fill-solution` handler like the backend (404, 409 MSG06, all rows or none); `seedFileWithoutSolutions()` for tests.
- Commit: default@34218f0
- Branch: `feat/KAN-31-ticket-import-csv-screens` (team branch rule).

### Deviations from the plan
- `ImportPreviewTable` changed here, not only in Task 29: `onPreview` passes the row, so the page knows the row's status.
- `status-tags.ts` unchanged: `NEEDS_SOLUTION` was added in Task 23.
- The popup keeps the SRS name "Import Ticket Preview"; the spec names no other popup.
- The first `sdd.ts commit` run crashed in Bun before writing; the second run recorded it once.

### Verification
- Node 24.22.2: `npm run format:check`, `lint`, `typecheck`, `build`: pass. `test`: 1429 of 1429 passed. `generate:api`: no generated file changed.
- New tests in `ImportPreviewPage.test.tsx` (+7): Needs Solution count and Result (AC-015.7 ×2); editable sections with Apply, Apply saves and refreshes, the Solution shown again after a reload, an Error row stays read-only (AC-015.2 ×4); a field error from the backend keeps the popup and its text (AC-015.3).
- `sdd.ts trace import-ticket-list STORY-015`: AC-015.1, .2, .3, .7 have tests; AC-015.4–.6 belong to Task 29.
- No review in `06-evaluation` yet.

### Blockers
none

### Next up
Task 29: Fill solution and the Confirm lock on screen (`fill-solution-screen`).
