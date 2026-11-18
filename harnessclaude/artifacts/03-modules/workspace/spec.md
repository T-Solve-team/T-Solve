---
module: workspace
date: 2026-11-13
owner: BA
---

# Spec: workspace

Covers UC-12 Create Department (Jira KAN-120, STORY-001..STORY-002), UC-13 Update Department (Jira KAN-121: the rename, STORY-003..STORY-004, and the default Project, STORY-012..STORY-013) and UC-10 Workspace Setup and UC-11 Workspace Settings, company information (Jira KAN-119, STORY-005..STORY-011; Jira access as the T-Solve Jira App, KAN-349, STORY-014). Source: SRS v3460, folder `Sua_31-10` (UC-10, UC-11, UC-12, UC-13; screens 2.1, 2.2 and 2.3; GBR-17, GBR-19, GBR-21, GBR-29, GBR-37, GBR-40; MSG01, MSG02, MSG04, MSG05, MSG06, MSG07, MSG08, MSG43, MSG156, MSG157). Decisions: KAN-120 `artifacts/04-plans/workspace/iteration-1/kan-120-decisions.md`, KAN-121 `kan-121-decisions.md` (written `K121-D6` in design.md); KAN-119 the master design's "Decisions made without the user (KAN-119)" (D1–D30) and `iteration-1/kan-119/design.md`.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-11 | STORY-001, STORY-002 | Initial spec: UC-12 Create Department | KAN-120 |
| 2026-11-11 | STORY-001, STORY-002 | Builder check: blank wins over length (AC-001.6, AC-002.7); Cancel is ignored during a save (AC-002.12, new AC-002.14); AC-002.9 names the shared query; whitespace and character defined; interim limits 4 and 5 added | Codex builder check |
| 2026-11-12 | STORY-005..STORY-011 | Initial spec for KAN-119 (UC-10 Workspace Setup, UC-11 Workspace Settings: company information and the Jira service account). Departments (KAN-120..KAN-123) are out of scope | KAN-119; decisions Q1–Q8 of 12/11 (user) and the self-made decisions listed in design.md "Decisions made without the user" (user delegated them, 12/11) |
| 2026-11-12 | STORY-009 | AC-009.1 reworded: "with no menu" removed; hiding the side menu on 2.1 becomes a change request to `fe-foundation` (Required from other modules, Out of Scope) | The menu is rendered by `fe-foundation`'s `AppShell`, whose code is locked (its spec changed since approval); decision D24 (user delegated decisions, 12/11) |
| 2026-11-12 | STORY-007, STORY-008 | AC-008.1 names the binding a worker job uses (`WorkerTenantService`, else `TenantScope`); AC-007.5 also covers a stored token that cannot be decrypted (MSG156) | Evaluation of Tasks 5 and 6: the suggestion job binds only `WorkerTenantService`, so `current()` failed in the job; an undecryptable token answered 500. Decisions D28, D29 (user delegated decisions, 12/11) |
| 2026-11-13 | STORY-003..STORY-009 | KAN-119 stories renumbered from STORY-001..STORY-007 (AC-001.x..AC-007.x become AC-003.x..AC-009.x), wording unchanged | KAN-120 (#202, STORY-001, STORY-002 Departments) reached `main` first; the two specs merge into one |
| 2026-11-13 | all | Merged the KAN-120 spec (main, #202) with the KAN-119 spec: Out of Scope no longer lists UC-10/UC-11 or the Departments KAN-120 built; requirements, contracts and Definition of Done joined | KAN-119 merges main |
| 2026-11-15 | STORY-006 | AC-006.1 and the Interface Contracts name `vn.tsolve.common.session.lookup.WorkspaceStatusLookup#isCompanyInformationEntered()` instead of the orphan `common.lookup.WorkspaceStatusLookup#isReady()` | BUG-206: `/me` (`SessionServiceImpl`) reads the `common.session.lookup` interface, so `workspaceReady` stayed false after Setup |
| 2026-11-15 | STORY-005..STORY-011 | KAN-119 stories renumbered again from STORY-003..STORY-009 (AC-003.x..AC-009.x become AC-005.x..AC-011.x), wording unchanged; the requirements numbered 7..11 become 10..14 | KAN-121 (#206, STORY-003, STORY-004 rename) reached `main` first and took STORY-003 and STORY-004; the specs merge into one |
| 2026-11-16 | STORY-006, STORY-010, STORY-011 | Change request: Workspace Settings shows the stored token masked with its last four characters, never revealable or copyable. `WorkspaceInfo` gains `serviceAccountTokenSet` and `serviceAccountTokenHint` (new AC-006.5, AC-010.7, AC-010.8); AC-006.2, AC-010.2, AC-011.7 and Non-Functional 4 reworded to allow only that hint. No issue date and no expiry date are shown (`token_expiry_date` stays Out of Scope) | User request during the staging test of 16/11 (BinhBinh13): the Admin cannot tell whether a token is stored or which one. Decisions of 16/11: last four characters (not dots only); expiry date and saved date dropped |
| 2026-11-16 | STORY-005..STORY-011 | KAN-344: the service account is an Atlassian service account with a scoped API token, sent as `Authorization: Bearer` to the gateway `https://api.atlassian.com/ex/jira/{cloudId}`. The email field is removed (AC-005.4 removed; AC-005.1, AC-005.3, AC-005.12, AC-006.2, AC-007.1, AC-007.3, AC-007.4, AC-007.7, AC-007.8, AC-009.2, AC-009.4, AC-010.1 reworded). `workspace_info` stores the site's cloud id (new AC-005.14, AC-007.11, AC-011.8); the check uses the gateway (AC-011.1 reworded); `JiraServiceAccount` returns the site, cloud id and token (AC-008.1 reworded, new AC-008.4); requirements 10, 13, 14 and Non-Functional 5 follow | Staging, 16/11: the Jira import answered MSG50 and RESEARCH-003 shows a real service account's scoped token gets 401 on the site URL. User decisions of 16/11: Bearer through the gateway (option B), no email field, cloud id in `workspace_info` (not the platform catalog yet), workspace and tracker in one PR |
| 2026-11-16 | STORY-005, STORY-007, STORY-009, STORY-010, STORY-011 | KAN-344 follow-up: `MSG156` carries a `reason` (`TOKEN_REJECTED`, `MISSING_PERMISSIONS`, `JIRA_UNREACHABLE`) and, for missing permissions, `missing_permissions` (new AC-011.9); the backend logs each failed check with the reason, the Jira status and the missing permission keys (new AC-011.10); Setup and Settings show a second line under the token field (new AC-009.8, AC-010.9). AC-005.7, AC-007.5, AC-007.11 and AC-011.1 reworded to send the reason. The `MSG156` text stays as System_Messages v3460 gives it | Staging, 16/11 (Workspace Bluefin): a valid token lacking only Modify reporter showed `MSG156` and the log had no Jira status, so finding the cause took manual curl calls. User decision of 16/11: screen line plus log, keep the `MSG156` text, Jira key KAN-344 |
| 2026-11-17 | STORY-003, STORY-012, STORY-013 | Add the default Project of UC-13 (steps 2 and 3, screen 2.3 "Default Project", GBR-40, MSG156, MSG157): new STORY-012 (read the Department with its default Project and choosable Projects; set or clear it in the rename PUT with the Jira checks and the audit, AC-012.1 to AC-012.17) and STORY-013 (2.3 field, AC-013.1 to AC-013.10). AC-003.6 and AC-003.14 reworded for the new field; requirement 8 and Non-Functional 2 reworded, requirements 15 to 17 added; the default-Project lines leave Out of Scope, which keeps the UC-58 consumer and the GBR-17/GBR-21 clearing writes; `getDepartment`/`updateDepartment` answer `DepartmentDetail`; MSG157 joins the catalog | KAN-121 (Jira criteria read 17/11); KAN-127 and KAN-119 are on `main`. Decisions D26 to D42 in `kan-121-decisions.md` (user delegated decisions) |
| 2026-11-12 | STORY-003, STORY-004 | Add UC-13 rename: read and rename API with the Space (AC-003.1 to AC-003.22) and screen 2.3 Department Detail with the 2.2 link (AC-004.1 to AC-004.14); UC-13 leaves Out of Scope except the default Project; interim limit 6 added; contract gains `getDepartment` and `updateDepartment` | KAN-121, decisions D4 and D6 to D20 |
| 2026-11-17 | STORY-005..STORY-011, STORY-014 | KAN-349: T-Solve calls Jira as its own Jira App (Forge app system token), not as a customer service account. The service account token, its check and its masked hint are removed (AC-005.7, AC-006.5, AC-007.2, AC-007.4, AC-007.5, AC-007.11, AC-008.3, AC-010.2, AC-010.7, AC-010.8, AC-011.1, AC-011.2, AC-011.5, AC-011.7 removed; the KAN-344 follow-up reason lines AC-009.8, AC-010.9, AC-011.9 and AC-011.10 removed with the check they explain); Setup and Settings keep the company name and the Jira site; new STORY-014 receives the app token (AC-014.1..AC-014.8; numbered after KAN-121's STORY-012 and STORY-013); `WorkspaceInfo` gains the Jira App status (AC-006.2, AC-010.1 reworded, new AC-006.6, AC-010.10; new AC-011.11 clears the stored credentials); KAN-121's default Project check (requirement 16, AC-012.7, AC-012.8) calls Jira with that same Jira access; AC-005.1, AC-005.6, AC-005.8, AC-005.10, AC-005.12, AC-005.14, AC-007.1, AC-007.6..AC-007.8, AC-008.1, AC-008.4, AC-009.2, AC-009.4, AC-010.3, AC-011.8 and requirements 10..14, Non-Functional 4..6 follow | RESEARCH-005 and its probe of 17/11: the Forge app user already holds Browse, Create issues, Modify reporter and Add comments on the default scheme, so no Admin step per project and no customer secret; user decisions of 17/11 (option D, no service account kept, app token encrypted in Redis, status shown on 2.2) |

## Requirements

### Functional
1. An Admin creates a Department by giving its name. The Department starts Active.
2. The Department name is unique in the Workspace, ignoring letter case and including deleted Departments.
3. Creating a Department also creates its Department Space. Both exist or neither does (GBR-29).
4. Each creation is recorded in the audit log.
5. Screen 2.2 Workspace Settings has the "New Department" button, its popup, and a Departments table with the Name column.
6. A new Department is offered when the Admin adds a Project Manager (UC-05).
7. An Admin renames an Active Department from screen 2.3 Department Detail. Its Department Space gets the same name in the same transaction (GBR-29). The new name follows the rules of requirement 2.
8. A save on 2.3 changes only the name and the default Project. The Department's status, Projects and tickets stay as they were (UC-13 step 5).
9. Each rename that changes the name is recorded in the audit log with the name before and after.
10. The tenant database of every Workspace holds exactly one `workspace` row once the Admin has set the Workspace up
   (UC-10): company name, Jira site address and the site's cloud id (`02_tenant.sql`). It holds no Jira credential.
   Before that the Workspace has no row and is "not set up".
11. Workspace Setup (UC-10, screen 2.1) is done once, by an Admin. It checks the fields and the Jira site address,
   reads the site's cloud id, links the Jira site through the catalog port, stores the row and records the action in
   the audit log (GBR-14, GBR-24).
12. Workspace Settings (UC-11, screen 2.2) lets an Admin edit the company name and shows whether the T-Solve Jira App
   is connected. The Jira site address can never be changed after Setup (GBR-24, GBR-37).
13. T-Solve calls Jira as its own Jira App (KAN-349, RESEARCH-005): the app, installed on the Workspace's Jira site,
   sends its app system token to the backend (STORY-014); the backend keeps it encrypted until it expires and uses it
   for every Jira call. The customer creates no service account and gives T-Solve no Jira credential.
14. `workspace` tells `/me` whether the Workspace is set up (`workspaceReady`) and gives other modules the current
   Jira access (`JiraServiceAccount` of `tracker`): site address, cloud id and the app token.
15. An Admin chooses or clears a Department's default Project on 2.3, in the same Save as the name (UC-13 step 2). Only
   an Active Project of that Department can be chosen. A Department with a default Project is one that handles support
   requests: the Knowledge Chatbot creates issues of type Task there; other Departments have none (GBR-40).
16. A newly chosen default Project is checked with Jira through the Workspace's Jira access (the Jira App, AC-008.1) before anything is stored: Create
   Issues and Modify Reporter in that Project (MSG156), and the issue type Task requiring no field other than summary
   and description (MSG157) (UC-13 step 3, GBR-40).
17. A default Project that is not Active counts as none: the Department has no default Project until the Admin
   chooses another one (GBR-40).

### Non-Functional
1. The name rules hold in the database: two requests sent at the same moment cannot create two Departments with one name.
2. The Department, its Space and the audit entry are written in one database transaction, with no outbound call inside it. The same holds for a rename and for a change of the default Project, whose Jira check runs before the transaction opens.
3. A Workspace's Departments live only in that Workspace's database (GBR-37, ADR-0002). The Workspace of a request never comes from request content.
4. The app token is kept only in Redis, encrypted with AES-256-GCM under the key `WORKSPACE_SECRET_KEY` with the
   Workspace id as associated data, and expires with the token; it never appears in a response, a log line, an audit
   entry or an exception message.
5. The backend makes outbound calls only to a Jira site address that passed the site rule of AC-011.3, and to
   `https://api.atlassian.com/ex/jira/{cloudId}` with a cloud id that passed AC-011.8 (no server-side request forgery
   through the address the Admin types or the token request carries).
6. Every Jira call made during Setup has a connect timeout of at most 5 s and a read timeout of at most 10 s.

### Interim limits
These are known limits of the code this story builds on. They are not defects of this story.

1. The endpoint needs a Workspace bound to the request. Until KAN-275 (identity Tasks 26a and 26b) lands, only the `identity-mock` profile binds one, as for the User List. A request with no bound Workspace is answered with MSG06 (AC-001.15).
2. A real Keycloak sign-in cannot reach this screen until KAN-275 lands.
3. The web app does not send the CSRF header yet, so a browser POST to the real backend gets 403. That is a separate quick change to `frontend/src/shared/api/http.ts` for every write (decision D17). This story is verified with MSW and backend tests.
4. In browser mock mode a new Department shows MSG05 but does not appear in the table or the Add User form, because the `users` mock answers `GET /api/v1/users/departments` from a static list. AC-002.9 is proven by a page test that overrides that GET.
5. The shipped app has one datasource until the tenant layer (KAN-98, KAN-99) lands. AC-001.14 and AC-003.20 are proven with the test router in `TwoTenantDatabases`.
6. In browser mock mode a renamed Department keeps its old name in the 2.2 table, for the reason of item 4. The `workspace` mock serves 2.3 for the two Departments the `users` mock lists, by copying their ids and names, not importing them (decision D18). AC-004.9 is proven by a page test.

## User Stories

### STORY-001: Create a Department with its Space
- As an Admin, I want to create a Department by name so that its people, Projects and Wiki have a place in the Workspace.
- Acceptance:
  - AC-001.1: WHEN an Admin sends `POST /api/v1/departments` with an allowed name, THE system SHALL store one Department in status `ACTIVE` under that name with leading and trailing whitespace removed and inner spaces kept, and answer 201 with its `departmentId` and stored `name`.
  - AC-001.2: WHEN a Department is created, THE system SHALL create exactly one Space for it in the same transaction, with the same name as the Department, status `ACTIVE`, and no Knowledge Manager.
  - AC-001.3: WHEN a Department is created, THE system SHALL write one audit entry in the same transaction with the signed-in Admin's id and email as actor, action `DEPARTMENT_CHANGE`, target type `DEPARTMENT`, target id the Department's `departmentId`, and details holding `operation` = `CREATE`, `name` and `spaceId`.
  - AC-001.4: WHEN a Department is created, THE system SHALL include it in the response of `GET /api/v1/users/departments`.
  - AC-001.5: IF the name is missing, empty or only whitespace, THEN THE system SHALL answer 400 with code MSG01 on field `name` and write no Department, Space or audit entry.
  - AC-001.6: IF the name is not blank and as received is longer than 50 characters, THEN THE system SHALL answer 400 with code MSG02 and `max_length` 50 on field `name` and write no Department, Space or audit entry.
  - AC-001.7: IF the name after whitespace removal equals the name of an Active Department, THEN THE system SHALL answer 409 with code MSG43 on field `name` and write no Department, Space or audit entry.
  - AC-001.8: IF the name after whitespace removal equals the name of a Deleted Department, THEN THE system SHALL answer 409 with code MSG43 on field `name` and write no Department, Space or audit entry.
  - AC-001.9: IF the name after whitespace removal differs from an existing Department's name only in letter case, THEN THE system SHALL answer 409 with code MSG43 on field `name` and write no Department, Space or audit entry.
  - AC-001.10: IF two requests with the same name arrive at the same moment, THEN THE system SHALL create one Department, one Space and one audit entry, answer one request with 201, and answer the other with 409 MSG43.
  - AC-001.11: IF writing the Space or the audit entry fails, THEN THE system SHALL leave no Department, Space or audit entry from that request and answer 500 with code MSG06.
  - AC-001.12: IF a signed-in user without the Admin role sends a create request with an allowed name, THEN THE system SHALL answer 403 with code MSG08 and write no Department, Space or audit entry.
  - AC-001.13: IF a create request has no signed-in user, THEN THE system SHALL answer 401 with code MSG07 and write no Department, Space or audit entry.
  - AC-001.14: WHILE a request is bound to one Workspace, THE system SHALL write only to that Workspace's database and SHALL NOT treat a Department of another Workspace as a duplicate.
  - AC-001.15: IF no Workspace is bound to the request, THEN THE system SHALL answer 500 with code MSG06 and write no Department, Space or audit entry.
  - AC-001.16: WHERE an audit entry has action `DEPARTMENT_CHANGE`, THE Audit Log screen SHALL label that action "Department change".

### STORY-002: New Department on Workspace Settings
- As an Admin, I want a New Department button and a list on Workspace Settings so that I can create a Department and see the result.
- Acceptance:
  - AC-002.1: WHEN an Admin opens 2.2 Workspace Settings, THE web app SHALL show a "New Department" button and a table labelled "Departments" with one column, Name, listing the Active Departments from `GET /api/v1/users/departments`.
  - AC-002.2: WHILE the Departments are loading, THE web app SHALL show the loading state in place of the table.
  - AC-002.3: IF the Departments cannot be loaded, THEN THE web app SHALL show the error state with a retry action in place of the table and keep the "New Department" button.
  - AC-002.4: WHERE the Workspace has no Active Department, THE web app SHALL show MSG04 in the table.
  - AC-002.5: WHEN the Admin clicks "New Department", THE web app SHALL open a popup titled "New Department" with an empty required field "Department name" and the buttons Save and Cancel.
  - AC-002.6: IF the field is empty or only whitespace when the Admin clicks Save, THEN THE web app SHALL show "The Department name field is required." (MSG01) below the field, send no request and keep the popup open.
  - AC-002.7: IF the field is not only whitespace and holds more than 50 characters when the Admin clicks Save, THEN THE web app SHALL show "Department name must not exceed 50 characters." (MSG02) below the field, send no request, keep the popup open and keep every character typed.
  - AC-002.8: WHEN the Admin clicks Save with an allowed name, THE web app SHALL send the name with leading and trailing whitespace removed and SHALL send one request however many times Save is clicked while it runs.
  - AC-002.9: WHEN the server answers 201, THE web app SHALL close the popup, show "Changes saved successfully." (MSG05) and refresh the Departments query that the table and the Add User form share, so the new Department appears in the table.
  - AC-002.10: IF the server answers 409 with MSG43 on field `name`, THEN THE web app SHALL show "A Department with this name already exists." below the field and keep the popup open with the typed name.
  - AC-002.11: IF the server answers any other error, THEN THE web app SHALL show the message of the returned code and keep the popup open with the typed name.
  - AC-002.12: WHEN the Admin clicks Cancel or the close icon while no save is running, THE web app SHALL close the popup, send no request and show an empty field the next time the popup opens.
  - AC-002.13: IF a user without the Admin role opens 2.2 Workspace Settings, THEN THE web app SHALL show the no-permission page with MSG08 and no "New Department" button.
  - AC-002.14: WHILE a save is running, THE web app SHALL ignore Cancel and the close icon.

### STORY-003: Read and rename a Department with its Space
- As an Admin, I want to rename a Department when its team is renamed so that every screen shows the correct name, and its Space follows.
- In this story "allowed name" means a name that passes AC-003.8 and AC-003.9; whitespace removal and character counting are as in STORY-001 (decision D22).
- Acceptance:
  - AC-003.1: WHEN an Admin sends `GET /api/v1/departments/{departmentId}` for an Active Department, THE system SHALL answer 200 with its `departmentId` and `name`.
  - AC-003.2: IF the `departmentId` of a read or rename request names no Department, names a Deleted Department, or is not a UUID, THEN THE system SHALL answer 404 with code MSG04 and write no Department, Space or audit entry.
  - AC-003.3: WHEN an Admin sends `PUT /api/v1/departments/{departmentId}` with an allowed name for an Active Department, THE system SHALL store that name with leading and trailing whitespace removed and inner spaces kept, and answer 200 with the `departmentId` and stored `name`.
  - AC-003.4: WHEN a Department is renamed, THE system SHALL set the name of its Space to the same name in the same transaction and keep the Space's status and Knowledge Manager.
  - AC-003.5: WHEN a Department is renamed, THE system SHALL write one audit entry in the same transaction with the signed-in Admin's id and email as actor, action `DEPARTMENT_CHANGE`, target type `DEPARTMENT`, target id the `departmentId`, and details holding `operation` = `UPDATE`, `before` = `{name: <old name>}`, `after` = `{name: <new name>}` and `spaceId`.
  - AC-003.6: WHEN a Department is renamed, THE system SHALL keep its status and its Projects unchanged, and keep its default Project WHILE `defaultProjectId` names the current one (STORY-012).
  - AC-003.7: WHEN a Department is renamed, THE system SHALL return the new name for it in `GET /api/v1/users/departments` and `GET /api/v1/departments/{departmentId}`.
  - AC-003.8: IF the name is missing, empty or only whitespace, THEN THE system SHALL answer 400 with code MSG01 on field `name` and write no Department, Space or audit entry.
  - AC-003.9: IF the name is not blank and as received is longer than 50 characters, THEN THE system SHALL answer 400 with code MSG02 and `max_length` 50 on field `name` and write no Department, Space or audit entry.
  - AC-003.10: IF the name after whitespace removal equals the name of another Active Department, THEN THE system SHALL answer 409 with code MSG43 on field `name` and write no Department, Space or audit entry.
  - AC-003.11: IF the name after whitespace removal equals the name of a Deleted Department, THEN THE system SHALL answer 409 with code MSG43 on field `name` and write no Department, Space or audit entry.
  - AC-003.12: IF the name after whitespace removal differs from another Department's name only in letter case, THEN THE system SHALL answer 409 with code MSG43 on field `name` and write no Department, Space or audit entry.
  - AC-003.13: WHEN the name after whitespace removal differs from the Department's own current name only in letter case, THE system SHALL rename it as in AC-003.3 to AC-003.5.
  - AC-003.14: IF the name after whitespace removal equals the Department's current name exactly and the default Project does not change (AC-012.10), THEN THE system SHALL answer 200 with the Department and write no Department, Space or audit change.
  - AC-003.15: IF two requests rename two Departments to the same name at the same moment, THEN THE system SHALL apply one, answer it with 200, answer the other with 409 MSG43, and write one audit entry.
  - AC-003.16: IF two requests rename the same Department at the same moment, THEN THE system SHALL apply them one after the other, answer both with 200, and write the second audit entry with a `before` equal to the first entry's `after`.
  - AC-003.17: IF the Department has no Space, or writing the Space or the audit entry fails, THEN THE system SHALL keep the Department's old name, write no Space or audit change, and answer 500 with code MSG06.
  - AC-003.18: IF a signed-in user without the Admin role sends a read request, or a rename request with an allowed name, THEN THE system SHALL answer 403 with code MSG08 and write no Department, Space or audit entry.
  - AC-003.19: IF a read or rename request has no signed-in user, THEN THE system SHALL answer 401 with code MSG07 and write no Department, Space or audit entry.
  - AC-003.20: WHILE a rename request is bound to one Workspace, THE system SHALL write only to that Workspace's database and SHALL NOT treat a Department of another Workspace as a duplicate.
  - AC-003.21: IF no Workspace is bound to a read or rename request, THEN THE system SHALL answer 500 with code MSG06 and write no Department, Space or audit entry.
  - AC-003.22: WHERE a Department is renamed, THE system SHALL keep earlier audit entries of that Department unchanged.

### STORY-004: Rename on 2.3 Department Detail
- As an Admin, I want to open a Department from Workspace Settings and rename it there so that I can fix its name where I see it.
- Acceptance:
  - AC-004.1: WHEN an Admin opens 2.2 Workspace Settings, THE web app SHALL show each Department name in the Departments table as a link to 2.3 Department Detail of that Department.
  - AC-004.2: WHEN an Admin opens 2.3 Department Detail of an Active Department, THE web app SHALL show its name from `GET /api/v1/departments/{departmentId}`, an "Edit" button and a link "Back to Workspace Settings".
  - AC-004.3: WHILE the Department is loading, THE web app SHALL show the loading state.
  - AC-004.4: IF the Department cannot be loaded, THEN THE web app SHALL show the error state with the message of the returned code (MSG04 for 404) and a retry action, and no "Edit" button.
  - AC-004.5: WHEN the Admin clicks "Edit", THE web app SHALL show the required field "Department name" holding the current name, and the buttons Save and Cancel in place of "Edit".
  - AC-004.6: IF the field is empty or only whitespace when the Admin clicks Save, THEN THE web app SHALL show "The Department name field is required." (MSG01) below the field, send no request and keep the form open.
  - AC-004.7: IF the field is not only whitespace and holds more than 50 characters when the Admin clicks Save, THEN THE web app SHALL show "Department name must not exceed 50 characters." (MSG02) below the field, send no request, keep the form open and keep every character typed.
  - AC-004.8: WHEN the Admin clicks Save with an allowed name, THE web app SHALL send it with leading and trailing whitespace removed in `PUT /api/v1/departments/{departmentId}` and SHALL send one request however many times Save is clicked while it runs.
  - AC-004.9: WHEN the server answers 200, THE web app SHALL close the form, show "Changes saved successfully." (MSG05), show the stored name on 2.3, and refresh every cached query so that 2.2 and the other screens show the new name.
  - AC-004.10: IF the server answers 409 with MSG43 on field `name`, THEN THE web app SHALL show "A Department with this name already exists." below the field and keep the form open with the typed name.
  - AC-004.11: IF the server answers any other error, THEN THE web app SHALL show the message of the returned code and keep the form open with the typed name.
  - AC-004.12: WHEN the Admin clicks Cancel while no save is running, THE web app SHALL close the form, send no request and show the current name, and the next "Edit" SHALL show the current name in the field.
  - AC-004.13: WHILE a save is running, THE web app SHALL ignore Cancel.
  - AC-004.14: IF a user without the Admin role opens 2.3 Department Detail, THEN THE web app SHALL show the no-permission page with MSG08 and no "Edit" button.

### STORY-005: Set up the Workspace (UC-10, backend)
- As the first Admin, I want to enter the company information and the Jira site once, so that users can work and
  Project Managers can connect Jira projects.
- Acceptance:
  - AC-005.1: WHEN an Admin calls `POST /api/v1/workspace/setup` with `companyName` and `jiraSiteUrl` while the
    Workspace is not set up and every check passes, THE backend SHALL store the `workspace` row with the bound
    Workspace's id as `workspace_id`, the normalised site address, the site's cloud id (AC-011.8), no Jira credential
    and `sign_in_provider` `GOOGLE`, and answer 201 with the Workspace information of AC-006.2.
  - AC-005.2: IF a required field is missing or blank, THEN THE backend SHALL answer 400 with `code` `MSG01` on that
    field and store nothing.
  - AC-005.3: IF `companyName` is longer than 100 characters or `jiraSiteUrl` longer than 200, THEN THE backend SHALL
    answer 400 with `code` `MSG02` and `params` `max_length` on that field and store nothing.
  - AC-005.5: IF `jiraSiteUrl` breaks the site rule of AC-011.3, THEN THE backend SHALL answer 400 with `code` `MSG61`
    on `jiraSiteUrl`, call nothing outside and store nothing.
  - AC-005.6: WHEN the fields are valid, THE backend SHALL read the site's cloud id (AC-011.8) before it links the site
    or stores anything.
  - AC-005.8: WHEN the cloud id is read, THE backend SHALL call `JiraSiteLinker.linkJiraSite(siteUrl)` in the same
    transaction that stores the row, so that a refused link stores no row.
  - AC-005.9: IF the linker refuses because the site is linked to another Workspace, THEN THE backend SHALL answer
    400 with `code` `MSG153` and `params` `tracker_name` = `Jira` on `jiraSiteUrl` and store nothing (GBR-14).
  - AC-005.10: IF the linker cannot read the site's tenant information, THEN THE backend SHALL answer 400 with `code`
    `MSG61` on `jiraSiteUrl`, log the failure without its body, and store nothing.
  - AC-005.11: IF the Workspace is already set up (a row exists, or a concurrent Setup stored it first), THEN THE
    backend SHALL answer 409 with `code` `WORKSPACE_ALREADY_SET_UP` (a technical code with no user message: the web app redirects instead), change nothing and never overwrite the Jira site (GBR-24).
  - AC-005.12: WHEN Setup succeeds, THE backend SHALL record one audit entry `TRACKER_CONFIGURATION_CHANGE`, target
    type `WORKSPACE`, target id the Workspace id, with details `companyName` and `jiraSiteUrl`.
  - AC-005.13: IF the caller is signed in without the Admin role, THEN THE backend SHALL answer 403 and store nothing.
  - AC-005.14: IF the site's cloud id cannot be read or breaks AC-011.8, THEN THE backend SHALL answer 400 with
    `code` `MSG61` on `jiraSiteUrl`, log the failure without its body, and store nothing.
  - AC-005.15: IF the request carries `serviceAccountEmail` or `serviceAccountToken`, THEN THE backend SHALL ignore
    them and store neither.

### STORY-006: Read the Workspace status and information
- As the web app, I want to know whether the Workspace is set up and read its information, so that it opens Workspace
  Setup or fills Workspace Settings.
- Acceptance:
  - AC-006.1: THE `workspace` module SHALL implement `vn.tsolve.common.session.lookup.WorkspaceStatusLookup#isCompanyInformationEntered()`, which answers true exactly
    when the bound Workspace's tenant database has its `workspace` row (be-foundation AC-003.2).
  - AC-006.2: WHEN an Admin calls `GET /api/v1/workspace` while the Workspace is set up, THE backend SHALL answer 200
    with `companyName`, `jiraSiteUrl`, `jiraAppConnected` and `jiraAppTokenReceivedAt` (AC-006.6), and no field that
    holds or hints at a token, and no cloud id.
  - AC-006.3: IF the Workspace is not set up, THEN THE backend SHALL answer `GET /api/v1/workspace` with 404 and
    `code` `MSG60`.
  - AC-006.4: IF the caller of `GET /api/v1/workspace` does not have the Admin role, THEN THE backend SHALL answer
    403.
  - AC-006.6: WHEN the backend answers with the Workspace information (Setup, read or update), THE backend SHALL set
    `jiraAppConnected` to true exactly when an unexpired app token of AC-014.1 is held for the bound Workspace, and
    `jiraAppTokenReceivedAt` to the time it was received, or null when none is held.

### STORY-007: Update the Workspace information (UC-11, backend)
- As the Admin, I want to edit the company name, so that T-Solve stays correct when the company changes.
- Acceptance:
  - AC-007.1: WHEN an Admin calls `PUT /api/v1/workspace` with `companyName` while the Workspace is set up and the
    field is valid, THE backend SHALL store it and answer 200 with the Workspace information of AC-006.2.
  - AC-007.3: IF `companyName` is missing or blank, THEN THE backend SHALL answer 400 with `code` `MSG01` on that
    field; IF it is longer than 100 characters, THEN THE backend SHALL answer 400 with `MSG02` on that field; in every
    case it changes nothing.
  - AC-007.6: WHEN an update is stored, THE backend SHALL not call Jira.
  - AC-007.7: IF the request carries `jiraSiteUrl`, `serviceAccountEmail` or `serviceAccountToken`, THEN THE backend
    SHALL ignore them and keep the stored site (the site is read-only, GBR-24).
  - AC-007.8: WHEN an update is stored, THE backend SHALL record one audit entry `TRACKER_CONFIGURATION_CHANGE`,
    target type `WORKSPACE`, with the old and new `companyName`.
  - AC-007.9: IF the Workspace is not set up, THEN THE backend SHALL answer `PUT /api/v1/workspace` with 404 and
    `code` `MSG60`.
  - AC-007.10: IF the caller does not have the Admin role, THEN THE backend SHALL answer 403 and change nothing.

### STORY-008: Jira access for other modules
- As `tracker`, the Jira import and the Project module, I want the current Workspace's Jira access, so that I call
  Jira as the T-Solve Jira App on the company's site.
- Acceptance:
  - AC-008.1: THE `workspace` module SHALL implement `tracker`'s `JiraServiceAccount.current()`, returning the bound
    Workspace's site address and the cloud id and token held by STORY-014, or empty while the Workspace is not set up
    or holds no unexpired app token. The bound Workspace is the one of the worker job running (`WorkerTenantService`)
    or else of the web request (`TenantScope`).
  - AC-008.2: THE backend SHALL have no other `JiraServiceAccount` bean: the stand-in `ConfiguredJiraServiceAccount`
    and its `tsolve.tracker.jira.site-url`, `service-account-email`, `service-account-token` settings are removed.
  - AC-008.4: IF the held token's cloud id differs from the cloud id stored at Setup, THEN THE implementation SHALL
    return empty and log the Workspace id only, so that a token of another site is never used for this Workspace.

### STORY-009: Screen 2.1 Workspace Setup
- As the first Admin, I want a Setup screen, so that I enter the information right after my first sign-in.
- Acceptance:
  - AC-009.1: WHILE a signed-in Admin's `/me` has `workspaceReady` false, THE web app SHALL open 2.1 Workspace Setup
    and show `MSG60` (existing guard `RequireWorkspace`, fe-foundation AC-003.2).
  - AC-009.2: THE screen SHALL have the fields Company name (at most 100 characters) and Jira site address (at most
    200, placeholder `https://company.atlassian.net`), both required, and no service account field.
  - AC-009.3: WHEN the Admin clicks Save with an empty required field, THE screen SHALL show `MSG01` under that field
    and send nothing.
  - AC-009.4: WHEN the backend answers with a field error (`MSG01`, `MSG02`, `MSG61`, `MSG153`), THE screen SHALL show
    the message under that field and keep every value entered.
  - AC-009.5: WHEN Setup succeeds, THE web app SHALL show `MSG05`, refetch `/me` and open the User List.
  - AC-009.6: IF the backend answers 409 (`WORKSPACE_ALREADY_SET_UP`), THEN THE web app SHALL show no error, refetch `/me` and
    open 2.2 Workspace Settings.
  - AC-009.7: WHILE the request is in flight, THE Save button SHALL be disabled, so the form is not sent twice.

### STORY-010: Screen 2.2 Workspace Settings, company information section
- As the Admin, I want to see and edit the Workspace information, so that I keep it correct.
- Acceptance:
  - AC-010.1: WHEN an Admin opens 2.2 Workspace Settings, THE screen SHALL load `GET /api/v1/workspace` and show the
    company name as an editable field, the Jira site address as read-only text and the Jira App status of AC-010.10,
    and no service account field.
  - AC-010.3: WHEN the Admin saves a valid company name, THE screen SHALL send `PUT /api/v1/workspace` with
    `companyName` only and show `MSG05`.
  - AC-010.4: WHEN the backend answers with a field error, THE screen SHALL show it under that field and keep the
    values entered.
  - AC-010.5: WHEN the Admin cancels, THE screen SHALL restore the loaded values and send nothing.
  - AC-010.6: IF loading fails, THEN THE screen SHALL show the shared error state with a retry.
  - AC-010.10: THE screen SHALL show a read-only "Jira App" line: WHILE `jiraAppConnected` is true, "Connected" with
    `jiraAppTokenReceivedAt` as the last update in the Workspace's local time; otherwise "Not connected" with the text
    "Install the T-Solve Jira App on this Jira site."

### STORY-011: Site rule and cloud id
- As the operations team, I want the outbound calls kept safe, so that a Workspace cannot make the backend call an
  internal address.
- Acceptance:
  - AC-011.3: THE site rule SHALL accept only an address that, after trimming and lower-casing, is `https`, has no
    user info, no port, no query, no fragment, no path other than empty or `/`, and a host that is a single label
    followed by `.atlassian.net`; THE normalised form SHALL be `https://<host>`.
  - AC-011.4: THE interim `JiraSiteLinker` SHALL read `https://<host>/_edge/tenant_info`, return its `cloudId`, follow
    no redirect, and refuse (as AC-005.10) when the answer is not 200 or has no `cloudId`.
  - AC-011.6: IF `WORKSPACE_SECRET_KEY` is missing or is not the Base64 form of exactly 32 bytes, THEN THE backend SHALL
    fail to start with an error that names the setting but not its value.
  - AC-011.8: WHEN the backend needs the site's cloud id (AC-005.6), THE backend SHALL read it from
    `https://<host>/_edge/tenant_info` of the normalised site as AC-011.4 does (no redirect, 200 with a `cloudId`),
    and accept it only when it is a UUID in canonical form (36 characters, hexadecimal and hyphens), so that it can
    never change the gateway path.
  - AC-011.11: WHEN this change is deployed, THE schema SHALL clear `service_account_email` and
    `service_account_api_token` of every `workspace` row (a new changeset), so no customer Jira credential stays
    stored.

### STORY-012: Default Project of a Department (API)
- As an Admin, I want to choose which Project of a support Department receives the issues created from the Knowledge Chatbot, so that those issues land in a Jira project that can take them.
- In this story a "Project option" is `{projectId, jiraProjectKey}`: the Project's public id and its Jira project key. The "current default Project" is the stored default Project while it is Active, else none (AC-012.3). "The Jira check" is the check of AC-012.7.
- Acceptance:
  - AC-012.1: WHEN an Admin sends `GET /api/v1/departments/{departmentId}` for an Active Department, THE system SHALL answer 200 with `departmentId`, `name`, `defaultProject` (the current default Project as a Project option, or null) and `projects` (every Active Project of that Department as a Project option, ordered by `jiraProjectKey`, empty when it has none).
  - AC-012.2: WHEN `PUT /api/v1/departments/{departmentId}` succeeds, THE system SHALL answer 200 with the body of AC-012.1 as stored after the save.
  - AC-012.3: IF the stored default Project is not Active, THEN THE system SHALL answer `defaultProject` null and treat the Department as having no default Project in every check of this story.
  - AC-012.4: WHEN an Admin sends the PUT with an allowed name and `defaultProjectId` naming an Active Project of this Department that is not the current default Project, and the Jira check passes, THE system SHALL store that Project as the Department's default Project.
  - AC-012.5: IF `defaultProjectId` names no Project, a Project that is not Active, or a Project of another Department, THEN THE system SHALL answer 400 with code MSG04 and `params` `{}` on field `defaultProjectId`, make no Jira call, and write no Department, Space or audit change.
  - AC-012.6: WHEN the PUT has `defaultProjectId` null or absent while the Department has a current default Project, THE system SHALL store no default Project for it and make no Jira call.
  - AC-012.7: WHEN `defaultProjectId` names a Project that is not the current default Project, THE system SHALL, before it opens the write transaction, call Jira with the Workspace's Jira access (`JiraServiceAccount`: the Jira App's token, `Authorization: Bearer` on `https://api.atlassian.com/ex/jira/{cloudId}`) and pass the check only when (a) `GET /rest/api/3/mypermissions?projectKey={jiraProjectKey}&permissions=CREATE_ISSUES,MODIFY_REPORTER` answers 200 with `havePermission` true for both, (b) `GET /rest/api/3/issue/createmeta/{jiraProjectKey}/issuetypes` lists an issue type named `Task` (ignoring letter case) that is not a sub-task, and (c) `GET /rest/api/3/issue/createmeta/{jiraProjectKey}/issuetypes/{issueTypeId}` for that type lists no field that is `required`, has `hasDefaultValue` false, and has a `fieldId` other than `summary`, `description`, `project`, `issuetype`, `reporter` and `labels`.
  - AC-012.8: IF the Workspace has no usable Jira access (AC-008.1 empty), a permission of (a) is missing, or Jira answers 401, 403, 404 or any other error status, times out or cannot be reached, THEN THE system SHALL answer 400 with code MSG156 and `params` `{tracker_name: "Jira"}` on field `defaultProjectId` and write no Department, Space or audit change.
  - AC-012.9: IF the Project has no issue type of (b) or a field of (c) is required, THEN THE system SHALL answer 400 with code MSG157 and `params` `{project_name: <jiraProjectKey>}` on field `defaultProjectId` and write no Department, Space or audit change.
  - AC-012.10: WHILE `defaultProjectId` names the current default Project, or is null or absent while there is none, THE system SHALL make no Jira call and leave the stored default Project unchanged.
  - AC-012.11: WHEN a save changes the name, the default Project or both, THE system SHALL write one audit entry as in AC-003.5 whose `before` and `after` hold `name` and, when the default Project changed, also `defaultProject` (`{projectId, jiraProjectKey}` or null); `spaceId` is present when the name changed.
  - AC-012.12: WHEN only the default Project changes, THE system SHALL leave the Department's name and its Space unchanged.
  - AC-012.13: IF, when the write transaction starts, the chosen Project is no longer an Active Project of this Department, THEN THE system SHALL answer 400 with code MSG04 and `params` `{}` on field `defaultProjectId` and write no Department, Space or audit change.
  - AC-012.14: IF the request is refused by AC-003.2, AC-003.8, AC-003.9, AC-003.18, AC-003.19 or AC-003.21, THEN THE system SHALL make no Jira call.
  - AC-012.15: IF the name is refused with MSG43 (AC-003.10 to AC-003.12) or the write fails (AC-003.17), THEN THE system SHALL keep the old name and the old default Project and write no audit entry.
  - AC-012.16: IF two requests change the default Project of the same Department to two different Projects at the same moment, THEN THE system SHALL apply them one after the other, answer both with 200, keep the Project of the one applied last, and write the second audit entry with a `before.defaultProject` equal to the first entry's `after.defaultProject`.
  - AC-012.17: THE Jira check SHALL use the timeouts of Non-Functional 6, and no Jira body, token or credential SHALL reach a response, a log line or an audit entry.

### STORY-013: Default Project on 2.3 Department Detail
- As an Admin, I want a Default Project field on Department Detail so that I choose, change or clear it with the name.
- Acceptance:
  - AC-013.1: WHEN an Admin opens 2.3 Department Detail, THE web app SHALL show below the name a "Default Project" item holding the `jiraProjectKey` of `defaultProject`, or "None" when it is null.
  - AC-013.2: WHEN the Admin clicks "Edit", THE web app SHALL show, below "Department name", an optional select "Default Project" holding the current default Project (or "None"), whose options are "None" followed by the `jiraProjectKey` of each entry of `projects` in the order received.
  - AC-013.3: WHERE the Department has no Active Project, THE select SHALL offer only "None".
  - AC-013.4: THE select SHALL carry the help text "Leave it as None for a Department that does not handle support requests. Issues from the Knowledge Chatbot are created in this Project with the type Task."
  - AC-013.5: WHEN the Admin clicks Save with an allowed name, THE web app SHALL send in one `PUT /api/v1/departments/{departmentId}` the trimmed name and `defaultProjectId` set to the chosen Project's `projectId`, or null for "None".
  - AC-013.6: WHEN the server answers 200, THE web app SHALL close the form, show "Changes saved successfully." (MSG05) and show the stored default Project on 2.3.
  - AC-013.7: IF the server answers 400 with MSG04, MSG156 or MSG157 on field `defaultProjectId`, THEN THE web app SHALL show that message with its `params` filled in below the "Default Project" select and keep the form open with the typed name and the chosen Project.
  - AC-013.8: WHEN the Admin clicks Cancel while no save is running, THE web app SHALL show the stored default Project, and the next "Edit" SHALL show the stored default Project in the select.
  - AC-013.9: WHILE a save is running, THE "Default Project" select SHALL be disabled.
  - AC-013.10: THE web app's message catalog SHALL hold MSG157 as a field message with the text `{project_name} cannot receive Chatbot issues: it has no issue type Task or requires other fields. Choose another Project.`

### STORY-014: Receive the Jira App's token (KAN-349)
- As the backend, I want the T-Solve Jira App to hand me its app token, so that I can call Jira for a Workspace when no
  one is inside Jira (Pull from Jira, the MSG80 comment, the Project read).
- Acceptance:
  - AC-014.1: WHEN `POST /api/v1/jira-app/app-token` arrives with a FIT that verifies (tracker STORY-001) and a
    non-blank `x-forge-oauth-system` header, THE backend SHALL find the Workspace of the FIT's `cloudId` through
    `JiraWorkspaceLookup`, bind it, keep `{token, cloudId, receivedAt}` for that Workspace in Redis encrypted as
    Non-Functional 4 with an expiry equal to the token's `exp`, and answer 204 with no body.
  - AC-014.2: IF the FIT's site has no Workspace, THEN THE backend SHALL answer 403 with `code` `MSG08` and keep
    nothing.
  - AC-014.3: IF the header is missing or blank, or the token's `exp` cannot be read from its payload or is not in the
    future, THEN THE backend SHALL answer 400 with `code` `MSG06` and keep nothing.
  - AC-014.4: IF the FIT's `app.apiBaseUrl` is not exactly `https://api.atlassian.com/ex/jira/` followed by its
    `cloudId` in canonical UUID form, THEN THE backend SHALL answer 400 with `code` `MSG06` and keep nothing.
  - AC-014.5: IF a token is already held for the Workspace with a later `exp`, THEN THE backend SHALL keep that one and
    answer 204.
  - AC-014.6: THE token SHALL not appear in any response body, log line, audit entry, exception message or
    `toString()`; a log line names only the Workspace id and the outcome.
  - AC-014.7: IF the held value cannot be decrypted (wrong key or tampered value), THEN THE backend SHALL treat the
    Workspace as holding no token, log an error without the value and delete the entry.
  - AC-014.8: THE endpoint SHALL accept only Jira App callers (role `JIRA_APP` of tracker STORY-001); a web session or
    an anonymous call SHALL get 401 with `code` `MSG08` as the other Jira App endpoints.

## Out of Scope
- UC-14 and UC-15: Delete and Restore Department. Each comes through Change Mode when its Jira story starts (KAN-122, KAN-123).
- The reader of the default Project: the Knowledge Chatbot issue form (UC-58, KAN-241 and KAN-242). It must offer only Departments whose default Project is Active (AC-012.3).
- The writes that clear a default Project when its Project is Removed (GBR-21, the remove-Project story of `project-integration`) or its Department is Deleted (GBR-17, KAN-122). Neither removal exists yet; until those stories set `default_project_id` to NULL, a default Project that is not Active reads as none (AC-012.3).
- The Managers tab and the Delete button of screen 2.3 (no story owns the tab; Delete is KAN-122).
- The Departments table columns Project Managers, Projects, Members and Tickets, the "Show deleted" switch and the Restore button. No story owns them yet.
- A Knowledge Manager, permission row, folder or document for the new Space. The Admin assigns a Knowledge Manager in UC-05, UC-07 and UC-09; GBR-30's own-Department View is derived, not stored.
- The CSRF header in the web app (Interim limits, item 3).
- Per-request Workspace binding and real sign-in (KAN-275, Interim limits, items 1 and 2).
- The UUID key alignment of `department` and `space` (KAN-248).
- A live browser journey against the real backend. It needs the two items above.
- The real catalog and its cross-Workspace uniqueness of the Jira site (MSG153 for another Workspace's site, platform
  AC-004.20/24): platform Task 18 `jira-site-link`. Until then the interim linker of AC-011.4 cannot detect a site
  linked elsewhere; AC-005.9 is tested with a refusing test double. **[Interim]**
- `/me` itself (be-foundation `me-session`): this module only implements `WorkspaceStatusLookup`.
- Hiding the side menu on 2.1 Workspace Setup (SRS screen 2.1 "It has no menu"): a layout change in `fe-foundation`
  (`AppShell`), requested there.
- `token_expiry_date`: stays null; Jira has no API to read it.
- Turning issue creation off by leaving the service account empty (UC-11 alternative): T-Solve has no service
  account since KAN-349; Jira access comes from the installed Jira App.

## Interface Contracts

### Exposed by this module

Departments (KAN-120):

`POST /api/v1/departments`, operationId `createDepartment`, tag `departments`, role Admin. Added to `frontend/openapi/tsolve-api.yaml` before the code.

Request, `application/json`, schema `CreateDepartmentRequest`:

```json
{ "name": "IT Support" }
```

| Field | Type | Rules |
|-------|------|-------|
| `name` | string, required | Not blank, at most 50 characters as received. Stored with leading and trailing whitespace removed. Whitespace is what Java `String.strip()` removes and a character is one Java `char` (decision D22). A name that is blank and over 50 characters gets MSG01. |

Response 201, schema `DepartmentRef`:

```json
{ "departmentId": "3f2a7c1e-52d1-4b7e-9d1f-0a4ab3c1d201", "name": "IT Support" }
```

Errors are `application/problem+json` with `code`, `params` and, for a field error, `fieldErrors` of `{field, code, params}`.

| Status | `code` | `params` | `fieldErrors[0]` | When |
|--------|--------|----------|------------------|------|
| 400 | MSG01 | `{field_name: "name"}` | `name`, MSG01, same params | Name missing, empty or only whitespace |
| 400 | MSG02 | `{field_name: "name", max_length: 50}` | `name`, MSG02, same params | Name over 50 characters |
| 401 | MSG07 | `{}` | none | No signed-in user |
| 403 | MSG08 | `{}` | none | Not an Admin |
| 409 | MSG43 | `{}` | `name`, MSG43, `{}` | Name already used, including Deleted, ignoring case |
| 500 | MSG06 | `{}` | none | Unexpected failure, or no Workspace bound |

Message texts come from `frontend/src/shared/messages/catalog.ts`, with the `params` above filled in. MSG01 and MSG02 name the field, so a server-side MSG01 reads "The name field is required."; the web app's own check shows the label "Department name" first (AC-002.6, AC-002.7). MSG05 "Changes saved successfully."; MSG06 "Something went wrong. Please try again later."; MSG08 "You do not have permission to access this page."; MSG43 "A Department with this name already exists."

`AuditAction` in the contract gains the value `DEPARTMENT_CHANGE`.

Service interface for later modules: `vn.tsolve.workspace.service.DepartmentService`, method `create(CreateDepartmentRequest)` returning `DepartmentRefResponse`.

`GET /api/v1/departments/{departmentId}`, operationId `getDepartment`, tag `departments`, role Admin. Response 200, schema `DepartmentDetail` (default Project, below), for an Active Department. Errors 401 MSG07, 403 MSG08, 404 MSG04 (unknown, Deleted, or a path id that is not a UUID), 500 MSG06.

`PUT /api/v1/departments/{departmentId}`, operationId `updateDepartment`, tag `departments`, role Admin. Request `application/json`, schema `UpdateDepartmentRequest`, with the same `name` field and rules as `CreateDepartmentRequest`, and `defaultProjectId` (STORY-012):

```json
{ "name": "IT Service Desk", "defaultProjectId": "7c1e3f2a-52d1-4b7e-9d1f-0a4ab3c1d301" }
```

Response 200, schema `DepartmentDetail`, with the stored name and default Project. Errors as for `createDepartment` (400 MSG01, 400 MSG02, 401 MSG07, 403 MSG08, 409 MSG43, 500 MSG06), plus 404 MSG04 with `params` `{}` and no `fieldErrors` for an unknown or Deleted Department or a path id that is not a UUID. Both paths are added to `frontend/openapi/tsolve-api.yaml` before the code; the tag description becomes "Create, read and rename a Department and its Space (SRS UC-12, UC-13)".

MSG04 "No results found."

`DepartmentService` gains `get(UUID departmentId)` and `update(UUID departmentId, UpdateDepartmentRequest request)`, both returning `DepartmentDetailResponse` (default Project, below).

Default Project (KAN-121, STORY-012):

| Field of `UpdateDepartmentRequest` | Type | Rules |
|-------|------|-------|
| `defaultProjectId` | string `uuid`, nullable, not required | The chosen Project's `projectId`. Null or absent: no default Project. An id that is not an Active Project of this Department: 400 MSG04 on `defaultProjectId` |

```json
{ "departmentId": "3f2a7c1e-52d1-4b7e-9d1f-0a4ab3c1d201", "name": "Customer Support",
  "defaultProject": { "projectId": "7c1e3f2a-52d1-4b7e-9d1f-0a4ab3c1d301", "jiraProjectKey": "SUP" },
  "projects": [ { "projectId": "7c1e3f2a-52d1-4b7e-9d1f-0a4ab3c1d301", "jiraProjectKey": "SUP" } ] }
```

Schemas (above, a `DepartmentDetail`): `DepartmentDetail { departmentId (uuid), name, defaultProject (DepartmentProjectOption, nullable), projects (DepartmentProjectOption[]) }`, all required; `DepartmentProjectOption { projectId (uuid), jiraProjectKey }`, both required. `DepartmentRef` is unchanged.

Added errors of `updateDepartment`, all with `fieldErrors[0].field` `defaultProjectId`:

| Status | `code` | `params` | When |
|--------|--------|----------|------|
| 400 | MSG04 | `{}` | Not an Active Project of this Department (AC-012.5, AC-012.13) |
| 400 | MSG156 | `{tracker_name: "Jira"}` | No usable Jira access (the Jira App's token), a permission missing, or Jira refused, timed out or could not be reached (AC-012.8) |
| 400 | MSG157 | `{project_name: <jiraProjectKey>}` | No issue type Task, or another field required (AC-012.9) |

MSG156 `The service account could not connect to {tracker_name} or lacks the required permissions.` MSG157 `{project_name} cannot receive Chatbot issues: it has no issue type Task or requires other fields. Choose another Project.`

Workspace information (KAN-119):

```
POST /api/v1/workspace/setup   (Admin)  body WorkspaceSetupRequest  → 201 WorkspaceInfo | 400 | 403 | 409
GET  /api/v1/workspace         (Admin)                               → 200 WorkspaceInfo | 403 | 404
PUT  /api/v1/workspace         (Admin)  body WorkspaceUpdateRequest → 200 WorkspaceInfo | 400 | 403 | 404

WorkspaceSetupRequest  { companyName, jiraSiteUrl }   both string, required
WorkspaceUpdateRequest { companyName }
WorkspaceInfo          { companyName, jiraSiteUrl, jiraAppConnected (boolean),
                         jiraAppTokenReceivedAt (date-time, nullable) }
POST /api/v1/jira-app/app-token  (Jira App, FIT + header x-forge-oauth-system) → 204 | 400 | 401 | 403  (STORY-014)
Errors: ProblemDetails with code / params / field, as the rest of the API.

vn.tsolve.common.session.lookup.WorkspaceStatusLookup { boolean isCompanyInformationEntered(); }  // used by /me (be-foundation)
vn.tsolve.tracker.service.JiraServiceAccount  { Optional<JiraAccount> current(); }  // implemented here
vn.tsolve.tracker.dto.JiraAccount(String siteUrl, String cloudId, String apiToken)  // apiToken = the app token (KAN-349)

Schema: changeset 042 added `jira_cloud_id VARCHAR(36)`; a new changeset clears `service_account_email` and
`service_account_api_token` (AC-011.11). Redis key per Workspace for the app token (STORY-014).
```

### Required from other modules
- identity: `GET /api/v1/users/departments` (Active Departments, sorted by name), `DepartmentRefResponse`, and the interim `TenantScope` port.
- common: `AuditService.record`, `Roles.ADMIN`, `BusinessRuleException`, `GlobalExceptionHandler`.
- fe-foundation: the Admin route guard of 2.2 and 2.3, `FormModal`, `DataTable`, `cells.link`, `LoadingState`, `ErrorState`, form rules and the message catalog.
- fe-foundation: the Admin route guard of 2.2, `FormModal`, `DataTable`, form rules and the message catalog.
- `identity`: `TenantScope.currentWorkspaceId()` (the bound Workspace of the request), `Roles.ADMIN`.
- `worker`: `WorkerTenantService.currentWorkspaceId()` (the Workspace a worker job binds, AC-008.1), optional bean.
- `tracker`: `TicketTrackerClient` gains `checkServiceAccount(JiraAccount)` (implemented in `JiraClient`).
- `tracker` (KAN-121, D39): `TrackerProjectService.checkChatbotProject(String projectKey)` returning `vn.tsolve.tracker.dto.ChatbotProjectCheck` (`READY`, `NOT_READY`, `DENIED`, `UNAVAILABLE`): the check of AC-012.7 as the current service account; it never throws for a Jira failure.
- `platform`: `WorkspaceCatalog.linkJiraSite(siteUrl)` (platform AC-004.19..24). Until it exists, the
  workspace-owned port `JiraSiteLinker` with the same contract is answered by an interim adapter **[Interim]**.
  Change requests for platform (not made here): AC-004.21 should use `MSG61` (baseline System_Messages) instead of
  `MSG156`; AC-001.20 should allow the `workspace` table's key `workspace_id`; AC-004.23 should accept a repeated
  link of the same site on the same Workspace, so a Setup whose tenant commit failed can be retried.
- `fe-foundation`: change request — `AppShell` renders no side menu on `paths.workspaceSetup` (SRS 2.1).
- `dashboard-audit`: `AuditService.record(...)` and `AuditAction.TRACKER_CONFIGURATION_CHANGE`.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name cites its AC id (backend suffix `_AC_001_7`, frontend prefix `AC-002.6`)
- [ ] KAN-121: no new changeset; the rename works on an empty database and on one that holds the existing tables
- [ ] KAN-121 default Project: no new changeset; the Jira calls of AC-012.7 are made once by hand against the staging site and the result noted in the task log
- [ ] `sdd.ts trace workspace` passes
- [ ] Backend: `mvn verify` passes, including the new IT on real PostgreSQL
- [ ] Frontend: `format:check`, `lint`, `typecheck`, `test` and `build` pass, and `generate:api` leaves no diff
- [ ] Both changesets apply on an empty database and on one that holds the existing tables
- [ ] Each task is committed on its own after the user agrees
- [ ] `WORKSPACE_SECRET_KEY` documented in `.env.example`; no secret committed
- [ ] The web app's message catalog holds `MSG61`, `MSG153`, `MSG156` and `MSG157` as System_Messages v3460 gives them (`MSG61` is
      no longer treated as Retired)
