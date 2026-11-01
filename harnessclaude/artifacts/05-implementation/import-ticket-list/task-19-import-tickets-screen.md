# Task 19: Import Tickets screen — CSV tab

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-01

### Working on: import-tickets-screen
- `ImportTicketsPage`: `SectionCard` + antd `Tabs` Jira · CSV · Manual entry, `activeKey="csv"` fixed; Jira and Manual entry `disabled` (D13). The page does not read `?tab=` — CSV is the only working tab, so there is nothing to switch to yet.
- `CsvImportForm` (`AppForm<UploadCsvImportBody>`): Project select from `useSession().projects` filtered by `roleInProject === 'PROJECT_MANAGER'`, label `<jiraProjectKey> · <departmentName>`, `rules.required('Project')`; Download template as a link button; `<CsvUpload name="file" label="CSV file" required />`; Upload (`loading={upload.isPending}`).
- Download template: a `useMutation` around `downloadCsvTemplate({ headers: { Accept: 'text/csv' } })` (errors go to the global toast), then `lib/save-text-file.ts` (Blob URL, hidden link, revoke).
- Upload: `useUploadCsvImport` with `meta.handlesOwnErrors`; success → `/import/preview?previewId=<id>`. Error routing by the catalog type of the code: `field` (MSG27, MSG28) → `form.setFields` on `file`; otherwise (MSG29 with `column_names`, MSG04, MSG08) → `Alert` above the form. The form keeps Project and file.
- Double click (edge case 34): the first test run showed two uploads — both clicks pass antd's async validation before `isPending` renders. Fixed with an `isSending` ref set before `mutate` and cleared in `onSettled`.
- Mocks: `mocks/data.ts` holds `CSV_TEMPLATE` (the order of `CsvColumns.TEMPLATE`) and `seedPreview()` (6 rows: Valid, Error MSG01, two Error `MSG_NEW_3`, Error MSG31, Removed; MSG30 message). `handlers.ts`: the generated template handler with that text; `POST /imports/csv` replaces the in-memory preview with a new id; a file name containing `missing-columns` answers MSG29, `empty` answers MSG04 (dev mode). `resetMockData()` restores the seed.
- Edge case 39 (no managed Project): tested by overriding `/me` with `projects: []` — Upload shows MSG01 at Project and sends nothing.

### Verify
- `ImportTicketsPage.test.tsx`: 13 tests (AC-008.1 – AC-008.8).
- `sdd.ts trace import-ticket-list STORY-008`: 8/8 acceptance criteria have a test.
- `format:check`, `lint` (exit 0; 1 old warning in `features/tickets`), `typecheck`, `test` (1045 passed), `build` (exit 0) pass; `generate:api` leaves no diff.

### Commit
- default@9e8aacb

### Next up
Task 20 import-preview-screen
