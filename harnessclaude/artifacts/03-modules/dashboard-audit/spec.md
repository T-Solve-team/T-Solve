---
module: dashboard-audit
date: 2026-10-31
owner: BA
status: draft
---

# Spec: dashboard-audit

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-31 | STORY-001 | Define the frontend-only audit log slice with MSW and a proposed OpenAPI contract | KAN-153; user selected frontend only and MSW with an API proposal |
| 2026-10-31 | STORY-001 | Select a text search over actor email instead of a user selector | User chose option 1; supports historical actors without a user-options endpoint |
| 2026-10-31 | STORY-001 | Finalize the inclusive Vietnam-time date range with no default range and no future dates; complete filter semantics | User chose option 1 for the final date-range decision |
| 2026-11-07 | STORY-002, STORY-003 | New STORY-002 (write audit entries for review decisions and CSV import through `AuditService.record` into a new append-only `audit_log_entry` table that replaces `audit_event`) and STORY-003 (the backend serves `GET /api/v1/audit-events` of the contract); Out of Scope and Interface Contracts updated; supersedes be-foundation STORY-004 | KAN-152, demo 08/11. The user, 07/11: actions = review decisions + import (no user/role backend exists yet); table = `audit_log_entry` from the team DDL `02_tenant.sql`, adapted to UUID keys and the contract's target fields; build the read API; be-foundation's spec is left untouched for now with a follow-up to retire its STORY-004 |
| 2026-11-09 | STORY-004, STORY-005 (new) | New STORY-004 Knowledge Dashboard figures API (KAN-150) and STORY-005 screen 9.1 (KAN-151); Requirements 6-7, Out of Scope, Interface Contracts (`getKnowledgeDashboard`, `KnowledgeDashboard`), Sources and Definition of Done follow; STORY-001..003 unchanged | User decisions 09/11: the five groups of SRS 9.1 from stored data without ticket views; one endpoint for all groups; knowledge gaps counted by the asker's Department; charts with antd and CSS, no new dependency. Planning call: the filter options come in the response because no Department or Project list API exists |
| 2026-11-09 | STORY-004, STORY-005 | AC-004.7: an Admin's `projectId` outside the given `departmentId` answers 400 MSG06; AC-004.10: "given in the period" is the time of the latest rating (`ticket_feedback.updated_at`) and the 5 lowest leave out the 5 highest; new AC-005.11: the page says that Knowledge gaps ignore the chosen Project | Review of PR #178 (trhieudev1309) and the user's decisions 09/11: "400 MSG06", "Loại trùng", "updated_at", and a note on the page for Project-independent gaps |

## Requirements

### Functional
1. Implement screen 9.2 Audit Log at `/audit-log` for the Admin, using the existing route and UI kit.
2. Show audit entries and combine filters for user (Actor), action and date range, within the current Workspace.
3. Use the existing generated API client pipeline and MSW until the backend implements the proposed contract.
4. Record an audit entry for each successful review decision (approve, reject, request changes) and each successful
   CSV import, in the same transaction as the action (KAN-152; `backend/AGENTS.md` §5).
5. Serve the recorded entries through the contract's `GET /api/v1/audit-events`, Admin only.
6. Serve the Knowledge Dashboard figures (UC-34, GBR-18) through one read-only `GET /api/v1/dashboard`: Project
   Managers see their Department, the Admin the whole Workspace; last 30 days by default (KAN-150).
7. Show them on screen 9.1 `/knowledge-dashboard` as charts with a period, Department and Project filter (KAN-151).

### Non-Functional
1. Keep list filters, page and sort in the URL, using the existing routing conventions.
2. Use server-style pagination of 20 rows per page, the existing date formatting, and catalog messages.
3. Keep application code inside `frontend/src/features/audit-log/`, apart from the OpenAPI contract and its generated outputs. Add no dependencies.

## User Stories

