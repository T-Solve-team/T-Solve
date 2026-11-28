# Test Report: tracker — Quick change BUG-403 (KAN-467): bind the issue-created webhook to its site's Workspace

**Date:** 2026-11-27 · **Attempt:** 2
**State tested:** worktree `T-Solve-KAN-467`, branch `fix/KAN-467-tracker-bind-issue-created`, uncommitted on 3098620b
(11 changed files + the 2 review files; the same file list as the developer's hand-off).
**Commands (reviewer, targeted only, per the card):**
`mvn -B test -Dtest=IssueCreatedServiceImplTest,JiraAppEventControllerTest -Dspotless.check.skip=true` ·
`mvn -B -q spotless:check` (in `backend/`) · `bun harnessclaude/hooks/sdd.ts trace tracker STORY-003 STORY-011` ·
`sdd.ts check tracker` · `sdd.ts diff tracker`

## Results
- Total: 22 · Passed: 22 · Failed: 0 · Skipped: 0 · BUILD SUCCESS
  - `JiraAppEventControllerTest`: 8, 0 failures (12.6 s, Spring context start)
  - `IssueCreatedServiceImplTest`: 14, 0 failures (0.9 s)
- Compile: passed as part of `mvn test`. Spotless: `spotless:check` exit 0 (ratchet from `origin/main`).
- Spec: `check tracker` "8 stories, 60 acceptance criteria — OK"; `diff tracker` "the spec is unchanged since it was
  approved (2026-11-27T03:11:47.263Z)".
- Developer's verify (from the card, not re-run): targeted 22/0 BUILD SUCCESS, trace 16/16; the end
  `mvn spotless:check test` was killed for low memory (no result); `SuggestionResultRepositoryIT` not run on this state
  (8/0 on the first fix). Full suites: GitHub CI on the PR (no PR yet).

## Coverage
Not measured (targeted run). The spec sets no coverage threshold.

## Acceptance Criteria → Tests
```
  ✓ AC-003.1  IssueCreatedServiceImplTest, JiraAppEventControllerTest, SuggestionResultRepositoryIT
  ✓ AC-003.4  IssueCreatedServiceImplTest
  ✓ AC-003.6  JiraAppEventControllerTest
  ✓ AC-003.8  IssueCreatedServiceImplTest
  ✓ AC-003.9  IssueCreatedServiceImplTest
  ✓ AC-011.1  IssueCreatedServiceImplTest, SuggestionJobQueueSelectionTest, TrackerProjectRolesTest, WorkerSuggestionJobQueueTest, JiraAppEventControllerTest, AdfTextExtractorTest, JiraClientRolesTest, SuggestionResultRepositoryIT
  ✓ AC-011.2  IssueCreatedServiceImplTest, TrackerProjectRolesTest, JiraClientRolesTest
  ✓ AC-011.3  SuggestionJobHandlerTest, SuggestionJobServiceImplTest
  ✓ AC-011.4  SuggestionJobServiceImplTest, TrackerProjectRolesTest, SuggestionResultRepositoryIT
  ✓ AC-011.5  SuggestionJobServiceImplTest, SuggestionResultRepositoryIT
  ✓ AC-011.6  SuggestionJobHandlerTest, SuggestionJobServiceImplTest
  ✓ AC-011.7  SuggestionJobServiceImplTest, SuggestionResultRepositoryIT
  ✓ AC-011.8  IssueCreatedServiceImplTest
  ✓ AC-011.9  InProcessSuggestionJobQueueTest, SuggestionJobQueueSelectionTest
  ✓ AC-011.10  SuggestionJobQueueSelectionTest, WebSuggestionJobQueueTest
  ✓ AC-011.11  WebSuggestionJobQueueTest
  ! ids in tests but not in the spec: AC-001.72, AC-002.13, AC-002.16, AC-010.13
trace tracker (STORY-003, STORY-011): 16/16 acceptance criteria have a test
```
AC-003.9 is checked by `touchesNothingForASiteWithNoWorkspace_AC_003_9_BUG_403` and
`touchesNothingInAProcessWithNoWorkspaceCatalog_AC_003_9` (no project lookup, no insert, no job).
`touchesNothingWithoutAJiraAppCaller_AC_003_9` checks a null caller, which AC-003.9 does not name (review, issue 2).
The four ids not in the spec come from other test files, not from this change.

## Failures
None.

## Notes
- Attempt-1 untested paths now covered: no `WorkspaceCatalog` bean (`StaticListableBeanFactory` with no bean), null
  caller, binding cleared after `record` throws (`unbindsTheWorkspaceWhenRecordingFails_BUG_403`), non-Jira-App
  authentication → 403 with the service untouched (`rejectsCallOfAnAuthenticationThatIsNotTheJiraApp_BUG_403`).
- The tests use a real `ThreadLocalTenantContext` (known = the test Workspace), so `runIn` and its `finally` are
  exercised, not mocked.
- `JiraAppEventControllerTest` text blocks were reflowed by `spotless:apply` (palantir 2.80.0, ratchet); format only.
- Still to run before the commit (not by the reviewer): `mvn spotless:check test` and
  `-Dit.test=SuggestionResultRepositoryIT`, or CI on the PR.
- No flaky, skipped or slow tests (the controller class's 12.6 s is the Spring context start).
