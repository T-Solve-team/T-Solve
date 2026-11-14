---
module: worker
date: 2026-11-07
owner: BA
---

# Spec: worker

## Changelog

| Date | Story | Change | Reason |
|---|---|---|---|
| 2026-11-14 | STORY-004, STORY-001, NFR-5 | Add a shared email outbox in the Jobs database with a recurring dispatcher (3 attempts within 1 hour, 30-day retention, plain text with optional HTML); the SMTP sender takes a ready-made message instead of a review decision; NFR-5 allows the outbox to hold recipient and content (KAN-162) | User wants any backend feature to send email by storing a ready-made row, with no business-database lookup at send time; user chose the Jobs database, insert-only callers with a polling dispatcher, 3 attempts in 1 hour, 30-day retention, optional HTML, and holding rows of a Suspended Workspace PENDING within the hour; builder check: a lease-expired attempt counts as a failed one (user) |
| 2026-11-08 | STORY-003 | The tenant port lists the Active Workspaces, for JOB-02's per-Workspace fan-out (KAN-157) | ticket-review STORY-014; user chose a worker change request over claiming the port in ticket-review |
| 2026-11-06 | STORY-001 | Define the SMTP delivery slice of KAN-162 | Verify review email delivery before the job queue is available |
| 2026-11-07 | STORY-002 | Add the KAN-102 contract including JobRunr integration and temporary TenantContext mock | User requested KAN-102, explicitly added JobRunr and requested mocked TenantContext |
| 2026-11-07 | STORY-002 | Renumber KAN-102 from STORY-001 / AC-001.x to STORY-002 / AC-002.x when merging main | KAN-162 merged first with STORY-001; ids must stay unique within the module |

## Requirements

### Functional

1. Send one ready-made email (recipient, subject, plain-text body, optional HTML body) through SMTP.
2. Report delivery failure to the caller so the outbox dispatcher can retry it.
3. Enqueue workspace-scoped jobs through JobRunr OSS and execute their handlers within runInTenant.
4. Capture the workspace at enqueue time; check its current eligibility before each execution.
5. Provide an explicit development mock for the missing platform TenantContext and workspace catalog.
6. Provide one shared email outbox in the Jobs database: any backend feature queues a ready-made email by storing one row, and a recurring dispatcher sends due rows, retries failures and deletes old rows.

### Non-Functional

1. Attempt one SMTP send per call. Bound connection, read and write waits with finite SMTP timeouts.
2. Keep recipient addresses, message contents and SMTP credentials out of logs.
3. Verify delivery against a local SMTP test server using synthetic recipients.
4. No job may execute under another workspace, including nested calls, retries and reused threads.
5. Store only workspace UUID, handler type and record UUID in the job payload; never serialize credentials, business content or executable closures as payload data. The email outbox table (STORY-004) is the one place in the Jobs database that holds a recipient address and message content, for at most the retention period after the row is finished.
6. Use the existing Java 21 / Spring Boot 3 stack, JobRunr OSS and a separate PostgreSQL Jobs database.

## User Stories

### STORY-001: Send a ready-made email through SMTP (KAN-162)

- As a backend feature, I want to hand the SMTP sender a ready-made message so that any email reaches its recipient the same way.
- Acceptance:
  - AC-001.1: WHEN the caller submits a message with a recipient, a subject and a plain-text body and no HTML body, THE SMTP sender SHALL send one plain-text email from the configured sender address to that recipient with that subject and body.
  - AC-001.2: WHEN the caller submits a message that also has an HTML body, THE SMTP sender SHALL send one multipart/alternative email that contains both the plain-text body and the HTML body, unchanged.
  - AC-001.3: IF the supplied recipient does not represent exactly one mailbox, THEN THE SMTP sender SHALL reject the request before attempting delivery.
  - AC-001.4: IF the SMTP server refuses delivery or the send times out, THEN THE SMTP sender SHALL report a delivery failure to the caller after one attempt, with no recipient address, message content or credentials in the reported error.
  - AC-001.5: THE SMTP sender SHALL configure finite SMTP connection, read and write timeouts.
  - AC-001.6: THE SMTP sender SHALL omit recipient addresses, message contents and SMTP credentials from its logs.
  - AC-001.7: WHILE SMTP settings are absent, THE web application SHALL start without an SMTP connection.

### STORY-002: Execute queued jobs in the right TenantContext (KAN-102)

