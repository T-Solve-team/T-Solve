---
module: fe-foundation
date: 2026-10-22
owner: BA
---

# Spec: fe-foundation

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-22 | STORY-001..STORY-010 | Initial spec from Report3 and decisions D1–D6 | Bring the web app base in line with Report3 (KAN-57, KAN-58) |
| 2026-10-23 | STORY-005 | AC-005.3: a placeholder only while its module has not built the screen | 12.1 is built by `chatbot` (KAN-71); later modules remove their test row with a quick change (FE Lead, 23/10) |

## Requirements

### Functional
1. The web app knows all roles of the signed-in user (Employee, Ticket Resolver, Project Manager, Knowledge
   Manager, Admin) and their Department, Departments, Projects and managed Spaces (GBR-02, GBR-16, GBR-28).
2. Screens open by role as the Permission Matrix (UC list 4.4) says; landing page, Workspace Setup redirect and
   menus follow decision D4.
3. All 35 web screens have a route or a popup slot; the new ones start as placeholders.
4. Every web system message comes from one catalog with Report3's exact code, type and text.
5. Mock mode and the test helpers offer users with one role and with several roles.
6. The frontend guidelines describe this model.

### Non-Functional
1. Every acceptance criterion has a test whose `it` name starts with its id; `sdd.ts trace fe-foundation` passes.
2. Each PR passes the frontend `verify`: `format:check`, `lint`, `typecheck`, `test`, `build`, `generate:api` with
   no diff.
3. No new npm dependency.
4. `renderApp(path, { role })` and `renderWithProviders(ui, { role })` keep their meaning: `{ role }` signs in the
   fixture user with that one role, so tests written that way need no change of call.
5. Each PR leaves `main` green on its own, including the review feature that is already on `main`.

## User Stories

### STORY-001: One session with all of a user's roles
- As a user with several jobs, I want one session that has all my roles so that I can use everything my roles allow
  without signing in again.
- Acceptance:
  - AC-001.1: THE web app SHALL know exactly five roles — `EMPLOYEE`, `TICKET_RESOLVER`, `PROJECT_MANAGER`,
    `KNOWLEDGE_MANAGER`, `ADMIN` — shown as "Employee", "Ticket Resolver", "Project Manager", "Knowledge Manager",
    "Admin".
  - AC-001.2: WHEN `GET /api/v1/me` answers 200, THE session SHALL expose the user, all their roles, their
    Department (or none), their Departments, their Projects with the role in each, their managed Spaces and
    whether the Workspace is set up, exactly as the response gives them.
  - AC-001.3: WHEN the session is read, THE session SHALL report the role `EMPLOYEE` for every signed-in user, even
    if the response lists only other roles (GBR-28).
  - AC-001.4: WHEN a feature asks whether the user has a role, or any of several roles, THE session SHALL answer from
    all of the user's roles.
  - AC-001.5: IF `GET /api/v1/me` answers 401, THEN THE session SHALL be "signed out" (no user, no roles), not an error.
  - AC-001.6: IF `GET /api/v1/me` fails for another reason, THEN THE web app SHALL show the session error page with
    Try again instead of any screen.
  - AC-001.7: IF `GET /api/v1/me` lists a role the web app does not know, THEN THE session SHALL leave it out and keep
    the known roles.

### STORY-002: Screens open by role
- As the team, we want each screen to open only for the roles the Permission Matrix allows so that users see only
  what they may use, while the backend still re-checks every request.
- Acceptance:
  - AC-002.1: WHEN a signed-in user opens a screen, THE web app SHALL show it if the user has at least one of the
    screen's roles.
  - AC-002.2: IF the user has none of the screen's roles, THEN THE web app SHALL show the 403 page with MSG08 in place
    and keep the URL.
  - AC-002.3: IF nobody is signed in, THEN THE web app SHALL send the user to Login with a `returnTo` link to the page
    they asked for.
  - AC-002.4: WHEN a signed-in user opens Login, THE web app SHALL send them to their landing page (STORY-003).
  - AC-002.5: THE route of each screen SHALL list exactly the roles of the screen table under STORY-005.

### STORY-003: Landing page and Workspace Setup
- As a user, I want to land on the screen of my main job so that I start where my work is; as an Admin of a new
  Workspace, I want to set it up first.
