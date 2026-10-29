---
module: fe-foundation
feature: host-routes
task: 9
verdict: pass
attempt: 1
date: 2026-10-28
reviewer: harnessclaude-reviewer
---

# Review: fe-foundation Task 9: route tree chosen by host (KAN-207, uncommitted work on `feature/KAN-207-fe-foundation-host-routes`)

## Summary
AC-013.1 to AC-013.4, AC-010.3 and design edge cases 29 and 30 are met, each with named tests. The Console host gets
`RootLayout` > `platformConsoleRoutes` + `*` 404 only (no guard, no AppShell); every other host gets the unchanged
Workspace tree, with no Console route. No critical or major finding. Verdict: PASS with nits (4 minor).

## Criteria
| Criterion | Result | Detail |
|---|---|---|
| Functionality | pass | `routes.tsx` `buildRoutes(host, routes)` returns `buildConsoleRoutes()` when `isPlatformConsoleHost(host)` is true, before any guard or AppShell is built; `host.ts` compares lower-cased with one trailing dot removed, so `administrator.localhost`, `myadmin.localhost` and `acme.admin.localhost` are Workspace hosts; the placeholder is an h1 "Platform Console" at the index route; any other Console path hits `*` -> `NotFoundPage` |
| Completeness | pass | Task 9 "Files" all present: `host.ts`, `routes.tsx`, `router.ts`, `feature-routes.ts` (comment only), `features/platform-console/{routes.ts,index.ts,mocks/handlers.ts,pages/PlatformConsolePage.tsx}`, `mocks/handlers.ts` registration, `render.tsx` `host` option, guidelines 01 / 08 / 13, `guidelines.test.ts`. `host.test.ts` is outside tasks.md and is noted in the session log (Q6). No EXTRA behaviour |
| Correctness | pass | Targeted run: 9 files / 180 tests pass. eslint (changed folders), prettier (router, platform-console, test, guidelines, bug-log) and `tsc -b --noEmit` clean. QA tests: host-routes 19 tests, host 17 tests, as listed in `qa-step2.md`; the guidelines test is the single added `it` (+7 lines in the diff); nothing weakened |
| Integration | pass | `router.ts` passes `window.location.hostname` (no port); `renderApp` passes `options.host ?? 'localhost'`; `platformConsoleRoutes` is `RouteObject[]` exported from the feature's `index.ts`; only `app/router/routes.tsx` imports `@/features/platform-console` (plus `src/mocks/handlers.ts` importing its `mocks/handlers`, the same as every feature); `featureRoutes` untouched except a comment |

## Stage 1: Spec compliance
| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | AC-013.1 Console host uses only Console routes, `admin` first label | PASS | `routes.tsx` buildRoutes early return; `host-routes.test.tsx` "gives the Platform Console host no Workspace screen, menu or landing page", 7 Workspace paths -> 404, "404" shown, MSG08 absent; `host.test.ts` AC-013.1 rows |
| 2 | AC-013.2 other host: Workspace routes only, no Console route | PASS | `host-routes.test.tsx` acme.localhost -> Review Queue, no Console heading; no host given -> Login; `featureRoutes` has no Console title or path; `host.test.ts` 9 Workspace-host rows incl. look-alikes |
| 3 | AC-013.3 placeholder "Platform Console" at `/` | PASS | `PlatformConsolePage.tsx`; test asserts h1 and pathname `/` with role null |
| 4 | AC-013.4 unknown Console path -> 404 | PASS | `*` route in `buildConsoleRoutes`; test `/no-such-page` on `admin.localhost`. Not a trivially-passing pin: a Console-only tree has no other 404 source |
| 5 | AC-010.3 decision log records route tree by host | PASS | FE-44 index row (matches `route tree` + `host`) and a full FE-44 entry; guidelines test line 117 |
| 6 | Edge 29 `admin.tsolve.io.vn/users` -> 404, no Workspace route, no 403 | PASS | test for roles null and ADMIN: 404 text, MSG08 absent, "User List" absent, pathname stays `/users` (no redirect to login) |
| 7 | Edge 30 upper-case host, trailing dot | PASS | `host.test.ts` and `host-routes.test.tsx` both cover `ADMIN.localhost`, `Admin.Tsolve.io.vn`, `admin.localhost.`, `ADMIN.localhost.`; `acme.localhost.` stays Workspace |

## Particular checks from the card
- No Workspace route, guard or AppShell on `admin.`: `buildConsoleRoutes` contains none of `RequireWorkspace`, `RequireAuth`, `GuestOnly`, `AppShell`; no Console route on other hosts: the other branch only reads `routes` (default `featureRoutes`). OK.
- `buildRoutes(host = 'localhost', routes)` default: acceptable. The only call without a host is `DevUserSwitcher.test.tsx:33`; production (`router.ts`) and `renderApp` pass a host. See minor 1.
- `features/platform-console/` holds exactly the 4 files, no `// Popups of this feature` line; import rule holds.
- `docs/bug-log.md`: one line changed (BUG-019 "Fixed in" -> `PR #76 (KAN-206)`); `task-8-site-sign-in.md`: one line (PR line). Nothing else.
- Guidelines: 01 (16th folder, `platformConsoleRoutes` `RouteObject[]`), 08 (new section 3a), 13 (FE-44 index row + entry) match the code. 01 and 08 have no test (as the session log says).
- Session log `task-9-host-routes.md` and its registry row exist; `progress.json` / `events.jsonl` change only the `host-routes` status (pending -> in-progress).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-013 STORY-010`: AC-010.1 to AC-010.4 (`guidelines.test.ts`), AC-013.1 and AC-013.2 (`host-routes.test.tsx`, `host.test.ts`), AC-013.3 and AC-013.4 (`host-routes.test.tsx`). Last line: "8/8 acceptance criteria have a test". No MISSING.

## Issues
Critical: none. Major: none.
Minor (do not block):
1. `frontend/src/app/dev/DevUserSwitcher.test.tsx:33` relies on the `host = 'localhost'` default of `buildRoutes`. Fix (optional): pass `buildRoutes('localhost')` there and drop the default, so a forgotten host can never silently pick the Workspace tree. Keeping the default is also fine: no production code relies on it.
2. `frontend/src/app/router/RootLayout.tsx:21-28` (unchanged file, used by the Console tree): the Console host also gets the Workspace "unknown / suspended site" page (`useSiteStatus`) and, in mock mode, the Workspace `DevUserSwitcher`. Not covered by the spec; a Console API answering MSG119 / MSG120 would show a Workspace site page. Fix: leave to `platform-console` (Bình) to decide; mention in the PR.
3. `harnessclaude/artifacts/05-implementation/fe-foundation/task-9-host-routes.md` "Verify" section only points to `prompts/handoffs/KAN-207-task-9.md`, outside the repo. The verify skill asks for the command and result lines in the session log. Fix: copy the 7 result lines (format:check ... trace) into the log.
4. The developer's full suite had 1 failure (`UserDetailPage.test.tsx` AC-005.7, also red on clean `main`); no row in `docs/bug-log.md` ("log every bug"). Not this task's, not reviewed here. Fix: log it (owner `users`, KAN-108) before the PR.

## Assumptions made
- Full suite and build not re-run, as the card says; the developer's evidence (733 tests, 1 pre-existing failure) was not verified by me.
- `platformConsoleRoutes` name and type are a decision (Q2) open for Bình on the PR; reviewed as decided.
- Proposed feature status: `host-routes` evaluating -> `passing`. Not changed by me (the card gives no instruction).

## Test Report
See `task-9-host-routes-test-report.md`.
