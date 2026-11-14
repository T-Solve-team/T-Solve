# Test Report: ticket-review — Task 20: review-email-outbox

**Date:** 2026-11-14
**Code under test:** commit `5cf5c10b` on `feat/KAN-162-worker-email-outbox` (branch head `29405ede`; no backend file differs between the two)
**Commands (run in `backend/`):**
- Compile + unit tests: `mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test`
- Format: `mvn -q spotless:check`
- Integration tests: not runnable locally (Docker is off). Evidence: CI of PR #233, run `43600347361`
- Traceability: `bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-010`
- Coverage: the project has no coverage command; not measured

## Results

### Local (run by the reviewer, 2026-11-14 20:35 +07)

- Total: 106 · Passed: 106 · Failed: 0 · Errors: 0 · Skipped: 0 · exit code 0
- Compile: pass (part of the test run) · Spotless: pass (exit code 0)

| Test class | Tests | Fail | Error | Skip |
|---|---|---|---|---|
| `worker.client.ReviewEmailRendererTest` | 17 | 0 | 0 | 0 |
| `worker.client.SmtpEmailSenderTest` | 12 | 0 | 0 | 0 |
| `worker.config.WorkerConfigurationTest` | 10 | 0 | 0 | 0 |
| `worker.service.impl.EmailOutboxDispatchHandlerTest` | 21 | 0 | 0 | 0 |
| `worker.service.impl.EmailOutboxRegistrationTest` | 2 | 0 | 0 | 0 |
| `worker.service.impl.EmailOutboxServiceImplTest` | 12 | 0 | 0 | 0 |
| `worker.service.impl.ReviewNotificationEnqueueBugTest` | 9 | 0 | 0 | 0 |
| `worker.service.impl.ReviewNotificationEnqueuerTest` | 9 | 0 | 0 | 0 |
| `worker.service.impl.TenantExecutionServiceImplTest` | 7 | 0 | 0 | 0 |
| `worker.service.impl.TenantJobServiceImplTest` | 5 | 0 | 0 | 0 |
| `worker.service.mock.MockWorkerTenantServiceTest` | 2 | 0 | 0 | 0 |

This is the worker package only, not the full backend suite. The full suite was not run locally; its evidence is the CI below.

### CI of PR #233 (head `29405ede5570b51ae21b0b4365a31f15859938d6`, which contains `5cf5c10b`)

`gh pr view 233 --json headRefOid` and `gh run view 43600347361 --json headSha` both return `29405ede…`, the local `HEAD`.

| Check | Result |
|---|---|
| backend-tests (unit) | pass (2m34s) |
| backend-tests (it-keycloak-a) | pass (2m50s) |
| backend-tests (it-keycloak-b) | pass (3m23s) |
| backend-tests (it-other) | pass (6m9s) |
| backend, changes, frontend, gitleaks, pr-title | pass |
| frontend-checks, frontend-tests | skipped (no frontend change) |

`ReviewNotificationIT` ran in `backend-tests (it-other)`, job `132463579534`:

```
[INFO] Running vn.tsolve.worker.service.impl.ReviewNotificationIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.446 s -- in vn.tsolve.worker.service.impl.ReviewNotificationIT
[INFO] Tests run: 533, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

16 matches the class: 14 test methods, two of them parameterized over Reject and Request Changes.

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured (no coverage tool configured) | none stated in the spec |

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

(All paths are under `backend/src/test/java/vn/tsolve/worker/`.) Each named test was read and does check its criterion. AC-010.4 is evidenced only by the IT, so only by CI.

## Failures

None.

## Notes

- Flaky: none seen (one local run, one CI run). Skipped: none. Slow: none above 5 s per class.
- `ReviewNotificationEnqueueBugTest.stopsAtTheFirstFailedEnqueue_BUG_164` passes but its name says the opposite of what it asserts (three queue calls). See the review, finding 1.
- The local run printed three expected WARN lines from `ReviewNotificationEnqueuer`; each holds only counts and an exception class name.
- No dependency audit tool was run; commit `5cf5c10b` does not touch `pom.xml`.
- Unresolved: no coverage number exists and the spec gives no threshold (listed in the review under "Assumptions made").
