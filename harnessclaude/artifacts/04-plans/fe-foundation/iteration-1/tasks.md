---
module: fe-foundation
date: 2026-10-22
total_tasks: 10
owner: Dev
---

# Tasks: fe-foundation — iteration 1

Spec: `artifacts/03-modules/fe-foundation/spec.md` · Design: `artifacts/04-plans/fe-foundation/design.md`

One task = one Jira Subtask = one branch = one PR, merged in this order. Every test's `it` name starts with the AC id
it proves (guideline 10 T6). Each task ends with the frontend `verify` (root `CLAUDE.md`).

## Task 1: Five roles, multi-role session, logout, mock users and test helpers

**Story:** STORY-001, STORY-008, STORY-009
**Feature:** roles-session
**Jira:** KAN-59 · branch `feature/KAN-59-fe-foundation-roles`
**Description:** Change the `/me` contract (spec Interface Contracts) and regenerate the client; five roles and labels;
`Session` with `EMPLOYEE` always present and unknown roles left out; `useSession` with `roles`, `hasRole`,
`hasAnyRole`; logout as the design says (session `null` locally, `useSignOut` never throws, `UserMenu` always goes to
Login, `tsolve.session.*` removed); the fixture users of the design; `dev-user.ts` and `DevUserSwitcher`; `renderApp`
/ `renderWithProviders` with `{ role }` (single-role user) and `{ user }`. So that the PR is green on its own, also
rename every old role value mechanically: `STAFF` → `TICKET_RESOLVER`, `DEPARTMENT_MANAGER` removed, in every
`routes.ts`, the `MENUS` keys, `ALL_SIGNED_IN_ROLES`, the guards (a temporary `roles.includes` check until Task 2) and
the tests; move the review feature's mock data and test to the one-Department PM (IT). No behaviour change for a
single-role user beyond Report3's roles.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/shared/auth/roles.ts`, `session.ts`, `useSession.ts`, `index.ts`
- Create: `frontend/src/shared/auth/dev-user.ts` · Delete: `frontend/src/shared/auth/dev-role.ts`
- Modify: `frontend/src/mocks/session.ts`, `frontend/src/test/render.tsx`
- Create: `frontend/src/app/dev/DevUserSwitcher.tsx`, `DevUserSwitcher.module.css` · Delete: `DevRoleSwitcher.tsx`, `DevRoleSwitcher.module.css`
- Modify: `frontend/src/app/router/RootLayout.tsx`, `frontend/src/app/layout/AppShell.tsx`, `UserMenu.tsx`,
  `frontend/src/app/router/guards.tsx` (compile only), `frontend/src/app/providers/global-errors.ts(x)`
- Modify (rename only): `frontend/src/features/*/routes.ts`, `frontend/src/shared/routing/types.ts`,
  `frontend/src/app/layout/menu-config.ts`, `frontend/src/features/review/mocks/**`
- Test: `frontend/src/shared/auth/useSession.test.tsx`, `frontend/src/shared/auth/sign-out.test.tsx`,
  `frontend/src/mocks/session.test.ts`, `frontend/src/test/render.test.tsx`, `frontend/src/app/dev/DevUserSwitcher.test.tsx`;
  update `frontend/src/app/router/routes.test.tsx`, `menu-config.test.ts`, `app/providers/global-errors.test.tsx`,
  `features/review/pages/ReviewQueuePage.test.tsx`
**Covers:** AC-001.1–AC-001.7, AC-008.1–AC-008.3, AC-009.1–AC-009.6; edge cases 1, 2, 3, 10, 11, 12, 13, 17, 18
**Dependencies:** the `/me` contract agreed with the backend owner
**Estimated size:** medium

---

## Task 2: Guards, landing page, Workspace Setup redirect and menus

