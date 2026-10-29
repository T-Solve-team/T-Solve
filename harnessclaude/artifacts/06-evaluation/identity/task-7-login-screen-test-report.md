# Test Report: identity — Task 7: login-screen (attempt 3)

**Date:** 2026-10-29
**Commands (in `frontend/`):** targeted `npm run test -- src/shared/auth src/features/auth src/app/dev/MockSignInPage.test.tsx src/app/router` · `npm run format:check` · `npm run generate:api` (md5 before/after). Attempt 2 also ran `npm run lint` · `npm run typecheck` · `vite build --outDir <temp>` (production bundle check).
Not re-run by the reviewer: `npm run build` into `dist/`, full `npm run test` (developer, 29/10: 786 passed, 2 known failures: `UserDetailPage` AC-005.7 fails on main locally, `UserListPage` AC-004.8 flaky; neither file is in this task).

## Results
- Attempt 3 targeted: Test Files 15 passed (15) · Tests 275 passed (275) · Failed 0 · Skipped 0 · Duration 28 s · exit 0
- Attempt 3 format: `prettier --check .` exit 0
- Attempt 3 `npm run generate:api`: exit 0; md5 of the files in `src/shared/api/generated` and of `git status --short` the same before and after -> no diff
- Attempt 2 (not re-run in attempt 3, scope was findings A, B, C): typecheck pass, lint pass, 274 tests passed, production bundle check exit 0: no `dev/sign-in`, `Mock sign-in` or `company.example` in any output file; `Sign in with Google` only in the `LoginPage` chunk. This confirms `import.meta.env.DEV && env.apiMocking` in `sign-in.ts` is folded by the build.

## Coverage
Not measured; the spec sets no threshold for this task.

## Acceptance Criteria -> Tests
```
  ✓ AC-001.40  frontend/src/features/auth/pages/LoginPage.test.tsx
  ✓ AC-001.41  frontend/src/features/auth/pages/LoginPage.test.tsx
  ✓ AC-001.42  frontend/src/features/auth/pages/LoginPage.test.tsx
  ✓ AC-001.43  frontend/src/features/auth/pages/LoginPage.test.tsx
trace identity (STORY-001): 4/43 acceptance criteria have a test
```
Edge 39 and 40 have their own named tests in `LoginPage.test.tsx`. Mock sign-in tests cite AC-012.1 / AC-011.6. The contract shape tests (`sign-in.test.ts`, `AC-012.1 contract: …`) check `code`, `error`, 400 `text/html`, 404, 200, `Set-Cookie`, `__Host-TSOLVE_SESSION`, `/login?error=MSG141`, the `servers` URL, and (new) that the callback names MSG120 and MSG135 and not MSG123.

## Failures
None.

## Notes
- `.env.local` sets `VITE_API_MOCKING=true`; `vite.config.ts` `test.env` forces `'false'`, so the run did not depend on it.
- Attempt 2 finding A is fixed: the contract assertion exists (`sign-in.test.ts:252`).
- Attempt 2 finding B is fixed: the `pageshow` test (`LoginPage.test.tsx:198-214`) clicks again and expects a second `navigateTo`. No `persisted: false` case (informational, not asked for).
- Real full-page navigation and the 1366 x 768 look are not coverable in jsdom (browser check, not done by this review).
