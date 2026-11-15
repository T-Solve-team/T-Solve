---
module: wiki
feature: wiki-folder-ui
task: 5
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 5: Folder popup and actions

## Summary
This review covers commit 68c7f452, checked against AC-003.13 to AC-003.16 and the folder actions of AC-002.21. No tests were run (dispatcher instruction). The verdict is pass **on the condition** that the frontend checks are green.

Checked by reading the code:
- **Folder popup.** `FolderModal` has the title "Folder", a Name field with a 100-character counter, and a Parent folder `TreeSelect` with a "Top level" entry. It offers only folders with `level < 3`. On Rename and Move it also leaves out the folder and its whole sub-tree (AC-003.13). Rename and Move open the same popup, already filled in.
- **Name validation.** `rules.required` and `rules.maxLength('Name', 100)` stop the submit and show MSG01 or MSG02 under the field. The tests check that no request is sent (AC-003.14).
- **Save.** On success, the shared hook shows MSG05 and refreshes the Space's `listWikiFolders` query, and the popup closes (AC-003.15). MSG85 appears under Name, and MSG86 appears as an inline `Alert` in the popup; the popup stays open and keeps the input. A ref and `isPending` block a second submit.
- **Delete.** A confirm popup shows the folder name and has Cancel. On confirm, the app shows MSG05 and refreshes the tree, and if the deleted folder was selected it is removed from the URL. MSG87 is shown with the server's counts through `ErrorState` (AC-003.16).
- **Manager only.** Rename, Move, Delete and New folder appear only when the selected Space's `accessLevel` is `KNOWLEDGE_MANAGER`. A test checks that a viewer sees none of them (AC-002.21).
- **Error handling and mocks.** Each popup handles its own errors (`meta.handlesOwnErrors`). The MSW handlers follow the server's rules (MSG01, 02, 04, 08, 85, 86, 87). No scope creep was found.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-003.13 to AC-003.16 and the AC-002.21 folder actions are implemented |
| Completeness | pass | Each AC has tests named with its id in `FolderModal.test.tsx` and `WikiHomePage.test.tsx` |
| Correctness | pass (conditional) | The reviewer did not run the tests. The result depends on `npm run format:check`, `lint`, `typecheck`, `test` and `build` |
| Integration | pass | Uses the generated hooks from Task 4 (`useCreateWikiFolder`, `useUpdateWikiFolder`, `useDeleteWikiFolder`) |

## Traceability
```
trace wiki (STORY-003): 17/17 acceptance criteria have a test
```
Task 5 owns AC-003.13 to AC-003.16. AC-002.21 is also covered in `WikiHomePage.test.tsx`.

## Issues
1. **Minor.** `frontend/src/features/wiki/components/FolderTree.tsx:88`: `DeleteFolderModal` is a second component in `FolderTree.tsx`, which breaks guideline 05 C1 (one component per file). Fix: move it to `components/DeleteFolderModal.tsx`.
2. **Minor.** `frontend/src/features/wiki/components/FolderTree.tsx:131`: the delete confirmation shows only the folder name under the title "Delete", and asks no question. The SRS has no confirm message for deleting a folder, so the gap is in the spec, not the code (see A1). Fix: once the user decides, use the text they choose or add an SRS confirm code.
3. **Minor.** `frontend/src/features/wiki/components/FolderModal.tsx:77`: the MSG86 alert is driven by `mutation.error`, so it stays visible after the user picks another parent, until the next Save. Fix: call `mutation.reset()` when `parentId` changes.

## Assumptions made
- **A1.** AC-003.16 says "ask for confirmation" but the SRS gives no message for it. The reviewer accepted a danger popup titled "Delete" that shows the folder name. It is built on `FormModal` rather than `ConfirmDialog`, so that MSG87 can appear inside it. Ask the user whether this confirmation needs its own message.
- **A2.** AC-003.13 is read as requiring only that level-3 folders, the folder itself and its sub-tree are hidden. A parent that would push a deep sub-tree past level 3 is still offered, and the server answers MSG86 (AC-003.5). This is treated as compliant.
- **A3.** Tests were not run (dispatcher instruction). The feature status was not changed. The caller sets `passing` once the frontend checks are green, or `fixing` if they are not. BUG-223 (Wiki Home test timing, still Open, logged in Task 4) may make `npm run test` fail now and then.

## Test Report
See `task-5-wiki-folder-ui-test-report.md`.
