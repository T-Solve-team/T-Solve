# Design: project-integration

Spec: `artifacts/03-modules/project-integration/spec.md` · Architecture: `artifacts/03-modules/architecture.md`

Scope today: KAN-128 (UC-15) only. KAN-125/126/127/129 extend this design in Change Mode.

## Architecture overview

```
ProjectDetailPage (3.2) ──► GET  /api/v1/projects/{id}                    ProjectController
  MembersTab           ──► GET  /api/v1/projects/{id}/members                │
  AddMemberModal       ──► GET  /api/v1/projects/{id}/member-candidates?q=   ▼
                       ──► POST /api/v1/projects/{id}/members        ProjectMemberService
                                                                           │  (manager check, validation, tx)
                                                         ProjectMemberRepository (JdbcTemplate)
                                                         project, project_membership, app_user, user_role, department
                                                                           │
                                                                    AuditService (same tx)
```

Backend package `vn.tsolve.project` (confirmed: it already holds `Project`, `ProjectRepository`, `ProjectService`). Layering follows `backend/AGENTS.md`: `controller/`, `dto/`, `service/` + `service/impl/`, `repository/`.

## Data model

No new table. Reads and writes the existing tables:

| Table | Use |
|-------|-----|
| `project` (002) | `public_id`, `jira_project_key`, `department_id`, `status` |
| `department` (workspace) | `name` for the header |
| `project_membership` (017, 023) | rows `(user_id, project_id = project.public_id, role_in_project, date_added)`; insert `MEMBER` rows |
| `app_user`, `user_role` (identity) | status `ACTIVE`, full name, email, role `TICKET_RESOLVER` / `PROJECT_MANAGER` |
| `audit_log_entry` | new action `PROJECT_MEMBER_CHANGE` |

Migration `034-project-audit-member-change.sql` (changeset `project-integration:034-project-audit-member-change`): drop and re-add `ck_audit_log_entry_action` with `PROJECT_MEMBER_CHANGE` appended, rollback to the 027 list — same shape as `027-workspace-audit-department-change.sql`. Register it in the master changelog the way 027/028 are.

## Key technical decisions

