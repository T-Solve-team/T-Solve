---
module: worker
feature: email-outbox-review-fixes
task: 6
verdict: fail
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: worker — Task 6: email-outbox-review-fixes

## Summary

Reviewed commit `c5bfbaee` (worker outbox: `EmailOutboxRepository`, `EmailOutboxDispatchHandler`, `EmailOutboxDispatchHandlerTest`, `EmailOutboxIT`, `docs/bug-log.md`) against STORY-004 of the spec and the five Medium findings of the task 5 review.

Four of the five findings are fixed with a regression test that would fail without the fix (BUG-218, BUG-219, BUG-220, BUG-222). The fifth, BUG-221 (run the recurring job through real JobRunr), is **not fixed**: its new IT `recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` fails in CI (`backend-tests (it-other)`, PR #233 run 43602465544), so `mvn verify` is red. The cause is in the test, not the production code: the sender bean it registers is dropped by `SmtpEmailSender`'s own `@ConditionalOnProperty(spring.mail.host)`. The bug log nonetheless marks BUG-221 Fixed.

No STORY-004 criterion is broken by the fixes: oldest-first order, the 20-row send bound, one claim per attempt (AC-004.8), the 60-minute and three-attempt rules all hold (analysis below). Verdict **fail** because of the failing test.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | The production changes do what BUG-218/219/220 describe and keep AC-004.4–004.9 intact. |
| Completeness | fail | Task 6 must fix all five Medium items; item 3 (BUG-221) is not demonstrated — its only test fails. |
| Correctness | fail | Local: 115 worker unit tests, 0 failures; `spotless:check` clean. CI: `it-other` 536 tests, 1 error (`EmailOutboxIT.recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221`). A failing test means fail. |
| Integration | pass | No public contract changed. `withDueRows(Instant, int, Consumer)` is kept as an overload; the only other caller is `EmailOutboxIT.guardsOutcomes`. CI log shows JobRunr resolving the CGLIB-proxied handler and deserialising `EmailOutboxDispatchRequest` (the run reaches `EmailOutboxDispatchHandler.run`), which covers part of the old finding 3. |

### Stage 1 — task 5 Medium findings

| # | Finding (bug) | Fixed? | Regression test fails without the fix? |
|---|---------------|--------|----------------------------------------|
| 1 | Head-of-line blocking by held rows (BUG-218) | Yes, bounded: the claim reads up to 10 pages of 20 under one transaction until 20 rows are claimed (`EmailOutboxRepository.java:52-80`, `EmailOutboxDispatchHandler.java:69-83`); eligibility cached per Workspace per run (`:68, :74`). | Yes. `sendsPastHeldRowsAndCachesEligibility_AC_004_4_BUG_218` (test:103) needs a second page; the old code read one page, so the Active row would not be sent. `failedRowsDoNotConsumeSendBatch` (:143), `capsReadsAtTen…` (:158). IT `sendsNewerActiveRowPast25HeldRows` passed in CI (by elimination: 9 run, 1 error, which is BUG-221). |
| 2 | JobRunr default retries (BUG-219) | Yes. `@Job(retries = 0)` on `run` (`EmailOutboxDispatchHandler.java:51`). | Yes. `declaresZeroJobRunrRetries_AC_004_4_BUG_219` (:125) asserts the annotation and that a recurring job scheduled through `JobRequestScheduler` stores 0 retries. |
| 3 | No test through real JobRunr (BUG-221) | **No.** The IT exists but fails in CI. | n/a — the test itself fails (finding 1). |
| 4 | Outcome write outside the per-row try (BUG-220) | Yes. Nested try, outer catch logs the class name only and continues (`EmailOutboxDispatchHandler.java:85-103`). | Yes. `continuesAfterOutcomeWriteFailure_AC_004_6_AC_004_7_BUG_220` (:188) for SENT, FAILED and reschedule: before the fix the exception escaped and the second row was never sent. Asserts no private data, no stack trace. |
| 5 | Hold SQL never run (BUG-222) | Yes. | Yes as coverage: `holdsSuspendedWithoutAttemptThenFailsPastHour_AC_004_9_BUG_222` (IT:190) runs `hold` and the past-hour FAILED against PostgreSQL; passed in CI (by elimination). |

### Stage 1 — STORY-004 criteria after the fixes

| Criterion | Status | Evidence |
|-----------|--------|----------|
| AC-004.4 oldest first, interval | PASS | Every page is `order by created_at`; rows handled in page order; later pages only return rows not yet decided (held rows get `next_attempt_at > now`, claimed rows the lease end, finished rows leave `PENDING`, all visible inside the same transaction). `sendsStoredMessages`, IT `ordersLimitsAndDeletes` (22 rows → first 20 in order). |
| Batch bound | PASS | `claimed.size() == batchSize` break (`:71`) and `return claimed.size() < batchSize` (`:82`); at most 20 sends per run, at most 10 × 20 rows read and locked. `failedRowsDoNotConsumeSendBatch` (exactly 20 sends). |
| AC-004.5 / 004.6 | PASS | Unchanged send path; SENT written after the send. |
| AC-004.7 three attempts / 60 minutes | PASS | `exhausted` and `pastHour` unchanged; FAILED rows written in the claim do not count against the batch. `retriesFailures`, `failsAfterHour`, IT `retriesRefusedSmtp`. |
| AC-004.8 one claim per attempt | PASS | Locks taken by earlier pages stay held until the single commit; `skip locked` skips only other transactions' locks, and this transaction's own rows have been updated out of the `where` clause, so a second page cannot re-claim them. Another dispatcher skips them while locked and finds them not due after commit. Writes remain guarded by `attempt_count` and `status`. The longer transaction (up to 10 reads) adds lock time, not a double claim. IT `concurrentClaims` (25 rows, two runs; the 5-row run does take the second-read path) passed in CI. See finding 8. |
| AC-004.9 held, FAILED after 60 min | PASS | Lookup failure cached as ineligible (`isActive` returns false on exception); IT BUG-222. |
| AC-004.11 | PASS | New WARN logs the class name only; unit test asserts. |
| AC-004.12 | PASS (CI) | IT classes ran; one new case fails (finding 1), the AC-004.12 cases pass. |

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

Paths under `backend/src/` unless stated.

CRITICAL / High (blocks pass):

1. **[High] `test/java/vn/tsolve/worker/EmailOutboxIT.java:224-279` (sender at `:240`) — the BUG-221 IT fails in CI.** `recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` times out after 60 s with the row still `PENDING, attempt_count 0`. Every JobRunr run threw `IllegalStateException: Email sender unavailable` (`EmailOutboxDispatchHandler.java:55`). `.withBean(SmtpEmailSender.class, mailpit::sender)` goes through `registerBean`, which evaluates the class's `@Profile("worker")` and `@ConditionalOnProperty(prefix = "spring.mail", name = "host")`; no `spring.mail.host` is set, so the bean is skipped. `mvn verify` (it-other) is red, and task 5 finding 3 remains open.
   Fix: set `spring.mail.host`/`spring.mail.port` to Mailpit and `TSOLVE_SMTP_FROM` in the test's property values (or supply the sender from a test `@Configuration` `@Bean` method), then confirm in CI.

Informational (does not block on its own):

**Medium**

2. `docs/bug-log.md:232-236` — the rows do not match the code: BUG-221 is `Fixed` though its test fails; all five rows have `Uncommitted` in the PR/commit column instead of `c5bfbaee` / PR #233; BUG-218, BUG-221, BUG-222 still say the IT was "written, not run" / "Docker execution deferred". The Found-by column ("KAN-162 independent reviews") names no person, which `backend/AGENTS.md` §10 asks for.
   Fix: BUG-221 back to Open (or In progress) until its IT passes; fill in the commit/PR; update the notes with the CI result.

**Low**

3. `main/java/vn/tsolve/worker/service/impl/EmailOutboxDispatchHandler.java:69, 76` — starvation is reduced, not removed: held rows are still re-due after one interval (30 s), and the claim reads at most 10 × 20 = 200 rows. More than 200 held rows older than an Active Workspace's row still block it every run, up to 60 minutes. The bug log states the cap honestly. Fix if needed: hold until the first backoff, or order held rows behind sendable ones.
4. `harnessclaude/artifacts/04-plans/worker/design.md:212, 229` — the design still describes one `select … limit :batch` per claim; the multi-page read, its cap of 10, and the per-run eligibility cache are not written down. Fix: update the claim step and the edge cases.
5. `main/java/vn/tsolve/worker/repository/EmailOutboxRepository.java:44-49` — the `Consumer` overload of `withDueRows` is now used only by `EmailOutboxIT.guardsOutcomes` (production code kept for a test; AGENTS §9 "no unused code"). Fix: move the test to the `Predicate` form and remove the overload.
6. `docs/bug-log.md:180` — `c5bfbaee` also edits the BUG-164 row to cite `continuesAfterAFailedQueueCall_BUG_164`, a test that exists only after `e9eec864` (BUG-224). At `c5bfbaee` the row names a missing test, and it is an unrelated change in this feature's commit. Correct at HEAD.
7. `EmailOutboxDispatchHandler.java:51-55` — with `retries = 0` a lasting cause (no sender, Jobs DB down) still leaves one FAILED JobRunr job per run (the CI log shows 3 per 15 s at a 5 s interval; about 2,880 a day at 30 s). Much better than ×10, but still growing storage. Fix (optional): return early with a WARN when the sender is missing, or rely on a JobRunr deletion policy for failed jobs.
8. Test gap for AC-004.8 with the new read loop: `concurrentClaims` uses only Active rows; no IT runs two dispatchers while held rows make both read further pages. The analysis above says it is safe; there is no direct evidence.

## Assumptions made

Judgment calls the reviewer could not ask the user about:

1. The failing BUG-221 IT is rated High and blocks the verdict, although the defect is in the test and not in production code: the evaluate rules make any failing test a fail, and the task's own goal (finding 3) is unproven.
2. The 10-read cap (finding 3) is accepted as a sufficient fix for task 5 finding 1. The spec sets no fairness or latency rule between Workspaces; if a Suspended Workspace must never delay others, this stays open.
3. BUG-218 and BUG-222 ITs are taken as passed by elimination (EmailOutboxIT: 9 run, 1 error, and the error is BUG-221); the log has no per-method pass lines.
4. "Regression test fails without the fix" was judged by reading the old code against each test, not by reverting the fix and re-running (no code edits allowed). For BUG-218 the unit test would not even compile against the old repository signature.
5. Coverage: no threshold in the spec and no backend coverage command; none reported.

## Test Report

Full detail: `harnessclaude/artifacts/06-evaluation/worker/task-6-email-outbox-review-fixes-test-report.md`.

- Commit under test: `2baa4282` (contains `c5bfbaee`; PR #233 head is the same commit).
- Run by the reviewer: `mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` → exit 0, 11 classes, 115 tests, 0 failures. `mvn -q spotless:check` → exit 0. `sdd.ts trace worker STORY-004` → 12/12.
- CI only (run 43602465544): unit, it-keycloak-a, it-keycloak-b pass; **it-other fails** — 536 tests, 1 error: `EmailOutboxIT.recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221`. `EmailOutboxIT` ran 9 tests (the 3 new included), `JobRunrTenantIT` 1/0, `SmtpEmailSenderIT` 3/0.
- Not verified: a recurring job delivering mail through real JobRunr (the failing IT); the real platform tenant adapter; concurrent dispatchers with held rows on several pages.
