---
module: worker
date: 2026-11-07
---

# Design: worker — KAN-102

Spec: artifacts/03-modules/worker/spec.md

## Architecture Overview

One feature, tenant-jobs: capture the current workspace at enqueue; persist a small JobRunr request; resolve a registered handler at execution; check workspace eligibility and execute under a restoring tenant boundary. No HTTP endpoint. Existing application startup is unchanged unless worker integration is enabled.

`TenantJobService` exposes enqueue. `TenantExecutionService` exposes runInTenant. `WorkerTenantService` is the temporary platform integration port (currentWorkspaceId, runIn, isWorkspaceActive). Its mock lives under worker and is registered only with worker-mock. Real platform will supply an adapter later; no substitute common.tenant class is installed.

JobRunr uses explicit core components rather than the starter to avoid auto-detecting the application's tenant DataSource. A lifecycle-managed runtime owns a private Hikari pool, PostgresStorageProvider, JobRequestScheduler and optionally BackgroundJobServer. Its pool is not published as a DataSource bean, preserving Spring Boot's existing business DataSource auto-configuration. Startup failures close partially constructed resources; shutdown stops execution before closing storage and pool.

## Data Model

TenantJobRequest contains workspace UUID, handler type and record UUID; no entity, content, credentials or tenant connection. JobRunr owns tables in its dedicated PostgreSQL Jobs DB. No tenant or catalog schema changes or Liquibase edits. The mock holds allow-listed Active/Suspended UUIDs and a non-inheritable ThreadLocal binding; no actual tenant data is accessed by the mock.

## Key Decisions

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

## File Structure

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

## External Dependencies

JobRunr OSS; existing Hikari, Jackson, PostgreSQL driver, JUnit, Mockito and Testcontainers. Official sources: https://www.jobrunr.io/en/documentation/background-methods/ and https://www.jobrunr.io/en/documentation/configuration/fluent/. JobRunr core avoids starter autoconfiguration; do not call the static global JobRunr configuration singleton.

## Edge Cases & Risks

Dimensions analyzed: input extremes, timing, scale, state transitions, environment, error cascades, authorization (workspace isolation), data integrity, integration, compliance, business logic. User types skipped: no user-facing endpoint or role-dependent flow.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Input extremes | Null workspace/action/record or unknown handler | High | Reject before handler/storage side effect | T1 |
| 2 | STORY-001 | Timing | A and B execute concurrently; next job reuses a thread after an exception | Critical | Each sees only its workspace, then no binding | T1 |
| 3 | STORY-001 | Authorization | Job B invoked inside scope A; same-scope nested call | Critical | Reject B without modifying A; permit A and restore | T1 |
| 4 | STORY-001 | State transitions | Workspace suspended after enqueue; handler fails before retry | Critical | Recheck on each attempt; no suspended handler call | T1 |
| 5 | STORY-001 | Error cascades | Catalog unavailable; handler throws; storage startup fails | High | Fail closed; propagate failure; clean context/resources | T1 |
| 6 | STORY-001 | Integration | JSON round trip; unknown handler after deployment | High | Preserve ids; reject unknown type, never arbitrary reflection | T1 |
| 7 | STORY-001 | Environment | Default app; worker without platform; worker-mock explicitly enabled | Critical | No worker by default; fail without adapter; mock only explicit | T1 |
| 8 | STORY-001 | Data integrity | Jobs DS config absent; business DataSource present | Critical | No fallback, fail integration startup | T1 |
| 9 | STORY-001 | Compliance | Jobs storage persists request | High | UUIDs/type only, no content or credentials | T1 |
| 10 | STORY-001 | Scale | Zero handlers or multiple handlers with same type | High | No unknown jobs accepted; reject ambiguous registration | T1 |
| 11 | STORY-001 | Business logic | Retry delivers same record more than once | High | Original ids retained; document at-least-once handler contract | T1 |

Open questions for the spec owner: none; scope and mocks approved in chat.

## Risk Review — 2026-11-07 — Verdict: CAUTION

Architect: preserve business datasource auto-configuration; keep platform integration replaceable. Security: the mock does not prove database isolation; explicitly opt in and deny unknown workspace ids. Performance: one dedicated bounded Jobs connection pool and native JobRunr retries suffice; fairness caps stay KAN-161. User: document commands and the mock limitation; never claim production tenancy works. Devil's advocate: a wrapper alone would be smaller, but the user explicitly requested real JobRunr integration; avoid a new queue or broad container work.

Agreements: tenant checks precede business handlers; keep payloads small; defaults must not start a worker; use JobRunr's own persistence and retries. Conflict: convenient datasource autodetection versus isolation — isolation wins, with an unexposed dedicated pool.

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Tenant data leakage through binding or datasource fallback | Critical | Cross-scope guard, finally restoration, dedicated private Jobs pool, concurrency/config tests | T1 |
| Mock mistaken for production tenant routing | High | Explicit worker-mock only; no default adapter; documented replacement contract | T1 |
| Failed job repeated after suspension | High | Check status each attempt; propagate native failures and retry identity | T1 |

## Verification

Builder check resolved: `tsolve.worker.enabled` activates enqueue/storage; `jobrunr.background-job-server.enabled` activates execution, defaults false and is set true by application-worker.yml. Core runtime explicitly consumes this flag; it does not rely on starter auto-configuration. Module paths include pom, profile/base YAML, environment example and worker documentation.

Focused unit/config tests, PostgreSQL-backed JobRunr serialization and dispatch integration test, then full mvn verify and spotless:check. Report infrastructure or unrelated baseline failures separately; do not weaken checks. One feature commit requires user confirmation.

Master: artifacts/04-plans/worker/design.md