### STORY-001: View and filter audit log
- As an Admin, I want to view and filter the audit log of my Workspace so that I can find recorded actions.
- Acceptance:
  - AC-001.1: WHEN an Admin opens `/audit-log`, THE frontend SHALL show screen 9.2 with the columns `Date and time`, `Actor`, `Action`, `Target` and `Summary`.
  - AC-001.2: WHEN an entry has a timestamp, THE frontend SHALL display it using the existing `cells.dateTime` formatter in `Asia/Ho_Chi_Minh`.
  - AC-001.3: WHEN the Admin filters by Actor, action or date range, THE frontend SHALL send the selected filters together to the audit list query.
  - AC-001.4: WHEN a filter changes, THE frontend SHALL return to page 1 and keep the filters in the URL across reload and browser Back navigation.
  - AC-001.5: WHEN more than 20 entries match, THE frontend SHALL paginate the results in groups of 20 and retain the filters when changing pages.
  - AC-001.6: WHILE the first audit query is pending, THE frontend SHALL show the existing LoadingState.
  - AC-001.7: IF no entries match, THEN THE frontend SHALL show the existing EmptyState with MSG04.
  - AC-001.8: IF the audit query fails, THEN THE frontend SHALL show the existing ErrorState with its catalog message and a retry action that retains the filters.
  - AC-001.9: IF the signed-in user has no Admin role, THEN THE existing route guard SHALL show the forbidden screen with MSG08 without requesting audit entries.
  - AC-001.10: WHEN the frontend requests audit entries, THE frontend SHALL use a relative same-origin API path without adding a Workspace parameter or header.
  - AC-001.11: WHEN mock mode supplies audit data, THE MSW handler SHALL apply the combined filters, stable ordering and pagination to the current mock Workspace's entries before returning them.
  - AC-001.12: WHEN the Admin views the audit log, THE frontend SHALL offer read-only viewing and filtering without audit edit, delete, export or entry-detail actions.
  - AC-001.13: WHEN the Admin searches the Actor field, THE frontend SHALL send the entered email search text as the `actor` query parameter instead of requesting a user-options list.
  - AC-001.14: WHEN the Admin first opens the audit log without date filters, THE frontend SHALL request entries without a default date restriction.
  - AC-001.15: WHEN the Admin selects a valid date range, THE frontend SHALL filter from the start of the first day through the end of the last day in `Asia/Ho_Chi_Minh`, including both selected dates.
  - AC-001.16: IF a supplied date range is incomplete, malformed, reversed or contains a future date in `Asia/Ho_Chi_Minh`, THEN THE frontend SHALL show MSG39 beside the date filter and prevent an audit request using that invalid range.
  - AC-001.17: WHEN the Admin searches by Actor and selects an action, THE MSW handler SHALL match the trimmed Actor text as a case-insensitive substring of the actor email and match the selected action exactly.

### STORY-002: Record audit entries (KAN-152)
- As an Admin, I want each review decision and each CSV import recorded once, with who did it to what and when, and
  never changed, so that the audit log can be trusted.
