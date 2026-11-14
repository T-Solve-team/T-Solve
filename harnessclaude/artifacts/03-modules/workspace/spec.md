---
module: workspace
date: 2026-11-11
owner: BA
---

# Spec: workspace

Covers UC-12 Create Department (Jira KAN-120) and the rename part of UC-13 Update Department (Jira KAN-121). Source: SRS v3460, folder `Sua_31-10` (UC-12, UC-13; screens 2.2 and 2.3; GBR-17, GBR-29, GBR-37; MSG01, MSG02, MSG04, MSG05, MSG06, MSG07, MSG08, MSG43). Decisions: `artifacts/04-plans/workspace/iteration-1/kan-120-decisions.md` and `kan-121-decisions.md`.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-11 | STORY-001, STORY-002 | Initial spec: UC-12 Create Department | KAN-120 |
| 2026-11-11 | STORY-001, STORY-002 | Builder check: blank wins over length (AC-001.6, AC-002.7); Cancel is ignored during a save (AC-002.12, new AC-002.14); AC-002.9 names the shared query; whitespace and character defined; interim limits 4 and 5 added | Codex builder check |
| 2026-11-14 | STORY-005 to STORY-008, STORY-002 | Add UC-14 Delete and UC-15 Restore (SRS v3460 numbers them UC-12 and UC-13): delete and restore API with Projects and Space (AC-005.1 to AC-005.17, AC-007.1 to AC-007.12), the Delete button and popup on 2.3 (AC-006.1 to AC-006.8), "Show deleted" and Restore on 2.2 (AC-008.1 to AC-008.9); `getDepartment` gains `projectCount` (AC-005.1); the 2.2 table reads the new `listDepartments` (AC-002.1, AC-002.4 and AC-002.9 reworded); UC-14 and UC-15 leave Out of Scope; interim limits 4 and 6 removed, 7 added | KAN-122, KAN-123, decisions K122-D1 to D13 |
| 2026-11-12 | STORY-003, STORY-004 | Add UC-13 rename: read and rename API with the Space (AC-003.1 to AC-003.22) and screen 2.3 Department Detail with the 2.2 link (AC-004.1 to AC-004.14); UC-13 leaves Out of Scope except the default Project; interim limit 6 added; contract gains `getDepartment` and `updateDepartment` | KAN-121, decisions D4 and D6 to D20 |

## Requirements

### Functional
1. An Admin creates a Department by giving its name. The Department starts Active.
2. The Department name is unique in the Workspace, ignoring letter case and including deleted Departments.
3. Creating a Department also creates its Department Space. Both exist or neither does (GBR-29).
4. Each creation is recorded in the audit log.
5. Screen 2.2 Workspace Settings has the "New Department" button, its popup, and a Departments table with the Name column.
6. A new Department is offered when the Admin adds a Project Manager (UC-05).
7. An Admin renames an Active Department from screen 2.3 Department Detail. Its Department Space gets the same name in the same transaction (GBR-29). The new name follows the rules of requirement 2.
8. A rename changes only the names. The Department's status, default Project, Projects and tickets stay as they were (UC-13 step 5).
9. Each rename that changes the name is recorded in the audit log with the name before and after.
10. An Admin deletes an Active Department from 2.3 after confirming MSG45. The Department becomes Deleted, its Active Projects become Removed, its default Project is cleared and its Space becomes Hidden, in one transaction (GBR-17, GBR-40). No row is erased and users keep their Department. The name stays reserved (requirement 2).
11. While a Department is Deleted, the readers that already show only Active Departments, Projects and Spaces hide it, its Projects, tickets and Space: Department lists, Search, the Knowledge Chatbot, AI Solution Suggestion, the Dashboard and review expiry (they filter on `department.status = 'ACTIVE'` today and are owned by their modules).
12. An Admin restores a Deleted Department from 2.2 with "Show deleted" on, after confirming MSG51. The Department and its Space become Active again; its Projects stay Removed until a Project Manager re-adds the Jira project (UC-14 of the SRS, not built).
13. Each delete and restore is recorded in the audit log.

