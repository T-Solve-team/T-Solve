---
module: workspace
date: 2026-11-11
total_tasks: 4
owner: Dev
---

# Tasks: workspace

Spec: `artifacts/03-modules/workspace/spec.md` · Design: `artifacts/04-plans/workspace/iteration-1/design.md` · Master: `artifacts/04-plans/workspace/design.md`

Branch: `feat/KAN-120-workspace-create-department` for Tasks 1 and 2 (KAN-120), `feat/KAN-121-workspace-update-department` for Tasks 3 and 4 (KAN-121). Each task is one commit after the user agrees. Test names cite the AC id: backend `..._AC_001_7`, frontend `it('AC-002.6 ...')`.

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

---

## Task 3: Read and rename Department API, with its Space and audit

**Story:** STORY-003
**Feature:** rename-department-api
**Description:** KAN-121, branch `feat/KAN-121-workspace-update-department`. Contract first (`backend/AGENTS.md` rule 8): widen the `departments` tag description to "Create, read and rename a Department and its Space (SRS UC-12, UC-13)"; add path `/api/v1/departments/{departmentId}` (parameter `departmentId`, `format: uuid`) with `get` (operationId `getDepartment`; 200 `DepartmentRef`; 401, 403, 404 `components/responses/Problem`) and `put` (operationId `updateDepartment`; body `UpdateDepartmentRequest`; 200 `DepartmentRef`; 400, 401, 403, 404, 409); schema `UpdateDepartmentRequest` (`name`: string, required, `minLength: 1`, `maxLength: 50`). Run `npm run generate:api`. Then the layers in the master design, section "KAN-121: read and rename": `UpdateDepartmentRequest` copies `CreateDepartmentRequest` with its compact constructor; the controller gains `get`, `update` (`@Valid @RequestBody`) and the regex `invalidId` route for GET and PUT copied from `UserDetailController.java:30, 82-93`; `DepartmentService` gains `get` and `update` with Javadoc like `create`'s; the repository gains `Current`, `findActive`, `lockActive`, `renameDepartment` (23505 to MSG43 on `name`) and `renameSpace` (no row: `IllegalStateException`). No changeset, no file outside `module.md` `paths`.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/workspace/dto/UpdateDepartmentRequest.java`
- Modify: `backend/src/main/java/vn/tsolve/workspace/controller/DepartmentController.java`
- Modify: `backend/src/main/java/vn/tsolve/workspace/service/DepartmentService.java`
- Modify: `backend/src/main/java/vn/tsolve/workspace/service/impl/DepartmentServiceImpl.java`
- Modify: `backend/src/main/java/vn/tsolve/workspace/repository/DepartmentRepository.java`
- Modify: `frontend/openapi/tsolve-api.yaml`
- Modify: `frontend/src/shared/api/generated/**` (regenerated, never by hand)
- Test: `backend/src/test/java/vn/tsolve/workspace/controller/DepartmentControllerTest.java`
- Test: `backend/src/test/java/vn/tsolve/workspace/service/impl/DepartmentServiceImplTest.java`
- Test: `backend/src/test/java/vn/tsolve/workspace/DepartmentRenameIT.java`
**Tests** (edge-case rows 35 to 56 of the design; backend names end in the AC id, e.g. `renamesDepartmentAndSpace_AC_003_3_AC_003_4_AC_003_5`):
- `DepartmentControllerTest` (`@WebMvcTest` as for Task 1; `.with(csrf())`; mocked service):
  - AC-003.1 GET 200 body
  - AC-003.2 GET and PUT with `abc` answer 404 MSG04 and the service is not called; service throws `NotFoundException` gives 404 MSG04 (rows 40, 46)
  - AC-003.3 PUT 200 body with a 50-character name (row 39)
  - AC-003.8 missing, `""`, spaces only, 51 spaces and a Unicode-blank name give MSG01 (row 38)
  - AC-003.9 51 characters give MSG02 with `max_length` 50 (row 39)
  - AC-003.10 service throws MSG43 gives 409 with the field error
  - AC-003.17 service throws `IllegalStateException` gives 500 MSG06
  - AC-003.18 non-Admin GET, and PUT with an allowed name, give 403 MSG08, service not called (row 35)
  - AC-003.19 anonymous GET and PUT give 401 MSG07 (row 36)
- `DepartmentServiceImplTest` (Mockito):
  - AC-003.3 strips before `renameDepartment` and returns the stored name
  - AC-003.5 audit details hold `operation` UPDATE, `before`, `after`, `spaceId`
  - AC-003.14 an unchanged name after `strip()` calls no rename and no audit (row 41)
  - AC-003.2 empty `lockActive` and empty `findActive` throw MSG04 with no write
  - AC-003.17 `renameSpace` throws, no audit call
  - AC-003.21 unbound scope throws before any repository call, for `get` and `update` (row 51)
