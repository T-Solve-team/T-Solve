# Worker jobs — KAN-102

JobRunr OSS stores requests in a **separate PostgreSQL Jobs database**. The worker captures the bound workspace UUID when enqueueing, checks that workspace is Active on every execution attempt, then calls the registered handler under `TenantExecutionService.runInTenant`. Cross-workspace nesting is rejected and the previous binding is restored after success or failure.

## Run locally

1. Prepare a separate Jobs database and a user allowed to create JobRunr's tables there. Set `JOBS_DB_URL` (a PostgreSQL JDBC URL), `JOBS_DB_USER` and `JOBS_DB_PASSWORD` in the existing git-ignored `.env` alongside the backend's other required settings. Do not point these at a tenant or catalog database. JobRunr manages only its own Jobs tables; this feature does not alter tenant Liquibase migrations.
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

Handlers must be idempotent: delivery is at least once. OSS enqueue is not atomic with an application's business transaction. SMTP, recurring JOB-02, per-workspace concurrency caps and production container rollout remain separate work.

## Verify

From `backend/`: `mvn test "-Dtest=TenantExecutionServiceImplTest,TenantJobServiceImplTest,WorkerConfigurationTest"`, then `mvn verify` with Docker running, then `mvn spotless:check`. `JobRunrTenantIT` starts an isolated PostgreSQL container, persists jobs in enqueue-only mode, restarts the Spring context in worker mode and checks actual execution and native retry with the original workspace payload. It also checks that JobRunr creates no tables in the business database.

Official integration references: [JobRequest and handlers](https://www.jobrunr.io/en/documentation/background-methods/), [programmatic configuration](https://www.jobrunr.io/en/documentation/configuration/fluent/). This implementation pins JobRunr 8.6.0 and verifies its API using the corresponding official Maven source artifact.
