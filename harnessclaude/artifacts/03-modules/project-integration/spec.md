---
module: project-integration
date: 2026-11-12
owner: BA
depth: Deep
---

# Spec: project-integration

Covers UC-16 Add a Project only (KAN-127), including reconnecting a Removed Project and the add flow on screen 3.1 Jira Integration. Sources: SRS v3460 in `Sua_31-10`, UC-16, screens 3.1 and 3.2 (the initial settings), GBR-04, GBR-10, GBR-14, GBR-20 and GBR-21; `Report_3_BreakDown/System_Messages.docx.md`; the four Jira acceptance criteria in the KAN-127 brief. Verified context: `artifacts/04-plans/project-integration/iteration-1/kan-127-scout.md`. Decisions: `artifacts/04-plans/project-integration/iteration-1/kan-127-decisions.md`, D1-D37; later decisions supersede the scout where they differ. Deep depth is required by the schema change and shared API contract. This run writes the specs and module definitions only; design, tasks, approval and status changes follow separately.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-12 | STORY-001..007 | Initial spec: UC-16 Add a Project (KAN-127) | KAN-127 |
| 2026-11-12 | STORY-001, 002 | Builder check: AC-001.21 and AC-001.22 for a requested key that differs from Jira's canonical key; AC-002.21 orders the local settings checks before the link check and Jira; the Definition of Done names how changeset 029 is proven (D44) | Builder check |
| 2026-11-12 | STORY-001, 002, 007 | Review of the Codex draft: AC-007.8 no longer puts access before Bean Validation (`@Valid` runs before `@PreAuthorize`); AC-007.3 names a valid body; AC-001.20 and AC-002.16 drop the non-string checks Jackson's scalar coercion does not make (D33, D36 revised) | Framework order and Jackson defaults; the criteria must be testable as written |

## Requirements

### Functional
1. A Project Manager tests a Jira project key using the Workspace's service account, without entering a token (GBR-14).
2. A Project belongs to the caller's own Active Department; adding it makes the caller its Project Manager and a member (GBR-20).
3. The initial Done statuses are the Jira project's Done-category statuses; the Project Manager can choose one or more of any of its statuses. The review range is a whole number of calendar days, at least 1, initially 3 (GBR-04, GBR-10).
4. A Jira project key belongs to one Project across all Departments and statuses in the Workspace. Re-adding an eligible Removed Project reconnects that same Project (GBR-14, GBR-21).
5. Every successful add or reconnect records one audit entry; failure records no change.
6. Screen 3.1 shows the caller's Department and its Projects and opens the Add project popup. Admin keeps the existing placeholder (D30).

### Non-Functional
1. Both Jira reads finish before and outside the write transaction; no outbound call executes with an active database transaction (`backend/AGENTS.md` section 4; AC-002.18).
2. Project, membership and audit changes commit in exactly one transaction, including reconnect; a failed membership or audit write rolls them all back (AC-002.9, AC-002.10, AC-003.9).
3. Logs and responses contain neither the service-account email/token nor a Jira response body, on success and on every error path (tracker Non-Functional 9; AC-001.17).
4. Every Project read and write uses only the bound Workspace's database. No request property, query parameter or body supplies a Workspace or Department to act in (AC-007.6, AC-007.7).
5. Simultaneous Saves cannot create a second row for one Jira key or a second Project Manager membership for one Project (AC-002.11, AC-003.8).

### Interim limits

These are known limits of the current dependencies, not defects of this story.

1. Until KAN-275, only `identity-mock` binds the Workspace for these requests; real sign-in alone cannot complete this flow. Missing binding returns 500 MSG06.
2. The browser client has no CSRF header (KAN-120 D17); a browser POST to the real backend can return 403 until that separate shared-client quick change. This story uses backend tests and MSW for browser verification.
3. The shipped app has one datasource until the tenant layer lands. Workspace isolation is verified using the existing test router and two tenant databases.
4. `JiraServiceAccount.current()` is the configured environment stand-in, using Basic authentication through `JiraClient`. A real scoped service-account token fails with Basic authentication; gateway Bearer support belongs to KAN-125/KAN-119 (D5, RESEARCH-003).
5. Removed Projects are available through seed data and fixtures only; there is no Remove story yet. Use a Removed Project of the caller's Department for reconnect fixtures, such as `OLD`, rather than the scout's inconsistent cross-Department demo membership.
6. The stale MSG48 catalog text is corrected by a separate quick change with FE Lead review before the screen task (D29). The required text in this spec comes from the SRS.

## User Stories