- As the worker, I want each job bound to its captured workspace so that background processing cannot cross company boundaries (GBR-37, GBR-38).
- Acceptance:
  - AC-002.1: WHEN a caller enqueues a job while a workspace is bound, THE queue SHALL persist that workspace UUID together with the registered handler type and record UUID through JobRunr.
  - AC-002.2: IF enqueue is called with no bound workspace, null record id or an unregistered handler type, THEN THE queue SHALL reject the request without persisting a job.
  - AC-002.3: WHEN JobRunr executes a job for an Active workspace, THE worker SHALL invoke its registered handler inside runInTenant with exactly the workspace UUID stored in that job.
  - AC-002.4: IF the workspace is unknown or Suspended at execution time, THEN THE worker SHALL invoke no business handler and SHALL report a failed execution to JobRunr without binding a workspace (GBR-38).
  - AC-002.5: IF workspace eligibility cannot be checked, THEN THE worker SHALL fail the execution without invoking the handler.
  - AC-002.6: WHEN an action finishes normally or throws, THE runInTenant boundary SHALL restore the calling thread's previous workspace binding and propagate the original action failure.
  - AC-002.7: IF a different workspace is already bound on the executing thread, THEN THE boundary SHALL reject the execution without invoking the action or changing that binding.
  - AC-002.8: WHEN the same workspace is already bound, THE boundary SHALL permit nested execution and preserve the outer binding afterwards.
  - AC-002.9: WHEN jobs for different workspaces run concurrently or sequentially on a reused thread, THE worker SHALL keep their context bindings isolated.
  - AC-002.10: WHEN a handler fails, THE worker SHALL let JobRunr retain and retry the failed job with its original workspace UUID and SHALL recheck workspace eligibility on every attempt.
  - AC-002.11: WHILE the explicit worker-mock profile is active, THE backend SHALL provide a temporary thread-scoped TenantContext mock and a configured allow-list of Active and Suspended workspace UUIDs, treating every unlisted UUID as unknown.
  - AC-002.12: WHILE worker-mock is inactive, THE backend SHALL register no mock platform adapter; IF job processing is enabled without a real adapter, THEN THE backend SHALL fail startup rather than permit jobs using mocked tenancy.
  - AC-002.13: WHEN JobRunr integration is enabled, THE backend SHALL use a dedicated Jobs DataSource configured by environment variables and SHALL never fall back to its business or catalog DataSource.
  - AC-002.14: THE backend SHALL disable job execution and the JobRunr dashboard by default; WHEN the worker profile is enabled with required configuration, THE backend SHALL enable background processing while keeping the dashboard disabled.
  - AC-002.15: IF a persisted job names an unknown handler or has a null workspace or record UUID, THEN THE worker SHALL fail it without invoking a handler.
  - AC-002.16: THE test suite SHALL demonstrate enqueue, persisted payload serialization and execution through real JobRunr components, including PostgreSQL-backed job storage, with only platform dependencies mocked.

### STORY-003: List the Active Workspaces for recurring jobs (KAN-157)

- As a recurring job such as JOB-02, I want the Workspaces that are Active right now so that I run once in each of them and in no Suspended one (GBR-37, GBR-38).
- Acceptance:
  - AC-003.1: WHEN a caller asks the worker tenant port for the Active Workspaces, THE port SHALL return the UUIDs of the Workspaces that are Active at that moment, without binding a Workspace on the calling thread.
  - AC-003.2: WHILE the worker-mock profile is active, THE mock SHALL return exactly its configured Active allow-list and no Suspended or unlisted Workspace.

### STORY-004: Queue any email in a shared outbox (KAN-162)

- As a backend developer, I want to send an email by storing a ready-made row so that a new kind of email needs no new job, handler or business-database lookup at send time.
- Acceptance:
  - AC-004.1: WHEN a caller queues an email while a Workspace is bound, THE email outbox SHALL store one PENDING row in the Jobs database with that Workspace UUID, the recipient, the subject, the plain-text body, the optional HTML body, an attempt count of zero and the creation time, and SHALL return the row's UUID.
  - AC-004.2: IF a queue call has no bound Workspace, a recipient that is not exactly one mailbox, a blank subject or one containing a line break, or a blank plain-text body, THEN THE email outbox SHALL reject it without storing a row.
  - AC-004.3: IF the Jobs database is not configured or cannot store the row, THEN THE email outbox SHALL report the failure to the caller without storing a partial row.
  - AC-004.4: WHILE job processing is enabled, THE outbox dispatcher SHALL run at a configured interval (default 30 seconds) and attempt every PENDING row whose next attempt time has come, oldest first.
  - AC-004.5: WHEN the dispatcher attempts a row, THE dispatcher SHALL send it through the SMTP sender using only the data stored in that row, without reading any Workspace database.
  - AC-004.6: WHEN the SMTP sender accepts a row's message, THE dispatcher SHALL mark the row SENT with the send time.
  - AC-004.7: IF delivery of a row fails, THEN THE dispatcher SHALL increase its attempt count and set its next attempt time after the configured backoff. IF that was the third attempt, or the failure happened more than 60 minutes after the row was created, THEN THE dispatcher SHALL mark the row FAILED and attempt it no more. IF a claimed attempt reports no outcome before its lease expires, THEN THE dispatcher SHALL count it as a failed attempt under the same rules.
  - AC-004.8: WHILE more than one dispatcher runs at the same time, THE dispatcher SHALL let only one of them claim a due row for a given attempt.
  - AC-004.9: IF a row's Workspace is Suspended or unknown when the row is due, or its eligibility cannot be checked, THEN THE dispatcher SHALL leave the row PENDING without attempting it or increasing its attempt count (GBR-38). IF such a row is still unattempted 60 minutes after it was created, THEN THE dispatcher SHALL mark it FAILED.
  - AC-004.10: WHEN a SENT or FAILED row has been finished for longer than the configured retention (default 30 days), THE dispatcher SHALL delete the row.
  - AC-004.11: THE email outbox and dispatcher SHALL omit recipient addresses, subjects and bodies from logs and from the errors they report.
  - AC-004.12: THE test suite SHALL demonstrate queueing, dispatch, retry to FAILED, retention and a concurrent claim against PostgreSQL-backed Jobs storage and a local SMTP server.

