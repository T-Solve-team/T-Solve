# Task 3: Create a Project with membership and audit atomically (KAN-127)

**Module:** project-integration
**Assignee:** Đặng Tấn Bình (Developer: Claude, supervising Codex CLI)
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-12

### Working on: project-create (STORY-002, STORY-007)

Branch `feat/KAN-127-project-integration-add-project`. `POST /api/v1/projects` now creates a new Project.
`ProjectController.create` (PM-only, `@Valid`) calls `ProjectService.create`. The service checks the bound Workspace,
the caller and their own Department, the key format, the range and the selected ids. It then checks the requested-key
link, reads Jira again outside any transaction, checks the canonical-key link and the selected ids against the fresh
statuses. After that it runs one `TransactionTemplate`. Inside it, the service re-reads the caller's Department and
locks and re-reads both links. It then inserts the Project with `ON CONFLICT (jira_project_key) DO NOTHING RETURNING`,
upserts the caller's `PROJECT_MANAGER` membership, reads the saved projection and records the audit entry
(`TRACKER_CONFIGURATION_CHANGE`, `PROJECT`). The controller answers 201 or 200 from `ProjectSaveResult.created`.
The contract was not changed (Task 2 already holds `createProject`).

**Who wrote what.**
- Codex CLI, one run (`codex exec -s workspace-write --ephemeral`, prompt `kan127-t3-step1.md`): it wrote the new
  create tests in `ProjectControllerTest` and `ProjectServiceImplTest`, then stopped with "You've hit your usage limit"
  (resets 14/11) before the skeletons, the IT and the test support. It changed no other file.
- Claude wrote everything else: the `ProjectApiITConfiguration` hook, `ProjectCreationIT`, the DTOs, skeletons,
  repository SQL, service and controller. Claude also reviewed Codex's tests against every Task 3 AC and kept them
  unchanged except one rename: the two new `validates…CreateBeforeMethodSecurity` tests cite `_AC_002_1` (blank key
  answered 400 before method security), not `_AC_007_3`, which is about a valid body getting 403.
- Coordinator addition (Task 2 review, D48, MINOR-2): `ProjectCreationIT.storesTheCanonicalKeyAndFindsTheCommittedProjectByIt_AC_002_8_AC_002_14`
  saves `PCRALIAS`, which Jira answers as canonical `PCRCANON`, and then
  `findActivePublicIdByJiraProjectKey("PCRCANON")` returns the committed id. The existing seeded-row AC-002.14 test
  is unchanged. The two Task 2 `_AC_007_3` controller tests are left for the main session.

**Red step (Claude ran it).** Against the skeletons, unit tests ran 171: 66 failures and 1 error, all in the new create
tests. They failed by assertion (500 instead of 201, 400, 403, 409 or 503 in MVC, `UnsupportedOperationException` in
the service). The one error was a Mockito `UnnecessaryStubbing` in `doesNotPretendToReconnectAnEligibleRemovedProject`,
because the skeleton threw before the stubbed lookup. `ProjectCreationIT` ran 15: 14 failed by assertion; the
unbound-Workspace case passed because the skeleton also answered 500.

### Decisions
- Check order follows D44 and AC-002.21: Bean Validation, then Workspace, caller, own Department and key format, then
  range and null or blank ids, then the requested-key link, then Jira, then the canonical-key link, then ids missing
  from the fresh statuses. The service also refuses a null range and null or empty ids, for direct callers (the
  IT calls the service directly).
- `testConnection` and `create` share `checkScopeAndKey`, `readJiraProject` and `checkCanonicalLink`; behaviour of the
  connection test is unchanged (its 49 service tests and 9 IT cases stay green).
- The recheck helper for Task 4 is `recheckLinks`/`lockedLink`. It finds the link, locks the row (`lockProject`,
  `SELECT … FOR UPDATE`) and reads the link again, because `FOR UPDATE` does not re-read the joined manager rows.
- Lost insert: `insertProject` returns empty, and the service reads the now-committed winner and throws MSG49 with its
  Department's name. It never tries a reconnect.