### Non-Functional
1. The name rules hold in the database: two requests sent at the same moment cannot create two Departments with one name.
2. The Department, its Space and the audit entry are written in one database transaction, with no outbound call inside it. The same holds for a rename.
3. A Workspace's Departments live only in that Workspace's database (GBR-37, ADR-0002). The Workspace of a request never comes from request content.

### Interim limits
These are known limits of the code this story builds on. They are not defects of this story.

1. The endpoint needs a Workspace bound to the request. Until KAN-275 (identity Tasks 26a and 26b) lands, only the `identity-mock` profile binds one, as for the User List. A request with no bound Workspace is answered with MSG06 (AC-001.15).
2. A real Keycloak sign-in cannot reach this screen until KAN-275 lands.
3. The web app does not send the CSRF header yet, so a browser POST to the real backend gets 403. That is a separate quick change to `frontend/src/shared/api/http.ts` for every write (decision D17). This story is verified with MSW and backend tests.
4. (Removed 2026-11-14: the 2.2 table now reads the `workspace` mock.) In browser mock mode the Add User form still lists the `users` mock's static Departments.
5. The shipped app has one datasource until the tenant layer (KAN-98, KAN-99) lands. AC-001.14 and AC-003.20 are proven with the test router in `TwoTenantDatabases`.
6. (Removed 2026-11-14, as item 4.) The `workspace` mock seeds copies of the `users` mock's two Departments (decision D18).
7. No Project access token is stored yet (no credential table exists; Add Project, KAN-127, is not built), so "tokens are deleted" of GBR-17 has nothing to delete. Setting the Projects to Removed is what stops the Solution Form and Jira import, through the readers that check `project.status`.

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
  - AC-002.1: WHEN an Admin opens 2.2 Workspace Settings, THE web app SHALL show a "New Department" button and a table labelled "Departments" with the column Name, listing the Active Departments from `GET /api/v1/departments`.
  - AC-002.2: WHILE the Departments are loading, THE web app SHALL show the loading state in place of the table.
  - AC-002.3: IF the Departments cannot be loaded, THEN THE web app SHALL show the error state with a retry action in place of the table and keep the "New Department" button.
  - AC-002.4: WHERE the table has no Department to list, THE web app SHALL show MSG04 in the table.
  - AC-002.5: WHEN the Admin clicks "New Department", THE web app SHALL open a popup titled "New Department" with an empty required field "Department name" and the buttons Save and Cancel.
  - AC-002.6: IF the field is empty or only whitespace when the Admin clicks Save, THEN THE web app SHALL show "The Department name field is required." (MSG01) below the field, send no request and keep the popup open.
  - AC-002.7: IF the field is not only whitespace and holds more than 50 characters when the Admin clicks Save, THEN THE web app SHALL show "Department name must not exceed 50 characters." (MSG02) below the field, send no request, keep the popup open and keep every character typed.
  - AC-002.8: WHEN the Admin clicks Save with an allowed name, THE web app SHALL send the name with leading and trailing whitespace removed and SHALL send one request however many times Save is clicked while it runs.
  - AC-002.9: WHEN the server answers 201, THE web app SHALL close the popup, show "Changes saved successfully." (MSG05) and refresh every cached query, so the new Department appears in the table and the Add User form.
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
  - AC-003.6: WHEN a Department is renamed, THE system SHALL keep its status, its default Project and its Projects unchanged.
  - AC-003.7: WHEN a Department is renamed, THE system SHALL return the new name for it in `GET /api/v1/users/departments` and `GET /api/v1/departments/{departmentId}`.
  - AC-003.8: IF the name is missing, empty or only whitespace, THEN THE system SHALL answer 400 with code MSG01 on field `name` and write no Department, Space or audit entry.
  - AC-003.9: IF the name is not blank and as received is longer than 50 characters, THEN THE system SHALL answer 400 with code MSG02 and `max_length` 50 on field `name` and write no Department, Space or audit entry.
  - AC-003.10: IF the name after whitespace removal equals the name of another Active Department, THEN THE system SHALL answer 409 with code MSG43 on field `name` and write no Department, Space or audit entry.
  - AC-003.11: IF the name after whitespace removal equals the name of a Deleted Department, THEN THE system SHALL answer 409 with code MSG43 on field `name` and write no Department, Space or audit entry.
  - AC-003.12: IF the name after whitespace removal differs from another Department's name only in letter case, THEN THE system SHALL answer 409 with code MSG43 on field `name` and write no Department, Space or audit entry.
  - AC-003.13: WHEN the name after whitespace removal differs from the Department's own current name only in letter case, THE system SHALL rename it as in AC-003.3 to AC-003.5.
  - AC-003.14: IF the name after whitespace removal equals the Department's current name exactly, THEN THE system SHALL answer 200 with the Department and write no Department, Space or audit change.
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

