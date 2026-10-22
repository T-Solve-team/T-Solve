---
module: fe-foundation
feature: guards-menus
task: 2
verdict: pass
attempt: 2
date: 2026-10-22
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: fe-foundation — Task 2: Guards, landing page, Workspace Setup redirect and menus

## Summary
All four attempt-1 issues are resolved in `03f8f8c..2381c5f`. The Review Queue tests that waited only 1 s after a
render now wait 5 s (guideline 10 §6, no higher), Vitest runs files on half the cores (FE Lead approved), and the
BUG-004 row explains the full fix. `RequireWorkspace` now strips a trailing slash, and three new tests cover the
trailing slash, the 404 page and `/dev/kit` while the Workspace is not set up. Format, lint, typecheck and build
pass. The full suite passed 158/158 in 3 of 3 runs. Trace: 13/13. STORY-002, STORY-003 and STORY-004 still hold as
verified in attempt 1. One new Low, informational finding (case-insensitive paths). It does not block.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Unchanged from attempt 1 apart from `guards.tsx:53`: `pathname.replace(/\/+$/, '') \|\| '/'`. So `/profile/` and `/workspace-setup/` stay open before setup, `/` maps to `/` and is still redirected for an Admin, and non-Admins are not affected (AC-003.2, AC-003.3). AC-002.1–AC-004.4 as verified in attempt 1 (any-role guard, 403 in place, `returnTo`, `landingPath` first match, `RequireWorkspace`, `MENUS`, `menuFor` order, `selectedMenuPath`) |
| Completeness | pass | No MISSING. The design's edge rules now have tests: `guards.test.tsx` "still shows 404 for an unknown path…" and "does not redirect the dev kit page…". Both would fail if a redirect changed the pathname. No EXTRA behaviour: the fix adds only the slash normalization, test waits, and the `maxWorkers` setting |
| Correctness | pass | Full suite 158/158 in 3 of 3 runs (exit 0). `ReviewQueuePage.test.tsx:45` and `:51` now give their first `findBy` 5 s. All other tests in the file use `openQueue` (5 s, `:15`), and the `beforeAll` pre-import stays. `vite.config.ts:55` `maxWorkers: '50%'` is a valid top-level option in Vitest 5 |
| Integration | pass | No interface change since attempt 1: `@/shared/auth` exports `landingPath(roles)`, and `SideMenu` takes `roles`. The route-table test (AC-002.5) passes. `/me`, `openapi/**` and `generated/**` are untouched. Security: the guards only hide screens (the backend re-checks); no secret or dangerous pattern; `npm audit --omit=dev` 0 |

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

Attempt-1 issues:
1. `ReviewQueuePage.test.tsx:50` flake: **resolved.** `:45` and `:51` wait 5 s, and `maxWorkers: '50%'` was added.
   No failure in 3 full runs.
2. `docs/bug-log.md:32` BUG-004 marked Fixed too soon: **resolved.** The Notes now describe the three parts of the
   fix and cite this review. "Fixed in" stays empty until the PR is opened (bug-log step 5).
3. `guards.tsx` trailing slash: **resolved** (`guards.tsx:53`, test "treats a trailing slash as the same screen").
4. No test for 404 / `/dev/kit` before setup: **resolved** (two new tests in `guards.test.tsx`).

New, informational (does not block):
5. [`frontend/src/app/router/guards.tsx:53,58`] **Low.** React Router matches paths case-insensitively by default
   (no `caseSensitive` in `routes.tsx`). So `/Profile` renders My Profile, but `OPEN_BEFORE_SETUP.includes` is
   case-sensitive and sends an Admin to Workspace Setup. Nothing links to such a URL, and the Admin can still set
   up the Workspace. Suggested fix, if wanted: compare `screen.toLowerCase()`, or use `matchPath` for each open
   path. It could be logged as a bug.

## Test Report
`task-2-guards-menus-test-report.md`. Format: pass. Lint: pass. Typecheck: pass. Build: pass (`✓ built in 756ms`).
Full suite: 158/158 in runs 1, 2 and 3 (exit 0; 49.0 s, 57.8 s, 58.0 s). Trace: 13/13. No coverage script, and the
spec sets no threshold. `npm audit --omit=dev`: 0 vulnerabilities.

## Assumptions made
- **AC-003.2, "or Login"** (carried from attempt 1, not yet confirmed). A signed-in Admin who opens `/login` is sent
  to the landing page (AC-002.4) and from there to Workspace Setup. I read the Login exception as "Login itself is
  not replaced by the redirect". I counted this as PASS; the user should confirm.
- **Flake judged by 3 runs.** Three clean full runs, plus a fix that addresses the cause (short waits under parallel
  load), are taken as enough to close attempt-1 issue 1. An intermittent failure cannot be fully ruled out. If it
  comes back, it is a new bug row.
- **`maxWorkers: '50%'`** is outside the task's planned files. The dispatcher says the FE Lead approved it, and I
  accepted it as in scope for BUG-004.
- **Issue 5 left non-blocking.** The spec names no URL case rule. Whether to log it as a bug is the user's call.