- Reconnect is Task 4. In this task an eligible Removed Project found by the in-transaction recheck throws
  `UnsupportedOperationException`, which answers 500 MSG06 and writes nothing. No path reports a reconnect as a
  success.
- Range: `BigDecimal` checked as `stripTrailingZeros().scale() > 0`, `< 1` or `> 2147483647` → 400 MSG44 on
  `reviewRange` with empty params; never truncated; `1.0` is a whole number and is stored as 1. Ids are exact strings,
  deduplicated in first-seen order.
- Audit details are built from the saved projection, so they match the stored values: `{operation: "CREATE",
  jiraProjectKey, departmentId (public UUID), doneStatusIds, reviewRange}`.
- `ProjectResponse.status` is a `String` (`ACTIVE`/`REMOVED`) so the entity enum stays inside the module's entity layer.
- Test support: `FakeTrackerProjectService.beforeAnswer(Runnable)` runs during the Jira read, after the
  no-active-transaction assertion. The race tests install a `CyclicBarrier(2)` there, so both Saves pass the local
  link check before either writes. The AC-002.17 tests make another, auto-committed change there, and the AC-002.12
  test clears the security context there. No sleeps.
- One write transaction is proven in the IT by equal `xmin` on the project, membership and audit rows (AC-002.18).

### Files
- Modify: `backend/src/main/java/vn/tsolve/project/service/ProjectService.java` (+5),
  `service/impl/ProjectServiceImpl.java` (+167 −28), `controller/ProjectController.java` (+17),
  `repository/ProjectLinkRepository.java` (+68)
- Create: `backend/src/main/java/vn/tsolve/project/dto/ProjectResponse.java` (12 lines), `dto/ProjectSaveResult.java` (4 lines)
- Test: `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java` (+448 −1),
  `controller/ProjectControllerTest.java` (+317), `ProjectApiITConfiguration.java` (+14 −4),
  `ProjectCreationIT.java` (create, 457 lines)
- `CreateProjectRequest.java` unchanged.

### Verify
Backend, from `backend/` (JAVA_HOME = IntelliJ jbr 21):
- `mvn -B test -Dtest='ProjectServiceImplTest,ProjectControllerTest' -Dspotless.check.skip=true` →
  `Tests run: 171, Failures: 0, Errors: 0, Skipped: 0` (controller 68, service 103).
- `mvn -B -Dapi.version=1.44 -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ProjectCreationIT,ProjectConnectionIT -Dspotless.check.skip=true verify`
  → `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0` (ProjectCreationIT 15, ProjectConnectionIT 9).
- `mvn -B spotless:apply && mvn -B -Dapi.version=1.44 verify` → unit (Surefire)
  `Tests run: 1600, Failures: 0, Errors: 0, Skipped: 0`; IT (Failsafe) `Tests run: 462, Failures: 0, Errors: 0, Skipped: 0`;
  `spotless:check` passed; `BUILD SUCCESS` (15:04 min).

Frontend: not run; the contract and generated client are unchanged.

Harness, from the repo root:
- `bun harnessclaude/hooks/sdd.ts check project-integration` → `spec project-integration: 7 stories, 103 acceptance criteria — OK` (exit 0).
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-002 STORY-007` →
  `trace project-integration (STORY-002, STORY-007): 29/29 acceptance criteria have a test` (exit 0).

Secrets: `gitleaks detect --no-git --redact --no-banner --source <file>` on each of the 10 new or changed files →
`no leaks found` for every file.

### Deviations
- Codex reached its usage limit after writing the tests, so Claude wrote the remaining code (the brief's fallback).
- No deviation from the task or design. Reconnect stays out (Task 4), as the task says.

### Bugs
None found.

### Commit
default@15b491d — `feat(project-integration): create a Project with its manager and audit at once (KAN-127)` (header shortened by the main session for commitlint's 100-character limit), committed after the user agreed (12/11)

### Blockers
none

### Next up
Commit after the user agrees; then Task 4 `project-reconnect`.