**Story:** STORY-002, STORY-003, STORY-004
**Feature:** guards-menus
**Jira:** KAN-60 · branch `feature/KAN-60-fe-foundation-guards`
**Description:** `RequireRole` passes on any of the route's roles; `landingPath(roles)`; `RequireWorkspace` sends an
Admin to `/workspace-setup` while `workspaceReady` is false (except Workspace Setup, My Profile, Login);
`GuestOnly` and `LandingRedirect` use `landingPath`; `MENUS` per role and `menuFor(roles)` in the D4 order;
`SideMenu` takes the roles.
**Files:**
- Modify: `frontend/src/app/router/guards.tsx`, `frontend/src/app/router/routes.tsx`
- Modify: `frontend/src/shared/auth/landing.ts`, `frontend/src/shared/auth/index.ts`
- Modify: `frontend/src/app/layout/menu-config.ts`, `SideMenu.tsx`, `AppShell.tsx`
- Test: `frontend/src/app/router/routes.test.tsx`, `frontend/src/app/router/guards.test.tsx`,
  `frontend/src/app/layout/menu-config.test.ts`, `frontend/src/shared/auth/landing.test.ts`
**Covers:** AC-002.1–AC-002.4, AC-003.1–AC-003.4, AC-004.1–AC-004.4; edge cases 3–9
**Dependencies:** Task 1
**Estimated size:** medium

---

## Task 3: Route map with the new screens as placeholders

**Story:** STORY-005, STORY-002
**Feature:** route-map
**Jira:** KAN-61 · branch `feature/KAN-61-fe-foundation-routes`
**Description:** Every path of AC-005.1 in `paths.ts`; every `routes.ts` with the spec table's roles and titles
(2.x renumbered, old roles removed, `ALL_SIGNED_IN_ROLES` replaced by `['EMPLOYEE']`); popups named in their
feature's `routes.ts`; new features `triage`, `wiki`, `chatbot` (routes, `index.ts`, empty `mocks/handlers.ts`,
placeholder pages) registered in `feature-routes.ts` and `src/mocks/handlers.ts`; the Workspace Setup placeholder;
the new menu entries (Wiki, Knowledge Chatbot, Triage History) in `menu-config.ts`.
**Files:**
- Modify: `frontend/src/shared/routing/paths.ts`, `frontend/src/shared/routing/types.ts`
- Modify: `frontend/src/features/*/routes.ts` (all 12), `frontend/src/app/layout/menu-config.ts`
- Modify: `frontend/src/features/workspace/pages/WorkspaceSettingsPage.tsx`, `DepartmentDetailPage.tsx` (2.2, 2.3)
- Create: `frontend/src/features/triage/**`, `frontend/src/features/wiki/**`, `frontend/src/features/chatbot/**`
- Create: `frontend/src/features/workspace/pages/WorkspaceSetupPage.tsx`
- Modify: `frontend/src/app/router/feature-routes.ts`, `frontend/src/mocks/handlers.ts`
- Test: `frontend/src/app/router/route-table.test.tsx`
**Covers:** AC-002.5, AC-005.1–AC-005.5; edge case 16
**Dependencies:** Task 2
**Estimated size:** medium

---

## Task 4: Report3 message catalog and triage status tags

**Story:** STORY-006, STORY-007
**Feature:** catalog-tags
**Jira:** KAN-62 · branch `feature/KAN-62-fe-foundation-catalog`
**Description:** Rewrite `catalog.ts` from Report3 System_Messages (AC-006.1–AC-006.3, toast level rule of the
design); `TRACKER_NAME` in `shared/config/tracker.ts` and `msg()` fills `{tracker_name}`; remap every code used
in the whole app, the review feature included (its SRS v2 MSG16/18/19/20/21/90 → Report3 MSG13/14/15/16/17/19;
no MSG20, D2); drop the `banner` type; Status tags `SIMPLE` and `COMPLEX`; the script that builds
`report3-messages.json` from the docs folder, and the JSON it produces.
**Files:**
- Create: `frontend/scripts/report3-messages.mjs`, `frontend/src/shared/messages/report3-messages.json`
- Modify: `frontend/src/shared/messages/catalog.ts`, `msg.ts`, `show.ts`
- Create: `frontend/src/shared/config/tracker.ts`
- Modify: every file that uses an SRS v2 code, incl. `features/review/**` and `app/dev/KitPage.tsx` (grep list in the PR)
- Modify: `frontend/src/shared/ui/StatusTag/status-tags.ts`
- Test: `frontend/src/shared/messages/catalog.test.ts`, `frontend/src/shared/messages/msg.test.ts`,
  `frontend/src/shared/ui/StatusTag/StatusTag.test.tsx`; update `features/review/pages/ReviewQueuePage.test.tsx`
