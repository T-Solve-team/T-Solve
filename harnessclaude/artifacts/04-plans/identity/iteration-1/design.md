---
module: identity
date: 2026-10-25
---

# Design: identity — current frontend delivery

Master: artifacts/04-plans/identity/design.md
Spec: artifacts/03-modules/identity/spec.md

## Architecture Overview

Deliver KAN-107–110 sequentially using the existing Admin routes, shared UI kit, generated API hooks and MSW data. This file groups the requested frontend work under the workspace's current iteration; Jira's S2 labels and dates are unchanged.

## Data Model

Use the master plan's user list, detail, mutation, lookup and handover contract areas. Keep actual endpoint/schema additions with the story that consumes them. No production backend changes.

## Key Decisions

Use the approved MSW approach and existing project stack. One issue branch and user-approved feature commit per task, followed by its harness commit. The hook approval command must be run by the user before code can start.

## File Structure

See the master's feature-local outline and the exact paths in tasks.md. Do not pre-create unused components.

## External Dependencies

No new dependencies.

## Edge Cases & Risks

The master plan's numbered risk table applies in full. Tasks 1–4 explicitly map its High-severity cases to verification. Risk verdict: CAUTION, with atomic mock mutations, rejected-operation tests, generated contracts and clearly scoped backend limitations as mitigations.

## Task 3 integration note
User approved the minimal central mock-session adapter for KAN-109/110, requiring FE Lead review. Follow the master's approved bridge note; retain unchanged session fixtures and keep the adapter implementation feature-local. No other shared/app edit is authorized by this note.

## KAN-111 preparation — draft

Master: artifacts/04-plans/identity/design.md, "KAN-111 preparation — 2026-10-29".
Spec: artifacts/03-modules/identity/spec.md, STORY-008 / AC-008.1–008.9.

The user selected preparing the scope/spec now and waiting for KAN-109/110 to merge into main before code. This is preparation in the current plan folder, not a change to Jira sprint dates. Reuse the included Project handover and its existing popup/mutation contracts; no standalone screen or endpoint. Reconcile the merged code against the master's risk table, implement only gaps, and retain existing Space behavior. Approval of the new spec and merged KAN-109 permit the first caller slice (user continuation request, 2026-10-30); the KAN-110 caller and final completion wait for KAN-110 on main. Full frontend verification, browser inspection and a user commit checkpoint apply; KAN-112 remains separate.

## KAN-112 — Task 20: Space handover

Master: artifacts/04-plans/identity/design.md, "KAN-112 — Space handover — 2026-10-31".
Spec: artifacts/03-modules/identity/spec.md, STORY-009 / AC-009.1–AC-009.9.

User approved the master design and placement in iteration 1 on 2026-10-31 after approving the spec. Reuse UC-06/07 caller flows, the shared handover popup, existing generated models and atomic mutable MSW helpers. Expand the existing OpenAPI proposal's descriptions; no new endpoint, model, dependency or backend implementation. Exercise master risk rows S1–S8: eligibility and stale choices, exactly the affected Space set, multi-Space/mixed-Project atomicity, cancellation/failure/retry, loading/refetch/pending controls and retained history. Task 20 owns implementation, browser evidence, trace and the required frontend checks. Runtime changes require a demonstrated gap; commit and push remain separate user checkpoints.

## STORY-003 My Profile — KAN-160 (2026-10-31)

Master: artifacts/04-plans/identity/design.md, "STORY-003: My Profile — KAN-160 plan, 2026-10-31".
Spec: artifacts/03-modules/identity/spec.md, STORY-003 / AC-003.1–003.14 and AC-002.7.

