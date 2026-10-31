# Task 8: Workspace site, central sign-in, MSG123 and the background header

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** in-progress (code and verify done; review pending)
**Iteration:** 1

---

## Session 2026-10-26

### Working on: site-sign-in (KAN-206, branch `feature/KAN-206-fe-foundation-site-sign-in`)
- Stories STORY-011, STORY-012, STORY-001, STORY-010; AC-011.1–AC-011.6, AC-012.1–AC-012.7, AC-001.6, AC-010.3; design edge cases 21–28. Change request KAN-198.
- Same origin only: `http()` calls the relative `/api/v1/…` path; `env.apiBaseUrl`, `VITE_API_BASE_URL` (env, `.env.example`, Dockerfile build arg, README, Caddyfile comment, guidelines 06 and 11, `vite-env.d.ts`) are gone. Vite accepts `.localhost` hosts and the `/api` proxy sets `changeOrigin: false`.
- Contract: `GET /api/v1/auth/sign-in` (`signIn`, optional `returnTo`, 302) in `tsolve-api.yaml`; client regenerated. orval `override.query.queryOptions` = `withBackgroundMarking` (`shared/api/query-options.ts`).
- `shared/api`: `background.ts` (`markBackground`, `isBackground`, header name), `site-state.ts` (store `{ site, foreignSession }`, `useSiteStatus`, `resetSiteState`, set by `http()` for MSG119 / MSG120 / 401 MSG123). `http()` adds `X-TSolve-Background: 1` for a marked signal.
- `shared/auth`: `startSignIn(returnTo?)` through `navigateTo` (`shared/lib/navigate.ts`), same-site check as a character loop equal to the design regex; `useSignInMessage()` (`useSearchParams`, allow-list, `google_domain` check, lower-cased, `params: {}` without placeholders). `fetchSession` no longer turns a 401 MSG123 into "signed out"; `useSession().isError` is false for MSG119 / MSG120 / MSG123.
- App: `SiteUnavailablePage`; `RootLayout` shows it in place of the outlet while the site is not `ok`; `RequireAuth` goes to `/login?error=MSG123` when the cookie is of another site; `global-errors.ts` skips MSG119 / MSG120 (no toast), resets the session once per site status, and lets a 401 MSG123 win over MSG07 (an open MSG07 dialog is closed with `Modal.destroyAll()`).
- Mocks and tests: dev user switcher offers "Unknown site" / "Suspended Workspace" and calls `resetSiteState()` on every pick; `renderApp` / `renderWithProviders` accept `{ site }`; `src/test/setup.ts` resets the site state after each test.
- Docs: guidelines 06 (header, error table), 08 §8, 11, 12, 13 (FE-43, FE-09 note).

### Decisions and findings
- Q1–Q12 as on the card (all in spec/design). Q10: the contract test skips tag `platform-console` and paths under `/api/v1/platform-console`; the Console's real tag/prefix is still open (QA note).
- MSG123 redirect is done by the route guard from the site-state store, not by an imperative navigate: `http()` records the 401 MSG123 before any query error reaches React, so the first-load `/me` case has no race with the guard's own `returnTo` redirect.
- `src/test/setup.ts` now imports the app modules inside `beforeAll` (references kept for `afterEach`/`afterAll`). Reason: Vitest caches what a setup file imports, so the QA test's `vi.mock('@/shared/lib/navigate')` never reached `shared/auth` (the real `navigateTo` ran; 27 of the 67 sign-in tests failed). The references are kept because `http.test.ts` calls `vi.resetModules()`.
- Files changed that Task 8's list does not name: `src/vite-env.d.ts` (the guidelines test reads it), `src/shared/api/site-state.ts` (new, the store of the design), and an `eslint-disable-next-line no-restricted-imports` for `Modal` in `global-errors.ts` (needed for `Modal.destroyAll()`; `shared/messages` is outside the card's scope). `AGENTS.md` needed no change.
- Not covered by a test: `.env.example`, Dockerfile, Caddyfile and README no longer mention `VITE_API_BASE_URL` (checked with grep); Vite `allowedHosts` / `changeOrigin` (browser check).
- No bug found, so no `docs/bug-log.md` row.

```
trace fe-foundation (STORY-011, STORY-012, STORY-001, STORY-010): 25/25 acceptance criteria have a test
```

### Verify (frontend, one after another)
- `npm run format:check` — All matched files use Prettier code style! (3 s)
- `npm run lint` — no output, exit 0 (13 s)
- `npm run typecheck` — exit 0 (6 s)
- `npm run test` — Test Files 32 passed (32), Tests 658 passed (658) (176 s)
- `npm run build` — ✓ built in 2.70s (10 s)
- `npm run generate:api` — no further diff in `src/shared/api/generated` (a second run gives the same files) (3 s)

### Hand-off (2026-10-26)
- done: Workspace site page (MSG119 / MSG120), central sign-in (`startSignIn`, `useSignInMessage`), MSG123 path, background header, same-origin API, mocks, docs; all QA tests green unchanged
- open: review; the Platform Console tag/prefix for the AC-011.2 contract test; browser check of `<site name>.localhost:5173` (Host kept by the proxy)
- next: Task 9 host-routes (KAN-…)
- branch: `feature/KAN-206-fe-foundation-site-sign-in`
- PR: not opened yet

### Fix 1 (2026-10-26, after the review, attempt 1)
Quick change against AC-011.5 / AC-012.5; no criterion changed. Bug: BUG-019 (`docs/bug-log.md`).
- Major (BUG-019): the first MSG119 / MSG120 answering a `handlesOwnErrors` mutation left the session and cache in memory. Failing test first: `site-state.own-errors.test.tsx` (3 tests, red: the session stayed, `clear` never called). Fixed in `shared/api/query-client.ts` `handle()`: MSG119 / MSG120 go to a new `onSiteUnavailable` handler before the `handlesOwnErrors` check (queries and all mutations); `global-errors.ts` passes `endSessionOnce` to it and no longer needs `handleError`. `showMessage` ignores MSG119 / MSG120, so a screen's own `showMessage(error.code)` shows no toast. Chosen over a store subscription: no listener per client to leak.
- Choice 1: `GuestOnly` sends a user with the foreign-session flag to `/login?error=MSG123` (not when `error=MSG123` is already there). Test: `guards.guest-only.test.tsx` (red first).
- Choice 2: `dismissAcknowledgement(code)` in `shared/messages/show.ts` closes only that code's dialog (and resolves its promise, no leave animation). `global-errors.ts` no longer imports `Modal`; the eslint-disable is gone. Test: `shared/messages/show.test.tsx`.
- Choice 3: `useHooks: false` on the orval `queryOptions` mutator in `orval.config.ts`; the generated names are `get…QueryOptions` again. Guideline 06 §3 and §6 say so.
- Verify: format:check, lint, typecheck, test (35 files, 669 tests, 181 s), build, generate:api (no change on a second run), trace 25/25.

### Hand-off (review attempt 2, 2026-10-26)
- done: review attempt 2 PASS (nits only); BUG-019 fixed (`9e697bb`, test `3b37736`); feature `site-sign-in` set to passing
- open: `signIn` / `useSignIn` exported but unused; no `returnTo` length limit; BUG-019 test uses a stand-in mutation; Platform Console prefix for the AC-011.2 contract test
- next: fe-foundation Task 9 host-routes (KAN-207)
- branch: `feature/KAN-206-fe-foundation-site-sign-in`
- PR #76 ready to merge (https://github.com/T-Solve-team/T-Solve/pull/76)
