# Test Report: fe-foundation — Task 1: roles-session

**Date:** 2026-10-22
**Code:** `default: 663f3e6..d9bfc7b` (branch `feature/KAN-59-fe-foundation-roles`, `frontend/`)
**Commands (run by the reviewer in `frontend/`, from `package.json` scripts):** `npm run format:check` · `npm run lint` ·
`npm run typecheck` · `npm run test` · `npm run build` · from the repo root
`bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-001 STORY-008 STORY-009` · coverage: no script in `package.json`

## Results
- Total: 87 · Passed: 87 · Failed: 0 · Skipped: 0 · Duration: 50.6s (17 test files, `vitest run`)
- Format check: pass (`All matched files use Prettier code style!`, exit 0)
- Lint: pass (`eslint .`, no output, exit 0)
- Typecheck: pass (`tsc -b --noEmit`, exit 0)
- Build: pass (`tsc -b && vite build`, `✓ built in 767ms`, exit 0)
- Re-run of the task's ten test files with `--reporter=verbose`: 41/41 passed — same result, no flakiness seen.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured — the project has no coverage script | none in the spec's non-functional requirements |

## Acceptance Criteria → Tests
```
  ✓ AC-001.1  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.2  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.3  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.4  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.5  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.6  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.7  frontend/src/shared/auth/session.test.ts
  ✓ AC-008.1  frontend/src/shared/auth/sign-out.test.tsx
  ✓ AC-008.2  frontend/src/shared/auth/sign-out.test.tsx
  ✓ AC-008.3  frontend/src/shared/auth/sign-out.test.tsx
  ✓ AC-009.1  frontend/src/mocks/session.test.ts
  ✓ AC-009.2  frontend/src/mocks/session.test.ts, frontend/src/app/dev/DevUserSwitcher.test.tsx
  ✓ AC-009.3  frontend/src/test/render.test.tsx
  ✓ AC-009.4  frontend/src/test/render.test.tsx
  ✓ AC-009.5  frontend/src/mocks/session.test.ts
  ✓ AC-009.6  frontend/src/app/dev/mock-mode.test.tsx
trace fe-foundation (STORY-001, STORY-008, STORY-009): 16/16 acceptance criteria have a test
```

## Failures
None.

## Notes
- Slow tests (over 2s): `mock-mode.test.tsx` "AC-009.6 starts no mock server…" 3.2s (loads the real `main.tsx`);
  `global-errors.test.tsx` "shows a failed mutation as a toast…" 2.3s. Both are under the 5s mark.
- jsdom prints `Not implemented: Window's getComputedStyle() method: with pseudo-elements` (antd measuring). This is
  noise, not a failure.
- Production-build half of AC-009.6: after `npm run build`, a grep of `frontend/dist/assets/*.js` and
  `dist/*.html` for `Dev user`, `DevUserSwitcher`, `tsolve.dev.user`, `mockServiceWorker`, `faker` and `Signed out`
  found nothing, and no dev/mock chunk was emitted. So the switcher and the mocks are not in the production bundle.
- `npm run generate:api` was not run: it rewrites `src/shared/api/generated/` in the working tree, and the reviewer
  writes no code. By inspection, the regenerated `currentUser.ts`, `role.ts`, `spaceRef.ts`, `index.ts` and
  `session.msw.ts` match the `openapi/tsolve-api.yaml` change. CI checks this.
- `npm audit`: 0 vulnerabilities. No new dependency (`package.json` unchanged in the range).
- Open question for the user: there is no coverage measurement and the spec sets no threshold. Is that acceptable?
