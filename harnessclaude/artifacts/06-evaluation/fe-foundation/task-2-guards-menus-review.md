---
module: fe-foundation
feature: guards-menus
task: 2
verdict: fail
attempt: 1
date: 2026-10-22
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: fe-foundation — Task 2: Guards, landing page, Workspace Setup redirect and menus

## Summary
The code meets STORY-002, STORY-003 and STORY-004: the any-role guard, `landingPath(roles)`, `RequireWorkspace`,
the per-role `MENUS`, and `menuFor(roles)` in the D4 order all match the spec. Every criterion has a test that
checks it. Format, lint, typecheck and build pass. The verdict is **fail** because the full suite failed in 1 of
3 runs. The failing test is in `ReviewQueuePage.test.tsx`, the file BUG-004 was about, and it times out the same
way. So the BUG-004 fix is incomplete: the file is still flaky in the full suite, and `docs/bug-log.md` marks it
`Fixed` too soon (NFR 2, NFR 5).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1, all 13 PASS. AC-002.1 `hasAnyRole` (`guards.tsx:22-26`); AC-002.2 403 in place, URL kept; AC-002.3 `returnTo` (`guards.tsx:14-17`); AC-002.4 and AC-003.1 `landingPath` (`landing.ts:5-15`), first match Admin → PM → KM → TR → Chatbot; AC-003.2/3 `RequireWorkspace` (`guards.tsx:43-62`) only for `ADMIN`, except `/workspace-setup` and `/profile`; Login is outside it (GuestOnly); AC-003.4 error page before any redirect (RequireAuth); AC-004.1 `MENUS` matches the spec lists item by item; AC-004.2 `menuFor` (`menu-config.ts:96-110`) in order A, PM, KM, TR, E, first entry kept; AC-004.3/4 `selectedMenuPath` longest segment prefix, or null |
| Completeness | pass | No MISSING. Design edge rules hold by construction: the `*` 404 and `/dev/kit` routes sit outside `RequireWorkspace` (`routes.tsx:52-76`), so they are not redirected; the session error page appears before any redirect. No EXTRA behaviour. `LANDING_PATHS` and `mainRole` were removed and nothing else uses them (grep). `AppShell.tsx` needed no change (it already passes `session.roles`) |
| Correctness | fail | Full suite run 1: 154/155. `ReviewQueuePage.test.tsx:50` `shows the error in place, with Try again, when the queue cannot be loaded` did not find `role="alert"` within 1193 ms. Runs 2 and 3 passed 155/155, and the file passes alone. The BUG-004 fix (`ReviewQueuePage.test.tsx:20-24`) covers only the first test |
| Integration | pass | `@/shared/auth` exports `landingPath(roles)` as the spec's interface contract says (`index.ts:10`). `SideMenu` takes `roles`. The route-table test (Task 3, AC-002.5) still passes. `/me` and `generate:api` are untouched. Security: the guards only hide screens (the backend re-checks); no secret or dangerous pattern; `npm audit` 0; no Critical or High finding |

## Traceability
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

## Issues

CRITICAL (blocks pass):
1. [`frontend/src/features/review/pages/ReviewQueuePage.test.tsx:50`] **High.** A test fails now and then in the
   full suite (1 of 3 runs, `Unable to find role="alert"` after about 1.2 s). The cause is the same as BUG-004.
   The `beforeAll` pre-import (`:20-24`) only warms the page module. Each test still renders the shell, `/me` and
   the queue request under parallel load, and `:50` (and `:44`, `shows MSG16 …`) wait only the default 1 s.
   Fix: give the first `findBy` after each `renderApp` in this file `{ timeout: 5000 }`, which guideline 10 §6
   allows (do not go higher), for example through a small helper. Name the fixed test with the bug id (reopen
   BUG-004, or add a new bug row), then run the full suite several times.
2. [`docs/bug-log.md:32`] **Medium.** BUG-004 is marked `Fixed`, but the file still flakes. Its Notes say "fixed
   without a longer timeout", which covers only the first test. Fix: when issue 1 is fixed, update the row (cause
   and Notes), and fill in "Fixed in" with the PR once it is opened (bug-log step 5).

Informational (does not block):
3. [`frontend/src/app/router/guards.tsx:44,57`] **Low.** `OPEN_BEFORE_SETUP.includes(pathname)` is an exact match.
   A trailing slash (`/profile/`), which React Router still matches to My Profile, sends the Admin to Workspace
   Setup. Fix: normalize the trailing slash, or use `matchPath`.
4. [`frontend/src/app/router/guards.test.tsx`] **Low.** No test covers the design's edge rules while the
   Workspace is not set up: an unknown path shows 404, and `/dev/kit` is not redirected. Both hold in
   `routes.tsx:52-76` today. Fix: add one test so a later reshuffle of the route tree cannot break them.

## Test Report
`task-2-guards-menus-test-report.md`. Format: pass. Lint: pass. Typecheck: pass. Build: pass (`✓ built in 1.36s`).
Full suite: run 1 154/155 (1 failed, exit 1), runs 2 and 3 155/155 (exit 0). The Review Queue file passes alone,
9/9. Trace 13/13. No coverage script, and the spec sets no threshold. `npm audit --omit=dev`: 0 vulnerabilities.

## Assumptions made
- **AC-003.2, "or Login".** A signed-in Admin who opens `/login` is sent to the landing page (AC-002.4, User List)
  and from there to Workspace Setup. I took the Login exception to mean "Login itself is not replaced by the
  redirect". Showing the Login page to a signed-in Admin would break AC-002.4. I counted this as PASS; the user
  should confirm.
- **Flaky test as a blocker.** I treated a test that fails intermittently, in the file BUG-004 was about, as a
  failing test (the evaluate rule), even though it is not the test named `(BUG-004)`. It is unknown whether the
  same flake happens on `main` (not checked, to avoid changing the working tree). The user may decide it is a
  separate, pre-existing bug and pass this task once it is logged.
- **Reopen or new bug.** Whether to reopen BUG-004 or log a new bug is the user's call.
