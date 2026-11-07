# Task 18: CsvUpload kit component

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-01

### Working on: csv-upload-kit
- `shared/ui/CsvUpload`: `<CsvUpload name label required />` = `FormField` + `Upload.Dragger`, exported from `shared/ui/index.ts` (D12; the FE Lead reviews it).
- `beforeUpload` checks the file, puts it in the field with `form.setFields` (or puts MSG27 / MSG28 on the field and keeps nothing) and returns `Upload.LIST_IGNORE`, so nothing is ever sent (AC-011.3). The list shows the field value; remove clears it.
- `accept={{ format: '.csv', filter: 'native' }}`: the picker still offers only .csv files, but rc-upload no longer drops a non-CSV file silently on drag, so MSG27 is shown for it too.
- Required uses its own rule `{ required, type: 'object' }` with the MSG01 text: `rules.required` is a string rule, and a File failed it.
- Idle text: the SRS III.5.1 field description "One .csv file, UTF-8, at most 10 MB." (no Figma text in the repo), with `InboxOutlined`.
- TDD note: the test file was written first, but its red run was only "module not found"; the component followed straight after. The first run then failed 2 tests (the required rule, and the remove check during antd's leave animation, which ends at the 2 s motion deadline in jsdom).

### Verify
- `CsvUpload.test.tsx`: 9 tests (AC-011.1 ×2, AC-011.2 ×5, AC-011.3).
- `format:check`, `typecheck`, `lint` (1 old warning in `features/tickets`), `test` (1032), `build` pass; `generate:api` leaves no diff.
- One full run failed `UserStatusPage.test.tsx` AC-006.5 / AC-008.x (BUG-034): already logged as BUG-076 (Open, fails on main too). It passed twice alone and in a full rerun.

### Not done
- `/dev/kit` (`src/app/dev/KitPage.tsx`) does not show `CsvUpload` yet (guideline 07 says every kit component is there). `src/app/` is the FE Lead's; ask in the PR.

### Commit
- default@09ab2ee

### Next up
Task 19 import-tickets-screen
