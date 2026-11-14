---
module: project-integration
date: 2026-11-15
total_tasks: 11
owner: Dev
---

# Tasks: project-integration — iteration 1 (KAN-128, KAN-129, KAN-127)

Spec: `artifacts/03-modules/project-integration/spec.md` · Master design: `artifacts/04-plans/project-integration/design.md`

Branch: `feat/KAN-128-project-integration-add-ticket-resolver`. One commit per task (team rules in the root `CLAUDE.md`).

## Task 1: Project members API

- **Story:** STORY-001, STORY-002
- **Feature:** `project-members-api`
- **Covers:** AC-001.1–AC-001.4, AC-002.1–AC-002.8
- **Description:** The four endpoints of the spec's Interface Contracts, the manager check (design D3, D4), all-or-nothing add with audit in one transaction (D5, D6), candidate search (D8), `AuditAction.PROJECT_MEMBER_CHANGE` + migration 030 (029 is identity's, KAN-310). Tests first; every test name starts with its AC id. Edge cases E1–E9, E11 each have a test.
- **Files:**
  - create `backend/src/main/java/vn/tsolve/project/controller/ProjectController.java`
  - create `backend/src/main/java/vn/tsolve/project/dto/{ProjectSummaryResponse,ProjectMemberResponse,MemberCandidateResponse,AddProjectMembersRequest}.java`
  - create `backend/src/main/java/vn/tsolve/project/service/ProjectMemberService.java`, `service/impl/ProjectMemberServiceImpl.java`
  - create `backend/src/main/java/vn/tsolve/project/repository/ProjectMemberRepository.java`
  - modify `backend/src/main/java/vn/tsolve/common/audit/AuditAction.java`
  - create `backend/src/main/resources/db/changelog/changes/034-project-audit-member-change.sql`; modify the master changelog
  - create tests under `backend/src/test/java/vn/tsolve/project/`
- **Verify:** `mvn verify` in `backend/`.
- **Depends on:** —

## Task 2: Project Detail Members tab

- **Story:** STORY-001, STORY-002
- **Feature:** `project-members-tab`
- **Covers:** AC-001.5–AC-001.7, AC-002.9–AC-002.16
- **Description:** Pull the OpenAPI from Task 1's backend into `frontend/openapi/tsolve-api.yaml`, regenerate the client, replace the 3.2 placeholder with header + Members tab + Add member popup (design D10, D11), MSW handlers, audit-log label for `PROJECT_MEMBER_CHANGE`. Tests first; every test name starts with its AC id.
- **Files:**
  - modify `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
  - modify `frontend/src/features/jira-integration/pages/ProjectDetailPage.tsx`
  - create `frontend/src/features/jira-integration/components/{ProjectHeader,MembersTable,AddMemberModal}.tsx` + tests
  - modify `frontend/src/features/jira-integration/mocks/handlers.ts`; create `mocks/data.ts`
  - modify `frontend/src/features/audit-log/hooks/audit-filters.ts` (and audit-log mocks/tests that list every action)
- **Verify:** in `frontend/`: `npm run format:check`, `lint`, `typecheck`, `test`, `build`; `npm run generate:api` leaves no diff.
- **Depends on:** Task 1

## Task 3: Remove member API (KAN-129)

- **Story:** STORY-003
- **Feature:** `project-member-remove-api`
- **Covers:** AC-003.1–AC-003.5
- **Description:** `DELETE /api/v1/projects/{projectId}/members/{userId}` with the manager check (D3, D4), the one-statement delete of the `MEMBER` row (D14), 409 for a Removed Project (D15), audit `{"removed": [id]}` in the same transaction (D16). An IT proves a ticket by the removed member is unchanged (D17). Branch `feat/KAN-129-project-integration-remove-member`. Tests first; every test name starts with its AC id; E12–E13 have a test.
- **Files:**
  - modify `backend/src/main/java/vn/tsolve/project/controller/ProjectController.java`
  - modify `backend/src/main/java/vn/tsolve/project/service/ProjectMemberService.java`, `service/impl/ProjectMemberServiceImpl.java`
  - modify `backend/src/main/java/vn/tsolve/project/repository/ProjectMemberRepository.java`
  - modify `backend/src/test/java/vn/tsolve/project/ProjectMembersIT.java`, `service/impl/ProjectMemberServiceImplTest.java`
- **Verify:** `mvn verify` in `backend/`.
- **Depends on:** Task 1

## Task 4: Remove button on the Members tab (KAN-129)

- **Story:** STORY-001, STORY-003
- **Feature:** `project-member-remove-tab`
- **Covers:** AC-001.7, AC-003.6–AC-003.10
- **Description:** Add the DELETE to `frontend/openapi/tsolve-api.yaml`, regenerate, a Remove button on `MEMBER` rows while the Project is Active, MSG55 confirm (D18), MSG05 / MSG06, MSW handler. Tests first; every test name starts with its AC id.
- **Files:**
  - modify `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
  - modify `frontend/src/features/jira-integration/components/MembersTable.tsx`, `pages/ProjectDetailPage.tsx` (+ tests)
  - create `frontend/src/features/jira-integration/hooks/useRemoveMemberFromProject.ts`
  - modify `frontend/src/features/jira-integration/mocks/handlers.ts`