### STORY-001: Test the connection to a Jira project
- As a Project Manager, I want to check the Jira project and see its settings before adding a Project to my Department.
- Acceptance:
  - AC-001.1: WHEN an authorised caller sends `POST /api/v1/projects/connection-test` with a valid key, THE system SHALL use that key stripped with `String.strip()` and upper-cased with `Locale.ROOT` for the link lookup and `TrackerProjectService.findProject`.
  - AC-001.2: IF `jiraProjectKey` is missing, null, empty or only whitespace, THEN THE system SHALL answer 400 MSG01 on field `jiraProjectKey` with params `{field_name: "jiraProjectKey"}`, without a Jira call.
  - AC-001.3: IF a nonblank `jiraProjectKey` exceeds 20 UTF-16 code units as received, THEN THE system SHALL answer 400 MSG02 on field `jiraProjectKey` with params `{field_name: "jiraProjectKey", max_length: 20}`, without a Jira call.
  - AC-001.4: IF the normalised key fails `[A-Z][A-Z0-9_]*`, THEN THE system SHALL answer 409 MSG48 with params `{tracker_name: "Jira"}` and no field error, without a Jira call.
  - AC-001.5: IF the normalised key belongs to an Active Project in any Department of the bound Workspace, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}`, without a Jira call.
  - AC-001.6: IF the normalised key belongs to a Removed Project of another Department, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}`, without a Jira call.
  - AC-001.7: IF the normalised key belongs to a Removed Project of the caller's Department and its previous Project Manager is a different user who is still Active, holds the Project Manager role and belongs to that Department, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}`, without a Jira call.
  - AC-001.8: WHEN the local link check allows the key, THE system SHALL obtain the Browse result and project statuses only through `tracker.service.TrackerProjectService.findProject(key)` as defined by tracker STORY-013.
  - AC-001.9: WHEN the tracker returns `FOUND` for an allowed key, THE system SHALL answer 200 `ProjectConnectionTest` with Jira's canonical `jiraProjectKey` and every returned project status once as `{id, name, done}`.
  - AC-001.10: WHEN the tested Jira project has no existing Project, THE system SHALL return `reconnect: false` and omit `projectId`.
  - AC-001.11: WHEN the tested key belongs to an eligible Removed Project of the caller's Department, THE system SHALL return `reconnect: true` and that Project's existing `projectId`.
  - AC-001.12: WHEN the tested Jira project is new, THE system SHALL prefill `doneStatusIds` with the returned statuses whose `done` is true and `reviewRange` with 3.
  - AC-001.13: WHEN the tested Jira project reconnects a Removed Project, THE system SHALL prefill `doneStatusIds` with its stored ids still in the returned statuses, falling back to the Done-category ids if no stored id remains.
  - AC-001.14: WHEN the tested Jira project reconnects a Removed Project, THE system SHALL prefill `reviewRange` with its stored value or 3 if that value is SQL NULL.
  - AC-001.15: IF the tracker returns `NOT_FOUND` or `DENIED`, THEN THE system SHALL answer 409 MSG48 with params `{tracker_name: "Jira"}` and no field error.
  - AC-001.16: IF the tracker returns `UNAVAILABLE`, THEN THE system SHALL answer 503 MSG06 with params `{}` and no field error.
  - AC-001.17: THE system SHALL exclude the service-account email, token and Jira response bodies from all Project responses and logs.
  - AC-001.18: THE connection test SHALL write no Project, membership, audit entry or other persisted data, on success or failure.
  - AC-001.19: IF Jira's canonical key differs from the requested normalised key and belongs to a Project refused by AC-001.5, AC-001.6 or AC-001.7, THEN THE system SHALL answer the corresponding 409 MSG49 instead of returning a successful test.
  - AC-001.20: IF the JSON body is unreadable, THEN THE system SHALL answer 400 MSG06 with params `{}` and no field error, without a Jira call.
  - AC-001.21: IF the requested normalised key and Jira's canonical key are held by two different Projects, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <Department of the Project holding the canonical key>}` and write nothing.
  - AC-001.22: WHEN exactly one of the requested normalised key and Jira's canonical key is held by a Project and that Project is an eligible Removed Project of the caller's Department, THE system SHALL treat it as the reconnect (`reconnect: true` with its `projectId`), and a Save SHALL store Jira's canonical key on that Project.

