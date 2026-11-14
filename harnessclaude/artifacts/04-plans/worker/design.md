---
module: worker
date: 2026-11-07
---

# Design: worker

The sections before the `# STORY-002` heading cover STORY-001 (KAN-162 SMTP delivery). STORY-002 (KAN-102 tenant jobs) follows.

Spec: [Worker SMTP delivery](../../03-modules/worker/spec.md)

## Architecture Overview

The review SMTP sender accepts the writer address, ticket key, decision kind and reason or comment. It validates a single mailbox, builds a UTF-8 plain-text message, and calls Spring Boot's `JavaMailSender` once. A mail failure becomes a delivery exception with a fixed, safe message. The sender runs outside a database transaction.

The sender is available when the SMTP host is configured. Startup does not connect to SMTP. Review and JobRunr will call this boundary in the later integration slice, after recipient lookup in the decision's Workspace.

## Data Model

The notification is an in-memory command. Its decision kind is either Reject or Request Changes. Its single recipient is already resolved by the caller. Ticket key and review feedback appear in the body, while the subject is fixed for each decision kind. There are no schema changes.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| SMTP client | Spring Boot Mail and `JavaMailSender` | Jakarta Mail session configured directly | The project uses Spring Boot. The user selected its standard mail configuration. |
| Message format | Plain text with fixed subjects `Ticket rejected` and `Changes requested for your ticket` | HTML templates and dynamic headers | The two templates need only the ticket key and feedback. Fixed headers keep review text in the body. |
| Configuration | Spring's mail properties for SMTP host, port, credentials and TLS settings. `TSOLVE_SMTP_FROM` supplies the sender address. Use the documented Spring Boot timeout example as initial connect/read/write defaults of 5/3/5 seconds. | Dedicated SMTP settings wrapper | Standard properties preserve deployment control. Environment values supply addresses and credentials. |
| Failure boundary | Return an internal delivery exception with a fixed message. Keep the raw transport exception out of the propagated exception and logs. | Propagate the original mail exception | A transport diagnostic can contain recipient addresses and SMTP details. The caller can still distinguish a delivery failure from invalid input. |
| Local SMTP verification | Proposed Mailpit container through the existing Testcontainers setup, using synthetic addresses and the capture API | Embedded SMTP library or a custom SMTP server | Exercises Spring Mail over SMTP without adding a Java test library or sending external email. |

## File Structure

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/vn/tsolve/worker/client/ReviewNotificationSender.java
    │   └── resources/application.yml
    └── test/java/vn/tsolve/worker/client/
        ├── ReviewNotificationSenderTest.java
        └── ReviewNotificationSenderIT.java
