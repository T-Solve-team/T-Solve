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
| 2026-10-25 | STORY-011, STORY-012, STORY-013 | New: the Workspace comes from the Workspace site (AC-011.1–AC-011.6), sign-in through the central sign-in, MSG123 and the background-request header (AC-012.1–AC-012.7), the route tree chosen by host (AC-013.1–AC-013.4) | Multi-tenant pool model: GBR-03, GBR-37, ADR-0001, D9, CR-01, CR-02 (KAN-198; Stories KAN-103, KAN-104); identity decision on the idle time (KAN-197, Q6 = B); module-map decision "Platform Console in the same web app" (KAN-195, Q4 = A) (Hiếu, 25/10) |
| 2026-10-25 | STORY-001, STORY-006 | AC-001.6 reworded (MSG119, MSG120, MSG123 are not the session error page); AC-006.1 reworded (also leaves out Retired codes and the Platform Console codes); new AC-006.8 (MSG119, MSG120, MSG122, MSG123, MSG134, MSG141) | Report3 System_Messages of 25/10 (MSG119–MSG141 new; MSG15, MSG17, MSG89 reworded, D35, CR-09; MSG61, MSG73 retired, CR-05, CR-06) (KAN-198, Hiếu, 25/10) |
| 2026-10-25 | STORY-005, STORY-010 | AC-005.2 drops 9.3 Audit Entry Detail; AC-010.1 counts 34 web screens; AC-010.3 also records the Workspace site, the central sign-in and the route tree by host | Screen 9.3 deleted (D18, CR-15) (KAN-198, Hiếu, 25/10) |
| 2026-10-25 | STORY-001, STORY-009 | New AC-001.8 (the session keeps every `/me` id as a UUID string; `isProjectManagerOf` / `isMemberOf` take a UUID string) and AC-009.7 (fixture users carry UUID ids); Interface Contracts: the ids of `UserSummary`, `DepartmentRef`, `ProjectMembership` and `SpaceRef` are `string` with `format: uuid` | Change request KAN-196 Q2 (the user, 25/10): UUID primary keys in every module, so ids are UUID strings in the contract (Phan_cong v6 §1); SPEC-P1-7 |

## Requirements

### Functional
1. The web app knows all roles of the signed-in user (Employee, Ticket Resolver, Project Manager, Knowledge
   Manager, Admin) and their Department, Departments, Projects and managed Spaces (GBR-02, GBR-16, GBR-28).
2. Screens open by role as the Permission Matrix (UC list 4.4) says; landing page, Workspace Setup redirect and
   menus follow decision D4.
3. All 34 web screens of the Workspace app have a route or a popup slot; the new ones start as placeholders.
4. Every web system message comes from one catalog with Report3's exact code, type and text.
5. Mock mode and the test helpers offer users with one role and with several roles.
6. The frontend guidelines describe this model.
7. The web app runs on the Workspace site `<site name>.tsolve.io.vn` and takes nothing Workspace-related from the
   user: the API decides the Workspace from the host name (GBR-37). Signing in goes through the central sign-in
   site `login.tsolve.io.vn` and back (UC-01, GBR-03, D9).
8. One web app serves both kinds of site: the Platform Console host `admin.tsolve.io.vn` gets the Platform Console
   routes, every Workspace site gets the Workspace app.

### Non-Functional
1. Every acceptance criterion has a test whose `it` name starts with its id; `sdd.ts trace fe-foundation` passes.
2. Each PR passes the frontend `verify`: `format:check`, `lint`, `typecheck`, `test`, `build`, `generate:api` with
   no diff.
3. No new npm dependency.
4. `renderApp(path, { role })` and `renderWithProviders(ui, { role })` keep their meaning: `{ role }` signs in the
   fixture user with that one role, so tests written that way need no change of call.