| # | Decision | Chosen | Alternatives | Rationale |
|---|----------|--------|--------------|-----------|
| D1 | Scope | KAN-128 only, before KAN-127 | whole module; KAN-127 first | User, 13/11 |
| D2 | Data access | `NamedParameterJdbcTemplate` repository in `project` | JPA entity for membership | `review` already maps `ProjectMembership` read-only; identity uses JDBC for these joins (`ScopeAccessRepository`). A second JPA entity on the same table would split ownership. |
| D3 | Manager check | One query in the service: caller is Active, holds `PROJECT_MANAGER` role, and has a `PROJECT_MANAGER` membership row for the Project; plus `@PreAuthorize("hasRole(PROJECT_MANAGER)")` on the controller | reuse `ScopeAccessService.project` | Scope access answers "may see", not "manages"; a `MEMBER` would pass it. |
| D4 | 404 vs 403 order | Project lookup first (404 when unknown), then the manager check (403) | 403 for both | Spec AC-001.4; the id is an opaque UUID so existence leaks nothing useful. |
| D5 | Add semantics | All-or-nothing: validate every id in one query, then batch insert, then audit — one `@Transactional`. A concurrent duplicate insert (PK violation) maps to 409. | partial add; `ON CONFLICT DO NOTHING` | Spec AC-002.7, NFR 1, NFR 3. |
| D6 | Audit | One entry per request, details `{"added": [ids]}` | one entry per member | "records the action" (UC-15 step 7) is one action. |
| D7 | Caller id | `TSolveUser.current()` (`common/security/TSolveUser.java`, as `AuditServiceImpl` uses it); call `TenantScope.currentWorkspaceId()` first like `DepartmentServiceImpl` so a request without a bound Workspace fails the same way | — | No new mechanism (builder check 3). |
| D8 | Candidate cap | 20 results, `q` ≤ 100 chars, `LIKE` with `%`/`_`/`\` escaped, `lower()` on both sides | paging | A popup search, not a list screen. |
| D9 | Errors | Existing global error handling (`ProblemDetail` / exceptions already mapped to 400/403/404/409 in `common`); reuse its exception types | new exception types | No new mechanism. |
| D10 | Frontend data | Orval-generated hooks from `frontend/openapi/tsolve-api.yaml` (pulled with `frontend/scripts/pull-api.mjs`), React Query, MSW handlers in `features/jira-integration/mocks/handlers.ts` | hand-written fetch | `frontend/AGENTS.md`. |
| D11 | Search debounce | 300 ms, request only while the popup is open | search on Enter | AC-002.10. |

## File structure plan

Backend (`backend/src/main/java/vn/tsolve/project/`):
- `controller/ProjectController.java` — the four endpoints, OpenAPI annotations (`@Tag(name = "projects")`, `operationId`s: `getProject`, `listProjectMembers`, `searchProjectMemberCandidates`, `addProjectMembers`).
- `dto/ProjectSummaryResponse.java`, `ProjectMemberResponse.java`, `MemberCandidateResponse.java`, `AddProjectMembersRequest.java` (records; Bean Validation `@NotNull @Size(min=1,max=50)` on `userIds`).
- `service/ProjectMemberService.java` + `service/impl/ProjectMemberServiceImpl.java`.
- `repository/ProjectMemberRepository.java` (JDBC).
- `common/audit/AuditAction.java` — add `PROJECT_MEMBER_CHANGE`.
- `resources/db/changelog/changes/034-project-audit-member-change.sql` (+ master changelog entry).

Backend tests (`backend/src/test/java/vn/tsolve/project/`): unit test of the service (validation, all-or-nothing, audit) and an IT (`*IT.java`, Testcontainers, same base as `UserListIT` / workspace ITs) covering every backend AC with names starting with the AC id.

Frontend (`frontend/src/features/jira-integration/`):
- `pages/ProjectDetailPage.tsx` — header + Members tab (replaces the placeholder).
- `components/ProjectHeader.tsx`, `components/MembersTable.tsx`, `components/AddMemberModal.tsx` (+ `.test.tsx` per component, names starting with the AC id).
- `mocks/handlers.ts`, `mocks/data.ts` — MSW for the four endpoints.
- `frontend/src/features/audit-log/hooks/audit-filters.ts` (+ its mocks/tests if they enumerate actions) — label `PROJECT_MEMBER_CHANGE: 'Project member change'`.
- `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` — regenerated.

## External dependencies

None new.

## Edge Cases & Risks

| # | Case | Severity | Handling | Test |
|---|------|----------|----------|------|
| E1 | A `MEMBER` (Ticket Resolver) of the Project calls the endpoints | High | 403 (D3) | AC-001.3 |
| E2 | A Project Manager of another Project in the same Department | High | 403 | AC-001.3 |
| E3 | Caller's account deactivated or PM role removed but membership row left | High | 403 | AC-001.3 |
| E4 | Same user posted twice in one request | Medium | 400 | AC-002.6 |
| E5 | Two PMs/tabs add the same person at once | Medium | PK violation → 409, nothing from the losing request | AC-002.7 (service test simulating `DuplicateKeyException`) |
| E6 | Candidate becomes inactive between search and add | Medium | 409 all-or-nothing | AC-002.7 |
| E7 | Adding a `PROJECT_MANAGER`-only account (no TR role) | Medium | not a candidate; 409 on POST | AC-002.1, AC-002.7 |
| E8 | `q` with `%` or `_` | Low | escaped, matched literally | AC-002.1 |
| E9 | Project `REMOVED` | Medium | POST 409, button hidden | AC-002.8, AC-002.9 |
| E10 | Department of the Project deleted | Low | Not specified by UC-15; reads still work for the PM — no special case (the Project is Removed then per UC-12, so E9 applies) | — |
| E11 | Unknown `projectId` / malformed UUID | Low | 404 / 400 (framework) | AC-001.4 |

## Decided in planning
- `project_membership` `MEMBER` rows are now written by `project` (UC-15) as well as identity (role removal, `UserRoleUpdateRepository.removeMembers`). (builder check 10)
- The eligibility query locks the candidate `app_user` rows `FOR SHARE` so identity's concurrent role removal (`FOR UPDATE`) cannot interleave. (builder check 9)
- `AddProjectMembersRequest.userIds` is `List<@NotNull UUID>` with `@NotNull @Size(min = 1, max = 50)`; duplicates are checked in the service and thrown as the existing 400 exception type. 409s use `BusinessRuleException` (MSG06). (builder check 5, 6)
- Header and members are read with JDBC joins; the JPA `Project` entity is left as is. The header ignores `department.status`. (builder check 12)
- `date_added` uses the column default `now()`; the 201 has no `Location` header. (builder check 13)
- 404 is tested with a Project Manager caller; a caller without the role gets 403 from `@PreAuthorize` first. (builder check 7)
- Deactivated caller (AC-001.3): if the session filter already rejects such a caller, the IT covers the role/membership cases and a service-level test covers the inactive-account branch of the manager query. (builder check 8)
- `triage` `ManagedProjects` / `NoManagedProjects` are left alone. (builder check 11)
- OpenAPI: pull with `frontend/scripts/pull-api.mjs` from the running backend; if it cannot run here, hand-edit `tsolve-api.yaml` to exactly what springdoc emits for the new controller. (builder check 19)
- After a failed add the popup refetches the candidate list; the selection is kept (AC-002.14). (builder check 18)
- Endpoints live under `/api/v1/projects/{projectId}` (matches `/api/v1/departments`).
- The header endpoint `GET /api/v1/projects/{id}` is built now, minimal; KAN-127 extends it with settings.

## KAN-129: Remove a member (STORY-003, Change Mode 14/11)

Branch `feat/KAN-129-project-integration-remove-member`, stacked on `feat/KAN-128-project-integration-add-ticket-resolver` (user, 14/11).

| # | Decision | Chosen | Alternatives | Rationale |
|---|----------|--------|--------------|-----------|
| D12 | Scope of the Solution Form rule | Only delete the `MEMBER` row; the spec's contract tells `jira-app` to check `project_membership` | add a membership port; edit the Solution Form (PR #203) | User, 14/11. The Solution Form is KAN-126, another person's open PR. |
| D13 | Endpoint | `DELETE /api/v1/projects/{projectId}/members/{userId}` → 200 with the member list, `operationId` `removeProjectMember` | 204; `POST …/members/remove` | Mirrors the add, which answers the list, so the table refreshes from one response. |
| D14 | Delete | One `DELETE … WHERE project_id = :p AND user_id = :u AND role_in_project = 'MEMBER'`; 0 rows → 409 (`BusinessRuleException` MSG06) | read then delete | One statement covers "not a member", "already removed by a concurrent request" and "the PM row" without a race. |
| D15 | Removed Project | 409 before the delete, like D5 | allow | Same rule as AC-002.8. |
| D16 | Audit | One entry, details `{"removed": [userId]}`; same action `PROJECT_MEMBER_CHANGE` (no migration) | new action | The label "Project member change" fits both. |
| D17 | Tickets kept | No code: tickets and Solutions reference the Project and the author's `app_user`, never `project_membership`. An IT proves a ticket by the removed member is unchanged. | — | AC-003.5. |
| D18 | Confirm popup | `ConfirmDialog` from `@/shared/ui` with MSG55 from the message catalog, as `DraftsList` and `ReviewQueuePage` do | `Modal.confirm`; a new modal | Existing kit component. |

Edge cases: E12 removing the PM row → 409 (AC-003.3); E13 two removes of the same member at once → the second deletes 0 rows → 409; E14 a member whose TICKET_RESOLVER role identity removes at the same time → either side deletes the row, the other 0 rows (409 or no-op in identity); E15 a member without a full name → MSG55 uses the email.

---

# KAN-127 design (STORY-004..010)

Spec: `artifacts/03-modules/project-integration/spec.md` (Deep). Scope: KAN-127, UC-16, STORY-004–010. Active plan: `artifacts/04-plans/project-integration/iteration-1/`. Evidence: that folder's `kan-127-scout.md` and `kan-127-decisions.md`. Decisions supersede the scout; D38 supersedes D26, D36 supersedes D20. This is a planning handoff; approvals and status changes are separate work.

## KAN-127: Architecture Overview

Controller → `ProjectService` interface → `ProjectServiceImpl` → new JDBC `ProjectLinkRepository`. Extend the existing service with `testConnection`, `create`, and `list`; retain `findActivePublicIdByJiraProjectKey` and its JPA lookup. The new paths return records, never entities. `Project` and `ProjectRepository` stay unchanged. No MapStruct mapper is needed because these paths project SQL directly into records.

Every new operation calls `TenantScope.currentWorkspaceId()` before its first repository access. No binding throws and the existing global handler answers 500 MSG06. Obtain the caller id from `TSolveUser.current()`; resolve the caller's own Active Department from SQL, also checking account status and current role. No Department/Workspace request parameter exists. All three controller methods declare `@PreAuthorize("hasRole('" + Roles.PROJECT_MANAGER + "')")`. `@Valid` resolves before method security: an invalid body from a non-PM can get 400; a valid one gets 403. Authentication still returns 401 MSG07 first.

```text
POST /api/v1/projects/connection-test              operationId testProjectConnection
  ProjectController @Valid, @PreAuthorize PM
    ProjectService.testConnection(request)         no transaction, no writes
      scope.currentWorkspaceId(); caller id
      ProjectLinkRepository.findCallerDepartment   absent/ineligible → 403 MSG08
      validate/normalise key; findLink              local conflict → 409 MSG49; no Jira
      TrackerProjectService.findProject(key)       only tracker service/dto boundary
        NOT_FOUND/DENIED → 409 MSG48; UNAVAILABLE → 503 MSG06
      check canonical-key link too, if different
      prefill new or reconnect settings
      200 ProjectConnectionTestResponse             no new id for a new Project
```

```text
POST /api/v1/projects                              operationId createProject
  ProjectController @Valid, @PreAuthorize PM
    ProjectService.create(request)                 no outer @Transactional
      scope.currentWorkspaceId(); caller id
      normalise; eligible own Department; key format; range and blank ids; link check
      TrackerProjectService.findProject(key)       always re-read; both HTTP calls outside tx
      canonical-key link check; validate selected ids against fresh statuses
      TransactionTemplate.execute                  ONE write transaction
        re-read eligible caller Department (no lock)
        lock relevant Project rows; recheck requested AND canonical links
        insert new OR conditional reconnect        loser → 409 MSG49
        membership insert OR replacement
        AuditService.record                        same JDBC connection/transaction
        build saved ProjectResponse
      201 new / 200 reconnect                       only after successful commit
```

```text
GET /api/v1/projects                               operationId listProjects
  ProjectController @PreAuthorize PM
    ProjectService.list()
      scope.currentWorkspaceId(); caller id
      findCallerDepartment                         absent/ineligible → 403 MSG08
      listProjects(own internal Department id)      no Jira; no writes
      200 ProjectListResponse(department, rows)     ordered key ASC, unpaged
