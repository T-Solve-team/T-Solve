# Task 5: List the caller's Department Projects (KAN-127)

**Module:** project-integration
**Assignee:** Đặng Tấn Bình (Developer: Claude, D49, no Codex)
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-13

### Working on: project-list (STORY-004, STORY-007)

Branch `feat/KAN-127-project-integration-add-project`, from HEAD `3b860534`. `GET /api/v1/projects` (`listProjects`)
now answers 200 `ProjectList`: the caller's own Department as `DepartmentRef` and all its Active and Removed
Projects as `ProjectRow`, sorted by key, with no paging.

- `ProjectServiceImpl.list()` runs the same scope check as the two POST operations, now pulled out as
  `checkScope()`: first the bound Workspace (`TenantScope.currentWorkspaceId()`, so an unbound call is 500 MSG06
  before any read), then the session caller, then `findCallerDepartment` (Active account, PM role, Active
  Department; otherwise 403 MSG08). `checkScopeAndKey` now calls `checkScope()` and keeps its key check, so the POST
  behaviour does not change. The service then returns
  `ProjectListResponse(new DepartmentRefResponse(publicId, name), links.listProjects(departmentId))`.
- `ProjectLinkRepository.listProjects(departmentId)`: one SQL read. It LEFT JOINs the `PROJECT_MANAGER` membership
  and `app_user` only while `status = 'ACTIVE'`, so the manager is null when there is no PM row or the account is not
  Active. `member_count` is a subquery that counts every membership row, whatever the account status. The order is
  `ORDER BY jira_project_key COLLATE "C"`.
- `ProjectController.list()`: `@GetMapping`, `@Operation(operationId = "listProjects")`,
  `@PreAuthorize("hasRole('PROJECT_MANAGER')")`, then `ResponseEntity.ok(service.list())`. It takes no parameters,
  so a client cannot pass paging or scope.
- DTOs: `ProjectListResponse(DepartmentRefResponse department, List<ProjectRowResponse> projects)` reuses identity's
  `dto.DepartmentRefResponse`. `ProjectRowResponse(projectId, jiraProjectKey, projectManager, memberCount, status)`
  has a nested `ProjectManagerRef(userId, fullName)`. A null manager is written as `"projectManager": null`; the
  contract makes the field required and nullable.