### STORY-005: Delete a Department with its Projects and Space
- As an Admin, I want to delete a Department that is no longer used so that its Projects, tickets and Space are hidden without losing any data (UC-14, GBR-17).
- Acceptance:
  - AC-005.1: WHEN an Admin sends `GET /api/v1/departments/{departmentId}` for an Active Department, THE system SHALL also answer `projectCount`, the number of its Projects in any status.
  - AC-005.2: WHEN an Admin sends `DELETE /api/v1/departments/{departmentId}` for an Active Department, THE system SHALL set the Department's status to `DELETED`, keep its name, and answer 204.
  - AC-005.3: WHEN a Department is deleted, THE system SHALL set each of its `ACTIVE` Projects to `REMOVED` in the same transaction and leave its other Projects unchanged.
  - AC-005.4: WHEN a Department is deleted, THE system SHALL clear its default Project in the same transaction.
  - AC-005.5: WHEN a Department is deleted, THE system SHALL set its Space's status to `HIDDEN` in the same transaction and keep the Space's name and Knowledge Manager.
  - AC-005.6: WHEN a Department is deleted, THE system SHALL write one audit entry in the same transaction with the signed-in Admin's id and email as actor, action `DEPARTMENT_CHANGE`, target type `DEPARTMENT`, target id the `departmentId`, and details holding `operation` = `DELETE`, `name`, `spaceId` and `removedProjectIds`, the ids of the Projects set to `REMOVED`.
  - AC-005.7: WHEN a Department is deleted, THE system SHALL erase no Department, Project, ticket, Space or user row and SHALL keep the Department of every user.
  - AC-005.8: WHILE a Department is Deleted, THE system SHALL leave it out of `GET /api/v1/users/departments` and of `GET /api/v1/departments` without `includeDeleted`, and leave its Space out of the Space options of Add User.
  - AC-005.9: WHILE a Department is Deleted, THE system SHALL answer 409 with code MSG43 to a create or rename with its name.
  - AC-005.10: IF the `departmentId` of a delete request names no Department, names a Deleted Department, or is not a UUID, THEN THE system SHALL answer 404 with code MSG04 and write no Department, Project, Space or audit change.
  - AC-005.11: IF two requests delete the same Department at the same moment, THEN THE system SHALL apply one, answer it with 204, answer the other with 404 MSG04, and write one audit entry.
  - AC-005.12: IF the Department has no Space, or writing a Project, the Space or the audit entry fails, THEN THE system SHALL keep the Department, its Projects and its Space as they were, write no audit entry, and answer 500 with code MSG06.
  - AC-005.13: IF a signed-in user without the Admin role sends a delete request, THEN THE system SHALL answer 403 with code MSG08 and change nothing.
  - AC-005.14: IF a delete request has no signed-in user, THEN THE system SHALL answer 401 with code MSG07 and change nothing.
  - AC-005.15: IF no Workspace is bound to a delete request, THEN THE system SHALL answer 500 with code MSG06 and change nothing.
  - AC-005.16: WHEN an Admin sends `GET /api/v1/departments`, THE system SHALL answer 200 with the Active Departments as `{departmentId, name, status}` sorted by name ignoring letter case, and with `includeDeleted=true` SHALL also list the Deleted Departments with status `DELETED`.
  - AC-005.17: IF a signed-in user without the Admin role sends `GET /api/v1/departments`, THEN THE system SHALL answer 403 with code MSG08, and IF it has no signed-in user, THEN 401 with code MSG07.