- Acceptance:
  - AC-003.1: WHEN a signed-in user opens `/`, THE web app SHALL send them to the first match of: Admin → User List ·
    Project Manager → Review Queue · Knowledge Manager → Wiki Home · Ticket Resolver → Search · otherwise →
    Knowledge Chatbot.
  - AC-003.2: WHILE the Workspace is not set up (`workspaceReady` is false), THE web app SHALL send an Admin who opens
    any screen other than 2.1 Workspace Setup, My Profile or Login to 2.1 Workspace Setup.
  - AC-003.3: WHILE the Workspace is not set up, THE web app SHALL not redirect a user without the Admin role (they
    open screens and land on `/` exactly as when it is set up).
  - AC-003.4: IF the session cannot be loaded, THEN THE web app SHALL not redirect and SHALL show the session error
    page (AC-001.6).

### STORY-004: Menus for users with several roles
- As a user with several roles, I want one menu with every screen my roles give me so that I can reach all of them.
- Acceptance:
  - AC-004.1: THE menu of one role SHALL be, in this order —
    Employee: Knowledge Chatbot, Wiki ·
    Ticket Resolver: Search, Ticket List ·
    Project Manager: Review Queue, Expired Tickets, Review History, Search, Ticket List, Import Tickets, Import
    History, Triage History, Knowledge Dashboard, Jira Integration ·
    Knowledge Manager: Wiki, Knowledge Chatbot ·
    Admin: User List, Workspace Settings, Jira Integration, Knowledge Dashboard, Audit Log.
  - AC-004.2: WHEN a user has several roles, THE menu SHALL list the menu of each role in the order Admin, Project
    Manager, Knowledge Manager, Ticket Resolver, Employee, keeping only the first entry of an item that appears more
    than once (D4).
  - AC-004.3: WHEN the current path is a detail screen, THE menu SHALL highlight the item whose path is the longest
    prefix of the current path.
  - AC-004.4: IF no menu item matches the current path, THEN THE menu SHALL highlight no item.

### STORY-005: Route map with placeholders
- As a feature owner, I want my screen's route, path, title, roles and menu entry to exist already so that I only
  replace a placeholder.
- Acceptance:
  - AC-005.1: THE web app SHALL have the routed screens of this table, with these paths, titles and roles:

    | # | Title | Path | Roles |
    |---|-------|------|-------|
    | 1.1.1 | Login | `/login` | signed out only |
    | 1.2.1 | My Profile | `/profile` | Employee |
    | 1.3.1 | User List | `/users` | Admin |
    | 1.3.2 | User Detail | `/users/new`, `/users/:userId` | Admin |
    | 2.1 | Workspace Setup | `/workspace-setup` | Admin |
    | 2.2 | Workspace Settings | `/workspace-settings` | Admin |
    | 2.3 | Department Detail | `/workspace-settings/departments/:departmentId` | Admin |
    | 3.1 | Jira Integration | `/jira-integration` | Admin, Project Manager |
    | 3.2 | Project Detail | `/jira-integration/projects/:projectId` | Project Manager |
    | 5.1 | Import Tickets | `/import` | Project Manager |
    | 5.2 | Import Preview | `/import/preview` | Project Manager |
    | 5.4 | Import History | `/import/history` | Project Manager |
    | 5.5 | Ticket Detail | `/tickets/:ticketId` | Ticket Resolver, Project Manager |
    | 6.1 | Search | `/search` | Ticket Resolver, Project Manager |
    | 6.2 | Ticket List | `/tickets` | Ticket Resolver, Project Manager |
    | 7.1 | Review Queue | `/review-queue` | Project Manager |
    | 7.4 | Review History | `/review-history` | Project Manager |
    | 7.5 | Expired Tickets | `/expired-tickets` | Project Manager |
    | 9.1 | Knowledge Dashboard | `/dashboard` | Project Manager, Admin |
    | 9.2 | Audit Log | `/audit-log` | Admin |
    | 10.1 | Triage History | `/triage-history` | Project Manager |
    | 11.1 | Wiki Home | `/wiki` | Employee |
    | 11.2 | Wiki Document | `/wiki/documents/:documentId` | Employee |
    | 11.4 | Upload Documents | `/wiki/upload` | Knowledge Manager |
    | 11.7 | Space Settings | `/wiki/spaces/:spaceId/settings` | Knowledge Manager |
    | 12.1 | Knowledge Chatbot | `/chatbot` | Employee |

  - AC-005.2: THE web app SHALL give these popups no path and SHALL name each in the `routes.ts` of its feature:
    1.3.3 Hand Over Responsibilities, 2.4 Delete Department, 5.3 Import Ticket Preview, 7.2 Request Changes, 7.3
    Reject Ticket, 8.1 Give Feedback, 9.3 Audit Entry Detail, 10.2 Triage Result Detail, 11.5 Folder.
  - AC-005.3: WHILE a placeholder screen is not built by its module, THE web app SHALL show a placeholder with the
    screen's number and title to a user who may open it. The placeholder screens are 2.1, 10.1, 11.1, 11.2, 11.4, 11.7
    and 12.1.
  - AC-005.4: THE features `triage`, `wiki` and `chatbot` SHALL be registered like every other feature (routes,
    public `index.ts`, mock handlers in `src/mocks/handlers.ts`).
  - AC-005.5: IF a user opens a path that no screen has, THEN THE web app SHALL show the 404 page.

