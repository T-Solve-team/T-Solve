---
module: wiki
feature: wiki-upload-ui
task: 8
verdict: fail
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 8: Upload Documents screen (commit 6bd9d92d)

## Summary
This review checks the code against AC-004.18 to AC-004.23 and against `frontend/AGENTS.md`. It reads only `git show 6bd9d92d`.

The behaviour matches the spec:
- The folder tree selector defaults to the folder in the URL.
- New rows take the Department visibility by default.
- The client checks file count, extension and size.
- The extract call returns one row per file, in order: File name, Visibility, Title, Extracted text, Words, Result (Ready or MSG90) and Remove.
- Publish is disabled when a row is UNREADABLE, a title is blank or over 200 characters, or no row is left.
- A successful publish shows MSG95 with the count and returns to 11.1 on the target folder.
- MSG84, MSG01 and MSG02 are shown on the affected row, and every row is kept.
- Cancel sends no request.
- A second click on Publish cannot send the request twice.

Each AC has `it('AC-004.x …')` tests.

**Verdict fail, on one major finding.** The screen uses raw antd `Upload`, which is banned outside `shared/ui` by a 🔒 rule (AGENTS.md rule 6, guideline 07 U1). The code silences the lint rule with `eslint-disable`, the only such disable in `src/`.

The plan (tasks.md, "AntD `Upload`") conflicts with the team rule, and the team rule wins. The way out goes through the FE Lead:
- Guideline 09: the FE Lead builds the upload kit components before the first screen that needs them.
- Rule 10: a change to `shared/` needs the FE Lead's review.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-004.18 to AC-004.23 behave as written |
| Completeness | pass | All six ACs have named tests, including the error paths and the guard against a double publish |
| Correctness | fail | A locked team rule is bypassed (issue 1). The reviewer ran no tests |
| Integration | pass | Uses the generated `useExtractWikiUploads` and `usePublishWikiDocuments`. Metadata goes as the JSON string part the backend accepts (BUG-229) |

## Traceability
```
trace wiki (STORY-004): 23/23 acceptance criteria have a test
```
AC-004.18 to AC-004.23 are tested in `frontend/src/features/wiki/pages/UploadDocumentsPage.test.tsx`.

## Issues
1. `frontend/src/features/wiki/components/WikiFileUpload.tsx:2`: major: the feature imports raw antd `Upload` under `// eslint-disable-next-line no-restricted-imports`. This bypasses the 🔒 rule (AGENTS.md rule 6, guideline 07 U1, guideline 11). Fix, either of:
   - add a kit uploader in `src/shared/ui/` (for example a generic `FileDropzone`, built like `CsvUpload`), with the FE Lead's review
   - get an FE Lead exception recorded in `guidelines/13-decision-log.md`

   Then remove the disable.
2. `frontend/src/features/wiki/pages/UploadDocumentsPage.tsx:44`: minor: when the total passes 10 files, the whole selection is rejected, including the files that would still fit. Invalid files also count toward the limit. This matches one reading of "add none of the extra files" (see A2). Fix, if the user chooses the other reading: add the first `10 - rows.length` valid files and show MSG88.
3. `frontend/src/features/wiki/pages/UploadDocumentsPage.tsx:95`: minor: row errors from the server are shown with the server's params. Their `field_name` is `documents[0].title`, so a server MSG01 or MSG02 would read "documents[0].title …". The test mocks `field_name: 'Title'`, which hides this. It happens only when the client checks are bypassed. Fix: render the message with `{ ...fieldError.params, field_name: 'Title' }`.

## Assumptions made
- **A1 (open judgment call).** Issue 1 is rated major because the rule is locked and was silenced instead of escalated. If the user or the FE Lead accepts the import as a recorded exception, issue 1 drops to minor and Task 8 passes on everything else.
- **A2 (open judgment call).** AC-004.19 says "add none of the extra files". This review reads it as "reject the whole selection that goes over the limit" and accepts the code as compliant. The user may prefer "add files up to 10".
- **A3.** `npm` was not run, per the dispatcher.
- **A4.** The feature status was not changed, per the dispatcher. For this fail, run `bun harnessclaude/hooks/sdd.ts feature wiki wiki-upload-ui fixing`.

## Test Report
See `task-8-wiki-upload-ui-test-report.md`.
