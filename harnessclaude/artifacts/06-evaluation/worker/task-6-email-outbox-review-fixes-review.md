---
module: worker
feature: email-outbox-review-fixes
task: 6
verdict: pass
attempt: 2
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: worker — Task 6: email-outbox-review-fixes

## Summary

Re-evaluation after attempt 1 (fail). Code under review: `c5bfbaee` (the outbox fixes) + `e6570be1` (BUG-225, test fix); `cd8186b4` fills the bug-log commit column. PR #233 head is `cd8186b4`.

1. **Attempt 1 High (BUG-221 IT fails) — fixed, test code only.** `e6570be1` changes only `backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java` (sets `spring.mail.host` to Mailpit at `:247`, asserts one `SmtpEmailSender` bean at `:256`), `backend/src/test/java/vn/tsolve/worker/WorkerMailpit.java` (`host()` at `:41-43`) and `docs/bug-log.md` (BUG-225 row). With the property set, `SmtpEmailSender`'s `@ConditionalOnProperty(spring.mail.host)` holds and the sender bean is registered; the supplier `mailpit::sender` sets the mapped port itself, so no port property is needed. CI on `cd8186b4`: `backend-tests (it-other)` passed, 536 tests, 0 failures, 0 errors; `EmailOutboxIT` 9 tests (the file has 9 `@Test`, none disabled), 0 failures, so `recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` ran and passed. The log has no "Email sender unavailable" line (attempt 1 had one per run).
2. **Attempt 1 Medium (bug log) — fixed for the commit column.** BUG-218, 219, 220, 222 show `PR #233 (c5bfbae)`; BUG-221 `PR #233 (c5bfbae, test fixed in e6570be (BUG-225))`; BUG-225 `PR #233 (e6570be)`. All short ids match the real commits and their contents. BUG-221 `Fixed` is now true (its IT passes in CI). Left over: the notes of BUG-218, 221, 222 still say the IT was "written, not run" / "Docker execution deferred" (Low, issue 2).
3. **No regression.** No worker production code for the outbox changed since `c5bfbaee` (`git diff c5bfbaee HEAD -- backend/src/main/java/vn/tsolve/worker/` touches only `ReviewNotificationEnqueuer.java`, which is task 21 / BUG-223, not this feature). Attempt 1's analysis of AC-004.4 to AC-004.12, BUG-218/219/220/222 and the Stage 1 tables is reused unchanged, since that code did not change. Worker unit tests 115/115 pass locally, spotless clean, all four backend CI jobs green.

Verdict **pass**. Low findings remain (below), including test residue that one CI run shows leaking into the next IT class.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Production code unchanged since attempt 1, which found it does what BUG-218/219/220 describe and keeps AC-004.4 to 004.9 (reused). |
| Completeness | pass | All five task 5 Medium findings are now fixed and shown by tests; item 3 (BUG-221: recurring job through real JobRunr, proxied handler, PostgreSQL storage, Mailpit delivery) passes in CI. |
| Correctness | pass | Local: 11 worker unit test classes, 115 tests, 0 failures; `spotless:check` exit 0. CI run 43603714210 on `cd8186b4`: unit, it-keycloak-a, it-keycloak-b, it-other all success; it-other 536 tests, 0 failures, 0 errors. |
| Integration | pass | No public contract changed by `e6570be1`. The BUG-221 IT runs the real wiring (`WorkerConfiguration`, `EmailOutboxConfiguration`, `EmailOutboxRegistration`, CGLIB-proxied handler) and the row reaches `SENT, attempt_count 1` with the message in Mailpit. |

## Traceability

```
  ✓ AC-004.1  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxServiceImplTest.java, backend/src/test/java/vn/tsolve/worker/config/WorkerConfigurationTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.2  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxServiceImplTest.java
  ✓ AC-004.3  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxServiceImplTest.java, backend/src/test/java/vn/tsolve/worker/config/WorkerConfigurationTest.java
  ✓ AC-004.4  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxRegistrationTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.5  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.6  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.7  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.8  backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.9  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.10  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
  ✓ AC-004.11  backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandlerTest.java, backend/src/test/java/vn/tsolve/worker/service/impl/EmailOutboxServiceImplTest.java
  ✓ AC-004.12  backend/src/test/java/vn/tsolve/worker/EmailOutboxIT.java
trace worker (STORY-004): 12/12 acceptance criteria have a test
```

