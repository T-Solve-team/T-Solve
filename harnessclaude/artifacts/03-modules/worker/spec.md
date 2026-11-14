---
module: worker
date: 2026-11-07
owner: BA
---

# Spec: worker

## Changelog

| Date | Story | Change | Reason |
|---|---|---|---|
| 2026-11-14 | STORY-001 | The SMTP sender becomes generic: it sends a submitted recipient, subject, plain-text and HTML body as one multipart email from T-Solve; review content is rendered before it (AC-001.1, AC-001.2 reworded) (KAN-333) | Requested by the KAN-162 email-outbox session so the outbox can reuse the sender unchanged; user agreed |
| 2026-11-14 | STORY-001 | The SMTP sender sends a multipart HTML + plain-text email from the display name T-Solve (AC-001.1, AC-001.2 reworded) (KAN-333) | ticket-review STORY-010 change: the user asked for a professional HTML email; the sender in worker/client must follow it |
| 2026-11-08 | STORY-003 | The tenant port lists the Active Workspaces, for JOB-02's per-Workspace fan-out (KAN-157) | ticket-review STORY-014; user chose a worker change request over claiming the port in ticket-review |
| 2026-11-06 | STORY-001 | Define the SMTP delivery slice of KAN-162 | Verify review email delivery before the job queue is available |
| 2026-11-07 | STORY-002 | Add the KAN-102 contract including JobRunr integration and temporary TenantContext mock | User requested KAN-102, explicitly added JobRunr and requested mocked TenantContext |
| 2026-11-07 | STORY-002 | Renumber KAN-102 from STORY-001 / AC-001.x to STORY-002 / AC-002.x when merging main | KAN-162 merged first with STORY-001; ids must stay unique within the module |

## Requirements

### Functional

1. Send one plain-text review notification to the ticket writer for a Reject or Request Changes decision.
2. Report delivery failure to the caller so the future worker job can handle it.
3. Enqueue workspace-scoped jobs through JobRunr OSS and execute their handlers within runInTenant.
4. Capture the workspace at enqueue time; check its current eligibility before each execution.
5. Provide an explicit development mock for the missing platform TenantContext and workspace catalog.

### Non-Functional

1. Attempt one SMTP send per call. Bound connection, read and write waits with finite SMTP timeouts.
2. Keep recipient addresses, message contents and SMTP credentials out of logs.
3. Verify delivery against a local SMTP test server using synthetic recipients.
4. No job may execute under another workspace, including nested calls, retries and reused threads.
5. Store only workspace UUID, handler type and record UUID in the job payload; never serialize credentials, business content or executable closures as payload data.
6. Use the existing Java 21 / Spring Boot 3 stack, JobRunr OSS and a separate PostgreSQL Jobs database.

## User Stories

### STORY-001: Send a review decision notification through SMTP (KAN-162)

- As a ticket writer, I want the review decision and its reason or comment by email so that I know how to respond.
- Acceptance:
  - AC-001.1: WHEN the caller submits an email with one recipient, a subject, a plain-text body and an HTML body, THE SMTP sender SHALL send one multipart email holding that plain-text part and that HTML part to the recipient, from the display name "T-Solve" at the configured sender address, with that subject.
  - AC-001.2: THE SMTP sender SHALL send the subject and both bodies exactly as submitted and SHALL add no review-specific content; the review email subject, layout and content are defined by ticket-review AC-010.2 and AC-010.7–010.9 and rendered before the sender is called.
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

## Out of Scope

- STORY-001: a later KAN-162 change covers retry policy and job delivery, including the limit of three retries in one hour. KAN-155 owns the trigger after a committed review decision; its integration verifies that email failure cannot roll back the decision. The job integration owns recipient lookup in the current Workspace; this slice uses only synthetic recipients in local tests. First Admin invitation and production SMTP provider setup are outside this slice; the HTML layout and its content are defined by ticket-review STORY-010.
- STORY-002: real platform TenantContext, catalog persistence, tenant database routing and row guards (KAN-99 / platform). The mock proves context propagation only, not physical database isolation.
- STORY-002: production container provisioning, dashboard rollout, per-Workspace recurring job registration on provisioning and per-workspace concurrency caps (KAN-161). JOB-02 itself (one recurring job fanning out per Active Workspace) is ticket-review STORY-014.
- STORY-002: wiring the SMTP sender and first-Admin invitation as job handlers (KAN-162 follow-up).
- REST endpoints, frontend changes, changes to tenant business tables and a new custom queue implementation.
- Exactly-once delivery: handlers must tolerate JobRunr retries; this module does not add a distributed transaction between business writes and job enqueue.

## Interface Contracts

### Exposed by this module

- SMTP delivery (STORY-001): an internal Java delivery operation accepts a decision kind, one writer address, a ticket key, and the associated reason or comment. It reports success when the SMTP server accepts the message and reports delivery failure to the caller when the send fails.
- `TenantJobService.enqueue(String handlerType, UUID recordId)` (STORY-002): returns the JobRunr job id; captures the bound workspace rather than accepting a caller-supplied workspace.
- `TenantExecutionService.runInTenant(UUID workspaceId, Runnable action)` (STORY-002): checked workspace scope; no transaction is held across workspace selection.
- `TenantJobHandler` (STORY-002): registered handler type plus execution of a record UUID under the job workspace; production business handlers are supplied by consuming modules.
- `WorkerTenantService.activeWorkspaceIds()` (STORY-003): the UUIDs of the Active Workspaces at the time of the call; the platform adapter implements it from the catalog later.
- JobRunr execution entry point (STORY-002): workspace UUID, handler type and record UUID only; no HTTP surface.

### Required from other modules

- The future caller of the SMTP sender supplies the committed review decision and resolves the writer address within the decision's Workspace. Local tests provide synthetic decision data and a local SMTP endpoint.
- Platform adapter contract: current workspace lookup, scoped binding with restoration, and workspace eligibility lookup. Until platform exists, a worker-owned mock adapter is enabled only by `worker-mock`; do not install a pretend production class in `common.tenant`.
- Workspace and record ids are Java UUIDs, following backend/AGENTS.md rather than the reference SQL's BIGINT ids.
- JobRunr owns its dedicated Jobs database tables; tenant Liquibase changelogs remain unchanged.
- Reference: architecture.md, Background Jobs; platform/spec.md AC-002.1, AC-002.12 through AC-002.14.

## Definition of Done

- [ ] Every acceptance criterion has a test whose name cites its AC id.
- [ ] Both notification kinds are verified with the local SMTP server.
- [ ] Focused scope, concurrency, retry and JobRunr storage/execution tests pass.
- [ ] `mvn spotless:apply`, `mvn verify` and `mvn spotless:check` pass; unavailable infrastructure is reported explicitly.
- [ ] `bun harnessclaude/hooks/sdd.ts check worker` and `trace worker STORY-001` / `STORY-002` pass.
- [ ] Mock profile and Jobs DB environment variables are documented without credentials.
- [ ] User reviews code and verification evidence before the feature commit.
