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

## Session 2026-11-04

### Working on: import-tickets-screen — Low findings of review attempt 1
- Review issue 1 → BUG-125: `CsvImportForm` ignores a 401 in its `onError`; the global MSG07 dialog is the one message.
- Review issue 4 → BUG-126: `saveTextFile` revokes the Blob URL on the next task.
- Review issues 2 and 3: AC-008.5 covers MSG28 on the screen; AC-008.6 checks the Project id and file content that were sent (jsdom files reach the request named "blob", so the content is compared).
- Verify: `mvn verify` BUILD SUCCESS (235 unit tests, 1 skipped as before; 10 ITs; spotless) with JDK 24 and `-Dapi.version=1.44` for Docker Desktop 29; frontend `format:check`, `lint` (0 errors, the old `TicketListFilters.tsx` warning), `typecheck`, `test` (58 files, 1083 passed), `build` pass; `generate:api` leaves no diff.
- Commit: default@653111a

### Next up
Module completion
