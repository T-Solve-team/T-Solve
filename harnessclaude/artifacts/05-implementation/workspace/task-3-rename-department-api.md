# Task 3: Read and rename Department API, with its Space and audit

**Module:** workspace
**Assignee:** Claude (Developer role, Sonnet)
**Status:** done pending review (tracking is moved by the main session)

---

## Session 2026-11-12

### Working on: rename-department-api (KAN-121, STORY-003)

**Order followed.** Contract first (`tsolve-api.yaml`: tag text, path `/api/v1/departments/{departmentId}` with `getDepartment` and `updateDepartment`, schema `UpdateDepartmentRequest`), then `npm run generate:api`, then compile stubs (`UpdateDepartmentRequest`, service interface methods, repository `Current` and four methods throwing `UnsupportedOperationException`), then tests, then production code. Code is verbatim from the master design (K121-D6 to D11); `findActive` returns `Current` too, and the service builds the response from the path id.

**Test-first evidence.** With the stubs and no controller routes: `DepartmentControllerTest` failed 18 of 32 on assertions (404/405 instead of 200, 400, 409, 403; the AC-003.19 anonymous cases already pass through `SecurityConfig`), `DepartmentServiceImplTest` failed 7 of 11 (3 assertion failures, 4 `UnsupportedOperationException` from the stub). No compile errors. After the production code: 32/32 and 11/11. `DepartmentRenameIT` was written against the stubs and run first against the finished code in `mvn verify` (see below); it was not run separately red.

**Files**
- New: `backend/src/main/java/vn/tsolve/workspace/dto/UpdateDepartmentRequest.java`, `backend/src/test/java/vn/tsolve/workspace/DepartmentRenameIT.java`, `frontend/src/shared/api/generated/model/updateDepartmentRequest.ts`.
- Modified: `DepartmentController` (get, update, regex `invalidId` route), `DepartmentService`, `DepartmentServiceImpl`, `DepartmentRepository`, `DepartmentControllerTest`, `DepartmentServiceImplTest`, `frontend/openapi/tsolve-api.yaml`, generated `departments/departments.ts`, `departments/departments.msw.ts`, `model/index.ts`.

**Deviations:** none. No changeset, no spec edit, no screen code, no file outside the module `paths`. No bug found.

### Verify
`mvn -B -Dapi.version=1.44 spotless:apply` then `verify` (backend/, JBR 21): BUILD SUCCESS. Unit 1398/0 failures (`DepartmentControllerTest` 32, `DepartmentServiceImplTest` 11), IT 459/0 (`DepartmentRenameIT` 17, `DepartmentCreationIT` 9), `spotless:check` passed. Frontend (Node 24.22.2): `typecheck` exit 0; `generate:api` re-run leaves the same four generated files, nothing more. Trace `workspace STORY-003`: 22/22.

### Next up
Review, then Task 4 (screen 2.3). Nothing staged or committed.

### Review fixes
- Finding 1: `invalidId` in `DepartmentController` is now `@Hidden` (springdoc no longer publishes a fake operation).
- Finding 2 not done: " " + 49 x "a" + " " is 51 characters as received, and AC-003.9 says MSG02 applies to a name "as received" longer than 50, so it must be rejected, not accepted. Asked the coordinator to confirm the intended behavior; no spec edit.
