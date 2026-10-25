---
module: fe-foundation
date: 2026-10-22
---

# Design: fe-foundation

Spec: `artifacts/03-modules/fe-foundation/spec.md` · Module: `artifacts/03-modules/fe-foundation/module.md` ·
Rules: `frontend/AGENTS.md` and `frontend/guidelines/` (they win over anything here that contradicts them).

## Architecture Overview

The module changes only the web app's shared layers; the folder structure and the import rules of guideline 01
stay as they are.

```
GET /api/v1/me ──► shared/api (generated CurrentUser) ──► shared/auth/useSession ──► app/router/guards
                                                              │                      app/layout/menu-config
                                                              ▼                      shared/auth/landing
                                                     roles[] (EMPLOYEE added)
features/*/routes.ts (roles per screen) ──► app/router/feature-routes ──► buildRoutes()
shared/messages/catalog.ts (Report3) ──► msg() / showMessage() ──► every screen
src/mocks/session.ts (fixture users) ◄── shared/auth/dev-user (mock mode) ◄── app/dev/DevUserSwitcher, test/render
```

- **Roles:** `shared/auth/roles.ts` holds the five roles and their labels. The session always contains `EMPLOYEE`
  (added on read if the server leaves it out), so a route that lists `EMPLOYEE` opens for every signed-in user.
- **Access:** `RequireRole` passes when `hasAnyRole(route.roles)`; otherwise the 403 page (MSG08) in place.
- **Landing:** `landingPath(roles)` takes the first match of a fixed order; `RequireWorkspace` (new, inside
  `RequireAuth`) sends an Admin to `/workspace-setup` while `workspaceReady` is false, except on Workspace Setup, My
  Profile and Login.
- **Menus:** `MENUS` keeps one list per role; `menuFor(roles)` concatenates them in the order Admin, Project
  Manager, Knowledge Manager, Ticket Resolver, Employee and drops repeated paths.
- **Catalog:** `catalog.ts` is rewritten from System_Messages; `msg()` fills `{tracker_name}` from
  `shared/config/tracker.ts` (`TRACKER_NAME = 'Jira'`) unless a parameter is passed.
- **Log out:** `useSignOut` → `logout()` (errors caught, not rethrown) → `resetSession(queryClient, { signedOut:
  true })`, which drops every other query and mutation, removes `tsolve.session.*` keys from `sessionStorage` and
  `localStorage`, and sets the session query to `null` locally instead of refetching — so the user is signed out in
  the app even when the logout request failed. `UserMenu` then always navigates to Login. A global 401 calls
  `resetSession` the same way.
- **Mocks and tests:** fixture users live in `src/mocks/session.ts`, keyed by a user id (`'pm'`, `'pm-km'`, …);
  `dev-user.ts` stores the chosen id; `renderApp(path, { role })` maps a role to its single-role fixture user.

### Workspace site, central sign-in and the route tree by host (change request KAN-198, Tasks 7–9)

```
browser at <site name>.tsolve.io.vn ──► /api/v1/** same origin (Host kept by Caddy) ──► platform: host → Workspace
   │ Login "Sign in" ── startSignIn(returnTo) ──► GET /api/v1/auth/sign-in?returnTo=… ── 302 ──► login.tsolve.io.vn
   │                                                                      (state value made by the server, D9)
   ◄── 302 back to /<returnTo> or /login?error=MSGxx[&google_domain=…] ◄── identity callback on login.
browser at admin.tsolve.io.vn ──► same bundle, buildRoutes(host) → platformConsoleRoutes only
```

- **Same origin only:** `http()` calls relative `/api/v1/…` paths; `VITE_API_BASE_URL` and its Docker build arg
  are removed, so no build can send requests to another host (AC-011.1, AC-011.2).
- **Site state:** a module-level `siteState` store in `shared/api` (`'ok' | 'unknown' | 'suspended'`, React reads it
  with `useSyncExternalStore`) is set by `http()` whenever a response carries MSG119 or MSG120, from `/me` or any
  later request. `RootLayout` renders `SiteUnavailablePage` (MSG119 or MSG120, no shell, no Try again) instead of
  the `<Outlet />` while it is not `'ok'`. The global error handlers skip both codes (no toast, no MSG07) and call
  `resetSession(client, { signedOut: true })` once (AC-011.3–AC-011.5). `useSession` does not report those codes
  as an error, so the session error page (AC-001.6) never shows for them.