- Acceptance:
  - AC-002.1: WHEN a module calls `AuditService.record` inside its transaction, THE backend SHALL store one
    `audit_log_entry` row with the given `AuditAction`, target type, target id and optional details, the signed-in
    user's id as `actor_id`, their email as `actor_email` and the current UTC time as `created_at`.
  - AC-002.2: IF `record` is called outside a transaction, THEN THE backend SHALL throw and store nothing.
  - AC-002.3: IF `record` is called with no signed-in user, THEN THE backend SHALL throw and store nothing.
  - AC-002.4: WHEN the caller's transaction rolls back, THE backend SHALL keep no audit entry of it.
  - AC-002.5: IF anything tries to update, delete or truncate rows of `audit_log_entry`, THEN THE database SHALL
    reject it.
  - AC-002.6: THE new Liquibase changeset SHALL drop the unused table `audit_event` and create `audit_log_entry` with
    `audit_log_entry_id` (UUID primary key, `gen_random_uuid()`), `actor_id` (UUID, nullable), `actor_email`,
    `action` (limited to the `AuditAction` values), `target_type`, `target_id` (UUID), `details` (JSONB, nullable)
    and `created_at` (TIMESTAMPTZ), with an index on `created_at`, no `updated_at` and no `workspace_id`.
  - AC-002.7: WHEN a review decision (approve, reject or request changes) succeeds, THE backend SHALL record one
    entry per decided ticket, with action `APPROVE`, `REJECT` or `REQUEST_CHANGES`, target type `TICKET` and the
    ticket id, in the decision's transaction.
  - AC-002.8: IF a review decision is refused or fails, THEN THE backend SHALL record no audit entry for it.
  - AC-002.9: WHEN a CSV import is confirmed and succeeds, THE backend SHALL record one entry with action `IMPORT`,
    target type `PROJECT`, the Project id, and details holding the Import Batch id and the created and skipped row
    counts, in the import transaction.
  - AC-002.10: IF a CSV import is refused or fails, THEN THE backend SHALL record no `IMPORT` entry.
  - AC-002.11: THE tests of AC-002.2, AC-002.4, AC-002.5, AC-002.6, AC-002.7, AC-002.8, AC-002.9 and AC-002.10 SHALL
    run against a real PostgreSQL started by Testcontainers from `pgvector/pgvector:pg16`, with the Liquibase
    changelog applied.

### STORY-003: Serve the audit log (KAN-152, for KAN-153)
- As an Admin, I want the Audit Log screen to show the recorded entries of my Workspace so that I can find who did
  what.
- Acceptance:
  - AC-003.1: WHEN an Admin requests `GET /api/v1/audit-events`, THE backend SHALL return the entries of the
    current Workspace's database as an `AuditEntryPage` of the contract, each with `id`, `createdAt`, `actor`
    (the actor email), `action`, `resourceType` (the target type), `resourceId` (the target id) and `summary`
    (`null`).
  - AC-003.2: WHEN no `sort` is given, THE backend SHALL order entries by `createdAt` descending with `id` ascending
    as tie-breaker, 20 per page by default.
  - AC-003.3: WHEN an `actor` filter is given, THE backend SHALL match its trimmed text as a case-insensitive
    substring of the actor email; a blank `actor` SHALL apply no Actor restriction.
  - AC-003.4: WHEN an `action` filter is given, THE backend SHALL return only entries with exactly that action.
  - AC-003.5: WHEN `from` and `to` are given, THE backend SHALL return entries from `from` 00:00 through the end of
    `to` in `Asia/Ho_Chi_Minh`, both days included; filters SHALL combine with AND.
  - AC-003.6: IF the date pair is invalid, THEN THE backend SHALL answer 400 with `MSG39`; invalid means only one
    of `from` and `to` is given, a date is not a real `YYYY-MM-DD` day, `from` is after `to`, or a date is after
    today in `Asia/Ho_Chi_Minh` (as the MSW handler and Review History answer).
  - AC-003.7: IF another query parameter is malformed, THEN THE backend SHALL answer 400 with `MSG06`; malformed
    means an `action` that is not an `AuditAction`, a `sort` other than `createdAt` (asc or desc), or a `page` /
    `size` out of range (`page` ≥ 0, `size` 1–100).
  - AC-003.8: IF the signed-in user is not an Admin, THEN THE backend SHALL answer 403 with `MSG08` (no user: 401
    `MSG07`).

### STORY-004: Serve the knowledge dashboard figures (KAN-150)
- As a Project Manager or an Admin, I want the knowledge figures of my Department or of the whole Workspace for a
  period, so that I can manage the quality and reuse of the knowledge (UC-34, GBR-18).