### STORY-002: Add the Project
- As a Project Manager, I want to save the Project and its initial settings so that my Department can use its Jira project.
- Acceptance:
  - AC-002.1: WHEN an authorised caller sends `POST /api/v1/projects`, THE system SHALL apply AC-001.1 through AC-001.7 and AC-001.19 to `jiraProjectKey` with the same HTTP statuses, codes, params, fields and local-conflict ordering.
  - AC-002.2: WHEN a Save passes local validation and link checks, THE system SHALL read the Jira project and its statuses again through `TrackerProjectService.findProject` before any write, whether or not a connection test was previously sent.
  - AC-002.3: IF the Save lookup returns `NOT_FOUND`, `DENIED` or `UNAVAILABLE`, THEN THE system SHALL answer respectively 409 MSG48, 409 MSG48 or 503 MSG06 with the params and absence of field errors defined in AC-001.15 and AC-001.16, and write nothing.
  - AC-002.4: IF `doneStatusIds` is missing, null or empty, THEN THE system SHALL answer 400 MSG01 on field `doneStatusIds` with params `{field_name: "doneStatusIds"}` and write nothing.
  - AC-002.5: IF a selected id is null, blank or absent from the project's freshly read statuses, THEN THE system SHALL answer 400 MSG01 on field `doneStatusIds` with params `{field_name: "doneStatusIds"}` and write nothing.
  - AC-002.6: IF `reviewRange` is missing or null, THEN THE system SHALL answer 400 MSG01 on field `reviewRange` with params `{field_name: "reviewRange"}` and write nothing.
  - AC-002.7: IF numeric `reviewRange` is fractional, below 1 or above 2,147,483,647, THEN THE system SHALL answer 400 MSG44 on field `reviewRange` with params `{}` and write nothing.
  - AC-002.8: WHEN a valid Save adds a new Jira project, THE system SHALL insert exactly one `project` row with a generated `projectId`, the caller's Department, Jira's canonical key, the selected ids as a JSONB array in `done_status`, the entered `review_range`, `status = 'ACTIVE'` and `field_mapping = NULL`.
  - AC-002.9: WHEN a new Project is inserted, THE system SHALL insert exactly one `PROJECT_MANAGER` membership for the caller in the same transaction.
  - AC-002.10: WHEN a new Project is inserted, THE system SHALL record exactly one audit entry in that transaction with the signed-in caller as actor, action `TRACKER_CONFIGURATION_CHANGE`, target type `PROJECT`, target id its `projectId`, and details `{operation: "CREATE", jiraProjectKey, departmentId, doneStatusIds, reviewRange}` matching the stored values.
  - AC-002.11: IF two Saves compete to add the same Jira key, THEN THE system SHALL commit one Project, one caller membership and one audit entry, answer the winner 201, and answer the loser 409 MSG49 on `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <winning Department name>}`.
  - AC-002.12: IF inserting the membership or audit entry fails, THEN THE system SHALL roll back all writes from that Save and answer 500 MSG06 with params `{}` and no field error.
  - AC-002.13: WHEN a new Project commits, THE system SHALL answer 201 with its `Project` response.
  - AC-002.14: WHEN a new Project commits, THE existing `ProjectService.findActivePublicIdByJiraProjectKey` SHALL return its `projectId` for the stored canonical key.
  - AC-002.15: WHEN a Save contains repeated valid status ids, THE system SHALL store and return each id once in first-seen order.
  - AC-002.16: IF the JSON body is unreadable, `doneStatusIds` is not an array, or `reviewRange` cannot be read as a number (for example `"abc"`), THEN THE system SHALL answer 400 MSG06 with params `{}` and no field error, and write nothing.
  - AC-002.17: WHEN the write transaction begins after the Jira read, THE system SHALL recheck the caller's eligibility and the current link state before changing any Project or membership.
  - AC-002.18: THE system SHALL execute every Jira call before and outside any active database transaction in the add and reconnect flows.
  - AC-002.19: WHEN changeset `029-project-done-statuses.sql` applies to an existing database, THE migration SHALL convert each non-null legacy `done_status` scalar to a JSONB singleton string array and preserve SQL NULL as SQL NULL without changing another column, row or reference.
  - AC-002.21: IF `reviewRange` breaks AC-002.7 or a selected id is null or blank, THEN THE system SHALL refuse the Save after the Workspace, eligibility and key format checks and before the link check, without a Jira call; an id absent from the project's statuses (AC-002.5) is refused only after the Jira read.
  - AC-002.20: THE migration SHALL leave `review_range` nullable with no new database CHECK, default or backfill, so existing zero and negative fixtures retain their review fallback behaviour (D20).

