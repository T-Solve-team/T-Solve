# Test Report: import-ticket-list — Task 29: fill-solution-screen

**Date:** 2026-11-07. The reviewer ran every check at HEAD `6267856`; the code is the same as `d3df940`. One shared run serves tasks 27–29. Node v24.22.2, Git Bash.
**Commands** (in `frontend/`): `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` (`vitest run`) · `npm run build` · `npm run generate:api` + `git status -- src/shared/api/generated`
**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-015`

## Results
- format:check: exit 0 · lint: exit 0, 0 errors (1 unrelated warning in `features/tickets`) · typecheck: exit 0
- vitest, run 1 (overlapping with lint, typecheck and build): exit 1. 6 failed, 1150 passed, 286 skipped of 1442 tests, all from load. `catalog.test.ts` was skipped by the `setup.ts:133` hook timeout (BUG-105). None of this task's `ImportPreviewPage` tests failed.
- vitest, run 2 (alone): exit 0. 80/80 files, 1442/1442 tests, 216.32 s.
- vitest, verbose run of the 3 STORY-015 test files: exit 0, 274/274.
- build: exit 0 (`ImportPreviewPage` chunk 8.64 kB) · generate:api: exit 0, no diff in `src/shared/api/generated`

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported | The spec is silent and the project has no coverage gate (guideline 10). |

## Acceptance Criteria → Tests
`sdd.ts trace`: 7/7. This task's ACs are in `frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx` and `frontend/src/shared/messages/catalog.test.ts`:

| AC | Test | Result |
|---|---|---|
| AC-015.4 | `:526` no Fill solution on a file with Solutions | pass (1012 ms) |
| AC-015.4 | `:533` disabled for no selection; enabled for Needs Solution / Valid; disabled once an Error row is selected; Removed rows cannot be selected | pass (2991 ms) |
| AC-015.4 | `:552` an Error row selected on page 1 keeps it disabled on page 2 | pass (1598 ms) |
| AC-015.5 | `:561` empty sections with no row fields; Needs Solution rows only → no MSG36; one request with `rowNumbers [1, 7]`; the selection is cleared | pass (3603 ms) |
| AC-015.5 | `:586` edge case 50: MSG36 with count 2 for Valid rows on two pages; Cancel saves nothing and keeps the popup and its text; OK saves `[1, 7, 21, 22]` | pass (5006 ms) |
| AC-015.5 | `:634` a row saved through Preview counts as Valid in MSG36 | pass (4188 ms) |
| AC-015.5 | `:651` a rejected bulk Apply keeps the popup, its text and the selection | pass (2679 ms) |
| AC-015.6 | `:676` ×3 (2/0, 0/3, 2/3 Needs Solution/Error): Confirm disabled, alert `MSG_NEW_4` with the sum | pass (~0.94 s each) |
| AC-015.6 | `:704` ×2: no alert once only Valid/Removed remain; Confirm enabled with Valid 1, disabled with Valid 0 | pass |
| AC-015.6 | `catalog.test.ts:147` `MSG_NEW_4` is inline with the spec text | pass (1 ms) |

## Failures
None in the authoritative run (run 2). Run 1's failures are listed in the task 27 test report; they were all load timeouts.

## Notes
- `:586` is the slowest test of the task (5.0 s alone): it pages twice and opens two dialogs. Under heavy load it is the nearest to the 15 s `testTimeout`.
- No test covers a `409 MSG_NEW_4` answered by the backend to a Confirm that the screen still sent. Confirm is disabled in that state, and `handleConfirm` shows any rejection as a toast (AC-009.7 path).
