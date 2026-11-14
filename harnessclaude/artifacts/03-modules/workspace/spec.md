---
module: workspace
date: 2026-11-13
owner: BA
---

# Spec: workspace

Covers UC-12 Create Department (Jira KAN-120, STORY-001..STORY-002) and UC-10 Workspace Setup and UC-11 Workspace Settings, company information (Jira KAN-119, STORY-003..STORY-009). Source: SRS v3460, folder `Sua_31-10` (UC-12; screen 2.2; GBR-17, GBR-29, GBR-37; MSG01, MSG02, MSG05, MSG06, MSG07, MSG08, MSG43). Decisions: KAN-120 `artifacts/04-plans/workspace/iteration-1/kan-120-decisions.md`; KAN-119 the master design's "Decisions made without the user" (D1–D30) and `iteration-1/kan-119/design.md`.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-11 | STORY-001, STORY-002 | Initial spec: UC-12 Create Department | KAN-120 |
| 2026-11-11 | STORY-001, STORY-002 | Builder check: blank wins over length (AC-001.6, AC-002.7); Cancel is ignored during a save (AC-002.12, new AC-002.14); AC-002.9 names the shared query; whitespace and character defined; interim limits 4 and 5 added | Codex builder check |
| 2026-11-12 | STORY-003..STORY-009 | Initial spec for KAN-119 (UC-10 Workspace Setup, UC-11 Workspace Settings: company information and the Jira service account). Departments (KAN-120..KAN-123) are out of scope | KAN-119; decisions Q1–Q8 of 12/11 (user) and the self-made decisions listed in design.md "Decisions made without the user" (user delegated them, 12/11) |
| 2026-11-12 | STORY-007 | AC-007.1 reworded: "with no menu" removed; hiding the side menu on 2.1 becomes a change request to `fe-foundation` (Required from other modules, Out of Scope) | The menu is rendered by `fe-foundation`'s `AppShell`, whose code is locked (its spec changed since approval); decision D24 (user delegated decisions, 12/11) |
| 2026-11-12 | STORY-005, STORY-006 | AC-006.1 names the binding a worker job uses (`WorkerTenantService`, else `TenantScope`); AC-005.5 also covers a stored token that cannot be decrypted (MSG156) | Evaluation of Tasks 5 and 6: the suggestion job binds only `WorkerTenantService`, so `current()` failed in the job; an undecryptable token answered 500. Decisions D28, D29 (user delegated decisions, 12/11) |
| 2026-11-13 | STORY-003..STORY-009 | KAN-119 stories renumbered from STORY-001..STORY-007 (AC-001.x..AC-007.x become AC-003.x..AC-009.x), wording unchanged | KAN-120 (#202, STORY-001, STORY-002 Departments) reached `main` first; the two specs merge into one |
| 2026-11-13 | all | Merged the KAN-120 spec (main, #202) with the KAN-119 spec: Out of Scope no longer lists UC-10/UC-11 or the Departments KAN-120 built; requirements, contracts and Definition of Done joined | KAN-119 merges main |

## Requirements

### Functional
1. An Admin creates a Department by giving its name. The Department starts Active.
2. The Department name is unique in the Workspace, ignoring letter case and including deleted Departments.
3. Creating a Department also creates its Department Space. Both exist or neither does (GBR-29).
4. Each creation is recorded in the audit log.
5. Screen 2.2 Workspace Settings has the "New Department" button, its popup, and a Departments table with the Name column.
6. A new Department is offered when the Admin adds a Project Manager (UC-05).
7. The tenant database of every Workspace holds exactly one `workspace` row once the Admin has set the Workspace up
   (UC-10): company name, Jira site address, Jira service account email and its encrypted API token
   (`02_tenant.sql`). Before that the Workspace has no row and is "not set up".
8. Workspace Setup (UC-10, screen 2.1) is done once, by an Admin. It checks the fields, the Jira site address, the
   service account with Jira, links the Jira site through the catalog port, stores the row and records the action in
   the audit log (GBR-14, GBR-24).
9. Workspace Settings (UC-11, screen 2.2) lets an Admin edit the company name and the service account. The Jira site
   address can never be changed after Setup (GBR-24, GBR-37).
10. The service account must authenticate with Jira and hold Browse projects, Create Issues and Modify Reporter
   (GBR-14, GBR-40), checked whenever it is entered or changed.
11. `workspace` tells `/me` whether the Workspace is set up (`workspaceReady`) and gives other modules the current
   service account (`JiraServiceAccount` of `tracker`).

### Non-Functional
1. The name rules hold in the database: two requests sent at the same moment cannot create two Departments with one name.
2. The Department, its Space and the audit entry are written in one database transaction, with no outbound call inside it.
3. A Workspace's Departments live only in that Workspace's database (GBR-37, ADR-0002). The Workspace of a request never comes from request content.
4. The API token is encrypted at rest with AES-256-GCM under the key `WORKSPACE_SECRET_KEY`; it never appears in a
   response, a log line, an audit entry or an exception message.
5. The backend makes outbound calls only to a Jira site address that passed the site rule of AC-009.3 (no
   server-side request forgery through the address the Admin types).
6. Every Jira call made for a check has a connect timeout of at most 5 s and a read timeout of at most 10 s.

### Interim limits
These are known limits of the code this story builds on. They are not defects of this story.

1. The endpoint needs a Workspace bound to the request. Until KAN-275 (identity Tasks 26a and 26b) lands, only the `identity-mock` profile binds one, as for the User List. A request with no bound Workspace is answered with MSG06 (AC-001.15).
2. A real Keycloak sign-in cannot reach this screen until KAN-275 lands.
3. The web app does not send the CSRF header yet, so a browser POST to the real backend gets 403. That is a separate quick change to `frontend/src/shared/api/http.ts` for every write (decision D17). This story is verified with MSW and backend tests.
4. In browser mock mode a new Department shows MSG05 but does not appear in the table or the Add User form, because the `users` mock answers `GET /api/v1/users/departments` from a static list. AC-002.9 is proven by a page test that overrides that GET.
5. The shipped app has one datasource until the tenant layer (KAN-98, KAN-99) lands. AC-001.14 is proven with the test router in `TwoTenantDatabases`.

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

### STORY-003: Set up the Workspace (UC-10, backend)
- As the first Admin, I want to enter the company information, the Jira site and the service account once, so that
  users can work and Project Managers can connect Jira projects.
- Acceptance:
  - AC-003.1: WHEN an Admin calls `POST /api/v1/workspace/setup` with `companyName`, `jiraSiteUrl`,
    `serviceAccountEmail` and `serviceAccountToken` while the Workspace is not set up and every check passes, THE
    backend SHALL store the `workspace` row with the bound Workspace's id as `workspace_id`, the normalised site
    address, the email, the encrypted token and `sign_in_provider` `GOOGLE`, and answer 201 with the Workspace
    information of AC-004.2.
  - AC-003.2: IF a required field is missing or blank, THEN THE backend SHALL answer 400 with `code` `MSG01` on that
    field and store nothing.
  - AC-003.3: IF `companyName` is longer than 100 characters, `jiraSiteUrl` longer than 200 or `serviceAccountEmail`
    longer than 100, THEN THE backend SHALL answer 400 with `code` `MSG02` and `params` `max_length` on that field and
    store nothing.
  - AC-003.4: IF `serviceAccountEmail` is not a valid email address, THEN THE backend SHALL answer 400 with `code`
    `MSG03` on `serviceAccountEmail` and store nothing.
  - AC-003.5: IF `jiraSiteUrl` breaks the site rule of AC-009.3, THEN THE backend SHALL answer 400 with `code` `MSG61`
    on `jiraSiteUrl`, call nothing outside and store nothing.
  - AC-003.6: WHEN the fields are valid, THE backend SHALL check the service account against the normalised site as
    in STORY-009 before it links the site or stores anything.
  - AC-003.7: IF the service account check fails, THEN THE backend SHALL answer 400 with `code` `MSG156` and `params`
    `tracker_name` = `Jira` on `serviceAccountToken`, link nothing and store nothing.
  - AC-003.8: WHEN the service account check passes, THE backend SHALL call `JiraSiteLinker.linkJiraSite(siteUrl)`
    in the same transaction that stores the row, so that a refused link stores no row.
  - AC-003.9: IF the linker refuses because the site is linked to another Workspace, THEN THE backend SHALL answer
    400 with `code` `MSG153` and `params` `tracker_name` = `Jira` on `jiraSiteUrl` and store nothing (GBR-14).
  - AC-003.10: IF the linker cannot read the site's tenant information, THEN THE backend SHALL answer 400 with `code`
    `MSG61` on `jiraSiteUrl`, log the failure without the token, and store nothing.
  - AC-003.11: IF the Workspace is already set up (a row exists, or a concurrent Setup stored it first), THEN THE
    backend SHALL answer 409 with `code` `WORKSPACE_ALREADY_SET_UP` (a technical code with no user message: the web app redirects instead), change nothing and never overwrite the Jira site (GBR-24).
  - AC-003.12: WHEN Setup succeeds, THE backend SHALL record one audit entry `TRACKER_CONFIGURATION_CHANGE`, target
    type `WORKSPACE`, target id the Workspace id, with details `companyName`, `jiraSiteUrl` and
    `serviceAccountEmail`, never the token.
  - AC-003.13: IF the caller is signed in without the Admin role, THEN THE backend SHALL answer 403 and store nothing.

### STORY-004: Read the Workspace status and information
- As the web app, I want to know whether the Workspace is set up and read its information, so that it opens Workspace
  Setup or fills Workspace Settings.
- Acceptance:
  - AC-004.1: THE `workspace` module SHALL implement `WorkspaceStatusLookup.isReady()`, which answers true exactly
    when the bound Workspace's tenant database has its `workspace` row (be-foundation AC-003.2).
  - AC-004.2: WHEN an Admin calls `GET /api/v1/workspace` while the Workspace is set up, THE backend SHALL answer 200
    with `companyName`, `jiraSiteUrl` and `serviceAccountEmail`, and no field that holds or hints at the token.
  - AC-004.3: IF the Workspace is not set up, THEN THE backend SHALL answer `GET /api/v1/workspace` with 404 and
    `code` `MSG60`.
  - AC-004.4: IF the caller of `GET /api/v1/workspace` does not have the Admin role, THEN THE backend SHALL answer
    403.

### STORY-005: Update the Workspace information (UC-11, backend)
- As the Admin, I want to edit the company name and the service account, so that T-Solve stays correct when the
  company changes.
- Acceptance:
  - AC-005.1: WHEN an Admin calls `PUT /api/v1/workspace` with `companyName`, `serviceAccountEmail` and an optional
    `serviceAccountToken` while the Workspace is set up and every check passes, THE backend SHALL store the new values
    and answer 200 with the Workspace information of AC-004.2.
  - AC-005.2: WHERE `serviceAccountToken` is absent or blank, THE backend SHALL keep the stored token.
  - AC-005.3: IF `companyName` or `serviceAccountEmail` is missing or blank, THEN THE backend SHALL answer 400 with
    `code` `MSG01` on that field; IF a field breaks the limits of AC-003.3 or the email format of AC-003.4, THEN THE
    backend SHALL answer 400 with `MSG02` or `MSG03` on that field; in every case it changes nothing.
  - AC-005.4: WHEN the email or the token differs from the stored one, THE backend SHALL check the resulting service
    account (new or stored email with new or stored token) against the stored site as in STORY-009 before it stores
    anything.
  - AC-005.5: IF that check fails, or the stored token it needs cannot be decrypted, THEN THE backend SHALL answer
    400 with `code` `MSG156` and `params` `tracker_name` = `Jira` on `serviceAccountToken` and change nothing.
  - AC-005.6: WHILE only the company name changes, THE backend SHALL not call Jira.
  - AC-005.7: IF the request carries `jiraSiteUrl`, THEN THE backend SHALL ignore it and keep the stored site (the
    site is read-only, GBR-24).
  - AC-005.8: WHEN an update is stored, THE backend SHALL record one audit entry `TRACKER_CONFIGURATION_CHANGE`,
    target type `WORKSPACE`, with the old and new `companyName` and `serviceAccountEmail` and `tokenChanged`
    true/false, never a token.
  - AC-005.9: IF the Workspace is not set up, THEN THE backend SHALL answer `PUT /api/v1/workspace` with 404 and
    `code` `MSG60`.
  - AC-005.10: IF the caller does not have the Admin role, THEN THE backend SHALL answer 403 and change nothing.

### STORY-006: The service account for other modules
- As `tracker` and the Chatbot, I want the current Workspace's service account, so that I call Jira as the company.
- Acceptance:
  - AC-006.1: THE `workspace` module SHALL implement `tracker`'s `JiraServiceAccount.current()`, returning the bound
    Workspace's site address, email and decrypted token, or empty while the Workspace is not set up. The bound
    Workspace is the one of the worker job running (`WorkerTenantService`) or else of the web request (`TenantScope`).
  - AC-006.2: THE backend SHALL have no other `JiraServiceAccount` bean: the stand-in `ConfiguredJiraServiceAccount`
    and its `tsolve.tracker.jira.site-url`, `service-account-email`, `service-account-token` settings are removed.
  - AC-006.3: IF the stored token cannot be decrypted (wrong key or tampered value), THEN THE implementation SHALL log
    an error without the value and return empty, so no Jira call is made with a broken token.

### STORY-007: Screen 2.1 Workspace Setup
- As the first Admin, I want a Setup screen, so that I enter the information right after my first sign-in.
- Acceptance:
  - AC-007.1: WHILE a signed-in Admin's `/me` has `workspaceReady` false, THE web app SHALL open 2.1 Workspace Setup
    and show `MSG60` (existing guard `RequireWorkspace`, fe-foundation AC-003.2).
  - AC-007.2: THE screen SHALL have the fields Company name (at most 100 characters), Jira site address (at most 200,
    placeholder `https://company.atlassian.net`), Service account email (at most 100) and Service account API token
    (masked input), all required.
  - AC-007.3: WHEN the Admin clicks Save with an empty required field, THE screen SHALL show `MSG01` under that field
    and send nothing.
  - AC-007.4: WHEN the backend answers with a field error (`MSG01`, `MSG02`, `MSG03`, `MSG61`, `MSG153`, `MSG156`),
    THE screen SHALL show the message under that field and keep every value entered.
  - AC-007.5: WHEN Setup succeeds, THE web app SHALL show `MSG05`, refetch `/me` and open the User List.
  - AC-007.6: IF the backend answers 409 (`WORKSPACE_ALREADY_SET_UP`), THEN THE web app SHALL show no error, refetch `/me` and
    open 2.2 Workspace Settings.
  - AC-007.7: WHILE the request is in flight, THE Save button SHALL be disabled, so the form is not sent twice.

### STORY-008: Screen 2.2 Workspace Settings, company information section
- As the Admin, I want to see and edit the Workspace information, so that I keep it correct.
- Acceptance:
  - AC-008.1: WHEN an Admin opens 2.2 Workspace Settings, THE screen SHALL load `GET /api/v1/workspace` and show the
    company name and service account email as editable fields and the Jira site address as read-only text.
  - AC-008.2: THE API token field SHALL start empty with the hint that leaving it empty keeps the current token, and
    THE screen SHALL never show a stored token.
  - AC-008.3: WHEN the Admin saves valid values, THE screen SHALL send `PUT /api/v1/workspace` (the token only when
    typed) and show `MSG05`.
  - AC-008.4: WHEN the backend answers with a field error, THE screen SHALL show it under that field and keep the
    values entered.
  - AC-008.5: WHEN the Admin cancels, THE screen SHALL restore the loaded values and send nothing.
  - AC-008.6: IF loading fails, THEN THE screen SHALL show the shared error state with a retry.

### STORY-009: Service account check, site rule and token at rest
- As the operations team, I want the secret and the outbound calls kept safe, so that a Workspace cannot leak its
  token or make the backend call an internal address.
- Acceptance:
  - AC-009.1: WHEN the service account is checked, THE backend SHALL call `GET /rest/api/3/myself` and then
    `GET /rest/api/3/mypermissions?permissions=BROWSE_PROJECTS,CREATE_ISSUES,MODIFY_REPORTER` on the site with Basic
    auth (email, token), and pass only when both answer 200 and all three permissions have `havePermission` true.
  - AC-009.2: IF Jira answers 401 or 403, any other error status, times out or cannot be reached, THEN THE check SHALL
    fail (the caller answers `MSG156`) and no Jira body or credential SHALL reach the response or a log line.
  - AC-009.3: THE site rule SHALL accept only an address that, after trimming and lower-casing, is `https`, has no
    user info, no port, no query, no fragment, no path other than empty or `/`, and a host that is a single label
    followed by `.atlassian.net`; THE normalised form SHALL be `https://<host>`.
  - AC-009.4: THE interim `JiraSiteLinker` SHALL read `https://<host>/_edge/tenant_info`, return its `cloudId`, follow
    no redirect, and refuse (as AC-003.10) when the answer is not 200 or has no `cloudId`.
  - AC-009.5: THE backend SHALL encrypt the token with AES-256-GCM, a fresh random 96-bit nonce per value and the
    Workspace id as associated data, so a value copied to another Workspace's row does not decrypt.
  - AC-009.6: IF `WORKSPACE_SECRET_KEY` is missing or is not the Base64 form of exactly 32 bytes, THEN THE backend SHALL
    fail to start with an error that names the setting but not its value.
  - AC-009.7: THE token SHALL not appear in any response body, log line, audit entry, exception message or
    `toString()` of the module's types.

## Out of Scope
- UC-13, UC-14 and UC-15: Update, Delete and Restore Department. Each comes through Change Mode when its Jira story starts (KAN-121 to KAN-123).
- The Departments table columns Project Managers, Projects, Members and Tickets, the "Show deleted" switch and the Restore button. No story owns them yet.
- A Knowledge Manager, permission row, folder or document for the new Space. The Admin assigns a Knowledge Manager in UC-05, UC-07 and UC-09; GBR-30's own-Department View is derived, not stored.
- The CSRF header in the web app (Interim limits, item 3).
- Per-request Workspace binding and real sign-in (KAN-275, Interim limits, items 1 and 2).
- The UUID key alignment of `department` and `space` (KAN-248).
- A live browser journey against the real backend. It needs the two items above.
- The real catalog and its cross-Workspace uniqueness of the Jira site (MSG153 for another Workspace's site, platform
  AC-004.20/24): platform Task 18 `jira-site-link`. Until then the interim linker of AC-009.4 cannot detect a site
  linked elsewhere; AC-003.9 is tested with a refusing test double. **[Interim]**
- `/me` itself (be-foundation `me-session`): this module only implements `WorkspaceStatusLookup`.
- The per-Project Create Issues / Modify Reporter check when a default Project is chosen (UC-13).
- Hiding the side menu on 2.1 Workspace Setup (SRS screen 2.1 "It has no menu"): a layout change in `fe-foundation`
  (`AppShell`), requested there.
- `token_expiry_date`: stays null; Jira has no API to read it.
- Turning issue creation off by leaving the service account empty (UC-11 alternative): the service account is
  required (screen 2.2).

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

Workspace information (KAN-119):

```
POST /api/v1/workspace/setup   (Admin)  body WorkspaceSetupRequest  → 201 WorkspaceInfo | 400 | 403 | 409
GET  /api/v1/workspace         (Admin)                               → 200 WorkspaceInfo | 403 | 404
PUT  /api/v1/workspace         (Admin)  body WorkspaceUpdateRequest → 200 WorkspaceInfo | 400 | 403 | 404

WorkspaceSetupRequest  { companyName, jiraSiteUrl, serviceAccountEmail, serviceAccountToken }   all string, required
WorkspaceUpdateRequest { companyName, serviceAccountEmail, serviceAccountToken? }
WorkspaceInfo          { companyName, jiraSiteUrl, serviceAccountEmail }
Errors: ProblemDetails with code / params / field, as the rest of the API.

vn.tsolve.common.lookup.WorkspaceStatusLookup { boolean isReady(); }        // used by /me (be-foundation)
vn.tsolve.tracker.service.JiraServiceAccount  { Optional<JiraAccount> current(); }  // implemented here
```

### Required from other modules
- identity: `GET /api/v1/users/departments` (Active Departments, sorted by name), `DepartmentRefResponse`, and the interim `TenantScope` port.
- common: `AuditService.record`, `Roles.ADMIN`, `BusinessRuleException`, `GlobalExceptionHandler`.
- fe-foundation: the Admin route guard of 2.2, `FormModal`, `DataTable`, form rules and the message catalog.
- `identity`: `TenantScope.currentWorkspaceId()` (the bound Workspace of the request), `Roles.ADMIN`.
- `worker`: `WorkerTenantService.currentWorkspaceId()` (the Workspace a worker job binds, AC-006.1), optional bean.
- `tracker`: `TicketTrackerClient` gains `checkServiceAccount(JiraAccount)` (implemented in `JiraClient`).
- `platform`: `WorkspaceCatalog.linkJiraSite(siteUrl)` (platform AC-004.19..24). Until it exists, the
  workspace-owned port `JiraSiteLinker` with the same contract is answered by an interim adapter **[Interim]**.
  Change requests for platform (not made here): AC-004.21 should use `MSG61` (baseline System_Messages) instead of
  `MSG156`; AC-001.20 should allow the `workspace` table's key `workspace_id`; AC-004.23 should accept a repeated
  link of the same site on the same Workspace, so a Setup whose tenant commit failed can be retried.
- `fe-foundation`: change request — `AppShell` renders no side menu on `paths.workspaceSetup` (SRS 2.1).
- `dashboard-audit`: `AuditService.record(...)` and `AuditAction.TRACKER_CONFIGURATION_CHANGE`.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name cites its AC id (backend suffix `_AC_001_7`, frontend prefix `AC-002.6`)
- [ ] `sdd.ts trace workspace` passes
- [ ] Backend: `mvn verify` passes, including the new IT on real PostgreSQL
- [ ] Frontend: `format:check`, `lint`, `typecheck`, `test` and `build` pass, and `generate:api` leaves no diff
- [ ] Both changesets apply on an empty database and on one that holds the existing tables
- [ ] Each task is committed on its own after the user agrees
- [ ] `WORKSPACE_SECRET_KEY` documented in `.env.example`; no secret committed
- [ ] The web app's message catalog holds `MSG61`, `MSG153` and `MSG156` as System_Messages v3460 gives them (`MSG61` is
      no longer treated as Retired)