- `DepartmentRenameIT` (real PostgreSQL through `TwoTenantDatabases`, class annotations copied from `DepartmentCreationIT`, requests inside `scope.runIn(ACME, ...)`):
  - AC-003.1 GET returns the Department
  - AC-003.2 unknown id and a seeded `DELETED` Department answer 404 MSG04 for GET and PUT, and the Deleted row keeps its name (row 46)
  - AC-003.3, AC-003.4, AC-003.5 rename stores the stripped name, the Space has the same name with status and `space_manager` row kept, one audit row with actor, action, target and details (rows 45, 53)
  - AC-003.6 `status`, `default_project_id` and the Department's `project` rows are unchanged (row 47)
  - AC-003.7 the new name is in `GET /api/v1/users/departments` and `GET /api/v1/departments/{id}` (row 55)
  - AC-003.10 another Active name, also padded, gives 409 MSG43 with the old name and no audit row (rows 42, 54)
  - AC-003.11 a Deleted Department's name gives 409 MSG43 (row 43)
  - AC-003.12 `PHÒNG IT` against another `Phòng IT` gives 409 MSG43 (row 44)
  - AC-003.13 own `it support` to `IT Support` is stored and audited (row 45)
  - AC-003.14 the same name with spaces around it answers 200 and adds no audit row (row 41)
  - AC-003.15 two threads released by a latch rename two Departments to one name: one 200, one 409, one audit row, as `concurrentRequestsCreateOneDepartment_AC_001_10` (row 48)
  - AC-003.16 two threads rename the same Department to two names: both 200, and the second audit `before` equals the first `after` (row 49)
  - AC-003.17 the Space row deleted first, and separately the security context cleared so `AuditService` throws: old Department and Space names kept (row 50)
  - AC-003.20 a name held in BETA is allowed in ACME and only `tenant_a` changes (row 52)
  - AC-003.21 GET and PUT with no `runIn` answer 500 MSG06 and write nothing (row 51)
  - AC-003.22 after a rename, the create audit entry still holds the old name (row 53)

**Verify:** in `backend/`: `mvn -B -Dapi.version=1.44 verify` (tests and `spotless:check`; JAVA_HOME as in the local-verify note); in `frontend/`: `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build`, and `npm run generate:api` leaves no diff in `src/shared/api/generated`.
**Dependencies:** Task 1
**Estimated size:** medium

---

## Task 4: Rename on 2.3 Department Detail

**Story:** STORY-004
**Feature:** rename-department-screen
**Description:** KAN-121. Link the 2.2 Name cell to 2.3 with `cells.link(paths.departmentDetail(d.departmentId), d.name)`. Replace the 2.3 placeholder as the master design says ("Web app" under "KAN-121: read and rename"): `DepartmentDetailPage` (`useParams`, `useGetDepartment`, `LoadingState`, `ErrorState` with retry, link "Back to Workspace Settings", `SectionCard` "Department Detail") and `DepartmentNameForm` (Name shown in `Descriptions`, "Edit", inline `FormField` "Department name" prefilled, Save and Cancel, `submitting` ref, trim, `applyApiErrors`). Add `useRenameDepartment` beside `useAddDepartment` (`handlesOwnErrors`, MSG05, `client.invalidateQueries()` with no key). MSW: seed the local list with copies of IT (`...0000000000d1`) and HR (`...0000000000d2`), add `GET` and `PUT */api/v1/departments/:departmentId`, keep `resetMockData` restoring the seed; check that no existing `WorkspaceSettingsPage.test.tsx` case now collides with the seeded names. Do not touch `routes.ts`, `src/mocks/handlers.ts` or `src/shared/`. Header comment of `DepartmentDetailPage.tsx`: "UC-13, UC-14".
**Files:**
- Modify: `frontend/src/features/workspace/pages/WorkspaceSettingsPage.tsx`
- Modify: `frontend/src/features/workspace/pages/DepartmentDetailPage.tsx`
- Create: `frontend/src/features/workspace/components/DepartmentNameForm.tsx`
- Modify: `frontend/src/features/workspace/hooks/useDepartmentMutations.ts`
- Modify: `frontend/src/features/workspace/mocks/handlers.ts`
- Test: `frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx`
- Test: `frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx`
**Tests** (`renderApp(paths.departmentDetail(id), { role: 'ADMIN' })`; `server.use` for error and held cases; edge-case rows 57 to 68 of the design; names start with the AC id):
- `WorkspaceSettingsPage.test.tsx`: AC-004.1 each Name is a link to `paths.departmentDetail(id)`
- `DepartmentDetailPage.test.tsx`:
  - AC-004.2 name, "Edit" and "Back to Workspace Settings" for a seeded id, opened directly by URL (row 66)
  - AC-004.3 loading state while the GET is held
  - AC-004.4 404 shows MSG04 with "Try again" and no "Edit"; 500 shows MSG06 (row 63)
  - AC-004.5 Edit shows the field with the current name, Save and Cancel, and hides "Edit"
  - AC-004.6 empty and spaces only, no PUT (row 59)
  - AC-004.7 51 characters, text kept, no PUT (row 59)
  - AC-004.8 PUT body is trimmed (row 60) and two clicks send one PUT (row 57)
  - AC-004.9 form closes, MSG05, the stored name shows, and the GET is called again after the PUT (row 65)
  - AC-004.10 MSG43 below the field, form and text kept (row 61)
  - AC-004.11 404, 403 and 500 on Save show the message by code, form and text kept (row 62)
  - AC-004.12 Cancel sends nothing and shows the current name; Edit again shows the current name, not the typed one (row 64)
  - AC-004.13 Cancel during a held PUT keeps the form open (row 58)
  - AC-004.14 `role: 'EMPLOYEE'` sees MSG08 and no "Edit" (row 67)

**Verify:** in `frontend/`: `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build`, and `npm run generate:api` leaves no diff in `src/shared/api/generated`.
**Dependencies:** Task 3
**Estimated size:** medium
