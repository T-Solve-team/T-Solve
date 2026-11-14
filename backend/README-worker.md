# Worker jobs — KAN-102

JobRunr OSS stores requests in a **separate PostgreSQL Jobs database**. The worker captures the bound workspace UUID when enqueueing, checks that workspace is Active on every execution attempt, then calls the registered handler under `TenantExecutionService.runInTenant`. Cross-workspace nesting is rejected and the previous binding is restored after success or failure.

## Run locally

1. Prepare a separate Jobs database and a user allowed to create tables there. Set `JOBS_DB_URL` (a PostgreSQL JDBC URL), `JOBS_DB_USER` and `JOBS_DB_PASSWORD` in the existing git-ignored `.env` alongside the backend's other required settings. Do not point these at a tenant or catalog database. JobRunr manages its job tables; a separate Liquibase changelog manages `email_outbox` in the same Jobs database. The tenant changelog stays separate.
2. Until platform supplies its adapter, put workspace UUIDs in `TSOLVE_MOCK_ACTIVE_WORKSPACES` and optionally `TSOLVE_MOCK_SUSPENDED_WORKSPACES` (comma-separated). An empty allow-list permits no workspace. Overlapping or malformed entries fail startup.
3. From `backend/`, start the existing application with `mvn spring-boot:run "-Dspring-boot.run.profiles=worker,worker-mock"`. The worker profile enables JobRunr processing. The dashboard is never started. Dedicated non-web container deployment is KAN-161; the existing web configuration is retained here.

Without the worker profile, processing is disabled. `TSOLVE_WORKER_ENABLED=true` enables the enqueue/storage integration for a web process; it does not enable processing. That process also needs either a real platform adapter or the explicitly selected `worker-mock` profile. Missing Jobs DB configuration is a startup error when integration is enabled; there is no fallback to Spring's business DataSource. The private Jobs pool is not registered as a DataSource bean, so it cannot replace the business pool.

## Mock boundary and platform replacement

`worker-mock` registers `MockWorkerTenantService`, a temporary non-inheritable ThreadLocal context and configured workspace status allow-list. **It does not route business queries to tenant databases or prove database isolation.** Do not use this mock as production tenancy. With integration enabled and no adapter, startup fails.

Platform will provide a `WorkerTenantService` bean backed by its real TenantContext and catalog. Its methods must:

- Return the current workspace via `currentWorkspaceId()`.
- Return true only for an existing Active workspace via `isWorkspaceActive(UUID)`; lookup failures must throw.
- Bind within `runIn(UUID, Runnable)`, reject a different already-bound workspace, and restore the previous binding in `finally`.

Disable `worker-mock` when installing that adapter. No fake class is added to `common.tenant`.

## Enqueue and handle

Consuming modules provide a `TenantJobHandler` bean with a unique nonblank `handlerType()` and `execute(UUID recordId)`. They inject `TenantJobService` and call `enqueue(handlerType, recordId)` from an already-bound workspace scope. The returned UUID is JobRunr's job id. There is no API to choose a workspace during enqueue and no REST endpoint in this feature.

The persisted request contains only workspace UUID, registered handler type and record UUID. JobRunr resolves one fixed request-handler bean; our dispatcher accepts only registered handler types. Persist no ticket content, email bodies, tokens, database connection settings or executable actions in requests. JobRunr storage itself is trusted infrastructure; restrict access to its database credentials.

Unknown/Suspended workspace, catalog failure, unknown handler or malformed ids fail execution before business code. JobRunr records the failure and applies its native retries; every attempt checks status again. A workspace suspended after enqueue cannot begin a new handler execution. A suspension during an already-running handler does not cancel that handler. There is no custom replay or retry loop.

Handlers must be idempotent: delivery is at least once. OSS enqueue is not atomic with an application's business transaction. Per-workspace concurrency caps and production container rollout remain separate work.

## Verify

From `backend/`: `mvn test "-Dtest=TenantExecutionServiceImplTest,TenantJobServiceImplTest,WorkerConfigurationTest"`, then `mvn verify` with Docker running, then `mvn spotless:check`. `JobRunrTenantIT` starts an isolated PostgreSQL container, persists jobs in enqueue-only mode, restarts the Spring context in worker mode and checks actual execution and native retry with the original workspace payload. It also checks that JobRunr creates no tables in the business database.