- **Verify:** in `frontend/`: `npm run format:check`, `lint`, `typecheck`, `test`, `build`; `npm run generate:api` leaves no diff.
- **Depends on:** Task 3

## KAN-127: Tasks 5–11 (STORY-004..010)

The KAN-127 tasks follow the merged KAN-128/KAN-129 Tasks 1–4. They were Tasks 1–7 on the KAN-127 branch and are renumbered here after main (Task n becomes Task n+4); the artifact files under `05-implementation/` and `06-evaluation/` follow the new numbers. Their stories were STORY-001..007 and are now STORY-004..010.


Spec: `artifacts/03-modules/project-integration/spec.md` · Design: `artifacts/04-plans/project-integration/iteration-1/design.md` · Master: `artifacts/04-plans/project-integration/design.md`

Branch: `feat/KAN-127-project-integration-add-project` (current issue branch from main; do not switch to `feat/project-integration`). Assignee: Đặng Tấn Bình. One task/feature per code commit, plus its harness commit: run verification, report feature/files/results/proposed `type(scope): summary (KAN-127)` with `STORY-…` body and AI Co-Authored-By trailer, then stop for user agreement before committing or starting the next feature. Never push/merge/open a PR without a request. This plan does not open code gates; both modules need current spec approval in the implementation session.

Test-first for every task: add the behavioural test and see it fail, then implement minimally and verify. Backend names cite `_AC_005_14`; frontend `it('AC-009.15 ...')`; tracker Task 19 names tracker `_AC_013_n` only. Never use another module's ids in a Project test. Any implementation bug is logged by its owner in `docs/bug-log.md` with a bug-id regression test; this planning run writes only artifacts.

Files marked **Continue** do not exist yet in this checkout; they are created by the named prerequisite, then extended in the later task. Existing paths alone are called Modify. Generated file names must be obtained by running orval; `generated/**` is an output set, never a hand-edit list.

Verification aliases used below:

- **BE:** `mvn spotless:apply`, then `mvn verify` in `backend/` (unit, IT and Spotless check; Docker required). Targeted failing/passing tests during TDD do not replace this gate.
- **FE:** in `frontend/`, `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build`, then `npm run generate:api`; regeneration leaves no diff against the already generated output in `src/shared/api/generated`. Never hand-edit generated files to make that assertion pass.

Dependency before Project Task 6: **tracker Task 19**, `jira-project-read`, module `tracker` (tracker story 013). Its complete body is in `artifacts/04-plans/tracker/iteration-1/tasks.md`; do not add a duplicate Task 5 or tracker feature to this module. Project Task 5 can run independently first. Screen Task 10 also depends on the separately approved D47 MSG48 catalog quick change with FE Lead review; none of these tasks edits that catalog.

## Task 5: Preserve legacy Done statuses as JSONB

