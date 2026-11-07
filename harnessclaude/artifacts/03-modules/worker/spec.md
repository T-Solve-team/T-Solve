---
module: worker
date: 2026-11-07
owner: BA
---

# Spec: worker — KAN-102

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-07 | STORY-001 | Initial KAN-102 contract including JobRunr integration and temporary TenantContext mock | User requested KAN-102, explicitly added JobRunr and requested mocked TenantContext |

## Requirements

### Functional
1. Enqueue workspace-scoped jobs through JobRunr OSS and execute their handlers within runInTenant.
2. Capture the workspace at enqueue time; check its current eligibility before each execution.
3. Provide an explicit development mock for the missing platform TenantContext and workspace catalog.

### Non-Functional
1. No job may execute under another workspace, including nested calls, retries and reused threads.
2. Store only workspace UUID, handler type and record UUID in the job payload; never serialize credentials, business content or executable closures as payload data.
3. Use the existing Java 21 / Spring Boot 3 stack, JobRunr OSS and a separate PostgreSQL Jobs database.

## User Stories

### STORY-001: Execute queued jobs in the right TenantContext (KAN-102)
- As the worker, I want each job bound to its captured workspace so that background processing cannot cross company boundaries (GBR-37, GBR-38).
- Acceptance:
  - AC-001.1: WHEN a caller enqueues a job while a workspace is bound, THE queue SHALL persist that workspace UUID together with the registered handler type and record UUID through JobRunr.
  - AC-001.2: IF enqueue is called with no bound workspace, null record id or an unregistered handler type, THEN THE queue SHALL reject the request without persisting a job.
  - AC-001.3: WHEN JobRunr executes a job for an Active workspace, THE worker SHALL invoke its registered handler inside runInTenant with exactly the workspace UUID stored in that job.
  - AC-001.4: IF the workspace is unknown or Suspended at execution time, THEN THE worker SHALL invoke no business handler and SHALL report a failed execution to JobRunr without binding a workspace (GBR-38).
  - AC-001.5: IF workspace eligibility cannot be checked, THEN THE worker SHALL fail the execution without invoking the handler.
  - AC-001.6: WHEN an action finishes normally or throws, THE runInTenant boundary SHALL restore the calling thread's previous workspace binding and propagate the original action failure.
  - AC-001.7: IF a different workspace is already bound on the executing thread, THEN THE boundary SHALL reject the execution without invoking the action or changing that binding.
  - AC-001.8: WHEN the same workspace is already bound, THE boundary SHALL permit nested execution and preserve the outer binding afterwards.
  - AC-001.9: WHEN jobs for different workspaces run concurrently or sequentially on a reused thread, THE worker SHALL keep their context bindings isolated.
  - AC-001.10: WHEN a handler fails, THE worker SHALL let JobRunr retain and retry the failed job with its original workspace UUID and SHALL recheck workspace eligibility on every attempt.
  - AC-001.11: WHILE the explicit worker-mock profile is active, THE backend SHALL provide a temporary thread-scoped TenantContext mock and a configured allow-list of Active and Suspended workspace UUIDs, treating every unlisted UUID as unknown.
  - AC-001.12: WHILE worker-mock is inactive, THE backend SHALL register no mock platform adapter; IF job processing is enabled without a real adapter, THEN THE backend SHALL fail startup rather than permit jobs using mocked tenancy.
  - AC-001.13: WHEN JobRunr integration is enabled, THE backend SHALL use a dedicated Jobs DataSource configured by environment variables and SHALL never fall back to its business or catalog DataSource.
  - AC-001.14: THE backend SHALL disable job execution and the JobRunr dashboard by default; WHEN the worker profile is enabled with required configuration, THE backend SHALL enable background processing while keeping the dashboard disabled.
  - AC-001.15: IF a persisted job names an unknown handler or has a null workspace or record UUID, THEN THE worker SHALL fail it without invoking a handler.
  - AC-001.16: THE test suite SHALL demonstrate enqueue, persisted payload serialization and execution through real JobRunr components, including PostgreSQL-backed job storage, with only platform dependencies mocked.

## Out of Scope
- Real platform TenantContext, catalog persistence, tenant database routing and row guards (KAN-99 / platform). The mock proves context propagation only, not physical database isolation.
- Production container provisioning, dashboard rollout, recurring JOB-02 registration and per-workspace concurrency caps (KAN-161).
- SMTP delivery and first-Admin invitation business handlers (KAN-162).
- REST endpoints, frontend changes, changes to tenant business tables and a new custom queue implementation.
- Exactly-once delivery: handlers must tolerate JobRunr retries; this ticket does not add a distributed transaction between business writes and job enqueue.

## Interface Contracts

### Exposed by this module
- `TenantJobService.enqueue(String handlerType, UUID recordId)`: returns the JobRunr job id; captures the bound workspace rather than accepting a caller-supplied workspace.
- `TenantExecutionService.runInTenant(UUID workspaceId, Runnable action)`: checked workspace scope; no transaction is held across workspace selection.
- `TenantJobHandler`: registered handler type plus execution of a record UUID under the job workspace; production business handlers are supplied by consuming modules.
- JobRunr execution entry point: workspace UUID, handler type and record UUID only; no HTTP surface.

### Required from other modules
- Platform adapter contract: current workspace lookup, scoped binding with restoration, and workspace eligibility lookup. Until platform exists, a worker-owned mock adapter is enabled only by `worker-mock`; do not install a pretend production class in `common.tenant`.
- Workspace and record ids are Java UUIDs, following backend/AGENTS.md rather than the reference SQL's BIGINT ids.
- JobRunr owns its dedicated Jobs database tables; tenant Liquibase changelogs remain unchanged.
- Reference: architecture.md, Background Jobs; platform/spec.md AC-002.1, AC-002.12 through AC-002.14.

## Definition of Done
- [ ] Each acceptance criterion has a test naming its AC id.
- [ ] Focused scope, concurrency, retry and JobRunr storage/execution tests pass.
- [ ] `mvn spotless:apply`, `mvn verify` and `mvn spotless:check` pass; unavailable infrastructure is reported explicitly.
- [ ] `bun harnessclaude/hooks/sdd.ts check worker` and `trace worker STORY-001` pass.
- [ ] Mock profile and Jobs DB environment variables are documented without credentials.
- [ ] User reviews code and verification evidence before the feature commit; no push or merge.