### STORY-006: Delete on 2.3 Department Detail
- As an Admin, I want a Delete button on the Department Detail so that I can delete a Department after confirming.
- Acceptance:
  - AC-006.1: WHEN an Admin opens 2.3 Department Detail of an Active Department, THE web app SHALL show a "Delete" button next to "Edit".
  - AC-006.2: WHEN the Admin clicks "Delete", THE web app SHALL open the Delete Department popup with MSG45 filled with the Department name and its `projectCount`, and the buttons Delete and Cancel.
  - AC-006.3: WHEN the Admin clicks Cancel in the popup while no delete is running, THE web app SHALL close it and send no request.
  - AC-006.4: WHEN the Admin clicks Delete in the popup, THE web app SHALL send `DELETE /api/v1/departments/{departmentId}` once however many times it is clicked while it runs.
  - AC-006.5: WHEN the server answers 204, THE web app SHALL show "Department deleted." (MSG46), refresh every cached query and open 2.2 Workspace Settings.
  - AC-006.6: IF the server answers an error, THEN THE web app SHALL close the popup, stay on 2.3 and show the message of the returned code.
  - AC-006.7: WHILE the rename form is open, THE web app SHALL hide the "Delete" button.
  - AC-006.8: WHILE a delete is running, THE web app SHALL ignore Cancel.

### STORY-007: Restore a Deleted Department
- As an Admin, I want to restore a deleted Department so that the team can use T-Solve again with its previous data (UC-15).
- Acceptance:
  - AC-007.1: WHEN an Admin sends `POST /api/v1/departments/{departmentId}/restore` for a Deleted Department, THE system SHALL set its status to `ACTIVE` and answer 200 with its `departmentId` and `name`.
  - AC-007.2: WHEN a Department is restored, THE system SHALL set its Space's status to `ACTIVE` in the same transaction.
  - AC-007.3: WHEN a Department is restored, THE system SHALL leave its Projects in their current status and leave its default Project empty.
  - AC-007.4: WHEN a Department is restored, THE system SHALL write one audit entry in the same transaction with the signed-in Admin's id and email as actor, action `DEPARTMENT_CHANGE`, target type `DEPARTMENT`, target id the `departmentId`, and details holding `operation` = `RESTORE`, `name` and `spaceId`.
  - AC-007.5: WHEN a Department is restored, THE system SHALL list it again in `GET /api/v1/users/departments` and `GET /api/v1/departments`, answer it in `GET /api/v1/departments/{departmentId}`, and list its Space again in the Space options of Add User.
  - AC-007.6: IF the `departmentId` of a restore request names no Department, names an Active Department, or is not a UUID, THEN THE system SHALL answer 404 with code MSG04 and change nothing.
  - AC-007.7: IF two requests restore the same Department at the same moment, THEN THE system SHALL apply one, answer it with 200, answer the other with 404 MSG04, and write one audit entry.
  - AC-007.8: IF the Department has no Space, or writing the Space or the audit entry fails, THEN THE system SHALL keep the Department Deleted and its Space Hidden, write no audit entry, and answer 500 with code MSG06.
  - AC-007.9: IF a signed-in user without the Admin role sends a restore request, THEN THE system SHALL answer 403 with code MSG08 and change nothing.
  - AC-007.10: IF a restore request has no signed-in user, THEN THE system SHALL answer 401 with code MSG07 and change nothing.
  - AC-007.11: IF no Workspace is bound to a restore request, THEN THE system SHALL answer 500 with code MSG06 and change nothing.
  - AC-007.12: WHEN a Department is deleted and then restored, THE system SHALL keep its earlier audit entries unchanged.

