# Test Report: triage — Task 2: suggestion-history-contract (attempt 2)

**Date:** 2026-11-04
**Code state tested:** `default` repo at HEAD `66c58c6`. Its code is the same as the fix commit `c2a80da`; `66c58c6` changes harness files only. Range under review: `626ce59..c2a80da` (Task 2 code `bef5072`, fix `c2a80da`).
**Commands** (from `frontend/` unless noted): `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run build` · `npm run generate:api` + `git status --porcelain src/shared/api/generated` · `npx vitest run --maxWorkers=2 src/features/triage src/app/router/route-table.test.tsx` (×2) · `npx vitest run src/features/triage/hooks/history-filters.test.ts` · `npx vitest run --maxWorkers=2 --reporter=verbose src/features/triage/mocks/handlers.test.ts src/features/triage/pages` · root: `bun harnessclaude/hooks/sdd.ts trace triage STORY-001 STORY-002`. No full-suite run was made, per the dispatcher.

## Results

| Check | Exit | Key output |
|-------|------|------------|
| `npm run format:check` | 0 | `All matched files use Prettier code style!` |
| `npm run lint` | 0 | `✖ 1 problem (0 errors, 1 warning)`; the warning is at `src/features/tickets/components/TicketListFilters.tsx:23`, not triage |
| `npm run typecheck` | 0 | `tsc -b --noEmit`, no errors |
| `npm run build` | 0 | `✓ built in 2.60s` |
| `npm run generate:api` | 0 | orval regenerated the client. `git status --porcelain src/shared/api/generated` is empty, and the work tree stays clean |
| Targeted run, `--maxWorkers=2` (run 1) | 1 | `Test Files 1 failed \| 3 passed (4)` · `Tests 71 passed \| 8 skipped (79)` · 62.20s. The failed file is `history-filters.test.ts` (setup hook timeout) |
| Targeted run, `--maxWorkers=2` (run 2) | 1 | Same result: `Tests 71 passed \| 8 skipped (79)` · 52.63s. Same file |
| `history-filters.test.ts` alone | 0 | `Test Files 1 passed (1)` · `Tests 8 passed (8)` · 10.65s |
| Verbose: `handlers.test.ts` + `pages/` | 0 | `Test Files 2 passed (2)` · `Tests 27 passed (27)`. It includes `AC-001.11 rejects unmanaged Projects and ignores a forged Workspace selector` ✓ and `AC-001.7 shows a separate HTTPS Jira link and detail action` ✓ |
| `sdd.ts trace triage STORY-001 STORY-002` | 0 | `20/20 acceptance criteria have a test` |

- Typecheck: pass · Lint: pass · Build: pass · Deterministic generation: pass.
- Triage assertions: 35/35 pass across `handlers.test.ts` (8), `TriageHistoryPage.test.tsx` (19) and `history-filters.test.ts` (8, run alone). `route-table.test.tsx` passes in the targeted run.

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line/branch coverage | not measured (no coverage script) | none set. NFR-1 only needs an AC-named test for every criterion |

## Acceptance Criteria → Tests

```
trace triage (STORY-001, STORY-002): 20/20 acceptance criteria have a test
```
The per-AC file list is in the review's Traceability section. Trace checks names only. The attempt-1 vacuous AC-001.11 assertion is fixed: `handlers.test.ts:77-78` now checks `ITSUP-9999` and `Foreign private data`. These strings occur only in the foreign-Workspace row (`mocks/data.ts:95-107`). That row would be the newest row on page 0 if `handlers.ts:96` stopped filtering by Workspace. The review shows the full reasoning.

## Failures

### `src/features/triage/hooks/history-filters.test.ts`: whole suite (targeted run, `--maxWorkers=2`, 2 of 2 runs)
- Error: `Error: Hook timed out in 10000ms.` at `src/test/setup.ts:103:1`. 8 tests skipped, 0 assertions failed.
- Cause: BUG-105, which is Open in `docs/bug-log.md` and owned by the FE Lead. The shared `beforeAll` imports every feature's MSW handlers and exceeds the 10 s default `hookTimeout` under load. `vite.config.ts:57` raises only `testTimeout`. Triage code is not the cause.
- Suggested fix: the FE Lead should raise `hookTimeout` or make the shared setup lighter. This is outside Task 2.

## Notes
- No triage assertion failed in any run.
- `c2a80da` also adds the null-URL check to AC-001.7 (`TriageHistoryPage.test.tsx:129-132`), switches the mock sort to `Date.parse` (`handlers.ts:104-107`), and renames the day-boundary test to AC-001.3 (`handlers.test.ts:54`).
- Slow tests: `AC-002.6` takes about 3.0 s and `AC-001.1` about 2.5 s. None takes more than 5 s.
- Not independently verified: the browser check at 1366 × 768.
- Open question: whether the BUG-105 skip is acceptable for this pass (see the review, Assumptions made).