## Out of Scope

- STORY-001 / STORY-004: retry and delivery are STORY-004. Callers own the content: the module that queues an email composes its recipient, subject and bodies from its own data and escapes user text in the HTML body; the worker does not template or check that content. KAN-155 (ticket-review STORY-010) owns the review email and verifies that email failure cannot roll back the decision. First Admin invitation, production SMTP provider setup, attachments, several recipients, CC/BCC and a screen for outbox rows are outside this slice.
- STORY-004: queueing is not in the business transaction, because the Jobs database is separate; a row lost between the business commit and the insert is not recovered. Delivery is at least once: if the worker stops after SMTP accepts a message and before the row is marked SENT, the message can be sent again.
- STORY-002: real platform TenantContext, catalog persistence, tenant database routing and row guards (KAN-99 / platform). The mock proves context propagation only, not physical database isolation.
- STORY-002: production container provisioning, dashboard rollout, per-Workspace recurring job registration on provisioning and per-workspace concurrency caps (KAN-161). JOB-02 itself (one recurring job fanning out per Active Workspace) is ticket-review STORY-014.
- STORY-002: first-Admin invitation email (a later caller of STORY-004).
- REST endpoints, frontend changes, changes to tenant business tables and a new custom queue implementation.
- Exactly-once delivery: handlers must tolerate JobRunr retries; this module does not add a distributed transaction between business writes and job enqueue.

## Interface Contracts

### Exposed by this module

- SMTP delivery (STORY-001): an internal Java delivery operation accepts one recipient, a subject, a plain-text body and an optional HTML body. It reports success when the SMTP server accepts the message and reports delivery failure to the caller when the send fails. Only the outbox dispatcher calls it.
- Email outbox (STORY-004): an internal Java operation that queues one ready-made email (recipient, subject, plain-text body, optional HTML body) for the bound Workspace and returns the row UUID, or reports a rejection or a storage failure. Available in every process where job queueing is enabled; it is the only way other modules send email.
- `TenantJobService.enqueue(String handlerType, UUID recordId)` (STORY-002): returns the JobRunr job id; captures the bound workspace rather than accepting a caller-supplied workspace.
- `TenantExecutionService.runInTenant(UUID workspaceId, Runnable action)` (STORY-002): checked workspace scope; no transaction is held across workspace selection.
- `TenantJobHandler` (STORY-002): registered handler type plus execution of a record UUID under the job workspace; production business handlers are supplied by consuming modules.
- `WorkerTenantService.activeWorkspaceIds()` (STORY-003): the UUIDs of the Active Workspaces at the time of the call; the platform adapter implements it from the catalog later.
- JobRunr execution entry point (STORY-002): workspace UUID, handler type and record UUID only; no HTTP surface.

### Required from other modules

- Callers of the email outbox supply a ready-made email composed from their own committed data in their Workspace, and queue it after their transaction commits. Local tests use synthetic recipients and a local SMTP endpoint.
- Platform adapter contract: current workspace lookup, scoped binding with restoration, and workspace eligibility lookup. Until platform exists, a worker-owned mock adapter is enabled only by `worker-mock`; do not install a pretend production class in `common.tenant`.
- Workspace and record ids are Java UUIDs, following backend/AGENTS.md rather than the reference SQL's BIGINT ids.
- JobRunr owns its dedicated Jobs database tables; the worker owns the email outbox table there; tenant Liquibase changelogs remain unchanged.
- Reference: architecture.md, Background Jobs; platform/spec.md AC-002.1, AC-002.12 through AC-002.14.

## Definition of Done

- [ ] Every acceptance criterion has a test whose name cites its AC id.
- [ ] Plain-text and multipart emails, and outbox dispatch, retry and retention, are verified with the local SMTP server and PostgreSQL Jobs storage.
- [ ] Focused scope, concurrency, retry and JobRunr storage/execution tests pass.
- [ ] `mvn spotless:apply`, `mvn verify` and `mvn spotless:check` pass; unavailable infrastructure is reported explicitly.
- [ ] `bun harnessclaude/hooks/sdd.ts check worker` and `trace worker STORY-001` / `STORY-002` / `STORY-004` pass.
- [ ] Mock profile and Jobs DB environment variables are documented without credentials.
- [ ] User reviews code and verification evidence before the feature commit.
