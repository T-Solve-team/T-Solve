---
module: wiki
feature: wiki-upload-ui
task: 8
verdict: pass
attempt: 2
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 8: Upload Documents screen (commits 6bd9d92d, fix 186f3b38)

## Summary
Attempt 2 re-checks AC-004.18 to AC-004.23 and the frontend rules on the current branch (`feat/KAN-163-wiki-space-permissions`). It reads `git show 6bd9d92d 186f3b38` and the current files.

- **Attempt-1 issue 1 (major, raw antd `Upload` under `eslint-disable`) is resolved.**
  - `features/wiki/components/WikiFileUpload.tsx` is deleted.
  - The dropzone is now the kit component `frontend/src/shared/ui/FileDropzone/FileDropzone.tsx`, exported from `shared/ui/index.ts`. It is generic (label, accept, hint, disabled, onAdd) and built the same way as `CsvUpload`.
  - `features/wiki` has no `eslint-disable`, and no `Upload` import from antd.
  - The kit component has its own tests (BUG-276).
- **Attempt-1 issue 3 (server field name) is resolved.** `UploadDocumentsPage.tsx:98` overrides `field_name` with `Title`. The AC-004.22 test now mocks the server's real `documents[0].title` path (BUG-277).
- **Attempt-1 issue 2 (10-file limit reading) still stands, unchanged.** It is a spec reading, not a defect (A2).

The behaviour verified in attempt 1 has not regressed:
- the folder default
- Department as the default visibility
- the client checks MSG88 and MSG89
- the extract rows
- the Publish disable rules
- MSG95 and the return to 11.1
- row errors that keep every row
- Cancel without a request
- the guard against a double publish

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-004.18 to AC-004.23 behave as written. Attempt-1 issues 1 and 3 are fixed |
| Completeness | pass | All six ACs have named tests. FileDropzone has its own unit tests |
| Correctness | pass | The suite is green (main session evidence plus the reviewer's own runs). The open flaky test BUG-278 did not reproduce in 2 runs |
| Integration | pass | Generated `useExtractWikiUploads` and `usePublishWikiDocuments`. The kit component goes through `@/shared/ui` |

## Traceability
```
✓ AC-004.18  frontend/src/features/wiki/pages/UploadDocumentsPage.test.tsx
✓ AC-004.19  frontend/src/features/wiki/pages/UploadDocumentsPage.test.tsx
✓ AC-004.20  frontend/src/features/wiki/pages/UploadDocumentsPage.test.tsx
✓ AC-004.21  frontend/src/features/wiki/pages/UploadDocumentsPage.test.tsx
✓ AC-004.22  frontend/src/features/wiki/pages/UploadDocumentsPage.test.tsx
✓ AC-004.23  frontend/src/features/wiki/pages/UploadDocumentsPage.test.tsx
trace wiki (STORY-004): 23/23 acceptance criteria have a test
```

## Issues
No blocking issues. Minor and informational:

1. `frontend/src/features/wiki/pages/UploadDocumentsPage.tsx:43`: minor, carried over from attempt-1 issue 2.
   - When the total passes 10 files, the whole selection is rejected, including files that would still fit.
   - Invalid files (wrong type or over 10 MB) also count toward the limit before they are filtered out. Ten valid rows plus one 20 MB file therefore gives MSG88, not MSG89.
   - This matches the reading "add none of the extra files" (A2).
   - Fix, only if the user picks the other reading: run the type and size check first, then apply the limit to the valid files.
2. `frontend/src/shared/ui/FileDropzone/FileDropzone.tsx:1`: process, not code. This is a new `src/shared/ui` component, and rule 10 requires the FE Lead's review. The bug log (BUG-276) records it as pending. Also, `frontend/guidelines/07-*.md:64` (the kit table row "Web/Upload") does not list `FileDropzone`. Fix: the FE Lead reviews the component in the PR, and the kit table row gets `FileDropzone`.
3. `frontend/src/features/wiki/pages/UploadDocumentsPage.test.tsx:291`: minor. The stateful test "AC-004.22 uses stateful mocks…" is logged as flaky (BUG-278, Open; 3 of 4 runs failed at the time it was logged). It passed in the main session's full run and in 2 isolated reviewer runs. Fix: track it under BUG-278 or BUG-262 (the same cause: Wiki Home queries outlive the `findBy` timeout).
4. `docs/bug-log.md:294`: nit. The BUG-278 row reads "Found while verifying BUG-276/248". The renumbered id is BUG-277. Fix: change 248 to 277.

## Assumptions made
- **A1 (open judgment call).** Moving the uploader to `shared/ui/FileDropzone` is accepted as the fix for attempt-1 issue 1. It still needs the FE Lead's review (rule 10). This review does not block on that review; the PR does.
- **A2 (open judgment call, carried over).** AC-004.19 "add none of the extra files" is read as "reject the whole selection that goes over the limit". Counting invalid files toward the limit is also accepted. The user may prefer "add valid files up to 10".
- **A3 (open judgment call).** The flaky stateful test (BUG-278, Open) does not fail the verdict, because it passed in every run seen today. If the team treats any known-flaky test as a failing test, the verdict becomes fail.
- **A4.** The main session's checks were taken as given, as the dispatcher allowed: format, lint, typecheck, full vitest 119 files / 1880 tests, build, and `generate:api` with no diff. The reviewer reran only `UploadDocumentsPage.test.tsx` and `FileDropzone` twice: 19/19 passed both times.
- **A5.** The feature status was not changed, per the dispatcher. For this pass, run `bun harnessclaude/hooks/sdd.ts feature wiki wiki-upload-ui passing`.

## Test Report
- Main session, today on this branch:
  - `npm run format:check`: pass
  - `lint`: 0 errors, 1 unrelated warning in `tickets/TicketListFilters.tsx`
  - `typecheck`: pass
  - `vitest run`: 119 files, 1880 tests, all pass
  - `build`: pass
  - `generate:api`: no diff
- Reviewer: `npx vitest run src/features/wiki/pages/UploadDocumentsPage.test.tsx src/shared/ui/FileDropzone` was run twice: 19 passed and 19 passed, about 58 s each.
- `grep eslint-disable frontend/src/features/wiki` finds nothing. `features/wiki` has no antd `Upload` import.