Tasks 15–18: the web app on MSW mocks (D1), stacked on `chore/KAN-231-identity-plan` (PR #94). Two new endpoints
under tag `profile` (`getMyViewableSpaces`, `getMySolutions`), each in its screen's PR; feature-local mocks read the
dev user with `getDevUser()`. No shared edit: in My Solutions only the Ticket ID of a `canOpen` row is a link
(`cells.link`, R1 A). Edge-case rows 53–81 of the master apply. Task 19 (backend
AC-003.4–.10) is a stub with no feature until it has a Jira issue.


## KAN-107 backend on the SQL Workspace path — draft, 03/11/2026

Spec: `artifacts/03-modules/identity/spec.md`, STORY-004 AC-004.9–.19.
This amendment supersedes FE-only assumptions for User List reads only. Existing FE completion
does not count as backend completion. Master: `artifacts/04-plans/identity/design.md`.

### Architecture Overview

GET `/api/v1/users` and GET `/api/v1/users/departments` use controller → service interface →
service implementation → read repository. Use the qualified SQL tenant JDBC/transaction manager
from the platform amendment; never inject the legacy default JdbcTemplate/DataSource. This is a
bounded exception to the old JPA-only repository convention: parameterized read projections need
no writable entities, duplicate Account store or mapping of all 23 tables. No native SQL string may
include request text; sort fields/directions come from a fixed allow-list.

A trusted authenticated principal carries immutable Long workspaceId and accountId. It is created
only by upstream sign-in, not from query parameters/headers. The User List security path rejects an
unrecognized/missing principal or a principal from a different host Workspace with 401 MSG07,
then reloads the account and current roles inside the bound database. Missing/deactivated account
gets 401 MSG07; a current non-Admin gets 403 MSG08. Endpoint `@PreAuthorize` checks ADMIN after
these current authorities are loaded, so stale roles cannot grant access or prevent a newly granted
role. A dedicated read-path security matcher avoids changing existing auth/Jira/mutation chains.
The existing UUID-only draft sign-in port is not a numeric Workspace binding and is not accepted.

No OIDC callback/session issuance is built here. Tests inject a trusted Spring Security session
containing the same production principal type; they do not add a dev login, production mock or
header-based identity. Real sign-in integration remains the identity session task. KAN-117 is not
silently merged or duplicated: reuse existing reviewed helpers when available, and keep this read
chain restricted to its two endpoints.

### Data Model

Read `app_user(user_id, department_id, full_name, company_email, account_status)`,
`user_role(user_id, role)` and `department(department_id, name, status)` exactly from SQL.
Employee is implicit; materialize it only in the response. Fetch distinct users/count first using
EXISTS/NOT EXISTS role predicates, then fetch roles for the selected page in one bounded query;
do not page a join that duplicates a multi-role account or run one query per user. Use a read-only
REPEATABLE READ tenant transaction for account authorization, count, page and roles.

New list-only response schema `UserListItem`: id, fullName, email, roles, nullable department and
status. `UserAccountPage.content` uses it; no lastSignInAt property is required or synthesized.
Use a list-specific Department reference with decimal-string id, preserving the existing shared
UUID contracts of unported screens. Do not globally migrate project, ticket or session ids.
The generated client keeps string ids. User Detail must allow a numeric list link through its
route validation, but its backend stays a separate task. Mock list data must comply with the new
list contract; any mock/detail compatibility mapping stays in the mock boundary, never production.

### Key Decisions

| Decision | Choice | Alternatives considered | Rationale |
|----------|--------|-------------------------|-----------|
| IDs | Positive decimal strings on API, Long in Java, BIGINT in SQL | JSON numbers; UUID remapping | User decision; preserve integers above 2^53 |
| Last sign-in | Remove list column, payload field and sort | New storage column | User decision; keep SQL unchanged |
| Filters | Literal case-insensitive name/email substring; AND filters; EMPLOYEE-only means no explicit roles | Wildcard search; any Employee membership | Existing contract plus implicit Employee in SQL |
| Sort | fullName/email only, default name asc, numeric user_id tie-breaker | Last sign-in; dynamic column expressions | Available SQL data and stable pagination |
| Departments | Include retained inactive options, sort name/id | Active-only | Existing user assignments remain filterable |
| Scope | Read API and necessary numeric links; unported write/detail contracts unchanged | Convert every identity screen/store | Separate user-authorized backend tasks |

Malformed filters/page/sort return 400 MSG06. Search over 100 trimmed characters returns 400 MSG02
with Search/max_length=100 field metadata. A nonexistent valid Department yields an empty page.
Accept decimal ids only through Long.MAX_VALUE. Percent, underscore and backslash in q are literal.
Do not add an audit write to these read-only endpoints. On the FE normalize obsolete lastSignInAt
URL sorts before fetching; keep URL-backed filters, retry and MSG04 empty-state behavior.

### File Structure

- `backend/src/main/java/vn/tsolve/identity/{controller,dto,service,service/impl,repository}/`: User List layers.
- `backend/src/main/java/vn/tsolve/identity/session/`: numeric trusted principal and read-session validation.
- Tests mirror the above; PostgreSQL IT covers real host routing, permissions and cross-Workspace sessions.
- `frontend/openapi/tsolve-api.yaml`: list-only contracts and allowed parameters; regenerate clients.
- `frontend/src/features/users/`: UserListPage, URL normalization, list mocks/tests and numeric detail-link handling.

### External Dependencies

Platform SQL Workspace foundation must pass first. Reuse Spring Security, Spring JDBC, existing
ProblemDetails, generated API client, shared UI and current testing tools. No new package.

### Edge Cases & Risks

All 12 dimensions reviewed. Mutations/deletion/retention and third-party webhook replays are outside
read scope; UI kit responsiveness/accessibility remain existing checks. Applicable scenarios:

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| U1 | STORY-004 | User types/auth | Anonymous, stale Admin, deactivated user, foreign-Workspace session | Critical | 401/403 as spec; no returned user data | SQL-U2/U4 |
| U2 | STORY-004 | Input extremes | 101 chars, unicode, SQL text, percent/underscore/backslash, BIGINT max/overflow | High | Parameterized literal search; precision retained; invalid input 400 | SQL-U1/U3/U4 |
| U3 | STORY-004 | Scale/integrity | Multi-role users across page boundary, equal names/emails, empty/high page | High | Unique rows, correct count, stable id tie-break | SQL-U3/U4 |
| U4 | STORY-004 | Integration/state | lastSignInAt URL, numeric link, unported detail API | High | Normalize sort; preserve id; no claim of detail BE completion | SQL-U1/U5 |
| U5 | STORY-004 | Timing | Role/status changes; page rows change between count and fetch | High | Re-read authorization each request; consistent read snapshot | SQL-U2/U3/U4 |
| U6 | STORY-004 | Error cascades | Tenant DB unavailable; filter/role lookup fails | High | Coded error, retry UI; never fallback DB | SQL-U4/U5 |
| U7 | STORY-004 | Business logic | Employee-only; absent Department; inactive Department; no match | High | SQL-derived role semantics, nullable reference, retained filters, empty page | SQL-U3/U4/U5 |
| U8 | STORY-004 | Privacy/environment | Logs on query failure, large ids in browser | High | No query values/user data logged; no Number conversion | SQL-U4/U5 |

### Risk Review — 03/11/2026 — Verdict: CAUTION

| Persona | Independent finding | Resolution |
|---------|---------------------|------------|
| Architect | Duplicate UUID accounts and SQL app_user would diverge | Read SQL directly; no second persistent Account model |
| Security | Snapshot authorities or a bare numeric account id allow wrong-tenant access | Trusted Workspace principal plus current SQL account/roles |
| Performance | Paging role joins duplicates users; per-row roles makes N+1 | EXISTS predicates and one bounded page-role query |
| User | Removing sort but retaining old URL gives repeated errors | Normalize obsolete URL sorts and verify numeric list links |
| Devil's advocate | Mock success could mask missing production sign-in | Full HTTP/database IT; explicitly separate sign-in acceptance |

Agreement: tenant-bound authorization and real SQL evidence are mandatory. Conflict: migrating all
shared UUID contracts versus preserving other owners' screens; list-specific DTOs minimize scope.
Mitigations map to SQL-U1–U5. No production sign-in or User Detail completion is claimed.

### Builder Check

No new user story is needed: this expands STORY-004. Contracts, error cases, SQL ownership and
dependency order are explicit. Class naming and query assembly remain implementation details.
The feature checkpoint follows full backend/frontend verify, browser check and trace STORY-004.