- **Sign-in:** `startSignIn(returnTo?)` does `window.location.assign('/api/v1/auth/sign-in' + query)`; a
  `returnTo` that is not a same-site path is dropped (it must match `^/(?!/)` and hold no `:` before its first
  `/`). `useSignInMessage()` reads `error` and `google_domain` from the Login URL and returns `{ code, params }`
  only for MSG06, MSG09, MSG10, MSG122, MSG123, MSG141, with MSG122 only when the lower-cased `google_domain`
  matches `^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$`. The Login screen of `identity`
  renders it inline (AC-012.1–AC-012.4).
- **MSG123:** in `global-errors.ts`, a 401 whose code is MSG123 skips the MSG07 dialog, calls
  `resetSession(client, { signedOut: true })` (cache, mutations, `tsolve.session.*`) and navigates to
  `/login?error=MSG123` (AC-012.5). Any other 401 keeps the MSG07 behaviour.
- **Background header:** orval's `override.query.queryOptions` mutator (`shared/api/query-options.ts`) wraps every
  generated `queryFn`. When `context.client.getQueryState(context.queryKey)?.data !== undefined` (a refetch:
  interval, window focus, reconnect, invalidation), it adds `context.signal` to a `WeakSet` in
  `shared/api/background.ts`; `http()` adds `X-TSolve-Background: 1` when `init.signal` is in that set. A first
  load, every mutation, sign-in (a page navigation) and logout never carry it (AC-012.6, AC-012.7). A hand-written
  query calls `markBackground(signal)` the same way.
- **Route tree by host:** `isPlatformConsoleHost(hostname)` is true when the first label is `admin` (lower-cased,
  trailing dot removed). `buildRoutes(host)` then returns `platformConsoleRoutes` (from
  `@/features/platform-console`) plus the 404 page; otherwise the Workspace tree of STORY-002 to STORY-005 and
  STORY-011, which never includes a Console route (AC-013.1–AC-013.4). The Console's own guards and session belong
  to `platform-console`.
- **Local development:** the app is opened at `<site name>.localhost:5173` (browsers resolve `*.localhost` to the
  loopback address); `server.allowedHosts: ['.localhost']`, and the `/api` proxy keeps `changeOrigin: false`, so
  the backend sees `<site name>.localhost` and resolves the Workspace with the development base domain `localhost`
  (`platform`). `admin.localhost:5173` opens the Platform Console tree. Mock mode needs no site name: plain
  `localhost:5173` is a Workspace site for the mocks.
- **Mocks:** the dev user switcher gains "Unknown site" and "Suspended Workspace" (stored like a fixture user id);
  the `/me` mock and a catch-all mock handler then answer MSG119 (404) or MSG120 (403). `renderApp` and
  `renderWithProviders` accept `{ site: 'unknown' | 'suspended' }` and `{ host }` (default `localhost`).

## Data Model

```ts
// shared/auth/roles.ts
export const ROLES = ['EMPLOYEE', 'TICKET_RESOLVER', 'PROJECT_MANAGER', 'KNOWLEDGE_MANAGER', 'ADMIN'] as const;
export type Role = (typeof ROLES)[number];           // must equal the generated Role enum (type test)

// shared/auth/session.ts — the generated CurrentUser, with roles normalized
export type Session = CurrentUser & { roles: readonly Role[] };   // always includes 'EMPLOYEE'

// src/mocks/session.ts
export const MOCK_USERS: Record<MockUserId, CurrentUser>;        // see "Fixture users" below
export const USER_FOR_ROLE: Record<Role, MockUserId>;           // the single-role user of each role

// shared/messages/catalog.ts
type MessageDefinition = { type: 'field' | 'inline' | 'toast' | 'confirm' | 'tooltip'; level?: 'success' | 'error' | 'info'; text: string };
```

**Fixture users** (Vietnamese names, `@company.example` emails; every PM has exactly one Department):

| Id | Roles (plus Employee) | Department · Departments · Projects · Spaces |
|----|-----------------------|----------------------------------------------|
| `employee` | — | none · [] · [] · [] |
| `tr` | Ticket Resolver | none · [IT] · ITSUP (member) · [] |
| `tr-two-departments` | Ticket Resolver | none · [IT, HR] · ITSUP, HRHELP (member) · [] |
| `pm` | Project Manager | IT · [IT] · ITSUP, ITNET (PM) · [] |
| `km` | Knowledge Manager | none · [] · [] · [IT Space] |
| `admin` | Admin | none · [] · [] · [] |
| `pm-km` | Project Manager, Knowledge Manager | HR · [HR] · HRHELP (PM) · [HR Space] |
| `admin-tr` | Admin, Ticket Resolver | none · [IT] · ITSUP (member) · [] |

