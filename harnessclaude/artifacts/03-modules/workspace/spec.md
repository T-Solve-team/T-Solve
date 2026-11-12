---
module: workspace
date: 2026-11-12
owner: BA
---

# Spec: workspace

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-12 | STORY-001..STORY-007 | Initial spec for KAN-119 (UC-10 Workspace Setup, UC-11 Workspace Settings: company information and the Jira service account). Departments (KAN-120..KAN-123) are out of scope | KAN-119; decisions Q1–Q8 of 12/11 (user) and the self-made decisions listed in design.md "Decisions made without the user" (user delegated them, 12/11) |
| 2026-11-12 | STORY-005 | AC-005.1 reworded: "with no menu" removed; hiding the side menu on 2.1 becomes a change request to `fe-foundation` (Required from other modules, Out of Scope) | The menu is rendered by `fe-foundation`'s `AppShell`, whose code is locked (its spec changed since approval); decision D24 (user delegated decisions, 12/11) |
| 2026-11-12 | STORY-003, STORY-004 | AC-004.1 names the binding a worker job uses (`WorkerTenantService`, else `TenantScope`); AC-003.5 also covers a stored token that cannot be decrypted (MSG156) | Evaluation of Tasks 5 and 6: the suggestion job binds only `WorkerTenantService`, so `current()` failed in the job; an undecryptable token answered 500. Decisions D28, D29 (user delegated decisions, 12/11) |

## Requirements

### Functional
1. The tenant database of every Workspace holds exactly one `workspace` row once the Admin has set the Workspace up
   (UC-10): company name, Jira site address, Jira service account email and its encrypted API token
   (`02_tenant.sql`). Before that the Workspace has no row and is "not set up".
2. Workspace Setup (UC-10, screen 2.1) is done once, by an Admin. It checks the fields, the Jira site address, the
   service account with Jira, links the Jira site through the catalog port, stores the row and records the action in
   the audit log (GBR-14, GBR-24).
3. Workspace Settings (UC-11, screen 2.2) lets an Admin edit the company name and the service account. The Jira site
   address can never be changed after Setup (GBR-24, GBR-37).
4. The service account must authenticate with Jira and hold Browse projects, Create Issues and Modify Reporter
   (GBR-14, GBR-40), checked whenever it is entered or changed.
5. `workspace` tells `/me` whether the Workspace is set up (`workspaceReady`) and gives other modules the current
   service account (`JiraServiceAccount` of `tracker`).

### Non-Functional
1. The API token is encrypted at rest with AES-256-GCM under the key `WORKSPACE_SECRET_KEY`; it never appears in a
   response, a log line, an audit entry or an exception message.
2. The backend makes outbound calls only to a Jira site address that passed the site rule of AC-007.3 (no
   server-side request forgery through the address the Admin types).
3. Every Jira call made for a check has a connect timeout of at most 5 s and a read timeout of at most 10 s.

## User Stories

### STORY-001: Set up the Workspace (UC-10, backend)
- As the first Admin, I want to enter the company information, the Jira site and the service account once, so that
  users can work and Project Managers can connect Jira projects.
