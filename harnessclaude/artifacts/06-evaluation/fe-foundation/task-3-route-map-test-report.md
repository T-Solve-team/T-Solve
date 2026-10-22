# Test Report: fe-foundation — Task 3: route-map

**Date:** 2026-10-22
**Code:** `default: b902ab0..02e9fc3` (branch `feature/KAN-61-fe-foundation-routes`, `frontend/`)
**Commands (run by the reviewer in `frontend/`, from `package.json` scripts):** `npm run format:check` · `npm run lint` ·
`npm run typecheck` · `npm run test` · `npm run build` · from the repo root
`bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-005` (and `STORY-002` for AC-002.5) · coverage: no script in `package.json`

## Results
- Total: 132 · Passed: 132 · Failed: 0 · Skipped: 0 · Duration: 38.4s (18 test files, `vitest run`, exit 0) — first run,
  no `findBy` timeout, so no re-run was needed
- Format check: pass (`All matched files use Prettier code style!`, exit 0)
- Lint: pass (`eslint .`, no output, exit 0)
- Typecheck: pass (`tsc -b --noEmit`, exit 0)
- Build: pass (`tsc -b && vite build`, `✓ built in 769ms`, exit 0; one lazy chunk per new placeholder page)
- Re-run of `src/app/router/route-table.test.tsx` with `--reporter=verbose`: 45/45 passed (1 AC-005.1, 27 AC-002.5, 9
  AC-005.2, 7 AC-005.3, 1 AC-005.4); slowest is the first AC-005.3 case (931 ms, lazy module load)
- `generate:api`: not run (not in the dispatched command list, and it writes files); `frontend/openapi/**` and
  `src/shared/api/generated/**` are untouched in `b902ab0..02e9fc3`, so no diff is expected

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured — the project has no coverage script | none in the spec's non-functional requirements |

## Acceptance Criteria → Tests
```
  ✓ AC-005.1  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.2  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.3  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.4  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.5  frontend/src/app/router/routes.test.tsx
trace fe-foundation (STORY-005): 5/5 acceptance criteria have a test

  ✓ AC-002.5  frontend/src/app/router/route-table.test.tsx
  ✗ AC-002.1 – AC-002.4  no test yet — Task 2 (guards-menus, KAN-60), which runs after this task
trace fe-foundation (STORY-002): 1/5 acceptance criteria have a test
```

## Failed Tests
None.

## Notes
- jsdom prints `Not implemented: Window's getComputedStyle() method: with pseudo-elements` (antd); noise only, no failure.
- The known first-`findBy` timeout under full-suite load (separately logged bug) did not occur in this run.