### STORY-003: Reconnect a Removed Project
- As a Project Manager, I want to reconnect an eligible Removed Project so that its tickets and members remain with the same Project.
- Acceptance:
  - AC-003.1: WHEN a valid Save identifies an eligible Removed Project of the caller's Department, THE system SHALL update that row to `ACTIVE` with Jira's canonical key and the saved `doneStatusIds` and `reviewRange`, using all validation and tracker failure rules of STORY-002.
  - AC-003.2: WHEN a Removed Project reconnects, THE system SHALL keep its existing `projectId`, Department, tickets, Ticket Resolver memberships and `field_mapping` unchanged.
  - AC-003.3: WHEN the previous Project Manager re-adds the Removed Project, THE system SHALL keep the Project's membership rows unchanged.
  - AC-003.4: WHEN another eligible Project Manager re-adds the Removed Project, THE system SHALL delete the previous Project Manager membership if present before upserting the caller's membership as `PROJECT_MANAGER`, including when the caller already has a `MEMBER` row.
  - AC-003.5: WHEN a Removed Project reconnects, THE system SHALL record one audit entry in the same transaction with the signed-in caller as actor, action `TRACKER_CONFIGURATION_CHANGE`, target type `PROJECT`, target id its retained `projectId`, and details `{operation: "RECONNECT", jiraProjectKey, departmentId, doneStatusIds, reviewRange}` matching the saved values.
  - AC-003.6: WHEN the reconnect commits, THE system SHALL answer 200 with the updated `Project` response.
  - AC-003.7: IF the Removed Project belongs to another Department or still has another Active Project Manager holding that role in its Department, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}` and write nothing.
  - AC-003.8: IF simultaneous reconnect or add Saves compete for the same Removed row, THEN THE system SHALL commit one reconnect with one audit entry, answer the winner 200 and the loser 409 MSG49 on `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}`.
  - AC-003.9: IF a membership change or the audit write fails while reconnecting, THEN THE system SHALL answer 500 MSG06 with params `{}` and restore the original Removed row, settings and membership rows with no audit entry from that Save.
  - AC-003.10: WHEN the reconnect commits, THE existing `ProjectService.findActivePublicIdByJiraProjectKey` SHALL return the retained `projectId` for the canonical key.

### STORY-004: Projects of my Department
- As a Project Manager, I want to list the Projects of my Department so that I can see the result of adding or reconnecting them.
- Acceptance:
  - AC-004.1: WHEN an authorised caller sends `GET /api/v1/projects`, THE system SHALL answer 200 `ProjectList` with the caller's own Department as `{departmentId, name}` and all its Active and Removed Projects.
  - AC-004.2: WHEN the system lists Projects, THE system SHALL order them by `jiraProjectKey` ascending without paging.
  - AC-004.3: WHEN the system lists a Project, THE system SHALL return its `projectId`, `jiraProjectKey`, `projectManager`, `memberCount` and `status` as `ProjectRow`.
  - AC-004.4: WHEN a Project's `PROJECT_MANAGER` membership names an Active account, THE system SHALL return that account as `projectManager: {userId, fullName}`.
  - AC-004.5: IF a Project has no `PROJECT_MANAGER` membership or that account is not Active, THEN THE system SHALL return `projectManager: null`.
  - AC-004.6: WHEN the system lists a Project, THE system SHALL set `memberCount` to the count of all its membership rows, including the Project Manager.
  - AC-004.7: IF the caller's Department has no Projects, THEN THE system SHALL answer 200 with its Department and `projects: []`.
  - AC-004.8: THE Projects list SHALL make no Jira call and write no persisted data.

### STORY-005: Projects on Jira Integration (web app, screen 3.1)
- As a Project Manager, I want to see my Department and its Projects on Jira Integration so that I can manage the add flow from one screen.
- Acceptance:
  - AC-005.1: WHEN a Project Manager opens `/jira-integration`, THE web app SHALL load `GET /api/v1/projects` and show the returned Department name in a read-only field labelled "Department".
  - AC-005.2: WHEN the list loads, THE web app SHALL show a Projects table with columns "Project ID", "Jira project key", "Project Manager", "Members" and "Status" using the returned order.
  - AC-005.3: WHEN a row has a `projectManager`, THE web app SHALL show its `fullName` in the Project Manager column.
  - AC-005.4: IF a row has `projectManager: null`, THEN THE web app SHALL show MSG54 in that column.
  - AC-005.5: WHEN a row has status `ACTIVE` or `REMOVED`, THE web app SHALL show respectively "Active" or "Removed".
  - AC-005.6: WHILE the Projects query is loading, THE web app SHALL show the loading state in place of the table.
  - AC-005.7: IF the Projects query fails, THEN THE web app SHALL show the returned message by code and an error state with a retry action.
  - AC-005.8: IF the loaded list is empty, THEN THE web app SHALL show MSG04 in place of the table.
  - AC-005.9: WHEN an Admin without the Project Manager role opens screen 3.1, THE web app SHALL keep the existing Jira Integration placeholder without sending a Projects request or showing the Add project button.
  - AC-005.10: IF a signed-in user has neither Admin nor Project Manager role, THEN THE web app SHALL show the route guard's MSG08 no-permission page without sending a Projects request.

### STORY-006: Add project popup (web app)
- As a Project Manager, I want to test a key and save the initial settings in a popup so that I can add or reconnect a Project.
- Acceptance:
  - AC-006.1: WHEN a Project Manager clicks "Add project" on screen 3.1, THE web app SHALL open a `FormModal` titled "Add project" with an empty required "Project key" field, "Test connection" and Cancel, with settings and Save hidden.
  - AC-006.2: IF Project key is empty or only whitespace when Test connection is clicked, THEN THE web app SHALL show MSG01 below it with params `{field_name: "Project key"}` and send no request.
  - AC-006.3: IF a nonblank Project key exceeds 20 UTF-16 code units as typed, THEN THE web app SHALL show MSG02 below it with params `{field_name: "Project key", max_length: 20}`, preserve the whole input and send no request.
  - AC-006.4: WHEN Test connection is clicked with a locally valid key, THE web app SHALL send one `POST /api/v1/projects/connection-test` with the original `jiraProjectKey`, however often the button is clicked while it runs.
  - AC-006.5: WHEN the connection test answers 200, THE web app SHALL show MSG47 with params `{tracker_name: "Jira"}` as a toast.
  - AC-006.6: WHEN the connection test answers 200, THE web app SHALL reveal a "Done statuses" multi-select containing every returned status by name and id, preselected from `doneStatusIds`.
  - AC-006.7: WHEN the connection test answers 200, THE web app SHALL reveal "Review range" prefilled from `reviewRange` and the Save button.
  - AC-006.8: WHEN the Project Manager edits Project key after a successful test, THE web app SHALL discard the tested result and hide the settings and Save until another successful test.
  - AC-006.9: IF no Done status is selected when Save is clicked, THEN THE web app SHALL show MSG01 below Done statuses with params `{field_name: "Done statuses"}` and send no request.
  - AC-006.10: IF Review range is empty when Save is clicked, THEN THE web app SHALL show MSG01 below it with params `{field_name: "Review range"}` and send no request.
  - AC-006.11: IF Review range is not a whole number from 1 through 2,147,483,647 when Save is clicked, THEN THE web app SHALL show MSG44 below it with params `{}` and send no request.
  - AC-006.12: WHEN Save is clicked with tested settings passing local checks, THE web app SHALL send one `POST /api/v1/projects` with `jiraProjectKey` from the tested canonical key, the selected `doneStatusIds` and numeric `reviewRange`, however often Save is clicked while it runs.
  - AC-006.13: WHEN Save answers 201 or 200, THE web app SHALL close the popup.
  - AC-006.14: WHEN Save answers 201 or 200, THE web app SHALL show MSG05 as a toast.
  - AC-006.15: WHEN Save answers 201 or 200, THE web app SHALL invalidate the generated `listProjects` query key so the Projects table refreshes.
  - AC-006.16: IF either request returns MSG49 on `jiraProjectKey`, THEN THE web app SHALL show that message below Project key with the server's params.
  - AC-006.17: IF either request returns MSG48, THEN THE web app SHALL show it inline in the popup with the server's params.
  - AC-006.18: IF either request returns another field error, THEN THE web app SHALL show its message by code and params below the corresponding field.
  - AC-006.19: IF either request returns another error without a field error, THEN THE web app SHALL show its message by code and params using the shared error handling, falling back to MSG06 when no recognised code exists.
  - AC-006.20: IF Test connection or Save fails, THEN THE web app SHALL keep the popup open with the entered values and any previously revealed settings intact.
  - AC-006.21: WHILE Test connection or Save is running, THE web app SHALL prevent field edits and further Test connection or Save requests.
  - AC-006.22: WHILE Test connection or Save is running, THE web app SHALL ignore Cancel, the close icon, Escape and mask dismissal.
  - AC-006.23: WHEN the Project Manager dismisses the idle popup, THE web app SHALL close it without a request and clear its values, messages and tested result before the next opening.
  - AC-006.24: THE Add project popup SHALL contain no token, service-account email, service-account credential or field mapping editor.

### STORY-007: Restrict Project operations to the caller's Workspace and Department
- As the Workspace, I want all three Project endpoints restricted to eligible Project Managers so that Project links cannot cross Departments or Workspaces.
- Acceptance:
  - AC-007.1: THE handlers for `POST /api/v1/projects/connection-test`, `POST /api/v1/projects` and `GET /api/v1/projects` SHALL each declare `@PreAuthorize` using `Roles.PROJECT_MANAGER`.
  - AC-007.2: IF the caller has no signed-in session, THEN THE system SHALL answer 401 MSG07 with params `{}` and no field error, without a write or Jira call.
  - AC-007.3: IF a signed-in caller without the Project Manager role sends a request with a valid body, THEN THE system SHALL answer 403 MSG08 with params `{}` and no field error, without a write or Jira call.
  - AC-007.4: IF an authenticated Project Manager has no Department, the Department is not Active, or the caller account is not Active or no longer holds that role, THEN THE system SHALL answer 403 MSG08 with params `{}` and no field error, without a write or Jira call.
  - AC-007.5: IF no Workspace is bound for an authenticated Project Manager, THEN THE system SHALL answer 500 MSG06 with params `{}` and no field error, without a write or Jira call.
  - AC-007.6: WHILE any of the three endpoints runs in a bound Workspace, THE system SHALL read and write Project, Department, membership and audit data only in that Workspace's database, allowing the same Jira key in a different Workspace database.
  - AC-007.7: THE system SHALL derive the Workspace from `TenantScope` and the caller's own Department from `app_user.department_id` joined to `department`, without accepting either scope from request content.
  - AC-007.8: THE system SHALL check the bound Workspace and the caller's eligibility (AC-007.4) before the key format check, the link check and any Jira call, for all three endpoints.

## Out of Scope

- UC-17 and UC-18: adding/removing Ticket Resolver members (KAN-128/KAN-129).
- Screen 3.2 Project Detail, including its Settings tab and editing settings after adding (D15). No current story owns that edit; it is a backlog gap.
- Connection status, Last import, per-row Test connection and Remove on screen 3.1 (D13).
- A field mapping editor; a new Project stores NULL for standard Jira fields (D14).
- Storing Jira's project id, following Jira project key renames, or changing existing key readers (D16).
- The GBR-04 end-to-end gap: KAN-126 `watchDone.js` still opens the Solution Form by Jira's Done status category. Reading these stored Done statuses for the Jira App and opening by the chosen ids is a later change; KAN-127 stores the configuration only.
- The Admin's Jira site address and service account configuration (UC-10/UC-11, KAN-119), credential encryption and gateway Bearer support.
- UUID key alignment of the internal tables (KAN-248); public API ids remain UUIDs through existing `public_id` columns.
- A Department's `default_project_id` (UC-13); adding a Project does not choose a default Project.
- CSRF shared-client changes, per-request production Workspace binding and a live browser journey requiring those dependencies.

## Interface Contracts

### Exposed by this module

Exactly three operations are added to `frontend/openapi/tsolve-api.yaml` before implementation, all tagged `projects`, all requiring the Project Manager role. The browser uses generated API code through `@/shared/api` and TanStack Query. No Project credential or caller-supplied Department/Workspace property is in a request schema.

| Method and path | operationId | Request | Success |
|-----------------|-------------|---------|---------|
| `POST /api/v1/projects/connection-test` | `testProjectConnection` | `ProjectConnectionTestRequest` | 200 `ProjectConnectionTest` |
| `POST /api/v1/projects` | `createProject` | `CreateProjectRequest` | 201 new `Project`; 200 reconnected `Project` |
| `GET /api/v1/projects` | `listProjects` | No body or query parameters | 200 `ProjectList` |

`application/json` is used for request and success bodies. All UUID fields below are UUID strings, never internal BIGINT ids. `projectId` refers to `project.public_id`; `departmentId` refers to `department.public_id`; `userId` refers to `app_user.id` (D32).

#### Request schemas

`ProjectConnectionTestRequest`:

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `jiraProjectKey` | string | yes | Nonblank; max 20 UTF-16 code units as received; blank wins over length. Server strips then upper-cases with `Locale.ROOT`; result matches `[A-Z][A-Z0-9_]*`. Missing/null uses MSG01. Invalid format uses MSG48 rather than Bean Validation MSG06. |

`CreateProjectRequest`:

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `jiraProjectKey` | string | yes | Same rules as `ProjectConnectionTestRequest`. |
| `doneStatusIds` | array of strings | yes | At least one; every id is a nonblank exact id in the freshly read project's statuses. Any category is allowed. Duplicates collapse in first-seen order; no trimming of ids. Missing/null/empty or null/blank/unknown element uses MSG01; a body where it is not an array uses MSG06. |
| `reviewRange` | JSON number | yes | Integral value 1..2147483647; schema `type: number`, `multipleOf: 1`, `minimum: 1`, `maximum: 2147483647`. Missing/null uses MSG01; fractional/out-of-range numeric values use MSG44; a value Jackson cannot read as a number (for example `"abc"`) uses unreadable-body MSG06. Never truncate a fractional value. |

The initial 3 is a connection-test prefill, not a Save default: omitting `reviewRange` is an error. The server accepts a valid direct Save without a previous test. A successful test neither reserves a key nor authorises a later Save. Bean Validation of the body (`@Valid`, 400) runs before `@PreAuthorize`, as in every T-Solve controller; the Workspace and eligibility checks precede the key format check and the link check, and local key/link checks precede Jira. Local settings shape/range checks can fail before Jira; checking chosen ids against available statuses uses the new Jira read. No failed Save changes data.

#### Response schemas

`TrackerStatus` (HTTP shape corresponding to tracker `dto.TrackerStatus`):

| Field | Type | Required | Meaning |
|-------|------|----------|---------|
| `id` | string | yes | Jira status id, opaque and nonblank. |
| `name` | string | yes | Jira status name, nonblank. |
| `done` | boolean | yes | True exactly when `statusCategory.key` is `done`. Does not restrict which statuses can be selected. |

`ProjectConnectionTest`:

| Field | Type | Required | Meaning |
|-------|------|----------|---------|
| `jiraProjectKey` | string | yes | Canonical key returned by Jira. |
| `statuses` | array of `TrackerStatus` | yes | Every status once by id, in first-seen order across issue types; can be empty. |
| `reconnect` | boolean | yes | True only for an eligible Removed Project of the caller's Department. |
| `projectId` | UUID string | on reconnect | Existing Project id; omitted for a new Project, never a newly allocated id. |
| `doneStatusIds` | array of strings | yes | New: Done-category ids. Reconnect: retained stored ids, or Done-category ids if none remain; first-seen order, no duplicates. Can be empty and then Save requires a selection. |
| `reviewRange` | integer | yes | New: 3. Reconnect: stored value, or 3 if NULL; a legacy invalid value must be corrected before Save. |

`Project`:

| Field | Type | Required | Meaning |
|-------|------|----------|---------|
| `projectId` | UUID string | yes | Generated once on creation, retained on reconnect. |
| `departmentId` | UUID string | yes | Caller’s own Department, retained on reconnect. |
| `jiraProjectKey` | string | yes | Stored canonical Jira key. |
| `doneStatusIds` | array of strings | yes | Saved unique selected ids. |
| `reviewRange` | integer | yes | Saved whole calendar days, 1..2147483647. |
| `status` | string enum `ACTIVE`, `REMOVED` | yes | Always `ACTIVE` for a successful Save. |

`ProjectList`:

| Field | Type | Required | Meaning |
|-------|------|----------|---------|
| `department` | existing `DepartmentRef` | yes | `{departmentId: UUID string, name: string}` for the caller's own Active Department. |
| `projects` | array of `ProjectRow` | yes | All Active and Removed Projects of that Department, sorted by key; empty array when none. |

`ProjectRow`:

| Field | Type | Required | Meaning |
|-------|------|----------|---------|
| `projectId` | UUID string | yes | Public Project ID displayed in the table. |
| `jiraProjectKey` | string | yes | Stored key. |
| `projectManager` | object or null | yes | `{userId: UUID string, fullName: string}` from the Project Manager membership if the account is Active, otherwise null. This display rule is distinct from the stricter D27 reconnect protection rule. |
| `memberCount` | integer, minimum 0 | yes | Count of all membership rows; Project Manager counts. |
| `status` | string enum `ACTIVE`, `REMOVED` | yes | Browser labels are Active and Removed. |

#### Error body and message parameters

Errors use the existing `application/problem+json` `ProblemDetail` schema: `status`, `code`, `params`, and `fieldErrors: [{field, code, params}]` for a field error. No field error means `fieldErrors` is absent. Each operation's table below defines its errors; success bodies carry no MSG code. Unexpected failures use 500 MSG06.

MSG01 uses `field_name`, for example "The Project key field is required.". MSG02 uses `field_name` and `max_length`, for example "Project key must not exceed 20 characters.". Server `field_name` is the API property name; client validation uses the displayed field label (D31). MSG44 has no params: "Review range must be a whole number of days, 1 or more.". MSG47 is "Connected to Jira successfully." with `tracker_name = Jira`. MSG48 is "Could not connect to Jira. Check the project key and that the service account can browse this project." with `tracker_name = Jira`. MSG49 uses `tracker_name = Jira` and the owning Department's name, for example "This Jira project already belongs to IT Operations.". MSG54 is "No Project Manager. Tickets of this Project are not being reviewed.". MSG04 is "No results found."; MSG05 is "Changes saved successfully.". The web app renders these through the MSG catalog.

#### `testProjectConnection` status table

| Status | `code` | `params` | Field | When |
|--------|--------|----------|-------|------|
| 200 | none | none | none | Lookup is FOUND and link is allowed; no write. |
| 400 | MSG01 | `{field_name: "jiraProjectKey"}` | `jiraProjectKey` | Missing/null/blank key. |
| 400 | MSG02 | `{field_name: "jiraProjectKey", max_length: 20}` | `jiraProjectKey` | Nonblank key too long as received. |
| 400 | MSG06 | `{}` | none | Unreadable body. |
| 401 | MSG07 | `{}` | none | Anonymous caller. |
| 403 | MSG08 | `{}` | none | Not a Project Manager, inactive caller or missing/inactive own Department. |
| 409 | MSG48 | `{tracker_name: "Jira"}` | none | Invalid key format (no Jira call), NOT_FOUND or DENIED, including missing service account and Jira 401/403/other 4xx except 429. |
| 409 | MSG49 | `{tracker_name: "Jira", department_name: <owning Department name>}` | `jiraProjectKey` | Active link anywhere; Removed link in another Department; protected previous Project Manager; canonical-key conflict. Local conflicts make no Jira call. |
| 500 | MSG06 | `{}` | none | No bound Workspace or unexpected failure. |
| 503 | MSG06 | `{}` | none | Tracker UNAVAILABLE: 429, 5xx, timeout, transport failure or unreadable Jira response. |

#### `createProject` status table

| Status | `code` | `params` | Field | When |
|--------|--------|----------|-------|------|
| 201 | none | none | none | New Project, membership and audit committed. |
| 200 | none | none | none | Removed Project reconnected with membership and audit committed. |
| 400 | MSG01 | `{field_name: "jiraProjectKey"}` | `jiraProjectKey` | Missing/null/blank key. |
| 400 | MSG02 | `{field_name: "jiraProjectKey", max_length: 20}` | `jiraProjectKey` | Nonblank key too long as received. |
| 400 | MSG01 | `{field_name: "doneStatusIds"}` | `doneStatusIds` | Missing/null/empty selection, or null/blank/unknown id. |
| 400 | MSG01 | `{field_name: "reviewRange"}` | `reviewRange` | Missing/null range. |
| 400 | MSG44 | `{}` | `reviewRange` | Numeric range fractional, below 1 or above 2147483647. |
| 400 | MSG06 | `{}` | none | Unreadable body, `doneStatusIds` not an array, or a range that cannot be read as a number. |
| 401 | MSG07 | `{}` | none | Anonymous caller. |
| 403 | MSG08 | `{}` | none | Not a Project Manager, inactive caller or missing/inactive own Department, including failed eligibility recheck. |
| 409 | MSG48 | `{tracker_name: "Jira"}` | none | Invalid key format (no Jira call), NOT_FOUND or DENIED on the new read. |
| 409 | MSG49 | `{tracker_name: "Jira", department_name: <owning Department name>}` | `jiraProjectKey` | Disallowed existing/canonical link or a lost create/reconnect race. |
| 500 | MSG06 | `{}` | none | No bound Workspace, failed Project/membership/audit write or unexpected failure; transaction rolls back. |
| 503 | MSG06 | `{}` | none | Tracker UNAVAILABLE on the project or statuses read; no write. |

#### `listProjects` status table

| Status | `code` | `params` | Field | When |
|--------|--------|----------|-------|------|
| 200 | none | none | none | Own Department and its Projects, including an empty list. |
| 401 | MSG07 | `{}` | none | Anonymous caller. |
| 403 | MSG08 | `{}` | none | Not a Project Manager, inactive caller or missing/inactive own Department. |
| 500 | MSG06 | `{}` | none | No bound Workspace or unexpected read failure. |

#### JSON examples

Connection-test request and new-Project response:

```json
{ "jiraProjectKey": " ops " }
```

```json
{
  "jiraProjectKey": "OPS",
  "statuses": [
    { "id": "10000", "name": "To Do", "done": false },
    { "id": "10002", "name": "Done", "done": true }
  ],
  "reconnect": false,
  "doneStatusIds": ["10002"],
  "reviewRange": 3
}
```

Reconnect test response, preserving an available selected non-Done-category status:

```json
{
  "jiraProjectKey": "OLD",
  "statuses": [
    { "id": "10001", "name": "In Review", "done": false },
    { "id": "10002", "name": "Done", "done": true }
  ],
  "reconnect": true,
  "projectId": "8d1b5d72-6c12-4b95-83e4-c9a6b1181001",
  "doneStatusIds": ["10001"],
  "reviewRange": 5
}
```

Save request and 201 response (200 has the same schema and retains the Removed Project's id):

```json
{ "jiraProjectKey": "OPS", "doneStatusIds": ["10002"], "reviewRange": 3 }
```

```json
{
  "projectId": "8d1b5d72-6c12-4b95-83e4-c9a6b1181002",
  "departmentId": "3f2a7c1e-52d1-4b7e-9d1f-0a4ab3c1d201",
  "jiraProjectKey": "OPS",
  "doneStatusIds": ["10002"],
  "reviewRange": 3,
  "status": "ACTIVE"
}
```

List response:

```json
{
  "department": {
    "departmentId": "3f2a7c1e-52d1-4b7e-9d1f-0a4ab3c1d201",
    "name": "IT Operations"
  },
  "projects": [
    {
      "projectId": "8d1b5d72-6c12-4b95-83e4-c9a6b1181001",
      "jiraProjectKey": "OLD",
      "projectManager": null,
      "memberCount": 2,
      "status": "REMOVED"
    },
    {
      "projectId": "8d1b5d72-6c12-4b95-83e4-c9a6b1181002",
      "jiraProjectKey": "OPS",
      "projectManager": {
        "userId": "16257b22-0d64-46ef-9cd3-1ee109989a01",
        "fullName": "Alex Nguyen"
      },
      "memberCount": 1,
      "status": "ACTIVE"
    }
  ]
}
```

Conflict response (same envelope for both POST operations):

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "code": "MSG49",
  "params": { "tracker_name": "Jira", "department_name": "IT Operations" },
  "fieldErrors": [
    {
      "field": "jiraProjectKey",
      "code": "MSG49",
      "params": { "tracker_name": "Jira", "department_name": "IT Operations" }
    }
  ]
}
```

