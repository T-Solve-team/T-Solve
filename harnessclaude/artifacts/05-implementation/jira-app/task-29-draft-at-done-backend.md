# Task 29: draft-at-done-backend

**Module:** jira-app · **Assignee:** Claude (for the user) · **Status:** evaluating · **Iteration:** 2

## Session 2026-11-18

### Working on: draft-at-done-backend (KAN-371)
- Contract first: `POST /api/v1/jira-app/events/issue-updated` + `IssueUpdatedPayload` (every field optional, 202
  whatever the content) in `tsolve-api.yaml`; `npm run generate:api`.
- Changeset `048-jira-app-issue-done-state.sql`: table `jira_issue_done_state` with its own `<table>_id` key and a
  unique (project_id, jira_issue_key) — **deviation from the design's composite PK**, because backend AGENTS §7 wants
  `<table>_id` on every tenant table. Entity + `JiraIssueDoneStateRepository.record` (native upsert).
- `SolutionDraftRepository.insertIfAbsent` (`ON CONFLICT DO NOTHING`); `SolutionDraftService.findIssueDone` and
  `recordIssueStatus(IssueStatusChange)` — state upsert + Draft insert in one transaction.
- jiraapp: `JiraAppIssueEventController` (`ROLE_JIRA_APP`, principal from the FIT), `IssueUpdatedServiceImpl`
  (Workspace from the FIT's cloudId; reads in the Workspace; Jira status read via new `RestJiraStatusClient` only
  when the issue was never recorded, outside a transaction; failed read = a move), `AdfText` (port of
  `watchDone.js#issueText`), `IssueText` from Task 24.
- Tests: `IssueUpdatedServiceImplTest` (10), `JiraAppIssueEventControllerTest` (3), `AdfTextTest` (2),
  `SolutionDraftServiceImplTest` (+2), `SolutionFormTicketIT` (+1, DB: upsert, no replace, never-recorded).
- Checks: `mvn test` on `vn.tsolve.jiraapp.**` and `vn.tsolve.ticket.**` unit tests — all pass except
  `JiraRestIssueSourceTest.AC_001_7_enforcesTheReadTimeoutAgainstASlowServer` ("Unable to establish loopback
  connection", this machine's known JDK/loopback issue, untouched code). `mvn spotless:check` clean; frontend
  `format:check`, `typecheck` clean. **The IT was not run locally (Docker not running)** — left to CI.
- Trace STORY-005: AC-005.6 to AC-005.10 covered; AC-005.3 ambiguity pre-existing.
- Commit: default@1dbf74ac

### Next up
Task 30 issue-updated-trigger
