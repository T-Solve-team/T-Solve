---
module: ticket-review
feature: review-email-notification
task: 11
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-review — Task 11: Email the writer about Reject and Request Changes (STORY-010, KAN-155)

## Summary

Code range `558ded70..ce6ecc31` (squashed PR #146). I reviewed it against `spec.md` STORY-010 (AC-010.1–010.6), using the design section "KAN-155 extension" only to check edge cases and risks.

I checked the three focus points directly.
- **Enqueue only after commit.** `TicketDecisionServiceImpl.java:85-90` publishes `ReviewFeedbackDecidedEvent` inside the `@Transactional decide`, and only for non-Approve actions. `ReviewNotificationEnqueuer.java:25` listens with `@TransactionalEventListener(AFTER_COMMIT)`, so a rollback enqueues nothing. A PostgreSQL IT proves this (`enqueuesNothingWhenTheBatchRollsBack_AC_010_1`). The enqueue writes through JobRunr's own Hikari pool (`JobRunrRuntime.java:24-29`), so it does not join the finished business transaction.
- **No SMTP inside a transaction.** The only transaction on the job path is `ReviewNotificationServiceImpl.java:22` `@Transactional(readOnly = true)`, and it returns before `ReviewNotificationJobHandler.java:53` calls the sender. `TenantJobRequestHandler.run` and `runInTenant` add no transaction. The SMTP send also runs in the worker job, never in the HTTP request.
- **No personal data in logs.** The job payload holds only `(workspaceId, handlerType, decisionId)`. The enqueuer swallows exception text and logs only counts (`ReviewNotificationEnqueuer.java:36-42`). `DeliveryException` and the missing-sender `IllegalStateException` carry generic messages, and output-capture tests assert that neither the recipient nor the feedback appears.

All 6 criteria are implemented and named in tests, and the tests pass. My two MAJOR findings are both non-blocking under the checklist's CRITICAL definitions. The first means that today no real HTTP decision can produce an email in any deployment, because the platform does not bind a Workspace for the worker in the API request. I record it as an open judgment call rather than a failure, because the gap is in a platform adapter that this module does not own.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-010.1: one job per decided ticket, keyed by `review_decision_id`, only after commit; none for Approve (`TicketDecisionServiceImpl.java:86`). AC-010.2: a native query loads the decision, ticket key and writer `app_user.email` (`ReviewDecisionRecordRepository` `findWriterNotification`), and the job sends one plain-text mail through the KAN-162 `ReviewNotificationSender` with the matching subject. AC-010.3: enqueue failures are caught and the decision stays committed with `count` returned; with queueing disabled the listener returns early (`:27-31`). AC-010.4: `DeliveryException` propagates out of `execute`, so JobRunr retries. AC-010.5: no row (no writer, missing decision) or an `APPROVED` decision gives `Optional.empty()`, and the job completes without sending. AC-010.6: no address or feedback is logged. |
| Completeness | pass | 6/6 criteria traced to named tests. There is no EXTRA behaviour. |
| Correctness | pass | 18 targeted tests pass in my run (ReviewNotificationIT 8, ReviewNotificationJobHandlerTest 3, ReviewNotificationSenderTest 7), plus ReviewNotificationSenderIT 1. The dispatcher's full `mvn verify` on HEAD reported 807 unit + 165 IT tests, 0 failures, Spotless OK. No CRITICAL findings. |
| Integration | pass (with open judgment call) | The KAN-162 sender is reused unchanged and the job type is registered via `TenantJobHandler`. However, `TenantJobServiceImpl.enqueue` needs a Workspace bound in `WorkerTenantService`, and nothing in the API request path binds one (see Issue 1). End-to-end delivery therefore depends on an unbuilt platform adapter that the spec does not list as a dependency. |

## Traceability

```
  ✓ AC-010.1  backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationJobHandlerTest.java, backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationIT.java
  ✓ AC-010.2  backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationIT.java
  ✓ AC-010.3  backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationIT.java
  ✓ AC-010.4  backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationJobHandlerTest.java
  ✓ AC-010.5  backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationIT.java
  ✓ AC-010.6  backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationJobHandlerTest.java, backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationIT.java
trace ticket-review (STORY-010): 6/6 acceptance criteria have a test
```

I read each named test, and each one checks what it names. `usesItsOwnJobType_AC_010_1` is weak on its own, but the IT covers AC-010.1 fully.

## Issues

No CRITICAL findings. The findings below do not block the verdict.

1. **MAJOR (integration / open judgment call):** `backend/src/main/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueuer.java:35` calls `TenantJobServiceImpl.enqueue`, which throws `IllegalStateException("Enqueue requires a bound workspace")` at `TenantJobServiceImpl.java:24` unless `WorkerTenantService.currentWorkspaceId()` is bound.
   - The only `WorkerTenantService` is `MockWorkerTenantService` (profile `worker-mock`). Its ThreadLocal is set only by `TenantExecutionServiceImpl.runInTenant`, which runs during job execution, never in an HTTP request.
   - So in an API process with `TSOLVE_WORKER_ENABLED=true`, every enqueue fails, the exception is swallowed and one WARN with counts is logged. With the default `false`, nothing is enqueued.
   - Result: no deployed configuration emails the writer after a real decision. AC-010.1/010.2 hold only with a mocked `TenantJobService` (`ReviewNotificationIT.java:83`).
   - Fix: this needs a spec or platform decision. Either (a) add the platform `TenantContext` → `WorkerTenantService` binding (architecture.md, Background Jobs: "workspaceId, taken from TenantContext when it is enqueued") as a "Required from other modules" item and a Definition of Done note for STORY-010, or (b) bind the worker context from the request's Workspace (`TenantScope`) inside the enqueuer once the platform provides it.
   - Add one IT that enqueues through the real `TenantJobServiceImpl` (as `JobRunrTenantIT` does) from an HTTP decision, so the gap stays visible.
2. **MAJOR (availability, AC-010.3 failure path):** `ReviewNotificationEnqueuer.java:33-40` enqueues one job per decision, synchronously, on the request thread after commit, and keeps going after a failure.
   - If the Jobs database is unreachable, each `scheduler.enqueue` waits for Hikari's default `connectionTimeout` (30 s). `JobRunrRuntime.java:24-29` does not set it.
   - A 500-ticket Reject therefore holds the HTTP thread for up to about 250 minutes before it returns the "same success response". In practice the client times out, and Tomcat threads can run out.
   - Fix: stop after the first failure and count the rest as failed, or enqueue the batch in one call (`JobRequestScheduler.enqueue(Stream<JobRequest>)`). Set a short `connectionTimeout` on the Jobs pool.
3. **Minor (observability):** `ReviewNotificationEnqueuer.java:42` drops the exception entirely, so operators cannot tell "no bound workspace" from "Jobs DB down". Fix: log `e.getClass().getSimpleName()`. A class name carries no mail data.
4. **Minor (tests):** the "job queueing disabled" branch of AC-010.3 (`ReviewNotificationEnqueuer.java:27-31`, `getIfAvailable() == null`) has no named test. Other ITs that run with the worker disabled cover it only incidentally, e.g. the HTTP request-changes in `ReviewHistoryApiIT.showsANewHttpDecisionInHistory_AC_011_6`. Fix: add a named test with no `TenantJobService` bean.
5. **Informational:** delivery is at least once. If SMTP accepts the mail and the job then fails before JobRunr records success, the writer gets a duplicate email. This is acceptable for a demo, and `TenantJobHandler` documents repeated delivery. JobRunr's default of 10 retries applies, and the spec puts the KAN-162 three-attempt limit out of scope.

## Assumptions made

- I treat Issue 1 as a dependency gap, not a code defect of this task. Multi-tenant request binding does not exist anywhere in production yet (`LocalTenantScope` binds only during sign-in), and STORY-009 Review has the same limitation. **Open judgment call for the user:** may STORY-010 count as passing while end-to-end email delivery cannot happen until the platform binding exists, or should the spec first record that dependency (verdict fail until then)?
- I classify Issue 2 as MAJOR, not CRITICAL. It appears only when the Jobs database is down, and the response eventually is the spec'd success. **Open judgment call:** should it block?
- "Plain-text email … through the KAN-162 SMTP sender" is met by reusing `ReviewNotificationSender` unchanged. I did not re-review its subject and body wording (already evaluated in KAN-162).

## Test Report

See `task-11-review-email-notification-test-report.md`. Targeted run on HEAD: 19 tests, 0 failures, 0 errors. Full HEAD `mvn -Dapi.version=1.44 verify` (dispatcher evidence): 807 unit + 165 IT, 0 failures, Spotless OK.