### Persistence contract

Changeset `029-project-done-statuses.sql`, author `project`, id `029-project-done-statuses`, is included last in `db.changelog-master.xml` (D19). It changes `done_status` from VARCHAR to JSONB with the preservation rules in AC-002.19; no applied changeset is edited. No new review-range CHECK is permitted (D20).

`ProjectLinkRepository` uses `NamedParameterJdbcTemplate` (D22), deliberately differing from the JPA repository default in `backend/AGENTS.md` section 3. The existing `Project` entity remains unchanged. Insert uses `ON CONFLICT (jira_project_key) DO NOTHING RETURNING`; no returned row means 409 MSG49, not a swallowed failed transaction. Reconnect uses a conditional `UPDATE ... WHERE status = 'REMOVED'` on the identified row, after eligibility checks. No updated row means 409 MSG49. A losing insert never turns into an implicit reconnect. Membership replacement deletes the old Project Manager row before `ON CONFLICT (user_id, project_id) DO UPDATE` upserts the caller; it respects `uq_membership_one_manager`. Conditional writes and audit share one transaction. The test never calls these writes.

### Required from other modules

| Owner | Required interface | Use |
|-------|--------------------|-----|
| tracker | `tracker.service.TrackerProjectService.findProject(String projectKey)` returning `tracker.dto.TrackerProjectLookup(outcome, key, statuses)` | New tracker STORY-013, D28. `FOUND`, `NOT_FOUND`, `DENIED`, `UNAVAILABLE`; no client or exception leaks into Project. |
| identity | `identity.signin.port.TenantScope.currentWorkspaceId()` and authenticated `TSolveUser` | Workspace binding and caller id. Own Department is read through Project's repository using existing tables (D21), not another module's repository. |
| workspace | Existing `department` data and `DepartmentRef` API shape | Read-only Department in the list; no new workspace operation or dependency on the unmerged KAN-120 branch. |
| common | `AuditService.record(action, targetType, targetId, details)` | Joins the write transaction and obtains the signed-in actor; audit ids are public UUIDs. |
| common | `Roles.PROJECT_MANAGER`, `GlobalExceptionHandler`, coded exceptions and `ProblemDetail` | Shared method security and the error envelope; no module exception handler. |
| fe-foundation | Existing role guard, `FormModal`, `DataTable`, status display kit, MSG catalog and shared API/error helpers | Screen 3.1, popup, loading/error/empty states; no new dependency. |
| worker | Existing Workspace execution boundary | Retained module dependency; UC-16 schedules no job and makes no worker call. |

