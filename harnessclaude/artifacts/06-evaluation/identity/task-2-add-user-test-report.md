# Test Report: identity — Task 2: add-user

**Date:** 2026-10-26
**Commands:** `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run build` · `npm run generate:api` · `npm run test -- src/features/users src/shared/forms --reporter=json`

## Results
- Parent independent targeted run: Total52 · Passed52 · Failed0 · Skipped0 · Duration42.05s. Covers handlers19, detail14, list15 and forms4.
- Developer independent targeted run also passes52/52 after restoring the latest KAN-107 base and UUID contract.
- Format, lint, typecheck and production build: pass.
- API regeneration: before/after SHA-256 file-content set identical.
- Full branch Linux CI: pending existing workflow_dispatch after push. The task remains evaluating.
- Source commit1697121; no changed KAN-107 list page/toolbar/test files.

## Coverage
No numeric coverage script/threshold is configured. Acceptance tracing is8/8 for STORY-005; numeric line coverage is not inferred.

## Acceptance Criteria → Tests

| Criterion | Evidence |
|-----------|----------|
| AC-005.1 | Add user opens creation mode; unauthorized creation/options rejected |
| AC-005.2 | Required, malformed, overlong and malformed-JSON email cases |
| AC-005.3 | Wrong domain and case-insensitive Active duplicate errors; no mutation/input loss |
| AC-005.4 | Employee selected/disabled and retained server-side |
| AC-005.5 | Required/foreign Department rejection; successful numeric selection BUG-014 |
| AC-005.6 | API options, required KM Spaces, occupied/invalid Space errors, lookup failure/retry/empty |
| AC-005.7 | UUID insertion, list invalidation/MSG12, network retry, concurrent creation and duplicate-submit guard BUG-013 |
| AC-005.8 | Deactivated duplicate MSG111 with unchanged records/form |

`sdd.ts trace identity STORY-005`:8/8 acceptance criteria have tests.

## Failures
- BUG-013: rapid double Save sent two POSTs before isPending rendered. Test-first synchronous submission latch fixed it.
- BUG-014: selected numeric Department failed the string required rule. Feature-local type:number rule fixed it.
- BUG-017: restored creation used Math.max over UUID strings, producing an invalid id. UUID creation regression was red; crypto.randomUUID resolves it. The exact test now names BUG-017; the final19-handler rerun passes after this bookkeeping label update.
- Environment-only failed attempts lost transformed-client files under the execution environment's temporary directory (ENOENT before UI suites ran). Stable workspace TEMP/TMP permits all52 tests to execute. No test timeout changed.
- Full local chatbot tests have existing15-second timeout failures (BUG-016), independently reproduced on main during KAN-107. That branch's original full Linux CI passes29 files/516 tests. This is not substituted for KAN-108's pending full run.

## Notes
- Browser: Admin,1366×768 viewport, rejected outside-domain email with form retained; created a PM with IT Department and UUID detail link; total changes24→25. See registered add-user and created-user screenshots.
- A stale dynamic-import log occurred during branch/HMR switching; the actual creation flow succeeds. Fresh acceptance-page console will be checked separately.
- New user management behavior is frontend/MSW only; real backend integration remains outside this delivery.

## Full Linux CI result
- Exact verified head84d31e728a5533a06a6934e9c88de683af631445, run40220379992, frontend job121250495984 completed successfully.
- Total543 · Passed543 · Failed0 · Skipped0;30/30 files; duration107.79s.
- Format, lint, typecheck, build and generate:api with a clean generated Git diff all pass under the original workflow/test settings.
- Evidence:https://github.com/T-Solve-team/T-Solve/actions/runs/40220379992/job/121250495984
- Fresh stable acceptance page loads creation mode with no console errors. Earlier HMR import error is absent after the branch settled.
