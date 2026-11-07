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

## Requirements

### Functional
1. Implement screen 9.2 Audit Log at `/audit-log` for the Admin, using the existing route and UI kit.
2. Show audit entries and combine filters for user (Actor), action and date range, within the current Workspace.
3. Use the existing generated API client pipeline and MSW until the backend implements the proposed contract.
4. Record an audit entry for each successful review decision (approve, reject, request changes) and each successful
   CSV import, in the same transaction as the action (KAN-152; `backend/AGENTS.md` §5).
5. Serve the recorded entries through the contract's `GET /api/v1/audit-events`, Admin only.

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

## Out of Scope
- Frontend changes: the screen of STORY-001 already calls the contract; it reads the backend once mocks are off.
- Auditing user/role, permission, Department, publish/unpublish, delete and Jira configuration changes: no backend
  writes them yet; each adds its `AuditService.record` call when it is built.
- `summary` text: no SRS message defines it, so the backend returns `null`.
- Production tenant isolation beyond "one database per Workspace" (ADR-0002): the read uses the request's database.
- The knowledge dashboard (UC-34), which remains a separate slice of this module.
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

### Required from other modules
- fe-foundation: session/role guard, same-origin API transport, global session/site errors, list URL state,
  DataTable, loading/error/empty states, catalog and date formatter.
- be-foundation: `TSolveUser`, `Roles.ADMIN`, `@PreAuthorize`, `GlobalExceptionHandler` (MSG06/07/08/39).
- ticket-review and import-ticket-list: `TicketDecisionServiceImpl.decide` and `ImportConfirmServiceImpl.confirm`
  each call `AuditService.record` on success (STORY-002).

## Sources
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
