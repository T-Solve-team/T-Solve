# Task 27: Import type on the CSV tab

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-07

### Working on: import-type-form
- `CsvImportForm`: `Radio.Group` "Import type" (`aria-label` so the radiogroup has its name) in a `FormField` with `initialValue` `WITH_SOLUTION`; options "Tickets with a Solution" and "Tickets without a Solution" (AC-015.1). `Form.useWatch` gives the chosen type to Download template, which sends `{ importType }`; the upload sends it as a form field. The saved file name stays `ticket-import-template.csv`. Changing the type keeps the chosen file.
- Mocks: the template handler answers by the `importType` query; the upload handler stores the type in the preview it creates.
- Commit: default@ff90aa7
- Branch: `feat/KAN-31-ticket-import-csv-screens` (team branch rule).

### Deviations from the plan
- `mocks/data.ts` changed too (not in the task's file list): `CSV_TEMPLATE_WITHOUT_SOLUTION` and an `importType` parameter of `seedImport`. The mock rows stay the seed rows; Needs Solution mock rows come with the screens that use them (Tasks 28–29).

### Verification
- Node 24.22.2: `npm run format:check`, `typecheck`, `build`: pass. `lint`: 0 errors, the 1 warning that was already there (`TicketListFilters.tsx`). `test`: 80 files, 1422 of 1422 passed. `generate:api`: no generated file changed.
- New tests in `ImportTicketsPage.test.tsx` (AC-015.1 ×3): the choice and its default; the template of the chosen type; the type sent with the upload and the file kept after a change.
- `sdd.ts trace import-ticket-list STORY-015`: AC-015.1 has a test; AC-015.2–AC-015.7 belong to Tasks 28–29.
- No review in `06-evaluation` yet.

### Blockers
none

### Next up
Task 28: write the Solution of one row (`row-solution-popup`).
