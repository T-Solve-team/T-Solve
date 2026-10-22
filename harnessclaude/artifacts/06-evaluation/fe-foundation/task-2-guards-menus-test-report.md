# Test Report: fe-foundation — Task 2: guards-menus (attempt 2)

**Date:** 2026-10-22
**Code:** `default: 8a1b1e7..2381c5f` (branch `feature/KAN-60-fe-foundation-guards`; the attempt-1 fix is `03f8f8c..2381c5f`)
**Commands (run by the reviewer in `frontend/`, from `package.json` scripts):** `npm run format:check` · `npm run lint` ·
`npm run typecheck` · `npm run build` · `npm run test` (3 full runs) · `npm audit --omit=dev` · from the repo root
`bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-002 STORY-003 STORY-004` · coverage: no script in `package.json`

## Results
- Full suite, run 1: Total 158 · Passed 158 · Failed 0 · Skipped 0 · Duration 49.0s (20 files, exit 0)
- Full suite, run 2: Total 158 · Passed 158 · Failed 0 · Skipped 0 · Duration 57.8s (20 files, exit 0)
- Full suite, run 3: Total 158 · Passed 158 · Failed 0 · Skipped 0 · Duration 58.0s (20 files, exit 0)
- Test count 155 → 158: the three new `guards.test.tsx` tests (trailing slash, 404, `/dev/kit` while the Workspace
  is not set up)
- Format check: pass (`All matched files use Prettier code style!`)
- Lint: pass (`eslint .`, no output, exit 0)
- Typecheck: pass (`tsc -b --noEmit`, exit 0)
- Build: pass (`tsc -b && vite build`, `✓ built in 756ms`, exit 0)
- `generate:api`: not run (writes files; not in the dispatched list); `frontend/openapi/**` and
  `src/shared/api/generated/**` are untouched in `8a1b1e7..2381c5f`, so no diff is expected
- Security: `npm audit --omit=dev` → `found 0 vulnerabilities`; no secret or dangerous pattern in the diff

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured — the project has no coverage script | none in the spec's non-functional requirements |

## Acceptance Criteria → Tests
```
  ✓ AC-002.1  frontend/src/app/router/guards.test.tsx
  ✓ AC-002.2  frontend/src/app/router/guards.test.tsx, frontend/src/app/router/routes.test.tsx
  ✓ AC-002.3  frontend/src/app/router/routes.test.tsx
  ✓ AC-002.4  frontend/src/app/router/routes.test.tsx
  ✓ AC-002.5  frontend/src/app/router/route-table.test.tsx
  ✓ AC-003.1  frontend/src/shared/auth/landing.test.ts, frontend/src/app/router/guards.test.tsx, frontend/src/app/router/routes.test.tsx
  ✓ AC-003.2  frontend/src/app/router/guards.test.tsx
  ✓ AC-003.3  frontend/src/app/router/guards.test.tsx
  ✓ AC-003.4  frontend/src/app/router/guards.test.tsx
  ✓ AC-004.1  frontend/src/app/layout/menu-config.test.ts
  ✓ AC-004.2  frontend/src/app/layout/menu-config.test.ts
  ✓ AC-004.3  frontend/src/app/router/routes.test.tsx, frontend/src/app/layout/menu-config.test.ts
  ✓ AC-004.4  frontend/src/app/layout/menu-config.test.ts
trace fe-foundation (STORY-002, STORY-003, STORY-004): 13/13 acceptance criteria have a test
```

## Failures
None in 3 of 3 full runs.

## Notes
- Flakiness: the attempt-1 flake (`ReviewQueuePage.test.tsx:50`, 1 of 3 runs) did not recur in 3 runs. Its first
  `findBy` after `renderApp` now waits 5 s (`:51`), as does `:45`; every other test in the file goes through
  `openQueue` (5 s). Vitest now runs files on half the cores (`maxWorkers: '50%'`, approved by the FE Lead).
  Three clean runs lower, but cannot prove away, an intermittent failure.
- No skipped tests. Stderr noise: jsdom `getComputedStyle() with pseudo-elements` not implemented (antd) — harmless,
  pre-existing.
