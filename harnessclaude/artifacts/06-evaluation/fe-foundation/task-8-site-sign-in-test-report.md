# Test report: fe-foundation Task 8 (site-sign-in), attempt 2

**Date:** 2026-10-26
**Commands, run in `frontend/` one after another:**
`npx vitest run src/app/router src/shared/api src/shared/auth src/app/providers src/shared/messages src/test`
(default + JSON reporter) · `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run build` ·
`npm run generate:api`, run twice, with a hash of every file under `src/shared/api/generated` taken before and after ·
`bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-011 STORY-012 STORY-001 STORY-010`

## Results
- Targeted run: `Test Files 19 passed (19)`, `Tests 486 passed (486)`, 36.3 s. 0 failed, 0 skipped.

  | File | Passed | Note |
  |---|---|---|
  | src/shared/auth/sign-in.test.ts | 67/67 | QA, unchanged |
  | src/shared/api/http.test.ts | 13/13 | QA, unchanged |
  | src/shared/api/background.test.tsx | 10/10 | QA, unchanged |
  | src/app/router/site-state.test.tsx | 11/11 | QA, unchanged |
  | src/shared/auth/useSession.test.tsx | 12/12 | QA, unchanged |
  | src/app/providers/global-errors.test.tsx | 10/10 | QA, unchanged |
  | src/test/render.test.tsx | 6/6 | QA, unchanged |
  | src/test/guidelines.test.ts | 56/56 | QA, unchanged |
  | src/app/router/site-state.own-errors.test.tsx | 3/3 | new (BUG-019) |
  | src/app/router/guards.guest-only.test.tsx | 3/3 | new (choice 1) |
  | src/shared/messages/show.test.tsx | 5/5 | new (choice 2, `showMessage` guard) |
  | 8 other files in these folders | 293/293 | not Task 8 |

  `src/app/dev/DevUserSwitcher.test.tsx` (QA, 6) is outside the card's folders. Its file is unchanged since QA
  step 2 (mtime 02:51, earlier than the attempt-1 review at 03:24).
- format:check: "All matched files use Prettier code style!"
- lint: `eslint .` exit 0
- typecheck: `tsc -b --noEmit` exit 0
- build: exit 0, "✓ built in 1.41s" (the antd chunk is 805.55 kB, as before)
- generate:api: exit 0 both times. `git status` / `git diff` of `generated/` and a hash of every file in it (the
  untracked `signInParams.ts` included) are the same before and after: "all generated files identical". The
  exported names are `get…QueryOptions`, and there are 0 `use…QueryOptions`.
- Not run (card scope): the full suite. The developer's run: 35 files, 669 tests passed.

## Coverage
No coverage command was run. The spec sets no threshold (NFR 1: one named test per AC).

## Acceptance Criteria → Tests
```
  ✓ AC-001.1  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.2  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.3  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.4  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.5  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.6  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.7  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.8  frontend/src/shared/auth/session.test.ts, frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-010.1  frontend/src/test/guidelines.test.ts
  ✓ AC-010.2  frontend/src/test/guidelines.test.ts
  ✓ AC-010.3  frontend/src/test/guidelines.test.ts
  ✓ AC-010.4  frontend/src/test/guidelines.test.ts
  ✓ AC-011.1  frontend/src/shared/api/http.test.ts
  ✓ AC-011.2  frontend/src/test/guidelines.test.ts, frontend/src/shared/api/http.test.ts
  ✓ AC-011.3  frontend/src/app/router/site-state.test.tsx
  ✓ AC-011.4  frontend/src/app/router/site-state.test.tsx
  ✓ AC-011.5  frontend/src/shared/messages/show.test.tsx, frontend/src/app/router/site-state.own-errors.test.tsx, frontend/src/app/router/site-state.test.tsx
  ✓ AC-011.6  frontend/src/test/render.test.tsx, frontend/src/app/dev/DevUserSwitcher.test.tsx
  ✓ AC-012.1  frontend/src/shared/auth/sign-in.test.ts
  ✓ AC-012.2  frontend/src/shared/auth/sign-in.test.ts
  ✓ AC-012.3  frontend/src/shared/auth/sign-in.test.ts
  ✓ AC-012.4  frontend/src/shared/auth/sign-in.test.ts
  ✓ AC-012.5  frontend/src/app/router/guards.guest-only.test.tsx, frontend/src/app/providers/global-errors.test.tsx
  ✓ AC-012.6  frontend/src/shared/api/background.test.tsx, frontend/src/shared/api/http.test.ts
  ✓ AC-012.7  frontend/src/shared/auth/sign-in.test.ts, frontend/src/shared/api/background.test.tsx, frontend/src/shared/api/http.test.ts
trace fe-foundation (STORY-011, STORY-012, STORY-001, STORY-010): 25/25 acceptance criteria have a test
```
Both attempt-1 gaps are closed. The `handlesOwnErrors` mutation path of AC-011.5 is tested in
`site-state.own-errors.test.tsx`, and `/me` 401 MSG123 on a directly opened Login (AC-012.5) in
`guards.guest-only.test.tsx`.

## Failures
None.

## Notes
- No flaky, skipped or slow (> 5 s) tests in the JSON report.
- Logs (outside the repo):
  `C:\Users\ADMIN\AppData\Local\Temp\claude\C--Users-ADMIN-Documents-GitHub-T-Solve\89993de5-ffbe-4a3c-b6d0-b5847c41d157\scratchpad\`
  (`vitest.log`, `vitest.json`, `format-check.log`, `lint.log`, `typecheck.log`, `build.log`, `generate.log`,
  `trace.log`).