.env.example
```

## External Dependencies

- [Spring Boot Mail](https://docs.spring.io/spring-boot/reference/io/email.html) provides the SMTP client and configuration.
- [Mailpit](https://mailpit.axllent.org/docs/install/docker/) provides the proposed SMTP capture fixture for tests. Testcontainers manages its lifecycle.

## Edge Cases & Risks

Dimensions analyzed are input extremes, timing, environment, error cascades, authorization, data integrity, integration, compliance and business logic. User type, scale and persisted state transitions belong to the later job and review flows.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|---|---|---|---|---|---|
| 1 | STORY-001 | Input and integrity | Multiple addresses, an address group or a header line break | High | Reject before sending any mail | SMTP delivery |
| 2 | STORY-001 | Timing and integration | SMTP refusal or timeout | High | Make one attempt and report a safe delivery failure | SMTP delivery |
| 3 | STORY-001 | Environment | SMTP properties absent | High | Web application starts without SMTP | SMTP delivery |
| 4 | STORY-001 | Input and business logic | Unicode or multiline review feedback | Medium | Preserve feedback as UTF-8 body text under the correct fixed subject | SMTP delivery |
| 5 | STORY-001 | Compliance | Transport exception contains an address, content or credentials | High | Exclude those details from the returned exception and logs | SMTP delivery |
| 6 | STORY-001 | Authorization | Caller provides an address from a different Workspace | High | The future job resolves the recipient in its own Workspace before calling the sender | Later job integration |

## Risk Review — 2026-11-06 — Verdict: CAUTION

The sender is suitable for the local slice. Live delivery depends on the later job verifying the recipient's Workspace.

| Perspective | Finding | Mitigation |
|---|---|---|
| Architecture | A synchronous SMTP call can couple review latency to delivery | Keep the sender outside the transaction and connect it only through the future worker |
| Security | Addresses and review feedback can leak through headers, errors or logs | Validate one recipient, keep dynamic text in the body and sanitize transport errors |
| Performance | An SMTP server can leave a connection waiting indefinitely | Set finite connect, read and write timeouts |
| User | An SMTP acknowledgement confirms acceptance by the server | Describe the result as accepted for delivery. Show clear subjects for both decisions |
| Simplicity | A custom mail framework would add maintenance before the queue is ready | Use one Spring Mail sender and a local capture test |

# STORY-002: tenant-scoped JobRunr jobs (KAN-102)

### Architecture Overview

One feature, tenant-jobs: capture the current workspace at enqueue; persist a small JobRunr request; resolve a registered handler at execution; check workspace eligibility and execute under a restoring tenant boundary. No HTTP endpoint. Existing application startup is unchanged unless worker integration is enabled.

`TenantJobService` exposes enqueue. `TenantExecutionService` exposes runInTenant. `WorkerTenantService` is the temporary platform integration port (currentWorkspaceId, runIn, isWorkspaceActive). Its mock lives under worker and is registered only with worker-mock. Real platform will supply an adapter later; no substitute common.tenant class is installed.

JobRunr uses explicit core components rather than the starter to avoid auto-detecting the application's tenant DataSource. A lifecycle-managed runtime owns a private Hikari pool, PostgresStorageProvider, JobRequestScheduler and optionally BackgroundJobServer. Its pool is not published as a DataSource bean, preserving Spring Boot's existing business DataSource auto-configuration. Startup failures close partially constructed resources; shutdown stops execution before closing storage and pool.

### Data Model

TenantJobRequest contains workspace UUID, handler type and record UUID; no entity, content, credentials or tenant connection. JobRunr owns tables in its dedicated PostgreSQL Jobs DB. No tenant or catalog schema changes or Liquibase edits. The mock holds allow-listed Active/Suspended UUIDs and a non-inheritable ThreadLocal binding; no actual tenant data is accessed by the mock.

### Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Scope | KAN-102 plus real JobRunr enqueue/storage/execution | wrapper only; all of KAN-161/162 | User explicitly included JobRunr and requested temporary TenantContext mock. |
| Queue | JobRunr OSS 8.6.0 core, explicit Spring beans | starter auto-storage; custom queue | Architecture selects OSS; explicit private Jobs pool prevents storage fallback and accidental business DB selection. Pin a Java-21-compatible release and verify APIs from published artifact. |
| Context adapter | worker-owned WorkerTenantService | fake production common.tenant class; wait for platform | Keep dependency replacement localized and visible; fail startup without adapter when enabled. |
| Context behavior | reject cross-workspace nesting; restore prior binding in finally | overwrite binding; inheritable thread context | Matches platform AC-002.12/13 and isolates reused threads. |
| Eligibility | check every execution attempt, fail unknown/suspended/unavailable | cache status; silently succeed; enqueue-time check only | Prevent a queued job from starting after suspension and preserve failed-job visibility. |
| Payload | typed JobRequest with only UUIDs and registered type | serialize Runnable or business content | Small persisted arguments, explicit handler resolution, no secrets in Jobs DB. |
| Activation | tsolve.worker.enabled=false by default; worker profile enables processing | always start server; dashboard | Existing web process is unaffected. Dashboard is never constructed. Non-web container packaging stays KAN-161. |
| Retry | JobRunr native retry behavior | custom retries; exactly-once claims | Failures propagate; consumers supply idempotent business handlers. |
| Test boundaries | real JobRunr in memory plus real PostgreSQL Testcontainers integration | mocked scheduler only | Exercise serialization, worker dispatch and retry lifecycle; only platform is fake. |

### File Structure

- backend/pom.xml — pinned JobRunr dependency.
- backend/src/main/java/vn/tsolve/worker/dto/TenantJobRequest.java.
- backend/src/main/java/vn/tsolve/worker/service/{WorkerTenantService,TenantJobService,TenantExecutionService,TenantJobHandler}.java.
- backend/src/main/java/vn/tsolve/worker/service/impl/ — execution, queue and request-handler implementations.
- backend/src/main/java/vn/tsolve/worker/config/ — conditional configuration, properties and JobRunr runtime.
- backend/src/main/java/vn/tsolve/worker/service/mock/ — profile-limited context/catalog mock.
- backend/src/main/resources/application-worker.yml — enable processing; no dashboard.
- backend/src/main/resources/application-worker-mock.yml — environment-backed mock allow-lists.
- backend/src/test/java/vn/tsolve/worker/ — boundary, configuration, queue and PostgreSQL execution tests.
- .env.example and backend/README-worker.md — activation, mock limits, integration contract and Jobs DB setup.

### External Dependencies

JobRunr OSS; existing Hikari, Jackson, PostgreSQL driver, JUnit, Mockito and Testcontainers. Official sources: https://www.jobrunr.io/en/documentation/background-methods/ and https://www.jobrunr.io/en/documentation/configuration/fluent/. JobRunr core avoids starter autoconfiguration; do not call the static global JobRunr configuration singleton.

### Edge Cases & Risks

Dimensions analyzed: input extremes, timing, scale, state transitions, environment, error cascades, authorization (workspace isolation), data integrity, integration, compliance, business logic. User types skipped: no user-facing endpoint or role-dependent flow.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-002 | Input extremes | Null workspace/action/record or unknown handler | High | Reject before handler/storage side effect | T1 |
| 2 | STORY-002 | Timing | A and B execute concurrently; next job reuses a thread after an exception | Critical | Each sees only its workspace, then no binding | T1 |
| 3 | STORY-002 | Authorization | Job B invoked inside scope A; same-scope nested call | Critical | Reject B without modifying A; permit A and restore | T1 |
| 4 | STORY-002 | State transitions | Workspace suspended after enqueue; handler fails before retry | Critical | Recheck on each attempt; no suspended handler call | T1 |
| 5 | STORY-002 | Error cascades | Catalog unavailable; handler throws; storage startup fails | High | Fail closed; propagate failure; clean context/resources | T1 |
| 6 | STORY-002 | Integration | JSON round trip; unknown handler after deployment | High | Preserve ids; reject unknown type, never arbitrary reflection | T1 |
| 7 | STORY-002 | Environment | Default app; worker without platform; worker-mock explicitly enabled | Critical | No worker by default; fail without adapter; mock only explicit | T1 |
| 8 | STORY-002 | Data integrity | Jobs DS config absent; business DataSource present | Critical | No fallback, fail integration startup | T1 |
| 9 | STORY-002 | Compliance | Jobs storage persists request | High | UUIDs/type only, no content or credentials | T1 |
| 10 | STORY-002 | Scale | Zero handlers or multiple handlers with same type | High | No unknown jobs accepted; reject ambiguous registration | T1 |
| 11 | STORY-002 | Business logic | Retry delivers same record more than once | High | Original ids retained; document at-least-once handler contract | T1 |

Open questions for the spec owner: none; scope and mocks approved in chat.

### Risk Review — 2026-11-07 — Verdict: CAUTION

Architect: preserve business datasource auto-configuration; keep platform integration replaceable. Security: the mock does not prove database isolation; explicitly opt in and deny unknown workspace ids. Performance: one dedicated bounded Jobs connection pool and native JobRunr retries suffice; fairness caps stay KAN-161. User: document commands and the mock limitation; never claim production tenancy works. Devil's advocate: a wrapper alone would be smaller, but the user explicitly requested real JobRunr integration; avoid a new queue or broad container work.

Agreements: tenant checks precede business handlers; keep payloads small; defaults must not start a worker; use JobRunr's own persistence and retries. Conflict: convenient datasource autodetection versus isolation — isolation wins, with an unexposed dedicated pool.

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Tenant data leakage through binding or datasource fallback | Critical | Cross-scope guard, finally restoration, dedicated private Jobs pool, concurrency/config tests | T1 |
| Mock mistaken for production tenant routing | High | Explicit worker-mock only; no default adapter; documented replacement contract | T1 |
| Failed job repeated after suspension | High | Check status each attempt; propagate native failures and retry identity | T1 |

### Verification

Builder check resolved: `tsolve.worker.enabled` activates enqueue/storage; `jobrunr.background-job-server.enabled` activates execution, defaults false and is set true by application-worker.yml. Core runtime explicitly consumes this flag; it does not rely on starter auto-configuration. Module paths include pom, profile/base YAML, environment example and worker documentation.

Focused unit/config tests, PostgreSQL-backed JobRunr serialization and dispatch integration test, then full mvn verify and spotless:check. Report infrastructure or unrelated baseline failures separately; do not weaken checks. One feature commit requires user confirmation.

## STORY-003: Active Workspaces for recurring jobs (KAN-157)

`WorkerTenantService` gains `Set<UUID> activeWorkspaceIds()`. It is abstract, so the future platform adapter must implement it from the catalog, and it binds nothing on the calling thread (AC-003.1). `MockWorkerTenantService` returns an unmodifiable copy of its configured Active allow-list, never a Suspended or unlisted UUID (AC-003.2). The only consumer is ticket-review's JOB-02 fan-out (STORY-014). No payload, runtime or storage change.

# STORY-004: Shared email outbox (KAN-162 change, 2026-11-14)

Spec: STORY-004 and the reworded AC-001.1 / AC-001.2. This replaces the per-decision `review-notification` job of ticket-review STORY-010.

## Architecture Overview

```text
 any backend feature (API or worker process)          worker process (profile worker)
 ───────────────────────────────────────────          ──────────────────────────────────────────────
 business tx commits                                   JobRunr recurring job "worker-email-outbox" (every 30 s)
   └─ AFTER_COMMIT listener composes the email           └─ EmailOutboxDispatchHandler
        └─ EmailOutboxService.queue(OutboundEmail)            1. claim due PENDING rows (SKIP LOCKED, lease)
             └─ INSERT email_outbox (PENDING) ──► Jobs DB ◄── 2. per row: Workspace Active? → SmtpEmailSender.send
                                                              3. mark SENT / back off / FAILED
                                                              4. delete rows finished > 30 days ago
