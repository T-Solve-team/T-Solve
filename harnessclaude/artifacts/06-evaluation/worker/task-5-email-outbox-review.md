---
module: worker
feature: email-outbox
task: 5
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: worker — Task 5: email-outbox

## Summary

Reviewed `023e517c..29405ede` on `feat/KAN-162-worker-email-outbox`: the outbox (`EmailOutboxServiceImpl`, `EmailOutboxRepository`, Jobs changelog), the dispatcher (`EmailOutboxDispatchHandler`, `EmailOutboxRegistration`), `SmtpEmailSender`, `OutboundEmail`, `EmailAddressRules`, their configuration and tests. `ReviewNotificationEnqueuer` and `ReviewEmail*` were not reviewed (module ticket-review).

All 19 acceptance criteria (AC-001.1–001.7, AC-004.1–004.12) are implemented and each has a test that names and checks it. No CRITICAL code-review finding and no Critical/High security finding. Verdict **pass**, with 5 Medium and 5 Low findings that do not block, and 6 spec interpretations listed under "Assumptions made" for the user to confirm.

Limits of this review: the integration tests (`EmailOutboxIT`, `SmtpEmailSenderIT`, `JobRunrTenantIT`) were not run by the reviewer (no Docker); they are evidenced by the CI of PR #233 on the same commit. AC-004.8 and AC-004.12 therefore rest on CI evidence only.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every AC checked against code and its tests (table below). |
| Completeness | pass | 19/19 criteria traced; no MISSING. No EXTRA behavior beyond BUG-215/216/217 fixes, which serve AC-004.7 and AC-004.11. |
| Correctness | pass | Local: 106 worker unit tests, 0 failures; `spotless:check` clean. CI on the same commit: all backend jobs pass, the three IT classes ran. No CRITICAL finding. |
| Integration | pass | `EmailOutboxService.queue(OutboundEmail) → UUID` and `SmtpEmailSender.send(OutboundEmail)` match the spec's interface contracts; Workspace is captured from the binding, not caller-supplied; the Jobs pool is not exposed as a Spring `DataSource` (AC-002.13 kept, `WorkerConfigurationTest`). Gap: the recurring job is never run through real JobRunr in a test (finding 3). |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.1 multipart / plain text from "T-Solve" | PASS | `SmtpEmailSender.java:41-49`; `SmtpEmailSenderTest.sendsPlainText`, `SmtpEmailSenderIT.sendsPlainText`, `sendsAlternatives` (CI) |
| 2 | AC-001.2 bodies unchanged, subject line breaks → space | PASS | `SmtpEmailSender.java:31-37`; `SmtpEmailSenderTest.sendsMultipartAlternatives`, `replacesSubjectLineBreaks` |
| 3 | AC-001.3 not exactly one mailbox → rejected before delivery | PASS | `EmailAddressRules.java:24-40`; `SmtpEmailSenderTest.rejectsInvalidRecipient` (list, group, CRLF, empty; `verifyNoInteractions(mail)`) |
| 4 | AC-001.4 one attempt, safe failure | PASS | `SmtpEmailSender.java:51-54` (fixed message, no cause); `reportsSafeFailure` |
| 5 | AC-001.5 finite timeouts | PASS | `application.yml:8-12`; `configuresTimeouts` (5000/3000/5000 ms) |
| 6 | AC-001.6 nothing private in logs | PASS | The sender has no log statement; `reportsSafeFailure` captures output |
| 7 | AC-001.7 starts without SMTP settings | PASS | `@ConditionalOnProperty(spring.mail.host)` + `@Profile("worker")`; `startsWithoutSmtp` |
| 8 | AC-004.1 one PENDING row, returns UUID | PASS | `EmailOutboxServiceImpl.java:26-36`, `EmailOutboxRepository.java:26-40`; `queuesMessage`, `EmailOutboxIT.queuesInJobsDatabase` (CI) |
| 9 | AC-004.2 invalid input rejected, no row | PASS | `EmailAddressRules.java:11-22`, `EmailOutboxServiceImpl.java:28`; `rejectsInvalid` (9 cases), `rejectsUnboundWorkspace` |
| 10 | AC-004.3 not configured / cannot store → reported, no partial row | PASS | No bean unless `tsolve.worker.enabled` (`EmailOutboxConfiguration.java:20`); single-statement insert; `sanitizesFailure`. See finding 8. |
| 11 | AC-004.4 interval (30 s), due rows oldest first | PASS | `EmailOutboxRegistration.java:23-27`, `EmailOutboxRepository.java:45-49`; `EmailOutboxRegistrationTest`, `sendsStoredMessages`, `EmailOutboxIT.ordersLimitsAndDeletes` (CI). See assumption 1. |
| 12 | AC-004.5 only the stored row, no Workspace database | PASS | `EmailOutboxDispatchHandler.java:78-80`; `sendsStoredMessages` (`runIn` never called), `deliversThroughOutbox` (CI) |
| 13 | AC-004.6 SENT with send time | PASS | `EmailOutboxDispatchHandler.java:89`; `sendsStoredMessages` (time read after the send), `deliversThroughOutbox` |
| 14 | AC-004.7 backoff, third attempt / 60 min → FAILED, expired lease | PASS | `EmailOutboxDispatchHandler.java:66-67, 81-87, 102-108`; `retriesFailures`, `failsAfterHour`, `expiresLease`, `attemptsOldUnclaimedRows`, `EmailOutboxIT.retriesRefusedSmtp`, `guardsOutcomes` (CI). See assumption 2. |
| 15 | AC-004.8 one claim per attempt | PASS | `for update skip locked` in one transaction + guard `attempt_count = :count and status = 'PENDING'` on every write (`EmailOutboxRepository.java:45-49, 73, 85, 100`); `EmailOutboxIT.concurrentClaims` (CI only) |
| 16 | AC-004.9 ineligible Workspace held, FAILED after 60 min | PASS | `EmailOutboxDispatchHandler.java:68-70, 94-100`; `holdsIneligible` (6 cases). See finding 5, assumption 3. |
| 17 | AC-004.10 retention delete | PASS | `EmailOutboxDispatchHandler.java:91`, `EmailOutboxRepository.java:109-118`; `deletesOldRows`, `ordersLimitsAndDeletes` (boundary at exactly 30 days, PENDING rows kept) |
| 18 | AC-004.11 no address/subject/body in logs or errors | PASS | Only log: `EmailOutboxDispatchHandler.java:56` (class name only); fixed-message exceptions without cause; `sanitizesErrors_BUG_216`, `hidesDeliveryFailure`, `sanitizesFailure` |
| 19 | AC-004.12 IT against PostgreSQL + local SMTP | PASS | `EmailOutboxIT` (6 tests) — CI only |

