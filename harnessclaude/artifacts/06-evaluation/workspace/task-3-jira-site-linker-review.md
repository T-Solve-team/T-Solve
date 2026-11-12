---
module: workspace
feature: jira-site-linker
task: 3
verdict: pass
attempt: 2
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 3: Jira site rule and interim site linker

## Summary
Commits `default@7da71b8` (original) and `default@52e87dc` (fix). The fix closes the only attempt-1 finding. It moves
the production `RestClient.Builder` into a package-private static `InterimJiraSiteLinker.clientBuilder(...)`
(`InterimJiraSiteLinker.java:38-49`). The new test runs that real builder against a local `HttpServer`
(`InterimJiraSiteLinkerTest.java:78-123`). The server's `/_edge/tenant_info` answers 302 with a relative Location to a
second path that would return a valid `cloudId`. The test asserts `UNREADABLE` and zero hits on the target.

An interceptor rewrites only the URI. `InterceptingClientHttpRequest` then creates the delegate request from the
production `SimpleClientHttpRequestFactory`, so the `prepareConnection` override runs. If
`setInstanceFollowRedirects(false)` were deleted, `HttpURLConnection` would follow the same-protocol 302 and get a
`cloudId`, the target counter would be 1, and the test would fail. The test can now fail when the code is wrong. The
production behaviour has not changed: the constructor delegates to the same builder.

I re-checked the whole feature against the current spec. AC-007.3 and AC-007.4 did not change in the spec update of
2026-11-12. No new defect was found. Verdict: **pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `JiraSiteRule.java:15-38` implements AC-007.3: trim, `Locale.ROOT` lower-case, https, one label + `.atlassian.net`, no user info/port (an empty `:` too)/query/fragment, path empty or `/`, result `https://<host>`. `InterimJiraSiteLinker.java:56-82` re-applies the rule and GETs `<site>/_edge/tenant_info` with no credentials. Non-200 / IO / decode / blank `cloudId` → `UNREADABLE`, logged with host and status only. No redirects (`:39-45`). Timeouts come from `tsolve.tracker.jira.*`. |
| Completeness | pass | AC-007.3 and AC-007.4 are each proven by a test that can fail (Stage 1). No EXTRA behaviour. |
| Correctness | pass | Orchestrator's verify after the four fixes: `mvn -B verify` — 1594 unit + 481 IT, BUILD SUCCESS. Not re-run by the reviewer (brief: low-RAM machine). No CRITICAL finding. |
| Integration | pass | `WorkspaceServiceImpl.java:86` applies `JiraSiteRule.normalise` → `MSG61` before any outbound call. `:100-107` maps `LINKED_ELSEWHERE` → `MSG153` and anything else → `MSG61`, inside the setup transaction (AC-001.8). |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-007.3 site rule and normalised form | PASS | `JiraSiteRuleTest` (5 accepted, 30 rejected forms, including every SSRF case of design edge 2). `InterimJiraSiteLinkerTest` "AC-007.3 refuses unchecked or non-normalised input before any outbound call" makes no request. |
| 2 | AC-007.4 reads `https://<host>/_edge/tenant_info`, returns `cloudId` | PASS | "AC-007.4 returns the cloudId from public tenant information without credentials" (exact URI, GET, no `Authorization`). |
| 3 | AC-007.4 refuses when not 200 or no `cloudId` | PASS | Parameterised over 12 statuses with a log check; blank or absent `cloudId` (5 bodies); malformed body; IO failure. No body or cause is kept. |
| 4 | AC-007.4 follows no redirect | PASS (fixed) | "AC-007.4 the production client refuses a 302 and never requests its Location" (`InterimJiraSiteLinkerTest.java:78-123`) runs the real request factory. |

## Traceability
```
$ bun harnessclaude/hooks/sdd.ts trace workspace STORY-007
  ✓ AC-007.1  backend/src/test/java/vn/tsolve/workspace/jira/JiraServiceAccountCheckTest.java
  ✓ AC-007.2  backend/src/test/java/vn/tsolve/workspace/jira/JiraServiceAccountCheckTest.java
  ✓ AC-007.3  backend/src/test/java/vn/tsolve/workspace/site/InterimJiraSiteLinkerTest.java, backend/src/test/java/vn/tsolve/workspace/site/JiraSiteRuleTest.java
  ✓ AC-007.4  backend/src/test/java/vn/tsolve/workspace/site/InterimJiraSiteLinkerTest.java
  ✓ AC-007.5  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
  ✓ AC-007.6  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
  ✓ AC-007.7  backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java
trace workspace (STORY-007): 7/7 acceptance criteria have a test
```
This task owns AC-007.3 and AC-007.4. Both are now proven, not only named.

## Issues
CRITICAL: none. IMPORTANT: none.

MINOR (carried from attempt 1, do not block):
1. `backend/src/main/java/vn/tsolve/workspace/site/JiraSiteRule.java:11`: the label is not capped at 63 characters
   (RFC 1035). This is harmless for SSRF. Fix (optional): `{0,61}` in the middle group.
2. `backend/src/main/java/vn/tsolve/workspace/site/InterimJiraSiteLinker.java:64-71`: the tenant-info body is read
   without a size limit. The host is Atlassian's and the read timeout applies. No fix needed for the interim adapter.

## Attempt 1
Verdict fail (`7da71b8`).
- IMPORTANT (blocked as an AC clause without a real test): the "follow no redirect" clause of AC-007.4 was tested only
  through `MockRestServiceServer`. That mock replaced the production request factory, so the test could not fail.
  **Resolved** in `52e87dc` by the package-private `clientBuilder` and a local-`HttpServer` test of the real factory
  (see Summary).
- Informational: the 63-char label cap and the unbounded body read. **Not changed**; they are carried as MINOR 1–2.

## Assumptions made
- Tests were not re-run by the reviewer. The dispatcher's brief forbids `mvn` on this low-RAM machine. The
  orchestrator's recorded verify (session log, "Session 2026-11-13") is used. The test's ability to fail was judged by
  reading it against Spring's `InterceptingClientHttpRequest` and `HttpURLConnection` redirect behaviour. The session
  log also says it was checked by flipping the setting.
- NFR 3 (timeouts) is not an AC, so the lack of a linker timeout test is not a finding.

## Test Report
See `task-3-jira-site-linker-test-report.md`. Recorded: `mvn -B verify` BUILD SUCCESS (1594 unit + 481 IT).
Trace STORY-007: 7/7 (run by the reviewer).