**Story:** STORY-005
**Feature:** project-done-statuses-schema
**Description:** First write an IT against real PostgreSQL demonstrating the pre-036 VARCHAR → JSONB upgrade with a scalar and SQL NULL. Add the exact master changeset 036 (author project, id 036-project-done-statuses) and include it LAST after current 026; do not alter applied changesets. Preserve all other values/references and add no range CHECK/default/backfill. Reuse the `TwoTenantDatabases` server: create a random scratch schema holding a `project` table with the pre-036 columns of `002-tenant-project-suggestion.sql` plus `public_id UUID` (`done_status VARCHAR(100)`), insert the legacy fixtures, execute the changeset's statement and then its rollback statement read from `036-project-done-statuses.sql`, and drop the schema in `finally` (D61). Do not touch the shared tenant schemas or their migration history. Document the rollback's element-zero loss for several values. Notify the main session that the external DDL owner must change `done_status` to JSONB; do not edit the DDL.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/036-project-done-statuses.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test (Create): `backend/src/test/java/vn/tsolve/project/ProjectDoneStatusesMigrationIT.java`
**Tests:**
- AC-005.19: scalar `10002`, JSON-looking text and SQL NULL become singleton string arrays/SQL NULL. Assert the column type and that every other column of every row (ids, `public_id`, `department_id`, key, `field_mapping`, `review_range`, `status`) is unchanged; `db.changelog-master.xml` has 036 last (edge row 28).
- AC-005.20: legacy NULL/0/-1 ranges survive; insert 0 and -1 after migration as proof there is no new CHECK/default/backfill; existing `SendBackApiIT` stays green through BE (row 29).
- AC-005.19: rollback turns singleton and NULL fixtures back to VARCHAR(100) values; reapplying 036 preserves them. The full changelog on an empty database is proven by every IT that starts on it. Use the existing server, not a new Testcontainers field.
**Verify:** BE.
**Dependencies:** none.
**Estimated size:** small.

## Task 6: Test a Project connection and prefill settings