## Traceability

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

## Issues

CRITICAL (blocks pass): none.

Informational (does not block). Paths are under `backend/src/main/java/vn/tsolve/worker/` unless stated.

**Medium**

1. `repository/EmailOutboxRepository.java:45-49` with `service/impl/EmailOutboxDispatchHandler.java:68-70` — head-of-line blocking across Workspaces. A held row (Workspace Suspended, unknown, or lookup failing) gets `next_attempt_at = now + interval`, so it is due again at the next run, and the claim takes the 20 oldest due rows whatever their Workspace. Twenty or more held rows older than everything else fill each batch; emails of Active Workspaces queued after them are delayed until the held rows turn FAILED (up to 60 minutes). Not covered by a test (design edge cases 4 and 11 are tested separately, never together).
   Fix: hold for longer than one interval (for example the first backoff), or do not let held rows count against the batch (loop the claim until 20 sendable rows or no due rows remain).
2. `service/impl/EmailOutboxDispatchHandler.java:47-58` — a failing run is thrown to JobRunr with its default retries (the handler has no `@Job(retries = 0)`), while the recurring job fires again every 30 seconds. A lasting cause (worker without `spring.mail.host`, Jobs database down) produces about 2,880 failed runs a day, each retried up to 10 times and then kept as FAILED jobs in JobRunr storage.
   Fix: `@Job(retries = 0)` on `run` (the next tick is the retry), as `ReviewExpiryJobHandler` does.
