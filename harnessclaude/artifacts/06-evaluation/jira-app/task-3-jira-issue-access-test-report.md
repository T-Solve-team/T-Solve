---
module: jira-app
feature: jira-issue-access
task: 3
attempt: 1
iteration: 2
date: 2026-11-13
reviewer: main session (harnessclaude evaluate, run-tests)
---

# Test Report: jira-app — Task 3 "Jira issue access" (KAN-126)

Branch `feat/KAN-126-jira-app-solution-form-v2`, head `221d53ca` (repo `default`, monorepo). The feature's code is at `2dba1a73`. Run on 2026-11-13 by the main session; the review is `task-3-jira-issue-access-review.md`.

## Results

| Check | Command (directory) | Result |
|-------|---------------------|--------|
| JiraIssueAccessImplTest | `mvn verify` (`backend/`) | PASS. 7/7 |
| JiraIssueAccessReviewAdapterTest | | PASS. 4/4 |
| ConfigJiraWorkspaceLookupTest | | PASS. 3/3 |
| AccountLookupServiceImplTest | | PASS. 6/6 |
| ReviewDecisionApiIT | Testcontainers | NOT RUN (no Docker) |
| Backend format | `mvn spotless:check` (`backend/`) | PASS |

## Not run

- **Full `mvn verify`**: it ran on the branch head `221d53ca` and stopped in the unit-test phase with 1411 tests, 3 failures and 129 errors, all in `ai.client`, `identity.*`, `common.security.SecurityConfigCsrfOrderTest` and `ticket.client.JiraRestIssueSourceTest`. Their cause is the sandbox: WireMock and the Keycloak/Redis clients fail with `Unable to establish loopback connection` / `SocketException: Invalid argument: connect`. None of them is a test of this feature, and none of those files is changed by KAN-126. The CI backend job is the check for them.
- **Integration tests (Testcontainers)**: not run. Docker Desktop is not running on this machine (`Could not find a valid Docker environment`), so `failsafe` could not start PostgreSQL.
- **Coverage**: JaCoCo did not run, because `verify` stopped before the report. Traceability is checked below instead.


## Traceability

`sdd.ts trace jira-app STORY-001 STORY-008`: every criterion of the task (AC-001.2, AC-001.3, AC-008.1, AC-008.7) has a test.
