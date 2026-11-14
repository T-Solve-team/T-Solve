# Test Report: ticket-review — Task 21: review-email-link-fallback

**Date:** 2026-11-14
**Code under test:** commit `e9eec864` on `feat/KAN-162-worker-email-outbox` (local `HEAD` `2baa4282`; `git diff --stat e9eec864 2baa4282 -- backend` is empty, so no backend file differs)
**Commands (run in `backend/`):**
- Compile + unit tests: `mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test`
- Format: `mvn -q spotless:check`
- Integration tests: cannot run locally (Docker is off). Evidence: CI of PR #233, run `43602465544`
- Traceability: `bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-010`
- Coverage: the project has no coverage command, so coverage was not measured

## Results

### Local (run by the reviewer, 2026-11-14 21:00–21:01 +07)

- Total: 115 · Passed: 115 · Failed: 0 · Errors: 0 · Skipped: 0 · exit code 0
- Compile: pass (part of the test run) · Spotless: pass (exit code 0)

| Test class | Tests | Fail | Error | Skip |
|---|---|---|---|---|
| `worker.client.ReviewEmailRendererTest` | 17 | 0 | 0 | 0 |
| `worker.client.SmtpEmailSenderTest` | 12 | 0 | 0 | 0 |
| `worker.config.WorkerConfigurationTest` | 10 | 0 | 0 | 0 |
| `worker.service.impl.EmailOutboxDispatchHandlerTest` | 29 | 0 | 0 | 0 |
| `worker.service.impl.EmailOutboxRegistrationTest` | 2 | 0 | 0 | 0 |
| `worker.service.impl.EmailOutboxServiceImplTest` | 12 | 0 | 0 | 0 |
| `worker.service.impl.ReviewNotificationEnqueueBugTest` | 9 | 0 | 0 | 0 |
| `worker.service.impl.ReviewNotificationEnqueuerTest` | 10 | 0 | 0 | 0 |
| `worker.service.impl.TenantExecutionServiceImplTest` | 7 | 0 | 0 | 0 |
| `worker.service.impl.TenantJobServiceImplTest` | 5 | 0 | 0 | 0 |
| `worker.service.mock.MockWorkerTenantServiceTest` | 2 | 0 | 0 | 0 |

`ReviewNotificationEnqueuerTest` went from 9 to 10 tests (the new BUG-223 case). `ReviewNotificationEnqueueBugTest` stays at 9 (rename only). This run covers the worker package only, not the full backend suite; the evidence for the full suite is the CI below.

### CI of PR #233

Head `2baa42820e1ab8644be5bb43ff3953465da61425`. `gh pr view 233 --json headRefOid` and `gh run view 43602465544 --json headSha` both return it, and it is the local `HEAD`. `git merge-base --is-ancestor e9eec864 2baa4282` succeeds.

| Check | Result |
|---|---|
| backend-tests (unit) | pass (2m10s) |
| backend-tests (it-keycloak-a) | pass (2m44s) |
| backend-tests (it-keycloak-b) | pass (3m42s) |
| **backend-tests (it-other)** | **fail (7m16s), job `132470028372`** |
| changes, frontend, gitleaks, pr-title | pass |
| frontend-checks, frontend-tests | skipped (no frontend change) |

From the it-other job log:

```
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.445 s -- in vn.tsolve.worker.service.impl.ReviewNotificationIT
[ERROR] Tests run: 9, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 72.23 s <<< FAILURE! -- in vn.tsolve.worker.EmailOutboxIT
[ERROR] Tests run: 536, Failures: 0, Errors: 1, Skipped: 0
[ERROR] BUILD FAILURE
```

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured (no coverage tool is configured) | the spec sets none |

## Acceptance Criteria → Tests

```
  ✓ AC-010.1  ReviewNotificationEnqueuerTest, ReviewNotificationIT
  ✓ AC-010.2  ReviewNotificationEnqueuerTest, ReviewEmailRendererTest, ReviewNotificationIT
  ✓ AC-010.3  ReviewNotificationEnqueuerTest, ReviewNotificationIT
  ✓ AC-010.4  ReviewNotificationIT
  ✓ AC-010.5  ReviewNotificationEnqueuerTest, ReviewNotificationIT
  ✓ AC-010.6  ReviewNotificationEnqueuerTest, ReviewNotificationIT
  ✓ AC-010.7  ReviewEmailRendererTest, ReviewNotificationIT
  ✓ AC-010.8  ReviewNotificationEnqueuerTest, ReviewEmailRendererTest
  ✓ AC-010.9  ReviewNotificationEnqueuerTest, ReviewEmailRendererTest
trace ticket-review (STORY-010): 9/9 acceptance criteria have a test
```

(The tool printed full paths under `backend/src/test/java/vn/tsolve/worker/`; they are shortened here.)

## Failures

Local: none.

### `backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java` — `recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` (CI only)
- Error: `ConditionTimeoutException` at `EmailOutboxIT.java:253 -> :269`. The row was expected to have `status=SENT`, but stayed `status=PENDING`, `attempt_count=0`, `claimed_at=null` for 1 minute.
- Cause: the recurring `worker-email-outbox` job never claimed the row in the `ApplicationContextRunner` context. This is the test's first CI run (BUG-221: "written and compiled only"). It is in the worker module (commit `c5bfbaee`), outside the `e9eec864` diff.
- Suggested fix (worker owner): diagnose the recurring registration or the test context so the job runs, then re-run PR #233.

## Notes

- **Regression test is real (BUG-223), checked by reading the code, not by running it:** before `e9eec864`, `render` called `sites.siteHost` once per decision and the per-decision `catch (RuntimeException)` swallowed the exception, so no email was queued and `siteHost` ran 3 times. Both `verify(outbox, times(3)).queue(...)` and `verify(sites).siteHost(workspace)` (which means exactly once) would fail against the old code. The test was not run against the reverted code, because that would mean changing the working tree.
- Flaky: none seen locally (one run). The CI failure has been seen once; whether it is flaky or always fails is unknown. Skipped: none. Slow: no unit class above 5 s.
- The local run printed the expected WARN lines from the failure-path tests; they contain only counts and exception class names.
- No dependency audit was run; `pom.xml` is unchanged by `e9eec864`.