### STORY-008: Show deleted and Restore on 2.2 Workspace Settings
- As an Admin, I want to see deleted Departments on Workspace Settings and restore one so that I can bring a team back.
- Acceptance:
  - AC-008.1: WHEN an Admin opens 2.2 Workspace Settings, THE web app SHALL show a switch "Show deleted", turned off.
  - AC-008.2: WHEN the Admin turns "Show deleted" on, THE web app SHALL list the Active and Deleted Departments from `GET /api/v1/departments?includeDeleted=true`, and WHEN it is turned off, only the Active ones.
  - AC-008.3: WHILE "Show deleted" is on, THE web app SHALL show a "Deleted" tag and a "Restore" button on each Deleted row, show its name as plain text, and show no Restore button on Active rows.
  - AC-008.4: WHEN the Admin clicks "Restore", THE web app SHALL open a popup with MSG51 filled with the Department name, and the buttons Restore and Cancel.
  - AC-008.5: WHEN the Admin clicks Cancel in the popup while no restore is running, THE web app SHALL close it and send no request.
  - AC-008.6: WHEN the Admin clicks Restore in the popup, THE web app SHALL send `POST /api/v1/departments/{departmentId}/restore` once however many times it is clicked while it runs.
  - AC-008.7: WHEN the server answers 200, THE web app SHALL close the popup, show "Department restored." (MSG52) and refresh every cached query, so the row shows as Active.
  - AC-008.8: IF the server answers an error, THEN THE web app SHALL close the popup and show the message of the returned code.
  - AC-008.9: WHILE a restore is running, THE web app SHALL ignore Cancel.

## Out of Scope
- UC-10 and UC-11: company information. It comes through Change Mode when its Jira story starts (KAN-119).
- UC-66 Migrate Department data, its "Migrate data" button on 2.2 and screen 2.5 (no Jira story yet).
- Reconnecting a Removed Project after a restore (Add Project, KAN-127) and MSG54.
- Changing the readers of other modules: they already hide a Deleted Department (requirement 11).
- The default Project of UC-13 (steps 2 and 3, screen 2.3 "Default Project", GBR-40, MSG156, MSG157), which KAN-121's Jira criteria ask for. No Project can exist until Add Project (UC-16, KAN-127) is built, and the Jira checks and the stored service account (KAN-119) do not exist. It comes through Change Mode once KAN-127 is done; KAN-121 stays open in Jira until then (decision D4). Screen 2.3 has no Default Project field until then.
- The Managers tab of screen 2.3 (no story owns it).
- The Departments table columns Project Managers, Projects, Members and Tickets. No story owns them yet.
- A Knowledge Manager, permission row, folder or document for the new Space. The Admin assigns a Knowledge Manager in UC-05, UC-07 and UC-09; GBR-30's own-Department View is derived, not stored.
- The CSRF header in the web app (Interim limits, item 3).
- Per-request Workspace binding and real sign-in (KAN-275, Interim limits, items 1 and 2).
- The UUID key alignment of `department` and `space` (KAN-248).
- A live browser journey against the real backend. It needs the two items above.

## Interface Contracts

### Exposed by this module

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

`GET /api/v1/departments/{departmentId}`, operationId `getDepartment`, tag `departments`, role Admin. Response 200, schema `DepartmentRef`, for an Active Department. Errors 401 MSG07, 403 MSG08, 404 MSG04 (unknown, Deleted, or a path id that is not a UUID), 500 MSG06.