- Acceptance:
  - AC-001.1: WHEN an Admin calls `POST /api/v1/workspace/setup` with `companyName`, `jiraSiteUrl`,
    `serviceAccountEmail` and `serviceAccountToken` while the Workspace is not set up and every check passes, THE
    backend SHALL store the `workspace` row with the bound Workspace's id as `workspace_id`, the normalised site
    address, the email, the encrypted token and `sign_in_provider` `GOOGLE`, and answer 201 with the Workspace
    information of AC-002.2.
  - AC-001.2: IF a required field is missing or blank, THEN THE backend SHALL answer 400 with `code` `MSG01` on that
    field and store nothing.
  - AC-001.3: IF `companyName` is longer than 100 characters, `jiraSiteUrl` longer than 200 or `serviceAccountEmail`
    longer than 100, THEN THE backend SHALL answer 400 with `code` `MSG02` and `params` `max_length` on that field and
    store nothing.
  - AC-001.4: IF `serviceAccountEmail` is not a valid email address, THEN THE backend SHALL answer 400 with `code`
    `MSG03` on `serviceAccountEmail` and store nothing.
  - AC-001.5: IF `jiraSiteUrl` breaks the site rule of AC-007.3, THEN THE backend SHALL answer 400 with `code` `MSG61`
    on `jiraSiteUrl`, call nothing outside and store nothing.
  - AC-001.6: WHEN the fields are valid, THE backend SHALL check the service account against the normalised site as
    in STORY-007 before it links the site or stores anything.
  - AC-001.7: IF the service account check fails, THEN THE backend SHALL answer 400 with `code` `MSG156` and `params`
    `tracker_name` = `Jira` on `serviceAccountToken`, link nothing and store nothing.
  - AC-001.8: WHEN the service account check passes, THE backend SHALL call `JiraSiteLinker.linkJiraSite(siteUrl)`
    in the same transaction that stores the row, so that a refused link stores no row.
  - AC-001.9: IF the linker refuses because the site is linked to another Workspace, THEN THE backend SHALL answer
    400 with `code` `MSG153` and `params` `tracker_name` = `Jira` on `jiraSiteUrl` and store nothing (GBR-14).
  - AC-001.10: IF the linker cannot read the site's tenant information, THEN THE backend SHALL answer 400 with `code`
    `MSG61` on `jiraSiteUrl`, log the failure without the token, and store nothing.
  - AC-001.11: IF the Workspace is already set up (a row exists, or a concurrent Setup stored it first), THEN THE
    backend SHALL answer 409 with `code` `WORKSPACE_ALREADY_SET_UP` (a technical code with no user message: the web app redirects instead), change nothing and never overwrite the Jira site (GBR-24).
  - AC-001.12: WHEN Setup succeeds, THE backend SHALL record one audit entry `TRACKER_CONFIGURATION_CHANGE`, target
    type `WORKSPACE`, target id the Workspace id, with details `companyName`, `jiraSiteUrl` and
    `serviceAccountEmail`, never the token.
  - AC-001.13: IF the caller is signed in without the Admin role, THEN THE backend SHALL answer 403 and store nothing.

### STORY-002: Read the Workspace status and information
- As the web app, I want to know whether the Workspace is set up and read its information, so that it opens Workspace
  Setup or fills Workspace Settings.
- Acceptance:
  - AC-002.1: THE `workspace` module SHALL implement `WorkspaceStatusLookup.isReady()`, which answers true exactly
    when the bound Workspace's tenant database has its `workspace` row (be-foundation AC-003.2).
  - AC-002.2: WHEN an Admin calls `GET /api/v1/workspace` while the Workspace is set up, THE backend SHALL answer 200
    with `companyName`, `jiraSiteUrl` and `serviceAccountEmail`, and no field that holds or hints at the token.
  - AC-002.3: IF the Workspace is not set up, THEN THE backend SHALL answer `GET /api/v1/workspace` with 404 and
    `code` `MSG60`.
  - AC-002.4: IF the caller of `GET /api/v1/workspace` does not have the Admin role, THEN THE backend SHALL answer
    403.

### STORY-003: Update the Workspace information (UC-11, backend)
- As the Admin, I want to edit the company name and the service account, so that T-Solve stays correct when the
  company changes.
- Acceptance:
  - AC-003.1: WHEN an Admin calls `PUT /api/v1/workspace` with `companyName`, `serviceAccountEmail` and an optional
    `serviceAccountToken` while the Workspace is set up and every check passes, THE backend SHALL store the new values
    and answer 200 with the Workspace information of AC-002.2.
  - AC-003.2: WHERE `serviceAccountToken` is absent or blank, THE backend SHALL keep the stored token.
  - AC-003.3: IF `companyName` or `serviceAccountEmail` is missing or blank, THEN THE backend SHALL answer 400 with
    `code` `MSG01` on that field; IF a field breaks the limits of AC-001.3 or the email format of AC-001.4, THEN THE
    backend SHALL answer 400 with `MSG02` or `MSG03` on that field; in every case it changes nothing.
  - AC-003.4: WHEN the email or the token differs from the stored one, THE backend SHALL check the resulting service
    account (new or stored email with new or stored token) against the stored site as in STORY-007 before it stores
    anything.
  - AC-003.5: IF that check fails, or the stored token it needs cannot be decrypted, THEN THE backend SHALL answer
    400 with `code` `MSG156` and `params` `tracker_name` = `Jira` on `serviceAccountToken` and change nothing.
  - AC-003.6: WHILE only the company name changes, THE backend SHALL not call Jira.
  - AC-003.7: IF the request carries `jiraSiteUrl`, THEN THE backend SHALL ignore it and keep the stored site (the
    site is read-only, GBR-24).
  - AC-003.8: WHEN an update is stored, THE backend SHALL record one audit entry `TRACKER_CONFIGURATION_CHANGE`,
    target type `WORKSPACE`, with the old and new `companyName` and `serviceAccountEmail` and `tokenChanged`
    true/false, never a token.
  - AC-003.9: IF the Workspace is not set up, THEN THE backend SHALL answer `PUT /api/v1/workspace` with 404 and
    `code` `MSG60`.
  - AC-003.10: IF the caller does not have the Admin role, THEN THE backend SHALL answer 403 and change nothing.

