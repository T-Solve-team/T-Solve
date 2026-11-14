# Test Report: worker — Task 6: email-outbox-review-fixes

**Date:** 2026-11-14
**Commit under test:** local HEAD `2baa42820e1ab8644be5bb43ff3953465da61425` (contains `c5bfbaee`; the later commits `e9eec864`, `2baa4282` touch no worker outbox file). PR #233 head = the same commit.
**Commands (from `backend/`):**
- `mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test`
- `mvn -q spotless:check`
- CI (PR #233, run 43602465544): `backend-tests (unit)`, `(it-other)`, `(it-keycloak-a)`, `(it-keycloak-b)`
- `bun harnessclaude/hooks/sdd.ts trace worker STORY-004`

## Results

### Run by the reviewer (local, no Docker)

- Worker unit tests: exit 0 — 11 classes, 115 tests, 0 failures, 0 errors, 0 skipped (task 5: 106; +9 from the new BUG-218/219/220 cases, counting parameterized runs).
  - `EmailOutboxDispatchHandlerTest` 29 tests, 0 failures — includes `sendsPastHeldRowsAndCachesEligibility_AC_004_4_BUG_218` (2 runs), `failedRowsDoNotConsumeSendBatch_AC_004_4_BUG_218`, `capsReadsAtTenAndRefreshesEligibilityNextRun_AC_004_4_BUG_218`, `declaresZeroJobRunrRetries_AC_004_4_BUG_219`, `continuesAfterOutcomeWriteFailure_AC_004_6_AC_004_7_BUG_220` (3 runs).
- `spotless:check`: exit 0.
- Typecheck: compilation of main and test sources succeeded (part of the test run). Lint: none beyond Spotless for the backend.

### CI only (PR #233, run 43602465544, head 2baa4282)

| Job | Result |
|-----|--------|
| backend-tests (unit) | pass (2m10s) |
| backend-tests (it-keycloak-a) | pass |
| backend-tests (it-keycloak-b) | pass |
| **backend-tests (it-other)** | **FAIL** (7m16s) — 536 tests, 0 failures, **1 error** |
| backend (aggregate) | fail |
| gitleaks, pr-title, changes, frontend | pass |

`it-other` class lines (from the job log):
- `EmailOutboxIT` — Tests run: 9, Failures: 0, **Errors: 1** (6 earlier + 3 new). By elimination the new `sendsNewerActiveRowPast25HeldRows_AC_004_4_BUG_218` and `holdsSuspendedWithoutAttemptThenFailsPastHour_AC_004_9_BUG_222` passed; they have no own line in the log.
- `JobRunrTenantIT` 1/0, `SmtpEmailSenderIT` 3/0, `ReviewNotificationIT` 16/0.

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured — the backend has no coverage command | none stated |

## Acceptance Criteria → Tests

```
  ✓ AC-004.1  EmailOutboxServiceImplTest, WorkerConfigurationTest, EmailOutboxIT
  ✓ AC-004.2  EmailOutboxServiceImplTest
  ✓ AC-004.3  EmailOutboxServiceImplTest, WorkerConfigurationTest
  ✓ AC-004.4  EmailOutboxDispatchHandlerTest, EmailOutboxRegistrationTest, EmailOutboxIT
  ✓ AC-004.5  EmailOutboxDispatchHandlerTest, EmailOutboxIT
  ✓ AC-004.6  EmailOutboxDispatchHandlerTest, EmailOutboxIT
  ✓ AC-004.7  EmailOutboxDispatchHandlerTest, EmailOutboxIT
  ✓ AC-004.8  EmailOutboxIT
  ✓ AC-004.9  EmailOutboxDispatchHandlerTest, EmailOutboxIT
  ✓ AC-004.10  EmailOutboxDispatchHandlerTest, EmailOutboxIT
  ✓ AC-004.11  EmailOutboxDispatchHandlerTest, EmailOutboxServiceImplTest
  ✓ AC-004.12  EmailOutboxIT
trace worker (STORY-004): 12/12 acceptance criteria have a test
```

## Failures

### `backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java` — `recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` (CI)

- Error: `ConditionTimeoutException` after 60 s at `EmailOutboxIT.java:253` / lambda at `:269`: the row stayed `status=PENDING, attempt_count=0` (expected `SENT`, 1).
- Log: the recurring job was registered and triggered by real JobRunr through the CGLIB proxy, and every run threw `java.lang.IllegalStateException: Email sender unavailable` at `EmailOutboxDispatchHandler.java:55` — `senders.getIfAvailable()` returned null.
- Cause (reviewer's analysis): the test registers the sender with `.withBean(SmtpEmailSender.class, mailpit::sender)` (`EmailOutboxIT.java:240`). `registerBean` evaluates the class's conditions, and `SmtpEmailSender` is `@Profile("worker") @ConditionalOnProperty(prefix = "spring.mail", name = "host")`. No `spring.mail.host` is set (neither in the test's property values nor in `application.yml`), so the bean is skipped and the handler sees no sender.
- Suggested fix: add `spring.mail.host` / `spring.mail.port` (Mailpit) and `TSOLVE_SMTP_FROM` to the test's property values, or provide the sender bean without evaluating the class's conditions (for example a `@Bean` method in a test configuration). Then confirm the IT passes in CI.

## Notes

- The worker unit run printed expected WARN lines from `ReviewNotificationEnqueuer` tests (class names only); no flaky or skipped tests. No unit test above 5 s.
- `recurringJobDispatchesThroughJobRunr` costs 60 s when it fails and about 5-10 s when it passes; it uses the shared Jobs database container, so it adds no container.
- Not verified at all: dispatcher behaviour with the real platform tenant adapter (only the mock exists); concurrent dispatchers while held rows force a second page read.
