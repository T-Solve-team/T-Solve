# Test Report: triage — Task 1: suggestion-history

**Date:** 2026-11-04
**Code state tested:** `default` repo at `bef5072` (working tree clean; HEAD `fd2c1f6` adds harness files only, no `frontend/` change since `bef5072`). Task 1 commits: `a294fbf` (feature), `6de048c` (BUG-104 fix). The same run backs the Task 2 report.
**Commands** (from `frontend/` unless noted): `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run build` · `npm run generate:api` + `git status --porcelain src/shared/api/generated` · `npx vitest run src/features/triage src/app/router/route-table.test.tsx` · `npx vitest run --maxWorkers=2` (full suite) · root: `bun harnessclaude/hooks/sdd.ts trace triage STORY-001 STORY-002`

## Results

| Check | Exit | Key output |
|-------|------|------------|
| `npm run format:check` | 0 | `All matched files use Prettier code style!` |
| `npm run lint` | 0 | `✖ 1 problem (0 errors, 1 warning)` — warning in `src/features/tickets/components/TicketListFilters.tsx:23` (not triage) |
| `npm run typecheck` | 0 | `tsc -b --noEmit`, no output |
| `npm run build` | 0 | `✓ built in 3.48s` |
| `npm run generate:api` | 0 | `T-Solve API - Your OpenAPI spec has been converted…`; then `git status --porcelain src/shared/api/generated` printed nothing (no diff); whole tree clean |
| Targeted run #1 (default workers) | 1 | `Test Files 1 failed \| 3 passed (4)` · `Tests 71 passed \| 8 skipped (79)` · 38.33s |
| Targeted run #2 (default workers) | 1 | same: `Tests 71 passed \| 8 skipped (79)` · 38.62s |
| `npx vitest run src/features/triage/hooks/history-filters.test.ts` (alone) | 0 | `Tests 8 passed (8)` · 9.33s |
| Targeted run, `--maxWorkers=2` | 0 | `Test Files 4 passed (4)` · `Tests 79 passed (79)` · 45.49s |
| Full suite `npx vitest run --maxWorkers=2` | 1 | `Test Files 1 failed \| 70 passed (71)` · `Tests 1317 passed \| 2 skipped (1319)` · 1045.86s |
| `npx vitest run src/app/dev/mock-mode.test.tsx` (alone) | 0 | `Tests 2 passed (2)` · 14.78s |
| `sdd.ts trace triage STORY-001 STORY-002` | 0 | `20/20 acceptance criteria have a test` |

- Typecheck: pass · Lint: pass (0 errors) · Build: pass · Generated client deterministic: pass.
- Triage tests: every triage test that ran passed in every run; no assertion failure in any triage test.

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line/branch coverage | not measured — the project has no coverage script | spec sets none; NFR-1 asks for an AC-named test per criterion (met, see trace) |

## Acceptance Criteria → Tests

```
  ✓ AC-001.1  frontend/src/features/triage/pages/TriageHistoryPage.test.tsx
  ✓ AC-001.2  …/pages/TriageHistoryPage.test.tsx, …/mocks/handlers.test.ts, …/hooks/history-filters.test.ts
  ✓ AC-001.3  …/pages/TriageHistoryPage.test.tsx, …/mocks/handlers.test.ts, …/hooks/history-filters.test.ts
  ✓ AC-001.4  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-001.5  …/pages/TriageHistoryPage.test.tsx, …/mocks/handlers.test.ts, …/hooks/history-filters.test.ts
  ✓ AC-001.6  …/pages/TriageHistoryPage.test.tsx, …/mocks/handlers.test.ts
  ✓ AC-001.7  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-001.8  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-001.9  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-001.10  …/pages/TriageHistoryPage.test.tsx, …/mocks/handlers.test.ts
  ✓ AC-001.11  …/mocks/handlers.test.ts
  ✓ AC-001.12  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-002.1  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-002.2  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-002.3  …/pages/TriageHistoryPage.test.tsx, …/mocks/handlers.test.ts
  ✓ AC-002.4  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-002.5  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-002.6  …/pages/TriageHistoryPage.test.tsx
  ✓ AC-002.7  …/mocks/handlers.test.ts
  ✓ AC-002.8  …/pages/TriageHistoryPage.test.tsx
trace triage (STORY-001, STORY-002): 20/20 acceptance criteria have a test
```

Trace checks names only. See the review for one AC-001.11 assertion that names the criterion but checks nothing since `bef5072` (Task 2 finding).

## Failures

### `src/features/triage/hooks/history-filters.test.ts` — whole suite (targeted run, default workers, 2 of 2 runs)
- Error: `Error: Hook timed out in 10000ms.` at `src/test/setup.ts:103:1` (the shared `beforeAll` that imports `@/mocks/handlers`, `@/mocks/server`, `@/shared/api`). 8 tests skipped, none failed.
- Cause: load, not triage code. The shared setup's dynamic import of every feature's mock handlers runs past the 10 s hook timeout when this small pure-function file starts in parallel with the heavy page tests. Alone the file passes 8/8 (but in 9.33 s total, so the setup alone is close to the 10 s limit); with `--maxWorkers=2` it passes 8/8 in the targeted run and in the full run.
- Suggested fix: shared test setup (`src/test/setup.ts`, FE Lead) — not a triage change. Log it in `docs/bug-log.md`; it is not covered by an existing row (BUG-080 and BUG-094 are other causes).

### `src/app/dev/mock-mode.test.tsx` — whole suite (full run, `--maxWorkers=2`)
- Error: same `Hook timed out in 10000ms.` at `src/test/setup.ts:103:1`; 2 tests skipped.
- Cause: same shared-setup load timeout; not a triage file. Alone: 2/2 pass.
- Suggested fix: same as above.

## Notes
- Full suite: 1317/1317 executed tests passed; the only red is the setup-hook timeout above (2 skipped tests in an `src/app` file). No triage test failed or was skipped in the full run.
- Default-worker full `npm run test` was not run (12–20 min and known load timeouts per the dispatcher); the 2-worker run is the recorded outcome.
- Slow: `TriageHistoryPage.test.tsx` first `findBy` waits use 5 s timeouts for the lazy page (guideline 10 §6), acceptable.
- Not independently verified: the DoD browser check at 1366 × 768 (no browser tool in this evaluation).
