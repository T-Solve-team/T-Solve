---
module: wiki
feature: wiki-trash-ui
task: 10
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 10: Delete button and Trash tab

Commit reviewed: `9fe4cb5d` (KAN-175). Reviewed by reading the code only; the reviewer ran no tests (see Test Report).

## Summary
11.2 shows Delete only when `canDelete` is true. It asks MSG100 with the title, sends one request on confirm (double-clicks are guarded), shows MSG101 and goes to 11.1. A failed delete keeps the dialog open and lets the user retry. The global mutation handler shows the error toast. `SpaceSettingsPage` now has a tab shell (Permissions is left empty for Task 12) and a `?tab=trash` Trash tab. `TrashTable` shows Title, Folder, Owner, Deleted by and Deleted on, with Restore and "Delete permanently" on each row. It shows MSG04 when the trash is empty and handles the loading and error states. Restore shows MSG102, plus an info Alert with MSG84 when `renamed` is true. "Delete permanently" asks MSG103 with the title, then shows MSG05. After each action, `useWikiTrashMutations` invalidates the trash list, the Space documents list, Company-wide, search and the document detail. Messages all come from the catalog. The MSW mocks keep state and match the backend's 403/404 rules (BUG-283). No critical or major findings.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-005.13–AC-005.16 are built as written. |
| Completeness | pass | Each AC has a test named with its id. The tests include cancel, double-click, failure and retry, empty, error, and a manager of another Space. |
| Correctness | pass (static) | The reviewer did not run the tests. |
| Integration | pass | The page uses the Task 9 generated hooks (`useTrashWikiDocument`, `useListWikiTrash`, `useRestoreWikiDocument`, `useDeleteWikiDocumentPermanently`). 409 MSG84 and other mutation errors go through `app/providers/global-errors.ts`. |

## Traceability
```
  ✓ AC-005.13  frontend/src/features/wiki/pages/WikiDocumentPage.test.tsx
  ✓ AC-005.14  frontend/src/features/wiki/components/TrashTable.test.tsx
  ✓ AC-005.15  frontend/src/features/wiki/components/TrashTable.test.tsx
  ✓ AC-005.16  frontend/src/features/wiki/components/TrashTable.test.tsx
trace wiki (STORY-005): 17/17 acceptance criteria have a test
```

## Issues
Minor only (they do not fail the task):
1. `frontend/src/features/wiki/pages/WikiDocumentPage.tsx:36`: minor. When the Knowledge Manager opens a trashed document (AC-005.4), the page still shows Download, which always answers 404 MSG110 for a trashed document (AC-002.13). Nothing on the page says the document is in the trash. Fix: hide Download (and show a "in the trash" note) when the document is trashed. This needs a field such as `status`/`trashed` on `WikiDocumentDetail` or a link from the Trash tab. Raise it as a spec question first.
2. `frontend/src/features/wiki/pages/SpaceSettingsPage.tsx:20`: minor. A non-manager gets `ErrorState` with MSG08 instead of the app's `ForbiddenPage` (403 result). This is AC-006.14 (Task 12) behaviour, built early. Task 12 should confirm the "no-permission page" wording is met or add a shared 403 component.
3. `frontend/src/features/wiki/pages/WikiDocumentPage.tsx` (trash `onSuccess`): minor. It goes back to `paths.wikiHome` without `?space=`, so 11.1 forgets the Space the user came from. Fix: navigate to `paths.wikiHome` with `?space=${data.spaceId}&folder=${data.folderId}`, if the user wants that.

## Assumptions made
- "Return to 11.1" (AC-005.13) is accepted as the Wiki Home root, without keeping the selected Space.
- Leaving the Permissions tab empty is accepted as the Task 12 split named in tasks.md.
- The reviewer ran no tests, as the dispatcher instructed. This PASS depends on `npm run format:check`, `lint`, `typecheck`, `test` and `build` being green in the orchestrator's run.

## Test Report
See `task-10-wiki-trash-ui-test-report.md`.