5. Each PR leaves `main` green on its own, including the review feature that is already on `main`.
6. No API request carries a Workspace id, site name or Google domain, and none goes to a host other than the page's
   own origin.

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
    Try again instead of any screen; the codes MSG119, MSG120 and MSG123 are not such a reason (AC-011.3, AC-011.4,
    AC-012.5).
  - AC-001.7: IF `GET /api/v1/me` lists a role the web app does not know, THEN THE session SHALL leave it out and keep
    the known roles.
  - AC-001.8: WHEN `GET /api/v1/me` answers 200, THE session SHALL keep the ids of the user, the Departments, the
    Projects and the Spaces as the UUID strings the response gives, and `isProjectManagerOf` and `isMemberOf` SHALL
    take a Project id as a UUID string and compare it as a string, never as a number.

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
    Reject Ticket, 8.1 Give Feedback, 10.2 Triage Result Detail, 11.5 Folder (9.3 Audit Entry Detail is deleted,
    D18).
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
    (Jira App only), the codes whose Context is "Retired", the Platform Console codes MSG125 to MSG133 and MSG135
    to MSG140 (UC-54 to UC-56, module `platform-console`), MSG20 and MSG70 (decision D2), and MSG58 and MSG79
    (replaced by the Hand Over popup, D3).
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
  - AC-006.8: THE catalog SHALL hold the multi-tenant web codes with their exact Report3 text: MSG119, MSG120,
    MSG122, MSG123 and MSG141 (type inline) and MSG134 (type field, adding a user, UC-05).

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
  - AC-009.7: THE fixture users SHALL carry UUID strings as the ids of the user, the Departments, the Projects and
    the Spaces, each id the same wherever the same record appears.

### STORY-010: Guidelines match Report3
- As a person or an AI agent reading `frontend/`, I want the guidelines to describe the Report3 app so that I build
  the right thing.
- Acceptance:
  - AC-010.1: THE files `frontend/AGENTS.md` and `frontend/guidelines/*.md` SHALL name 34 web screens and the five
    roles, and SHALL not contain, as whole words with this capitalisation, Staff, Department Manager, Escalation
    handler, Migrate, Sensitive, Route to handler, Correct class, Wiki review, Wiki editor or Transfer owner.
  - AC-010.2: THE glossary (guideline 03) SHALL define Employee, Ticket Resolver, Knowledge Manager, Space, Wiki
    document, Folder, Company-wide, triage class, Knowledge Chatbot and citation, and SHALL list the role values.
  - AC-010.3: THE decision log (guideline 13) SHALL record the role model (several roles per user), the Report3
    catalog, the Workspace taken from the Workspace site with the central sign-in (STORY-011, STORY-012) and the
    route tree chosen by host (STORY-013), and SHALL mark FE-34 as kept by D1.
  - AC-010.4: IF a guideline uses a role value, a `paths.` key or a MSG code, THEN THE value SHALL exist in
    `ROLES`, `paths` or the catalog.

### STORY-011: The Workspace comes from the Workspace site
- Jira: KAN-104 (TS-021) · Sources: GBR-37, ADR-0001, CR-01, CR-03, D16
- As a user of one company, I want the web app to work only with the Workspace of the site I opened so that I
  never choose a company and never see another company's data; as the team, we want an unknown or suspended site
  to say so clearly instead of failing.
- Acceptance:
  - AC-011.1: THE web app SHALL send every API request to its own origin as a relative `/api/v1/…` path, so that
    the host name of the Workspace site decides the Workspace (GBR-37).
  - AC-011.2: THE web app SHALL send no Workspace id, site name or Google domain in any API path, query, header or
    body, and SHALL have no setting that sends API requests to another host (`VITE_API_BASE_URL` is removed).
  - AC-011.3: IF any API response has the code MSG119, THEN THE web app SHALL show a full page with MSG119 in
    place of every screen of the Workspace app, Login included, with no menu, no user menu and no Try again.
  - AC-011.4: IF any API response has the code MSG120, THEN THE web app SHALL show a full page with MSG120 in
    place of every screen of the Workspace app in the same way, whether a user is signed in or not.
  - AC-011.5: WHEN MSG119 or MSG120 arrives while a user is signed in, THE web app SHALL drop every cached query
    and mutation and SHALL show no toast or dialog for that error, only the page of AC-011.3 or AC-011.4.
  - AC-011.6: WHERE mock mode is on, THE dev user switcher SHALL offer "Unknown site" and "Suspended Workspace",
    which make every mock API request answer MSG119 or MSG120, and THE test helpers SHALL accept
    `{ site: 'unknown' }` and `{ site: 'suspended' }` for the same answers.