**Covers:** AC-006.1–AC-006.7, AC-007.1–AC-007.3; edge cases 14, 15, 19
**Dependencies:** Task 3
**Estimated size:** medium

---

## Task 5: Frontend guidelines for Report3

**Story:** STORY-010
**Feature:** guidelines
**Jira:** KAN-63 · branch `chore/KAN-63-fe-foundation-docs`
**Description:** Update `frontend/AGENTS.md` and guidelines 01, 02, 03, 06–10, 12, 13 as gap report E lists
(35 screens, five roles, several per user, glossary and banned words, MSG codes in examples, the walkthrough's
role and Jira key, the `tsolve.session.` prefix in 06 §7, decision-log entries); add the docs check test.
**Files:**
- Modify: `frontend/AGENTS.md`, `frontend/guidelines/*.md`
- Test: `frontend/src/test/guidelines.test.ts`
**Covers:** AC-010.1–AC-010.4
**Dependencies:** Task 4
**Estimated size:** medium

---

## Task 6: Placeholders only while a screen is not built

**Story:** STORY-005
**Feature:** placeholder-rule
**Jira:** KAN-71 · branch `feature/KAN-71-chatbot-ask-answer` (change request, inside the chatbot PR)
**Description:** AC-005.3 now holds only while a screen is not built by its module. Remove the 12.1 row of the
placeholder test, since `chatbot` builds 12.1 in the same PR. Later modules remove their own row with a quick change.
**Files:**
- Test: `frontend/src/app/router/route-table.test.tsx`
**Covers:** AC-005.3
**Dependencies:** Task 3
**Estimated size:** small

---

## Task 7: Catalog synced to the System_Messages of 25/10; 9.3 Audit Entry Detail removed

**Story:** STORY-006, STORY-005, STORY-010
**Feature:** catalog-sync
**Jira:** new Subtask of KAN-103 (Hiếu creates it; the key goes here and in the branch name) · branch
`feature/KAN-…-fe-foundation-catalog-sync` · change request KAN-198
**Description:** Refresh `report3-messages.json` from the System_Messages of 25/10 (`npm run messages:report3`,
141 codes). The catalog test leaves out the Retired codes (Context "Retired") and the Platform Console codes
MSG125–MSG133 and MSG135–MSG140 next to the existing exclusions (AC-006.1). Add MSG119, MSG120, MSG122, MSG123,
MSG141 (inline) and MSG134 (field) to `catalog.ts` (AC-006.8); take the new texts of MSG15, MSG17 and MSG89;
remove the retired MSG61 and MSG73. The review screens read MSG15 and MSG17 through `msg()`, so their tests follow
without a change of call. D18: drop 9.3 Audit Entry Detail from the popup list (route-table test, the comment in
`features/audit-log/routes.ts`, `shared/routing/paths.ts`) and count 34 web screens in `AGENTS.md`, guidelines 01,
02 and 08 and the docs check.
**Files:**
- Modify: `frontend/src/shared/messages/report3-messages.json` (regenerated), `frontend/src/shared/messages/catalog.ts`
- Modify: `frontend/src/features/audit-log/routes.ts`, `frontend/src/shared/routing/paths.ts`
- Modify: `frontend/AGENTS.md`, `frontend/guidelines/01-project-structure.md`, `02-conflict-avoidance.md`,
  `08-routing-and-access.md`
- Test: `frontend/src/shared/messages/catalog.test.ts`, `frontend/src/app/router/route-table.test.tsx`,
  `frontend/src/test/guidelines.test.ts`