`workspaceReady` is true for all; tests that need false override `/me` with `server.use(...)`.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| `/me` shape | `roles[]`, `department`, `departments`, `projects`, `managedSpaces`, `workspaceReady` (spec contract) | + `viewableSpaces`; keep the old `role` | Viewable Spaces come from the Wiki API, so the session stays stable; `role` has no reader outside this module (user, 22/10) |
| New paths | Gap report A4 (`/workspace-setup`, `/triage-history`, `/wiki…`, `/chatbot`) | Wiki nested under `/wiki/spaces/:spaceId` | Company-wide documents need no pseudo-Space; short links (user, 22/10) |
| Unmapped message types | Upload area → field; error row and chat answer → inline | A new `upload` type | No new type to keep in sync (user, 22/10) |
| MSG-new-2 key | `MSG_NEW_2` | `MSG119` | Cannot clash with a future number (user, 22/10) |
| Workspace not set up | Only an Admin is redirected; others land as usual | A "not set up yet" page | MSG60 is for the Admin; no new message needed (user, 22/10) |
| `EMPLOYEE` in the session | Added on read when missing | Trust the server | GBR-28 makes it always true; one place to guarantee it |
| Employee-only screens | Route roles `['EMPLOYEE']` | A special `ALL_SIGNED_IN` value | Same meaning, one mechanism; the table in the spec reads the same |
| Session-only data | Keys starting `tsolve.session.` cleared at logout | A registry of sign-out callbacks | Works for any feature without wiring; the Chatbot (KAN-48) stores its history under it |
| Toast level | Decided in planning: from the Report3 context — the action succeeded → `success`; it failed → `error`; otherwise `info` (builder check) | A level per code from the BA | Report3 gives no level; the rule is checkable from the context column |
| `banner` type | Removed from `MessageType` | Keep it | Banners are Jira App only (excluded from the web catalog) |
| Placeholders | `ScreenPlaceholder` page per new screen in its feature folder | One shared placeholder route | Feature owners replace their own page file; matches the existing 18 placeholders |
| Docs check | A Vitest test that reads `frontend/AGENTS.md` and the guidelines: banned words (whole word, case-sensitive), and every MSG code, role value and `paths.` key they name must exist | Manual review only | Keeps STORY-010 traceable and stops the old model coming back |
| PR #26 | Ignored: the plan works against `main`; #26 reworks onto the foundation | Wait for #26; share files with it | User, 22/10: #26 needs rework anyway |
| Task 1 green on its own | Task 1 also renames every role reference mechanically (routes.ts, `MENUS` keys, `ALL_SIGNED_IN_ROLES`, tests, review mocks); behaviour changes stay in Tasks 2–3 | Merge Tasks 1–3 | Small reviewable PRs, each green (builder check) |
| Tracker App codes | Web catalog keeps every code except the tracker-panel types and Tracker comment (MSG62, MSG65–69, MSG71, MSG72 stay, unused by the web app) | Leave out the whole Tracker App section except MSG73, MSG75 | One simple rule; MSG72 may serve the web Evidence upload (user, 22/10) |
| Catalog test source | `frontend/scripts/report3-messages.mjs` builds `frontend/src/shared/messages/report3-messages.json` from `$TSOLVE_DOCS/Report3/System_Messages.md`; the test checks every catalog entry against it | Hard-coded texts; review only | A Report3 change shows up as a failing test with the changed text (user, 22/10) |
| Logout when the request fails | Session set to `null` locally (no refetch); `useSignOut` never throws; `UserMenu` always navigates to Login | Refetch `/me` | A refetch after a failed logout would sign the user back in (builder check) |
| Names | Decided in planning (builder check): `paths` keys `workspaceSetup`, `triageHistory`, `wikiHome`, `wikiDocument`, `wikiUpload`, `spaceSettings`, `chatbot`; menu icons `ReadOutlined` (Wiki), `MessageOutlined` (Knowledge Chatbot), `BranchesOutlined` (Triage History) — the kit v4 icons of UI decision 5; dev storage key `tsolve.dev.user`; `hasRole(role)`, `hasAnyRole(roles)`; `{ role }` default `'PROJECT_MANAGER'`; `{ role }` and `{ user }` are a union type, never both; Status tag colours Simple = green, Complex = orange until the Figma kit says otherwise | — | Calls any builder could make; the FE Lead can change them in review |
| Edge rules | Decided in planning (builder check): while not set up, an unknown path still shows 404 and `/dev/kit` is not redirected; `/workspace-setup` opens normally after setup; every user's menu ends with the Employee items (D4); a global 401 also clears `tsolve.session.*` | — | Follow the spec's wording; no new behaviour |
| Login screen boundary (KAN-198) | fe-foundation gives `startSignIn` and `useSignInMessage`; the 1.1.1 Login page stays with `identity` | fe-foundation builds a minimal Login; all sign-in in `identity` | One owner per screen while KAN-197 writes the identity spec (Hiếu, 25/10, Q1) |
| Sign-in start | Full-page `GET /api/v1/auth/sign-in?returnTo=` on the Workspace site; the server makes the state value and redirects to `login.` | `POST` that returns the `login.` URL | The state value never passes through the web app (D9) (Hiếu, 25/10, Q2) |
| Sign-in errors | Back on `/login?error=MSGxx` (plus `google_domain` for MSG122), allow-listed codes only; MSG141 without a known Workspace site is shown on `login.` by the backend | The server keeps the result for one read; `login.` shows every error | Matches FE-09 and guideline 08 §8; the allow-list and the domain check stop crafted messages (Hiếu, 25/10, Q3) |
| MSG119 / MSG120 | Any `/api/**` response carries the code (before sign-in checks); the web app reads only the code and shows a full page with no shell and no retry | A public `GET /api/v1/site` at load; a static Caddy page for MSG119 | No extra request; works the same mid-session (Hiếu, 25/10, Q4) |
| MSG123 | Treated as signed out: drop the session, go to `/login?error=MSG123`, no MSG07 | A dialog with MSG123's text | Report3 types MSG123 Inline (Hiếu, 25/10, Q5) |
| Catalog exclusions | Also leave out Retired codes and the Platform Console codes MSG125–MSG133, MSG135–MSG140; MSG134 stays (UC-05) | Only add the five codes; also hold the Console codes | The reference file can be refreshed from Report3 as a whole (Hiếu, 25/10, Q6) |
| 9.3 Audit Entry Detail | Removed in this change request (D18): 34 web screens | A separate change request | Small, same re-approval (Hiếu, 25/10, Q7) |
| API base URL | `VITE_API_BASE_URL` removed; the API is always same origin | Dev-only setting; keep it | A set value would send requests to another host and lose the Workspace (Hiếu, 25/10, Q8) |
| Local dev host | `<site name>.localhost:5173`, Host kept by the Vite proxy; backend dev base domain `localhost` | A backend default site for `localhost`; leave it to `platform` | Same host rule in dev and production (Hiếu, 25/10, Q9) |
| Site states in mock mode | "Unknown site" and "Suspended Workspace" in the dev user switcher; `{ site }` in the test helpers | Tests only with `server.use` | The pages can be demoed without a backend (Hiếu, 25/10, Q10) |
| Background header | `X-TSolve-Background: 1` on every refetch of a query that already holds data, marked through orval's `queryOptions` mutator and a `WeakSet` of abort signals read by `http()` | A `meta.background` flag per hook; the header only on `refetchInterval` queries | Covers polling, focus, reconnect and invalidation in one place with no change to feature hooks; `context.client` exists in TanStack Query 5.104 (identity Q6 = B, Hiếu, 25/10) |
| Route tree by host | `buildRoutes(host)`: first label `admin` → `platformConsoleRoutes` only; any other host → the Workspace tree | Two bundles; a path prefix `/console` | One React app (module-map Q4 = A, Hiếu, 25/10); a Workspace path can never open on `admin.` |
| Tasks of KAN-198 | Task 7 `catalog-sync`, Task 8 `site-sign-in`, Task 9 `host-routes`; Jira Subtasks created by Hiếu under KAN-103 / KAN-104 | One task; three split by site state / sign-in / catalog | Small PRs, each green; Task 9 holds the host rule (Hiếu, 25/10, Q11) |
| Ids (KAN-196 Q2) | Every id in the `/me` contract is `{ type: string, format: uuid }`, so the generated types are `string`; the session compares ids as strings; fixture ids are fixed UUID literals in `src/mocks/session.ts` (one constant per Department, Project and Space, reused by every fixture user); Task 10 `uuid-ids` | Keep int64; convert to numbers in the web app | UUID primary keys in every module (the user, 25/10; platform AC-001.8; `backend/AGENTS.md` §2); a UUID never fits a JS number. The review screen's ids (`features/review/**`) stay with `ticket-review` (Phạm Tùng Dương) (SPEC-P1-7, U6) |