```

Create body sketch (planned helpers and repository operations below, not existing code):

```java
scope.currentWorkspaceId();
UUID caller = signedInCallerId();
String key = normaliseKey(request.jiraProjectKey()); // body blank/length checked by @Valid
var department = requireCallerDepartment(caller);
requireKeyFormat(key);
int range = requireWholeRange(request.reviewRange());              // MSG44, before Jira (AC-005.21)
var requested = requireNoBlankAndDeduplicate(request.doneStatusIds()); // MSG01, before Jira
var initialLink = checkRequestedLink(key, department, caller);
var jira = requireFound(trackerProjectService.findProject(key)); // outside tx
checkCanonicalLink(key, jira.key(), initialLink, department, caller); // AC-004.19, 004.21, 004.22
var ids = requireKnownIds(requested, jira.statuses());              // MSG01 after Jira (AC-005.5)
return writeTransaction.execute(tx -> {
    var currentDepartment = requireCallerDepartment(caller);
    // Re-read requested and canonical links; lock a found Project row FOR UPDATE.
    var link = recheckLinksForWrite(key, jira.key(), currentDepartment, caller);
    var saved = link.isNew()
            ? insertOrConflict(currentDepartment, jira.key(), ids, range)
            : reconnectOrConflict(link, jira.key(), ids, range);
    maintainManagerMembership(saved, link, caller);
    audit.record(AuditAction.TRACKER_CONFIGURATION_CHANGE, "PROJECT", saved.projectId(),
            auditDetails(link.isNew() ? "CREATE" : "RECONNECT", saved));
    return new ProjectSaveResult(saved, link.isNew());
});
```

`ProjectSaveResult(ProjectResponse project, boolean created)` is an internal service result; the controller uses `created` to select 201/200 and serializes only `project`. Success bodies contain no MSG code. Bind `TransactionTemplate` to the existing application transaction manager; JDBC and `AuditService` must share that transaction. Never put an outer transaction on either Jira-using service method; test actual transaction activity at the tracker boundary. Avoid class-level `@Transactional` on `ProjectServiceImpl`; the existing JPA lookup can retain its method annotation.

Validation: `ProjectConnectionTestRequest(String jiraProjectKey)` and `CreateProjectRequest(String jiraProjectKey, List<String> doneStatusIds, BigDecimal reviewRange)`. Key annotations are `@NotBlank(message="MSG01") @Size(max=20, message="MSG02")`; selected list has `@NotEmpty(message="MSG01")`; range has `@NotNull(message="MSG01")`. Use the service's coded validation exception for fractional, <1 or >2147483647 numeric values, preserving `reviewRange` as the field and empty params for MSG44. No rounding or truncation is allowed. Validate null/blank elements before constructing sets. Blank key wins over length (including 21 spaces) through the shared handler's `FIELD_CODE_PRECEDENCE` (MSG01 before MSG02, one error per field); no group sequence (D60).

Normalize key with `strip()` then `toUpperCase(Locale.ROOT)` after checking original UTF-16 length. Check `[A-Z][A-Z0-9_]*` after scope and eligibility. Invalid format → `BusinessRuleException("MSG48", Map.of("tracker_name", "Jira"))`. Linked key → `BusinessRuleException("MSG49", params, "jiraProjectKey")`, with owning Department's name. Use `project/exception/ProjectAccessDeniedException` (403 MSG08) and `ProjectTrackerUnavailableException` (503 MSG06), each extending `MessageCodeException`, and the existing `common/exception/FieldMessageCodeException` for service-side 400 field errors (MSG44, MSG01 on `doneStatusIds`), like `identity/exception/ScopeAccessDeniedException`. Unexpected storage/unbound errors use the existing 500 handler. No module `@ExceptionHandler`, no shared exception edit. Jackson scalar coercion stays as in D51; do not promise rejection of numeric keys or numeric strings.

Link eligibility: Active in any Department is refused. Removed in another Department is refused. For a Removed row in the caller's Department, protect another previous manager only if that account is Active, still holds the PM role and still belongs to that Department. The previous manager is identified by its membership even if deactivated. Same caller → membership rows stay unchanged. Replacement → delete the previous PM row, then upsert the caller, preserving other MEMBER rows. Prefill uses stored exact ids still present in Jira, otherwise Done-category ids; stored range is used even if legacy-invalid, with NULL alone falling back to 3. Empty Jira statuses are allowed for testing but cannot produce a valid Save.

Canonical aliases: inspect both requested and returned keys. If they resolve to the same row, treat it once. If just one resolves, use that row after eligibility checks; if two distinct rows resolve, do not combine or delete Projects: return MSG49 naming the canonical row's Department (D59). The canonical key is stored for creation and reconnect. Keep `public_id`, Department, tickets and `field_mapping` on reconnect.

**Web app:** `JiraIntegrationPage` keeps the existing placeholder for Admin-only sessions. Render a separate `DepartmentProjects` component only when `useSession().hasRole('PROJECT_MANAGER')`; its query therefore never mounts for Admin-only. Existing routes already guard other roles. `DepartmentProjects` reads the Department from `GET /projects`, shows the read-only Department field and `ProjectsTable`, and opens `AddProjectModal`. `LoadingState`, `ErrorState` with query retry, `EmptyState`/MSG04 and `StatusTag` are existing kit components. Do not add paging or client sorting. Use `projectId` as row key; null manager shows MSG54 via `msg`.

`AddProjectModal` follows `RejectTicketModal`: typed generated request shape, `Form.useForm`, `FormField`, `FormModal`, `submitting` ref, `applyApiErrors`, and owned error handling. `FormModal` already renders `ModalTitle`. Its primary action initially has `okText="Test connection"`; after a successful test it has `okText="Save"`. There is no separate re-test button: editing the key hides the settings and brings back Test connection (AC-009.8). Only mount settings fields when tested, so key-only Test does not validate hidden settings. The tested response stays in the mutation's result cache; clear/reset it on an idle key edit, dismissal or new opening. Form state holds editable settings only. Guard submit and cancellation synchronously with the same ref for both mutations; disable all fields/buttons while either is pending. `onCancel` handles mask, Escape, close icon and Cancel; the guard ignores them while busy. No kit API change is required.

Use `rules.required`, `rules.maxLength` plus `count` (no native maxLength). The browser's key length check uses the original JS string's `.length` (UTF-16), with blank checked first; do not rely on antd's Unicode length semantics. `rules.wholeNumberMin(1)` plus a feature-local validator for the INT upper bound produces MSG44 without changing `shared/forms`. Keep InputNumber precision unrestricted so fractional entry is refused, not silently rounded. On Test success show MSG47; on Save success show MSG05, invalidate generated `getListProjectsQueryKey()` and close. These generated symbols are expected from the specified operationIds and must be checked after generation. Hooks set `meta: { handlesOwnErrors: true }`. Field errors go through `applyApiErrors`; MSG48 without fields is an inline Alert; other non-field errors go through `showMessage(error.code, error.params)`, with shared 401/site handling retained. Failure retains values and prior tested settings. Save sends the tested canonical key.

MSW uses only `features/jira-integration/mocks/handlers.ts`: seed IT Operations, an Active OPS Project, a Removed OLD of that Department (same PM for the basic reconnect), and a separate foreign Active SUP conflict. `MISSING` is the explicit MSG48 key. Other valid unknown keys can test/create. The list includes only own Department rows; the conflict store includes SUP. Test returns statuses including a Done-category and non-Done choice; Save revalidates, updates that local store and returns 201/200, so invalidation visibly refreshes the table. `resetMockData()` deep-resets list, settings and membership counts for independent tests. No import of another feature's mock internals; registration and reset are already wired.

## KAN-127: Data Model

No new table. `project`: existing BIGSERIAL `project_id`, UUID `public_id`, BIGINT Department FK, unique Jira key across all statuses. Only `done_status` changes type. `department`, `app_user`, `user_role` are read; `project_membership` and `audit_log_entry` are written. Membership's Project FK is UUID `project.public_id`, not BIGINT `project_id`. Department ids in HTTP/audit are `department.public_id`. `field_mapping` is NULL on new rows and preserved on reconnect. No `default_project_id` update.

Create `backend/src/main/resources/db/changelog/changes/036-project-done-statuses.sql` in full:

```sql
--liquibase formatted sql

--changeset project:036-project-done-statuses
ALTER TABLE project ALTER COLUMN done_status TYPE JSONB USING CASE WHEN
  done_status IS NULL THEN NULL ELSE jsonb_build_array(done_status) END;