The list operation, `ProjectList`, `ProjectRow`, `ProjectManagerRef` and `DepartmentRef` were already in
`frontend/openapi/tsolve-api.yaml` (added in Task 2's commit), and the code matches them. The contract did not
change. `npm run generate:api` (Node 24.22.2) left no diff in `src/shared/api/generated`.

**Who wrote what.** Claude wrote all code and tests (Codex is out of quota, D49).

**Red step.** The DTOs, the interface method and two `UnsupportedOperationException` placeholders (service and
repository) came first, so the tests could compile; there was no GET handler yet. Unit run: 204 tests, 11 failures and
3 errors, all in the new tests. The controller tests got 405 instead of 200/403/500, and the `list()` handler
reflection test found no method. The service tests failed on the placeholder or on the access assertions.
`refusesAnonymousList_AC_007_2` passed before the handler existed, because the security filter answers 401 before
dispatch. `ProjectListIT`: 10 tests, 10 failures (405).

### Decisions
- **Sort order.** `COLLATE "C"` gives code-point order, the same as Java's `String.compareTo` for the stored
  `[A-Z][A-Z0-9_]*` keys. It also stops the order from depending on the database locale. A mutation check showed the
  locale matters: without `COLLATE "C"` the test database put `PLS_A` before `PLSB`, and the two sort-sensitive IT
  tests failed. The spec says only "ascending", so this is a reading of the spec, not a gap.
- **No transaction on `list()`.** `testConnection` has none either, so the two behave the same. The method makes two
  independent reads, and READ COMMITTED gives each statement its own snapshot, so a read-only transaction would not
  make them consistent. The Workspace check runs before either read.
- **Manager display.** Only `u.status = 'ACTIVE'` decides whether the manager is shown, not the stricter D27 reconnect
  rule. The IT shows an Active manager who moved to another Department and has no PM role, and the list still names
  them (AC-004.4, row 30).
- **The count does not filter on account status.** The IT counts a DEACTIVATED former manager and a DEACTIVATED
  MEMBER.
- No connection-status fan-out and no Jira call: the service has no tracker call on this path.
  `verifyNoInteractions(tracker, audit, transactionManager, projectRepository)` in the unit test, `tracker.calls()`
  empty and before/after snapshots of the tenant and main databases in the IT.
- One shared IT context: `ProjectListIT` has the same properties and `@Import(ProjectApiITConfiguration.class)` as the
  other Project ITs. No new `@MockBean`, `@TestPropertySource` or container. In the full Project IT run it took about
  0.7 s.

### Tests added (name → AC)
`ProjectServiceImplTest` (Mockito):
- `listsTheOwnDepartmentAndItsRowsWithoutJiraOrWrites_AC_004_1_AC_004_3_AC_004_8`: exact `ProjectListResponse`, only
  `findCallerDepartment` and `listProjects` on the repository, no tracker, audit, transaction or JPA repository calls.
- `returnsTheDepartmentWhenItHasNoProjects_AC_004_7`
- `refusesAnIneligibleListCallerBeforeReadingProjects_AC_007_4_AC_007_8`,
  `refusesAMissingListPrincipalBeforeAnyRead_AC_007_4_AC_007_8`
- `propagatesAnUnboundListWorkspaceBeforeAnyRead_AC_007_5`
- `derivesTheListScopeFromTheBindingAndSessionOnly_AC_007_7_AC_007_8`: the order is Workspace, then Department,
  then the list; `list()` has no parameters.

`ProjectControllerTest` (`@WebMvcTest`):
- `returnsTheListExampleWithANullManager_AC_004_1_AC_004_3_AC_004_4_AC_004_5`: the spec's JSON example, strict, with
  `"projectManager": null` present and no `page`.
- `returnsTheDepartmentWithAnEmptyArray_AC_004_7`
- `ignoresClientPagingAndScopeParameters_AC_004_2_AC_007_7`
- `declaresListProjectManagerAccess_AC_007_1`, `refusesAnonymousList_AC_007_2`, `refusesEmployeeList_AC_007_3`,
  `refusesAdminList_AC_007_3`, `rendersIneligibleListCaller_AC_007_4`, `rendersUnboundListWorkspace_AC_007_5`
  (rows 25–26).

`ProjectListIT` (new; real PostgreSQL, ACME/BETA tenant databases):
- `listsTheOwnDepartmentProjectsByKeyWithActiveManagersAndAllMemberships_AC_004_1_…_AC_004_6`: four Projects inserted
  out of order (`PLS9`, `PLSA`, `PLSB`, `PLS_A`, mixed Active/Removed). The checks: the exact body; no manager row →
  null; DEACTIVATED manager → null; Active manager from another Department without the PM role → shown; counts 1/3/2/0
  including DEACTIVATED rows; a foreign Department's Project left out (rows 13, 30).
- `readsWithoutJiraOrAnyPersistedChange_AC_004_8`: snapshots of department, app_user, user_role, project,
  project_membership and audit_log_entry, in the tenant and main databases (row 13).
- `returnsTheDepartmentWithAnEmptyProjectList_AC_004_7`
- `refusesAnIneligibleCallerWithoutReadingProjects_AC_007_4` (account deactivated, role removed, no Department,
  Department deactivated): 403 MSG08, empty params, no field errors, no Jira call, no change (row 26).
- `failsUnboundWithoutReadingAnyDatabase_AC_007_5`: 500 MSG06, main database unchanged (row 26).
- `listsOnlyTheBoundWorkspaceDatabase_AC_007_6`: the same key `PLSB` in BETA, with a different id and count (row 27).
- `ignoresDepartmentAndWorkspaceScopeFromTheRequest_AC_007_7`: forged `departmentId`/`workspaceId` query parameters.

### Files
- Modify: `backend/src/main/java/vn/tsolve/project/controller/ProjectController.java` (+10),
  `repository/ProjectLinkRepository.java` (+27: `listProjects`), `service/ProjectService.java` (+4: `list()`),
  `service/impl/ProjectServiceImpl.java` (+26 −5: `list()`, `checkScope()`, `CallerScope`)
- Create: `backend/src/main/java/vn/tsolve/project/dto/ProjectListResponse.java`, `dto/ProjectRowResponse.java`
- Test: `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java` (+76),
  `controller/ProjectControllerTest.java` (+116)
- Test (create): `backend/src/test/java/vn/tsolve/project/ProjectListIT.java` (289 lines)
- Unchanged: identity (its `DepartmentRefResponse` reused as is), the JPA entity and repository,
  `ProjectApiITConfiguration`, the contract and the generated client.

### Verify
Backend, from `backend/` (JAVA_HOME = IntelliJ jbr 21):
- `mvn -B spotless:apply`, then
  `mvn -B -Dapi.version=1.44 -Dtest='Project*Test,*Project*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='Project*IT' verify`
  → Surefire `Tests run: 246, Failures: 0, Errors: 0, Skipped: 0` (ProjectServiceImplTest 127,
  ProjectControllerTest 77, ProjectConnectionSecrecyTest 8, and others); Failsafe
  `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0` (ProjectReconnectIT 19, ProjectCreationIT 15, ProjectListIT 10,
  ProjectConnectionIT 9, ProjectDoneStatusesMigrationIT 5, ProjectMembershipReviewAccessIT 3); `spotless:check`
  passed; `BUILD SUCCESS`.
- Mutation check: with `COLLATE "C"` removed, `ProjectListIT` failed 2 of 10 (the sort-sensitive tests); the change
  was then restored.
- Full `mvn verify`: left to the main session.

Frontend, from `frontend/` (Node 24.22.2): `npm run generate:api` → no diff in `src/shared/api/generated` (and none
in `openapi/`). No frontend file changed, so format, lint, typecheck, test and build were not run for this task.

Harness, from the repo root:
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-004` →
  `trace project-integration (STORY-004): 8/8 acceptance criteria have a test` (exit 0).
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-007` →
  `trace project-integration (STORY-007): 8/8 acceptance criteria have a test` (exit 0).

Secrets: `gitleaks detect --no-git --redact --no-banner --source <file>` on each of the 9 changed or new files →
`no leaks found` for every file.

### Deviations
- The task's Files list has no change to the POST paths. The scope check was pulled out of `checkScopeAndKey` into
  `checkScope()`, so the list does not copy it; the POST behaviour and its tests are unchanged (all green).
- No `@Transactional(readOnly = true)` on `list()`, where `backend/AGENTS.md` §3 expects it on reads (see Decisions;
  this matches `testConnection`).

### Bugs
None found.

### Blockers
none

### Next up
Full `mvn verify` and commit after the user agrees; then Task 6 (Show Department and Projects on Jira Integration).

### Commit
default@05aeb3ad — `feat(project-integration): list the caller's Department Projects by key, read only (KAN-127)`, committed after the user agreed (13/11).

### Full verify (main session)
- `mvn -B spotless:apply && mvn -B -Dapi.version=1.44 verify` → unit `Tests run: 1641, Failures: 0, Errors: 0, Skipped: 0`;
  IT `Tests run: 491, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`.
