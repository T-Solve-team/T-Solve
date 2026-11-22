---
module: import-ticket-list
feature: apply-and-publish-popup
task: 32
verdict: pass
attempt: 2
date: 2026-11-19
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 32: Apply & publish in the Import Ticket Preview popup (STORY-017, KAN-373) — attempt 2

## Summary
I re-read the code (`97824ea6..fc506bea`) and the fix (`4e2b56cd..720ef9d2`), and ran every frontend check myself
against AC-017.8 to AC-017.10.

**Popup.** `SolutionFormModal` now has its own `onPublished(rowNo)`, separate from `onSuccess`. In
`saveAndPublish`, `onSuccess` runs as soon as fill-solution succeeds, so the page marks the selected row Valid even
when the publish is then rejected (attempt 1 issue 2). `onPublished` and `onClose` run only after the publish
succeeds.

**Page.** `ImportPreviewPage.tsx:243-246` drops the published row from `selectedRows` (BUG-349, attempt 1 issue 1).

**Loading state.** A local `isPublishing` makes Apply & publish spin for both requests. Meanwhile Apply is disabled
(`isOkDisabled`) and does not spin (`isSubmitting={isBusy && !isPublishing}`). During a plain Apply, Apply spins and
Apply & publish is disabled (attempt 1 issue 4).

**Double clicks and Cancel.** The `isSending` ref still blocks a double click. A shared `finish()` resets it, and
`isPublishing` with it, on every end path: fill error, publish success and publish error. Cancel stays blocked while
a request runs.

**Tests.** Four new tests:
- BUG-349: after a publish, Fill solution sends only `[7]` and shows no MSG04.
- A double click sends exactly one fill-solution and one publish.
- A publish 404 shows MSG04 and keeps the popup.
- A Jira preview issue is published while the others stay (`JiraImportPreviewPage.test.tsx:366`).

**Bug log.** `docs/bug-log.md` has the BUG-349 row, with the test named.

**Placement.** The button stays in the popup body. The user decided this (a footer slot needs a `shared/ui` change
and the FE Lead's review).

All checks pass. No CRITICAL or Major issue is left, so the verdict is **pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-017.8 (button for Needs Solution / Valid rows of a CSV without Solutions or of a Jira preview; save, then publish), AC-017.9 (MSG32, popup closes, Summary and rows refresh, the selection is cleaned) and AC-017.10 (a rejection of either call shows the backend message and keeps the form) are met. |
| Completeness | pass | Every attempt 1 finding is resolved or was decided by the user (placement). |
| Correctness | pass | format:check, lint (0 errors, only the known `TicketListFilters.tsx` warning), typecheck, `vitest run src/features/ticket-import` 142/142 and build all pass. |
| Integration | pass | Uses the generated `usePublishImportPreviewRow`. `generate:api` leaves no diff. The MSW handler follows the contract. `FormModal`'s existing `isOkDisabled` prop is used, and `shared/ui` is not changed. |

## Traceability
```
  ✓ AC-017.8  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ✓ AC-017.9  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  ✓ AC-017.10  frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx
  (AC-017.1–AC-017.7: backend ImportConfirmServiceImplTest, ImportControllerTest, ImportConfirmIT)
  ! ids in tests but not in the spec: AC-007.4
trace import-ticket-list (STORY-017): 10/10 acceptance criteria have a test
```
AC-017.8 and AC-017.9 are also named in `JiraImportPreviewPage.test.tsx:366`. The trace lists only the first
file for each criterion.

## Attempt 1 findings

| # | Attempt 1 finding | Status |
|---|-------------------|--------|
| 1 | Major: the published row stays selected and breaks Fill solution (BUG-349) | **Resolved.** The page drops the row in `onPublished` (`ImportPreviewPage.tsx:243-246`). Covered by `ImportPreviewPage.test.tsx:855`. Logged as BUG-349. |
| 2 | Minor: a fill that succeeds before a failed publish leaves the selection status stale | **Resolved** in code. `onSuccess` runs after fill-solution, before the publish (`SolutionFormModal.tsx` `saveAndPublish`). No test pins it (see residual note 2). |
| 3 | Minor: the button is in the body, not the footer | **Accepted.** The user decided the body placement. |
| 4 | Minor: the loading state showed only during the publish half | **Resolved.** `loading={isPublishing}` covers both requests, and Apply is disabled meanwhile (`SolutionFormModal.tsx:141-142`, `:152-155`). |
| 5 | Minor: test gaps (double click, Jira row, 404, selection) | **Resolved** for the double click (`:885`), the Jira row (`JiraImportPreviewPage.test.tsx:366`), the 404 on CSV (`:902`) and the selection (`:855`). No test covers a publish 404 on a Jira preview, which leaves the page through `leaveIfGone`. Attempt 1 accepted that behaviour, and Fill solution does the same. |

## Issues
None that block the verdict. Residual notes (Low, optional):
1. **Low — BUG-349 "Fixed in" is empty.** `docs/bug-log.md:367` has `—` in the Fixed in column. The bug-log
   steps (line 17) say the fix fills in Fixed in. *Suggested fix:* put the branch
   `feat/KAN-373-import-publish-row` (or commit `720ef9d2`) there.
2. **Low — no test for "save succeeds, publish rejected, selection marked Valid".** The attempt 1 issue 2 fix is
   right in code but untested. *Suggested fix (optional):* select row 1, make publish answer MSG40, Apply & publish
   it, then open Fill solution with another Needs Solution row selected and check that MSG36 counts row 1.

## Assumptions made
- **Placement of Apply & publish** in the popup body, right-aligned under the fields, is read as "next to Apply".
  The user decided this.
- **A publish 404 on a Jira preview leaves the page** (`leaveIfGone`), although the preview may still exist.
  Fill solution does the same, and attempt 1 accepted it. I did not count it against this task.
- **The "Fixed in" gap in the bug log is Low** and does not fail the verdict.

## Test Report
See `task-32-apply-and-publish-popup-test-report-2.md`.
- format:check: pass.
- lint: 0 errors, 1 known warning.
- typecheck: pass.
- `vitest run src/features/ticket-import`: 7 files, 142/142 passed.
- build: pass.
- `generate:api`: no diff.
