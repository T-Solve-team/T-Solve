---
module: workspace
feature: jira-site-linker
task: 3
verdict: fail
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 3: Jira site rule and interim site linker

## Summary
Commit `7da71b8` (default repo). It adds `JiraSiteRule.normalise`, the port `JiraSiteLinker`,
`JiraSiteLinkRefusedException` (`LINKED_ELSEWHERE`, `UNREADABLE`) and `InterimJiraSiteLinker`. The site rule is correct
and well tested, including every SSRF case from the design's risk review. The linker code is correct: its production
factory turns redirects off after `super.prepareConnection`. **But the "follow no redirect" clause of AC-007.4 has no
test that proves it.** Every linker test injects a `MockRestServiceServer` builder through the package-private
constructor. That replaces the production request factory and never follows redirects anyway. So the test "AC-007.4
refuses a 302 without following its Location" would still pass if `setInstanceFollowRedirects(false)` were deleted. The
clause is the SSRF control of NFR 2 and design edge case 6, and its test proves it in name only. Verdict: **fail**
(criterion clause without a real test). The fix is one test; no production code change is expected.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `JiraSiteRule.java:15-38` implements AC-007.3 exactly: trim, lower-case with `Locale.ROOT`, https, single label + `.atlassian.net`, no user info, no port (an empty `:` is rejected too), no query or fragment, path empty or `/`, result `https://<host>`. `InterimJiraSiteLinker.java:51-78` re-checks the input is already normalised, then GETs `<site>/_edge/tenant_info` with no credentials. Any non-200 status, an IO or decode failure, or a missing or blank `cloudId` → `UNREADABLE`. It logs only host and status and never keeps a body or cause. Redirects are off (`:34-41`). Timeouts come from `tsolve.tracker.jira.*` (NFR 3). |
| Completeness | **fail** | AC-007.4's "follow no redirect" has no test that can fail (Issue 1). Everything else in Stage 1 passes. |
| Correctness | pass | Orchestrator: `mvn -B verify` (with Task 4) → 1534 unit + 454 IT, 0 failures, BUILD SUCCESS. Includes JiraSiteRuleTest 35 and InterimJiraSiteLinkerTest 32. Not re-run here (brief's low-RAM limit). |
| Integration | pass | `WorkspaceServiceImpl.java:87` uses `JiraSiteRule.normalise` → `MSG61` before any outbound call. `:100-101` maps `LINKED_ELSEWHERE` → `MSG153` and the rest → `MSG61`. The port's Javadoc names platform AC-004.19..24 and marks the adapter `[Interim]`. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-007.3 site rule and normalised form | PASS | `JiraSiteRuleTest`: 5 accepted forms (case, spaces, trailing `/`, numeric and hyphenated labels) and 30 rejected ones: null, empty, blank, `http`, localhost, 169.254.169.254, `.evil.com` suffix, user info, ports 8443/443/empty, multi-label, a path, `?`/`#` (empty ones too), leading or trailing hyphen, `_`, trailing dot, `%2e`, `%2f`, `..`, backslash, `%`, `https:///`, a space, garbage. `InterimJiraSiteLinkerTest` "AC-007.3 refuses unchecked or non-normalised input before any outbound call" checks with `jira.verify()` that no request is made. |
| 2 | AC-007.4 reads `https://<host>/_edge/tenant_info`, returns `cloudId` | PASS | "AC-007.4 returns the cloudId from public tenant information without credentials" checks the exact URI, GET, and no `Authorization` header. |
| 3 | AC-007.4 refuses when not 200 or no `cloudId` | PASS | Statuses 201, 204, 301, 302, 307, 308, 400, 401, 403, 404, 429 and 500 are refused, and the log has `host` and `status=` with no body. An empty, `{}`, null, `""` or blank `cloudId` is refused. A malformed body and an IO failure are refused with no leak. |
| 4 | AC-007.4 follows no redirect | **MISSING** (named, not proven) | `InterimJiraSiteLinkerTest.java:72` (`refusesRedirect`) uses the mock builder from `:37-40`. `MockRestServiceServer.bindTo(builder)` swaps in its own request factory, so the production `SimpleClientHttpRequestFactory` override at `InterimJiraSiteLinker.java:34-41` is never run by any test. |

## Traceability
```
  ✓ AC-007.1  backend/src/test/java/vn/tsolve/workspace/jira/JiraServiceAccountCheckTest.java
  ✓ AC-007.2  backend/src/test/java/vn/tsolve/workspace/jira/JiraServiceAccountCheckTest.java
  ✓ AC-007.3  backend/src/test/java/vn/tsolve/workspace/site/InterimJiraSiteLinkerTest.java, backend/src/test/java/vn/tsolve/workspace/site/JiraSiteRuleTest.java
  ✓ AC-007.4  backend/src/test/java/vn/tsolve/workspace/site/InterimJiraSiteLinkerTest.java
  ✓ AC-007.5  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
  ✓ AC-007.6  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
  ✓ AC-007.7  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
trace workspace (STORY-007): 7/7 acceptance criteria have a test
```
This task owns AC-007.3 and AC-007.4. Trace sees a test name for AC-007.4, but one of its clauses is not proven
(Issue 1).

## Issues
1. **IMPORTANT (blocks: AC clause without a real test)** — `backend/src/test/java/vn/tsolve/workspace/site/InterimJiraSiteLinkerTest.java:72`
   (with `:37-40`) does not test the production redirect policy at
   `backend/src/main/java/vn/tsolve/workspace/site/InterimJiraSiteLinker.java:34-41`.
   Expected: deleting `connection.setInstanceFollowRedirects(false)` makes a test fail. Actual: all 32 linker tests stay
   green, because the mock factory never follows redirects.
   Fix: build the production `RestClient.Builder` in a package-private static factory, the way
   `JiraRestIssueSource.clientBuilder(...)` does. Then add an `AC-007.4 …` test against a local
   `com.sun.net.httpserver.HttpServer` on 127.0.0.1, as `JiraRestIssueSourceTest.java:307-330` already does. That
   server's `/_edge/tenant_info` answers 302 to a second path on itself that returns `{"cloudId":"x"}`. Assert that the
   linker logic refuses with `UNREADABLE` and the second path is never hit. Because `linkJiraSite` re-applies the site
   rule and would refuse `127.0.0.1`, the test must call a package-private seam that takes the target URI. Or assert
   on the `HttpURLConnection` that the factory prepares (`getInstanceFollowRedirects()` is false).

Informational (does not block):
2. `backend/src/main/java/vn/tsolve/workspace/site/JiraSiteRule.java:11` — the label is not capped at 63 characters
   (RFC 1035). This is harmless for SSRF, because any such host is still under `atlassian.net`. Fix (optional): use
   `{0,61}` in the middle group.
3. `backend/src/main/java/vn/tsolve/workspace/site/InterimJiraSiteLinker.java:60-67` — the response body is read
   without a size limit. The host is Atlassian-controlled and the read timeout applies, so the risk is low. Fix: none
   needed for the interim adapter.

## Assumptions made
- A clause of an AC whose only test cannot fail when that clause's code is removed counts as MISSING, which means
  verdict fail. The rest of AC-007.4 is proven, and the production code is believed correct. Open judgment call for
  the user: accept the code as it is and downgrade this to informational, or require the test (recommended, because
  this is the SSRF control).
- NFR 3 (timeouts) is not an AC, so the absence of a timeout test for the linker is not a finding. The values come
  from the same settings as `JiraClient`.

## Test Report
See `task-3-jira-site-linker-test-report.md`. Full suite run by the orchestrator: BUILD SUCCESS. Trace STORY-007: 7/7
by name; one clause of AC-007.4 is not proven.
