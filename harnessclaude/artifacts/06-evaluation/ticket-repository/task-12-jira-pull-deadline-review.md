---
module: ticket-repository
feature: jira-pull-deadline
task: 12
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 12: Pull deadline, 60 s for every Jira call of one pull (STORY-001, AC-001.13, BUG-189)

## Summary
This review checks the code at HEAD `7554caca`. The feature was merged in PR #166 (`a28db06d`). Since then:
- #264 (`3f05b210`) changed the `JiraRestIssueSource` constructor and connection.
- `JiraImportServiceImpl` has a 3-line diff since `a28db06d`.

Neither change touched the deadline logic.

- `JiraImportServiceImpl.read` (`backend/src/main/java/vn/tsolve/ticket/service/impl/JiraImportServiceImpl.java:122`) sets one deadline, `Instant.now(clock) + PULL_BUDGET` (60 s, line 46). It passes the same `Instant` with every page (line 127).
- `JiraRestIssueSource.call` (`backend/src/main/java/vn/tsolve/ticket/client/JiraRestIssueSource.java:234-267`) checks the deadline in two places:
  - `requireBefore(deadline, now)` before every request: the search pages and every comment page, since both go through `call`.
  - `requireBefore(deadline, now + Retry-After)` before every retry wait.

  When it is past the deadline, it throws `JiraUnavailableException("The pull took more than 60 seconds")`.
- `read` catches that exception (line 144). It records a Failed Jira Import Batch through `recorder.recordJiraFailed` and rethrows it, so the answer is MSG50 / 502 with no preview. This is the same path as AC-001.7. The reason text has no credential.
- A request that is already running at the deadline still ends at its 30 s read timeout. This is the accepted ceiling in the design ("Pull deadline" row) and in the `ponytail:` comment at lines 232-233.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.13: one deadline per pull covers every page, every comment call and every retry wait. Past it, the pull fails with MSG50, a Failed batch and no preview (AC-001.7 path). |
| Completeness | pass | AC-001.13 is traced in both the source test and the service test. BUG-189 row is Fixed in `docs/bug-log.md:206`. |
| Correctness | pass | JiraRestIssueSourceTest 24/24, JiraImportServiceImplTest 16/16, JiraImportControllerTest 3/3. |
| Integration | pass | The `JiraIssueSource.resolvedIssues(..., Instant deadline)` port has one implementation and one caller. `Clock` is injected, so tests are deterministic. |

## Traceability
```
  ✓ AC-001.13  backend/src/test/java/vn/tsolve/ticket/service/impl/JiraImportServiceImplTest.java, backend/src/test/java/vn/tsolve/ticket/client/JiraRestIssueSourceTest.java
trace ticket-repository (STORY-001): 14/14 acceptance criteria have a test
```
Tests that name AC-001.13:
- `JiraRestIssueSourceTest.stopsWithMsg50WithoutCallingJiraOnceThePullTookMoreThan60Seconds_AC_001_13_BUG_189` (line 163). The `@AfterEach` `jira.verify()` proves that no request was sent.
- `JiraRestIssueSourceTest.doesNotWaitForARetryThatWouldEndAfterTheDeadline_AC_001_13_BUG_189` (line 170). It asserts that no sleep happens.
- `JiraImportServiceImplTest.givesEveryJiraPageOfOnePullTheSame60SecondDeadline_AC_001_13_BUG_189` (line 269). Both pages get the same `CLOCK + 60 s`.

## Issues
No critical or blocking issues. All of the following are informational:

1. `backend/src/main/java/vn/tsolve/ticket/client/JiraRestIssueSource.java:238-242`: the deadline is checked only before a request starts, never after a response arrives.
   - If the last request starts at 59 s and answers at 85 s, the pull succeeds after 85 s. AC-001.13 literally asks for MSG50 in that case.
   - This is the documented ceiling (design "Pull deadline" row, the comment at lines 232-233, the BUG-189 note), and the user accepted it on 2026-11-08.
   - Fix if wanted: give each request a read timeout of `min(30 s, time left)`, as the ponytail comment proposes.
2. `JiraRestIssueSource.java:115`: since #264, `connect()` (one DB read plus decrypting the token) runs before the first deadline check at line 236. An expired deadline therefore still costs one `JiraServiceAccount.current()` call. The cost is negligible.
   - Fix (optional): check the deadline at the top of `resolvedIssues`.
3. No service-level test feeds the "took more than 60 seconds" reason through `pull` to assert MSG50 plus `recordJiraFailed`. The path is the same `catch (JiraUnavailableException)` that the AC-001.7 service tests cover (`JiraImportServiceImplTest:263, 318`). I accept that as coverage.

## Assumptions made
- Finding 1 (a request already running at the deadline can let the pull end successfully after 60 s) does not fail AC-001.13. I rely on the design's recorded user decision. The spec text alone ("SHALL stop reading") is stricter. Judgment call for the user: keep the ceiling, or tighten it per request.
- I did not run the integration tests (Docker; the dispatcher runs `mvn verify` separately). This PASS assumes they are green.

## Test Report
I ran `mvn -B -Dtest='JiraRestIssueSourceTest,JiraImportServiceImplTest,JiraImportControllerTest' test` from `backend/` at HEAD `7554caca`:
- JiraRestIssueSourceTest: 24 run, 0 failures, 0 errors.
- JiraImportServiceImplTest: 16 run, 0 failures, 0 errors.
- JiraImportControllerTest: 3 run, 0 failures, 0 errors.

I did not run `spotless:check` or the ITs, because the full verify runs in the same tree.