**Story:** STORY-004, STORY-005 (request validation), STORY-010
**Feature:** project-connection-test
**Description:** Contract first: add `projects` tag, all three operations (`testProjectConnection`, `createProject`, `listProjects`), request/response schemas and exact success/error tables from the spec to `tsolve-api.yaml`. Reuse DepartmentRef/Problem; `reviewRange` request is number, multipleOf 1, min 1, max 2147483647; ids are public UUIDs. Run `npm run generate:api` and check generated names, including `getListProjectsQueryKey`; do not edit generated output. Then failing MVC/service tests, module exceptions and DTOs, JDBC eligible-own-Department/link reads, testConnection service and controller. No create/list implementation yet. Do not put @Transactional on the Jira path. Respect canonical conflict checks, prefill retained exact status ids and NULL-only range fallback. Add shared IT configuration with a deterministic tracker service double; no per-class container or varying MockBean contexts.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`
- Generate: `frontend/src/shared/api/generated/**` (only `npm run generate:api`)
- Modify: `backend/src/main/java/vn/tsolve/project/service/ProjectService.java`
- Modify: `backend/src/main/java/vn/tsolve/project/service/impl/ProjectServiceImpl.java`
- Create: `backend/src/main/java/vn/tsolve/project/controller/ProjectController.java`
- Create: `backend/src/main/java/vn/tsolve/project/repository/ProjectLinkRepository.java`
- Create: `backend/src/main/java/vn/tsolve/project/dto/ProjectConnectionTestRequest.java`
- Create: `backend/src/main/java/vn/tsolve/project/dto/CreateProjectRequest.java`
- Create: `backend/src/main/java/vn/tsolve/project/dto/ProjectConnectionTestResponse.java`
- Create: `backend/src/main/java/vn/tsolve/project/exception/ProjectAccessDeniedException.java`
- Create: `backend/src/main/java/vn/tsolve/project/exception/ProjectTrackerUnavailableException.java`
- Test (Modify): `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java`
- Test (Create): `backend/src/test/java/vn/tsolve/project/controller/ProjectControllerTest.java`
- Test support (Create): `backend/src/test/java/vn/tsolve/project/ProjectApiITConfiguration.java`
- Test (Create): `backend/src/test/java/vn/tsolve/project/ProjectConnectionIT.java`
**Tests:**
- Rename existing `findsTheActiveProjectOfAJiraKey_AC_006_1` to cite project-integration AC-005.14 (the Active public-id lookup); remove `_AC_006_8` from the negative lookup test because it proves no reconnect race. This prevents false trace coverage. Re-run both tests with the expanded constructor.
- MVC (`@WebMvcTest`, existing SecurityConfig/GlobalExceptionHandler, `.with(csrf())`, mocked ProjectService): AC-004.2/AC-004.3 missing/null/empty/whitespace/21 spaces, 20 versus 21 UTF-16 units and exact field/params; AC-004.20 unreadable JSON → 400 MSG06, no field (rows 4–5). Exercise `CreateProjectRequest` with Bean Validation directly for AC-005.4/AC-005.6, then add endpoint assertions in Task 7.
- MVC AC-010.1–AC-010.3: method annotations, anonymous → 401 MSG07, non-PM valid body → 403 MSG08, non-PM invalid body → 400, service uncalled (row 25). AC-004.15/AC-004.16 and access/storage exceptions map through shared handler to 409/503/403/500 with exact envelope, without module handlers (rows 24/26/33).
- Mockito service: AC-004.1 and AC-004.4 normalization/format ordering; AC-004.5–AC-004.7 local Active/foreign Removed/protected old PM conflicts precede tracker (rows 5/36). AC-004.8/AC-004.15/AC-004.16 four tracker outcomes. AC-004.17 captures Project logs and serializes success/failure responses to assert no service-account email/token or raw Jira body (rows 24/33/35).
- AC-004.9–AC-004.14: every unique status, canonical key, reconnect flag/omitted projectId for new versus retained id, Done-category default/3, stored intersection/fallback including empty statuses and legacy range 0 (rows 14/37). AC-004.19 canonical conflicts; AC-004.21 requested and canonical keys on two different rows; AC-004.22 a Removed own-Department row held under the requested key only, and under the canonical key only (row 31).
- AC-010.4/AC-010.5/AC-010.7/AC-010.8: TenantScope first; absent/inactive own Department, inactive caller, DB role removed before format/link/Jira; no scope accepted in request (rows 1/26).
- PostgreSQL `ProjectConnectionIT`, shared configuration and `scope.runIn`: AC-004.18 snapshots of Project/membership/audit unchanged for success and failure; AC-010.6 lookup only in bound ACME/BETA, same key allowed in each; no-bound path cannot reach default DB (row 27). SQL prefill and protected-manager fixtures use a Removed Project of caller's own Department. Tracker double asserts no active transaction.
**Verify:** BE and FE (contract generation affects the frontend).
**Dependencies:** Project Task 5; tracker Task 19 (`jira-project-read`).
**Estimated size:** medium (one endpoint plus the shared contract/scaffold, with business writes deferred).

## Task 7: Create a Project with membership and audit atomically

**Story:** STORY-005, STORY-010
**Feature:** project-create
**Description:** Write failing direct-Save, race and rollback tests, then extend ProjectService.create with the master flow and TransactionTemplate. Validate BigDecimal integrality/bounds without truncation, missing/empty/invalid selections, fresh status membership and ordered deduplication. Recheck eligibility and requested/canonical links in the write transaction; insert new only using ON CONFLICT DO NOTHING RETURNING. Do not implicitly reconnect on a lost insert. Insert caller membership, record the exact audit, read the saved projection and answer 201. Existing lookup resolves the committed canonical key. Controller uses ProjectSaveResult for HTTP status. Reconnect remains the next feature, with no path pretending it succeeded yet. Build the recheck helper now for reuse in Task 8.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/project/service/ProjectService.java`
- Modify: `backend/src/main/java/vn/tsolve/project/service/impl/ProjectServiceImpl.java`
- Continue (created Task 6): `backend/src/main/java/vn/tsolve/project/controller/ProjectController.java`
- Continue (created Task 6): `backend/src/main/java/vn/tsolve/project/repository/ProjectLinkRepository.java`
- Continue (created Task 6): `backend/src/main/java/vn/tsolve/project/dto/CreateProjectRequest.java`
- Create: `backend/src/main/java/vn/tsolve/project/dto/ProjectResponse.java`
- Create: `backend/src/main/java/vn/tsolve/project/dto/ProjectSaveResult.java`
- Test (Modify): `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java`
- Test (Continue Task 6): `backend/src/test/java/vn/tsolve/project/controller/ProjectControllerTest.java`
- Test support (Continue Task 6): `backend/src/test/java/vn/tsolve/project/ProjectApiITConfiguration.java`
- Test (Create): `backend/src/test/java/vn/tsolve/project/ProjectCreationIT.java`
**Tests:**
- MVC/Mockito AC-005.1–AC-005.7, AC-005.15/AC-005.16: same key refusals/order/canonical check; AC-005.21 range and blank-id refusals before the link check with no tracker call, unknown ids only after it; always fresh tracker lookup even without prior test; NOT_FOUND/DENIED/UNAVAILABLE write nothing; empty/null/blank/unknown ids, duplicates, numeric range null/0/-1/2.5/max/max+1, non-array and unreadable number/body with exact code/params/field (rows 6–7/24/31–33/36/38). Respect Jackson coercion instead of inventing stricter scalar rejection.
- AC-005.8–AC-005.10, AC-005.13/AC-005.14: real PostgreSQL inserts correct own Department, public id, canonical key, JSONB unique selected ids (including non-Done), range, ACTIVE, field_mapping NULL; one PM membership and one audit with signed-in actor and exact stored details; 201 body; existing Active lookup returns that id (rows 34/38).
- AC-005.11: latch/barrier-released independent requests/transactions, security and tenant context per thread: one 201 and one MSG49 naming winning Department; one Project/membership/audit. Include same and different caller Departments plus two aliases to one canonical key (rows 9/31). No sleeps.
- AC-005.12: Mockito: the membership insert throws → no audit call and the exception propagates; IT: call `ProjectService.create` inside `scope.runIn` with the security context cleared after the tracker double answers, so the real `AuditService` throws after both inserts; assert through a separate connection that no Project or membership row remains; MVC: a service exception maps to 500 MSG06 (row 22). This follows workspace AC-004.11; no triggers or failure hooks.
- AC-005.17: tracker double coordinates another transaction to deactivate caller, remove role, clear the caller's Department/change it to an inactive one, or claim the key during Jira read. The write recheck refuses and leaves no Project/membership/audit (rows 1/11/26). AC-005.18: the tracker service test double asserts the Project-to-tracker call has no active transaction; tracker Task 19 separately exercises both actual HTTP reads with that same non-transactional service boundary. Verify exactly one write transaction including the real AuditService (row 32).
- AC-010.1–AC-010.8 on POST: annotations/auth/access/scope order and tenant routing. ACME and BETA may save the same key without cross-DB changes; verify default/unbound DB untouched (rows 25–27).
**Verify:** BE.
**Dependencies:** Task 6.
**Estimated size:** medium.

## Task 8: Reconnect the same Removed Project safely

**Story:** STORY-006, STORY-005 (fresh validation), STORY-010
**Feature:** project-reconnect
**Description:** First add reconnect preservation/race/rollback tests; then complete create's Removed branch. Lock and re-read the Project/manager after Jira. Run the conditional UPDATE (zero rows → MSG49); persist canonical key if changed in the same transaction. Same previous PM leaves all memberships unchanged. Another eligible caller deletes previous PM row before caller upsert, preserving MEMBER rows and handling a caller already MEMBER. Keep public id/Department/tickets/field_mapping; save validated settings; record one RECONNECT audit and return 200. Do not edit identity or the JPA entity/repository.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/project/service/impl/ProjectServiceImpl.java`
- Continue (created Task 6): `backend/src/main/java/vn/tsolve/project/repository/ProjectLinkRepository.java`
- Test (Modify): `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java`
- Test (Continue Task 6): `backend/src/test/java/vn/tsolve/project/controller/ProjectControllerTest.java`
- Test support (Continue Task 6): `backend/src/test/java/vn/tsolve/project/ProjectApiITConfiguration.java`
- Test (Create): `backend/src/test/java/vn/tsolve/project/ProjectReconnectIT.java`
**Tests:**
- AC-006.1/AC-006.2/AC-006.3/AC-006.6/AC-006.10: OLD in own Department, same previous manager; 200 ACTIVE, retained id/Department/tickets/field_mapping/resolver rows/date_added; existing lookup returns retained id; selected exact settings saved (row 16).
- AC-006.4: no previous PM; deactivated previous PM; moved Department; removed PM role; caller already MEMBER. Delete only old PM row before upsert; no uniqueness failure, no duplicate caller row, every other membership unchanged (row 17).
- AC-006.5: one RECONNECT audit with signed-in actor and saved public ids/details, no audit on failure (row 34).
- AC-006.7: foreign Department or other protected Active same-Department PM gets exact MSG49, with zero Jira calls for pre-existing conflict and no writes; Active links refused (row 36).
- AC-006.8: two reconnect/direct-create requests compete on same Removed row, plus alias/canonical overlap; one 200, one MSG49, one audit and one manager. Prove waiter re-reads membership/state after lock (row 10).
- AC-006.9: inject membership and audit failures separately after conditional update, assert complete original Removed/settings/membership restoration including former PM and MEMBER caller and no audit (row 23). Use the AC-005.12 technique (repository mock in Mockito, cleared security context in the IT), not new containers.
- Reuse STORY-005's table of invalid selections/ranges and tracker failures for reconnect, AC-005.2/AC-005.3/AC-005.17/AC-005.18. Reconnect calls Jira outside tx and rechecks eligibility, respects AC-010.6 across two databases (rows 11/27/32).
**Verify:** BE.
**Dependencies:** Task 7.
**Estimated size:** medium.

## Task 9: List the caller's Department Projects

**Story:** STORY-007, STORY-010
**Feature:** project-list
**Description:** Write failing DTO/query/access tests, then extend ProjectService.list and GET handler. Query exact own Department, all Active/Removed Projects key ASC without paging, active-account manager display and all-membership count. Department is returned even when projects is empty. No Jira/no writes, no connection-status fan-out. Reuse identity's existing DepartmentRefResponse record.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/project/service/ProjectService.java`
- Modify: `backend/src/main/java/vn/tsolve/project/service/impl/ProjectServiceImpl.java`
- Continue (created Task 6): `backend/src/main/java/vn/tsolve/project/controller/ProjectController.java`
- Continue (created Task 6): `backend/src/main/java/vn/tsolve/project/repository/ProjectLinkRepository.java`
- Create: `backend/src/main/java/vn/tsolve/project/dto/ProjectListResponse.java`
- Create: `backend/src/main/java/vn/tsolve/project/dto/ProjectRowResponse.java`
- Test (Modify): `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java`
- Test (Continue Task 6): `backend/src/test/java/vn/tsolve/project/controller/ProjectControllerTest.java`
- Test (Create): `backend/src/test/java/vn/tsolve/project/ProjectListIT.java`
**Tests:**
- AC-007.1–AC-007.3/AC-007.7: own Department ref, zero/one/many mixed Active/Removed projects, sorted key, exact public UUID/row shape and no paging (row 13).
- AC-007.4–AC-007.6: active-account manager displayed even if display eligibility differs from stricter reconnect rule; missing/inactive manager null; count all rows including PM, never filter count by account status (row 30).
- AC-007.8: no Jira call and no persisted changes; Mockito verifies no tracker interaction, IT snapshots data (row 13).
- AC-010.1–AC-010.8 for GET: PM annotation, anonymous/wrong role, stale account/role, missing/inactive Department, no bound Workspace; TenantScope first; two databases prove isolation and no client scope accepted (rows 25–27).
**Verify:** BE.
**Dependencies:** Task 8 (linear task/commit sequence; query technically depends on Task 6).
**Estimated size:** small.

## Task 10: Show Department and Projects on Jira Integration

**Story:** STORY-008
**Feature:** jira-integration-projects-table
**Description:** First add page/MSW behaviour tests, then mount PM-only DepartmentProjects, query wrapper and ProjectsTable from the existing page. Keep Admin-only ScreenPlaceholder with no mounted query or Add button; the existing route guard handles other roles. Read Department from list response; implement all five columns, MSG54 null manager, Active/Removed StatusTag and loading/error-retry/empty states. Build feature-owned stateful MSW GET plus the test/save handlers needed for the next task (no UI mutation yet), with OPS Active, OLD Removed of IT Operations, SUP foreign conflict and MISSING → MSG48. State is local and resetMockData fully restores it. No routes/app/shared/registration edits.
**Files:**
- Modify: `frontend/src/features/jira-integration/pages/JiraIntegrationPage.tsx`
- Create: `frontend/src/features/jira-integration/components/DepartmentProjects.tsx`
- Create: `frontend/src/features/jira-integration/components/ProjectsTable.tsx`
- Create: `frontend/src/features/jira-integration/hooks/useProjects.ts`
- Modify: `frontend/src/features/jira-integration/mocks/handlers.ts`
- Test (Create): `frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx`
- Test (Create): `frontend/src/features/jira-integration/mocks/handlers.test.ts`
**Tests:**
- AC-008.1–AC-008.5: renderApp PM at /jira-integration, list request, read-only Department and exact column headings/order/public id/fullName/member count/status labels; MSG54 for null PM (row 30 through UI).
- AC-008.6–AC-008.8: held first GET shows loading; failure shows coded state/params with working retry; empty list MSG04 (rows 15/21).
- AC-008.9/AC-008.10: Admin-only placeholder/no Project request/no Add button, Admin+PM sees PM table, Employee route-guard MSG08/no request (row 2).
- Handlers use typed generated responses; GET sorted own Department rows, POST Test prefill, Save 201 updates GET, OLD Save 200 retains id, SUP MSG49 and MISSING MSG48, resetMockData restores prior ids/settings/counts. These are fixtures for AC-008.1/AC-009.15–AC-009.17; mutation UI acceptance is tested in Task 11 (rows 19/36/37).
**Verify:** FE.
**Dependencies:** Task 9; D47 separate MSG48 catalog quick change completed with FE Lead review.
**Estimated size:** medium.

## Task 11: Test and add or reconnect a Project in the popup

**Story:** STORY-009
**Feature:** jira-integration-add-project
**Description:** Write popup tests against MSW first, then add AddProjectModal and mutation wrappers, wire the Add button in DepartmentProjects. Use generated shapes and hooks, FormModal/FormField and existing ModalTitle via FormModal. Test action starts as the primary button, becomes Save after success; settings are mounted only for tested key. Share a synchronous submitting ref for both requests and all dismiss actions. Key edit clears test/settings; Save sends canonical key. Use field errors, inline MSG48, MSG47/MSG05 success toasts, owned generic error handling, and generated list-key invalidation. Browser key/range validators are local where shared rules do not implement UTF-16/INT maximum semantics. Do not add native maxLength, integer rounding, raw Modal or credentials. Retain input/tested settings on failure; reset on idle dismiss/reopen. Split tests by normal/error behaviour to stay below ~30 tests/file.
**Files:**
- Create: `frontend/src/features/jira-integration/components/AddProjectModal.tsx`
- Create: `frontend/src/features/jira-integration/hooks/useProjectMutations.ts`
- Continue (created Task 10): `frontend/src/features/jira-integration/components/DepartmentProjects.tsx`
- Modify: `frontend/src/features/jira-integration/mocks/handlers.ts` (complete any UI-discovered fixture gaps in the same feature)
- Test (Create): `frontend/src/features/jira-integration/components/AddProjectModal.test.tsx`
- Test (Create): `frontend/src/features/jira-integration/components/AddProjectModalErrors.test.tsx`
- Test (Continue Task 10): `frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx`
**Tests:**
- AC-009.1/AC-009.24: title Add project, required Project key, Test and Cancel; settings/Save absent; no credential/mapping fields (row 3).
- AC-009.2/AC-009.3: missing/whitespace including overlength whitespace → MSG01 first; 21 UTF-16 units → MSG02, pasted value retained, no request (rows 4/8).
- AC-009.4–AC-009.7: one Test POST of original padded/lowercase key; MSG47 once, every status name/id including non-Done selectable, server-prefilled ids/range and Save visible; empty statuses and legacy-invalid range require correction (rows 14/37).
- AC-009.8: idle key edit hides settings/Save and clears tested result until new Test (row 18).
- AC-009.9–AC-009.12: empty statuses/range MSG01; 0/-1/fraction/>max range MSG44 (`InputNumber`, so no text), max accepted, no rounding; valid Save sends tested canonical key, selected ids and numeric range once (rows 6–8/12).
- AC-009.13–AC-009.15: 201 new and 200 OLD each close, one MSG05, a second GET through generated list key and updated row; OLD keeps same projectId and member count (row 19).
- AC-009.16–AC-009.20: both operations' MSG49 field+params, MSG48 inline, other field errors applied, non-field 403/503/500/network by code/fallback; one display, values and any prior settings retained (rows 21/24/36).
- AC-009.21/AC-009.22: held Test and held Save prevent double click, cross-action submit, all field edits and Cancel/close/Escape/mask dismissal; no stale settings from another key (rows 12/20).
- AC-009.23: every idle dismissal sends no request; reopen clears form/messages/result (rows 18/20).
**Verify:** FE; at final handoff BE, `sdd.ts check project-integration`, `sdd.ts check tracker`, and each module's trace with its own paths. Review declared Basic/Bearer, GBR-04, CSRF/binding and DDL-owner follow-ups; no status/approval/push is implied by those checks.
**Dependencies:** Task 10; D47 catalog quick change remains a prerequisite.
**Estimated size:** medium.