3. Test gap — nothing runs `worker-email-outbox` through real JobRunr: `EmailOutboxRegistrationTest` mocks the scheduler, and every IT calls `handler.run(...)` directly. Serialisation of the empty `EmailOutboxDispatchRequest` record, resolution of the `@Transactional` (proxied) handler bean, and the actual 30-second trigger are unverified. The same request shape exists in `review/dto/ReviewExpiryDispatchRequest`, which lowers the risk but is not evidence for this job.
   Fix: one IT that starts the worker context against the Jobs database, queues a row and waits for SENT.
4. `service/impl/EmailOutboxDispatchHandler.java:78-89` — `repository.finish` / `reschedule` after a send are outside the per-row `try`. If one of them throws (Jobs database blip), the run aborts: the rows not yet sent keep their lease and lose one attempt without being sent, and a row already accepted by SMTP is sent again after the lease. BUG-215 fixed this for send exceptions only.
   Fix: catch per row around the outcome write as well, log the class name, continue.
5. Test gap for AC-004.9 — "left PENDING, attempt count unchanged" is asserted only against a mocked repository (`EmailOutboxDispatchHandlerTest.holdsIneligible`). The SQL of `hold` (`EmailOutboxRepository.java:82-95`) is never executed in a test for a row that stays PENDING; `EmailOutboxIT.ordersLimitsAndDeletes` exercises only the past-60-minutes branch. Design edge case 4 names `EmailOutboxIT` for this.
   Fix: add an `EmailOutboxIT` case with a Suspended Workspace inside the hour, asserting status, `attempt_count = 0` and no message in Mailpit.

**Low**

6. `config/EmailOutboxProperties.java:17-24` — `interval`, `lease`, `retention` and the backoff entries are not checked to be positive, and nothing relates `lease` to `batch-size × SMTP timeouts`. A larger batch with the default lease lets leases expire while the batch is still sending, which sends duplicates and burns attempts.
   Fix: validate positive durations; document or check `lease > batchSize × 13 s`.
7. `service/impl/EmailOutboxDispatchHandler.java:64-75` — `isWorkspaceActive` is called once per row while the claim transaction holds the row locks. Harmless with the in-memory mock; with the real platform adapter this is up to 20 catalog reads inside a Jobs-database transaction.
   Fix: resolve eligibility per distinct Workspace, once per run.
8. `src/test/java/vn/tsolve/worker/config/WorkerConfigurationTest.java:39-50` — the "Jobs database is not configured" half of AC-004.3 (no `EmailOutboxService` bean) is asserted here, in a test named for AC-002.12/AC-002.14; the test that cites AC-004.3 in this file (line 112) checks the enabled case.
   Fix: cite AC-004.3 in the name of the disabled-case test.
9. `config/EmailOutboxProperties.java:16` — `@ConditionalOnProperty` on a `@ConfigurationProperties` record has no effect by itself; registration is already controlled by `EmailOutboxConfiguration`.
   Fix: remove it, or keep it only if the BUG-136 scan test needs it (it does pass today).
10. `client/SmtpEmailSender.java:41, 45-49` — an empty or blank `bodyHtml` is sent as a multipart email with an empty HTML part.
    Fix: treat a blank HTML body as absent, in `EmailAddressRules.validate`.

## Security Review — worker / email-outbox — 2026-11-14

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 1 | 1 |