--rollback ALTER TABLE project ALTER COLUMN done_status TYPE VARCHAR(100) USING done_status ->> 0;
```

Append LAST before `</databaseChangeLog>` in `backend/src/main/resources/db/changelog/db.changelog-master.xml`, currently after `026-identity-app-user-keycloak-sub.sql`:

```xml
<include file="changes/036-project-done-statuses.sql" relativeToChangelogFile="true"/>
```

No CHECK/default/backfill on `review_range`: `review/SendBackApiIT` deliberately stores 0 and -1. Migration preserves every non-null legacy scalar as one string, even JSON-looking text; SQL NULL remains NULL. Rollback keeps only element zero and is lossy for later multi-status settings: deploy forward in normal operation; test rollback on a scratch schema. Rollback also turns `[]`, `[null]` and non-array values into SQL NULL and fails on an element longer than 100 characters; T-Solve's own writes never produce these. The DDL owner must update the `done_status` line to JSONB in `SEP490_G89-TaiLieu/SEP490_G89/Notes/DDL_01-11/02_tenant.sql`; this run must not edit that external document.

`ProjectLinkRepository` uses `NamedParameterJdbcTemplate`, constructor injection and named parameters. Planned method SQL follows. Record projections are nested in this repository for storage-only ids; SQL is the authority for columns. No cross-module repository import.

```sql
-- findCallerDepartment(:callerId) → Optional<CallerDepartment(internalId, publicId, name)>
SELECT d.department_id, d.public_id, d.name
FROM app_user u JOIN department d ON d.department_id = u.department_id
JOIN user_role r ON r.user_id = u.id AND r.role = 'PROJECT_MANAGER'
WHERE u.id = :callerId AND u.status = 'ACTIVE' AND d.status = 'ACTIVE';
-- recheck inside the write tx: the same SELECT (no lock; the window is the transaction itself).

-- findLink(:key) → Optional<Link>; lockLink: append FOR UPDATE OF p inside write tx.
SELECT p.public_id, p.department_id, d.public_id AS department_public_id,
       d.name AS department_name, p.jira_project_key, p.status,
       p.done_status, p.review_range, m.user_id AS manager_id,
       (u.status = 'ACTIVE' AND u.department_id = p.department_id AND EXISTS (
           SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'PROJECT_MANAGER'
       )) AS manager_protected
FROM project p JOIN department d ON d.department_id = p.department_id
LEFT JOIN project_membership m ON m.project_id = p.public_id AND m.role_in_project = 'PROJECT_MANAGER'
LEFT JOIN app_user u ON u.id = m.user_id
WHERE p.jira_project_key = :key;

-- lockProject(:id): lock a discovered row before re-reading its membership/link projection.
SELECT public_id FROM project WHERE public_id = :id FOR UPDATE;

-- insertProject: :ids is an ObjectMapper-serialized JSON string array; :departmentId is internal BIGINT.
INSERT INTO project (department_id, jira_project_key, done_status, review_range, status, field_mapping)
VALUES (:departmentId, :key, CAST(:ids AS jsonb), :range, 'ACTIVE', NULL)
ON CONFLICT (jira_project_key) DO NOTHING RETURNING public_id;

-- reconnectProject(:id, :ids, :range): conditional transition; 0 rows → MSG49.
UPDATE project SET status='ACTIVE', done_status=CAST(:ids AS jsonb), review_range=:range
WHERE public_id=:id AND status='REMOVED';

-- canonicaliseKey(:id, :canonicalKey), only if needed after successful conditional reconnect.
UPDATE project SET jira_project_key=:canonicalKey WHERE public_id=:id;

-- insertManager / upsertManager; both use this statement after choosing the appropriate path.
INSERT INTO project_membership (user_id, project_id, role_in_project)
VALUES (:callerId, :id, 'PROJECT_MANAGER')
ON CONFLICT (user_id, project_id) DO UPDATE SET role_in_project='PROJECT_MANAGER';

-- deletePreviousManager: replacement only; retain every other member.
DELETE FROM project_membership
WHERE project_id=:id AND user_id=:previousManagerId AND role_in_project='PROJECT_MANAGER';

-- readProject(:id) → ProjectResponse projection (done_status parsed as list, no entity).
SELECT p.public_id, d.public_id AS department_public_id, p.jira_project_key,
       p.done_status, p.review_range, p.status
FROM project p JOIN department d ON d.department_id=p.department_id WHERE p.public_id=:id;

-- listProjects(:departmentId): all members count, active-account PM display only.
SELECT p.public_id, p.jira_project_key, p.status,
       u.id AS manager_id, u.full_name AS manager_name,
       (SELECT count(*) FROM project_membership c WHERE c.project_id=p.public_id) AS member_count
