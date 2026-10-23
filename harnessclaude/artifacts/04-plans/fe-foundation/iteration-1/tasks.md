---
module: fe-foundation
date: 2026-10-22
total_tasks: 5
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