Official integration references: [JobRequest and handlers](https://www.jobrunr.io/en/documentation/background-methods/), [programmatic configuration](https://www.jobrunr.io/en/documentation/configuration/fluent/). This implementation pins JobRunr 8.6.0 and verifies its API using the corresponding official Maven source artifact.

## Shared email outbox — KAN-162

The queue is available in both API and worker processes when `tsolve.worker.enabled=true`.
Inject `ObjectProvider<EmailOutboxService>` into a consuming module's after-commit listener.
While its Workspace is still bound, build an `OutboundEmail` and call `queue`:

```java
EmailOutboxService outbox = outboxes.getIfAvailable();
if (outbox != null) {
    UUID emailId = outbox.queue(new OutboundEmail(recipient, subject, bodyText, bodyHtml));
}
```

Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` to invoke this after the
business transaction commits. If the listener runs without a bound Workspace, capture the Workspace
UUID before commit and restore it through `WorkerTenantService.runIn(workspaceId, action)` around
the call. Queueing writes independently to the Jobs database; a queue failure cannot roll back an
already committed business action. Callers decide how to handle an absent bean or an
`EmailOutboxException`. Avoid logging the message or transport/database exceptions with mail data.

Review Reject and Request Changes decisions use `ReviewNotificationEnqueuer` after commit. In the
decision's Workspace it loads the committed notification, renders the subject, plain text and HTML
with `ReviewEmailRenderer`, and queues one `OutboundEmail` per writer. Approve and missing writer
accounts queue nothing. Each decision is isolated: a lookup, rendering or queue failure logs only
counts and the exception class, and the remaining decisions continue. A missing outbox bean skips
queueing. Delivery and retry use this shared outbox; no review-specific job handler remains.
Existing review notification jobs in JobRunr storage will fail as unknown handlers; this is accepted
for the demo data. `ReviewNotificationIT` covers commit/rollback, stored content and SMTP retry
without changing the committed decision.

The recipient must be exactly one mailbox (a display name is dropped), at most 320 characters.
The subject must be nonblank, at most 255 characters, without CR/LF; plain text must be nonblank.
HTML is optional and must be escaped by the caller. Invalid input or no bound Workspace raises
`InvalidOutboundEmailException` with `Invalid outbound email`. Storage failures raise
`EmailOutboxUnavailableException` with `Email outbox unavailable`, without the original cause.
The queue does not check Workspace eligibility or open a tenant database.

Outbox settings use `TSOLVE_WORKER_EMAIL_INTERVAL` (30s), `TSOLVE_WORKER_EMAIL_BACKOFF` (5m,15m),
`TSOLVE_WORKER_EMAIL_LEASE` (5m), `TSOLVE_WORKER_EMAIL_BATCH_SIZE` (20), and
`TSOLVE_WORKER_EMAIL_RETENTION` (30d).

The recurring JobRunr job `worker-email-outbox` is registered on application readiness only when
background processing is enabled. Each tick locks up to the batch limit of due PENDING rows,
oldest first, using `FOR UPDATE SKIP LOCKED`. Active Workspace rows gain an attempt and a lease
(`claimed_at`, `next_attempt_at`); the transaction commits before SMTP. Outcomes are guarded by
the claimed attempt count so a stale sender cannot overwrite a later attempt.

SMTP acceptance marks SENT with the send time. Failure one retries after 5 minutes, failure two
after 15 minutes, and failure three marks FAILED. Any failure more than 60 minutes after creation
also marks FAILED. A scheduled retry or first attempt still runs when due past the hour. An expired
lease with `claimed_at` still set counts as a failed attempt: it retries immediately in that tick
within the limits, otherwise becomes FAILED. Outcomes always clear `claimed_at`.

Suspended, unknown, or unreadable Workspaces are held for one interval without consuming an attempt;
after the hour they become FAILED. Each tick deletes up to the batch limit of SENT/FAILED rows
finished strictly longer ago than retention. Missing SMTP configuration fails the run before any
rows change. Configure `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `TSOLVE_SMTP_FROM` and the existing
SMTP authentication/TLS environment settings for the worker. Finite SMTP timeouts bound send waits;
keep the lease longer than one batch of these waits (defaults: 20 messages, 5-minute lease).

Delivery is at least once: SMTP may accept a message before the worker records SENT; a crash there
can cause a duplicate after lease expiry. No tenant database is opened to send a stored message.

Run worker unit tests with `mvn -q -Dtest='vn.tsolve.worker.**.*Test' test`.
With Docker available, `EmailOutboxIT` verifies PostgreSQL queueing, concurrent claims, guarded
outcomes, retention, Mailpit delivery, and retries to FAILED against a refusing SMTP port;
`SmtpEmailSenderIT` verifies plain text and Unicode multipart contents.
