---
module: import-ticket-list
feature: row-solution-popup
task: 28
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 28: Write the Solution of one row

## Summary
Commit `34218f0` (repo `default`, range `34218f0^..34218f0`). I checked it at HEAD `6267856`, where task 29 extends `SolutionFormModal` and the page.

- **`SolutionFormModal`** (new, `frontend/src/features/ticket-import/components/SolutionFormModal.tsx`) is a kit `FormModal` titled "Import Ticket Preview" with okText "Apply". `RowFields` reads the row with `useGetImportPreviewRow` and shows Ticket ID, Title, Description, Author and Resolved date read-only. It then mounts the five sections as `TextArea` fields, started from the row's saved values (edge case 51), with `rules.required` on all but Workaround. Loading and `ErrorState` stay inside the popup.
- **`useFillPreviewSolution`** (`hooks/useImportPreview.ts:34-56`) has `handlesOwnErrors`. On success it invalidates the Summary, every page of rows and each written row's detail.
- **Page** (`pages/ImportPreviewPage.tsx:119-121,182-207`): Preview opens `SolutionFormModal` for a Needs Solution or Valid row of a `WITHOUT_SOLUTION` preview, and `ImportTicketPreviewModal` otherwise. A type 2 preview shows a Needs Solution StatCard (`:131`). `NEEDS_SOLUTION` ("Needs Solution") was already in `status-tags.ts` from `0526620`.
- **Mock** `fill-solution` handler (`mocks/handlers.ts:118`): 404, then 409 MSG06 for a type 1 preview or an Error/Removed row; it writes all rows or none. `seedFileWithoutSolutions` seeds 20 Needs Solution, 4 Error and 1 Removed rows.

All three ACs of the task have named tests, and the isolated run is green. I found no CRITICAL issue. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-015.2: editable sections with Apply for Needs Solution / Valid rows of type 2 (`ImportPreviewPage.tsx:119-121`). On success the popup closes only after the Summary and rows refetch, because the hook's `onSuccess` returns the `Promise.all` of the invalidations (`SolutionFormModal.tsx:56-59`). AC-015.3: `applyApiErrors` puts the 400 `fieldErrors` (`symptoms`, `rootCause`, `resolutionSteps`, `scope` match the form names and `FillSolutionRequest`) at their fields, and the popup and values stay (`:60-64`). AC-015.7: the StatCard, and the "Needs Solution" Result tag through the shared `StatusTag`. AC-010.1 still holds for Error and Removed rows (test `:802`). |
| Completeness | pass | AC-015.2, AC-015.3 and AC-015.7 are traced to 7 tests, and edge case 51 is tested (`:765`). One gap without a test is the rejection that has no field (issue 1). |
| Correctness | pass | Isolated run: 1442/1442, exit 0. Lint (0 errors), typecheck, format:check and build pass. `generate:api` left no diff. A double click on Apply is guarded by `isSending`, and Cancel is ignored while saving. |
| Integration | pass | It uses the generated `useFillImportPreviewSolution` (`POST /imports/previews/{previewId}/fill-solution`, `rowNumbers` of one; spec Interface Contracts) and `useGetImportPreviewRow` (AC-003.3 / AC-013.7). It reuses `FormModal`, `FormField`, `rules`, `applyApiErrors`, `SECTIONS` and `StatusTag`. No `shared/ui` component changed. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-015` → `7/7 acceptance criteria have a test`. The ACs of this task (`frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx`):

| AC | Test | Status |
|---|---|---|
| AC-015.2 | `:740` opens a Needs Solution row with editable sections and Apply | PASS |
| AC-015.2 | `:752` Apply closes the popup and refreshes the Summary (Valid 1, Needs Solution 19) and the row's Result | PASS |
| AC-015.2 | `:765` the saved Solution is shown again after a reload (edge case 51) | PASS |
| AC-015.2 | `:802` an Error row stays read-only | PASS |
| AC-015.3 | `:780` MSG01 at Scope, the popup open, the written Symptoms kept | PASS |
| AC-015.7 | `:727`, `:734` the Needs Solution count only for type 2, and the Needs Solution Result | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] The no-field rejection is untested. Where: `frontend/src/features/ticket-import/components/SolutionFormModal.tsx:63`, against `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportPreviewServiceImpl.java:106`. The backend rejects a Solution over 10,000 characters in total with 400 MSG02 (`SolutionTooLongException`) and no `fieldErrors`, because no single field holds the total. `applyApiErrors` then returns false, and the screen shows MSG02 as an error toast. The popup stays open with the text kept, but nothing tests this path, and the message is not "at its field" (see Assumptions). Fix: add a test where fill-solution answers `problem(400, 'MSG02', { params: { field_name: 'Solution', max_length: 10000 } })`. It should check that MSG02 is shown, the dialog stays open, and the sections keep their text. Optionally show such a code as an `Alert` inside the popup instead of a toast.

## Assumptions made
- AC-015.3 says "at its field". MSG02 for the five sections together has no field of its own, so I accept a toast with the popup kept open and the text kept. Judgment call for the user: should it be an inline `Alert` in the popup instead?
- I judge on the isolated full run (1442/1442). The first run, which overlapped with lint, typecheck and build, had this task's `:765` time out at 15 s; it passes in 3.07 s alone.
- Title "Import Ticket Preview" for the editable popup: the spec calls it "the popup" (STORY-010 / III.5.3), so I accept the same name.

## Test Report
See `task-28-row-solution-popup-test-report.md`. format:check, lint, typecheck and build each exit 0. Isolated vitest: 1442/1442 tests in 80/80 files, exit 0. `generate:api` left no diff. Trace: 7/7.
