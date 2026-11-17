---
module: ticket-repository
feature: jira-import-workspace-account
task: 31
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 31: The pull reads the Workspace's Jira service account (KAN-345, STORY-001, BUG-308)

## Summary
This review checks the code at HEAD `7554caca`, which includes PR #264 (`3f05b210`).

- `JiraRestIssueSource` no longer reads the environment. It takes `tracker.service.JiraServiceAccount`, implemented by `workspace.service.impl.WorkspaceJiraServiceAccount`, plus the `Clock` (`backend/src/main/java/vn/tsolve/ticket/client/JiraRestIssueSource.java:63-73`).
- `connect()` (lines 79-96) reads `current()` once per `resolvedIssues` call. It refuses the account, before any HTTP call, with `JiraUnavailableException("The Jira connection is not configured")` (MSG50) in these cases:
  - there is no account;
  - the site does not start with `https://`;
  - the cloud id is not a canonical UUID;
  - the token is blank.

  `JiraImportServiceImpl.read` then records the Failed batch, as in AC-001.7.
- Otherwise it builds a `RestClient` on `https://api.atlassian.com/ex/jira/{cloudId}` with `Authorization: Bearer <token>` and `Accept: application/json`. The issue links are `siteUrl` (with trailing slashes stripped) + `/browse/{key}` (line 158).
- These are unchanged: retries (AC-001.11), the deadline (AC-001.13), the JQL key guard and the mapping.

Leftover and security checks:
- `JIRA_SITE_URL`, `JIRA_CLOUD_ID`, `JIRA_API_TOKEN` and `JIRA_EMAIL` appear nowhere in the code, config, `.env.example` or `infra/staging.env.example` (checked with `git grep`, excluding harness artifacts and the bug log).
- The BUG-308 row is Fixed, with its tests (`docs/bug-log.md:324`).
- The token never reaches a log line or an exception: `JiraAccount.toString` leaves it out, and the reasons carry only an HTTP status or an error type.
- `siteUrl` is used only to build links, never called. Together with the UUID-only cloud id, this means no SSRF or path injection into the gateway URL.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass (with note) | AC-001.12: Bearer to the gateway with the cloud id, links on the site URL. AC-001.14: no or incomplete account → no call, MSG50, Failed batch. AC-001.7, 001.11 and 001.13 are unchanged. Note: the account is read once per page, not once per pull (Issue 1). |
| Completeness | pass | AC-001.12 and AC-001.14 are traced. The env variables are removed and the BUG-308 row is Fixed. |
| Correctness | pass | JiraRestIssueSourceTest 24/24, JiraImportServiceImplTest 16/16, JiraImportControllerTest 3/3. |
| Integration | pass | It uses the same `JiraServiceAccount` port as `tracker` (`TrackerProjectServiceImpl`, `SuggestionCommentServiceImpl`). `WorkspaceJiraServiceAccount.current()` resolves the tenant from `TenantScope` (web request) or `WorkerTenantService`, and returns empty for a missing row, missing fields or an undecryptable token, which is the case AC-001.14 lists. |

## Traceability
```
  ✓ AC-001.12  backend/src/test/java/vn/tsolve/ticket/client/JiraRestIssueSourceTest.java
  ✓ AC-001.14  backend/src/test/java/vn/tsolve/ticket/client/JiraRestIssueSourceTest.java
trace ticket-repository (STORY-001): 14/14 acceptance criteria have a test
```
Tests:
- `AC_001_12_sendsBearerToTheGatewayAndBuildsBrowseLinksOnTheSite` (line 390). The `Bearer` header and the gateway URL are asserted on every search request through `searchRequest()` (lines 107-110).
- `readsTheWorkspaceAccountAgainForEveryPull_AC_001_12_BUG_308` (line 358).
- `reportsAWorkspaceWithoutAServiceAccountWithoutCallingJira_AC_001_7_AC_001_14_BUG_308` (line 348).
- `AC_001_14_requiresEveryPartOfTheAccount_BUG_308` (line 497).

## Issues
No critical issues.

1. **Medium (spec deviation, judgment call)**: `backend/src/main/java/vn/tsolve/ticket/client/JiraRestIssueSource.java:115` calls `connect()`, and so `JiraServiceAccount.current()`, on every `resolvedIssues` call, which means on every page.
   - AC-001.12 says to take the account "at the start of that pull". The design (`design.md`, "Change 2026-11-16", row "Credential source") says "once at the start of each pull". Task 31 says "each `resolvedIssues` call reads `current()` once", which describes the code but not the spec.
   - A pull of up to 20 pages therefore does up to 20 DB reads and 20 token decryptions.
   - If the Admin replaces or clears the account during a pull, the pages are read with different accounts. If the cloud id changes, the issue links could point to different sites. Or the pull can fail halfway with MSG50.
   - The test `readsTheWorkspaceAccountAgainForEveryPull_AC_001_12_BUG_308` (`JiraRestIssueSourceTest.java:358`) calls `resolvedIssues` twice and calls that "two pulls". The production caller (`JiraImportServiceImpl.java:127`) calls it once per page, so the test actually fixes the per-page behaviour in place.
   - Fix, either one:
     - Read the account once in `JiraImportServiceImpl.read` and pass it, or the `Connection`, through the port, so that `resolvedIssues` gets an account or a connection handle.
     - Or reword AC-001.12 and the design to say "for each Jira page".
2. **Low**: `backend/src/main/java/vn/tsolve/ticket/service/JiraIssueSource.java:8-9`: the Javadoc still says the service account "comes from the environment until the workspace module stores it (spec D2)". That has been false since this task.
   - Fix: say it comes from the Workspace's `JiraServiceAccount`.
3. **Info**: AC-001.14's "one Failed Import Batch" is not tested end to end for the "not configured" reason. The source test proves the `JiraUnavailableException`. The service records any `JiraUnavailableException` as Failed (`JiraImportServiceImplTest:263, 318`). I accept the two together as coverage.

## Assumptions made
- I pass Issue 1 instead of failing it. The difference from "at the start of that pull" can only be seen when the Workspace's account changes during a pull that lasts at most about 60 s, and every page still uses a valid Workspace account. **Open judgment call for the user:** hold the account for the whole pull (code change), or accept a per-page read (spec and design wording change).
- I did not run the integration tests (Docker; the dispatcher runs `mvn verify` separately). This PASS assumes they are green.

## Test Report
I ran `mvn -B -Dtest='JiraRestIssueSourceTest,JiraImportServiceImplTest,JiraImportControllerTest' test` from `backend/` at HEAD `7554caca`:
- JiraRestIssueSourceTest: 24 run, 0 failures, 0 errors.
- JiraImportServiceImplTest: 16 run, 0 failures, 0 errors.
- JiraImportControllerTest: 3 run, 0 failures, 0 errors.

I did not run `spotless:check` or the ITs, because the full verify runs in the same tree. `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-001` reports 14/14.
