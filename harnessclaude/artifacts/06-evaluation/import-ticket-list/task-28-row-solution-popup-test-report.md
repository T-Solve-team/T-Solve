# Test Report: import-ticket-list — Task 28: row-solution-popup

**Date:** 2026-11-07. The reviewer ran every check at HEAD `6267856` (it holds `34218f0` and task 29 on top). One shared run serves tasks 27–29. Node v24.22.2, Git Bash.
**Commands** (in `frontend/`): `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` (`vitest run`) · `npm run build` · `npm run generate:api` + `git status -- src/shared/api/generated`
**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-015`

## Results
- format:check: exit 0 · lint: exit 0, 0 errors (1 unrelated warning in `features/tickets`) · typecheck: exit 0
- vitest, run 1 (overlapping with lint, typecheck and build): exit 1. 6 failed, 1150 passed, 286 skipped of 1442 tests, all from load. This includes `ImportPreviewPage.test.tsx:765` (this task's edge-case-51 test), which timed out at 15 s.
- vitest, run 2 (alone): exit 0. 80/80 files, 1442/1442 tests, 216.32 s.
- vitest, verbose run of the 3 STORY-015 test files: exit 0, 274/274.
- build: exit 0 (`ImportPreviewPage` chunk 8.64 kB) · generate:api: exit 0, no diff in `src/shared/api/generated`

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported | The spec is silent and the project has no coverage gate (guideline 10). |

## Acceptance Criteria → Tests
`sdd.ts trace`: 7/7. This task's ACs are in `frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx` (`describe('a file without Solutions')`):

| AC | Test | Result |
|---|---|---|
| AC-015.2 | `:740` Preview on a Needs Solution row: the read-only fields, five empty textboxes, Apply enabled | pass (1136 ms) |
| AC-015.2 | `:752` Apply → the popup closes; Valid 1, Needs Solution 19, row 1 "Valid" | pass (2178 ms) |
| AC-015.2 | `:765` edge case 51: after an unmount and reopen, the saved Symptoms text is in its field | pass (3066 ms) |
| AC-015.2 | `:802` an Error row of a type 2 preview stays read-only (no Apply, no textbox) | pass (851 ms) |
| AC-015.3 | `:780` 400 with `fieldErrors` `scope`/MSG01 → MSG01 inside the dialog, the dialog still open, Symptoms keeps what was written | pass (2276 ms) |
| AC-015.7 | `:727` the Needs Solution StatCard is 20 and row 1's Result is "Needs Solution" | pass (643 ms) |
| AC-015.7 | `:734` a file with Solutions shows no Needs Solution StatCard | pass (519 ms) |

## Failures
None in the authoritative run (run 2). Run 1's timeout of `:765` came from load: it passes in 3.07 s alone (see the task 27 test report for the full list).

## Notes
- No test covers a rejection without `fieldErrors`, such as MSG02 for the Solution total, which the backend sends with no field (review issue 1).
- `:765` is this module's slowest test of this task (3 s alone). Under heavy load it can reach the 15 s `testTimeout`.