FROM project p
LEFT JOIN project_membership m ON m.project_id=p.public_id AND m.role_in_project='PROJECT_MANAGER'
LEFT JOIN app_user u ON u.id=m.user_id AND u.status='ACTIVE'
WHERE p.department_id=:departmentId AND p.status IN ('ACTIVE','REMOVED')
ORDER BY p.jira_project_key ASC;
```

Lost insert: query the now-committed winning link and raise MSG49 without trying reconnect. Reconnect: lock existing Project row(s), then re-read link/manager state, then conditional update; a second waiter sees ACTIVE and loses. The canonical-key update can still meet a concurrent insert of that key; its unique violation is handled below. Translate only `uq_project_jira_key` violations after transaction rollback to MSG49 by reading the winning Department; never catch them and continue in a failed transaction. Other integrity failures propagate as 500 and roll back.


Audit uses the existing `AuditService.record` SQL, verified in `common/audit/impl/AuditServiceImpl.java`: insert into `audit_log_entry(actor_id, actor_email, action, target_type, target_id, details, created_at)`. Action is `TRACKER_CONFIGURATION_CHANGE`, target `PROJECT`, id public UUID. Details contain operation CREATE/RECONNECT, canonical jiraProjectKey, public departmentId, unique doneStatusIds and integer reviewRange. One success, one audit; all refusals write none. Credential-leak tests concern the Jira service account; the signed-in audit actor is required data.

## KAN-127: Key Decisions

The table below includes the historical choices as well as their explicit supersessions. Full questions and dates remain in `iteration-1/kan-127-decisions.md`.

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| D19: Who does the work? | Codex driven per role | Claude subagents alone; Codex CLI driven by Claude subagents per role | The user asked for it (12/11), same set-up as KAN-120 and KAN-121 D1. Researcher and Planner use Codex as an independent reader, Developer has Codex write the code and supervises and runs the tests, Reviewer uses Codex as a second opinion. Claude verifies every result before a status change |
| D20: Base branch: a Project belongs to a Department, and the Department entity exists only on `feat/KAN-120-workspace-create-department` (pull request #202, open) | from the KAN-120 branch (superseded by D36) | branch from `main` and wait; from the KAN-120 branch; from the KAN-121 branch (#206, stacked on KAN-120) | Adding a Project seemed to need the Department entity. The scout showed there is none (Q15) |
| D21: Untracked `docs/diagrams/` in the checkout (not ours) | leave, never stage | stop and ask; leave it and never stage it | Same as KAN-120 D3 |
| D22: Q1: where the Jira calls (read a project, read its statuses) live | (a) `TicketTrackerClient`/`JiraClient` gain the two reads through a `tracker` Change Mode, exposed to `project` by a `tracker/service` interface | tracker client through a tracker spec change; widen paths to `tracker/client`; own client in `project/client` | One Jira client with one error mapping and logging rule; follows `backend/AGENTS.md` §1; starts KAN-125. Costs a second spec approval (tracker) |
| D23: Q2: credential for the check | (a) `JiraServiceAccount.current()` | tracker's env stand-in (Basic); import's gateway Bearer; unify now | Only credential behind a service interface today; KAN-119 swaps it in one class. Known limit: a real scoped service-account token fails with Basic (RESEARCH-003), flagged to the user |
| D24: Q3: Browse check | (a), defaults from `GET /rest/api/3/project/{key}/statuses` (Done category) | `GET /rest/api/3/project/{key}`; `mypermissions`; both | One call answers "not found" and "cannot browse" and returns the canonical key |
| D25: Q4: Done statuses storage | (a) changeset after the last on `main`, DDL updated | JSONB array of status ids; comma list; one status | SRS says "one or more"; ids survive a rename |
| D26: Q5: review range | App validation plus DB CHECK >= 1 (historical; superseded by D38) | app check + DB `CHECK >= 1`; app only; also NOT NULL DEFAULT 3 | One line in D25's changeset; leaves seeded NULLs alone |
| D27: Q6: API shape | (a) `POST /api/v1/projects/connection-test`, `POST /api/v1/projects` (re-checks before writing), `GET /api/v1/projects` | test + save + list; one POST; no list | SRS has two buttons; access can change between clicks; the list carries the read-only Department |
| D28: Q7: Removed Project of another Department; re-add by someone else while the old PM is active | (a) 409 MSG49 for both | MSG49 both; MSG49 + MSG08; new message | The row stays linked; wording of the second flagged to the SRS author |
| D29: Q8: what a reconnect keeps | (a) | keep id, tickets, members, replace old PM row; demote old PM; keep settings, ignore form | `uq_membership_one_manager` forces the old row out; prefill keeps "its Project settings" |
| D30: Q9: audit action | Reuse TRACKER_CONFIGURATION_CHANGE | reuse `TRACKER_CONFIGURATION_CHANGE`; new `PROJECT_CHANGE`; none | No changeset, no edits outside the module paths |
| D31: Q10: screen 3.1 scope | (a) | Department + Projects table + add form; also connection status and last import; form only | Table shows the result and the Removed row to reconnect; skipped columns have no AC |
| D32: Q11: field mapping | NULL means standard Jira fields | NULL; explicit JSON; editor | Not in the AC, no reader |
| D33: Q12: edit settings after adding (3.2 Settings) | Out of scope: later settings editing story | out of scope; build now; read-only | No story owns it; flagged to the user as a backlog gap |
| D34: Q13: key input | (a) | trim + upper-case + format check, store Jira's key, no Jira id; add id column; store as typed | Matches the DDL and every reader; key rename in Jira is a logged ceiling |
| D35: Q14: failures without an SRS message | (b) | all MSG48; MSG48 but Jira down 503 MSG06; MSG156 | MSG48 is wrong advice when Jira is down; inactive Department gets 403 MSG08 |
| D36: Q15: base branch, revisits D20 | (b) branch recut from `origin/main` before any commit | stay on KAN-120; rebase onto `main` | With D30 nothing is shared with #202 except `tsolve-api.yaml`; the team rule asks for an up-to-date `main` |
| D37: Planning: number of the new changeset. `main` ends at 026; open pull requests hold `027-workspace-*`, `028-workspace-*` (#202, #206), `027-jira-app-solution-draft` (#203) and `027-dashboard-period-indexes` (#178) | `036-project-done-statuses.sql`, author `project`, included last in `db.changelog-master.xml` | 027; 029 | Liquibase ids would not collide with 027, but a fourth `027` confuses readers; 029 is the first number no open branch uses (scout section 3) Superseded on 15/11: after merging main (which reached 034, with 035 reserved for PR #215) the changeset is `036-project-done-statuses.sql`. |
| D38: Planning, revisits D26: `CHECK (review_range >= 1)` breaks `review/SendBackApiIT`, which stores 0 and -1 to test the review-due-date fallback of `TicketRepository.java:39` | no database check: the whole-number ≥ 1 rule is enforced by the `project` API only | keep the check and change the review test and spec; check `NOT VALID`; no database check | `NOT VALID` still checks new inserts. Changing the review module's test and its fallback criterion is another owner's spec for no user-visible gain; every path that writes a range through T-Solve goes through the checked API |
| D39: Planning: how `project` learns the caller's own Department (no identity service returns it; `TSolveUser` carries none) | SQL read in `project`'s repository, by the signed-in user's id | new identity service method; Department on `TSolveUser`; `project` reads `app_user.department_id` joined to `department` with SQL | Both other options edit another module's files (`identity`, `common/security`) for one read. `identity` and `review` already read each other's tables this way (`ScopeAccessRepository`, `ProjectMembershipRepository`) |
| D40: Planning: persistence of the add and reconnect writes | JDBC repository `ProjectLinkRepository`; insert with `ON CONFLICT (jira_project_key) DO NOTHING RETURNING`, reconnect with `UPDATE … WHERE status = 'REMOVED'`; the `Project` entity stays as it is | JPA `Project` entity with `saveAndFlush` and a caught `DataIntegrityViolationException`; `NamedParameterJdbcTemplate` repository | A lost race answers MSG49 without a failed transaction, the membership and list queries are SQL anyway, and the entity maps no changed column so `ddl-auto: validate` passes. Deliberate deviation from `backend/AGENTS.md` §3, as workspace D15 |
| D41: Planning: Save sends Done status ids that are not statuses of the Jira project (statuses changed between the clicks, or a forged request) | Save reads the statuses again and refuses with 400 MSG01 on `doneStatusIds` | store them; refuse them | A stored id that Jira does not have would never open the Solution Form; Save already calls Jira for the Browse check (D27) |
| D42: Planning: review range input rules beyond the SRS | missing or null → 400 MSG01 on `reviewRange`; a number that is not whole, below 1 or above 2,147,483,647 (the `INT` column) → 400 MSG44; a JSON value that is not a number → 400 MSG06 (unreadable body, the team's handler) | integer type (Jackson truncates 2.5 to 2); decimal type with checks | The SRS asks for MSG44 on 2.5; an `Integer` field would silently store 2. "No upper limit" ends at the column's range |
| D43: Planning: HTTP status of each UC-16 refusal | MSG01/MSG02 on `jiraProjectKey` → 400; MSG48 → 409 with no field (inline message), params `tracker_name` = `Jira`; MSG49 → 409 on field `jiraProjectKey`, params `tracker_name`, `department_name`; Jira unavailable → 503 MSG06; caller's Department missing or not Active → 403 MSG08 | — | Team rule: 400 validation, 409 business-rule conflict (`backend/AGENTS.md` §5); MSG48 is an inline message in the SRS, MSG49 a field message |
| D44: Planning: Projects table details (D31) | `GET /api/v1/projects` is unpaged and lists every Project of the caller's own Department (Active and Removed) sorted by Jira project key; Project Manager = the user of the `PROJECT_MANAGER` membership row when that account is Active, else none (the web app shows MSG54); Members = number of membership rows of the Project (the Project Manager counts) | — | A Department has few Projects (as the Departments list, workspace D13); the Removed row must be visible to be reconnected; 3.2 lists the Project Manager as a member |
| D45: Planning: reconnect details (D29) | "previous Project Manager" = the user of the Removed Project's `PROJECT_MANAGER` membership row; "still Active and still in the Department" = account `ACTIVE`, holds the `PROJECT_MANAGER` role and `app_user.department_id` is the Project's Department. When the caller is not that person, the old row is deleted and the caller is upserted as `PROJECT_MANAGER` (`ON CONFLICT (user_id, project_id) DO UPDATE`). Connection-test prefill: stored Done status ids that are still statuses of the project; none left or none stored → the Done-category defaults; stored range, or 3 when NULL | — | The caller may already be a Ticket Resolver member of the Project (primary key `(user_id, project_id)`); seeded Removed rows have NULL settings |
| D46: Planning: shape of the tracker read (D22, D24) | `tracker.service.TrackerProjectService.findProject(String projectKey)` → `TrackerProjectLookup(outcome, key, statuses)`, outcome `FOUND`, `NOT_FOUND`, `DENIED` or `UNAVAILABLE`; statuses `TrackerStatus(id, name, done)` deduplicated across issue types | one method per Jira call with exceptions; one method returning an outcome | No tracker exception crosses the module boundary; `project` maps four outcomes to D35/D43. Missing service account = `DENIED` without a Jira call |
| D47: Planning: MSG48 text in the web app catalog is stale ("Check the project and token…") and `frontend/src/shared/messages/catalog.ts` is outside the module's `paths` and needs the FE Lead's review | quick change before the screen task, against the SRS text | widen `paths`; quick change | It changes no criterion of any module; `shared/` edits go through the FE Lead (frontend rule 10) |
| D48: Planning: the Add project form on 3.1 | `FormModal` titled "Add project" opened by the "Add project" button: Project key + "Test connection"; after MSG47 the settings section (Done statuses multi-select, Review range) and Save appear; editing the key hides them again until the next test. The Admin keeps the 3.1 placeholder | inline section on the page; `FormModal` | The kit's pattern for a small form (workspace D14); the SRS describes no Admin content for 3.1. No source names the popup title |
| D49: RUN 1: exact key validation semantics and message parameters | Blank wins over length; server whitespace = `String.strip()`, case = `toUpperCase(Locale.ROOT)`, length = UTF-16 code units as received. Browser uses its blank check and original string length, sends the original key, and leaves format and canonicalisation to the server. MSG01/MSG02 server `field_name` is the JSON field; client `field_name` is the displayed label | implicit platform behaviour; explicit rules | Matches the workspace contract and the existing handler; preserves the 20-character check before stripping and keeps lowercase input usable |
| D50: RUN 1: exact response envelopes and identifier meaning | `Project` = `projectId`, `departmentId`, `jiraProjectKey`, `doneStatusIds`, `reviewRange`, `status`. `ProjectList` = `department` and `projects`; Department uses existing `DepartmentRef`. `ProjectRow` has only D44's fields. Response UUIDs and audit ids use `public_id`, membership uses `project.public_id`; internal BIGINT ids never leave the repository. Connection-test `projectId` is present only for reconnect | full database row; explicit DTOs | Defines the shared API without exposing storage details or requiring an extra Project read endpoint |
| D51: RUN 1: duplicate, null or blank selected status ids and unreadable input | Deduplicate selected ids in first-seen order; reject a null, blank or unknown id with 400 MSG01 on `doneStatusIds`. Status ids are exact strings, never trimmed. An unreadable body, a `doneStatusIds` that is not an array, or a range Jackson cannot read as a number gets 400 MSG06 without field errors. Jackson's default scalar coercion stays (a number sent as the key becomes a string; `"3"` becomes 3): revised in review, since turning it off is an application-wide setting in `common` | store as supplied; treat ids as a set; silently discard invalid ids | A multi-select represents a set; silent removal could save settings the caller did not choose. Keeps D41 and D42's validation meaning |
| D52: RUN 1: converting existing `done_status` values in changeset 036 | Convert SQL NULL to SQL NULL and every non-null VARCHAR value to a JSONB singleton string array, without interpreting the old text as JSON. Preserve all other columns, rows and references; no review range constraint, default or backfill | parse old text as JSON; discard it; preserve each old scalar | The old column holds one status, not a JSON document. A populated database must migrate without losing settings or disturbing review fallback fixtures (D38) |
| D53: RUN 1: invalid successful Jira response and error precedence across the two reads | A missing/blank canonical key or status id/name, or an unreadable project/statuses body, yields `UNAVAILABLE`. A valid empty statuses array yields `FOUND` with no statuses. Apply 404 / other 4xx / 429 / 5xx mapping to either read; 429 is always `UNAVAILABLE`. Do not call statuses if the project read fails | expose parsing errors; treat as unavailable | Uses the existing JiraClient malformed-response convention; no partial `FOUND` result reaches Project or a raw Jira error reaches its caller |
| D54: RUN 1: access checks versus request validation | All three operations declare `@PreAuthorize` for `Roles.PROJECT_MANAGER`. Bean Validation of the body runs first (`@Valid` resolves before `@PreAuthorize`, workspace D19), so a non-PM with an invalid body gets 400 and writes nothing; the bound Workspace and the caller's Active own Department are checked before the key format check, the link check and Jira (revised in review). The SQL Department lookup also requires an Active caller account holding the Project Manager role. Recheck eligibility and link state inside the write transaction after Jira; check the canonical key too if it differs | Bean Validation before method access; access before field validation | Meets the requested access responses and avoids a membership or Department change during Jira calls authorising a stale write; authentication remains the security layer's responsibility |
| D55: RUN 1: edits and dismissal while the popup has an in-flight request | While Test connection or Save runs, disable key/settings edits and further Test connection/Save requests; ignore Cancel, close icon, Escape and mask dismissal. Idle dismissal clears values and results for the next opening. Every idle key edit clears the tested result, settings and Save; errors retain the current popup values | allow changes and discard stale responses; lock the form | Prevents a response for an earlier key from revealing settings for a new key and makes one-request and Cancel behaviour testable with the existing FormModal |
| D56: RUN 2: tracker spec's client row says TrackerProject but the explicit brief requires Optional<String> | Optional<String> findProject(JiraAccount, String), empty on project 404; correct that interface row and log the correction, without changing ACs | retain stale row; synchronize the interface row | The latest explicit instruction defines the Java contract. No extra TrackerProject DTO is needed; service outcomes and AC-013.1-13 are unchanged |
| D57: RUN 2: extend ProjectService or add ProjectLinkService | Extend ProjectService with testConnection, create and list; preserve the JPA lookup method/entity/repository | extend existing; split new service | One Project public service, no recursive tracker lookup dependency; the old lookup's test ids must be corrected in Task 6 |
| D58: RUN 2: hide Save before testing using the current FormModal API; UTF-16 and INT-range checks | Primary action is Test connection initially, Save after a successful test, with a body re-test button when tested; feature-local UTF-16 length and INT-maximum validation | edit shared kit; use its primary action in two stages | Existing FormModal exposes okText/onSubmit and handles ModalTitle; no shared edit, CSS-hidden Save or silent InputNumber rounding is needed |
| D59: RUN 2: canonical aliases and concurrent reconnect membership reads | Check the requested key before Jira and the canonical key after it; one row found → that row, two distinct rows → MSG49 naming the canonical row's Department, never merged. In the write transaction lock the found Project row (`FOR UPDATE`) and re-read its manager; new rows rely on `uq_project_jira_key` with `ON CONFLICT DO NOTHING`, reconnects on the conditional `UPDATE … WHERE status = 'REMOVED'`; a unique violation from the canonical-key update is translated to MSG49 after rollback. No advisory locks (revised in review) | merge distinct Projects; refuse ambiguity and serialize writes | Never merge Project identities or delete tickets. Conditional writes remain the final guard; post-rollback translation is limited to the named Jira-key unique constraint |
| D60: RUN 2 builder check: @NotBlank and @Size can both fail on 21 spaces | No group sequence: the shared handler keeps one error per field and ranks MSG01 before MSG02 (revised in review) | depend on handler order; local validation group sequence | Meets D49's blank-wins rule without changing common validation handling; standard Jakarta Validation suffices |
| D61: RUN 2 builder check: migration/transaction/isolation IT setup without new containers | Migration IT: a random scratch schema on the `TwoTenantDatabases` server holding a minimal `project` table with `done_status VARCHAR(100)`; run the statement and the rollback statement of `036-project-done-statuses.sql` there and assert the values; the full changelog is proven by every other IT starting on it. Other Project ITs share one test configuration and a deterministic tracker double (revised in review: no Liquibase update-count run) | separate container per IT; reuse existing shared server | Tests actual migration on VARCHAR/NULL fixtures without touching shared tenant history, reuses one server and proves Jira runs outside transactions |

Decided in planning: extend existing `ProjectService` rather than introduce `ProjectLinkService`. It already owns the Project public boundary, and tracker reads use a separate non-recursive lookup service. Keep the lookup's existing JPA collaborators and semantics (D57).

Decided in planning: correct the stale tracker client interface row to `Optional<String>` (empty on project 404) to match the explicit RUN 2 instruction; no behavioural AC changes, no new `TrackerProject` DTO (D56).

Decided in planning: use the existing FormModal primary button for the two stages; no raw Modal, hidden-button CSS or shared kit edits. Apply feature-local UTF-16/upper-bound validators (D58, builder check).

Decided in planning: canonical aliases never merge two Project identities. The unique index and the conditional update decide races; the found row is locked `FOR UPDATE` and its manager re-read before the membership change (D59, revised in review).

Decided in planning: key blank-before-length precedence comes from `GlobalExceptionHandler` (`FIELD_CODE_PRECEDENCE`: MSG01, MSG02, ...; one error per field). No group sequence and no `ProjectKeyValidation` class (D60, revised in review).

Decided in planning: the outcome enum is nested, `vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome` (`FOUND`, `NOT_FOUND`, `DENIED`, `UNAVAILABLE`), as tracker Task 19 lists. Project Task 6 starts only after tracker Task 19 is merged on this branch and the tracker spec change is approved (builder check).

Decided in planning: Save order is D62 / AC-005.21: local range and blank-id checks before the link check and Jira; unknown ids after Jira (builder check).

Decided in planning: "Review range" is an antd `InputNumber` (via `FormField`), so text cannot be typed; the browser tests cover 0, -1, a fraction and 2,147,483,648 for MSG44 and empty for MSG01. On the server a whole value is one with `stripTrailingZeros().scale() <= 0`, so `3.0` and `1E+1` are accepted as 3 and 10 (builder check).

Decided in planning: the "Done statuses" multi-select uses the status id as the option value and the name as its label; when two returned statuses share a name, both labels become `name (id)` (builder check).

Decided in planning: an Admin who is also a Project Manager sees only the Project Manager content of 3.1; the placeholder is for Admin-only sessions (builder check).

Decided in planning: where the iteration design and this master differ, this master wins (builder check).

Decided in planning: one shared Project IT configuration supplies a deterministic tracker test double that asserts there is no active transaction on each call. Share it across connection/create/reconnect/list ITs with `TwoTenantDatabases`; no extra containers and no per-class @MockBean variations. Migration IT runs the changeset's statements on a minimal scratch table on the same server (D61).

## KAN-127: File Structure

Paths below are exact. New names are proposals; existing names and APIs were checked in the checkout. Later tasks may modify a file created by an earlier task, explicitly marked as such in their lists.

```text
backend/src/main/java/vn/tsolve/project/
  service/ProjectService.java                         Modify
  service/impl/ProjectServiceImpl.java                 Modify
  repository/ProjectLinkRepository.java                Create
  controller/ProjectController.java                    Create
  dto/ProjectConnectionTestRequest.java                 Create
  dto/CreateProjectRequest.java                        Create
  dto/ProjectConnectionTestResponse.java                Create
  dto/ProjectResponse.java                             Create
  dto/ProjectSaveResult.java                           Create
  dto/ProjectListResponse.java                         Create
  dto/ProjectRowResponse.java                          Create (nested Manager record)
  exception/ProjectAccessDeniedException.java            Create
  exception/ProjectTrackerUnavailableException.java      Create
