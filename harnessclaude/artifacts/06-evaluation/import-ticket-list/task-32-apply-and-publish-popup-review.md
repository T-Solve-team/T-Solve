---
module: import-ticket-list
feature: apply-and-publish-popup
task: 32
verdict: fail
attempt: 1
date: 2026-11-19
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 32: Apply & publish in the Import Ticket Preview popup (STORY-017, KAN-373)

## Summary
Code commit `fc506bea` (range `97824ea6..fc506bea`) changes four files under `frontend/src/features/ticket-import/`.
It adds an "Apply & publish" button to the one-row Solution popup (`SolutionFormModal`), a
`usePublishPreviewRow` hook (MSG32, then the Summary and rows are read again), an MSW handler and four page tests.

The popup flow itself is correct. `saveAndPublish` validates the form and calls fill-solution with that single row.
Publish is called only after fill succeeds. A rejection from either call goes through the same `showError`: field
errors go to their field, any other code is shown with `showMessage`, and 401 is left to the global dialog. The
popup stays open with what was written. `isSending` stops a double click: it is checked before and after
`validateFields`, it is reset in fill `onError` and publish `onSettled`, and Cancel is blocked while it is set. Apply
and Apply & publish are both disabled while either mutation is pending. antd `Button` is allowed (guideline 07, "Web/Button
→ antd `Button`, no wrapper"). All frontend checks pass.

The verdict is **fail** because of a bug that this change introduces in the page. After Apply & publish, the published row
stays in the page's selection, marked `VALID`, and the table keeps its key with `preserveSelectedRowKeys: true`. The
row is no longer in the table, so the user cannot uncheck it. From then on, Fill solution with any selection sends a
row number that no longer exists. The backend answers 404 (`fillSolution` checks
`rows.size() < numbers.size()`). On a CSV preview this shows MSG04. On a Jira preview `leaveIfGone` takes the user
off the preview. Only a reload clears it.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | AC-017.8–AC-017.10 are met inside the popup, but the page's `onSuccess` treats a published row as still present (issue 1) and breaks Fill solution afterwards. |
| Completeness | pass | Button for Needs Solution / Valid rows of WITHOUT_SOLUTION previews (CSV and Jira both use `importType === 'WITHOUT_SOLUTION'`); fill then publish; MSG32; refresh; errors keep the form. |
| Correctness | pass | format:check, lint (only the known `TicketListFilters.tsx` warning), typecheck, 138/138 ticket-import tests and the build all pass. |
| Integration | pass | Uses the generated `usePublishImportPreviewRow`. `generate:api` leaves no diff. The MSW handler follows the contract (404 MSG04, 409 MSG06, MSG40, MSG32 created 1 / skipped 0). |

## Traceability
```
  ✓ AC-017.8  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ✓ AC-017.9  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ✓ AC-017.10  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  (AC-017.1–AC-017.7: backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImplTest.java)
  ! ids in tests but not in the spec: AC-007.4
trace import-ticket-list (STORY-017): 10/10 acceptance criteria have a test
```

## Issues
1. **Major — the published row stays selected as `VALID` and breaks Fill solution.**
   `frontend/src/features/ticket-import/pages/ImportPreviewPage.tsx:233-241` (the `onSuccess` passed to
   `SolutionFormModal`) runs for both Apply and Apply & publish. For a one-row popup it maps the row to
   `status: 'VALID'` in `selectedRows`. After a publish the row has left the preview, and
   `ImportPreviewTable.tsx` keeps the key (`preserveSelectedRowKeys: true`). The user cannot uncheck a row that is
   no longer on screen. Then `canFill` is true, and Fill solution sends the stale row number. `fillSolution` returns
   404, so a CSV preview shows MSG04 and a Jira preview leaves the page through `leaveIfGone`. Remove still works
   (unknown rows are ignored) but its count is wrong. This line was not touched in this commit, but
   `SolutionFormModal.tsx:97-100` now calls the same `onSuccess` for a publish.
   *Fix:* give `SolutionFormModal` a separate `onPublished?: (rowNo) => void`, or pass a flag to `onSuccess`. On
   publish, the page drops the row: `setSelectedRows((rows) => rows.filter((r) => r.rowNo !== rowNo))`. Add a
   test: select row 1, open its popup, Apply & publish, then check that the selection count/Fill solution no longer
   include row 1. Log the bug in `docs/bug-log.md` (project rule).
2. **Minor — a fill that succeeds before a failed publish leaves the selection status stale.**
   Same handler. When fill succeeds and publish is rejected (MSG40/MSG33/403), the row is now Valid on the server.
   The page's `onSuccess` is not called, so a selected row keeps `NEEDS_SOLUTION`. `replaceCount` (MSG36 count)
   then under-counts by one. The Summary and rows are correct, because the fill hook invalidates them.
   *Fix:* set the row `VALID` in the selection on fill success, before publishing. The same `onPublished` split
   from issue 1 can do this.
3. **Minor — "next to Apply" is placed in the body, not in the footer.**
   `frontend/src/features/ticket-import/components/SolutionFormModal.tsx:136-147`. The button sits right-aligned
   under the fields, inside the modal body. Apply is in the `FormModal` footer, and `FormModal` has no prop for a
   second footer action. The test `ImportPreviewPage.test.tsx:812` only checks that the button exists.
   *Fix:* either accept the body placement (see Assumptions), or add an `extraActions` prop to `FormModal` in
   `src/shared/ui` (FE Lead review, as D12) and render Apply & publish beside Apply.
4. **Minor — the loading state shows only during the publish half.**
   `SolutionFormModal.tsx:140`: `loading={publish.isPending}`. During fill-solution, the clicked button is only
   disabled and the footer Apply spins instead (`isSubmitting={isBusy}`). Guideline 06 §148 / 09 §77 asks for the
   clicked button to show its loading state.
   *Fix:* `loading={isPublishing}`, with a local state set when Apply & publish starts and cleared in both
   `onError`/`onSettled` paths. Or track which action started, and give `FormModal` `isSubmitting` only for Apply.
5. **Minor — test gaps.** No test for a double click on Apply & publish (one fill, one publish). No test for a
   Jira preview row (AC-017.8 covers "or of a Jira preview"). No test that a 404 from publish on a Jira preview
   leaves the page as intended. No test for the selection after publish (issue 1).
   *Fix:* add the Jira and double-click cases to `ImportPreviewPage.test.tsx` next to the AC-017 tests.

## Assumptions made
- **Placement of Apply & publish (issue 3).** The spec says "next to Apply". I read this as "in the same popup,
  beside the form's actions" and did not fail on the body placement alone. **Open judgment call for the user:** is
  the body placement acceptable, or must the button be in the footer (a `shared/ui` change)?
- **Issue 1 is in scope although no AC names the selection.** AC-017.9 asks the app to "refresh the Summary and the
  rows". I read keeping a deleted row selected, where it is invisible and cannot be cleared, as a failure of that
  refresh and as a regression of Fill solution (AC-015.4/015.5). **Open judgment call** if the user sees it otherwise.
- A 404 from publish on a Jira preview calls `leaveIfGone`, which leaves the page even when only the row is gone
  (the preview still exists). Fill solution already behaves this way. I did not count it against this task.

## Test Report
See `task-32-apply-and-publish-popup-test-report.md`. format:check pass. lint 0 errors, 1 known warning.
typecheck pass. `vitest run src/features/ticket-import` 7 files, 138/138 passed. build pass. `generate:api` no diff.