### STORY-004: The service account for other modules
- As `tracker` and the Chatbot, I want the current Workspace's service account, so that I call Jira as the company.
- Acceptance:
  - AC-004.1: THE `workspace` module SHALL implement `tracker`'s `JiraServiceAccount.current()`, returning the bound
    Workspace's site address, email and decrypted token, or empty while the Workspace is not set up. The bound
    Workspace is the one of the worker job running (`WorkerTenantService`) or else of the web request (`TenantScope`).
  - AC-004.2: THE backend SHALL have no other `JiraServiceAccount` bean: the stand-in `ConfiguredJiraServiceAccount`
    and its `tsolve.tracker.jira.site-url`, `service-account-email`, `service-account-token` settings are removed.
  - AC-004.3: IF the stored token cannot be decrypted (wrong key or tampered value), THEN THE implementation SHALL log
    an error without the value and return empty, so no Jira call is made with a broken token.

### STORY-005: Screen 2.1 Workspace Setup
- As the first Admin, I want a Setup screen, so that I enter the information right after my first sign-in.
- Acceptance:
  - AC-005.1: WHILE a signed-in Admin's `/me` has `workspaceReady` false, THE web app SHALL open 2.1 Workspace Setup
    and show `MSG60` (existing guard `RequireWorkspace`, fe-foundation AC-003.2).
  - AC-005.2: THE screen SHALL have the fields Company name (at most 100 characters), Jira site address (at most 200,
    placeholder `https://company.atlassian.net`), Service account email (at most 100) and Service account API token
    (masked input), all required.
  - AC-005.3: WHEN the Admin clicks Save with an empty required field, THE screen SHALL show `MSG01` under that field
    and send nothing.
  - AC-005.4: WHEN the backend answers with a field error (`MSG01`, `MSG02`, `MSG03`, `MSG61`, `MSG153`, `MSG156`),
    THE screen SHALL show the message under that field and keep every value entered.
  - AC-005.5: WHEN Setup succeeds, THE web app SHALL show `MSG05`, refetch `/me` and open the User List.
  - AC-005.6: IF the backend answers 409 (`WORKSPACE_ALREADY_SET_UP`), THEN THE web app SHALL show no error, refetch `/me` and
    open 2.2 Workspace Settings.
  - AC-005.7: WHILE the request is in flight, THE Save button SHALL be disabled, so the form is not sent twice.

### STORY-006: Screen 2.2 Workspace Settings, company information section
- As the Admin, I want to see and edit the Workspace information, so that I keep it correct.
- Acceptance:
  - AC-006.1: WHEN an Admin opens 2.2 Workspace Settings, THE screen SHALL load `GET /api/v1/workspace` and show the
    company name and service account email as editable fields and the Jira site address as read-only text.
  - AC-006.2: THE API token field SHALL start empty with the hint that leaving it empty keeps the current token, and
    THE screen SHALL never show a stored token.
  - AC-006.3: WHEN the Admin saves valid values, THE screen SHALL send `PUT /api/v1/workspace` (the token only when
    typed) and show `MSG05`.
  - AC-006.4: WHEN the backend answers with a field error, THE screen SHALL show it under that field and keep the
    values entered.
  - AC-006.5: WHEN the Admin cancels, THE screen SHALL restore the loaded values and send nothing.
  - AC-006.6: IF loading fails, THEN THE screen SHALL show the shared error state with a retry.

### STORY-007: Service account check, site rule and token at rest
- As the operations team, I want the secret and the outbound calls kept safe, so that a Workspace cannot leak its
  token or make the backend call an internal address.