### STORY-006: Report3 message catalog
- As every screen owner, I want each MSG code to show Report3's exact text so that no screen shows the wrong message.
- Acceptance:
  - AC-006.1: THE catalog SHALL hold every code of Report3 System_Messages with its exact text, except the codes
    whose type is "Inline (tracker panel)", "Toast (tracker panel)", "Banner (tracker panel)" or "Tracker comment"
    (Jira App only), MSG20 and MSG70 (decision D2), and MSG58 and MSG79 (replaced by the Hand Over popup, D3).
  - AC-006.2: THE catalog SHALL give each code the type of its Report3 type: "Red text below the field" and "Red text
    below the upload area" → field · "Inline", "Inline (row)", "Inline (error row in the preview)", "Inline (chat
    answer)" → inline · "Toast" → toast · "Confirmation dialog" → confirm · "Tooltip" → tooltip.
  - AC-006.3: THE catalog SHALL hold `MSG_NEW_2` (type inline) until System_Messages numbers it (D3), with the text
    `{user_name} is responsible for the {responsibilities} below. Choose who takes over each one before {action}.`
  - AC-006.4: WHEN a message has the placeholder `{tracker_name}`, THE web app SHALL fill it with the one configured
    tracker name "Jira" unless the caller passes another value (D4).
  - AC-006.5: WHEN a message is shown with parameters, THE web app SHALL replace each `{name}` placeholder with its
    value.
  - AC-006.6: IF a placeholder has no value, THEN THE web app SHALL leave the placeholder text as it is.
  - AC-006.7: THE catalog SHALL hold no code other than the Report3 codes of AC-006.1 and `MSG_NEW_2`, so every code
    the app uses (review included) is a Report3 code.

### STORY-007: Triage status tags
- As the Triage and Dashboard owners, I want ready-made tags for the triage class so that both screens show it the
  same way.
- Acceptance:
  - AC-007.1: THE Status Tag SHALL have the values Simple and Complex for the triage class (GBR-35), next to the six
    ticket statuses (GBR-07).
  - AC-007.2: WHEN the Status Tag gets the value Simple or Complex, THE Status Tag SHALL show the label "Simple" or
    "Complex".
  - AC-007.3: IF the Status Tag gets a value it does not know, THEN THE type checker SHALL reject it.

### STORY-008: Log out
- As a user, I want logging out to forget everything of my session so that the next person at the computer sees
  nothing of mine.
- Acceptance:
  - AC-008.1: WHEN the user logs out, THE web app SHALL call `POST /api/v1/auth/logout`, drop every cached query and
    mutation of the previous user, and remove every browser-storage entry whose key starts with `tsolve.session.`
    (where features keep session-only data, such as the Chatbot history, UC-02).
  - AC-008.2: WHEN logout finishes, THE web app SHALL send the user to Login.
  - AC-008.3: IF `POST /api/v1/auth/logout` fails, THEN THE web app SHALL still drop the cached data and the
    session-only entries and send the user to Login.

### STORY-009: Mock users and test helpers
- As a developer and a test writer, I want ready users with one role or several so that every screen can be tried and
  tested for each combination without a backend.
- Acceptance:
  - AC-009.1: THE mock mode SHALL offer these fixture users: one per role (Employee only, Ticket Resolver, Project
    Manager, Knowledge Manager, Admin), a Project Manager + Knowledge Manager, an Admin + Ticket Resolver, and a
    Ticket Resolver in Projects of two Departments; every Project Manager among them has exactly one Department.
  - AC-009.2: WHEN a fixture user is chosen in the dev user switcher, THE `/me` mock SHALL answer that user, and
    "Signed out" SHALL make it answer 401.
  - AC-009.3: WHEN a test renders the app or a component with `{ role }`, THE helper SHALL sign in the fixture user
    that has exactly that role (plus Employee); `{ role: null }` SHALL sign out.
  - AC-009.4: WHEN a test renders with `{ user }` naming a fixture user, THE helper SHALL sign in that user.
  - AC-009.5: IF the stored dev user is unknown or storage is blocked, THEN THE `/me` mock SHALL answer the default
    fixture user (Project Manager).
  - AC-009.6: WHERE mock mode is off (a production build or `VITE_API_MOCKING=false`), THE web app SHALL not show the
    dev user switcher and SHALL not start the mock server.