**Covers:** AC-006.1, AC-006.7, AC-006.8, AC-005.2, AC-010.1; design edge case 20
**Dependencies:** Task 6
**Estimated size:** small

---

## Task 8: Workspace from the Workspace site, central sign-in, MSG123 and the background header

**Story:** STORY-011, STORY-012, STORY-001, STORY-010
**Feature:** site-sign-in
**Jira:** new Subtask of KAN-104 (Hiếu creates it) · branch `feature/KAN-…-fe-foundation-site-sign-in` · change
request KAN-198
**Description:** As the design's "Workspace site and central sign-in" section says:
- the API only on the page's own origin: remove `VITE_API_BASE_URL` (env, `.env.example`, Dockerfile build arg,
  README, Caddyfile comment, guidelines 06 and 11); the Vite dev server accepts `<site name>.localhost` and its
  proxy keeps the Host header;
- `GET /api/v1/auth/sign-in` (302) in the OpenAPI file; `startSignIn(returnTo?)` with the same-site check and
  `useSignInMessage()` with the allow-list and the `google_domain` check, in `shared/auth`;
- the site state: MSG119 / MSG120 from `/me` or any later request switch the Workspace app to
  `SiteUnavailablePage` (no shell), drop the cache and show no toast or dialog; AC-001.6 leaves those codes out;
- MSG123: the global 401 handler drops the session as at logout and goes to `/login?error=MSG123` without MSG07;
- the background header: the orval `query.queryOptions` mutator marks a refetch of a query that already holds data,
  and `http()` adds `X-TSolve-Background: 1` for it;
- mocks: "Unknown site" and "Suspended Workspace" in the dev user switcher; `{ site }` in `renderApp` and
  `renderWithProviders`;
- docs: guideline 06 (error table, header), 08 §8 (sign-in), 12 (local dev on `<site name>.localhost:5173`), 13
  (FE-09 updated, new FE entry for the Workspace site and the central sign-in).
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated),
  `frontend/orval.config.ts`
- Create: `frontend/src/shared/api/query-options.ts`, `frontend/src/shared/api/background.ts`
- Modify: `frontend/src/shared/api/http.ts`, `frontend/src/shared/api/index.ts`, `frontend/src/shared/config/env.ts`
- Create: `frontend/src/shared/auth/sign-in.ts` · Modify: `frontend/src/shared/auth/index.ts`, `useSession.ts`
- Create: `frontend/src/app/pages/SiteUnavailablePage.tsx`
- Modify: `frontend/src/app/router/guards.tsx`, `frontend/src/app/router/RootLayout.tsx`,
  `frontend/src/app/providers/global-errors.ts`
- Modify: `frontend/src/mocks/session.ts`, `frontend/src/shared/auth/dev-user.ts`, `frontend/src/app/dev/DevUserSwitcher.tsx`,
  `frontend/src/test/render.tsx`
- Modify: `frontend/vite.config.ts`, `frontend/.env.example`, `frontend/Dockerfile`, `frontend/Caddyfile`,
  `frontend/README.md`
- Modify: `frontend/AGENTS.md`, `frontend/guidelines/06-data-layer.md`, `08-routing-and-access.md`,
  `11-code-quality-and-tooling.md`, `12-workflow.md`, `13-decision-log.md`
- Test: `frontend/src/shared/auth/sign-in.test.ts`, `frontend/src/shared/api/http.test.ts`,
  `frontend/src/shared/api/background.test.tsx`, `frontend/src/app/router/site-state.test.tsx`; update
  `frontend/src/app/providers/global-errors.test.tsx`, `frontend/src/shared/auth/useSession.test.tsx`,
  `frontend/src/app/dev/DevUserSwitcher.test.tsx`, `frontend/src/test/render.test.tsx`,
  `frontend/src/test/guidelines.test.ts`
