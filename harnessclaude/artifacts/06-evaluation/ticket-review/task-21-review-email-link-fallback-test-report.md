# Test Report: ticket-review — Task 21: review-email-link-fallback (attempt 2)

**Date:** 2026-11-14
**Code under test:** commit `e9eec864` on `feat/KAN-162-worker-email-outbox`, local `HEAD` `cd8186b4`. `git diff e9eec864..HEAD --stat` on `ReviewNotificationEnqueuer.java` and `ReviewNotification*` tests is empty. Backend changes since `e9eec864` are test-only: `EmailOutboxIT.java` (+2), `WorkerMailpit.java` (+4), from `e6570be1` (BUG-225).
**Commands (run in `backend/`):**
- Compile + unit tests: `mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test`
- Format: `mvn -q spotless:check`
- Integration tests: cannot run locally (Docker is off). Evidence: CI of PR #233, run `43603714210`
- Traceability: `bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-010`
- Coverage: no coverage command in the project; not measured

## Results

### Local (run by the reviewer, 2026-11-14)

- Total: 115 · Passed: 115 · Failed: 0 · Errors: 0 · Skipped: 0 · exit code 0
- Compile: pass · Spotless: pass (exit code 0)

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

Identical counts to attempt 1.

### CI of PR #233

Head `cd8186b484fee04ecaf6778e988d2793a471b798` (`gh pr view 233 --json headRefOid` and `gh run view 43603714210 --json headSha` both return it; run conclusion `success`).

| Check | Result |
|---|---|
| backend-tests (unit) | pass (2m23s) |
| backend-tests (it-keycloak-a) | pass (2m49s) |
| backend-tests (it-keycloak-b) | pass (2m47s) |
| backend-tests (it-other) | pass (6m40s), job `132473741110` |
| backend | pass |
| changes, frontend, gitleaks, pr-title | pass |
| frontend-checks, frontend-tests | skipped (no frontend change) |

From the it-other job log (grepped):

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 29.70 s -- in vn.tsolve.worker.EmailOutboxIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.608 s -- in vn.tsolve.worker.service.impl.ReviewNotificationIT
[INFO] Tests run: 536, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured (no coverage tool configured) | the spec sets none |

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

(Paths shortened; the tool prints them under `backend/src/test/java/vn/tsolve/worker/`.)

## Failures

None, locally or in CI. Attempt 1's CI failure (`EmailOutboxIT.recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221`) now passes on `cd8186b4` (fix `e6570be1`, BUG-225).

## Notes

- BUG-223 regression evidence remains by reading the code (see attempt 1); not re-run against reverted code.
- Flaky: none seen. Slow: no unit class above 5 s.
- No dependency audit; `pom.xml` unchanged since `e9eec864`.