- Secrets: pattern grep over the added lines of the feature diff — no hit. `.env.example` adds only commented names with non-secret defaults. Tracked env files: `.env.example`, `frontend/.env.example` only; `.gitignore` covers `.env` and `.env.*`. CI `gitleaks` passes on the same commit.
- Dependencies: `backend/pom.xml` is unchanged in the range; no dependency audit tool was run (none is set up for Maven here).
- Code: all SQL in `EmailOutboxRepository` uses named parameters; no string concatenation. One log statement in the feature, class name only.
- STRIDE:
  - Tampering — recipient and subject are validated against header injection at queue time and again in the sender (`EmailAddressRules`); status updates are guarded by old status and attempt count. No finding.
  - Information disclosure — exceptions carry fixed messages and no cause; tests capture output. Recipient address and bodies sit unencrypted in `email_outbox` for up to 30 days after the row is finished; the spec accepts this (NFR-5). **[LOW][STRIDE]** `db/jobs-changelog/001-email-outbox.yaml:8-21`: bodies of SENT rows are kept for the whole retention although nothing reads them again. Fix (needs a spec decision): clear `body_text`/`body_html` when a row is marked SENT.
  - Denial of service — **[MEDIUM][STRIDE]** finding 1 above (one Workspace's held rows can delay every other Workspace's email for up to an hour); finding 2 (retry build-up in JobRunr storage). SMTP waits are bounded (AC-001.5); the batch and the retention delete are bounded.
  - Elevation of privilege — the Workspace comes from the bound context, never from the caller; the dispatcher reads all Workspaces' rows by design and opens no Workspace database. No HTTP surface. No finding.

## Assumptions made

Judgment calls the reviewer could not put to the user; each was read in the implementation's favour and should be confirmed.

1. AC-004.4 "attempt every PENDING row whose next attempt time has come" is read as "over successive runs": one run attempts at most `batch-size` (20) rows, the rest wait for the next tick (design edge case 11). If "every due row in each run" is meant, this is a fail.
2. AC-004.7 "count it as a failed attempt under the same rules" for an expired lease: the implementation applies the attempt limit and the 60-minute limit, but retries at once in the same run instead of waiting the configured backoff (5 or 15 minutes). The lease (5 minutes) acts as the wait. Accepted as the design states it; the spec sentence can be read to require the backoff too.
3. AC-004.9 "still unattempted 60 minutes after it was created → FAILED" is also applied to a row that was already attempted once and whose Workspace is ineligible after the hour (`EmailOutboxDispatchHandler.java:69`). The spec is silent on that case; FAILED is consistent with AC-004.7.
4. Finding 1 (head-of-line blocking) is rated Medium, not blocking: the spec sets no fairness or latency requirement between Workspaces, and the delay is bounded at 60 minutes. If a Suspended Workspace must never delay other Workspaces' email, treat it as a fail.
5. Coverage: the spec states no threshold and the backend has no coverage command, so no number is reported and none was required.
6. `EmailOutboxIT`, `SmtpEmailSenderIT` and `JobRunrTenantIT` were accepted on CI evidence (PR #233, run 43600347361, same commit `29405ede`) because Docker is off locally. AC-004.8 and AC-004.12 have no other evidence.

## Test Report

Full detail: `harnessclaude/artifacts/06-evaluation/worker/task-5-email-outbox-test-report.md`.

- Commit under test: `29405ede5570b51ae21b0b4365a31f15859938d6` (local HEAD = PR #233 head = CI run head).
- Run by the reviewer: `mvn -q -Dtest='vn.tsolve.worker.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` → exit 0, 11 classes, 106 tests, 0 failures, 0 errors, 0 skipped. `mvn -q spotless:check` → exit 0.
- CI only (not run by the reviewer): `backend-tests (it-other)` — `EmailOutboxIT` 6 tests, `SmtpEmailSenderIT` 3, `JobRunrTenantIT` 1, all 0 failures (class lines read from the job log); job total 533 tests, 0 failures. `backend-tests (unit)` 1737 tests, 0 failures, `spotless:check` ran. `it-keycloak-a`, `it-keycloak-b`, `gitleaks` pass.
- Traceability: STORY-001 7/7, STORY-004 12/12.
- Coverage: not measured.
- Not verified: the recurring job running under real JobRunr; behaviour with the real platform tenant adapter (only the mock exists); a dependency audit.
