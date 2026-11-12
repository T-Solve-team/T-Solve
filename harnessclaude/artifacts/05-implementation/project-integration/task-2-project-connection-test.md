# Task 2: Test a Project connection and prefill settings (KAN-127)

**Module:** project-integration
**Assignee:** Đặng Tấn Bình (Developer: Claude, supervising Codex CLI)
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-12

### Working on: project-connection-test (STORY-001, STORY-002 request validation, STORY-007)

Branch `feat/KAN-127-project-integration-add-project`. The contract now holds the `projects` tag and all three
operations (`testProjectConnection`, `createProject`, `listProjects`) with their schemas; the generated client
was produced by `npm run generate:api` only. The backend implements `POST /api/v1/projects/connection-test`:
`ProjectController` (PM-only, `@Valid`), `ProjectService.testConnection`, the JDBC `ProjectLinkRepository`
(caller's own Active Department, link lookup with the protected-manager flag) and the two module exceptions.
`CreateProjectRequest` exists with its Bean Validation only; create and list are Tasks 3 and 5.

**Who wrote what.** The Codex CLI wrote the code test-first in two runs (`codex exec -s workspace-write --ephemeral`).
Claude wrote the prompts, reviewed each diff against the task's ACs, the design and `backend/AGENTS.md`, ran
every check and made the small fixes listed below.
- Codex run 1: the YAML contract, all four test files, and compile-only skeletons. Its sandbox could not run
  Maven (`Could not create local repository at C:\.m2\repository`), so Claude ran the red step: the unit tests
  compiled, `ProjectControllerTest` 26/26 failed (404, no handler) and `ProjectServiceImplTest` 45/49 failed by
  assertion (`UnsupportedOperationException` from the skeleton, missing Bean Validation); the 4 passing were the
  renamed lookup tests and the record-shape test.
- Codex run 2: the production code (DTO annotations, repository SQL, service, controller). Its Maven again could
  not run; Claude ran the tests.
- Claude's edits:
  - Tests: Spring's `jsonPath(...).value(...)` re-reads the value as the expected object's class, so
    `Map.of(...)` came back null. Empty params now use `.isMap()` + `.isEmpty()`, other maps
    `new LinkedHashMap<>(...)` and lists `new ArrayList<>(...)`. A lenient default `findLink(anyString())` stub
    was added because Mockito strict stubs rejected the extra canonical-key lookups. The IT fixture casts its
    nullable range (`CAST(? AS INT)`). No assertion was weakened.
  - Production: `refuseIfLinked` refuses every link whose status is not `REMOVED` (Codex had written
    `"ACTIVE".equals`); one comment on the unbound-Workspace check.
  - Contract: `minLength: 1` on `CreateProjectRequest.jiraProjectKey`, the same as the connection-test request.
- Reverted: nothing. Both runs wrote only the task's files.

### Decisions
- Order in `testConnection`, as AC-007.8 and D36 ask: `TenantScope.currentWorkspaceId()` → caller from
  `TSolveUser.current()` → own Department by SQL (Active account, `PROJECT_MANAGER` row in `user_role`, Active
  Department) → strip, upper-case with `Locale.ROOT`, check the format (MSG48) → requested-key link (MSG49) →
  `TrackerProjectService.findProject` → canonical-key link (AC-001.19/21/22) → prefill. No `@Transactional`; no
  writes.
- `ProjectConnectionTestResponse` reuses the tracker's `TrackerStatus` record for `statuses` and leaves out
  `projectId` when null (`@JsonInclude(NON_NULL)` on that component only). The statuses pass through as the
  tracker returns them; the tracker already removes duplicates (AC-013.9).
- Reconnect prefill: stored ids kept in first-seen order without duplicates, limited to the returned ids; the
  Done-category ids when none remain. The stored range is used as it is (0 and -1 included), 3 only for SQL NULL.
- `ProjectApiITConfiguration` is the shared `@TestConfiguration` (imports `TwoTenantDatabases`, adds a `@Primary`
  `FakeTrackerProjectService` that throws when a transaction is active). `ProjectConnectionIT` uses the same
  `@SpringBootTest` properties as `UserListIT`, so later Project ITs share its cached context.

### Files
- Modify: `frontend/openapi/tsolve-api.yaml` (+199)
- Generate: `frontend/src/shared/api/generated/` — `projects/projects.ts`, `projects/projects.msw.ts`, 9 model
  files, and the three index files (generated names include `testProjectConnection`, `createProject`,
  `listProjects`, `getListProjectsQueryKey`)
- Modify: `backend/src/main/java/vn/tsolve/project/service/ProjectService.java`, `service/impl/ProjectServiceImpl.java`
- Create: `backend/src/main/java/vn/tsolve/project/controller/ProjectController.java`,
  `repository/ProjectLinkRepository.java`, `dto/ProjectConnectionTestRequest.java`, `dto/CreateProjectRequest.java`,
  `dto/ProjectConnectionTestResponse.java`, `exception/ProjectAccessDeniedException.java`,
  `exception/ProjectTrackerUnavailableException.java`
- Test: `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java` (modify; the old
  `_AC_003_1` test now cites AC-002.14 and `_AC_003_8` was dropped from the negative lookup test),
  `controller/ProjectControllerTest.java`, `ProjectApiITConfiguration.java`, `ProjectConnectionIT.java` (create)

### Verify
Backend, from `backend/` (JAVA_HOME = IntelliJ jbr 21):
- `mvn -B test -Dtest='ProjectServiceImplTest,ProjectControllerTest' -Dspotless.check.skip=true` →
  `Tests run: 75, Failures: 0, Errors: 0, Skipped: 0` (controller 26, service 49).
- `mvn -B -Dapi.version=1.44 -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ProjectConnectionIT -Dspotless.check.skip=true verify`
  → `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`.
- `mvn -B spotless:apply && mvn -B -Dapi.version=1.44 verify` → unit (Surefire) `Tests run: 1503, Failures: 0, Errors: 0, Skipped: 0`;
  IT (Failsafe) `Tests run: 447, Failures: 0, Errors: 0, Skipped: 0` (including `SendBackApiIT` 7/7);
  `spotless:check` passed; `BUILD SUCCESS` (17:35 min).

Frontend, from `frontend/` with Node v24.22.2, after `npm run generate:api`:
- `npm run format:check` exit 0; `npm run lint` exit 0 (`✖ 1 problem (0 errors, 1 warning)`, the existing
  `react-refresh/only-export-components` warning in `features/tickets/components/TicketListFilters.tsx`, not this task);
  `npm run typecheck` exit 0; `npm run test` → `Test Files 86 passed (86)`, `Tests 1499 passed (1499)`;
  `npm run build` → `✓ built in 21.45s`.
- A second `npm run generate:api` leaves `src/shared/api/generated` unchanged (sha1 of every file before and after:
  `6e099efa…` both times).

Harness, from the repo root:
- `bun harnessclaude/hooks/sdd.ts check project-integration` → `spec project-integration: 7 stories, 103 acceptance criteria — OK` (exit 0).
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-001 STORY-002 STORY-007` →
  `35/51 acceptance criteria have a test` (exit 1). ✓ all of AC-001.1–AC-001.22 and AC-007.1–AC-007.8; ✓ AC-002.4,
  AC-002.6, AC-002.14, AC-002.19, AC-002.20. ✗ AC-002.1–002.3, 002.5, 002.7–002.13, 002.15–002.18 and 002.21: the
  create flow of Tasks 3 and 4, so STORY-002's trace reaches exit 0 only when those land.

### Deviations
None from the task or design.

### Bugs
None found.

### Commit
default@6d71a20d, committed after the user agreed (12/11). The main session reworded one `createProject` description sentence in `tsolve-api.yaml` (and regenerated the client) because gitleaks read a comma-separated list of field rules as a generic API key (false positive); meaning unchanged

### Blockers
none

### Next up
Commit after the user agrees; then Task 3 `project-create`.
