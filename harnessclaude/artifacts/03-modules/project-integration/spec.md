---
module: project-integration
date: 2026-11-13
owner: BA
---

# Spec: project-integration

Covers UC-15 Add Ticket Resolver to Project (Jira KAN-128; numbered UC-17 in the Jira title and the module map before the SRS renumbering), STORY-001..STORY-002, and UC-16 Remove a member from a Project (Jira KAN-129, titled UC-18), STORY-003. Source: SRS v3460 (`_context/Report`): UC-15, UC-16, screen 3.2 Project Detail, GBR-02, GBR-20, MSG04, MSG05, MSG06, MSG08, MSG55. The other stories of this module (KAN-125, KAN-126, KAN-127 Add Project) are added later in Change Mode. Decision (user, 13/11): plan and build KAN-128 alone, before KAN-127.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-13 | STORY-001, STORY-002 | Initial spec: UC-15 Add Ticket Resolver to Project | KAN-128; scope "KAN-128 only" chosen by the user on 13/11 |
| 2026-11-13 | STORY-001, STORY-002 | Builder check (before approval): no Project Manager case and MSG54 removed (only the Project Manager opens 3.2), AC-001.7/001.8 renumbered 001.6/001.7; 404 only for Project Manager callers; null full names last; no Department filter on candidates; `q` trimmed; DB time; ids in order sent; popup loads at once; AC-002.16 audit label | Builder check findings 1, 2, 4, 7, 13–17 |
| 2026-11-14 | STORY-001, STORY-003 | Added STORY-003 Remove a member (UC-16, KAN-129): `DELETE …/members/{userId}`, MSG55 confirm, audit `{"removed": [...]}`, tickets kept; AC-001.7 reworded (no Remove button on the Project Manager's row, instead of none at all) | KAN-129; scope "remove the membership only" chosen by the user on 14/11 — the Solution Form's membership check belongs to `jira-app` (KAN-126) |

## Requirements

### Functional
1. Only the Project Manager of a Project opens its Project Detail (screen 3.2) and manages its members (GBR-20).
2. Project Detail shows a read-only header and the Members tab: Full name, Email, Role, Added on of every member. The Project Manager is always a member.
3. The Project Manager adds one or more Active Ticket Resolver accounts that are not yet members, found by name or email. They become members with role `MEMBER` in `project_membership`.
4. A new member sees the tickets of the Project's Department. The existing scope check (`ScopeAccessRepository`, KAN-118) already derives this from `project_membership`; this module adds no visibility code.
5. Each add is recorded in the audit log.

### Non-Functional
1. The membership rows and the audit entry are written in one database transaction; an add is all-or-nothing.
2. A Project's members live only in the Workspace's tenant database. The Workspace and the caller never come from request content.
3. Two adds sent at the same moment for the same person cannot create two rows (primary key `(user_id, project_id)`).

### Interim limits
These are known limits of the code this story builds on. They are not defects of this story.

1. There is no Projects table on Jira Integration (3.1) until KAN-127. Project Detail is reached by its path `/jira-integration/projects/{projectId}`; Projects come from seed data or test fixtures.
2. The web app does not send the CSRF header yet, so a browser POST to the real backend gets 403 (workspace decision D17). This story is verified with MSW and backend tests.
3. The Settings tab of 3.2 (field mapping, Done statuses, review range, Escalation handler) belongs to KAN-127 and is not shown.

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
  - AC-002.10: WHEN the Project Manager clicks "Add member", THE web app SHALL open the Add member popup with a search box (name or email) and the candidate list of AC-002.1, loaded at once with an empty `q` and refreshed 300 ms after the last keystroke, where one or more candidates can be selected.
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

## Out of Scope
- Adding, configuring, re-adding or removing Projects, the Projects table of 3.1 and the Settings tab of 3.2 — KAN-127 (UC-14).
- The Escalation handler — no Project setting holds it yet (KAN-127 Settings).
- Refusing the Solution Form and its resubmission to a removed member — `jira-app` (KAN-126, UC-19) checks `project_membership`; this module only removes the row (contract below).
- Ticket visibility of members — already derived from `project_membership` by identity's scope check (KAN-118, GBR-02).
- The Solution Form and who sees it — `jira-app` (UC-19).
- Emailing or notifying the added people.

## Interface Contracts

### Exposed by this module
- REST, tag `projects`, all `@PreAuthorize` `PROJECT_MANAGER` role plus the per-Project check of AC-001.3:
  - `GET /api/v1/projects/{projectId}` → `ProjectSummaryResponse { id: UUID, jiraProjectKey, departmentName, projectManagerName, status: ACTIVE|REMOVED }`
  - `GET /api/v1/projects/{projectId}/members` → `ProjectMemberResponse[] { userId: UUID, fullName, email, role: PROJECT_MANAGER|MEMBER, dateAdded: Instant }`
  - `GET /api/v1/projects/{projectId}/member-candidates?q=` → `MemberCandidateResponse[] { userId: UUID, fullName, email }`
  - `POST /api/v1/projects/{projectId}/members` body `AddProjectMembersRequest { userIds: UUID[] }` → 201 `ProjectMemberResponse[]`
  - `DELETE /api/v1/projects/{projectId}/members/{userId}` → 200 `ProjectMemberResponse[]`
- `project_membership` is the source of truth for who may write Solutions for a Project: a person without a row for the Project is not a member. The Solution Form (`jira-app`, KAN-126) must refuse to show or accept a submission or resubmission from a non-member.
- `AuditAction.PROJECT_MEMBER_CHANGE` (backend enum, DB check constraint, audit-log screen label "Project member change").

### Required from other modules
- `identity`: `app_user` (status, full name, email), `user_role`, the signed-in caller id; `project_membership` table (017, 023). Not changed.
- `be-foundation`: `AuditService`.
- `fe-foundation`: route `paths.projectDetail()` with role `PROJECT_MANAGER` (exists), shared UI, MSG texts.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] `mvn verify` passes in `backend/` (tests + `spotless:check`)
- [ ] In `frontend/`: `npm run format:check`, `lint`, `typecheck`, `test`, `build` pass, and `npm run generate:api` leaves no diff in `src/shared/api/generated`
- [ ] `bun harnessclaude/hooks/sdd.ts trace project-integration` reports every criterion covered