## File Structure

```
frontend/
├── openapi/tsolve-api.yaml                 Modify: CurrentUser, Role, SpaceRef (Task 1)
├── src/shared/api/generated/**             Regenerate (npm run generate:api) (Task 1)
├── src/shared/auth/
│   ├── roles.ts, session.ts, useSession.ts Modify: five roles, Session, hasRole/hasAnyRole, resetSession clears tsolve.session.* (Task 1)
│   ├── dev-user.ts (was dev-role.ts)       Modify: stores a fixture user id (Task 1)
│   └── landing.ts                          Modify: landingPath(roles) (Task 2)
├── src/mocks/session.ts                    Modify: fixture users of the table above (Task 1)
├── src/test/render.tsx                     Modify: { role } → single-role user, { user } (Task 1)
├── src/app/dev/DevUserSwitcher.tsx (+ .module.css)  Rename from DevRoleSwitcher: picks a user; RootLayout imports it (Task 1)
├── src/app/layout/UserMenu.tsx             Modify: always navigates to Login after sign-out (Task 1)
├── src/app/providers/global-errors.ts(x)   Modify: dev-user helper; 401 → resetSession signed out (Task 1)
├── src/features/review/**                  Modify: role values and one-Department mock data (Task 1); Report3 codes (Task 4)
├── scripts/report3-messages.mjs            Create: builds src/shared/messages/report3-messages.json from DOCS (Task 4)
├── src/app/router/guards.tsx               Modify: any-role check, RequireWorkspace, GuestOnly/LandingRedirect (Task 2)
├── src/app/layout/menu-config.ts, SideMenu.tsx, AppShell.tsx   Modify: per-role menus, menuFor(roles) (Task 2)
├── src/shared/routing/paths.ts, types.ts   Modify: new paths; ALL_SIGNED_IN_ROLES removed (Task 3)
├── src/features/*/routes.ts                Modify: new roles, renumbered titles, popup comments (Task 3)
├── src/features/{triage,wiki,chatbot}/     Create: routes.ts, index.ts, mocks/handlers.ts, pages/*Page.tsx placeholders (Task 3)
├── src/features/workspace/pages/WorkspaceSetupPage.tsx   Create: placeholder (Task 3)
├── src/app/router/feature-routes.ts, src/mocks/handlers.ts  Modify: register the new features (Task 3)
├── src/shared/config/tracker.ts            Create: TRACKER_NAME (Task 4)
├── src/shared/messages/catalog.ts, msg.ts  Modify: Report3 catalog, {tracker_name} (Task 4)
├── src/shared/ui/StatusTag/status-tags.ts  Modify: SIMPLE, COMPLEX (Task 4)
├── src/test/guidelines.test.ts             Create: docs check (Task 5)
├── AGENTS.md, guidelines/*.md              Modify: gap report E (Task 5)
└── tests next to their subjects: *.test.ts(x), it names start with the AC id
```

