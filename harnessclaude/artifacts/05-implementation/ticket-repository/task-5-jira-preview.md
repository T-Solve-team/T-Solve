# Task 5: Jira preview through the CSV preview endpoints

**Module:** ticket-repository
**Assignee:** Đặng Nhật Minh (code by Codex, reviewed by Claude)
**Status:** evaluating
**Commit:** default@791d4dd (reviewed; `mvn verify` with ITs and Spotless passed in a clone of the branch)
**Iteration:** 1

## Session 2026-11-07

### Working on: jira-preview

- Implemented only Task 5 on `feat/KAN-45-ticket-repository-jira-import`; changes remain uncommitted.
- Ported the fill-solution endpoint, service logic, writable Solution fields and shared length constant from
  KAN-31 commit `1c4a6f78`. Per the user's decision, the gate is `source == JIRA_IMPORT`, with no `ImportType`
  dependency or merge. A CSV preview is rejected with 409/MSG06.
- Copied `FillSolutionRequest.java` and `SolutionTooLongException.java` byte for byte from that commit.
  Their Git blob hashes match `c8c7f7b65a5ca5e64d3f76958c758bf62c0436c1` and
  `d268341ccabac44ecb370a0b75355dc58d8daf38`, respectively.
- Kept the port's request validation: required sections produce 400/MSG01 with every missing section named
  in `fieldErrors`, before the service is called. The service trims all sections, counts code points including
  Workaround, and rejects totals above 10,000 with 400/MSG02 before touching rows.
- Fill locks the preview using Finish's lock, deduplicates row numbers, checks the entire selection before
  mutation, rejects missing rows with 404 and Error/Removed rows with 409/MSG06, and stores independent Solution
  values on Needs Solution and Valid rows. All selected rows become Valid.
- Added `sourceUrl` and `comments` to row detail. The existing MapStruct mapper maps both by property name;
  its source needs no change. Task 4 already provides source, dates, draftSavedAt, counts and MSG35 in the
  preview response. Existing rows-list and remove-rows implementations already serve Jira previews.
- Added `JiraPreviewServiceTest` and eight endpoint tests in `JiraPreviewControllerTest`; existing CSV tests in
  `ImportControllerTest` are unchanged. Coverage includes Jira paging, snapshot and full Solution detail,
  replacement and independent copies, missing fields, the length boundary, removal, ownership, project access
  and Finish lock contention.
- No product bug discovered; `docs/bug-log.md` is unchanged.

### Verification

- Initial red run: 23 tests, four expected assertion failures (the missing endpoint and missing Jira URL).
- Fill red run with the API shape in place: 31 tests, seven failures and three errors caused by the
  deliberately unimplemented fill method; the snapshot and endpoint-validation tests passed.
- This sandbox resolves Maven's default cache to inaccessible `C:\.m2\repository`, and Java cannot use the
  original cache directly under `C:\Users\admin\.m2\repository`. Copied that existing offline cache under
  ignored `backend/target/offline-repository` and used `-Dmaven.repo.local=target/offline-repository`.
- Focused command, from `backend/`:
  `mvn -o -q '-Dmaven.repo.local=target/offline-repository' test '-Dtest=JiraPreviewServiceTest,ImportPreviewServiceImplTest,ImportControllerTest,JiraFinishTest,ImportConfirmServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false'`
  exited 0: 83 tests, zero failures/errors/skips. Log: `backend/target/task-5-focused.log`.
- Full unit command: `mvn -o '-Dmaven.repo.local=target/offline-repository' test` exited 0:
  `Tests run: 623, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (50.653 seconds).
  Log: `backend/target/task-5-unit.log`. This includes 16 new Jira service tests and eight new controller tests.
- `git diff --check` passed.
- After the controller test relocation, `sdd.ts trace ticket-repository STORY-002` reports 8/11:
  AC-002.4 resolves to `backend/src/test/java/vn/tsolve/ticket/controller/JiraPreviewControllerTest.java`.
  AC-002.6, AC-002.10 and AC-002.11 remain open frontend criteria for Task 9. No module path or frontend
  changes were made to alter this report.
- Workspace audit reports one existing failure (`fe-foundation` spec differs from its approval) and 14
  warnings, including unregistered ticket-repository artifacts. No audit metadata was changed.
- No integration tests, Docker or Spotless run, as requested. No commit, branch, push, merge, rebase, spec,
  frontend or tracking changes made. `progress.json` and `events/events.jsonl` were already modified at entry.

### Blockers

None. No open specification question.

### Follow-up: controller test trace paths

- Moved all eight added controller tests unchanged into
  `backend/src/test/java/vn/tsolve/ticket/controller/JiraPreviewControllerTest.java`, retaining their
  `_AC_002_n` names and the original `@WebMvcTest`, method security import, test properties, MockMvc and
  three service mock beans.
- Inspected HEAD's `ImportControllerTest`: it does not construct `ImportPreviewRowResponse`, so no
  compatibility edit is needed. The requested `git checkout --` was blocked by the sandbox's inability
  to create the Git index lock outside the worktree. Restored directly from HEAD's blob instead and
  verified byte-for-byte equality; the restored class compiles and passes all 13 tests.
- Focused command from `backend/`:
  `mvn -o -q test -Dtest='JiraPreviewControllerTest,ImportControllerTest,JiraPreviewServiceTest' -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.repo.local=target/offline-repository`
  exited 0: 37 tests (8 controller, 13 original import controller, 16 service), zero failures/errors/skips.
  Log: `backend/target/task-5-relocation-focused.log`.
- Full command from `backend/`: `mvn -o test -Dmaven.repo.local=target/offline-repository` exited 0:
  `Tests run: 623, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (45.122 seconds).
  Log: `backend/target/task-5-relocation-unit.log`.
- Root command: `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-002` reports
  `trace ticket-repository (STORY-002): 8/11 acceptance criteria have a test`.
  AC-002.4 now names `backend/src/test/java/vn/tsolve/ticket/controller/JiraPreviewControllerTest.java`.
  Exit 1 is expected because AC-002.6, AC-002.10 and AC-002.11 remain open for Task 9's web work.
  Log: `backend/target/task-5-relocation-trace.log`.
- Verified the eight moved test bodies are unchanged, and all other pre-existing source changes, spec,
  `progress.json`, events and approvals retain their entry hashes. No commit, frontend, integration test
  or Spotless action was performed.

### Next up

Review the uncommitted Task 5 changes. Tracking remains in-progress at the user's request.
