---
project: T-Solve
date: 2026-11-04
---

# Architecture Overview

System-level decisions that apply across all modules. Aligned with the multi-tenant high-level architecture
(drawio, 29/10) on 3/11 (KAN-231 HLA change): the Platform Console on `admin.`, the Platform / Tenant Registry
database, Redis for sessions; on 4/11 the decisions on Deployment, PII masking, the Jobs database, object storage,
provisioning and the per-database row guard (HLA Q1–Q6). Rows marked TBD are settled in the decompose phase.
Every backend design and task follows the team's backend conventions in `backend/AGENTS.md` — the one
source for code structure, naming, layers, API, database, tests and bugs. Every frontend design and task follows
`frontend/AGENTS.md` and the guidelines it links (`frontend/guidelines/`) — the one source for structure, naming,
data layer, UI kit, routing, forms, tests and tooling. This file keeps only system-level decisions.

Requirements baseline: `Report3/` (`CONTEXT.md` glossary, `UC_list_and_Actors.md`, `Business_Rules.md`,
`System_Messages.md`, `SRS_Delta.md` decisions) and the Report3 split files of 31/10 (`Report_3_BreakDown/`:
UC list, GBR, MSG), which win where they differ. Tenancy: `docs/adr/0002-database-per-workspace.md`, which
supersedes ADR-0001.

## Deployment Model

**Chosen:** modular monolith (one Spring Boot 3 app, Java 21, Maven), **multi-tenant with one database per
Workspace**: one deployment serves every Workspace, each Workspace has its own PostgreSQL database, and a catalog
database finds it (ADR-0002, GBR-37). The HLA names the catalog database the **Platform / Tenant Registry
database**. The same jar runs as three processes: the web process (REST API for the Workspace sites, the Platform
Console and the Jira App), the background worker (profile `worker`, no web server, its own container; see Background
Jobs) and the ops script (profile `ops`, the hand-run form of the same provisioning service the Platform Console calls).
**Rationale:** one team, one deployable; module boundaries live in packages, not services. A three-person ops
budget (one application VPS plus one AI VPS) cannot run one server per company, while a database per company keeps
each company's data behind a database boundary and lets it be backed up and restored on its own (ADR-0002, 29/10).
This replaces the pool model of ADR-0001 (24/10) and the single-tenant model of Report 3 v3460.

## Multi-Tenancy

**Chosen:** database per Workspace plus a catalog database (ADR-0002).

