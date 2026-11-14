---
module: workspace
feature: tracker-account-check
task: 2
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 2: Service account check in the tracker client

## Summary
Commits `787c7db` and the fix-up `e60a44e` (default repo). They add `TicketTrackerClient.checkServiceAccount(JiraAccount)`
and implement it in `JiraClient`. The check calls `GET /rest/api/3/myself`, then
`GET /rest/api/3/mypermissions?permissions=BROWSE_PROJECTS,CREATE_ISSUES,MODIFY_REPORTER` with Basic auth. It passes only
on 200 + 200 with all three `havePermission` true (JSON boolean). Any other result throws `JiraRejectedException` or
`JiraUnavailableException`, which carry only the status. The fix-up moves the tests unchanged to
`vn.tsolve.workspace.jira.JiraServiceAccountCheckTest` and makes the builder constructor public. The tracker
`JiraClientTest` net diff is formatting only. AC-009.1 and AC-009.2 are implemented and tested. Verdict: **pass**, with
informational findings.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `JiraClient.java:48-74`. Requests run in order; the permissions call is made only after `myself` returns 200. A non-200 2xx/3xx → `JiraRejectedException(status)`. A missing, false, null, `"true"` or `1` permission, or an empty body → `JiraRejectedException(403)`. A 4xx → rejected; 429/5xx → unavailable; IO or timeout → unavailable(0), all through the existing `call()`. |
| Completeness | pass | Stage 1 below: no MISSING, nothing EXTRA. The only implementer of `TicketTrackerClient` is `JiraClient`. |
| Correctness | pass | Orchestrator: `mvn -B verify` on 787c7db → 1409 unit + 439 IT, BUILD SUCCESS. After e60a44e: `spotless:check` + JiraClientTest 9 / JiraServiceAccountCheckTest 26 passed. The Task 4/5 full verifies (1534 unit + 454 IT, BUILD SUCCESS on the later tree) include the moved class. Not re-run here (brief's low-RAM limit). |
| Integration | pass | `WorkspaceServiceImpl.java:94,161` catches both `JiraRejectedException` and `JiraUnavailableException` → `MSG156`, as AC-003.7/AC-005.5 need. The timeouts come from the existing constructor: `tsolve.tracker.jira.connect-timeout: 5s` and `read-timeout: 10s` (`application.yml:89-90`), meeting NFR 3. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-009.1 `myself` then `mypermissions` with Basic auth; pass only on 200 + 200 and all three `havePermission` true | PASS | `JiraServiceAccountCheckTest`. "AC-009.1 checks identity then all three permissions with Basic auth" checks the order, the exact URIs, GET and the `Authorization: Basic` header. Separate tests reject each permission that is false or missing, a `havePermission` that is not boolean true (`{}`, null, `"true"`, `1`), an absent body or `permissions`, and 204/302 from either endpoint. |
| 2 | AC-009.2 401/403/other status/timeout/unreachable → fail; no Jira body or credential in response or log | PASS | Tests cover 401 and 403 from both endpoints (rejected, message `Jira rejected the call: <status>`, no cause), 500 from both (unavailable), and `SocketTimeoutException` from both (unavailable(0)). Each asserts captured output contains no body, email, token or Basic header. "Unreachable" takes the same `RestClientException` branch as the timeout. Mapping to `MSG156` is the caller's job (Task 4/5). |

## Traceability
```
  ✓ AC-009.1  backend/src/test/java/vn/tsolve/workspace/jira/JiraServiceAccountCheckTest.java
  ✓ AC-009.2  backend/src/test/java/vn/tsolve/workspace/jira/JiraServiceAccountCheckTest.java
  ✓ AC-009.3  backend/src/test/java/vn/tsolve/workspace/site/InterimJiraSiteLinkerTest.java, backend/src/test/java/vn/tsolve/workspace/site/JiraSiteRuleTest.java
  ✓ AC-009.4  backend/src/test/java/vn/tsolve/workspace/site/InterimJiraSiteLinkerTest.java
  ✓ AC-009.5  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
  ✓ AC-009.6  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
  ✓ AC-009.7  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
trace workspace (STORY-009): 7/7 acceptance criteria have a test
```
This task owns AC-009.1 and AC-009.2.

## Issues
No CRITICAL or IMPORTANT findings.

Informational (does not block):
1. `backend/src/main/java/vn/tsolve/tracker/client/JiraClient.java:36-42` — `SimpleClientHttpRequestFactory` sets
   `instanceFollowRedirects(true)` for GET. A 3xx from `myself`/`mypermissions` is therefore followed in production,
   with the Basic header, to whatever https host `Location` names. The test "AC-009.1 requires HTTP 200…" (302 case)
   runs on `MockRestServiceServer`, which never follows redirects, so it does not prove the production path. The risk
   is low: the host has already passed the `*.atlassian.net` rule, and Atlassian controls the response. Still, it
   weakens NFR 2 ("outbound calls only to a Jira site address that passed the site rule") and AC-009.1's "both answer
   200". The behaviour predates this task and is shared by every tracker call. Fix (optional): disable redirects in
   the tracker request factory, as `InterimJiraSiteLinker` does, or raise a tracker change request.
2. `backend/src/main/java/vn/tsolve/tracker/client/JiraClient.java:44-47` — the test-only constructor
   `JiraClient(RestClient.Builder)` became `public` (e60a44e) so a test in another package can use it. It is now part
   of production API, and a moved test no longer sits in the package of the class it tests (`backend/AGENTS.md`
   "mirrors main"). The session log gives the reason: `sdd.ts trace` saw AC-009.1/2 ambiguous between `tracker` and
   `workspace`. Fix (optional): keep it, or move the test back and rename the tracker spec's colliding ids.

## Assumptions made
- "Cannot be reached" in AC-009.2 is taken as proven by the timeout test: both go through the same
  `catch (RestClientException)` branch in `JiraClient.call()`, so no separate connection-refused test is required.
- Redirect-following in the tracker client (finding 1) is treated as informational, because AC-009.1/2 do not name
  redirects and the target host is Atlassian-controlled. Open judgment call for the user: accept it, or require
  `setInstanceFollowRedirects(false)` in `JiraClient` under NFR 2.

## Test Report
See `task-2-tracker-account-check-test-report.md`. Full suite run by the orchestrator: BUILD SUCCESS. Trace STORY-009:
7/7.
