---
module: import-ticket-list
feature: fill-solution-screen
task: 29
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 29: Fill solution and the Confirm lock on screen

## Summary
Commit `d3df940` (repo `default`, range `d3df940^..d3df940`). The code at HEAD `6267856` is the same.

- **Selection:** `ImportPreviewTable` now reports the selected records, not their keys (`components/ImportPreviewTable.tsx:108`). The page keeps `{ rowNo, status }` for each one (`pages/ImportPreviewPage.tsx:34-37,163-165`). With `preserveSelectedRowKeys`, antd (6.6.5) keeps the records of other pages in a cache that it refreshes from the shown page on every render, so statuses from other pages are carried along.
- **Fill solution** shows only on a `WITHOUT_SOLUTION` preview. It is enabled when the selection is non-empty and every selected row is Needs Solution or Valid (`:114-116,144-151`), and it is also disabled while a Remove or Confirm is running.
- **The popup** is `SolutionFormModal` in "selection" mode: empty `SolutionFields` and no row fields (`components/SolutionFormModal.tsx:78,89`). When `replaceCount` (the selected Valid rows, `ImportPreviewPage.tsx:117`) is above 0, Apply first opens a `ConfirmDialog` with MSG36 `{count}`. Cancel keeps the popup and its values; OK saves (`SolutionFormModal.tsx:69-73,91-100`). Success clears the selection (`ImportPreviewPage.tsx:193-202`). Apply through Preview marks that row Valid in the kept selection.
- **Confirm lock:** while a type 2 preview has `needsSolutionCount + errorCount > 0`, Confirm is disabled and a warning `Alert` shows `MSG_NEW_4 {count}` (`:112-113,136-138,173`). `MSG_NEW_4` is in the catalog as inline with the spec text (`shared/messages/catalog.ts`).

All three ACs have named tests, edge case 50 included, and the isolated run is green. I found no CRITICAL issue. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-015.4: enable rule `ImportPreviewPage.tsx:114-116`; hidden for type 1 (`:144`); Removed rows cannot be selected (`ImportPreviewTable.tsx:111`). AC-015.5: empty sections for the selected rows; MSG36 with the number of selected rows that already have a Solution (on type 2, Valid means it has one, AC-013.1); refusal saves nothing and keeps the popup open (`SolutionFormModal.tsx:99`). AC-015.6: Confirm disabled and `MSG_NEW_4` with the count of Needs Solution + Error rows. Type 2 with only Valid/Removed rows unlocks (edge case 49: Valid 0 still disables Confirm through AC-009.5). |
| Completeness | pass | AC-015.4, AC-015.5 and AC-015.6 are traced to 12 tests in `ImportPreviewPage.test.tsx` and 1 in `catalog.test.ts`. Edge case 50 (selection across pages, MSG36 count) is `:586`. The `AC-006.7` catalog test was updated for the new key. |
| Correctness | pass | Isolated run: 1442/1442, exit 0. Lint (0 errors), typecheck, format:check and build pass. `generate:api` left no diff. MSG36 OK → `save`, which goes through the `isSending` guard. The popup is remounted with `key` between Preview and Fill, so the form never carries one mode's values into the other. |
| Integration | pass | Same endpoint as task 28 (`fill-solution` with several `rowNumbers`, STORY-013). The backend still refuses Error/Removed rows (409 MSG06, AC-013.3) and an unready Confirm (409 `MSG_NEW_4`, AC-014.1), so the screen's checks are not the only guard. It reuses `ConfirmDialog`, `msg` and the catalog (AGENTS rule 5). |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-015` → `7/7 acceptance criteria have a test`. The ACs of this task:

| AC | Test (`frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx` unless named) | Status |
|---|---|---|
| AC-015.4 | `:526` hidden for type 1 · `:533` the enable rule · `:552` an Error row on another page | PASS |
| AC-015.5 | `:561` empty popup, no MSG36, selection cleared · `:586` MSG36 across pages, refusal, replace (edge case 50) · `:634` a Valid row saved through Preview counts · `:651` a rejected bulk Apply keeps text and selection | PASS |
| AC-015.6 | `:676` ×3 Confirm disabled + `MSG_NEW_4 {count}` · `:704` ×2 alert gone once ready · `shared/messages/catalog.test.ts:147` catalog entry | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] Selected rows' `status` is copied into `useState`. Where: `frontend/src/features/ticket-import/pages/ImportPreviewPage.tsx:34-36,163-165,196-200`. Guideline 06 (line 21) and AGENTS rule 4 say "no `useState` copies of query results". The design asks the page to keep these statuses so that rows on other pages count. The copy is kept true only for this page's own actions: Remove and Fill clear it, and Preview Apply patches it. If a row changes any other way, such as the same PM in a second tab, Fill solution's state and the MSG36 count go stale. The backend still blocks Error/Removed rows with MSG06, but a Valid row could then be replaced without MSG36. Fix: accept the design's choice, with a comment naming the limit, or rebuild the statuses at click time from the cached row pages (`queryClient.getQueriesData` on `getGetImportPreviewRowsQueryKey(previewId)`). Judgment call (see Assumptions).
2. [Low] Popup body switches during the fade-out. Where: `frontend/src/features/ticket-import/components/SolutionFormModal.tsx:89`. When a Preview popup closes, `rowNo` becomes `null` and the content switches to the empty `SolutionFields` (no row fields) during antd's fade-out, so the user sees the body change while it closes. Before this commit it went blank (`rowNo !== null && …`), the same class as the task 22 review's issue 2. Fix: pick the content by mode (`rowNumbers.length > 0`), or keep the last `rowNo` until `afterClose`.
3. [Info] Shared files changed. This task edits `src/shared/messages/catalog.ts` and `catalog.test.ts` (`MSG_NEW_4`). AGENTS rule 10 asks for the FE Lead's review of `shared/` changes in a feature PR. The design lists these files, so this is a pull request step, not a code fix.

## Assumptions made
- Issue 1 is a judgment call for the user: is the design's kept status (stale only after changes made outside this screen) acceptable under guideline 06, or must the statuses be derived from the query cache?
- AC-015.5 "already have a Solution": on a file without Solutions only a Valid row has one (AC-013.1, D19), so I accept counting the selected Valid rows.
- AC-015.6 sets no position for `MSG_NEW_4`. On a type 2 preview with Error rows the Summary shows both the backend's MSG30 and `MSG_NEW_4`. I accept both, since each AC (AC-009.1, AC-015.6) asks for its own message.
- I judge on the isolated full run (1442/1442). The first run overlapped with lint, typecheck and build. It failed only on load timeouts, and none of this task's tests failed there.

## Test Report
See `task-29-fill-solution-screen-test-report.md`. format:check, lint, typecheck and build each exit 0. Isolated vitest: 1442/1442 tests in 80/80 files, exit 0. `generate:api` left no diff. Trace: 7/7.
