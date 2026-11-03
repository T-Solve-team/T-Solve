---
module: import-ticket-list
feature: import-tickets-screen
task: 19
verdict: pass
attempt: 1
date: 2026-11-03
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 19: Import Tickets screen (CSV tab)

## Summary
Commit `9e8aacb` (repo `default`, range `9e8aacb^..9e8aacb`) builds 5.1 Import Tickets. The pieces are:
- `ImportTicketsPage` shows the Jira, CSV and Manual entry tabs; only CSV is active.
- `CsvImportForm` has the Project select, Download template, `CsvUpload` and Upload.
- `lib/save-text-file.ts` saves the template.
- Mock data and handlers for the upload and template endpoints.

Later commits (`84adf7e`, `55a1ee6`, `1b6bef5`) change only `mocks/data.ts` and `mocks/handlers.ts` among these files. They add the preview, rows, remove and confirm mocks and leave the upload and template handlers as they were. I reviewed the code at HEAD `15d346c`. All eight STORY-008 criteria have tests that check them, and the shared verify run is green. The findings below are Low only. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-008.1: `ImportTicketsPage.tsx:7-16` has a fixed `activeKey="csv"`, and Jira and Manual entry are `disabled`. AC-008.2: `CsvImportForm.tsx:31-36` filters `session.projects` by `roleInProject === 'PROJECT_MANAGER'`, labels each as `<jiraProjectKey> · <departmentName>` and uses `projectId` (UUID) as the value. AC-008.3: `downloadCsvTemplate` with `Accept: text/csv` is read as text by `http()` (`readBody`), then `saveTextFile(text, 'ticket-import-template.csv')` saves it. AC-008.4: `rules.required('Project')` and the required `CsvUpload` show MSG01 at each field, and `onFinish` does not run. AC-008.5: the `CsvUpload` kit (Task 18) gives MSG27 or MSG28 at the field and does not keep the file. AC-008.6: on success it navigates to `/import/preview?previewId=<id>` (:58-59). AC-008.7: field codes (MSG27, MSG28) go to `form.setFields` on `file`; inline codes (MSG29 with `column_names`, MSG04, MSG08) go to an `Alert` (:60-66). The form values are not reset, so the Project and file stay. AC-008.8: `loading={upload.isPending}` plus the `isSending` ref (:46-57) block a second request on a double click. |
| Completeness | pass | D13 is met: Jira and Manual entry are disabled, with no Drafts and no "Save as draft". Design edge cases 34 (double click) and 39 (the PM manages no Project) are tested. Nothing EXTRA was added: the route was already in `routes.ts` with `roles: ['PROJECT_MANAGER']`. |
| Correctness | pass | `ImportTicketsPage.test.tsx`: 13 test cases (9 `it`, two `it.each` × 2), all passing. Full vitest: 1074/1074. typecheck, lint (0 errors), format:check, build and generate:api (no diff) all pass. No CRITICAL finding. |
| Integration | pass | It uses only the generated `useUploadCsvImport` and `downloadCsvTemplate` from `@/shared/api`, with no hand-written fetch. `UploadCsvImportBody { file, projectId: string }` matches the UUID contract (D10). `ApiError.code` is always a known catalog code (`errors.ts:66-78`), so `MESSAGES[error.code].type` (:61) cannot crash. The MSW handlers follow guideline 10 §5 and are reset after each test. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-008 STORY-011`:

```
  ✓ AC-008.1 … AC-008.8  frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx
  ✗ AC-011.1–011.3  ambiguous (fe-foundation) — see task-18-csv-upload-kit-review.md
trace import-ticket-list (STORY-008, STORY-011): 8/11 acceptance criteria have a test
```

| AC | Test (`ImportTicketsPage.test.tsx`) | Status |
|---|---|---|
| AC-008.1 | tabs with CSV selected and the others disabled (:60) | PASS |
| AC-008.2 | only managed Projects with Jira key and Department; a MEMBER Project is hidden (:70) | PASS |
| AC-008.3 | the endpoint's text is saved as `ticket-import-template.csv`, and the URL is revoked (:88) | PASS |
| AC-008.4 | MSG01 at both fields, no request (:111); no managed Project gives MSG01 at Project (:122) | PASS |
| AC-008.5 | MSG27 at the field and `.xlsx` not kept (:134). The MSG28 half is covered only by `CsvUpload.test.tsx:101` (issue 2) | PASS |
| AC-008.6 | navigates to `/import/preview?previewId=<n>` (:143) | PASS |
| AC-008.7 | MSG29 with columns, stays, Project and file kept (:154); 400 MSG04 and 403 MSG08 shown above the form (:176); 400 MSG27 and 413 MSG28 shown at the field (:191) | PASS |
| AC-008.8 | loading, and one request for a double click (:208) | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `frontend/src/features/ticket-import/components/CsvImportForm.tsx:60-66`: a 401 on Upload shows two messages. The global handler opens the MSG07 dialog (`query-client.ts:44-46`), and the mutate-level `onError` still runs, putting MSG07 into the `Alert` (MSG07 is type `confirm`, so it takes the `else` branch). Guideline 06 §6 says: "Never show two messages for one error." Expected: only the MSG07 dialog. Fix: `if (error.status === 401) return;` at the top of `onError`. Add a test for it (no `alert` after a 401).
2. [Low] `frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx:134`: the screen test for AC-008.5 covers only the MSG27 half. The MSG28 half ("larger than 10 MB → MSG28 at the field, not kept") is proven only by the kit test `CsvUpload.test.tsx:101`. Because the screen uses that component, the behaviour is covered. Fix (optional): turn :134 into an `it.each` over `tickets.xlsx` → MSG27 and a 10 MB + 1 byte `big.csv` → MSG28.
3. [Low] `ImportTicketsPage.test.tsx:143`: the AC-008.6 test checks only the navigation. It does not check that the chosen Project's `projectId` and the file were the ones sent. If the select's value mapping (`CsvImportForm.tsx:34`) broke, the test would still pass. Fix: in a `server.use` handler, read `request.formData()` and assert `projectId === '<ITSUP uuid>'` and `file.name === 'tickets.csv'`.
4. [Low] `frontend/src/features/ticket-import/lib/save-text-file.ts:7-8`: `URL.revokeObjectURL(url)` runs right after `link.click()`. Some browsers (older Firefox and Safari) start the download asynchronously and can then fail with a revoked URL. Fix: `setTimeout(() => URL.revokeObjectURL(url), 0)`. The AC-008.3 test can then wait for the revoke.

## Assumptions made
- AC-008.7 "the message the backend returned": a 5xx or network error (MSG06) also goes to the `Alert`, because `handlesOwnErrors` turns off the global toast. I count this as acceptable: the spec lists only the business codes and is silent on MSG06.
- The upload-rejection `Alert` stays visible after the user changes the Project or file, until the next Upload (`CsvImportForm.tsx:29,51`). The spec is silent, so this is not counted as a finding.
- AC-008.1 "opens Import Tickets": I read it as the `/import` route (`?tab=` is ignored; the CSV tab is always active, per D13).

## Test Report
`task-19-import-tickets-screen-test-report.md`: the shared run at HEAD `15d346c` (second run; the first was invalid and is ignored). format:check, lint (0 errors), typecheck, vitest 1074/1074, build, and generate:api with no diff, all pass (`frontend exit=0`). `ImportTicketsPage.test.tsx` passed in 22.3 s. Coverage not measured (spec silent).
