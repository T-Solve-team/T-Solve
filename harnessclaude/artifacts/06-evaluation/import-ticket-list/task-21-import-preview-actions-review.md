---
module: import-ticket-list
feature: import-preview-actions
task: 21
verdict: pass
attempt: 1
date: 2026-11-03
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 21: Import Preview screen — Remove, Confirm, Cancel and leaving

## Summary
Commit `55a1ee6` (repo `default`, range `55a1ee6^..55a1ee6`), read at HEAD `15d346c`.

- **Remove** is disabled while nothing is selected (`ImportPreviewPage.tsx:99-105`). It sends the selected row numbers. On success it refetches the Summary and every rows page (`hooks/useImportPreview.ts:18-30`) and clears the selection (`:55-60`). Removed rows cannot be selected (`ImportPreviewTable.tsx:107-110`).
- **Confirm** is disabled at `validCount === 0` (`:117`). A ref stops a second request from a double click (`:63-64`), and the button shows loading while the request runs (`:118`). On success it shows MSG32 with `created` / `skipped`, sets `isFinished`, then opens Import Tickets (`:71-74`, `useImportPreview.ts:33-41`). Errors are shown with the returned code and the page stays (`:75`, `handlesOwnErrors`).
- **Cancel** navigates to Import Tickets. Leaving is guarded by one `useBlocker` + `ConfirmDialog` with MSG37 (`:35-40`, `:131-137`). The blocker applies only while the preview has loaded and before a successful Confirm.

Every task AC has a named test, and the shared run is green. I found no CRITICAL issue. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-009.3, .5, .6, .7, .8, .9 and .10 behave as written (lines above). MSG37 has one code path: the Cancel button and every in-app move go through the same blocker, and a successful Confirm skips it (design edge case 36). No `beforeunload`, as spec Out of Scope says. |
| Completeness | pass | All seven ACs of the task are traced to tests (below). Edge cases 34 (double click on Confirm), 35 (no Valid row) and 36 (no MSG37 after Confirm) are tested. |
| Correctness | pass | `ImportPreviewPage.test.tsx` passes in the shared run (1074/1074). The AC-009.8 test counts the requests that reach the mock backend: one Confirm for a double click. |
| Integration | pass | It uses the generated `useRemoveImportPreviewRows` / `useConfirmImport` and their query keys. `getGetImportPreviewRowsQueryKey(previewId)` without params is a prefix of every page's key, so all pages refetch. Remove errors go to the global toast; Confirm errors are handled by the screen (guideline 06 §6). The mock confirm handler covers MSG30 / MSG40 / MSG33 and 404 after confirm (AC-004.12). |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-009 STORY-010` → `16/16 acceptance criteria have a test`. The ACs of this task (`frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx`):

| AC | Test | Status |
|---|---|---|
| AC-009.3 | `:133` Removed rows not selectable · `:191` Remove disabled with no selection · `:199` remove, Summary and rows refreshed, selection cleared | PASS |
| AC-009.5 | `:219` Confirm disabled when `validCount` is 0 | PASS |
| AC-009.6 | `:229` MSG32 with 20 / 5, `/import`, no MSG37 | PASS |
| AC-009.7 | `:241` `it.each` 409 MSG30, 409 MSG40, 409 MSG33, 500 MSG33 → toast, stays | PASS |
| AC-009.8 | `:265` loading class and one request for a double click | PASS |
| AC-009.9 | `:289` Cancel → MSG37; dialog Cancel stays; OK → `/import`, no confirm request | PASS |
| AC-009.10 | `:309` `router.navigate('/my-profile')` → MSG37; Cancel stays; OK leaves | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `frontend/src/features/ticket-import/pages/ImportPreviewPage.tsx:115-119`: Confirm stays enabled while a Remove is running, and Remove stays enabled while a Confirm is running. A PM who clicks Remove and then Confirm quickly sends both. `validCount` is still the count from before the Remove. The backend Confirm does not lock against remove-rows (`task-9-csv-confirm-review.md`, attempt 2, issue 1), so a row the PM just removed can still be imported. Fix: `disabled={validCount === 0 || remove.isPending}` on Confirm and `disabled={selectedRowNos.length === 0 || confirm.isPending}` on Remove.
2. [Low] `frontend/src/features/ticket-import/pages/ImportPreviewPage.tsx:71-74` with `shared/api/query-client.ts:66` (`staleTime: 30_000`): after a successful Confirm, the preview and rows queries stay in the cache. If the PM uses browser Back within 30 s, the confirmed preview shows again as if it were still open: Summary, rows, an enabled Confirm, and MSG37 on leaving. A second Confirm then gets 404. Fix: in the Confirm `onSuccess`, call `queryClient.removeQueries({ queryKey: getGetImportPreviewQueryKey(previewId) })`, do the same for `getGetImportPreviewRowsQueryKey(previewId)`, and then navigate.
3. [Info] `ImportPreviewPage.tsx:75`: a 401 on Confirm shows an MSG07 toast in addition to the global MSG07 dialog, because `createQueryClient` calls `onUnauthorized` whatever `handlesOwnErrors` says (`query-client.ts:44-46`). The reference `RejectTicketModal` does the same. I record this only because guideline 06 §6 says "never two messages for one error". This is a kit-wide question, not a fix for this task.

## Assumptions made
- AC-009.10 "another screen of the web app": the blocker compares only `pathname` (`:39`), so a change of `?previewId` or another query string on `/import/preview` is not asked. I read the same path as the same screen.
- AC-009.7: a Confirm 404 (preview already confirmed elsewhere) shows the returned code as a toast (MSG04 from the real backend). The spec names only MSG30 / MSG33 / MSG40, and I accept the same behaviour for other codes.
- I judged only by the second shared verify run at HEAD `15d346c`, and did not run the suites myself.

## Test Report
`task-21-import-preview-actions-test-report.md`: frontend verify exit 0. format:check, lint (0 errors), typecheck, vitest 1074/1074 and build all pass, and `generate:api` left no diff.