**Covers:** AC-011.1–AC-011.6, AC-012.1–AC-012.7, AC-001.6, AC-010.3 (Workspace site, central sign-in); design edge
cases 21–28
**Dependencies:** Task 7 (the new codes); the contracts agreed with `identity` (KAN-197) and `platform` (KAN-196)
**Estimated size:** medium

---

## Task 9: Route tree chosen by host (Platform Console host)

**Story:** STORY-013, STORY-010
**Feature:** host-routes
**Jira:** new Subtask of KAN-104 (Hiếu creates it) · branch `feature/KAN-…-fe-foundation-host-routes` · change
request KAN-198
**Description:** `isPlatformConsoleHost(hostname)` (first label `admin`); `buildRoutes()` takes the host and gives
the Platform Console host only `platformConsoleRoutes` (with the 404 page for any other path) and every other host
only the Workspace routes. Register the `platform-console` feature (`routes.ts` with the "Platform Console"
placeholder at `/`, public `index.ts`, empty `mocks/handlers.ts`, registered in `src/mocks/handlers.ts`).
`renderApp(path, { host })` for tests. Docs: guideline 01 (the feature), 08 (the route tree by host), 13 (decision).
`features/platform-console/**` belongs to `platform-console` (Đặng Tấn Bình); this task adds only the registration
files and the placeholder page, which `platform-console` replaces.
**Files:**
- Create: `frontend/src/app/router/host.ts`
- Modify: `frontend/src/app/router/routes.tsx`, `frontend/src/app/router/router.ts`, `frontend/src/app/router/feature-routes.ts`
- Create: `frontend/src/features/platform-console/routes.ts`, `index.ts`, `mocks/handlers.ts`,
  `pages/PlatformConsolePage.tsx` (placeholder)
- Modify: `frontend/src/mocks/handlers.ts`, `frontend/src/test/render.tsx`
- Modify: `frontend/guidelines/01-project-structure.md`, `08-routing-and-access.md`, `13-decision-log.md`
- Test: `frontend/src/app/router/host-routes.test.tsx`; update `frontend/src/test/guidelines.test.ts`
**Covers:** AC-013.1–AC-013.4, AC-010.3 (route tree by host); design edge cases 29–30
**Dependencies:** Task 8 (both change `routes.tsx` and `render.tsx`); the route export agreed with `platform-console`
**Estimated size:** small

## Task 10: Ids are UUID strings in the `/me` contract

**Story:** STORY-001, STORY-009
**Feature:** uuid-ids
**Jira:** new Subtask (Hiếu creates it) · branch `feature/KAN-…-fe-foundation-uuid-ids` · change request KAN-196 Q2
(SPEC-P1-7)
**Description:** In the contract, every id of `UserSummary` (`id`), `DepartmentRef` (`departmentId`),
`ProjectMembership` (`projectId`, `departmentId`) and `SpaceRef` (`spaceId`) becomes `{ type: string, format: uuid }`;
regenerate the client. `isProjectManagerOf` and `isMemberOf` take `projectId: string`. The fixture users use fixed
UUID literals, one constant per Department, Project and Space, reused wherever the record appears. Tests that build
a `CurrentUser` by hand use UUID strings. Counts (`totalElements`, `count`) stay integers. The review screen's ids
(`ProjectRef`, `ReviewQueueItem.ticketId`, `ticketIds`, `departmentId` of the review paths, `features/review/**`)
are not part of this task: they belong to `ticket-review` (Phạm Tùng Dương). The chatbot's ids are `chatbot` Task 5.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/shared/auth/useSession.ts`, `frontend/src/mocks/session.ts`
- Test: `frontend/src/shared/auth/session.test.ts`, `frontend/src/shared/auth/useSession.test.tsx`,
  `frontend/src/mocks/session.test.ts`
**Covers:** AC-001.8, AC-009.7
**Dependencies:** ships in one PR with `chatbot` Task 5: the regenerated client makes `session.user.id` a `string`,
which `features/chatbot` reads, so neither is green alone; before `be-foundation`
Task 3 serves `/me` (its records use `UUID`)
**Estimated size:** small