| Layer | Decision |
|-------|----------|
| Data | Each Workspace has its own PostgreSQL database with the business tables of every module; tables have **no** `workspace_id`. The catalog (Platform / Tenant Registry) database holds only `workspaces` (site name, status, database connection with an encrypted password, the Jira site and its `cloudId`) and `workspace_emails` (one email in one Workspace, GBR-16), the short-lived sign-in states and hand-off codes of `identity` (read on `login.`, before any Workspace is bound). No business data is in the catalog (GBR-37). JobRunr's tables are in a separate Jobs database on the same server (Background Jobs). |
| Database access | `TenantContext` binds a Workspace; one routing `DataSource` sends every JPA and JDBC query to the bound Workspace's database through a lazily opened, bounded pool per Workspace. With no Workspace bound, no tenant database can be reached. Each Workspace database has its own runtime user that can connect only to it; migrations use a separate owner user and the runtime user has no `BYPASSRLS`. |
| Tenant row guard | The third isolation layer (HLA, 4/11): every Workspace database holds a one-row table with its own tenant id, written at provisioning; every tenant table has a row policy that returns no row unless the transaction's tenant setting (set by the router when it opens the transaction) equals that id, so a misrouted connection sees nothing even if the router has a bug. The convention guard test fails for a tenant table without the policy. The tables still have no `workspace_id`. |
| Resolution | The Workspace of a request comes **only** from (GBR-37): the host name of a Workspace site `{site name}.tsolve.io.vn` (web request); the `cloudId` of the verified Forge Invocation Token (call from the T-Solve app in Jira, GBR-14, GBR-19); or the job's Workspace (background job). No request can choose or change its Workspace and **no API takes a Workspace from the request**. A site name that belongs to no Workspace → MSG151; a call from a Jira site linked to no Workspace → MSG152. |
| Platform hosts | `login.`, `api.` and `admin.tsolve.io.vn` have no Workspace of their own. `login.` learns the Workspace only from a signed, short-lived state value created by the server and checked on the callback (D9). `api.` takes it only from the verified `cloudId`. `admin.` is the **Platform Console** (restored 3/11 from the HLA, which reverses the removal of 31/10): a Platform Operator on the allow-list signs in there (D13, D36) and sees no Workspace data. |
| Provisioning | A Platform Operator creates, suspends and reactivates a Workspace in the Platform Console and invites its first Admin (HLA, `platform` package): Workspace database and runtime user, migrations, registry row, first-Admin invitation — all or nothing (GBR-24). `infra/ops/workspace.sh` (KAN-240) stays as the hand-run form of the same provisioning service the Console calls (the demo seed uses it before the Console exists); the first Admin is invited, as in the HLA. The Admin then enters the company information, the Jira site and the service account at the first sign-in (UC-10); the Jira site's `cloudId` is recorded in the registry then and cannot change (GBR-14, GBR-24). Nobody deletes a Workspace or reads its data (GBR-38). |
| Sessions | A Workspace session is valid only on its own Workspace site (GBR-03). Sessions live in **Redis** (the HLA's shared, site-bound session store), so no API replica keeps a session of its own; the session store is kept apart from any eviction cache. |
| Suspended Workspace | Its Workspace site and sign-in are refused (MSG150), the Jira App's calls are refused; its database is kept; its jobs do not run and JOB-02 skips it; ticket tracker events received meanwhile are dropped and not replayed (D29, GBR-38). |
| Files | Stored in object storage (Garage, S3-compatible; HLA 4/11): every object key starts with the Workspace's prefix taken from `TenantContext` (GBR-37); files are served by signed URLs; no pod-local or container-local files, so any API replica can serve any file. |
| AI | Retrieval for Search, AI Solution Suggestion and the Knowledge Chatbot only uses data of the Workspace of the request or job, filtered by what the user may see (GBR-12, GBR-37). |

The tenant-isolation test (an Account of Workspace A cannot read Workspace B — web app, Jira App, Search,
Chatbot) is mandatory before the demo (ADR-0002, TS-163). Code: `TenantContext` and the routing `DataSource` in
`common/tenant/`; the resolvers, the catalog service and the ops script (if kept) in `platform/` (module `platform`,
`module-map.md`).

## Backend Code Structure

**Chosen:** layered architecture, folders split by module (package-by-module, then by layer).
Folder tree and dependency rules: `backend/AGENTS.md` §1. `common/tenant/` is shared infrastructure like the rest
of `common/` (no business logic, no entities).

## Frontend Code Structure

**Chosen:** React single-page app, one folder per feature under `frontend/src/features/`; other code imports a
feature only through its `index.ts`; `src/app/` and `src/shared/` change only with the FE Lead's review.
Folder tree and import rules: `frontend/guidelines/01-project-structure.md`; ownership: `02-conflict-avoidance.md`.
A module that owns frontend code lists its `frontend/src/features/<feature>/**` folders in `paths` (module.md),
so the gate opens them for it and `sdd.ts trace` counts their tests.
The app serves the Workspace sites and the Platform Console on `admin.` (the route tree is chosen by host); the
Console is back as in the HLA (3/11). KAN-244 (4/11) no longer removes the Console route tree or its sign-in screen:
it removes only the old triage code (see the `platform-console` module).

## Communication Patterns

**Between modules:** in-process calls to the other module's service interfaces (see Backend Code Structure); a
module that must tell a module above it about a change (for example a ticket Published) publishes an application
event instead of calling up the dependency graph.
**Between frontend and backend:** REST JSON under `/api/v1/**`; the agreed contract is `frontend/openapi/tsolve-api.yaml`.
The frontend client is generated from it (`npm run generate:api`) and never hand-written; a backend endpoint
changes the YAML in the same PR (`frontend/guidelines/06-data-layer.md` §2). `/api/v1/**` keeps the original Host,
from which the backend resolves the Workspace.
**Between web process and worker:** jobs in the Jobs database (see Background Jobs); a job carries its
Workspace id and record ids only.

## Background Jobs

**Chosen:** JobRunr, open-source edition, with PostgreSQL storage in a dedicated Jobs database (`tsolve_<env>_jobs`, on the same server as the catalog; HLA 4/11, so job load never touches the registry that every request reads), run by the
`worker` process (decision of 25/10). It gives retries, recurring jobs (JOB-02 at 00:00 per Workspace) and a
dashboard. T-Solve adds, in the `worker` module:
- every job carries its `workspaceId`, taken from `TenantContext` when it is enqueued;
- every job runs inside `runInTenant(workspaceId, …)`, so its queries go to its Workspace's database and its
  files are those of its Workspace; JOB-02 runs once per Active Workspace (GBR-37);
- a job of a Suspended Workspace does not run (GBR-38);
- fairness between Workspaces: a configurable cap on the jobs of one Workspace that run at the same time, so one
  company cannot block another (GBR-37; replaces the round-robin of TS-130, D43).

**Alternatives considered:** our own job table polled with `SELECT … FOR UPDATE SKIP LOCKED` (full control and
true per-Workspace turns, but retries, scheduling and a dashboard to build and test ourselves); JobRunr Pro dynamic
queues (per-tenant queues out of the box, but a paid licence).
**Rationale:** JobRunr OSS covers retries, recurring jobs and monitoring with no new infrastructure (it uses the
PostgreSQL server we already run); the one missing piece, per-Workspace queues, is covered well enough for the MVP by the
per-Workspace cap.

## Shared Infrastructure

| Resource | Shared or Per-Module | Detail |
|----------|---------------------|--------|
| Database | per Workspace | PostgreSQL 16 + pgvector on one server: a catalog (Platform / Tenant Registry) database, a Jobs database (`tsolve_<env>_jobs`) plus one database per Workspace (Multi-Tenancy, ADR-0002) |
| Session store | shared | Redis (the HLA's Redis HA in production): the Workspace and Operator sessions, one key per session with a time to live; not used as an eviction cache. On a developer machine and in the integration tests: a Redis container (a Docker Compose service for the local stack, Testcontainers Redis for the tests) |
| Schema migrations | shared | Liquibase, two changelogs: the catalog changelog `db/changelog/catalog/catalog-master.xml` and the tenant changelog `db/changelog/db.changelog-master.xml` (formatted-SQL changesets in `db/changelog/changes/NNN-<slug>.sql`), applied to every Workspace database at startup, in the deploy and when a Workspace is created; never edit an applied changeset, add a new one |
| Auth/Identity | shared | Central Google OIDC sign-in on `login.`; the Workspace comes from the signed state value (D9), kept in the registry database; the session lives in Redis; only an email the Admin added to that Workspace signs in (GBR-03) |
| Background jobs | shared | JobRunr OSS on the Jobs database, `worker` process (Background Jobs) |
| File storage | per Workspace | Garage (S3-compatible) in the stack: one key prefix per Workspace for Evidence and Wiki documents, signed URLs, no local files (Multi-Tenancy, Files) |
| AI | shared | Ollama on the AI VPS: the chat LLM and bge-m3 embeddings (1024 dimensions), for everything; no OpenRouter, no third-party AI service, no PII masking anywhere (ai module, Chatbot, suggestions; content never leaves T-Solve's servers; D3, D41, GBR-12). The HLA drawing still shows "PII masking"; it is dropped here (decision 4/11) and the drawing is to be corrected |
| Email | shared | SMTP, sent by the worker |
| Logging | shared | SLF4J to stdout |
| API docs | shared | springdoc-openapi: `/v3/api-docs`, Swagger UI `/swagger-ui.html` |

## Repo Structure

**Chosen:** monorepo — `backend/` (Spring Boot), `frontend/` (React), `jira-app/` (Forge app), `infra/`
(deployment, Caddy, seed; *proposed*), `harnessclaude/` (this workspace).

## Deployment Topology

**Target:** containers · **Provider:** VPS (Vietnam) · **Orchestration:** Docker Compose + Caddy for the MVP and the demo; the HLA (Kubernetes with an HA load balancer / Ingress, N API replicas, Redis HA, HPA) is the target architecture (decision 4/11). Report 4 shows both

| Server | Runs |
|--------|------|
| Application VPS | Caddy with the wildcard certificate `*.tsolve.io.vn`, routing by host: `{site name}.` → web app + `/api`, `admin.` → Platform Console, `login.` → central sign-in, `api.` → calls from the Jira App (Forge Remote); the web process; the worker; the ops script; Redis (one container with persistence, `noeviction`); Garage; PostgreSQL 16 + pgvector with the catalog database, the Jobs database and one database per Workspace |
| AI VPS | Ollama with the chat LLM and bge-m3; reachable only from the application VPS over a private, encrypted link, never from the internet; no Internet egress and no prompt logging (D41, GBR-12) |

The two servers exist because the models need their own CPU and RAM (D41; Jira KAN-194, KAN-200, KAN-203). On a
developer machine, Docker Compose also runs Redis, Garage and Ollama (every app-level HLA rule — registry, router, Redis sessions, site-bound sessions, row guard — works the same on Compose; the move to Kubernetes is a later step for infra); every environment points the backend at it with
`OLLAMA_BASE_URL`. Demo data (two Workspaces, created through the provisioning service) is seeded from `infra/seed/` on dev and staging only, never from the
production configuration (D42, GBR-24).

## Cross-Cutting Concerns

| Concern | Decision | Detail |
|---------|----------|--------|
| Configuration | env vars | Config files hold `${VAR}` placeholders. Secrets, credentials, hosts and URLs have **no** default — they come only from `.env` (git-ignored; Spring reads it via `spring.config.import`) or the server's environment. Non-sensitive values (a port, a feature flag) may carry a default: `${SERVER_PORT:8080}`. `.env.example` lists every name with no real value. Frontend: `VITE_*` values are build-time and end up in the browser, so they are never secret; `frontend/.env.example` lists them; Docker build args: `frontend/guidelines/11-code-quality-and-tooling.md` §6. |
| Tenancy | database per Workspace | The Workspace only from host / verified `cloudId` / job; queries routed to its database; no API takes a Workspace from the request (Multi-Tenancy, GBR-37, ADR-0002) |
| Error handling | `common/exception` + `<module>/exception` | Module exceptions extend the base exceptions in `common/exception`; one global handler: domain exceptions → `GlobalExceptionHandler` → RFC 9457 `ProblemDetail` with an SRS `code` (MSGxx). The frontend shows it by its `code` through the MSG catalog (`frontend/src/shared/messages/catalog.ts`) and never types message text (`frontend/guidelines/06-data-layer.md` §6) |
| Security | deny by default | Every endpoint declares its roles with `@PreAuthorize`; the backend always re-checks permissions. A frontend route lists its roles and the guard does the rest; the UI only hides what a user cannot do (`frontend/guidelines/08-routing-and-access.md`) |
| Audit | listed actions | The actions in "REST, errors, security" record an audit event; `audit_event` is never updated or deleted |
| API docs | springdoc | Minimal annotations on controllers; the YAML contract is the source of truth |
| Testing strategy | JUnit 5 · Vitest | Unit tests per service/entity rule; test names cite the AC id. Frontend: Vitest + React Testing Library + MSW, a page test per feature and a test per mutation flow; the `it` name starts with the AC id, `it('AC-001.1 …')` (`frontend/guidelines/10-testing.md`). A tenant-isolation test is mandatory (Multi-Tenancy) |
| Code style | Spotless · Prettier + ESLint | Palantir Java Format, checked on changed files (`mvn spotless:check`). Frontend: `npm run format:check` and `npm run lint` (`frontend/guidelines/11-code-quality-and-tooling.md`) |

## Key Constraints

- No secret or environment-specific value in any committed file.
- The schema is owned by Liquibase; Hibernate only validates (`ddl-auto: validate`).
- No user, search result, AI suggestion, Chatbot answer, file, job or email ever crosses Workspaces (GBR-37).
- No Workspace or Admin is created at installation or from the deployment configuration; Workspaces are created
  only by a Platform Operator in the Platform Console (HLA) (GBR-24, GBR-38). The demo seed runs on dev and staging only (D42).
- Content goes only to Ollama on the team's AI VPS; no content goes to a third-party AI service (GBR-12, D41).
- AI is optional: an AI failure never breaks the system (GBR-12).

## Coding Conventions

Team-owned, read by people and every AI tool:
- Backend: `backend/AGENTS.md` (`backend/CLAUDE.md` imports it).
- Frontend: `frontend/AGENTS.md` and `frontend/guidelines/` (`frontend/CLAUDE.md` imports it).

Bugs are logged in `docs/bug-log.md`.