backend/src/main/resources/db/changelog/
  changes/036-project-done-statuses.sql                 Create
  db.changelog-master.xml                              Modify
backend/src/test/java/vn/tsolve/project/
  ProjectDoneStatusesMigrationIT.java                   Create
  ProjectApiITConfiguration.java                       Create (shared test-only tracker double)
  ProjectConnectionIT.java                             Create
  ProjectCreationIT.java                               Create
  ProjectReconnectIT.java                              Create
  ProjectListIT.java                                   Create
  controller/ProjectControllerTest.java                 Create
  service/impl/ProjectServiceImplTest.java               Modify (remove false tracker AC ids)
frontend/openapi/tsolve-api.yaml                        Modify (projects tag, three paths, schemas)
frontend/src/shared/api/generated/**                    Generate only; never edit by hand
frontend/src/features/jira-integration/
  pages/JiraIntegrationPage.tsx                         Modify
  components/DepartmentProjects.tsx                     Create
  components/ProjectsTable.tsx                          Create
  components/AddProjectModal.tsx                        Create
  hooks/useProjects.ts                                 Create
  hooks/useProjectMutations.ts                          Create
  mocks/handlers.ts                                    Modify
  mocks/handlers.test.ts                               Create
  pages/JiraIntegrationPage.test.tsx                    Create
  components/AddProjectModal.test.tsx                   Create
  components/AddProjectModalErrors.test.tsx             Create
```

Tracker-owned files are enumerated in tracker Task 19 and its master design extension. Reuse `identity/dto/DepartmentRefResponse.java` for the Department response; tracker `dto/TrackerStatus` for HTTP statuses. No edits to `Project.java`, `ProjectRepository.java`, identity, common, workspace, shared UI, `src/app`, feature `routes.ts` or `src/mocks/handlers.ts`. Generated output is the sole automated shared API change. D47 is a separate FE Lead-reviewed quick change, outside these tasks' files.

## KAN-127: External Dependencies

None new. Existing Spring JDBC, transaction support, Jackson, Liquibase, PostgreSQL, Spring RestClient, React/antd, TanStack Query, orval, MSW, JUnit/Mockito and Vitest/RTL suffice. External service: Jira through tracker STORY-013 and `JiraServiceAccount.current()` only. No worker job is scheduled.

## KAN-127: Edge Cases & Risks

Dimensions analyzed: all 12. Skipped: none; compliance covers the existing audit and credential boundary only, and environment covers existing keyboard/error behaviour rather than new browser support. Each applicable dimension has 2–5 concrete rows. Numbers below are local to project-integration; tracker rows 58+ are separate. Every Critical/High row has a test owner.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-010 | User types | PM role remains in session but account is deactivated or role removed in DB | Critical | 403 MSG08 before Jira/write (AC-010.4, AC-010.8) | Task 6, extend Task 7 |
| 2 | STORY-008 | User types | Admin-only versus multi-role Admin+PM opens 3.1 | High | Placeholder and no request for Admin-only; PM content for PM (AC-008.1, AC-008.9) | Task 10 |
| 3 | STORY-009 | User types | New PM has never tested a key | Medium | Key, Test and Cancel only; no credentials (AC-009.1, AC-009.24) | Task 11 |
| 4 | STORY-004 | Input extremes | Missing/null/empty key and 21 whitespace characters | High | Blank wins, 400 MSG01; no Jira (AC-004.2) | Task 6 |
| 5 | STORY-004 | Input extremes | 20/21 UTF-16 units, padded lowercase, Unicode or SQL-like invalid format | High | Original length MSG02; strip/ROOT uppercase; invalid format MSG48; parameterized SQL (AC-004.1, AC-004.3, AC-004.4) | Task 6 |
| 6 | STORY-005 | Input extremes | Empty selection; null/blank/unknown id; duplicate ids | High | MSG01 on selection; valid duplicates keep first occurrence (AC-005.4, AC-005.5, AC-005.15) | Task 7 |
| 7 | STORY-005 | Input extremes | Range null, 0, -1, 2.5, 2147483647/2147483648 or unreadable body | High | MSG01/MSG44 or unreadable MSG06, exact params/field; no truncation; MSG44 before the link check and Jira (AC-005.6, AC-005.7, AC-005.16, AC-005.21) | Task 6, Task 7 |
| 8 | STORY-009 | Input extremes | Browser paste of 21 UTF-16 units or invalid range | High | MSG02/MSG44; retain full input; no request (AC-009.3, AC-009.11) | Task 11 |
| 9 | STORY-005 | Timing | Two direct Saves compete for new canonical key | Critical | One 201, one MSG49, one Project/membership/audit (AC-005.11) | Task 7 |
| 10 | STORY-006 | Timing | Two reconnects wait on same Removed row | Critical | One 200, one MSG49; no second membership/audit (AC-006.8) | Task 8 |
| 11 | STORY-005 | Timing | Caller loses eligibility or link becomes Active during Jira read | Critical | Transaction recheck refuses; no writes (AC-005.17) | Task 7 |
| 12 | STORY-009 | Timing | Repeated clicks, edits, Cancel/close/Escape/mask while either request is held | High | One request; edits and dismissal ignored (AC-009.4, AC-009.12, AC-009.21, AC-009.22) | Task 11 |
| 13 | STORY-007 | Scale | Own Department has zero, one, or many Projects | Medium | Empty array or complete key-sorted list, no paging (AC-007.1, AC-007.2, AC-007.7) | Task 9 |
| 14 | STORY-004 | Scale | Jira has no statuses or repeats ids across issue types | High | Empty allowed; all unique statuses once; no fabricated defaults (AC-004.9, AC-004.12; tracker AC-013.2, AC-013.9) | Task 6; tracker Task 19 |
| 15 | STORY-008 | Scale | Table query is initially pending or empty | Medium | Loading state or MSG04, no fake row (AC-008.6, AC-008.8) | Task 10 |
| 16 | STORY-006 | State transitions | Same previous PM reconnects OLD | High | Retain public id, tickets, settings mapping and all memberships (AC-006.1, AC-006.2, AC-006.3, AC-006.10) | Task 8 |
| 17 | STORY-006 | State transitions | Old manager inactive/moved/no longer PM; caller already MEMBER | Critical | Delete old PM row; promote caller by upsert; other members retained (AC-006.4) | Task 8 |
| 18 | STORY-009 | State transitions | Idle key edit or Cancel then reopen | High | Clear tested result/settings; reopened form empty (AC-009.8, AC-009.23) | Task 11 |
| 19 | STORY-009 | State transitions | Save gets 201 or reconnect 200 | High | Close, MSG05 once, list key invalidated and row refreshed (AC-009.13–AC-009.15) | Task 11 |
| 20 | STORY-009 | Environment | Keyboard Escape or close icon during busy request, then idle | High | Busy ignored; idle close clears form (AC-009.22, AC-009.23) | Task 11 |
| 21 | STORY-008 | Environment | Offline/list error then retry; mutation network error | High | Coded ErrorState retry; mutation retains form and shows MSG06 (AC-008.7, AC-009.19, AC-009.20) | Task 10, Task 11 |
| 22 | STORY-005 | Error cascades | Membership insertion fails after Project insert | Critical | Rollback Project/membership/audit, 500 MSG06 (AC-005.12) | Task 7 |
| 23 | STORY-006 | Error cascades | Audit throws after reconnect and PM replacement | Critical | Restore Removed row/settings/members; no audit (AC-006.9) | Task 8 |
| 24 | STORY-004 | Error cascades | Jira unavailable or returns malformed project/status body | High | 503 MSG06; no persisted data or partial prefill (AC-004.16, AC-004.18; tracker AC-013.7, AC-013.8) | Task 6; tracker Task 19 |
| 25 | STORY-010 | Authorization | Anonymous; wrong-role valid body; wrong-role invalid body | Critical | 401 MSG07; 403 MSG08; 400 validation respectively; no Jira/write (AC-010.1–AC-010.3; D54) | Task 6, Task 7, Task 9 |
| 26 | STORY-010 | Authorization | Unbound Workspace, missing or inactive own Department | Critical | 500 MSG06 or 403 MSG08 before link/Jira; ignore forged scope input (AC-010.4, AC-010.5, AC-010.7, AC-010.8) | Task 6, Task 7, Task 9 |
| 27 | STORY-010 | Authorization | Same Jira key in ACME and BETA databases | Critical | Operations affect only bound DB, key allowed separately (AC-010.6) | Task 6, Task 7, Task 8, Task 9 |
| 28 | STORY-005 | Data integrity | Upgrade legacy VARCHAR including JSON-looking scalar and SQL NULL | Critical | Singleton string arrays/SQL NULL; preserve ids, tickets, members and other columns (AC-005.19) | Task 5 |
| 29 | STORY-005 | Data integrity | Existing review ranges NULL, 0 and -1 | High | No new constraint/default/backfill; review fallback unaffected (AC-005.20) | Task 5 |
| 30 | STORY-007 | Data integrity | No PM membership or inactive PM; resolver memberships present | High | PM null; count all membership rows incl PM; active account alone controls display (AC-007.3–AC-007.6) | Task 9 |
| 31 | STORY-004 | Integration | Normalized alias returns canonical key held by disallowed Project | High | MSG49 with owner params, no successful test/save; one-row alias reconnects and stores the canonical key (AC-004.19, AC-004.21, AC-004.22, AC-005.1) | Task 6, Task 7 |
| 32 | STORY-005 | Integration | Save without earlier Test; Jira status changed after Test | High | Fresh lookup; unknown selection MSG01; no Jira inside tx (AC-005.2, AC-005.5, AC-005.18) | Task 7 |
| 33 | STORY-004 | Integration | Missing credential; 404 or other 4xx at either read | High | NOT_FOUND/DENIED → MSG48, no status call after failed project read (AC-004.8, AC-004.15; tracker AC-013.4–AC-013.6, AC-013.11) | Task 6; tracker Task 19 |
| 34 | STORY-005 | Compliance | Successful create/reconnect and failed attempts | High | Exactly one audit with caller/public ids/stored details on success, none on failure (AC-005.10, AC-006.5, AC-004.18) | Task 7, Task 8 |
| 35 | STORY-004 | Compliance | Error body/exception includes service-account email/token or Jira JSON | Critical | No credential/body in Project responses or logs (AC-004.17; tracker AC-013.12, AC-013.13) | Task 6; tracker Task 19 |
| 36 | STORY-004 | Business logic | Active key anywhere, Removed foreign Department, protected previous PM | High | Local MSG49 before Jira; same exact field/owner params (AC-004.5–AC-004.7, AC-006.7) | Task 6, Task 8 |
| 37 | STORY-004 | Business logic | Reconnect stored ids all stale or some still present; stored range NULL or 0 | High | Retain available ids else Done defaults; only NULL range → 3 (AC-004.10–AC-004.14) | Task 6 |
| 38 | STORY-005 | Business logic | Choose a valid non-Done-category status and maximum INT range | High | Store requested unique ids/range, field_mapping NULL, lookup resolves new id (AC-005.8, AC-005.13–AC-005.15) | Task 7 |

Additional popup test targets use these same rows: AC-009.2/AC-009.9/AC-009.10 (required fields, rows 6–8), AC-009.5–AC-009.7 (prefill, row 37), AC-009.16–AC-009.20 (MSG49 field, MSG48 inline, other errors and retained values, rows 21/24/36). Task 11 names every target explicitly. AC-004.20 is covered by Task 6's unreadable-body test.

Open questions for the spec owner: none requiring an answer in this run; new planning choices are D56–D61. The tracker interface correction follows the explicit RUN 2 instruction. External follow-ups (D47, DDL owner, GBR-04, Basic/Bearer and binding/CSRF) remain dependencies or declared limits, not hidden implementation assumptions.

## KAN-127: Risk Review — 2026-11-12 — CAUTION

Planning review, not implementation/security approval. Basic-auth credential versus real scoped service-account tokens: retain the existing boundary and mapping, expose no token input; KAN-119/KAN-125 must supply gateway Bearer before real scoped-token use. GBR-04 remains incomplete end to end because the Jira App still checks the Done category; a later reader of stored status ids is required. Reconnect membership rewrite deletes the previous PM row, so Task 8 must prove resolver preservation, caller promotion, locking and rollback. No DB review_range check is intentional: enforce all new API writes, preserve legacy review fallback fixtures, and keep direct-SQL writers visible to their owners. Shared browser CSRF and production Workspace binding are separate prerequisites; backend IT and MSW are the evidence for this slice.