### STORY-012: Sign in through the central sign-in
- Jira: KAN-103 (TS-020), KAN-104 (TS-021) · Sources: UC-01, GBR-03, D9, CR-02; idle time per identity (KAN-197)
- As a user, I want "Sign in" to take me to the company sign-in and back to the page I asked for, and to see why
  a sign-in was refused, so that I know what to do next.
- Acceptance:
  - AC-012.1: WHEN a signed-out user starts sign-in, THE web app SHALL navigate the whole page to
    `GET /api/v1/auth/sign-in?returnTo=<path>` on its own origin, and SHALL never build a `login.` URL or a
    sign-in state value itself (D9).
  - AC-012.2: IF `returnTo` is not a same-site path, THEN THE web app SHALL leave it out of the sign-in request
    (a same-site path starts with exactly one `/` and holds no scheme and no host).
  - AC-012.3: WHEN Login opens with `error` = MSG06, MSG09, MSG10, MSG122, MSG123 or MSG141, THE web app SHALL
    give the Login screen that code and its text to show inline, with `{google_domain}` of MSG122 filled from the
    parameter `google_domain`.
  - AC-012.4: IF `error` holds another value, or MSG122 has no valid `google_domain`, THEN THE web app SHALL give
    the Login screen no message (a valid domain name is one or more dot-separated labels, e.g. `company.com`).
  - AC-012.5: IF any API request answers 401 with the code MSG123, THEN THE web app SHALL drop the session, every
    cached query and mutation and the `tsolve.session.` entries as at logout (AC-008.1), SHALL not show the MSG07
    dialog, and SHALL send the user to Login with `error=MSG123`.
  - AC-012.6: WHEN the web app refetches a query that already holds data, THE request SHALL carry the header
    `X-TSolve-Background: 1`, so that it does not extend the 60-minute idle time (GBR-03); such a refetch is a
    request no user action started: polling (the Review Queue's), window focus, reconnect, invalidation.
  - AC-012.7: THE web app SHALL send the first load of a query, every mutation, sign-in and logout without the
    header `X-TSolve-Background`.

### STORY-013: One web app, the route tree chosen by host
- Jira: KAN-198 · Source: module-map decision "Platform Console in the same React app" (KAN-195, Q4 = A); the
  Platform Console screens are UC-54 to UC-56 of `platform-console` (Đặng Tấn Bình)
- As the owner of the Platform Console, I want the web app to give `admin.tsolve.io.vn` only the Platform Console
  routes so that I build the Console in `features/platform-console` and no Workspace screen ever opens there.
- Acceptance:
  - AC-013.1: WHEN the web app opens on the Platform Console host, THE web app SHALL use only the routes of
    `features/platform-console` and none of the Workspace screens, menus, landing page or site pages of STORY-002
    to STORY-005 and STORY-011; the Platform Console host is the host whose first label is `admin`
    (`admin.tsolve.io.vn`, and `admin.localhost` in development).
  - AC-013.2: WHEN the web app opens on any other host, THE web app SHALL use only the Workspace routes and SHALL
    register no route of `features/platform-console`.
  - AC-013.3: WHILE `platform-console` has built no screen, THE web app SHALL show a placeholder titled
    "Platform Console" at `/` on the Platform Console host.
  - AC-013.4: IF a path on the Platform Console host matches no Platform Console route, THEN THE web app SHALL show
    the 404 page.

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
- The Login screen 1.1.1 itself (button, layout, where the message shows) — `identity` (KAN-197); this module gives
  it `startSignIn` and `useSignInMessage` (STORY-012).
- The pages of the central sign-in site `login.tsolve.io.vn` (MSG141 when the state value names no Workspace
  site is shown there by the backend), the sign-in state value, the `hd` check and the session cookie — `identity`.
- The tenant resolver (host → Workspace, MSG119, MSG120), reserved site names and Caddy host routing —
  `platform` (KAN-196).
- The Platform Console screens, its Platform Operator session and its messages MSG125–MSG133 and MSG135–MSG140 —
  `platform-console` (Đặng Tấn Bình); this module only chooses the route tree by host (STORY-013).
- Jira App messages MSG121 and MSG124 (tracker panel).

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
      projects:       ProjectMembership[]    # fields unchanged; ids are UUID strings
      managedSpaces:  SpaceRef[]             # Knowledge Manager's Spaces; others: []
      workspaceReady: boolean
  Role:     enum [EMPLOYEE, TICKET_RESOLVER, PROJECT_MANAGER, KNOWLEDGE_MANAGER, ADMIN]
  # every id below: { type: string, format: uuid }; all fields required (AC-001.8)
  UserSummary:       { id: uuid, fullName: string, email: string (email) }
  DepartmentRef:     { departmentId: uuid, name: string }
  ProjectMembership: { projectId: uuid, jiraProjectKey: string, departmentId: uuid, departmentName: string,
                       roleInProject: ProjectRole }
  SpaceRef:          { spaceId: uuid, name: string }
  ```
  401 = signed out. `POST /api/v1/auth/logout` → 204 (unchanged).
- **`@/shared/auth`**: `ROLES`, `ROLE_LABELS`, `useSession()` (`session`, `roles`, `hasRole`, `hasAnyRole`,
  `isProjectManagerOf`, `isMemberOf`), `landingPath(roles)`, `useSignOut()`, `resetSession()`.
- **`@/shared/messages`**: the catalog, `msg(code, params)`, `showMessage(code, params)`, `isMsgCode`.
- **`@/shared/routing`**: `paths` for every screen of AC-005.1.
- **`@/test/render`**: `renderApp(path, { role | user })`, `renderWithProviders(ui, { role | user })`.
- **Session-only storage**: browser-storage keys starting with `tsolve.session.` are removed at logout.
- **Sign-in** (`@/shared/auth`): `startSignIn(returnTo?)` (AC-012.1, AC-012.2) and `useSignInMessage()`, which
  returns `{ code, params }` of an allowed `error` on Login, or `null` (AC-012.3, AC-012.4).
- **`GET /api/v1/auth/sign-in?returnTo=<path>`** in `frontend/openapi/tsolve-api.yaml`: `302` to the central
  sign-in site with a server-created state value (D9); `returnTo` optional, a same-site path only.
- **Background header**: a request the web app sends without a user action carries `X-TSolve-Background: 1`
  (AC-012.6); the backend does not count it as activity for the 60-minute idle time.
- **Route tree by host** (`app/router`): host label `admin` → `platformConsoleRoutes` from
  `@/features/platform-console`; any other host → the Workspace routes (STORY-013).

### Required from other modules
- `be-foundation`: serves `CurrentUser` as above (agreed with Anh Tran Hai in the KAN-58 review).
- `workspace`: sets `workspaceReady`; `wiki`: lists the Spaces a user can view.
- `platform` (KAN-196, Trần Trung Hiếu): on a site name that belongs to no Workspace, every `/api/**` request,
  `/me` included and before any sign-in check, answers a ProblemDetail with code MSG119 (status 404); on a
  Suspended Workspace, code MSG120 (status 403). The web app reads only the code. In development the Workspace
  site is `<site name>.localhost:5173`; the Vite proxy keeps the Host header, and the backend's development base
  domain is `localhost`.
- `identity` (KAN-197, Trần Trung Hiếu): serves `GET /api/v1/auth/sign-in`; the callback on `login.` sends the
  user back to `/login?error=<code>` on the Workspace site named by the state value (with `google_domain` for
  MSG122), or to `returnTo` / `/` on success; a missing or invalid state value with no Workspace site is shown on
  `login.` with MSG141; a session of another Workspace site answers 401 with code MSG123; a request with
  `X-TSolve-Background: 1` does not extend the idle time.
- `platform-console` (Đặng Tấn Bình): `features/platform-console` exports `platformConsoleRoutes` (its screens,
  guards and session); it replaces the placeholder of AC-013.3 with a quick change when it builds its first
  screen.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id; `sdd.ts trace fe-foundation` passes
- [ ] The frontend `verify` passes on every PR (KAN-59 to KAN-63)
- [ ] The `/me` contract is agreed with the backend owner before KAN-59 merges
- [ ] Each PR is reviewed by the FE Lead; KAN-59 also by the backend owner
- [ ] The sign-in contract (STORY-012) is agreed with `identity` (KAN-197) and the MSG119 / MSG120 contract
  (STORY-011) with `platform` (KAN-196) before Task 8 merges; the route tree by host with `platform-console`
  (Đặng Tấn Bình) before Task 9 merges