`features/workspace/pages/WorkspaceSetupPage.tsx` belongs to `workspace`; the plan PR adds that one file to this
module's `paths` too, so the gate opens it for the placeholder (it has no test, so trace is not affected).

Change request KAN-198 (Tasks 7–9):

```
frontend/
├── src/shared/messages/report3-messages.json, catalog.ts   Modify: 25/10 System_Messages, new exclusions (Task 7)
├── src/features/audit-log/routes.ts, src/shared/routing/paths.ts   Modify: 9.3 removed (Task 7)
├── openapi/tsolve-api.yaml, src/shared/api/generated/**    Modify: GET /api/v1/auth/sign-in (302) (Task 8)
├── orval.config.ts                                         Modify: query.queryOptions mutator (Task 8)
├── src/shared/api/query-options.ts, background.ts          Create: refetch marking, siteState store (Task 8)
├── src/shared/api/http.ts, src/shared/config/env.ts        Modify: same origin only, background header, site state (Task 8)
├── src/shared/auth/sign-in.ts                              Create: startSignIn, useSignInMessage (Task 8)
├── src/app/pages/SiteUnavailablePage.tsx                   Create: MSG119 / MSG120 full page (Task 8)
├── src/app/router/RootLayout.tsx, guards.tsx               Modify: site state before every screen (Task 8)
├── src/app/providers/global-errors.ts                      Modify: MSG119, MSG120, MSG123 (Task 8)
├── src/mocks/session.ts, src/shared/auth/dev-user.ts, src/app/dev/DevUserSwitcher.tsx, src/test/render.tsx
│                                                           Modify: site states; { site }, { host } (Tasks 8, 9)
├── vite.config.ts, .env.example, Dockerfile, Caddyfile, README.md   Modify: *.localhost, no VITE_API_BASE_URL (Task 8)
├── src/app/router/host.ts                                  Create: isPlatformConsoleHost (Task 9)
├── src/app/router/routes.tsx, router.ts, feature-routes.ts Modify: buildRoutes(host) (Task 9)
├── src/features/platform-console/ routes.ts, index.ts, mocks/handlers.ts, pages/PlatformConsolePage.tsx
│                                                           Create: registration + placeholder (Task 9)
└── AGENTS.md, guidelines/01, 02, 06, 08, 11, 12, 13        Modify: 34 screens, sign-in, dev host, host rule (Tasks 7–9)
```

