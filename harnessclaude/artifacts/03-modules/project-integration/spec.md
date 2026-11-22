---
module: project-integration
date: 2026-11-13
owner: BA
depth: Deep
---

# Spec: project-integration

Covers UC-15 Add Ticket Resolver to Project (Jira KAN-128; numbered UC-17 in the Jira title and the module map before the SRS renumbering), STORY-001..STORY-002, and UC-16 Remove a member from a Project (Jira KAN-129, titled UC-18), STORY-003. Source: SRS v3460 (`_context/Report`): UC-15, UC-16, screen 3.2 Project Detail, GBR-02, GBR-20, MSG04, MSG05, MSG06, MSG08, MSG55. The other stories of this module (KAN-125, KAN-126, KAN-127 Add Project) are added later in Change Mode. Decision (user, 13/11): plan and build KAN-128 alone, before KAN-127.

Covers UC-16 Add a Project only (KAN-127), including reconnecting a Removed Project and the add flow on screen 3.1 Jira Integration. Sources: SRS v3460 in `Sua_31-10`, UC-16, screens 3.1 and 3.2 (the initial settings), GBR-04, GBR-10, GBR-14, GBR-20 and GBR-21; `Report_3_BreakDown/System_Messages.docx.md`; the four Jira acceptance criteria in the KAN-127 brief. Verified context: `artifacts/04-plans/project-integration/iteration-1/kan-127-scout.md`. Decisions: `artifacts/04-plans/project-integration/iteration-1/kan-127-decisions.md`, D19-D37; later decisions supersede the scout where they differ. Deep depth is required by the schema change and shared API contract. This run writes the specs and module definitions only; design, tasks, approval and status changes follow separately.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-13 | STORY-001, STORY-002 | Initial spec: UC-15 Add Ticket Resolver to Project | KAN-128; scope "KAN-128 only" chosen by the user on 13/11 |
| 2026-11-13 | STORY-001, STORY-002 | Builder check (before approval): no Project Manager case and MSG54 removed (only the Project Manager opens 3.2), AC-001.7/001.8 renumbered 001.6/001.7; 404 only for Project Manager callers; null full names last; no Department filter on candidates; `q` trimmed; DB time; ids in order sent; popup loads at once; AC-002.16 audit label | Builder check findings 1, 2, 4, 7, 13–17 |
| 2026-11-14 | STORY-001, STORY-003 | Added STORY-003 Remove a member (UC-16, KAN-129): `DELETE …/members/{userId}`, MSG55 confirm, audit `{"removed": [...]}`, tickets kept; AC-001.7 reworded (no Remove button on the Project Manager's row, instead of none at all) | KAN-129; scope "remove the membership only" chosen by the user on 14/11 — the Solution Form's membership check belongs to `jira-app` (KAN-126) |
| 2026-11-12 | STORY-004..010 | Initial spec: UC-16 Add a Project (KAN-127) | KAN-127 |
| 2026-11-12 | STORY-004, 005 | Builder check: AC-004.21 and AC-004.22 for a requested key that differs from Jira's canonical key; AC-005.21 orders the local settings checks before the link check and Jira; the Definition of Done names how changeset 036 is proven (D62) | Builder check |
| 2026-11-12 | STORY-004, 005, 010 | Review of the Codex draft: AC-010.8 no longer puts access before Bean Validation (`@Valid` runs before `@PreAuthorize`); AC-010.3 names a valid body; AC-004.20 and AC-005.16 drop the non-string checks Jackson's scalar coercion does not make (D51, D54 revised) | Framework order and Jackson defaults; the criteria must be testable as written |
| 2026-11-15 | STORY-004..010 | Merged `origin/main` into the KAN-127 branch: this module's KAN-127 stories STORY-001..007 are renumbered STORY-004..010 (AC-00n.m becomes AC-0(n+3).m) after the merged STORY-001..003 of KAN-128/KAN-129; changeset 029 renamed 036 (035 is reserved for PR #215). No criterion changes meaning | Two unmerged specs used the same ids; the unmerged PR renumbers after main |
| 2026-11-15 | STORY-004..010 | KAN-127 design decisions D1..D57 (design.md, kan-127-decisions.md) renumbered D19..D75 (n + 18) after the D1..D18 of KAN-128/KAN-129; references in the KAN-127 spec, design, tasks, session logs, reviews, tracker docs and code comments follow. No criterion changes | Two unmerged designs used the same decision ids; the unmerged PR renumbers after main |
| 2026-11-17 | STORY-008 | AC-008.2 reworded: the Projects table drops the "Project ID" column and starts with a "No." column (the row's position, from 1). New AC-008.11: the Jira project key of a Project whose Project Manager is the signed-in user is a link to 3.2 Project Detail; on other rows it is plain text, because 3.2 answers 403 to anyone else (AC-001.3) | KAN-360: the Project UUID is noise in the table, users open a Project from its key (user, 17/11) |
| 2026-11-18 | STORY-011 (new), STORY-002 | New STORY-011 (KAN-367, AC-011.1 to AC-011.11): the Add member popup gets a "From Jira" tab that lists the people of a chosen role of the Jira project, read with the T-Solve Jira App's token; addable people (linked, Active Ticket Resolvers, not members) can be ticked, the others are greyed with a reason, group actors are named but not expanded. AC-002.10 now describes the "Search" tab. Out of Scope adds removing members who left the Jira role and expanding groups | RESEARCH-007; user decisions of 18/11: role chosen in the popup (not stored), greyed rows with a reason, two tabs, never remove automatically, groups not expanded |

## Requirements

### Functional — KAN-128/129 (STORY-001..003)
1. Only the Project Manager of a Project opens its Project Detail (screen 3.2) and manages its members (GBR-20).
2. Project Detail shows a read-only header and the Members tab: Full name, Email, Role, Added on of every member. The Project Manager is always a member.
3. The Project Manager adds one or more Active Ticket Resolver accounts that are not yet members, found by name or email. They become members with role `MEMBER` in `project_membership`.
4. A new member sees the tickets of the Project's Department. The existing scope check (`ScopeAccessRepository`, KAN-118) already derives this from `project_membership`; this module adds no visibility code.
5. Each add is recorded in the audit log.

### Non-Functional — KAN-128/129 (STORY-001..003)
1. The membership rows and the audit entry are written in one database transaction; an add is all-or-nothing.
2. A Project's members live only in the Workspace's tenant database. The Workspace and the caller never come from request content.
3. Two adds sent at the same moment for the same person cannot create two rows (primary key `(user_id, project_id)`).

### Interim limits — KAN-128/129 (STORY-001..003)
These are known limits of the code this story builds on. They are not defects of this story.

1. There is no Projects table on Jira Integration (3.1) until KAN-127. Project Detail is reached by its path `/jira-integration/projects/{projectId}`; Projects come from seed data or test fixtures.
2. The web app does not send the CSRF header yet, so a browser POST to the real backend gets 403 (workspace decision D17). This story is verified with MSW and backend tests.
3. The Settings tab of 3.2 (field mapping, Done statuses, review range, Escalation handler) belongs to KAN-127 and is not shown.

### Functional — KAN-127 (STORY-004..010)
1. A Project Manager tests a Jira project key using the Workspace's service account, without entering a token (GBR-14).
2. A Project belongs to the caller's own Active Department; adding it makes the caller its Project Manager and a member (GBR-20).
3. The initial Done statuses are the Jira project's Done-category statuses; the Project Manager can choose one or more of any of its statuses. The review range is a whole number of calendar days, at least 1, initially 3 (GBR-04, GBR-10).
4. A Jira project key belongs to one Project across all Departments and statuses in the Workspace. Re-adding an eligible Removed Project reconnects that same Project (GBR-14, GBR-21).
5. Every successful add or reconnect records one audit entry; failure records no change.
6. Screen 3.1 shows the caller's Department and its Projects and opens the Add project popup. Admin keeps the existing placeholder (D48).

### Non-Functional — KAN-127 (STORY-004..010)
1. Both Jira reads finish before and outside the write transaction; no outbound call executes with an active database transaction (`backend/AGENTS.md` section 4; AC-005.18).
2. Project, membership and audit changes commit in exactly one transaction, including reconnect; a failed membership or audit write rolls them all back (AC-005.9, AC-005.10, AC-006.9).
3. Logs and responses contain neither the service-account email/token nor a Jira response body, on success and on every error path (tracker Non-Functional 9; AC-004.17).
4. Every Project read and write uses only the bound Workspace's database. No request property, query parameter or body supplies a Workspace or Department to act in (AC-010.6, AC-010.7).
5. Simultaneous Saves cannot create a second row for one Jira key or a second Project Manager membership for one Project (AC-005.11, AC-006.8).

### Interim limits — KAN-127 (STORY-004..010)
These are known limits of the current dependencies, not defects of this story.

1. Until KAN-275, only `identity-mock` binds the Workspace for these requests; real sign-in alone cannot complete this flow. Missing binding returns 500 MSG06.
2. The browser client has no CSRF header (KAN-120 D17); a browser POST to the real backend can return 403 until that separate shared-client quick change. This story uses backend tests and MSW for browser verification.
3. The shipped app has one datasource until the tenant layer lands. Workspace isolation is verified using the existing test router and two tenant databases.
4. `JiraServiceAccount.current()` is the configured environment stand-in, using Basic authentication through `JiraClient`. A real scoped service-account token fails with Basic authentication; gateway Bearer support belongs to KAN-125/KAN-119 (D23, RESEARCH-003).
5. Removed Projects are available through seed data and fixtures only; there is no Remove story yet. Use a Removed Project of the caller's Department for reconnect fixtures, such as `OLD`, rather than the scout's inconsistent cross-Department demo membership.
6. The stale MSG48 catalog text is corrected by a separate quick change with FE Lead review before the screen task (D47). The required text in this spec comes from the SRS.

## User Stories

### STORY-001: View the members of a Project
- As a Project Manager, I want to see who is a member of my Project so that I know who can write Solutions for it.
- Acceptance:
  - AC-001.1: WHEN the Project Manager of a Project requests `GET /api/v1/projects/{projectId}`, THE backend SHALL answer 200 with the Project id, Jira project key, Department name, the Project Manager's full name and the status (`ACTIVE` or `REMOVED`).
  - AC-001.2: WHEN the Project Manager of a Project requests `GET /api/v1/projects/{projectId}/members`, THE backend SHALL answer 200 with every member of the Project — user id, full name, email, role in the Project (`PROJECT_MANAGER` or `MEMBER`) and date added — the Project Manager first, then the other members by full name, ascending, ignoring letter case, members without a full name last.
  - AC-001.3: IF the caller is not the Project Manager of the Project (no `PROJECT_MANAGER` membership row for it, or no `PROJECT_MANAGER` role, or an account that is not Active), THEN THE backend SHALL answer 403 to every endpoint of this spec and return no Project data.
  - AC-001.4: IF a caller with the `PROJECT_MANAGER` role gives a `projectId` no Project has, THEN THE backend SHALL answer 404 to every endpoint of this spec.
  - AC-001.5: WHEN the Project Manager opens Project Detail, THE web app SHALL show the header (Jira project key, Department, Project Manager, status) and the Members tab with the columns Full name, Email, Role, Added on, with "Added on" as a date in the app time zone.
  - AC-001.6: IF the backend answers 403, THEN THE web app SHALL show MSG08 in place of the screen; IF it answers 404, THEN THE web app SHALL show MSG04; IF it fails otherwise, THEN THE web app SHALL show MSG06.
  - AC-001.7: THE web app SHALL show no Remove button on the Project Manager's row of the Members tab.

### STORY-002: Add Ticket Resolvers to a Project
- As a Project Manager, I want to add Ticket Resolvers to my Project so that only the people who handle its issues write Solutions for it.
- Acceptance:
  - AC-002.1: WHEN the Project Manager requests `GET /api/v1/projects/{projectId}/member-candidates?q={text}`, THE backend SHALL answer 200 with at most 20 accounts — user id, full name, email — that are Active, hold the `TICKET_RESOLVER` role, are not members of the Project, and whose full name or email contains `q` (trimmed) literally, ignoring letter case, sorted by full name ascending ignoring letter case, accounts without a full name last. Candidates are not limited to the Project's Department (UC-15 step 6).
  - AC-002.2: WHERE `q` is missing or blank after trimming, THE backend SHALL apply no name or email filter to the candidates of AC-002.1.
  - AC-002.3: IF `q` is longer than 100 characters after trimming, THEN THE backend SHALL answer 400 and return no candidates.
  - AC-002.4: WHEN the Project Manager posts `POST /api/v1/projects/{projectId}/members` with `{"userIds": [...]}` of 1 to 50 distinct ids, each an Active `TICKET_RESOLVER` account that is not a member of the Project, THE backend SHALL add each as a member with role `MEMBER` and date added = the database time of the insert, and answer 201 with the full member list in the form and order of AC-001.2.
  - AC-002.5: WHEN members are added, THE backend SHALL record one audit entry with action `PROJECT_MEMBER_CHANGE`, target type `PROJECT`, target id the Project id, and details `{"added": [<user ids in the order sent>]}`, in the same transaction as the rows.
  - AC-002.6: IF `userIds` is missing, empty, longer than 50, or contains a duplicate or a null, THEN THE backend SHALL answer 400 and add nobody.
  - AC-002.7: IF any id in `userIds` is not an Active account with the `TICKET_RESOLVER` role, or is already a member of the Project, THEN THE backend SHALL answer 409, add nobody and record no audit entry.
  - AC-002.8: IF the Project is `REMOVED`, THEN THE backend SHALL answer 409 to `POST …/members`, add nobody and record no audit entry.
  - AC-002.9: WHILE the Project is `ACTIVE`, THE web app SHALL show the "Add member" button in the Members tab; WHILE it is `REMOVED`, THE web app SHALL not show it.
  - AC-002.10: WHEN the Project Manager clicks "Add member", THE web app SHALL open the Add member popup whose "Search" tab (STORY-011) has a search box (name or email) and the candidate list of AC-002.1, loaded at once with an empty `q` and refreshed 300 ms after the last keystroke, where one or more candidates can be selected.
  - AC-002.11: IF the candidate list is empty, THEN THE web app SHALL show MSG04 in the popup.
  - AC-002.12: WHILE no candidate is selected or an add is in progress, THE web app SHALL disable the Add button of the popup.
  - AC-002.13: WHEN the Project Manager clicks Add and the backend answers 201, THE web app SHALL close the popup, show MSG05 and show the returned member list.
  - AC-002.14: IF the add fails, THEN THE web app SHALL keep the popup open with the selection and show MSG06 (any error, 409 included).
  - AC-002.15: WHEN the Project Manager clicks Cancel or closes the popup, THE web app SHALL send no add request and change nothing.
  - AC-002.16: THE audit log screen SHALL label the action `PROJECT_MEMBER_CHANGE` "Project member change".

### STORY-003: Remove a member from a Project
- As a Project Manager, I want to remove a member from my Project so that people who no longer handle its issues stop writing Solutions for it.
- Acceptance:
  - AC-003.1: WHEN the Project Manager sends `DELETE /api/v1/projects/{projectId}/members/{userId}` for a member with role `MEMBER`, THE backend SHALL delete that membership row and answer 200 with the full member list in the form and order of AC-001.2.
  - AC-003.2: WHEN a member is removed, THE backend SHALL record one audit entry with action `PROJECT_MEMBER_CHANGE`, target type `PROJECT`, target id the Project id, and details `{"removed": [<user id>]}`, in the same transaction as the delete.
  - AC-003.3: IF `userId` has no `MEMBER` row in the Project (not a member, already removed, or the Project's `PROJECT_MANAGER` row), THEN THE backend SHALL answer 409, delete nothing and record no audit entry.
  - AC-003.4: IF the Project is `REMOVED`, THEN THE backend SHALL answer 409 to `DELETE …/members/{userId}`, delete nothing and record no audit entry.
  - AC-003.5: WHEN a member is removed, THE backend SHALL keep every ticket and Solution in the Project unchanged, those the removed person wrote included.
  - AC-003.6: WHILE the Project is `ACTIVE`, THE web app SHALL show a Remove button on every `MEMBER` row of the Members tab; WHILE it is `REMOVED`, THE web app SHALL show no Remove button.
  - AC-003.7: WHEN the Project Manager clicks Remove, THE web app SHALL ask for confirmation with MSG55, where `user_name` is the member's full name (else email) and `project_key` is the Jira project key, and send no request yet.
  - AC-003.8: WHEN the Project Manager confirms and the backend answers 200, THE web app SHALL show MSG05 and show the returned member list.
  - AC-003.9: IF the remove fails, THEN THE web app SHALL show MSG06 and keep the member list unchanged (any error, 409 included).
  - AC-003.10: WHEN the Project Manager cancels the confirmation, THE web app SHALL send no remove request and change nothing.

### STORY-004: Test the connection to a Jira project
- As a Project Manager, I want to check the Jira project and see its settings before adding a Project to my Department.
- Acceptance:
  - AC-004.1: WHEN an authorised caller sends `POST /api/v1/projects/connection-test` with a valid key, THE system SHALL use that key stripped with `String.strip()` and upper-cased with `Locale.ROOT` for the link lookup and `TrackerProjectService.findProject`.
  - AC-004.2: IF `jiraProjectKey` is missing, null, empty or only whitespace, THEN THE system SHALL answer 400 MSG01 on field `jiraProjectKey` with params `{field_name: "jiraProjectKey"}`, without a Jira call.
  - AC-004.3: IF a nonblank `jiraProjectKey` exceeds 20 UTF-16 code units as received, THEN THE system SHALL answer 400 MSG02 on field `jiraProjectKey` with params `{field_name: "jiraProjectKey", max_length: 20}`, without a Jira call.
  - AC-004.4: IF the normalised key fails `[A-Z][A-Z0-9_]*`, THEN THE system SHALL answer 409 MSG48 with params `{tracker_name: "Jira"}` and no field error, without a Jira call.
  - AC-004.5: IF the normalised key belongs to an Active Project in any Department of the bound Workspace, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}`, without a Jira call.
  - AC-004.6: IF the normalised key belongs to a Removed Project of another Department, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}`, without a Jira call.
  - AC-004.7: IF the normalised key belongs to a Removed Project of the caller's Department and its previous Project Manager is a different user who is still Active, holds the Project Manager role and belongs to that Department, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}`, without a Jira call.
  - AC-004.8: WHEN the local link check allows the key, THE system SHALL obtain the Browse result and project statuses only through `tracker.service.TrackerProjectService.findProject(key)` as defined by tracker STORY-013.
  - AC-004.9: WHEN the tracker returns `FOUND` for an allowed key, THE system SHALL answer 200 `ProjectConnectionTest` with Jira's canonical `jiraProjectKey` and every returned project status once as `{id, name, done}`.
  - AC-004.10: WHEN the tested Jira project has no existing Project, THE system SHALL return `reconnect: false` and omit `projectId`.
  - AC-004.11: WHEN the tested key belongs to an eligible Removed Project of the caller's Department, THE system SHALL return `reconnect: true` and that Project's existing `projectId`.
  - AC-004.12: WHEN the tested Jira project is new, THE system SHALL prefill `doneStatusIds` with the returned statuses whose `done` is true and `reviewRange` with 3.
  - AC-004.13: WHEN the tested Jira project reconnects a Removed Project, THE system SHALL prefill `doneStatusIds` with its stored ids still in the returned statuses, falling back to the Done-category ids if no stored id remains.
  - AC-004.14: WHEN the tested Jira project reconnects a Removed Project, THE system SHALL prefill `reviewRange` with its stored value or 3 if that value is SQL NULL.
  - AC-004.15: IF the tracker returns `NOT_FOUND` or `DENIED`, THEN THE system SHALL answer 409 MSG48 with params `{tracker_name: "Jira"}` and no field error.
  - AC-004.16: IF the tracker returns `UNAVAILABLE`, THEN THE system SHALL answer 503 MSG06 with params `{}` and no field error.
  - AC-004.17: THE system SHALL exclude the service-account email, token and Jira response bodies from all Project responses and logs.
  - AC-004.18: THE connection test SHALL write no Project, membership, audit entry or other persisted data, on success or failure.
  - AC-004.19: IF Jira's canonical key differs from the requested normalised key and belongs to a Project refused by AC-004.5, AC-004.6 or AC-004.7, THEN THE system SHALL answer the corresponding 409 MSG49 instead of returning a successful test.
  - AC-004.20: IF the JSON body is unreadable, THEN THE system SHALL answer 400 MSG06 with params `{}` and no field error, without a Jira call.
  - AC-004.21: IF the requested normalised key and Jira's canonical key are held by two different Projects, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <Department of the Project holding the canonical key>}` and write nothing.
  - AC-004.22: WHEN exactly one of the requested normalised key and Jira's canonical key is held by a Project and that Project is an eligible Removed Project of the caller's Department, THE system SHALL treat it as the reconnect (`reconnect: true` with its `projectId`), and a Save SHALL store Jira's canonical key on that Project.

### STORY-005: Add the Project
- As a Project Manager, I want to save the Project and its initial settings so that my Department can use its Jira project.
- Acceptance:
  - AC-005.1: WHEN an authorised caller sends `POST /api/v1/projects`, THE system SHALL apply AC-004.1 through AC-004.7 and AC-004.19 to `jiraProjectKey` with the same HTTP statuses, codes, params, fields and local-conflict ordering.
  - AC-005.2: WHEN a Save passes local validation and link checks, THE system SHALL read the Jira project and its statuses again through `TrackerProjectService.findProject` before any write, whether or not a connection test was previously sent.
  - AC-005.3: IF the Save lookup returns `NOT_FOUND`, `DENIED` or `UNAVAILABLE`, THEN THE system SHALL answer respectively 409 MSG48, 409 MSG48 or 503 MSG06 with the params and absence of field errors defined in AC-004.15 and AC-004.16, and write nothing.
  - AC-005.4: IF `doneStatusIds` is missing, null or empty, THEN THE system SHALL answer 400 MSG01 on field `doneStatusIds` with params `{field_name: "doneStatusIds"}` and write nothing.
  - AC-005.5: IF a selected id is null, blank or absent from the project's freshly read statuses, THEN THE system SHALL answer 400 MSG01 on field `doneStatusIds` with params `{field_name: "doneStatusIds"}` and write nothing.
  - AC-005.6: IF `reviewRange` is missing or null, THEN THE system SHALL answer 400 MSG01 on field `reviewRange` with params `{field_name: "reviewRange"}` and write nothing.
  - AC-005.7: IF numeric `reviewRange` is fractional, below 1 or above 2,147,483,647, THEN THE system SHALL answer 400 MSG44 on field `reviewRange` with params `{}` and write nothing.
  - AC-005.8: WHEN a valid Save adds a new Jira project, THE system SHALL insert exactly one `project` row with a generated `projectId`, the caller's Department, Jira's canonical key, the selected ids as a JSONB array in `done_status`, the entered `review_range`, `status = 'ACTIVE'` and `field_mapping = NULL`.
  - AC-005.9: WHEN a new Project is inserted, THE system SHALL insert exactly one `PROJECT_MANAGER` membership for the caller in the same transaction.
  - AC-005.10: WHEN a new Project is inserted, THE system SHALL record exactly one audit entry in that transaction with the signed-in caller as actor, action `TRACKER_CONFIGURATION_CHANGE`, target type `PROJECT`, target id its `projectId`, and details `{operation: "CREATE", jiraProjectKey, departmentId, doneStatusIds, reviewRange}` matching the stored values.
  - AC-005.11: IF two Saves compete to add the same Jira key, THEN THE system SHALL commit one Project, one caller membership and one audit entry, answer the winner 201, and answer the loser 409 MSG49 on `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <winning Department name>}`.
  - AC-005.12: IF inserting the membership or audit entry fails, THEN THE system SHALL roll back all writes from that Save and answer 500 MSG06 with params `{}` and no field error.
  - AC-005.13: WHEN a new Project commits, THE system SHALL answer 201 with its `Project` response.
  - AC-005.14: WHEN a new Project commits, THE existing `ProjectService.findActivePublicIdByJiraProjectKey` SHALL return its `projectId` for the stored canonical key.
  - AC-005.15: WHEN a Save contains repeated valid status ids, THE system SHALL store and return each id once in first-seen order.
  - AC-005.16: IF the JSON body is unreadable, `doneStatusIds` is not an array, or `reviewRange` cannot be read as a number (for example `"abc"`), THEN THE system SHALL answer 400 MSG06 with params `{}` and no field error, and write nothing.
  - AC-005.17: WHEN the write transaction begins after the Jira read, THE system SHALL recheck the caller's eligibility and the current link state before changing any Project or membership.
  - AC-005.18: THE system SHALL execute every Jira call before and outside any active database transaction in the add and reconnect flows.
  - AC-005.19: WHEN changeset `036-project-done-statuses.sql` applies to an existing database, THE migration SHALL convert each non-null legacy `done_status` scalar to a JSONB singleton string array and preserve SQL NULL as SQL NULL without changing another column, row or reference.
  - AC-005.21: IF `reviewRange` breaks AC-005.7 or a selected id is null or blank, THEN THE system SHALL refuse the Save after the Workspace, eligibility and key format checks and before the link check, without a Jira call; an id absent from the project's statuses (AC-005.5) is refused only after the Jira read.
  - AC-005.20: THE migration SHALL leave `review_range` nullable with no new database CHECK, default or backfill, so existing zero and negative fixtures retain their review fallback behaviour (D38).

### STORY-006: Reconnect a Removed Project
- As a Project Manager, I want to reconnect an eligible Removed Project so that its tickets and members remain with the same Project.
- Acceptance:
  - AC-006.1: WHEN a valid Save identifies an eligible Removed Project of the caller's Department, THE system SHALL update that row to `ACTIVE` with Jira's canonical key and the saved `doneStatusIds` and `reviewRange`, using all validation and tracker failure rules of STORY-005.
  - AC-006.2: WHEN a Removed Project reconnects, THE system SHALL keep its existing `projectId`, Department, tickets, Ticket Resolver memberships and `field_mapping` unchanged.
  - AC-006.3: WHEN the previous Project Manager re-adds the Removed Project, THE system SHALL keep the Project's membership rows unchanged.
  - AC-006.4: WHEN another eligible Project Manager re-adds the Removed Project, THE system SHALL delete the previous Project Manager membership if present before upserting the caller's membership as `PROJECT_MANAGER`, including when the caller already has a `MEMBER` row.
  - AC-006.5: WHEN a Removed Project reconnects, THE system SHALL record one audit entry in the same transaction with the signed-in caller as actor, action `TRACKER_CONFIGURATION_CHANGE`, target type `PROJECT`, target id its retained `projectId`, and details `{operation: "RECONNECT", jiraProjectKey, departmentId, doneStatusIds, reviewRange}` matching the saved values.
  - AC-006.6: WHEN the reconnect commits, THE system SHALL answer 200 with the updated `Project` response.
  - AC-006.7: IF the Removed Project belongs to another Department or still has another Active Project Manager holding that role in its Department, THEN THE system SHALL answer 409 MSG49 on field `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}` and write nothing.
  - AC-006.8: IF simultaneous reconnect or add Saves compete for the same Removed row, THEN THE system SHALL commit one reconnect with one audit entry, answer the winner 200 and the loser 409 MSG49 on `jiraProjectKey` with params `{tracker_name: "Jira", department_name: <owning Department name>}`.
  - AC-006.9: IF a membership change or the audit write fails while reconnecting, THEN THE system SHALL answer 500 MSG06 with params `{}` and restore the original Removed row, settings and membership rows with no audit entry from that Save.
  - AC-006.10: WHEN the reconnect commits, THE existing `ProjectService.findActivePublicIdByJiraProjectKey` SHALL return the retained `projectId` for the canonical key.

### STORY-007: Projects of my Department
- As a Project Manager, I want to list the Projects of my Department so that I can see the result of adding or reconnecting them.
- Acceptance:
  - AC-007.1: WHEN an authorised caller sends `GET /api/v1/projects`, THE system SHALL answer 200 `ProjectList` with the caller's own Department as `{departmentId, name}` and all its Active and Removed Projects.
  - AC-007.2: WHEN the system lists Projects, THE system SHALL order them by `jiraProjectKey` ascending without paging.
  - AC-007.3: WHEN the system lists a Project, THE system SHALL return its `projectId`, `jiraProjectKey`, `projectManager`, `memberCount` and `status` as `ProjectRow`.
  - AC-007.4: WHEN a Project's `PROJECT_MANAGER` membership names an Active account, THE system SHALL return that account as `projectManager: {userId, fullName}`.
  - AC-007.5: IF a Project has no `PROJECT_MANAGER` membership or that account is not Active, THEN THE system SHALL return `projectManager: null`.
  - AC-007.6: WHEN the system lists a Project, THE system SHALL set `memberCount` to the count of all its membership rows, including the Project Manager.
  - AC-007.7: IF the caller's Department has no Projects, THEN THE system SHALL answer 200 with its Department and `projects: []`.
  - AC-007.8: THE Projects list SHALL make no Jira call and write no persisted data.

### STORY-008: Projects on Jira Integration (web app, screen 3.1)
- As a Project Manager, I want to see my Department and its Projects on Jira Integration so that I can manage the add flow from one screen.
- Acceptance:
  - AC-008.1: WHEN a Project Manager opens `/jira-integration`, THE web app SHALL load `GET /api/v1/projects` and show the returned Department name in a read-only field labelled "Department".
  - AC-008.2: WHEN the list loads, THE web app SHALL show a Projects table with columns "No." (the row's position, from 1), "Jira project key", "Project Manager", "Members" and "Status" using the returned order.
  - AC-008.3: WHEN a row has a `projectManager`, THE web app SHALL show its `fullName` in the Project Manager column.
  - AC-008.4: IF a row has `projectManager: null`, THEN THE web app SHALL show MSG54 in that column.
  - AC-008.5: WHEN a row has status `ACTIVE` or `REMOVED`, THE web app SHALL show respectively "Active" or "Removed".
  - AC-008.6: WHILE the Projects query is loading, THE web app SHALL show the loading state in place of the table.
  - AC-008.7: IF the Projects query fails, THEN THE web app SHALL show the returned message by code and an error state with a retry action.
  - AC-008.8: IF the loaded list is empty, THEN THE web app SHALL show MSG04 in place of the table.
  - AC-008.9: WHEN an Admin without the Project Manager role opens screen 3.1, THE web app SHALL keep the existing Jira Integration placeholder without sending a Projects request or showing the Add project button.
  - AC-008.10: IF a signed-in user has neither Admin nor Project Manager role, THEN THE web app SHALL show the route guard's MSG08 no-permission page without sending a Projects request.
  - AC-008.11: WHEN a row's `projectManager.userId` is the signed-in user's id, THE web app SHALL show its Jira project key as a link to 3.2 Project Detail of that Project (`/jira-integration/projects/{projectId}`); IF it is not, THEN THE web app SHALL show the key as plain text.

### STORY-009: Add project popup (web app)
- As a Project Manager, I want to test a key and save the initial settings in a popup so that I can add or reconnect a Project.
- Acceptance:
  - AC-009.1: WHEN a Project Manager clicks "Add project" on screen 3.1, THE web app SHALL open a `FormModal` titled "Add project" with an empty required "Project key" field, "Test connection" and Cancel, with settings and Save hidden.
  - AC-009.2: IF Project key is empty or only whitespace when Test connection is clicked, THEN THE web app SHALL show MSG01 below it with params `{field_name: "Project key"}` and send no request.
  - AC-009.3: IF a nonblank Project key exceeds 20 UTF-16 code units as typed, THEN THE web app SHALL show MSG02 below it with params `{field_name: "Project key", max_length: 20}`, preserve the whole input and send no request.
  - AC-009.4: WHEN Test connection is clicked with a locally valid key, THE web app SHALL send one `POST /api/v1/projects/connection-test` with the original `jiraProjectKey`, however often the button is clicked while it runs.
  - AC-009.5: WHEN the connection test answers 200, THE web app SHALL show MSG47 with params `{tracker_name: "Jira"}` as a toast.
  - AC-009.6: WHEN the connection test answers 200, THE web app SHALL reveal a "Done statuses" multi-select containing every returned status by name and id, preselected from `doneStatusIds`.
  - AC-009.7: WHEN the connection test answers 200, THE web app SHALL reveal "Review range" prefilled from `reviewRange` and the Save button.
  - AC-009.8: WHEN the Project Manager edits Project key after a successful test, THE web app SHALL discard the tested result and hide the settings and Save until another successful test.
  - AC-009.9: IF no Done status is selected when Save is clicked, THEN THE web app SHALL show MSG01 below Done statuses with params `{field_name: "Done statuses"}` and send no request.
  - AC-009.10: IF Review range is empty when Save is clicked, THEN THE web app SHALL show MSG01 below it with params `{field_name: "Review range"}` and send no request.
  - AC-009.11: IF Review range is not a whole number from 1 through 2,147,483,647 when Save is clicked, THEN THE web app SHALL show MSG44 below it with params `{}` and send no request.
  - AC-009.12: WHEN Save is clicked with tested settings passing local checks, THE web app SHALL send one `POST /api/v1/projects` with `jiraProjectKey` from the tested canonical key, the selected `doneStatusIds` and numeric `reviewRange`, however often Save is clicked while it runs.
  - AC-009.13: WHEN Save answers 201 or 200, THE web app SHALL close the popup.
  - AC-009.14: WHEN Save answers 201 or 200, THE web app SHALL show MSG05 as a toast.
  - AC-009.15: WHEN Save answers 201 or 200, THE web app SHALL invalidate the generated `listProjects` query key so the Projects table refreshes.
  - AC-009.16: IF either request returns MSG49 on `jiraProjectKey`, THEN THE web app SHALL show that message below Project key with the server's params.
  - AC-009.17: IF either request returns MSG48, THEN THE web app SHALL show it inline in the popup with the server's params.
  - AC-009.18: IF either request returns another field error, THEN THE web app SHALL show its message by code and params below the corresponding field.
  - AC-009.19: IF either request returns another error without a field error, THEN THE web app SHALL show its message by code and params using the shared error handling, falling back to MSG06 when no recognised code exists.
  - AC-009.20: IF Test connection or Save fails, THEN THE web app SHALL keep the popup open with the entered values and any previously revealed settings intact.
  - AC-009.21: WHILE Test connection or Save is running, THE web app SHALL prevent field edits and further Test connection or Save requests.
  - AC-009.22: WHILE Test connection or Save is running, THE web app SHALL ignore Cancel, the close icon, Escape and mask dismissal.
  - AC-009.23: WHEN the Project Manager dismisses the idle popup, THE web app SHALL close it without a request and clear its values, messages and tested result before the next opening.
  - AC-009.24: THE Add project popup SHALL contain no token, service-account email, service-account credential or field mapping editor.

### STORY-010: Restrict Project operations to the caller's Workspace and Department
- As the Workspace, I want all three Project endpoints restricted to eligible Project Managers so that Project links cannot cross Departments or Workspaces.
- Acceptance:
  - AC-010.1: THE handlers for `POST /api/v1/projects/connection-test`, `POST /api/v1/projects` and `GET /api/v1/projects` SHALL each declare `@PreAuthorize` using `Roles.PROJECT_MANAGER`.
  - AC-010.2: IF the caller has no signed-in session, THEN THE system SHALL answer 401 MSG07 with params `{}` and no field error, without a write or Jira call.
  - AC-010.3: IF a signed-in caller without the Project Manager role sends a request with a valid body, THEN THE system SHALL answer 403 MSG08 with params `{}` and no field error, without a write or Jira call.
  - AC-010.4: IF an authenticated Project Manager has no Department, the Department is not Active, or the caller account is not Active or no longer holds that role, THEN THE system SHALL answer 403 MSG08 with params `{}` and no field error, without a write or Jira call.
  - AC-010.5: IF no Workspace is bound for an authenticated Project Manager, THEN THE system SHALL answer 500 MSG06 with params `{}` and no field error, without a write or Jira call.
  - AC-010.6: WHILE any of the three endpoints runs in a bound Workspace, THE system SHALL read and write Project, Department, membership and audit data only in that Workspace's database, allowing the same Jira key in a different Workspace database.
  - AC-010.7: THE system SHALL derive the Workspace from `TenantScope` and the caller's own Department from `app_user.department_id` joined to `department`, without accepting either scope from request content.
  - AC-010.8: THE system SHALL check the bound Workspace and the caller's eligibility (AC-010.4) before the key format check, the link check and any Jira call, for all three endpoints.

### STORY-011: Suggest members from the Jira project's roles
- As a Project Manager, I want the Add member popup to list the people of my Jira project's team role so that I do not search again for people I already added in Jira (RESEARCH-007).
- Terms: **role** = a project role of the Project's Jira project (team-managed: Administrator, Member, Viewer; company-managed: as the Jira admin defined them); **role person** = an actor of the role of type user (`atlassian-user-role-actor`); **group actor** = an actor of type group (`atlassian-group-role-actor`); **addable** = a role person whose Jira account is linked (identity AC-001.78) to an Active account with the `TICKET_RESOLVER` role that is not a member of the Project.
- Acceptance:
  - AC-011.1: WHEN the Project Manager requests `GET /api/v1/projects/{projectId}/jira-roles`, THE backend SHALL read the roles of the Project's Jira project with the Workspace's Jira App token (tracker `JiraServiceAccount`) and answer 200 with each role's id and name, sorted by name ignoring letter case, without the role `atlassian-addons-project-access`.
  - AC-011.2: WHEN the Project Manager requests `GET /api/v1/projects/{projectId}/jira-roles/{roleId}/people`, THE backend SHALL read that role with the Workspace's Jira App token and answer 200 with every role person — Jira account id, Jira display name and a `status`: `ADDABLE` (with the account's user id, full name and email), `NOT_LINKED`, `NOT_TICKET_RESOLVER`, `NOT_ACTIVE` or `ALREADY_MEMBER` — sorted by Jira display name ignoring letter case, and the names of the role's group actors.
  - AC-011.3: THE backend SHALL never read the members of a group actor and SHALL never add, change or remove a membership row while answering AC-011.1 or AC-011.2.
  - AC-011.4: IF there is no Jira App token for the Workspace, or Jira answers an error, does not answer within 10 seconds, or the role is not one of the project's roles, THEN THE backend SHALL answer 503 with `code` `MSG06` to AC-011.1 and AC-011.2, log the Jira status code only, and change nothing.
  - AC-011.5: THE endpoints of AC-011.1 and AC-011.2 SHALL be refused like every endpoint of this spec (AC-001.3: 403 for a caller who is not the Project Manager; AC-001.4: 404 for an unknown Project), before any Jira call.
  - AC-011.6: WHEN the Add member popup opens, THE web app SHALL show two tabs, "From Jira" (selected) and "Search" (AC-002.10, unchanged), and SHALL keep the people ticked on either tab when the Project Manager switches tabs.
  - AC-011.7: WHEN the "From Jira" tab opens, THE web app SHALL load the roles of AC-011.1 into a role selector, select `Member` when the project has it (otherwise the first role), and load that role's people (AC-011.2); WHEN the Project Manager selects another role, THE web app SHALL load its people.
  - AC-011.8: THE "From Jira" tab SHALL list every role person by Jira display name: an `ADDABLE` person with the T-Solve full name and email and a checkbox; any other person greyed, without a checkbox, with the reason "Not linked to T-Solve", "Not a Ticket Resolver", "Account not active" or "Already a member" (web app labels — user, 2026-11-18).
  - AC-011.9: WHERE the role has group actors, THE "From Jira" tab SHALL show `This role also contains {groups}; their members are not listed.` with the group names, comma-separated, in place of `{groups}` (a web app label — user, 2026-11-18).
  - AC-011.10: IF the role has no role person, THEN THE "From Jira" tab SHALL show MSG04; IF loading the roles or the people fails, THEN THE tab SHALL show "Suggestions from Jira are not available for this Project." (a web app label — user, 2026-11-18) and THE "Search" tab SHALL keep working.
  - AC-011.11: WHEN the Project Manager clicks Add, THE web app SHALL send every ticked person of both tabs in one `POST /api/v1/projects/{projectId}/members` (AC-002.4), with the result and errors of AC-002.13 and AC-002.14.

## Out of Scope

KAN-128/129:
- Editing a Project's settings after it is added (the Settings tab of 3.2) and removing a Project. Adding a Project, reconnecting it and the Projects table of 3.1 are KAN-127 (STORY-004..010 below).
- The Escalation handler — no Project setting holds it yet (KAN-127 Settings).
- Refusing the Solution Form and its resubmission to a removed member — `jira-app` (KAN-126, UC-19) checks `project_membership`; this module only removes the row (contract below).
- Ticket visibility of members — already derived from `project_membership` by identity's scope check (KAN-118, GBR-02).
- The Solution Form and who sees it — `jira-app` (UC-19).
- Emailing or notifying the added people.
- Removing a member because they left the Jira role, or adding anyone without the Project Manager's Add (STORY-011 only suggests; user, 2026-11-18).
- Listing the members of a Jira group actor (STORY-011 names the group only).

KAN-127:
- UC-15 and UC-16 member management (add and remove Ticket Resolvers on 3.2): KAN-128/KAN-129, STORY-001..003 above.
- Screen 3.2 Project Detail, including its Settings tab and editing settings after adding (D33). No current story owns that edit; it is a backlog gap.
- Connection status, Last import, per-row Test connection and Remove on screen 3.1 (D31).
- A field mapping editor; a new Project stores NULL for standard Jira fields (D32).
- Storing Jira's project id, following Jira project key renames, or changing existing key readers (D34).
- The GBR-04 end-to-end gap: KAN-126 `watchDone.js` still opens the Solution Form by Jira's Done status category. Reading these stored Done statuses for the Jira App and opening by the chosen ids is a later change; KAN-127 stores the configuration only.
- The Admin's Jira site address and service account configuration (UC-10/UC-11, KAN-119), credential encryption and gateway Bearer support.
- UUID key alignment of the internal tables (KAN-248); public API ids remain UUIDs through existing `public_id` columns.
- A Department's `default_project_id` (UC-13); adding a Project does not choose a default Project.
- CSRF shared-client changes, per-request production Workspace binding and a live browser journey requiring those dependencies.

## Interface Contracts

### Exposed by this module — KAN-128/129 (STORY-001..003)
- REST, tag `projects`, all `@PreAuthorize` `PROJECT_MANAGER` role plus the per-Project check of AC-001.3:
  - `GET /api/v1/projects/{projectId}` → `ProjectSummaryResponse { id: UUID, jiraProjectKey, departmentName, projectManagerName, status: ACTIVE|REMOVED }`
  - `GET /api/v1/projects/{projectId}/members` → `ProjectMemberResponse[] { userId: UUID, fullName, email, role: PROJECT_MANAGER|MEMBER, dateAdded: Instant }`
  - `GET /api/v1/projects/{projectId}/member-candidates?q=` → `MemberCandidateResponse[] { userId: UUID, fullName, email }`
  - `POST /api/v1/projects/{projectId}/members` body `AddProjectMembersRequest { userIds: UUID[] }` → 201 `ProjectMemberResponse[]`
  - `DELETE /api/v1/projects/{projectId}/members/{userId}` → 200 `ProjectMemberResponse[]`
  - `GET /api/v1/projects/{projectId}/jira-roles` → `JiraProjectRoleResponse[] { id: number, name }` (STORY-011)
  - `GET /api/v1/projects/{projectId}/jira-roles/{roleId}/people` → `JiraRolePeopleResponse { people: JiraRolePersonResponse[] { jiraAccountId, displayName, status: ADDABLE|NOT_LINKED|NOT_TICKET_RESOLVER|NOT_ACTIVE|ALREADY_MEMBER, userId?, fullName?, email? }, groups: string[] }`; 503 MSG06 (STORY-011)
- `project_membership` is the source of truth for who may write Solutions for a Project: a person without a row for the Project is not a member. The Solution Form (`jira-app`, KAN-126) must refuse to show or accept a submission or resubmission from a non-member.
- `AuditAction.PROJECT_MEMBER_CHANGE` (backend enum, DB check constraint, audit-log screen label "Project member change").

### Required from other modules — KAN-128/129 (STORY-001..003)
- `identity`: `app_user` (status, full name, email), `user_role`, the signed-in caller id; `project_membership` table (017, 023). Not changed.
- `identity`: `JiraAccountLinkLookup.linkedAccountIds` — Jira account ids → linked account ids, whatever the account's status (AC-001.78, STORY-011).
- `tracker`: `JiraServiceAccount` — the Workspace's Jira App token and site (STORY-011).
- `be-foundation`: `AuditService`.
- `fe-foundation`: route `paths.projectDetail()` with role `PROJECT_MANAGER` (exists), shared UI, MSG texts.

### Exposed by this module — KAN-127 (STORY-004..010)
Exactly three operations are added to `frontend/openapi/tsolve-api.yaml` before implementation, all tagged `projects`, all requiring the Project Manager role. The browser uses generated API code through `@/shared/api` and TanStack Query. No Project credential or caller-supplied Department/Workspace property is in a request schema.

| Method and path | operationId | Request | Success |
|-----------------|-------------|---------|---------|
| `POST /api/v1/projects/connection-test` | `testProjectConnection` | `ProjectConnectionTestRequest` | 200 `ProjectConnectionTest` |
| `POST /api/v1/projects` | `createProject` | `CreateProjectRequest` | 201 new `Project`; 200 reconnected `Project` |
| `GET /api/v1/projects` | `listProjects` | No body or query parameters | 200 `ProjectList` |

`application/json` is used for request and success bodies. All UUID fields below are UUID strings, never internal BIGINT ids. `projectId` refers to `project.public_id`; `departmentId` refers to `department.public_id`; `userId` refers to `app_user.id` (D50).

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
| `projectManager` | object or null | yes | `{userId: UUID string, fullName: string}` from the Project Manager membership if the account is Active, otherwise null. This display rule is distinct from the stricter D45 reconnect protection rule. |
| `memberCount` | integer, minimum 0 | yes | Count of all membership rows; Project Manager counts. |
| `status` | string enum `ACTIVE`, `REMOVED` | yes | Browser labels are Active and Removed. |

#### Error body and message parameters

Errors use the existing `application/problem+json` `ProblemDetail` schema: `status`, `code`, `params`, and `fieldErrors: [{field, code, params}]` for a field error. No field error means `fieldErrors` is absent. Each operation's table below defines its errors; success bodies carry no MSG code. Unexpected failures use 500 MSG06.

MSG01 uses `field_name`, for example "The Project key field is required.". MSG02 uses `field_name` and `max_length`, for example "Project key must not exceed 20 characters.". Server `field_name` is the API property name; client validation uses the displayed field label (D49). MSG44 has no params: "Review range must be a whole number of days, 1 or more.". MSG47 is "Connected to Jira successfully." with `tracker_name = Jira`. MSG48 is "Could not connect to Jira. Check the project key and that the service account can browse this project." with `tracker_name = Jira`. MSG49 uses `tracker_name = Jira` and the owning Department's name, for example "This Jira project already belongs to IT Operations.". MSG54 is "No Project Manager. Tickets of this Project are not being reviewed.". MSG04 is "No results found."; MSG05 is "Changes saved successfully.". The web app renders these through the MSG catalog.

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

Changeset `036-project-done-statuses.sql`, author `project`, id `036-project-done-statuses`, is included last in `db.changelog-master.xml` (D37). It changes `done_status` from VARCHAR to JSONB with the preservation rules in AC-005.19; no applied changeset is edited. No new review-range CHECK is permitted (D38).

`ProjectLinkRepository` uses `NamedParameterJdbcTemplate` (D40), deliberately differing from the JPA repository default in `backend/AGENTS.md` section 3. The existing `Project` entity remains unchanged. Insert uses `ON CONFLICT (jira_project_key) DO NOTHING RETURNING`; no returned row means 409 MSG49, not a swallowed failed transaction. Reconnect uses a conditional `UPDATE ... WHERE status = 'REMOVED'` on the identified row, after eligibility checks. No updated row means 409 MSG49. A losing insert never turns into an implicit reconnect. Membership replacement deletes the old Project Manager row before `ON CONFLICT (user_id, project_id) DO UPDATE` upserts the caller; it respects `uq_membership_one_manager`. Conditional writes and audit share one transaction. The test never calls these writes.

### Required from other modules — KAN-127 (STORY-004..010)
| Owner | Required interface | Use |
|-------|--------------------|-----|
| tracker | `tracker.service.TrackerProjectService.findProject(String projectKey)` returning `tracker.dto.TrackerProjectLookup(outcome, key, statuses)` | New tracker STORY-013, D46. `FOUND`, `NOT_FOUND`, `DENIED`, `UNAVAILABLE`; no client or exception leaks into Project. |
| identity | `identity.signin.port.TenantScope.currentWorkspaceId()` and authenticated `TSolveUser` | Workspace binding and caller id. Own Department is read through Project's repository using existing tables (D39), not another module's repository. |
| workspace | Existing `department` data and `DepartmentRef` API shape | Read-only Department in the list; no new workspace operation or dependency on the unmerged KAN-120 branch. |
| common | `AuditService.record(action, targetType, targetId, details)` | Joins the write transaction and obtains the signed-in actor; audit ids are public UUIDs. |
| common | `Roles.PROJECT_MANAGER`, `GlobalExceptionHandler`, coded exceptions and `ProblemDetail` | Shared method security and the error envelope; no module exception handler. |
| fe-foundation | Existing role guard, `FormModal`, `DataTable`, status display kit, MSG catalog and shared API/error helpers | Screen 3.1, popup, loading/error/empty states; no new dependency. |
| worker | Existing Workspace execution boundary | Retained module dependency; UC-16 schedules no job and makes no worker call. |

Tracker already calls `ProjectService.findActivePublicIdByJiraProjectKey` for issue-created events. This existing read remains compatible; the new tracker lookup only calls Jira and never calls Project, so the new lookup path does not recurse through that existing flow.

## Definition of Done

- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] `mvn verify` passes in `backend/` (tests + `spotless:check`)
- [ ] In `frontend/`: `npm run format:check`, `lint`, `typecheck`, `test`, `build` pass, and `npm run generate:api` leaves no diff in `src/shared/api/generated`
- [ ] `bun harnessclaude/hooks/sdd.ts trace project-integration` reports every criterion covered
- [ ] Every acceptance criterion has a behavioural test naming its AC id: backend suffix such as `_AC_004_7`, frontend prefix such as `AC-008.3`.
- [ ] `bun harnessclaude/hooks/sdd.ts check project-integration` and `check tracker` pass; specs are approved separately before code is written.
- [ ] `sdd.ts trace project-integration` and tracker STORY-013 trace pass using each module's code and test paths.
- [ ] Backend `mvn verify` passes, including tests and `mvn spotless:check`; PostgreSQL tests prove rollback, concurrency and Workspace isolation using the existing shared-container test setup.
- [ ] Frontend `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test` and `npm run build` pass in `frontend/`.
- [ ] `npm run generate:api` leaves no diff in `src/shared/api/generated` after generation from the updated YAML contract.
- [ ] Changeset 036 applies on an empty database (every IT starts on the full changelog) and its statements convert a pre-036 `project` shape holding NULL and non-null legacy Done status values with every other column unchanged; existing zero/negative review fallback fixtures still work.
- [ ] MSW supports list, test, create and reconnect consistently, including a Removed fixture of the caller's Department; page tests verify refresh and error retention.
- [ ] The separate D47 MSG48 catalog quick change is complete before the screen task; the interim limits and GBR-04 follow-up remain visible in the implementation handoff.
- [ ] Each feature stops after verification for user agreement on its commit, with one feature per code commit plus its harness commit; no push, merge or pull request without an explicit request.
