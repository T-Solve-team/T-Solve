---
module: workspace
date: 2026-11-11
total_tasks: 2
owner: Dev
---

# Tasks: workspace

Spec: `artifacts/03-modules/workspace/spec.md` · Design: `artifacts/04-plans/workspace/iteration-1/design.md` · Master: `artifacts/04-plans/workspace/design.md`

Branch: `feat/KAN-120-workspace-create-department`. Each task is one commit after the user agrees. Test names cite the AC id: backend `..._AC_001_7`, frontend `it('AC-002.6 ...')`.

## Task 1: Create Department API, Space and audit

**Story:** STORY-001
**Feature:** create-department-api
**Description:** Contract first (`backend/AGENTS.md` rule 8): add tag `departments`, `POST /api/v1/departments` (operationId `createDepartment`; 201 `DepartmentRef`; 400, 401, 403, 409 `components/responses/Problem`), schema `CreateDepartmentRequest` (`name`: string, required, `minLength: 1`, `maxLength: 50`) and `DEPARTMENT_CHANGE` in `AuditAction`, then run `npm run generate:api` and add the `ACTION_LABELS` entry "Department change" (D21). Then the two changesets, the enum value and the layers in the design. `CreateDepartmentRequest` is a record with `@NotBlank(message = "MSG01") @Size(max = 50, message = "MSG02") String name`. The controller copies `UserCreationController`'s annotations and takes `@Valid @RequestBody`. Change the seeder's clause to `ON CONFLICT DO NOTHING` and the `UserListIT` fixture to `'list fixture beta'` (D20). The insert has no conflict target (see the design).
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/027-workspace-audit-department-change.sql`
- Create: `backend/src/main/resources/db/changelog/changes/028-workspace-department-name-lower-unique.sql`
- Create: `backend/src/main/java/vn/tsolve/workspace/controller/DepartmentController.java`
- Create: `backend/src/main/java/vn/tsolve/workspace/service/DepartmentService.java`
- Create: `backend/src/main/java/vn/tsolve/workspace/service/impl/DepartmentServiceImpl.java`
- Create: `backend/src/main/java/vn/tsolve/workspace/repository/DepartmentRepository.java`
- Create: `backend/src/main/java/vn/tsolve/workspace/dto/CreateDepartmentRequest.java`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Modify: `backend/src/main/java/vn/tsolve/common/audit/AuditAction.java`
- Modify: `backend/src/main/java/vn/tsolve/identity/mock/MockUserListSeeder.java`
- Modify: `backend/src/test/java/vn/tsolve/identity/UserListIT.java`
- Modify: `frontend/openapi/tsolve-api.yaml`
- Modify: `frontend/src/shared/api/generated/**` (regenerated, never by hand)
- Modify: `frontend/src/features/audit-log/hooks/audit-filters.ts`
- Test: `backend/src/test/java/vn/tsolve/workspace/controller/DepartmentControllerTest.java`
- Test: `backend/src/test/java/vn/tsolve/workspace/service/impl/DepartmentServiceImplTest.java`
- Test: `backend/src/test/java/vn/tsolve/workspace/DepartmentCreationIT.java`
- Test: `frontend/src/features/audit-log/hooks/audit-filters.test.ts`
**Tests** (edge-case rows from the design):
- `DepartmentControllerTest` (`@WebMvcTest` with `SecurityConfig` and `GlobalExceptionHandler`; `.with(csrf())`; mocked service): AC-001.1 201 body, with a 50-character name (row 6) · AC-001.5 missing, empty, spaces only and 51 spaces (row 5) · AC-001.6 51 characters (row 6) · AC-001.12 non-Admin with an allowed name, service not called (rows 1, 19) · AC-001.13 anonymous (row 2) · AC-001.11 service throws, answer is 500 MSG06 (row 14).
- `DepartmentServiceImplTest` (Mockito): AC-001.1 strips before insert and returns the stored name · AC-001.7 empty insert result throws MSG43 on `name`, with no Space insert and no audit call (row 19) · AC-001.11 `insertSpace` throws, no audit call (row 14) · AC-001.15 unbound scope throws before any repository call (row 15).
- `DepartmentCreationIT` (real PostgreSQL through `TwoTenantDatabases`, requests inside `scope.runIn(ACME, ...)`):
  - AC-001.1, AC-001.2, AC-001.3: one Active Department, one Active Space with the same name, no `space_manager` row, one audit row with actor, action, target and details (rows 16, 18, 20)
  - AC-001.4: the new Department is in `GET /api/v1/users/departments` (row 21)
  - AC-001.7: duplicate of an Active name, also padded with spaces; no second Space or audit row (rows 7, 19)
  - AC-001.8: a seeded `DELETED` name is refused (row 9)
  - AC-001.9: `ABC` against `abc`, and `Phòng IT` against `PHÒNG IT` (row 10)
  - AC-001.10: two threads released by a latch, following `concurrentClaimsLeaveOneManagerAndOneConflict_AC_005_13` (row 13)
  - AC-001.11: call `DepartmentService.create` inside `scope.runIn(ACME, ...)` with the security context cleared, so `AuditService` throws after both inserts; assert no Department and no Space (row 14)
  - AC-001.14: a name held in BETA is allowed in ACME and the new row exists only in `tenant_a` (row 17)
  - AC-001.15: a request with no `runIn` answers 500 MSG06 and writes nothing (row 15)
- `audit-filters.test.ts`: AC-001.16 `ACTION_LABELS.DEPARTMENT_CHANGE` is "Department change" (row 20).

**Verify:** `mvn verify` in `backend/`; in `frontend/`: `format:check`, `lint`, `typecheck`, `test`, `build`, and `generate:api` leaves no diff.
**Dependencies:** none
**Estimated size:** medium

---

## Task 2: New Department on Workspace Settings

**Story:** STORY-002
**Feature:** create-department-screen
**Description:** Replace the 2.2 placeholder with the "New Department" button, `NewDepartmentModal` and the Name-only `DataTable` labelled "Departments" (no paging, no `loading` prop; `LoadingState` while `isPending`; `error` and `onRetry` from the query). Keep the button outside the loading and error branches. Add the MSW POST handler. Do not touch `routes.ts`, `src/mocks/handlers.ts` or `src/shared/`.
**Files:**
- Modify: `frontend/src/features/workspace/pages/WorkspaceSettingsPage.tsx`
- Create: `frontend/src/features/workspace/components/NewDepartmentModal.tsx`
- Create: `frontend/src/features/workspace/hooks/useDepartmentMutations.ts`
- Modify: `frontend/src/features/workspace/mocks/handlers.ts`
- Test: `frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx`
**Tests** (`renderApp('/workspace-settings', { role: 'ADMIN' })`; `server.use` for error and list cases; edge-case rows from the design):
- AC-002.1 button and table with the Name column and the Departments
- AC-002.2 loading state · AC-002.3 list failure, retry and button kept (row 28) · AC-002.4 MSG04 when empty (row 31)
- AC-002.5 popup title, empty field, Save and Cancel
- AC-002.6 empty and spaces only (row 24) · AC-002.7 51 characters, text kept, no request (row 24)
- AC-002.8 request body is trimmed (row 25) and two clicks send one request (row 22)
- AC-002.9 popup closes, MSG05, and a test-local stateful GET shows the new row and is called again after the POST (row 30)
- AC-002.10 MSG43 below the field, popup and text kept (row 26)
- AC-002.11 403 and 500 show the message by code, popup and text kept (row 27)
- AC-002.12 Cancel and the close icon send nothing, reopen is empty (row 29)
- AC-002.13 `role: 'EMPLOYEE'` sees MSG08 and no button (row 33)
- AC-002.14 Cancel during a held POST leaves the popup open (row 23)

**Verify:** in `frontend/`: `format:check`, `lint`, `typecheck`, `test`, `build`, and `generate:api` leaves no diff.
**Dependencies:** Task 1
**Estimated size:** medium