`features/platform-console/**` belongs to `platform-console`; like the Workspace Setup placeholder, this plan adds
`pages/PlatformConsolePage.tsx` to this module's `paths` (the registration files are already covered by
`features/*/routes.ts`, `index.ts` and `mocks/handlers.ts`).

## External Dependencies
- None new. `/me` is served by `be-foundation` (Anh Tran Hai); until then MSW serves the fixture users.

## Edge Cases & Risks

Dimensions analyzed: 1 User types, 3 Timing, 5 State transitions, 6 Environment, 7 Error cascades, 8 Authorization,
9 Data integrity, 10 Integration · Skipped: 2 Input extremes (no user input in this module), 4 Scale (menus and
roles are bounded), 11 Compliance (no personal data stored beyond the session), 12 Business logic (no amounts or
quotas).

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Data integrity | `/me` lists roles without `EMPLOYEE` | High | Session has `EMPLOYEE`; Employee screens open | Task 1 |
| 2 | STORY-001 | Integration | `/me` returns a role the app does not know | High | Left out (AC-001.7); the known roles still work | Task 1 |
| 3 | STORY-001 | Error cascades | `/me` 500 | High | Session error page with Try again; no redirect | Task 1, Task 2 |
| 4 | STORY-002 | Authorization | Ticket Resolver opens `/review-queue` by URL | Critical | 403 page, URL kept; nothing of the screen rendered | Task 2 |
| 5 | STORY-002 | Authorization | Admin + Ticket Resolver opens Search | High | Allowed (any role matches) | Task 2 |
| 6 | STORY-003 | State transitions | Admin with `workspaceReady` false opens `/users` | High | Sent to `/workspace-setup`; My Profile still reachable | Task 2 |
| 7 | STORY-003 | User types | Employee-only user opens `/` | Medium | Lands on Knowledge Chatbot | Task 2 |
| 8 | STORY-003 | User types | PM + KM opens `/` | Medium | Lands on Review Queue (PM before KM) | Task 2 |
| 9 | STORY-004 | Data integrity | PM + KM menu | Medium | PM items, then Wiki, Knowledge Chatbot; no duplicates | Task 2 |
| 10 | STORY-008 | Timing | Logout request fails (network) | High | Cache and `tsolve.session.*` cleared; Login shown | Task 1 |
| 11 | STORY-008 | Authorization | User B signs in after user A logged out on the same browser | Critical | No data of A in cache or session storage | Task 1 |
| 12 | STORY-009 | Environment | Storage blocked (private mode) in mock mode | Low | Default fixture user (PM) | Task 1 |
| 13 | STORY-009 | Environment | Production build | High | No mock code, no switcher in the bundle | Task 1 |
| 14 | STORY-006 | Data integrity | A code's text differs from Report3 by one character | High | Catalog test fails | Task 4 |
| 15 | STORY-006 | Integration | Backend sends a code the catalog does not have | Medium | Generic error text (MSG06), not a crash (existing `isMsgCode` path) | Task 4 |
| 16 | STORY-005 | Authorization | New screen roles copied wrong | High | Route-table test against the spec table fails | Task 3 |
| 17 | STORY-001 | Integration | Existing tests call `renderApp(path, { role: 'PROJECT_MANAGER' })` | High | Same meaning: the one-role PM fixture (NFR 4) | Task 1 |
| 18 | STORY-008 | Error cascades | Logout request fails while the server session is still valid | Critical | Signed out in the app (session `null`, no refetch); Login shown | Task 1 |
| 19 | STORY-006 | Integration | Review screens on `main` use SRS v2 codes | High | Task 4 remaps them; review tests pass with Report3 texts | Task 4 |
| 20 | STORY-006 | Data integrity | Report3 retires a code the catalog still holds (MSG61, MSG73) | Medium | Catalog test fails until the code is removed (AC-006.7) | Task 7 |
| 21 | STORY-012 | Authorization | `returnTo=//evil.example` or `https://evil.example` (open redirect) | Critical | `returnTo` left out; the backend checks it again | Task 8 |
| 22 | STORY-012 | Data integrity | Crafted link `/login?error=MSG122&google_domain=<script>` or an unknown code | High | No message (AC-012.4); text only through `msg()`, never HTML | Task 8 |
| 23 | STORY-011 | State transitions | Workspace suspended while a user works (a mutation answers MSG120) | High | Suspended page, cache dropped, no toast (AC-011.5) | Task 8 |
| 24 | STORY-011 | Error cascades | Several requests answer MSG119 / MSG120 at once | Medium | One page, `resetSession` once | Task 8 |
| 25 | STORY-012 | Error cascades | A 401 with MSG123 and other 401s at once | High | The MSG123 path wins; no MSG07 dialog | Task 8 |
| 26 | STORY-001 | Error cascades | `/me` answers MSG119 or MSG120 on the first load | High | Site page, not the session error page (AC-001.6) | Task 8 |
| 27 | STORY-012 | Timing | The Review Queue polls every 5 s for an hour with no user action | High | Every poll carries `X-TSolve-Background: 1`; the session ends after 60 min idle (MSG07) | Task 8 |
| 28 | STORY-012 | Timing | A user only browses cached pages (refetches with data) | Low | Those refetches carry the header; accepted: the first load of a new page and every mutation extend the session | Task 8 |
| 29 | STORY-013 | Authorization | `admin.tsolve.io.vn/users` (a Workspace path on the Console host) | High | 404 page; no Workspace route is registered there | Task 9 |
| 30 | STORY-013 | Environment | `ADMIN.localhost` or a trailing dot in the host | Low | Host compared lower-cased, trailing dot removed | Task 9 |

