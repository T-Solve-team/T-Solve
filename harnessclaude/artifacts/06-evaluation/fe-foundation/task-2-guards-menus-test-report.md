# Test Report: fe-foundation — Task 2: guards-menus

**Date:** 2026-10-22
**Code:** `default: 8a1b1e7..03f8f8c` (branch `feature/KAN-60-fe-foundation-guards`, `frontend/` and `docs/bug-log.md`)
**Commands (run by the reviewer in `frontend/`, from `package.json` scripts):** `npm run format:check` · `npm run lint` ·
`npm run typecheck` · `npm run test` (3 full runs) · `npm run build` · `npx vitest run src/features/review/pages/ReviewQueuePage.test.tsx`
(file alone) · from the repo root `bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-002 STORY-003 STORY-004` ·
coverage: no script in `package.json`

## Results
- Full suite, run 1: Total 155 · Passed 154 · **Failed 1** · Skipped 0 · Duration 57.6s (20 files, exit 1)
- Full suite, run 2: Total 155 · Passed 155 · Failed 0 · Skipped 0 · Duration 58.2s (exit 0)
- Full suite, run 3 (extra, to characterize the flake): Total 155 · Passed 155 · Failed 0 · Duration 59.8s (exit 0)
- `ReviewQueuePage.test.tsx` alone: 9/9 passed
- Format check: pass (`All matched files use Prettier code style!`, exit 0)
- Lint: pass (`eslint .`, no output, exit 0)
- Typecheck: pass (`tsc -b --noEmit`, exit 0)
- Build: pass (`tsc -b && vite build`, `✓ built in 1.36s`, exit 0)
- `generate:api`: not run (writes files; not in the dispatched list); `frontend/openapi/**` and
  `src/shared/api/generated/**` are untouched in `8a1b1e7..03f8f8c`, so no diff is expected
- Security: `npm audit --omit=dev` → `found 0 vulnerabilities`; no secret or dangerous pattern in the diff; only
  `.env.example` files tracked

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
### `frontend/src/features/review/pages/ReviewQueuePage.test.tsx` — shows the error in place, with Try again, when the queue cannot be loaded
- Error (run 1 only): `TestingLibraryElementError: Unable to find role="alert"` at
  `ReviewQueuePage.test.tsx:50:25` (`expect(await screen.findByRole('alert'))…`), after 1193 ms; the DOM shows the
  AppShell with the menu (Review Queue selected) but no error alert yet. The file took 44.2 s in that run.
- Cause: the same timing problem as BUG-004, in another test of the same file. The BUG-004 fix (`beforeAll` that
  pre-imports the page module, `:20-24`) only warms the module; each test still renders the AppShell, `/me` and the
  queue request under a parallel full suite. This test's first `findBy` after `renderApp` uses the 1 s default,
  so it fails when the machine is busy. `:44` (`shows MSG16 when no tickets are waiting`) has the same pattern.
- Suggested fix: give the first `findBy` after each `renderApp` in this file `{ timeout: 5000 }` (what guideline 10
  §6 allows for a fresh render — no higher), e.g. a small helper used by `:44` and `:50`; then either reopen BUG-004
  or log a new bug row for this test, name the fixed test with that id, and re-run the full suite several times.

## Notes
- Flaky: 1 failure in 3 full runs (pass/fail flip on identical code, passes alone) → timing, not logic.
- The test named `(BUG-004)` passed in all 3 runs; 3 runs cannot prove an intermittent bug (2 of 6 on 23/10) fixed,
  and its "fails before the fix" step cannot be reproduced on demand.
- No skipped tests. Slow: `ReviewQueuePage.test.tsx` (44 s for 9 tests in run 1) — known, guideline 10 §6.
- Stderr noise: jsdom `getComputedStyle() with pseudo-elements` not implemented (antd) — harmless, pre-existing.