### STORY-010: Guidelines match Report3
- As a person or an AI agent reading `frontend/`, I want the guidelines to describe the Report3 app so that I build
  the right thing.
- Acceptance:
  - AC-010.1: THE files `frontend/AGENTS.md` and `frontend/guidelines/*.md` SHALL name 35 web screens and the five
    roles, and SHALL not contain, as whole words with this capitalisation, Staff, Department Manager, Escalation
    handler, Migrate, Sensitive, Route to handler, Correct class, Wiki review, Wiki editor or Transfer owner.
  - AC-010.2: THE glossary (guideline 03) SHALL define Employee, Ticket Resolver, Knowledge Manager, Space, Wiki
    document, Folder, Company-wide, triage class, Knowledge Chatbot and citation, and SHALL list the role values.
  - AC-010.3: THE decision log (guideline 13) SHALL record the role model (several roles per user) and the Report3
    catalog, and SHALL mark FE-34 as kept by D1.
  - AC-010.4: IF a guideline uses a role value, a `paths.` key or a MSG code, THEN THE value SHALL exist in
    `ROLES`, `paths` or the catalog.

## Out of Scope
- The Review Queue's behaviour (gap report B: the Project filter, decisions, popups) and Ticket Detail's content —
  `ticket-review` (KAN-24) and `ticket-repository`. This module changes the review feature on `main` only where its
  own changes require it: renumbered MSG codes, role values, and mock / test data for a Project Manager with one
  Department.
- The content of the new screens (Triage, Wiki, Chatbot, Workspace Setup, Hand Over) — their modules (e.g.
  KAN-48 for the Chatbot).
- Kit components the new screens need (chat thread, citation, folder tree, Wiki upload) — built by the screen that
  needs them.
- Backend code: the backend serves the new `/me` in `be-foundation`.
- The Jira App and its messages.
- The Spaces a user can view (footnote ⁸) — from the Wiki API, not `/me`.

## Interface Contracts

### Exposed by this module
- **`GET /api/v1/me` → `CurrentUser`** (in `frontend/openapi/tsolve-api.yaml`, served by `be-foundation`):

  ```yaml
  CurrentUser:
    required: [user, roles, department, departments, projects, managedSpaces, workspaceReady]
    properties:
      user:           UserSummary
      roles:          Role[]                 # contains EMPLOYEE for every user
      department:     DepartmentRef | null   # anyOf [$ref, {type: 'null'}]; only a Project Manager has one
      departments:    DepartmentRef[]        # PM: [department]; Ticket Resolver: Departments of their Projects; others: []
      projects:       ProjectMembership[]    # unchanged
      managedSpaces:  SpaceRef[]             # Knowledge Manager's Spaces; others: []
      workspaceReady: boolean
  Role:     enum [EMPLOYEE, TICKET_RESOLVER, PROJECT_MANAGER, KNOWLEDGE_MANAGER, ADMIN]
  SpaceRef: { spaceId: int64, name: string }   # both required
  ```
  401 = signed out. `POST /api/v1/auth/logout` → 204 (unchanged).
- **`@/shared/auth`**: `ROLES`, `ROLE_LABELS`, `useSession()` (`session`, `roles`, `hasRole`, `hasAnyRole`,
  `isProjectManagerOf`, `isMemberOf`), `landingPath(roles)`, `useSignOut()`, `resetSession()`.
- **`@/shared/messages`**: the catalog, `msg(code, params)`, `showMessage(code, params)`, `isMsgCode`.
- **`@/shared/routing`**: `paths` for every screen of AC-005.1.
- **`@/test/render`**: `renderApp(path, { role | user })`, `renderWithProviders(ui, { role | user })`.
- **Session-only storage**: browser-storage keys starting with `tsolve.session.` are removed at logout.

### Required from other modules
- `be-foundation`: serves `CurrentUser` as above (agreed with Anh Tran Hai in the KAN-58 review).
- `workspace`: sets `workspaceReady`; `wiki`: lists the Spaces a user can view.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id; `sdd.ts trace fe-foundation` passes
- [ ] The frontend `verify` passes on every PR (KAN-59 to KAN-63)
- [ ] The `/me` contract is agreed with the backend owner before KAN-59 merges
- [ ] Each PR is reviewed by the FE Lead; KAN-59 also by the backend owner