Open questions for the spec owner: none.

## Risk Review — 2026-10-22 — Verdict: CAUTION

Agreements:
- The backend must serve the same `/me` shape; the risk is contract drift, not the design.
- The UI only hides; the backend re-checks every permission (architecture: deny by default).
- Keeping the meaning of `{ role }` in the test helpers keeps existing tests and open work readable and green.

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| `/me` change | Fits the contract-first rule | Roles in the client are hints only | One request, a little larger | — | Why not wait for the backend? | Contract agreed in this plan PR; MSW mocks typed by the generated schema |
| Any-role guard | Simple, central | A wrong route table opens a screen in the UI | — | Users with two jobs see both | Could list every role per route instead | Route-table test against the spec (row 16) |
| Session-only keys | No feature coupling | Logout must clear them | Negligible | — | A feature may forget the prefix | Prefix documented in guideline 06 §7 (Task 5) and in KAN-48 |
| Big catalog rewrite | One file | — | — | Wrong text is the main user risk | Rewrite only used codes? | Full rewrite plus a test of every entry against Report3 |

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Backend serves another `/me` shape | High | Spec contract reviewed by Anh Tran Hai in KAN-58; KAN-59 merges only after agreement | Task 1 |
| A route opens for the wrong role | High | Test of every route's roles against the spec table | Task 3 |
| Catalog text drifts from Report3 | High | Test of every entry; grep for SRS v2 codes | Task 4 |
| A task leaves `main` red | High | Task 1 renames every role reference; Task 4 remaps review's codes; full `verify` on each PR | Task 1, Task 4 |
| Logout leaves data of the previous user | Critical | Clear cache, mutations and `tsolve.session.*`; test with two users | Task 1 |