## Issues

No CRITICAL or High findings. Paths under `backend/src/` unless stated.

**Low**

1. `test/java/vn/tsolve/worker/EmailOutboxIT.java:276-278` — test residue leaks into the next IT class. The `finally` deletes only the recurring job, not job instances it already created (the 5 s schedule is below JobRunr's 15 s poll, so JobRunr "catches up" and creates several). In CI run 43603714210 three `EmailOutboxDispatchHandler.run` jobs were picked up right after `EmailOutboxIT` by `JobRunrTenantIT`'s background server on the shared Jobs DB and failed with `NoSuchBeanDefinitionException: No qualifying bean of type 'vn.tsolve.worker.service.impl.EmailOutboxDispatchHandler'` (it-other log, 14:15:48). Nothing failed, but it is noise and a possible source of flakiness if another IT ever counts jobs or failed jobs. Fix: in the `finally`, also delete the remaining jobs of that recurring job (for example via the storage provider, by recurring job id / state) or use a dedicated JobRunr table prefix for this test.
2. `docs/bug-log.md:232, 235, 236` — notes of BUG-218, BUG-221 and BUG-222 still say "IT written, not run" / "Written and compiled only; Docker execution deferred". CI on `cd8186b4` ran and passed all three ITs. Fix: replace with the CI result (PR #233, it-other green). The commit column itself is now correct.
3. `docs/bug-log.md:239` — BUG-225 has no test named with its id; it points to `recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` and explains why. The project rule asks a fix to come with a test named with the bug id. See Assumptions 1.
4. Still open from attempt 1 (code unchanged, not re-raised as blockers): Found-by column names no person (attempt 1 issue 2, last part); starvation beyond 200 held rows (issue 3); design not updated for the multi-page claim (issue 4); `withDueRows` `Consumer` overload kept for one test (issue 5); one FAILED JobRunr job per run on lasting causes (issue 7); no IT for two dispatchers with held rows on several pages (issue 8).

## Assumptions made

Judgment calls the reviewer could not ask the user about:

1. BUG-225 keeps the BUG_221 test name instead of a `_BUG_225` test. Accepted as Low, not blocking: BUG-225 is a defect in that test's own setup, and the new `hasSingleBean(SmtpEmailSender.class)` assertion (`EmailOutboxIT.java:256`) is the regression check and would fail without the fix. If the user wants the rule applied literally, rename or add the id.
2. "`recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` passed" is concluded from the class summary (9 run, 0 failures, 0 errors, 0 skipped; 9 `@Test` methods, none disabled or conditional) and from the absence of "Email sender unavailable"; surefire prints no per-method pass lines and the method name does not appear in the log.
3. The test residue (issue 1) is rated Low because no test failed and production is unaffected.
4. Attempt 1's conclusions on code unchanged since `c5bfbaee` (production outbox code and the other tests) are reused, as allowed by the dispatcher; only `e6570be1` and `cd8186b4` were reviewed afresh.
5. Coverage: no threshold in the spec and no backend coverage command; none reported.

## Test Report

Full detail: `harnessclaude/artifacts/06-evaluation/worker/task-6-email-outbox-review-fixes-test-report.md`.

- Local (HEAD `cd8186b4`): `mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` exit 0, 11 classes, 115 tests, 0 failures/errors. `mvn -q spotless:check` exit 0. `sdd.ts trace worker STORY-004` 12/12.
- CI, PR #233 head `cd8186b4`, run 43603714210: backend-tests unit, it-keycloak-a, it-keycloak-b, it-other all success. it-other: 536 tests, 0 failures, 0 errors; `EmailOutboxIT` 9/0/0, `JobRunrTenantIT` 1/0/0, `SmtpEmailSenderIT` 3/0/0, BUILD SUCCESS.
