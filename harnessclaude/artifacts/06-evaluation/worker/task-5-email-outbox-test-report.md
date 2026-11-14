# Test Report: worker — Task 5: email-outbox

**Date:** 2026-11-14
**Commit:** `29405ede5570b51ae21b0b4365a31f15859938d6` (local `git rev-parse HEAD` = head of PR #233 = commit of CI run 43600347361)
**Commands (run from `backend/`, Maven 3.9.14 from the wrapper distribution):**
`mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` (compiles main and test code) · `mvn -q spotless:check` · coverage: none (the backend has no coverage plugin)

## What was run by whom

| Scope | Run by the reviewer | Evidence |
|---|---|---|
| Compile + worker unit tests (`vn.tsolve.worker.**.*Test`) | Yes, locally | exit 0; 11 classes, 106 tests, 0 failures, 0 errors, 0 skipped (surefire reports written 20:35) |
| `spotless:check` | Yes, locally | exit 0 |
| `EmailOutboxIT`, `SmtpEmailSenderIT`, `JobRunrTenantIT` | **No** — Docker is off on this machine | CI only: PR #233, run 43600347361, job `backend-tests (it-other)`, on the commit above |
| Full backend suite (`mvn verify`) | **No** | CI only: jobs `backend-tests (unit)`, `(it-keycloak-a)`, `(it-keycloak-b)`, `(it-other)` all pass on the commit above |

## Results

### Local run (worker unit tests)

- Total: 106 · Passed: 106 · Failed: 0 · Skipped: 0
- Compile: pass · Spotless: pass

| Class | Tests | Belongs to this feature |
|---|---|---|
| `client.SmtpEmailSenderTest` | 12 | yes |
| `service.impl.EmailOutboxDispatchHandlerTest` | 21 | yes |
| `service.impl.EmailOutboxServiceImplTest` | 12 | yes |
| `service.impl.EmailOutboxRegistrationTest` | 2 | yes |
| `config.WorkerConfigurationTest` | 10 | yes (outbox wiring, BUG-217) |
| `client.ReviewEmailRendererTest` | 17 | no (ticket-review) |
| `service.impl.ReviewNotificationEnqueueBugTest` | 9 | no (ticket-review) |
| `service.impl.ReviewNotificationEnqueuerTest` | 9 | no (ticket-review) |
| `service.impl.TenantExecutionServiceImplTest` | 7 | no (STORY-002) |
| `service.impl.TenantJobServiceImplTest` | 5 | no (STORY-002) |
| `service.mock.MockWorkerTenantServiceTest` | 2 | no (STORY-002/003) |

### CI (PR #233, run 43600347361, head `29405ede`, conclusion success)

Lines taken from the job logs with `gh run view --job <id> --log`, filtered by class name:

- `backend-tests (it-other)`: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.worker.EmailOutboxIT`
- `backend-tests (it-other)`: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.worker.client.SmtpEmailSenderIT`
- `backend-tests (it-other)`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.worker.JobRunrTenantIT`
- `backend-tests (it-other)` total: `Tests run: 533, Failures: 0, Errors: 0, Skipped: 0` · BUILD SUCCESS
- `backend-tests (unit)` total: `Tests run: 1737, Failures: 0, Errors: 0, Skipped: 0` · `spotless:check` ran · BUILD SUCCESS
- `backend-tests (it-keycloak-a)`, `(it-keycloak-b)`, `backend`, `gitleaks`: pass

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured (no coverage command in `backend/pom.xml`) | none stated in the spec |

## Acceptance Criteria → Tests

```
  ✓ AC-001.1  backend/src/test/java/vn/tsolve/worker/client/SmtpEmailSenderTest.java, backend/src/test/java/vn/tsolve/worker/client/SmtpEmailSenderIT.java
  ✓ AC-001.2  backend/src/test/java/vn/tsolve/worker/client/SmtpEmailSenderTest.java, backend/src/test/java/vn/tsolve/worker/client/SmtpEmailSenderIT.java
  ✓ AC-001.3  backend/src/test/java/vn/tsolve/worker/client/SmtpEmailSenderTest.java
  ✓ AC-001.4  backend/src/test/java/vn/tsolve/worker/client/SmtpEmailSenderTest.java
  ✓ AC-001.5  backend/src/test/java/vn/tsolve/worker/client/SmtpEmailSenderTest.java
  ✓ AC-001.6  backend/src/test/java/vn/tsolve/worker/client/SmtpEmailSenderTest.java
  ✓ AC-001.7  backend/src/test/java/vn/tsolve/worker/client/SmtpEmailSenderTest.java
trace worker (STORY-001): 7/7 acceptance criteria have a test
  ✓ AC-004.1  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxServiceImplTest.java, backend/src/test/java/vn/tsolve/worker/config/WorkerConfigurationTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.2  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxServiceImplTest.java
  ✓ AC-004.3  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxServiceImplTest.java, backend/src/test/java/vn/tsolve/worker/config/WorkerConfigurationTest.java
  ✓ AC-004.4  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxRegistrationTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.5  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.6  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.7  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.8  backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.9  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java
  ✓ AC-004.10  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.11  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxServiceImplTest.java
  ✓ AC-004.12  backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
trace worker (STORY-004): 12/12 acceptance criteria have a test
```

AC-004.8 and AC-004.12 are covered only by `EmailOutboxIT`, so their evidence is CI only.

## Failures

None.

## Notes

- Skipped tests: none locally, none in the two CI backend totals read.
- Flaky tests: none seen (one local run, one CI run).
- Slow tests: none over 5 s locally; in CI `JobRunrTenantIT` took 31.5 s (not part of this feature's change beyond the shared container).
- Not run by the reviewer: every `*IT` class, `mvn verify` as a whole, any coverage tool, any dependency audit tool.
- Gaps in what the tests exercise (details in the review): no test runs the recurring job `worker-email-outbox` through real JobRunr; AC-004.9 "held, attempt count unchanged" is checked only against a mocked repository; the "Jobs database not configured" half of AC-004.3 is asserted in a test whose name cites AC-002.12/AC-002.14.