- Acceptance:
  - AC-007.1: WHEN the service account is checked, THE backend SHALL call `GET /rest/api/3/myself` and then
    `GET /rest/api/3/mypermissions?permissions=BROWSE_PROJECTS,CREATE_ISSUES,MODIFY_REPORTER` on the site with Basic
    auth (email, token), and pass only when both answer 200 and all three permissions have `havePermission` true.
  - AC-007.2: IF Jira answers 401 or 403, any other error status, times out or cannot be reached, THEN THE check SHALL
    fail (the caller answers `MSG156`) and no Jira body or credential SHALL reach the response or a log line.
  - AC-007.3: THE site rule SHALL accept only an address that, after trimming and lower-casing, is `https`, has no
    user info, no port, no query, no fragment, no path other than empty or `/`, and a host that is a single label
    followed by `.atlassian.net`; THE normalised form SHALL be `https://<host>`.
  - AC-007.4: THE interim `JiraSiteLinker` SHALL read `https://<host>/_edge/tenant_info`, return its `cloudId`, follow
    no redirect, and refuse (as AC-001.10) when the answer is not 200 or has no `cloudId`.
  - AC-007.5: THE backend SHALL encrypt the token with AES-256-GCM, a fresh random 96-bit nonce per value and the
    Workspace id as associated data, so a value copied to another Workspace's row does not decrypt.
  - AC-007.6: IF `WORKSPACE_SECRET_KEY` is missing or is not the Base64 form of exactly 32 bytes, THEN THE backend SHALL
    fail to start with an error that names the setting but not its value.
  - AC-007.7: THE token SHALL not appear in any response body, log line, audit entry, exception message or
    `toString()` of the module's types.

## Out of Scope
- Departments: create, update, delete, restore and the Departments table of 2.2 (KAN-120..KAN-123, later in this
  module).
- The real catalog and its cross-Workspace uniqueness of the Jira site (MSG153 for another Workspace's site, platform
  AC-004.20/24): platform Task 18 `jira-site-link`. Until then the interim linker of AC-007.4 cannot detect a site
  linked elsewhere; AC-001.9 is tested with a refusing test double. **[Interim]**
- `/me` itself (be-foundation `me-session`): this module only implements `WorkspaceStatusLookup`.
- The per-Project Create Issues / Modify Reporter check when a default Project is chosen (UC-13).
- Hiding the side menu on 2.1 Workspace Setup (SRS screen 2.1 "It has no menu"): a layout change in `fe-foundation`
  (`AppShell`), requested there.
- `token_expiry_date`: stays null; Jira has no API to read it.
- Turning issue creation off by leaving the service account empty (UC-11 alternative): the service account is
  required (screen 2.2).

## Interface Contracts

### Exposed by this module
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
- `identity`: `TenantScope.currentWorkspaceId()` (the bound Workspace of the request), `Roles.ADMIN`.
- `worker`: `WorkerTenantService.currentWorkspaceId()` (the Workspace a worker job binds, AC-004.1), optional bean.
- `tracker`: `TicketTrackerClient` gains `checkServiceAccount(JiraAccount)` (implemented in `JiraClient`).
- `platform`: `WorkspaceCatalog.linkJiraSite(siteUrl)` (platform AC-004.19..24). Until it exists, the
  workspace-owned port `JiraSiteLinker` with the same contract is answered by an interim adapter **[Interim]**.
  Change requests for platform (not made here): AC-004.21 should use `MSG61` (baseline System_Messages) instead of
  `MSG156`; AC-001.20 should allow the `workspace` table's key `workspace_id`; AC-004.23 should accept a repeated
  link of the same site on the same Workspace, so a Setup whose tenant commit failed can be retried.
- `fe-foundation`: change request — `AppShell` renders no side menu on `paths.workspaceSetup` (SRS 2.1).
- `dashboard-audit`: `AuditService.record(...)` and `AuditAction.TRACKER_CONFIGURATION_CHANGE`.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] `mvn verify` passes (tests and `spotless:check`)
- [ ] Frontend: `format:check`, `lint`, `typecheck`, `test`, `build` pass and `npm run generate:api` leaves no diff
- [ ] `WORKSPACE_SECRET_KEY` documented in `.env.example`; no secret committed
- [ ] The web app's message catalog holds `MSG61`, `MSG153` and `MSG156` as System_Messages v3460 gives them (`MSG61` is
      no longer treated as Retired)
