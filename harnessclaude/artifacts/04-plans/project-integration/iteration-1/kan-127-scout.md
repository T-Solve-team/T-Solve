Scouted by Codex CLI (read-only), verified by Claude

# KAN-127 scout: [UC-16] Add a Project and link it to a Jira project

Module `project-integration`, status `decomposed`, no spec yet. Written 2026-11-12 for the Planner.
Code facts are from the branch `feat/KAN-127-project-integration-add-project` at `4eb50c68`. It matches `feat/KAN-120-workspace-create-department` (pull request #202, not merged) and is 11 commits ahead of `origin/main` and 1 behind. The commit it is behind, `6aec84a7`, changes only `04-plans/identity/iteration-1/tasks.md`. Line numbers are for `4eb50c68`.

Names used below:

- **SRS** = `SEP490_G89-TaiLieu/SEP490_G89/_context/Report/Sua_31-10/T-Solve_Report3_Software_Requirement_Specification_EN_v3460.docx.md`. The dispatcher pointed to `_context/Report/Minh_sua_report123/...v3456.docx.md`, which does not exist. This report uses the copy KAN-120 D6 chose.
- **Messages file** = `.../Sua_31-10/Report_3_BreakDown/System_Messages.docx.md`; **Rules file** = `.../Report_3_BreakDown/Business_Rules.docx.md`. GBR-04, 10, 14, 20 and 21 and MSG44, 47, 48, 49 and 54 read the same as in the SRS. This was checked cell by cell.
- **DDL** = `SEP490_G89-TaiLieu/SEP490_G89/Notes/DDL_01-11/02_tenant.sql` (UUID ids since 04/11). There is no DDL under `t-solve/docs/`.
- **Stories** = `SEP490_G89-TaiLieu/SEP490_G89/Notes/Stories_T-Solve.md` (24/10).
- `B/` = `backend/src/main/java/vn/tsolve/`, `C/` = `backend/src/main/resources/db/changelog/changes/`, `F/` = `frontend/src/`.

## 1. UC-16 as written

**Actor.** Project Manager; secondary actor Jira (SRS line 1030). Permission Matrix: "Project | Add and link to ticket tracker". Admin: "No". Project Manager: "Restricted ²", where ² means "Own Departments only". Every other role: "No" (SRS lines 706, 732).

**Preconditions** (SRS line 1033): "The Project Manager is signed in and their Department is Active (UC-05)." and "The Admin has saved the company Jira site address (UC-10)."

**Normal flow "Add a Project"** (SRS line 1034, verbatim, shortened only at step 13):

1. The Project Manager opens Jira Integration and clicks "Add project".
2. The system shows the Project form; the Project will belong to the Project Manager's Department, shown read-only.
3. The Project Manager enters the Jira project key.
4. The Project Manager clicks "Test connection".
5. The system checks the fields (MSG01) and checks that the Jira project is not linked to any Project (MSG49).
6. The system calls Jira with the Workspace's Jira service account, checks that it can read the Jira project (MSG48) and shows MSG47.
7. The system opens the Project settings section with default values: standard Jira field mapping, Done statuses in the Done status category and a review range of 3 days.
8. If needed, the Project Manager changes the Done statuses and enters the review range.
9. The Project Manager clicks Save.
10. The system checks that the review range is a whole number of 1 or more.
11. The system creates the Project with a system-generated Project ID and makes the Project Manager the Project Manager and a member of the Project.
12. The system records the action in the audit log and shows MSG05.
13. (Consequences: Solution Form UC-19, AI Solution Suggestion UC-36, Ticket Resolvers UC-17, import UC-20.)

**Alternative flows** (SRS line 1035, verbatim):

- Step 6, Connection fails: "The Workspace's Jira service account cannot browse the project, or Jira cannot find it. The system shows MSG48 and does not save the Project."
- Step 5a, Jira project already linked: "The system shows MSG49 and does not save the Project."
- Step 5b, Re-adding the Jira project of a removed Project: "The Jira project key matches a Removed Project **of the same Department**. After a successful connection test, the system reconnects that same Project (keeping its Project ID, tickets, members and Project settings) and sets the Project back to Active. If the previous Project Manager is still Active and still in the Department, only that person can re-add the Project. Otherwise, the Project Manager re-adding it becomes the Project Manager of the Project."

**Exception flows.** None are written. The generic messages apply: MSG06, MSG07 and MSG08. No flow covers a Removed Project of another Department, a refused re-add by someone other than the previous Project Manager, a missing service account, Jira being unavailable, or two Project Managers adding the same key at once.

**Postconditions** (SRS line 1036): "The Project belongs to the Project Manager's Department, is linked to exactly one Jira project, has exactly one Project Manager and its Project settings are valid. Or the Project is Removed and its tickets are kept." and "The action is recorded in the audit log."

**Jira text vs SRS vs Stories.** The Jira AC (updated 01/11) and the SRS agree: no token, the Browse projects check, the Done status, review range ≥ 1 with default 3, one Project per Jira project, and reconnection. The Stories row (line 102, 3 points, Sprint S1) still says "nhập token read-only" ("enter a read-only token"). It is superseded by the SRS changelog of 31/10 (SRS line 209). **Field mapping** is in SRS step 7 but in neither the Jira AC nor any story (Q11).

**Business rules**

| Rule | Text that matters here | Source |
|---|---|---|
| GBR-04 | "The Solution Form only appears when an issue moves to a Done status of the Project (UC-16)" | SRS line 2488 |
| GBR-10 | "The review range of each Project is a whole number of calendar days, 1 or more, set by its Project Manager (UC-16; default 3)". The review due date is fixed when the ticket enters the queue and does not change when the range changes. | SRS line 2494 |
| GBR-14 | "no access token per Project. Each Workspace has exactly one service account ... (email and API token) ... T-Solve uses it to read every linked project (for Jira, Browse projects) ... A ticket tracker project is linked to only one Project." | SRS line 2498 |
| GBR-20 | "Each Project belongs to exactly one Department ... A Project Manager belongs to exactly one Department at a time, adds Projects only to that Department and becomes the Project Manager of every Project they add ... and is also a member of the Project." | SRS line 2504 |
| GBR-21 | "A removed Project becomes Removed and is not deleted ... Re-adding the same ticket tracker project reconnects that same Project ... A Removed Project whose Project Manager has been deactivated has no Project Manager (MSG54) until an Active Project Manager of the Department re-adds it and becomes its Project Manager (UC-16)." | SRS line 2505 |
| GBR-17 | Deleting a Department makes its Projects Removed; after a restore "a Project Manager of the Department reconnects its Projects (UC-16)". | SRS line 2501 |
| Entity Project | Attributes "Project ID, Department, Jira project key, field mapping, done status, review range, status". "a Jira project is linked to only one Project". Lifecycle Active to Removed and back; "never permanently deleted". | SRS lines 415-421 |
| Entity Project Membership | "each Project has at most one membership with the Project Manager role (exactly one while the Project is Active)"; the PM membership is created "when a Project Manager adds the Project, when an Active Project Manager re-adds a Removed Project that has no Project Manager, or when the Admin hands the Project over (UC-08)". | SRS lines 423-428 |
| Glossary | "Done status ... Default: the statuses in the Done status category of Jira." "Review range ... (1 or more) ... Default: 3." | SRS lines 256, 268 |
| Audit trail | "changes to Projects, Project settings and members ... are recorded in the audit log" | SRS line 2466 |

**Messages, exact text** (Messages file; the same in the SRS)

| Code | Type | Text | In `F/shared/messages/catalog.ts`? |
|---|---|---|---|
| MSG01 | Red text below the field | The {field_name} field is required. | yes, line 24 |
| MSG02 | Red text below the field | {field_name} must not exceed {max_length} characters. (key "at most 20 characters", screen 3.1) | yes, line 25 |
| MSG44 | Red text below the field | Review range must be a whole number of days, 1 or more. | yes, line 121 |
| MSG47 | Toast | Connected to {tracker_name} successfully. | yes, line 127 |
| MSG48 | Inline | Could not connect to {tracker_name}. Check the project key and that the service account can browse this project. | **stale**: the catalog says "Check the project and token and try again." (lines 128-131) |
| MSG49 | Red text below the field | This {tracker_name} project already belongs to {department_name}. | yes, line 132 |
| MSG54 | Inline | No Project Manager. Tickets of this Project are not being reviewed. | yes, line 146 |
| MSG156 | Red text below the field | The service account could not connect to {tracker_name} or lacks the required permissions. (the Admin's service-account field, GBR-14) | **no** |
| MSG05, MSG06, MSG08 | | as KAN-121 | yes |

Messages file rows: MSG44 line 58, MSG47 61, MSG48 62, MSG49 63, MSG54 68, MSG156 76.

**Screens**

- **3.1 Jira Integration** (SRS lines 1676-1696). "Shows different content depending on the role", but only Project Manager content is described. Fields: "Department (Project Manager)": "Read-only: the Project Manager's Department". **Projects table**: "Project ID, Jira project key, Project Manager, Members, Connection status, Status (Active / Removed), Last import", with row actions Test connection, Remove (MSG53) and open the Project Detail, and MSG54 for a Removed Project without an Active Project Manager. **Project key \***: "Text, at most 20 characters; ... (MSG49). Entering the key of a Removed Project again reconnects that same Project." **Project Manager**: "Read-only ... (GBR-20)". The screen images are embedded pictures and were not readable.
- **3.2 Project Detail** (SRS lines 1698-1722). Members and Settings tabs. The Settings tab has **Field mapping** (title, description, resolution comments, resolved date, reporter: "The default is the standard Jira fields"), **Done statuses \*** ("Choose one or more statuses of the Jira project (MSG01 when no status is chosen) ... Default: the statuses in the Done status category"), **Review range \*** ("A whole number of calendar days, 1 or more, with no upper limit (MSG44). Default: 3.") and Save (MSG05). When the Project is Removed, the Settings tab is read-only.
- No story owns Remove Project, the Settings tab or field mapping. KAN-128 and KAN-129 own the Members tab (Stories lines 103-104).

## 2. What already exists

**Backend, `B/project/`.** This is the minimal lookup tracker needed (`package-info.java:1`).

| Piece | Where | Note |
|---|---|---|
| `Project` JPA entity: `Long id` (`project_id`), `UUID publicId`, `jiraProjectKey`, `ProjectStatus status`. Javadoc: "the Project owner adds the rest (department, field mapping, review range...)" | `project/entity/Project.java:14-35` | `department_id`, `field_mapping`, `done_status` and `review_range` are not mapped. `ddl-auto: validate` lets them be added |
| `ProjectStatus { ACTIVE, REMOVED }` | `project/entity/ProjectStatus.java:4-7` | No database check constraint on `status` |
| `ProjectRepository extends JpaRepository<Project, Long>`, `findByJiraProjectKeyAndStatus` | `project/repository/ProjectRepository.java:8-10` | |
| `ProjectService.findActivePublicIdByJiraProjectKey` | `project/service/ProjectService.java:9`, impl `service/impl/ProjectServiceImpl.java:21-27` | The only exported operation |
| Test | `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java` | |

**Who reads `project` today** (Java import or SQL):

| Consumer | What it reads | Where |
|---|---|---|
| tracker, issue created | Jira key to the Active Project's `public_id`; unknown keys are ignored | `B/tracker/service/impl/IssueCreatedServiceImpl.java:45-47` (the only importer of `vn.tsolve.project`) |
| review, send back | **`review_range`**, falling back to 3 when it is null or ≤ 0 | `B/ticket/repository/TicketRepository.java:39` |
| review, readable Projects | Active Projects, key, Department, whether the caller manages them | `B/review/repository/ProjectMembershipRepository.java:26-41` |
| identity, scope checks (KAN-118) | memberships and the Project's Department | `B/identity/repository/ScopeAccessRepository.java:18-49` |
| identity, user detail and handover (UC-08) | key, status, PM; handover offers Active managed Projects only | `B/identity/repository/UserDetailRepository.java:28-33,78-82`; PM transfer `UserRoleUpdateRepository.java:103-119` (demote or delete the old PM row, then upsert the new one) |
| search | key shown as the project name, Department | `B/search/scope/entity/SearchTicket.java:20-26`, `search/query/repository/TicketRankingRepository.java`, `search/retrieval/repository/SolutionRetrievalRepository.java` |
| demo seeder (`identity-mock`) | Departments "Customer Support" and "IT Operations"; Projects `SUP` (Active, Customer Support), `OPS` (Active, IT Operations), **`OLD` (Removed, IT Operations)**; `manager@acme.test` (PM, IT Operations) is the PM of all three | `B/identity/mock/MockUserListSeeder.java:38-40,65-97` |

Nothing in production code reads `done_status`, `field_mapping` or `department.default_project_id`.

**Stand-ins that a real Project service would later replace** (not in KAN-127's scope, recorded for the Planner): `StubProjectAccess` for imports, configured by `tsolve.stub.projects` / `jira-keys` (`B/ticket/service/impl/StubProjectAccess.java:15-21`, `application.yml:100-105`), and `NoManagedProjects` for triage history (`B/triage/service/impl/NoManagedProjects.java:8-18`).

**Department (workspace, KAN-120).** There is **no Department JPA entity**. `workspace` uses JDBC (`B/workspace/repository/DepartmentRepository.java:10-39`) and only has create (`service/DepartmentService.java:12`, `POST /api/v1/departments`, Admin). The `department` table (`C/002-tenant-project-suggestion.sql:8-14`), `public_id` (`C/008-search-public-ids.sql:13-15`) and `app_user.department_id` (`C/019-identity-user-role-department.sql:13`) are all on `main`. Assigning the PM role requires a Department (`B/identity/service/impl/UserRoleUpdateServiceImpl.java:94`).

**Scope checks (KAN-118, merged in #191, commit `705ad787`, on this branch).** These are `B/identity/service/ScopeAccessService.java:6-18` and its implementation. Each check calls `tenantScope.currentWorkspaceId()` first. A PM's Departments are `u.department_id` plus the Departments of their counted memberships (`ScopeAccessRepository.java:18-27`). `canAccessProject` requires an Active account, an Active Department and a counted membership, **but not an Active Project** (lines 39-49). A refusal is 403 MSG08 (`identity/exception/ScopeAccessDeniedException.java:8-11`). Nothing outside identity calls it yet. For KAN-127, `requireDepartment` cannot express "this is the caller's *own* Department", because for a PM who is also a member it covers several Departments. The caller's own Department has to be read directly (`app_user.department_id`). No identity service interface returns it today (`UserDetailRepository.account` is identity-internal, line 42).

**Principal and security.** `TSolveUser(UUID id, String email, String fullName, Set<Role> roles)` carries **no Department** (`B/common/security/TSolveUser.java:15`). Method security is on (`common/config/MethodSecurityConfig.java:11`). The PM pattern is `@PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")` (e.g. `review/controller/ReviewReadController.java`; `ticket/controller/ImportController.java:45`).

**Jira client and service account**

| Piece | Where | What it does |
|---|---|---|
| `TicketTrackerClient` interface: `addComment` and `findComment` only | `B/tracker/client/TicketTrackerClient.java:11-21` | Added by KAN-131 (`19e7a30e`); it was not added by KAN-125 |
| `JiraClient implements TicketTrackerClient`: **Basic auth `email:token` against the site URL**; 5 s / 10 s timeouts; 429 and 5xx become `JiraUnavailableException`, other 4xx become `JiraRejectedException`; no Jira body is kept | `B/tracker/client/JiraClient.java:25-112` | Calls `POST /rest/api/3/issue/{key}/comment`, `GET /rest/api/3/myself` and `GET .../comment` |
| `JiraServiceAccount.current()` returns `Optional<JiraAccount>`: "owned by `workspace` ... a configured stand-in answers until `workspace` implements it" | `B/tracker/service/JiraServiceAccount.java:6-15` | A service interface, so other modules may call it |
| `ConfiguredJiraServiceAccount` reads `tsolve.tracker.jira.site-url / service-account-email / service-account-token` (`JIRA_SITE_URL`, `JIRA_SERVICE_ACCOUNT_EMAIL`, `JIRA_SERVICE_ACCOUNT_TOKEN`); any blank value means no account | `B/tracker/service/impl/ConfiguredJiraServiceAccount.java:9-26`; `application.yml:86-91` | |
| `JiraRestIssueSource` (import, KAN-45): a **different** credential. It sends `Bearer` with `JIRA_API_TOKEN` through `https://api.atlassian.com/ex/jira/{JIRA_CLOUD_ID}`, and its key check is `[A-Z][A-Z0-9_]*` | `B/ticket/client/JiraRestIssueSource.java:44,56-80` | `GET /rest/api/3/search/jql`, comments |
| `TrackerProperties`: only `jiraAppId` and `forgeJwksUri` | `B/common/config/TrackerProperties.java:10-12` | Not a credential |
| `ReviewJiraAccess`: a port with **no production implementation**; the controller refuses when it is absent | `B/review/service/ReviewJiraAccess.java:9-22`, `review/controller/JiraReviewFeedbackController.java:25,41` | Not relevant to UC-16 |
| `jiraapp/**`: only the Ask T-Solve questions | `B/jiraapp/**` (8 files) | Not relevant |

No code calls `GET /rest/api/3/project/{key}`, `/mypermissions` or `/project/{key}/statuses`. RESEARCH-003 records two facts. First, real Atlassian service accounts can only create *scoped* tokens, which must be sent as Bearer through the gateway; a scoped token sent to the site URL gets 401 (`01-research/RESEARCH-003-jira-import-auth-and-api.md:48,54`). Second, the connection test is `GET /rest/api/3/project/{key}`, where "404 = project not visible to the credential (missing Browse projects or wrong key), 401 = bad / expired token" (line 70). So `JiraClient`'s Basic auth fails with a real service account. Import has already moved to Bearer; tracker has not.

**Frontend, `F/features/jira-integration/`**

| Piece | Where | Note |
|---|---|---|
| Routes: 3.1 `/jira-integration`, roles `ADMIN`, `PROJECT_MANAGER`; 3.2 `/jira-integration/projects/:projectId`, `PROJECT_MANAGER` | `routes.ts:4-17`, `F/shared/routing/paths.ts:20-21` | Asserted by `F/app/router/route-table.test.tsx:22` |
| Menu "Jira Integration" for PM and Admin | `F/app/layout/menu-config.ts:48-52,89,92` | |
| `JiraIntegrationPage`, `ProjectDetailPage` | `pages/*.tsx` | `ScreenPlaceholder` only |
| MSW handlers | `mocks/handlers.ts:7-12` | empty |
| Session: `GET /api/v1/me` is in the contract, and `CurrentUser.department` is "the Department of a Project Manager (exactly one)" | `frontend/openapi/tsolve-api.yaml:270-283,1875-1879`; used by `F/shared/auth/useSession.ts:3` | **No backend controller serves `/api/v1/me`** (no match in `backend/src/main/java`), so the Department is known only in MSW mock mode |

**Contract.** There is no `/api/v1/projects` path and no Add Project schema. `ProjectRef { projectId, name }` exists (`tsolve-api.yaml:1909-1917`). The audit action enum is at lines 1620-1621 (`TRACKER_CONFIGURATION_CHANGE`, `DEPARTMENT_CHANGE`).

**Audit.** `AuditAction` (`B/common/audit/AuditAction.java:3-15`) has `TRACKER_CONFIGURATION_CHANGE`, which **nothing records yet** (only enum, contract and `F/features/audit-log/hooks/audit-filters.ts:14`). The database check is in `C/027-workspace-audit-department-change.sql:4-8` (KAN-120).

## 3. Dependencies and their state

| Dependency | State | What KAN-127 does with it |
|---|---|---|
| **KAN-125** TicketTrackerClient + JiraClient | **Not started**: no commit, no branch (`git log --all --grep KAN-125` and `git branch -r` both empty). Its Stories text still says "token read-only theo Project" (line 100). `TicketTrackerClient` exists from KAN-131, with comments only. The module map assigns KAN-125 to `project-integration` (`module-map.md:228`), but the `tracker` module's `module.md` owns `TicketTrackerClient` + `JiraClient` under `tracker/**`, a path `project-integration` cannot write. Jira status not checked (no Jira MCP call) | KAN-127 needs two read calls (project, statuses), Q1-Q3 |
| **KAN-118** scope checks | **Merged** (#191), on this branch | Use the PM role plus the caller's own Department; `requireProject` later for 3.2 |
| **KAN-120** (#202, open) | The branch is stacked on it (KAN-127 D2) | **D2's premise does not hold**: there is no Department entity. The `department` table, `app_user.department_id`, scope checks, `AuditService` and the seeder are all on `main`. What KAN-127 shares with KAN-120 is the audit check changeset 027 (if Q9 adds an action), the changelog master file and `tsolve-api.yaml` with the generated client. See Q15 |
| **Service account** (KAN-119, UC-10/11) | **Not built**. No `workspace` table in the changelog. The DDL has `workspace.jira_site_address`, `service_account_email` and `service_account_api_token` (DDL lines 10-14); the catalog has `tenant_workspace.jira_site_url` and `jira_cloud_id` (`01_catalog.sql:13-14`) | The env stand-in `ConfiguredJiraServiceAccount` is what exists (Q2). The precondition "the Admin has saved the Jira site address" cannot be checked yet |
| **KAN-126** Solution Form (#203, open) | Decides when the form opens with `statusCategory.key === 'done'` (`origin/feat/KAN-126-jira-app-solution-form-v2:jira-app/TSolve_JiraApp/src/frontend/background/watchDone.js:11-14,30`) | **The Done statuses KAN-127 stores have no reader yet.** GBR-04 is only met once the Jira App reads them (caveat, section 6) |
| **KAN-248** schema alignment (UUID ids) | Not started (no commit, no branch) | KAN-127 builds on the current BIGINT `project_id` + UUID `public_id` (Q4 note) |

**Open pull requests touching these areas** (`gh pr list`, 12/11): #203 (KAN-126) adds `C/027-jira-app-solution-draft.sql` and changes `TicketRepository.java` and `tsolve-api.yaml`. #178 (KAN-150) adds `C/027-dashboard-period-indexes.sql` and changes `tsolve-api.yaml`. #206 (KAN-121, stacked on #202) changes `tsolve-api.yaml`. None changes `AuditAction` or `project/**`. Three different changesets are numbered `027`. Liquibase ids differ, so they do not collide, but a new KAN-127 changeset should take a number after 028 and be placed last in `db.changelog-master.xml`.

## 4. Schema gaps

Current `project` (`C/002-tenant-project-suggestion.sql:16-26` + `C/008-search-public-ids.sql:9-11`) vs DDL (`02_tenant.sql:52-62`):

| Column | Changelog | DDL | Gap for UC-16 |
|---|---|---|---|
| `project_id` | BIGSERIAL | UUID | KAN-248; the API uses `public_id` |
| `public_id` | UUID, unique | absent | Project ID shown to users |
| `department_id` | BIGINT FK | UUID FK | none |
| `jira_project_key` | VARCHAR(20) NOT NULL, `uq_project_jira_key UNIQUE` (global, every status) | same | Matches GBR-14: one row per Jira project forever, so reconnect is an `UPDATE` of the same row, and a race between two adds gives 23505 |
| `field_mapping` | JSONB, nullable | same | NULL can mean "standard fields" (Q11) |
| `done_status` | **VARCHAR(100), one value** | same | **The SRS says "one or more statuses"** (Q4) |
| `review_range` | INT, nullable, **no default, no check** | same | The default 3 and the rule ≥ 1 are enforced in the app only (Q5) |
| `status` | VARCHAR(20) DEFAULT 'ACTIVE' | same | none |
| Jira project id | absent | absent | A Jira key rename breaks the link (Q13) |
| created / updated time | absent | absent | AC-001.20 no longer requires them (memory 04/11). "Added on" of the PM comes from `project_membership.date_added` |

`project_membership` (`C/017-project-membership.sql:7-15`): PK `(user_id, project_id)`, role `PROJECT_MANAGER` or `MEMBER`, `date_added`. `uq_membership_one_manager` allows at most one PM per Project (`C/023-identity-one-project-manager.sql:4-5`). On reconnect the old PM row must therefore go before the new PM row is inserted, as `UserRoleUpdateRepository.transferProject` does (lines 103-119).

## 5. What KAN-127 must add or change

**Backend, `project/`**: map `departmentId`, `doneStatus` and `reviewRange` (and `fieldMapping` if Q11 needs it) on `Project`. Add a controller (PM only) with the endpoints of Q6, a service with one `@Transactional` save, and **the Jira calls before the transaction** (`backend/AGENTS.md:144`). The Jira calls cannot go through `tracker/client` directly (`backend/AGENTS.md:78-79`: "never its `repository/`, `entity/`, `mapper/` or `client/`"), see Q1.

**Changeset** (number 029 or later, last in the master file): only if Q4(a)/Q5(a) or Q9(b) is chosen.

**Contract first**, then `npm run generate:api`: paths and schemas of Q6, under a new tag `projects`.

**Frontend, `features/jira-integration/`**: on 3.1, the read-only Department, a small Projects table (Q10), an "Add project" form or modal with key, Test connection (MSG47 / MSG48 / MSG49), a settings section with a Done statuses multi-select prefilled with the Done-category statuses and a review range of 3 (MSG01, MSG44), and Save (MSG05). Add MSW handlers with a Removed seed row for the reconnect path.

**Outside the module's `paths`** (`03-modules/project-integration/module.md:7-14`). Each of these needs a quick change, a module spec of its own, or a `paths` change:
- `F/shared/messages/catalog.ts:128-131`: MSG48 text is stale. This is a quick change against the SRS.
- `B/tracker/**`: new client calls (Q1).
- `B/common/audit/AuditAction.java` and `F/features/audit-log/hooks/audit-filters.ts`: only if Q9(b).

**Behaviours the spec needs to cover** (each testable):
- a new key is stored for the caller's Department, with status Active, PM membership for the caller and 201;
- the key is stored as Jira returns it;
- the default settings are the Done-category statuses and 3 days;
- the review range is saved as entered when ≥ 1;
- there is one audit entry;
- empty key gives MSG01, key over 20 characters gives MSG02, no Done status chosen gives MSG01, review range of 0, -1, 2.5 or text gives MSG44;
- a key linked to an Active Project in any Department gives MSG49 with that Department's name;
- a key of a Removed Project of another Department gives MSG49 (Q7);
- a key of a Removed Project of the same Department reconnects it: same Project ID, tickets and members, status Active (Q8);
- the reconnect PM rule of 5b.3 (Q7, Q8);
- Jira 404 gives MSG48 and nothing is saved;
- a bad or missing service account gives MSG48 and nothing is saved (Q14);
- Jira unavailable gives Q14's answer;
- Save re-checks Browse before writing;
- two PMs adding the same key at once give one 201 and one MSG49 (unique index);
- non-PM gets 403 MSG08, anonymous gets 401 MSG07, a PM whose Department is not Active gets 403 MSG08 (Q14), no bound Workspace gives 500 MSG06, all with no write;
- the new Project is found by `ProjectService.findActivePublicIdByJiraProjectKey` (so issue-created events start flowing);
- web app: double-click sends one request, client-side MSG01 / MSG02 / MSG44, and the server's MSG49 / MSG48 are shown where the SRS puts them.

## 6. Caveats for the Planner

- **GBR-04 is not met end to end by KAN-127.** The Jira App opens the form on the Done *category* (KAN-126 `watchDone.js:11-14`). The stored Done statuses need a backend read for the Jira App and a change in KAN-126 or a follow-up. Tell the user. The issue-created flow (UC-36) does work once a Project is Active (`IssueCreatedServiceImpl.java:45`).
- **Two Jira credentials, two configs.** The tracker uses Basic with `JIRA_SERVICE_ACCOUNT_*` on the site URL, and import uses Bearer with `JIRA_API_TOKEN` + `JIRA_CLOUD_ID` through the gateway. A connection test made with one credential does not prove the other can read. Real service accounts work only with the second (RESEARCH-003:48,54). Fixing this belongs to KAN-125 / KAN-119.
- **Seed data breaks GBR-20.** `manager@acme.test` belongs to IT Operations but is the PM of `SUP` (Customer Support) (`MockUserListSeeder.java:40,65,79-90`). It is demo data only, but a reconnect test on seeded `SUP` would be misleading. Use `OLD` (Removed, IT Operations, same PM) for the reconnect demo.
- **`/api/v1/me` has no backend.** The read-only Department on 3.1 must come from the KAN-127 endpoint itself, not the session (Q10).
- **Removed Project rows.** Nothing in the app makes a Project Removed today. There is no Remove story, and KAN-122 (Delete Department) is not built. Reconnect is reachable only through the seeder and test fixtures.
- **Interim limits of KAN-120 still apply**: Workspace binding under `identity-mock` only, no CSRF header in the browser client, one datasource. A browser POST to the real backend gets 403 until the CSRF quick change (KAN-120 D17).
- **Malformed project id in later 3.2 paths.** Map it to 404 MSG04, as KAN-121's caveat describes (`identity/controller/UserDetailController.java:82-93`). This is not needed if KAN-127 has no `{projectId}` path.

## 7. Open questions

Each has a recommendation so the main session can decide and log it. **[build]** marks the questions whose answer changes what gets built.

**Q1 [build]. Where do the Jira calls (read a project, read its statuses) live?** `TicketTrackerClient`/`JiraClient` are in `tracker/client`, owned by the `tracker` module (`03-modules/tracker/module.md`, status `implementing`). `project` may not use another module's `client/` (`backend/AGENTS.md:79`), and `tracker/**` is not in `project-integration`'s `paths`.
Options: (a) add `findProject(account, key)` and `projectStatuses(account, key)` to `TicketTrackerClient`/`JiraClient` through a `tracker` spec change (a KAN-125 slice), exposed to `project` by a `tracker/service` interface; (b) widen `project-integration`'s `paths` to `tracker/client/**` and do it under KAN-127; (c) a separate small Jira client inside `project/client`.
Recommended: (a). It keeps one Jira client with its error mapping and the no-body logging rule, follows the module boundary, and is the start of KAN-125. (c) duplicates `JiraClient`, and (b) bends ownership.

**Q2 [build]. Which credential does the check use?**
Options: (a) `JiraServiceAccount.current()` (tracker's env stand-in, Basic on the site URL), the same as the KAN-131 comment; (b) import's gateway Bearer config (`JIRA_API_TOKEN`, `JIRA_CLOUD_ID`); (c) unify both behind `JiraServiceAccount` with Bearer now.
Recommended: (a). It is the only service-interface credential that exists, and swapping it for the stored account (KAN-119) or Bearer (RESEARCH-003 rec. 1) later changes one class for every caller. Log the known limitation: a real service account's scoped token fails with Basic.

**Q3 [build]. How is "Browse projects" checked?**
Options: (a) `GET /rest/api/3/project/{key}`: 200 means it can browse, 404 means not found or no Browse (MSG48), 401/403 means a bad credential; (b) `GET /rest/api/3/mypermissions?projectKey=&permissions=BROWSE_PROJECTS`; (c) both.
Recommended: (a). One call answers both "Jira cannot find it" and "cannot browse" from the SRS alternative flow, and returns the canonical key (RESEARCH-003:70). The defaults then come from `GET /rest/api/3/project/{key}/statuses`, keeping statuses whose `statusCategory.key` is `done`.

**Q4 [build]. Done statuses: how many, stored how?** The SRS says "one or more"; the column is one `VARCHAR(100)` in both the changelog and the DDL.
Options: (a) a changeset turning `done_status` into a JSONB array of Jira status ids, with the DDL updated and logged next to KAN-248; (b) comma-separated status ids in the existing column (no changeset, about 16 ids max); (c) one status only.
Recommended: (a). Ids survive a status rename, and a structured value is what the Jira App reader will need. (b) is a string to parse forever, and (c) contradicts screen 3.2.

**Q5 [build]. Review range rules in storage.**
Options: (a) validate in the app (whole number ≥ 1, no upper limit; a non-integer or text in the JSON gives MSG44, not MSG06) and always store the value; add `CHECK (review_range >= 1)` in Q4's changeset (existing NULLs pass); (b) app validation only; (c) also `NOT NULL DEFAULT 3` with a backfill of the seeded NULLs.
Recommended: (a). The check is one line in a changeset that Q4 needs anyway. (c) touches rows that `TicketRepository.java:39` already treats as 3.

**Q6 [build]. API shape.**
Options: (a) `POST /api/v1/projects/connection-test {jiraProjectKey}` returns 200 `{jiraProjectKey, statuses:[{id,name,done}], reconnect, current settings when reconnecting}` (the UI shows MSG47); `POST /api/v1/projects {jiraProjectKey, doneStatusIds, reviewRange}` re-runs the MSG49 and Browse checks before the transaction and returns 201 for new or 200 for reconnected; `GET /api/v1/projects` serves the table (Q10). (b) One POST that tests and saves. (c) (a) without the GET.
Recommended: (a). The SRS has two buttons. AC "check ... before saving" means Save must re-check, because access can change between the two clicks. The GET carries the read-only Department, which `/api/v1/me` cannot supply yet.

**Q7 [build]. Refusals the SRS does not name.** These are: (i) the key belongs to a Removed Project of *another* Department; (ii) a same-Department Removed Project whose previous PM is still Active and still in the Department, re-added by someone else.
Options: (a) 409 MSG49 with the owning Department's name for both; (b) MSG49 for (i) and 403 MSG08 for (ii); (c) a new message for (ii) (SRS change).
Recommended: (a) for (i): the row is still linked (one Project per Jira project), and MSG49 names its Department. For (ii) take (a) too and flag the wording to the SRS author, since "already belongs to {your Department}" is true but unhelpful.

**Q8 [build]. What reconnection keeps and replaces.**
Options: (a) keep the Project ID, tickets and Ticket Resolver members. Set status Active. Prefill the form with the stored Done statuses and range, and store what the PM saves. If the caller is the previous PM, nothing changes in membership. Otherwise delete the old PM's membership row and insert the caller as PM. (b) Like (a) but demote the old PM to MEMBER. (c) Keep the stored settings and ignore the form.
Recommended: (a). `uq_membership_one_manager` forces the old row out. A demoted ex-PM would not count as a member anyway: `ScopeAccessRepository.java:14-15` counts MEMBER rows only for Ticket Resolvers. Prefilling honours "keeping its Project settings" while the SRS still lets the PM change them at step 8.

**Q9 [build]. Audit action.**
Options: (a) reuse `TRACKER_CONFIGURATION_CHANGE`, target `PROJECT`, details `{"operation":"CREATE"|"RECONNECT","jiraProjectKey":..,"departmentId":..,"doneStatusIds":[..],"reviewRange":3}`. It has no changeset, and the enum, contract and audit-log label already exist. (b) A new `PROJECT_CHANGE`, which needs a check-constraint changeset after 027, `AuditAction` in `common`, the contract enum and `audit-filters.ts` (three files outside the module's paths). (c) No audit.
Recommended: (a). Linking a Project to the tracker and choosing its Done statuses is tracker configuration, the value is unused today, and it avoids three out-of-module edits. Choose (b) only if the main session wants UC-11 (service account change) to own `TRACKER_CONFIGURATION_CHANGE` alone.

**Q10 [build]. How much of screen 3.1 to build.**
Options: (a) the read-only Department, plus a table of the Department's Projects with Project ID, Jira project key, Project Manager (or MSG54), Members count and Status, plus the Add project form; no Connection status, Last import, Remove or per-row Test; Admin keeps the placeholder. (b) Also Connection status (one Jira call per row) and Last import (ticket's `import_batch`). (c) The add form only, with no table.
Recommended: (a). The table is how a PM sees the result and finds a Removed row to reconnect. The skipped columns need cross-module reads or Jira fan-out with no AC, and Remove has no story. The SRS describes no Admin content for 3.1.

**Q11. Field mapping.**
Options: (a) store NULL, meaning the standard Jira fields, with no UI in KAN-127; (b) store the explicit standard mapping JSON; (c) build the mapping editor.
Recommended: (a). It is not in the Jira AC, no story edits it, and no reader exists. NULL keeps "standard" true without inventing a JSON shape.

**Q12. Editing settings after the Project is added (3.2 Settings tab).** No story owns it, so after KAN-127 a PM cannot change the Done statuses or range.
Options: (a) out of scope; 3.2 stays a placeholder (KAN-128 builds Members); flag the missing story to the user; (b) build the Settings tab with Done statuses and range now; (c) a read-only Settings tab.
Recommended: (a). The AC is met in the add flow, and (b) roughly doubles the story. The gap is real and should go to the user and the backlog.

**Q13. Jira key input and identity.**
Options: (a) strip and upper-case the input, then MSG01 when empty, MSG02 over 20 characters, and MSG48 for anything that is not `[A-Z][A-Z0-9_]*` (as `JiraRestIssueSource.java:44`, so no Jira call is made). Store the key Jira returns. Store no Jira project id, as in the DDL; a key rename in Jira breaks the link, which is logged as a known ceiling. (b) Also add a `jira_project_id` column and match on it. (c) Store the key as typed.
Recommended: (a). It matches the DDL and every reader (`findByJiraProjectKeyAndStatus`, issue-created events carry the key). (b) needs a changeset and every reader changed for a rare event.

**Q14. Failures with no SRS message.**
Options: (a) no service account configured, Jira 401/403, or Jira 404 give MSG48 with nothing saved; Jira unavailable (timeout, 429, 5xx) gives MSG48 as well; a PM whose own Department is missing or not Active gets 403 MSG08. (b) As (a), but Jira unavailable gives 503 MSG06. (c) Use MSG156 for credential problems.
Recommended: (b). MSG48 tells the PM to check the key and Browse permission, which is wrong advice when Jira is down. MSG156 is the Admin's field message on the Workspace form. The precondition "Department is Active" gets the generic no-permission answer.

**Q15. Base branch (revisits KAN-127 D2).** D2 says the Department entity exists only on the KAN-120 branch. In fact there is no entity, and everything KAN-127 needs is on `main` (section 3).
Options: (a) stay stacked on KAN-120 and rebase when #202 merges; (b) rebase onto `main` now, before any code.
Recommended: (b) if Q9(a) is chosen: no shared file remains except `tsolve-api.yaml`, which merges textually, and #202 cannot block KAN-127. Keep (a) if Q9(b) is chosen, because the new check constraint must list `DEPARTMENT_CHANGE` from changeset 027.

## 8. Codex cross-check

Codex CLI ran read-only and ephemeral from the repo root, using the prompt `kan127-scout-prompt.md`. Its output is `kan127-scout-codex.md`, both in the session scratchpad. It finished. Every Codex claim used above was opened and checked. Corrections and drops:

- **PR list**: Codex could not run `gh` (HTTP 401) and reported the PR list as UNSURE. It was re-run here (section 3).
- **Q2 of Codex** ("searchable list of the service account's projects", recommended) contradicts SRS step 3 and the 3.1 field "Project key \*". It was dropped; Q13 covers key entry.
- **Q3 of Codex** ("only statuses in Jira's Done category", recommended) contradicts screen 3.2: "Choose one or more statuses of the Jira project ... Default: the statuses in the Done status category". It was replaced by Q4, where the defaults come from the category but any status can be chosen.
- **Q6 of Codex** (whether the first Project sets the Department's default Project) is not in UC-16. The default Project is UC-13 (KAN-121 Q1). It was dropped.
- **Q8 of Codex** asked for MSG156. MSG156 is the Admin's service-account message; UC-16 uses MSG48, whose SRS text exists and is token-free. Only the catalog copy is stale. This is now section 1 plus a quick change.
- Codex said there is no DDL under `docs/`. That is correct: the DDL is in `SEP490_G89-TaiLieu/.../DDL_01-11/`.
- Codex's claim that `canAccessProject` does not require an Active Project, its schema comparison and its MSG48 catalog finding were all confirmed.
- Added beyond Codex: the tracker module owns `TicketTrackerClient` (Q1); the Basic vs Bearer credential split (Q2); KAN-126 uses the Done category, so stored statuses have no reader; `/api/v1/me` has no backend; the seeder breaks GBR-20; D2's premise; the three `027` changesets; `TRACKER_CONFIGURATION_CHANGE` is unused (Q9).