```

Callers never touch JobRunr. The dispatcher never opens a Workspace database: a row holds everything needed to send it (AC-004.5). The Workspace check uses `WorkerTenantService.isWorkspaceActive`, which reads the platform catalog, not tenant data.

## Data Model

Table `email_outbox` in the Jobs database, created by its own Liquibase changelog (Key Decisions):

| Column | Type | Notes |
|---|---|---|
| `email_outbox_id` | `uuid` PK | returned by `queue` |
| `workspace_id` | `uuid not null` | captured from the bound Workspace |
| `recipient` | `varchar(320) not null` | one mailbox, already validated |
| `subject` | `varchar(255) not null` | no CR/LF |
| `body_text` | `text not null` | |
| `body_html` | `text null` | caller-escaped |
| `status` | `varchar(10) not null` | `PENDING`, `SENT`, `FAILED` (check constraint) |
| `attempt_count` | `int not null default 0` | attempts started |
| `next_attempt_at` | `timestamptz not null` | creation time on insert; lease or backoff afterwards |
| `claimed_at` | `timestamptz null` | set while an attempt has no outcome yet (see "Dispatch run", step 2) |
| `created_at` | `timestamptz not null` | |
| `finished_at` | `timestamptz null` | send time for SENT, give-up time for FAILED |

Indexes: partial `(next_attempt_at, created_at) where status = 'PENDING'` for the claim; `(finished_at) where status <> 'PENDING'` for retention.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| Where the email lives | `email_outbox` in the Jobs database (user, 2026-11-14) | content in the JobRunr payload; an outbox per tenant database | User wants one shared table any feature can write to; JobRunr payloads stay UUID-only (NFR-5). |
| How sending starts | Callers only insert; a JobRunr recurring job polls (user) | insert + one JobRunr job per email | New emails need no JobRunr knowledge; no row is stranded by a failed enqueue. Costs up to one interval of delay. |
| Schema management | Second Liquibase changelog `db/jobs-changelog/db.changelog-master.yaml`, run by a `SpringLiquibase` bean on the Jobs pool whenever `tsolve.worker.enabled` (user) | `CREATE TABLE IF NOT EXISTS` script at startup | Keeps the team's migration tooling and history; Liquibase's lock serialises API and worker starting together. The tenant changelog stays unchanged. |
| Recurring job | `scheduleRecurrently("worker-email-outbox", Duration.ofSeconds(interval), new EmailOutboxDispatchRequest())`, registered on `ApplicationReadyEvent`, like ticket-review's `ReviewExpiryRegistration` | Spring `@Scheduled` | One recurring definition shared by every worker instance, visible in JobRunr storage; the claim below still makes concurrent runs safe. |
| Claim | One short transaction: `select … where status = 'PENDING' and next_attempt_at <= now() order by created_at limit :batch for update skip locked`, then `attempt_count = attempt_count + 1, next_attempt_at = now() + lease`; commit before any SMTP call | hold the row lock while sending | No database lock is held across SMTP waits. A dispatcher that dies mid-send leaves a lease that expires, so the row is retried (at least once, as the spec says). |
| Retry schedule | Attempt 1 when due; after failure 1 wait 5 min, after failure 2 wait 15 min; failure 3 → FAILED. Also FAILED on any failure later than `created_at + 60 min` | exponential with jitter | Three attempts end about 20 minutes after creation, well inside the hour (AC-004.7). Values in `tsolve.worker.email.backoff` (`5m,15m`). |
| Suspended Workspace | Checked before the attempt is claimed: no SMTP, no `attempt_count` change, `next_attempt_at` moved by one interval; FAILED once `now() > created_at + 60 min` (user) | fail at once; send anyway | GBR-38, with the same one-hour bound as delivery failures (AC-004.9). Eligibility lookup errors are treated the same way. |
| Retention | Each run deletes up to `batch-size` rows with `finished_at < now() - retention` (default `P30D`) | a separate daily job | Keeps deletes small and needs no second schedule (AC-004.10). |
| Database access | `JdbcClient` over the Jobs pool, exposed by `JobRunrRuntime.jobsDataSource()`; not a Spring `DataSource` bean | JPA entity | The pool must not become a candidate business `DataSource` (AC-002.13); JPA is bound to the business database. |
| Message model | `OutboundEmail(recipient, subject, bodyText, bodyHtml)` record in `worker/dto`; shared validation in `EmailAddressRules` | separate request types per feature | One shape for queue and send; AC-001.3 and AC-004.2 use the same checks. |
| SMTP sender | `ReviewNotificationSender` becomes `SmtpEmailSender.send(OutboundEmail)`; `MimeMessageHelper(multipart = bodyHtml != null, UTF-8)` with `setText(text, html)` | keep `SimpleMailMessage` for text | `SimpleMailMessage` cannot carry HTML; one helper path builds both forms (AC-001.1, AC-001.2). |
| Sender missing | If the worker has no `SmtpEmailSender` (no `spring.mail.host`), the dispatch run fails before claiming any row | claim and fail rows | Rows must not burn attempts because of local configuration; JobRunr shows the failed run. |

Decided in planning: batch size 20 (`tsolve.worker.email.batch-size`); lease 5 minutes (`lease`), longer than one batch of SMTP timeouts (20 × 13 s).

### Dispatch run, step by step (builder check, 2026-11-14)

All times come from an injected `java.time.Clock` (bean `workerClock`, `Clock.systemUTC()` by default) and are passed to SQL as parameters, never `now()`, so tests move time without waiting. One run of `EmailOutboxDispatchHandler`:

1. **Sender check** — no `SmtpEmailSender` bean → throw before touching rows (edge case 7).
2. **Lease marker** — the table has one more column, `claimed_at timestamptz null`: set when an attempt is claimed, cleared when its outcome is written (SENT, FAILED or a backoff). A due row with `claimed_at` set is therefore an attempt whose lease expired with no outcome; a due row with `claimed_at` null is waiting for its first attempt or for a scheduled retry. (Corrected 2026-11-14: the first version of step 3 applied the 60-minute rule to every previously attempted row, which contradicted step 4.)
3. **Claim** — in one transaction `select … for update skip locked` the due rows (`status = 'PENDING' and next_attempt_at <= :now`, `order by created_at`, `limit :batch`). For each locked row, in this order:
   - `claimed_at` set (lease expired — a failed attempt, AC-004.7): if `attempt_count >= 3` or `:now > created_at + 60 min` → `FAILED`, `finished_at = :now`, `claimed_at = null`. Not sent. Otherwise fall through to the next two checks, so the retry happens in this run with no extra backoff.
   - Workspace not Active, or `isWorkspaceActive` throws → if `:now > created_at + 60 min` then `FAILED` (`finished_at = :now`), else `next_attempt_at = :now + interval`; `attempt_count` unchanged, `claimed_at = null`. Not sent. A throw affects only that row; the batch continues (AC-004.9).
   - Otherwise → `attempt_count + 1`, `claimed_at = :now`, `next_attempt_at = :now + lease`, kept for sending. A row with `claimed_at` null is claimed whatever its age: the 60-minute rule applies only when a failure happens.
   Commit. A row that is not claimed by this run (locked by another dispatcher) is skipped (AC-004.8).
4. **Send** each kept row, outside any transaction, then update it with a guard `where email_outbox_id = :id and attempt_count = :claimedCount and status = 'PENDING'`, always clearing `claimed_at`:
   - accepted → `SENT`, `finished_at = :sentAt`.
   - `DeliveryException` → if `attempt_count = 3` or `:now > created_at + 60 min` → `FAILED`; else `next_attempt_at = :now + backoff[attempt_count - 1]` (5 min after attempt 1, 15 min after attempt 2). The 60-minute check uses the failure time, as the spec says; a retry scheduled before the hour still runs if it comes due after it.
5. **Retention** — `delete … where email_outbox_id in (select … where status <> 'PENDING' and finished_at < :now - retention limit :batch)`. Larger backlogs drain over later runs; the AC-004.10 test uses fewer than 20 rows.

A row that was never attempted because the dispatcher was down is attempted once when it comes due, whatever its age; only a failure after the hour marks it FAILED.

### Contracts (builder check)

- `EmailOutboxService.queue(OutboundEmail) → UUID`. Throws `InvalidOutboundEmailException` (fixed message "Invalid outbound email", AC-004.2, also used by `SmtpEmailSender` for AC-001.3), `EmailOutboxUnavailableException` (fixed message "Email outbox unavailable", AC-004.3: no bound Workspace counts as invalid input; a database error maps here). Both extend `EmailOutboxException`. No cause is attached when the cause text can carry content.
- "Not configured" (AC-004.3) = no `EmailOutboxService` bean: `EmailOutboxConfiguration` is `@ConditionalOnProperty(tsolve.worker.enabled=true)`, like `WorkerConfiguration`, and callers take it through `ObjectProvider` (ticket-review AC-010.3).
- The Workspace is read from `WorkerTenantService.currentWorkspaceId()`. Queueing does not check eligibility; a Suspended Workspace's row is held by the dispatcher (AC-004.9).
- After the merge of KAN-333 (PR #230): `SmtpEmailSender` sends from `T-Solve <TSOLVE_SMTP_FROM>` and replaces a line break in the subject with a space instead of rejecting it (AC-001.2); the outbox still rejects such a subject at queue time (AC-004.2). `ReviewEmail`, `ReviewEmailRenderer` and the template stay in `worker/client` and are called by ticket-review code.
- Mailbox rule: `InternetAddress.parse(value, true)`, exactly one non-group address, `validate()`, stored as `getAddress()` (display name dropped), at most 320 characters. Subject: not blank, no `\r` or `\n`, at most 255 characters. Text body: not blank. Over-long values are invalid input.
- Properties `tsolve.worker.email.{interval=30s, backoff=5m,15m, lease=5m, batch-size=20, retention=30d}` in `EmailOutboxProperties`.
- `EmailOutboxRegistration` registers the recurring job only when the background job server runs (`JobRunrRuntime.isAutoStartup()`, i.e. the `worker` profile); `EmailOutboxService` exists in the API too.
- Test fixtures: `ReviewNotificationSenderIT` already runs Mailpit and `JobRunrTenantIT` PostgreSQL through Testcontainers; `EmailOutboxIT` reuses both.

## File Structure

```text
backend/src/main/java/vn/tsolve/worker/
├── client/SmtpEmailSender.java                 (replaces ReviewNotificationSender)
├── config/JobRunrRuntime.java                  (+ jobsDataSource())
├── config/EmailOutboxConfiguration.java        (SpringLiquibase on the Jobs pool, outbox beans)
├── config/EmailOutboxProperties.java           (interval, backoff, lease, batch-size, retention)
├── dto/OutboundEmail.java
├── dto/EmailOutboxDispatchRequest.java
├── repository/EmailOutboxRepository.java       (JdbcClient: insert, claim, mark, delete)
├── service/EmailOutboxService.java
├── service/impl/EmailOutboxServiceImpl.java
├── service/impl/EmailOutboxDispatchHandler.java
├── service/impl/EmailOutboxRegistration.java
└── util/EmailAddressRules.java
backend/src/main/resources/db/jobs-changelog/db.changelog-master.yaml (+ 001-email-outbox.yaml)
backend/src/test/java/vn/tsolve/worker/
├── client/SmtpEmailSenderTest.java, SmtpEmailSenderIT.java   (renamed from ReviewNotificationSender*)
├── service/impl/EmailOutboxServiceImplTest.java
├── service/impl/EmailOutboxDispatchHandlerTest.java
└── EmailOutboxIT.java                          (PostgreSQL Jobs DB + Mailpit)
```

`module.md` paths gain `backend/src/main/resources/db/jobs-changelog/**`.

## External Dependencies

No new library: Spring Boot Mail (`MimeMessageHelper`), Liquibase and JobRunr are already on the classpath; Mailpit and PostgreSQL Testcontainers are the existing fixtures.

## Edge Cases & Risks

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test |
|---|---|---|---|---|---|---|
| 1 | STORY-004 | Concurrency | Two dispatchers run the same tick | Critical | `SKIP LOCKED` + lease: each due row is attempted once per attempt (AC-004.8) | `EmailOutboxIT`, two threads |
| 2 | STORY-004 | Error cascade | Worker stops after SMTP accepted, before SENT | High | Lease expires; counted as a failed attempt; retried (a duplicate is accepted, spec Out of Scope) or FAILED after the third | `EmailOutboxDispatchHandlerTest` (no duplicate-delivery assertion) |
| 3 | STORY-004 | Timing | Dispatcher down for over an hour | High | A row never attempted is attempted once; on failure it is FAILED (past 60 min) | `EmailOutboxDispatchHandlerTest` |
| 4 | STORY-004 | Tenancy | Row of a Suspended or unknown Workspace | High | Not sent, no attempt counted; FAILED after 60 min (AC-004.9) | `EmailOutboxIT` with the mock allow-list |
| 5 | STORY-004 | Input | Recipient list or group, CR/LF in subject, blank text | High | Rejected before insert (AC-004.2) | `EmailOutboxServiceImplTest` |
| 6 | STORY-004 | Environment | API runs without `tsolve.worker.enabled` | High | No `EmailOutboxService` bean; callers handle its absence (ticket-review AC-010.3) | `WorkerConfigurationTest` |
| 7 | STORY-004 | Environment | Worker without SMTP host | Medium | Dispatch run fails before claiming; rows untouched | `EmailOutboxDispatchHandlerTest` |
| 8 | STORY-004 | Compliance | Errors or logs carry addresses or bodies | High | Fixed messages and counts only (AC-004.11) | log-capture assertions |
| 9 | STORY-004 | Integrity | API and worker run the Jobs changelog at the same time | Medium | Liquibase lock serialises; the second run applies nothing | `EmailOutboxIT` startup |
| 10 | STORY-001 | Input | HTML body with Unicode | Medium | multipart/alternative, both parts UTF-8 and unchanged (AC-001.2) | `SmtpEmailSenderIT` |
| 11 | STORY-004 | Scale | Large backlog | Low | 20 rows per run, oldest first; the rest wait for the next tick | `EmailOutboxIT` |