`PUT /api/v1/departments/{departmentId}`, operationId `updateDepartment`, tag `departments`, role Admin. Request `application/json`, schema `UpdateDepartmentRequest`, with the same `name` field and rules as `CreateDepartmentRequest`:

```json
{ "name": "IT Service Desk" }
```

Response 200, schema `DepartmentRef`, with the stored name. Errors as for `createDepartment` (400 MSG01, 400 MSG02, 401 MSG07, 403 MSG08, 409 MSG43, 500 MSG06), plus 404 MSG04 with `params` `{}` and no `fieldErrors` for an unknown or Deleted Department or a path id that is not a UUID. Both paths are added to `frontend/openapi/tsolve-api.yaml` before the code; the tag description becomes "Create, read and rename a Department and its Space (SRS UC-12, UC-13)".

MSG04 "No results found."

`DepartmentService` gains `get(UUID departmentId)` and `update(UUID departmentId, UpdateDepartmentRequest request)`, both returning `DepartmentRefResponse`.

KAN-122 and KAN-123 (2026-11-14):

- `getDepartment` answers schema `DepartmentDetail` = `DepartmentRef` plus `projectCount` (integer, 0 or more) (AC-005.1). `DepartmentService.get` returns `DepartmentDetailResponse`.
- `GET /api/v1/departments`, operationId `listDepartments`, role Admin. Query `includeDeleted` (boolean, default false). Response 200, array of `DepartmentSummary` `{departmentId, name, status}` with `status` enum `ACTIVE`, `DELETED`, sorted by `lower(name)`. Errors 401 MSG07, 403 MSG08, 500 MSG06.
- `DELETE /api/v1/departments/{departmentId}`, operationId `deleteDepartment`, role Admin. Response 204, no body. Errors 401 MSG07, 403 MSG08, 404 MSG04 (unknown, already Deleted, or not a UUID), 500 MSG06.
- `POST /api/v1/departments/{departmentId}/restore`, operationId `restoreDepartment`, role Admin, no body. Response 200 `DepartmentRef`. Errors 401 MSG07, 403 MSG08, 404 MSG04 (unknown, Active, or not a UUID), 500 MSG06.
- Tag description becomes "Create, read, rename, delete and restore a Department and its Space (SRS UC-12 to UC-15)".
- No changeset: `department.status` has no check constraint and takes `DELETED`; `project.status` takes `REMOVED` (`ProjectStatus`); `space.status` already allows `HIDDEN`.
- Messages: MSG45 confirm `{department_name}`, `{count}`; MSG46 toast "Department deleted."; MSG51 confirm `{department_name}`; MSG52 toast "Department restored." (already in `catalog.ts`).
- `DepartmentService` gains `list(boolean includeDeleted)`, `delete(UUID departmentId)` and `restore(UUID departmentId)`.

### Required from other modules
- identity: `GET /api/v1/users/departments` (Active Departments, sorted by name), `DepartmentRefResponse`, and the interim `TenantScope` port.
- common: `AuditService.record`, `Roles.ADMIN`, `BusinessRuleException`, `GlobalExceptionHandler`.
- fe-foundation: the Admin route guard of 2.2 and 2.3, `FormModal`, `DataTable`, `cells.link`, `LoadingState`, `ErrorState`, form rules and the message catalog.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name cites its AC id (backend suffix `_AC_001_7`, frontend prefix `AC-002.6`)
- [ ] KAN-121: no new changeset; the rename works on an empty database and on one that holds the existing tables
- [ ] `sdd.ts trace workspace` passes
- [ ] Backend: `mvn verify` passes, including the new IT on real PostgreSQL
- [ ] Frontend: `format:check`, `lint`, `typecheck`, `test` and `build` pass, and `generate:api` leaves no diff
- [ ] Both changesets apply on an empty database and on one that holds the existing tables
- [ ] Each task is committed on its own after the user agrees
