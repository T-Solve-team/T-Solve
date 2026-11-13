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

Migration `030-project-audit-member-change.sql` (changeset `project-integration:030-project-audit-member-change`): drop and re-add `ck_audit_log_entry_action` with `PROJECT_MEMBER_CHANGE` appended, rollback to the 027 list — same shape as `027-workspace-audit-department-change.sql`. Register it in the master changelog the way 027/028 are.

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
- `resources/db/changelog/changes/030-project-audit-member-change.sql` (+ master changelog entry).

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