Tracker already calls `ProjectService.findActivePublicIdByJiraProjectKey` for issue-created events. This existing read remains compatible; the new tracker lookup only calls Jira and never calls Project, so the new lookup path does not recurse through that existing flow.

## Definition of Done

- [ ] Every acceptance criterion has a behavioural test naming its AC id: backend suffix such as `_AC_001_7`, frontend prefix such as `AC-005.3`.
- [ ] `bun harnessclaude/hooks/sdd.ts check project-integration` and `check tracker` pass; specs are approved separately before code is written.
- [ ] `sdd.ts trace project-integration` and tracker STORY-013 trace pass using each module's code and test paths.
- [ ] Backend `mvn verify` passes, including tests and `mvn spotless:check`; PostgreSQL tests prove rollback, concurrency and Workspace isolation using the existing shared-container test setup.
- [ ] Frontend `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test` and `npm run build` pass in `frontend/`.
- [ ] `npm run generate:api` leaves no diff in `src/shared/api/generated` after generation from the updated YAML contract.
- [ ] Changeset 029 applies on an empty database (every IT starts on the full changelog) and its statements convert a pre-029 `project` shape holding NULL and non-null legacy Done status values with every other column unchanged; existing zero/negative review fallback fixtures still work.
- [ ] MSW supports list, test, create and reconnect consistently, including a Removed fixture of the caller's Department; page tests verify refresh and error retention.
- [ ] The separate D29 MSG48 catalog quick change is complete before the screen task; the interim limits and GBR-04 follow-up remain visible in the implementation handoff.
- [ ] Each feature stops after verification for user agreement on its commit, with one feature per code commit plus its harness commit; no push, merge or pull request without an explicit request.