- Acceptance:
  - AC-004.1: WHEN a Project Manager or an Admin sends `GET /api/v1/dashboard`, THE backend SHALL answer
    `KnowledgeDashboard` with the period, the filter options and the five groups of Interface Contracts, for the
    period and scope asked, in one response.
  - AC-004.2: WHEN `from` and `to` are both absent, THE backend SHALL use the last 30 days in `Asia/Ho_Chi_Minh`
    (from 29 days before today through today, GBR-18) and SHALL return that period.
  - AC-004.3: IF the date pair is invalid, THEN THE backend SHALL answer 400 with MSG39; invalid means only one date
    given, a malformed date, `from` after `to`, a date after today in `Asia/Ho_Chi_Minh`, or more than 366 days.
  - AC-004.4: WHILE the user is a Project Manager and not an Admin, THE backend SHALL count only their own Department
    (`app_user.department_id`), optionally one of its Projects with `projectId`.
  - AC-004.5: IF a Project Manager's request is outside their Department, THEN THE backend SHALL answer 403 with
    MSG08 (another `departmentId`, a `projectId` of another Department, or a user with no Department; Admins excepted).
  - AC-004.6: WHILE the user is an Admin, THE backend SHALL count the whole Workspace, or one Department with
    `departmentId`, or one Project with `projectId`.
  - AC-004.7: IF `departmentId` or `projectId` is not a UUID, THEN THE backend SHALL answer 400 with MSG06; IF it
    names no Department or Project, THEN 404 with MSG04; IF an Admin sends a `projectId` whose Project is not in the
    given `departmentId`, THEN 400 with MSG06.
  - AC-004.8: THE backend SHALL return as Solution outcomes, for the period and scope: the APPROVED, REJECTED and
    CHANGES_REQUESTED review decisions, plus as approved the tickets imported or created manually in the period
    (source other than the Solution Form, by `created_at`); the same per day (every day of the period, zeros
    included) and per Project; the approval rate (approved / all three, null when zero); and the current numbers of
    Pending review and Expired tickets of the scope.
  - AC-004.9: THE backend SHALL return as Knowledge coverage the current numbers of Published tickets and of all
    tickets of the scope, the Published share (null when there is no ticket), and the same per Department.
  - AC-004.10: THE backend SHALL return as Reuse & feedback the Helpful and Not helpful feedback given in the period
    (by the time of each user's latest rating, `ticket_feedback.updated_at`) on tickets of the scope, the helpful
    rate (null when none), and the 5 highest and the 5 lowest rated tickets by helpful rate among the tickets with
    feedback in the period (ties: more feedback first, then title); the 5 lowest SHALL leave out the tickets already
    among the 5 highest.
  - AC-004.11: THE backend SHALL return as Knowledge gaps the 10 most frequent searches of the period with no result
    (query trimmed and grouped case-insensitively, every channel) and the number and the 10 most frequent questions
    of Knowledge Chatbot answers with outcome `NO_SOURCE` in the period; a Department scope counts by the asker's
    Department (`search_log.department_id`, `app_user.department_id` for the Chatbot); `projectId` does not apply.
  - AC-004.12: THE backend SHALL return as Suggestion results the suggestions posted in Jira in the period (by
    `commented_at`) for Projects of the scope, how many of them the reporter answered YES, and the solved share
    (null when none).
  - AC-004.13: THE backend SHALL return as filter options the Departments and the Projects the user may choose:
    every active Department and Project for an Admin, the user's Department and its Projects for a Project Manager.
  - AC-004.14: IF the user has neither the Project Manager nor the Admin role, THEN THE backend SHALL answer 403 with
    MSG08; with no web session, 401 with MSG07.
  - AC-004.15: THE backend SHALL only read, SHALL accept no Workspace parameter or header, and SHALL write no question
    or query text to the application log.

### STORY-005: Knowledge Dashboard page (KAN-151)
- As a Project Manager or an Admin, I want the figures of STORY-004 as charts with a period and a Department filter,
  so that I can see trends at a glance (UC-34, screen 9.1).
- Acceptance:
  - AC-005.1: WHEN a Project Manager or an Admin opens `/knowledge-dashboard`, THE web app SHALL show screen 9.1 with
    the sections Solution outcomes, Knowledge coverage, Reuse & feedback, Knowledge gaps and Suggestion results,
    filled from one `getKnowledgeDashboard` request.
  - AC-005.2: THE web app SHALL offer the periods Last 7 days, Last 30 days (default), Last 90 days and a custom
    range, and SHALL request the figures again with `from` and `to` when the period changes.
  - AC-005.3: IF a custom range is incomplete, reversed, in the future or longer than 366 days, THEN THE web app SHALL
    show MSG39 beside the period and SHALL not send a request with it.
  - AC-005.4: WHERE the user is an Admin, THE web app SHALL offer All Departments (default) or one Department, and a
    Project of the chosen Department; WHERE the user is a Project Manager only, THE web app SHALL show their
    Department fixed and offer its Projects; the options come from the response (AC-004.13).
  - AC-005.5: THE web app SHALL keep the period, the dates, the Department and the Project in the URL, so they stay
    across reload and browser Back.
  - AC-005.6: THE web app SHALL draw the outcomes per day and per Project as bars, the approval, helpful, Published
    and solved shares as progress bars, the totals as statistics, and the top and bottom 5 tickets and the gaps as
    tables, using antd and CSS only (no new npm dependency).
  - AC-005.7: WHILE the request is pending, THE web app SHALL show the existing LoadingState; IF it fails, THEN THE
    web app SHALL show the existing ErrorState (MSG06) with a retry that keeps the filters.
  - AC-005.8: IF a section has nothing to show for the period, THEN THE web app SHALL show MSG04 in that section.
  - AC-005.9: WHERE the user is a Project Manager, THE web app SHALL open the Ticket Detail when a ticket of the top or
    bottom 5 is clicked; for an Admin who is not a Project Manager the tickets are not links.
  - AC-005.10: WHEN mock mode is on, THE feature's MSW handler SHALL answer `getKnowledgeDashboard` with data for
    every section, the requested period and 400 MSG39 for an invalid range; with `VITE_API_MOCKING=false` the page
    SHALL work against the real backend with no change in the feature other than its `mocks/` folder.
  - AC-005.11: WHERE a Project is chosen, THE web app SHALL say in the Knowledge gaps section that its figures count
    the whole Department, or the whole Workspace when no Department is chosen (AC-004.11).

## Out of Scope
- Frontend changes: the screen of STORY-001 already calls the contract; it reads the backend once mocks are off.
- Auditing user/role, permission, Department, publish/unpublish, delete and Jira configuration changes: no backend
  writes them yet; each adds its `AuditService.record` call when it is built.
- `summary` text: no SRS message defines it, so the backend returns `null`.
- Production tenant isolation beyond "one database per Workspace" (ADR-0002): the read uses the request's database.
- Dashboard (STORY-004, STORY-005): the number of ticket views (no view is recorded yet; needs a counter on the
  Ticket Detail, user 09/11), triage classes and routing (`suggestion_result` has neither), opening a filtered
  Ticket List from an outcome number, and leaving greetings and refused questions out of the gaps (KAN-243 has not
  built the classification yet: until then every `NO_SOURCE` answer counts).
- A chart npm dependency (user 09/11: antd and CSS only).
- Popup 9.3 Audit Entry Detail, removed by D18/CR-15 and fe-foundation AC-005.2.
- The keyword filter mentioned by the older SRS field table: this slice follows KAN-153's three requested filters.
- Claiming that MSW or frontend routing enforces authorization or Workspace isolation on the real server.

## Interface Contracts

### Exposed by this module
Screen 9.2 uses the existing Admin-only `/audit-log` route.

The API below started as a frontend proposal (STORY-001, MSW); STORY-003 implements it in the backend
(`vn.tsolve.analytics`), unchanged apart from the contract's description text.

`GET /api/v1/audit-events` (`operationId: getAuditEvents`, tag `audit-log`):

| Property | Shape |
|----------|-------|
| Entry | `id` UUID; `createdAt` UTC timestamp; `actor` email; `action` AuditAction; `resourceType` string; `resourceId` UUID; `summary` nullable string |
| Page | `{ content: AuditEntry[], page: { size, number, totalElements, totalPages } }`, matching the existing Spring-style page schema |
| Paging | Existing `page` (zero-based), `size` and `sort` parameters; default `createdAt,desc` with id as a stable tie-breaker |
| Actor filter | Optional `actor` string: trim surrounding whitespace and match a case-insensitive substring of actor email; empty means no Actor restriction; no user-options endpoint |
| Action filter | Optional `action` AuditAction value, matched exactly; absent means all actions |
| Date filter | Optional paired `from` and `to` calendar dates in `YYYY-MM-DD`; absent means no date restriction |
| Errors | Existing ProblemDetail response; unauthenticated 401/MSG07, forbidden 403/MSG08, unexpected failures MSG06 |
| Workspace | Resolved from the request host; never selected by a query parameter or a custom header |

AuditAction values reuse be-foundation AC-004.1: `APPROVE`, `REJECT`, `REQUEST_CHANGES`, `PUBLISH`,
`UNPUBLISH`, `IMPORT`, `DELETE`, `ROLE_CHANGE`, `PERMISSION_CHANGE`, `TRACKER_CONFIGURATION_CHANGE`.
`Target` displays the resource type and id; it does not navigate to another feature.
`summary` is an optional read-model field for the SRS Summary column, not a proposed database migration.

### Filter behavior

The Actor field is a text search, selected by the user on 2026-10-31. Submitting a blank or whitespace-only
Actor value clears that restriction. Action choices use the AuditAction values above with readable labels.
Filters combine with AND. No matching rows is a successful empty page, not an error.

The date filter has no default restriction, selected by the user on 2026-10-31. Clearing the range removes
both parameters together. A selected range must have both dates, `from <= to`, and neither date after today
in `Asia/Ho_Chi_Minh`. For matching, interpret the lower bound as `from` at 00:00:00 +07:00 and the exclusive
upper bound as the day after `to` at 00:00:00 +07:00. This includes the entire final day, including timestamps
with fractional seconds. The frontend retains invalid URL date values for correction and shows MSG39 rather
than silently making an unfiltered request. The MSW handler returns 400/MSG39 for invalid date parameters.

The date controls disallow future dates; URL validation also enforces this rule. Dates in the URL are calendar
dates, not UTC timestamps, so browser timezone changes do not move the requested days.

### Audit writer (STORY-002)

`vn.tsolve.common.audit.AuditService` — `record(AuditAction action, String targetType, UUID targetId)` and
`record(AuditAction action, String targetType, UUID targetId, Map<String, Object> details)`. It sits in `common`
so that every module can call it without depending on `analytics` (which will read other modules for the
dashboard). It joins the caller's transaction (no own transaction) and takes the actor from the signed-in
`TSolveUser`. Target types used now: `TICKET`, `PROJECT`. This replaces be-foundation STORY-004 (`audit_events`,
`TenantContext`), which predates the database-per-Workspace decision; retiring that story is a follow-up.

Table `audit_log_entry` (from the team DDL `database/02_tenant.sql`, adapted): UUID key as every tenant table;
`actor_email` added because the screen shows the email and it stays readable after the user leaves; the team
DDL's single `target` split into `target_type` + `target_id` to match the contract's `resourceType` /
`resourceId`; `actor_id` has no foreign key, because dev and Keycloak users are not always in `app_user` yet.
Append-only: a trigger refuses UPDATE, DELETE and TRUNCATE.

### Knowledge dashboard (STORY-004, STORY-005)

Screen 9.1 uses the existing `/knowledge-dashboard` route (`PROJECT_MANAGER`, `ADMIN`).

`GET /api/v1/dashboard` (`operationId: getKnowledgeDashboard`, tag `dashboard`), read only, in `vn.tsolve.analytics`:

| Parameter | Shape |
|-----------|-------|
| `from`, `to` | Optional pair of calendar dates `YYYY-MM-DD` (`Asia/Ho_Chi_Minh`), inclusive; absent → last 30 days |
| `departmentId` | Optional Department `public_id` (UUID); Admin only, a Project Manager may send only their own |
| `projectId` | Optional Project `public_id` (UUID) inside the chosen scope |

`KnowledgeDashboard` (every count an integer, every rate a number 0..1 or null):

```yaml
period:    { from: date, to: date }
options:
  departments: [{ departmentId: uuid, name: string }]
  projects:    [{ projectId: uuid, name: string, departmentId: uuid }]   # name = Jira project key
outcomes:
  approved, rejected, changesRequested, approvalRate, pendingReview, expired
  perDay:     [{ date, approved, rejected, changesRequested }]           # every day of the period
  perProject: [{ projectId, name, approved, rejected, changesRequested }]
coverage:
  publishedTickets, allTickets, publishedShare
  perDepartment: [{ departmentId, name, publishedTickets, allTickets, publishedShare }]
feedback:
  helpful, notHelpful, helpfulRate
  topRated, lowestRated: [{ ticketId: uuid, title, helpful, notHelpful, helpfulRate }]   # at most 5 each
gaps:
  noResultSearches:  [{ query, count }]      # at most 10, most frequent first
  chatbotNoSource:   count
  chatbotQuestions:  [{ question, count }]   # at most 10, most frequent first
suggestions:
  posted, solved, solvedShare
```

Errors: 400 MSG39 (date range), 400 MSG06 (malformed id), 401 MSG07, 403 MSG08, 404 MSG04.

### Required from other modules
- fe-foundation: session/role guard, same-origin API transport, global session/site errors, list URL state,
  DataTable, loading/error/empty states, catalog and date formatter.
- be-foundation: `TSolveUser`, `Roles.ADMIN`, `@PreAuthorize`, `GlobalExceptionHandler` (MSG06/07/08/39).
- ticket-review and import-ticket-list: `TicketDecisionServiceImpl.decide` and `ImportConfirmServiceImpl.confirm`
  each call `AuditService.record` on success (STORY-002).

## Sources
- SRS v3460 III.9.1 Knowledge Dashboard field table, UC-34, GBR-18; Jira KAN-150, KAN-151 (read 09/11); user
  decisions 09/11 in chat (five groups without ticket views, one endpoint, gaps by the asker's Department, no chart
  dependency).
- Jira KAN-153, read from the authenticated Jira page on 2026-10-31.
- User decisions in this chat: frontend only; MSW with a proposed OpenAPI contract; Actor email text search;
  no default date range, inclusive Vietnam-time boundaries and no future dates.
- SRS v3460, III.9.2 field table: Filters and Log table; later D18/CR-15 overrides the obsolete popup.
- `harnessclaude/artifacts/03-modules/be-foundation/spec.md`, STORY-004.
- `frontend/guidelines/05-react-components.md`, `06-data-layer.md`, `07-ui-and-styling.md`, `10-testing.md`.
- Jira KAN-152 (read 07/11) and its 03/11 comment; `backend/AGENTS.md` §5 Audit; team DDL `database/02_tenant.sql`
  (`audit_log_entry`); user decisions 07/11 in this chat (scope, table, read API, be-foundation note).

## Definition of Done
- [ ] All clarification markers resolved and the current spec approved through sdd.ts.
- [ ] Design and iteration-scoped task list reviewed before implementation.
- [ ] Each acceptance criterion has a behavior test whose name starts with its AC id; module trace passes.
- [ ] MSW tests cover matching/nonmatching filters, paging, errors/retry, Admin access and Workspace scoping.
- [ ] `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test` and `npm run build` pass.
- [ ] A second `npm run generate:api` introduces no further generated-client differences.
- [ ] Browser verification confirms the usable audit log screen and filters.
- [ ] Report the feature, changed files, verification and proposed commit message; wait for the user before committing.
- [ ] STORY-004 and STORY-005: every criterion has a test named with its id (`…_AC_004_1`, `AC-005.1 …`);
      `sdd.ts trace dashboard-audit STORY-004 STORY-005` passes; `mvn verify` and the frontend verify pass; the page
      works with `VITE_API_MOCKING=false` against the real backend.
