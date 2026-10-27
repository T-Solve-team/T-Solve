---
project: T-Solve
date: 2026-10-25
---

# Architecture Overview

System-level decisions that apply across all modules. Rows marked TBD are settled in the decompose phase.
Every backend design and task follows the team's backend conventions in `backend/AGENTS.md` — the one
source for code structure, naming, layers, API, database, tests and bugs. Every frontend design and task follows
`frontend/AGENTS.md` and the guidelines it links (`frontend/guidelines/`) — the one source for structure, naming,
data layer, UI kit, routing, forms, tests and tooling. This file keeps only system-level decisions.

Requirements baseline: `Report3/` (`CONTEXT.md` glossary, `UC_list_and_Actors.md`, `Business_Rules.md`,
`System_Messages.md`, `SRS_Delta.md` decisions, `adr/0001-multi-tenant-pool-model.md`).

## Deployment Model

**Chosen:** modular monolith (one Spring Boot 3 app, Java 21, Maven) in the **multi-tenant pool model**: one
deployment and one database serve every Workspace (ADR-0001, GBR-37). The same jar runs as two processes: the web
process (REST API for the Workspace sites, the Platform Console and the Jira App) and the background worker
(profile `worker`, no web server, its own container; see Background Jobs).
**Rationale:** one team, one deployable; module boundaries live in packages, not services. A three-person ops
budget (one application VPS plus one AI VPS) cannot run and upgrade one server per company (ADR-0001, 24/10/2026).
This replaces the single-tenant "one Workspace per installation" model of Report 3 v3460 (CR-01…04).

## Multi-Tenancy

**Chosen:** pool model — shared tables, a Workspace on every record, isolation in three layers (ADR-0001).

| Layer | Decision |
|-------|----------|
| Data | Every tenant table has `workspace_id` (not null). Entities extend the base entity in `common/tenant/`, whose `workspace_id` is a Hibernate `@TenantId`: Hibernate fills it on insert and adds it to every query from the current `TenantContext`. Platform tables (Workspaces with their site name, Google domain and status; their Jira sites and `cloudId`) have no `workspace_id`. |
| Database | PostgreSQL row-level security on every tenant table: a policy compares `workspace_id` with a per-transaction setting written from `TenantContext`. The application connects with a role that does not own the tables and cannot bypass RLS. RLS is the last guard when a query forgets the Workspace (ADR-0001). |
| Resolution | The Workspace of a request comes **only** from (GBR-37): the host name of a Workspace site `{site name}.tsolve.io.vn` (web request); the `cloudId` of the verified Forge Invocation Token (call from the T-Solve app in Jira, GBR-14, GBR-19); or the job's Workspace (background job). No request can choose or change its Workspace and **no API takes a Workspace from the request**. A site name that belongs to no Workspace → MSG119; a call from a Jira site registered to no Workspace → MSG124. |
| Platform hosts | `login.`, `admin.` and `api.tsolve.io.vn` have no Workspace of their own. `login.` learns the Workspace only from a signed, short-lived state value created by the server and checked on the callback (D9, MSG141). `api.` takes it only from the verified `cloudId`. `admin.` (Platform Console) works only on platform data and never runs in a Workspace's context, except the user count of UC-54 and the first Admin account of UC-56 (GBR-37). |
| Sessions | A Workspace session is valid only on its own Workspace site (MSG123) and never on `admin.`; a Platform Operator signs in through `login.` against an allow-list of Operator emails in the deployment configuration, and that session is valid only on `admin.` (D13, GBR-03). |
| Suspended Workspace | Its Workspace site, sign-in and Jira App calls are refused (MSG120, MSG121); its data is kept; its jobs are postponed, not run, and JOB-02 skips it (D12); ticket tracker events received meanwhile are dropped and not replayed (D29, GBR-24). |
| Files | Stored in one folder per Workspace, `/{workspaceId}/…`, taken from `TenantContext` (GBR-37). |
| AI | Retrieval for Search, AI Ticket Triage and the Knowledge Chatbot only uses data of the Workspace of the request or job (GBR-12). |

The tenant-isolation test (an Account of Workspace A cannot read Workspace B — web app, Jira App, Search,
Chatbot) is mandatory before the demo (ADR-0001, TS-163). Code: `TenantContext`, the base entity and the RLS hook in
`common/tenant/`; the resolvers and the Workspace registry in `platform/` (module `platform`, `module-map.md`).

## Backend Code Structure

**Chosen:** layered architecture, folders split by module (package-by-module, then by layer).
Folder tree and dependency rules: `backend/AGENTS.md` §1. `common/tenant/` is shared infrastructure like the rest
of `common/` (no business logic, no entities; the base entity is a `@MappedSuperclass`).

## Frontend Code Structure

**Chosen:** React single-page app, one folder per feature under `frontend/src/features/`; other code imports a
feature only through its `index.ts`; `src/app/` and `src/shared/` change only with the FE Lead's review.
Folder tree and import rules: `frontend/guidelines/01-project-structure.md`; ownership: `02-conflict-avoidance.md`.
A module that owns frontend code lists its `frontend/src/features/<feature>/**` folders in `paths` (module.md),
so the gate opens them for it and `sdd.ts trace` counts their tests.
The same app serves the Workspace sites and the Platform Console (`admin.`): the app chooses its route tree by
host; the Platform Console screens live in `features/platform-console/`.

## Communication Patterns

**Between modules:** in-process calls to the other module's service interfaces (see Backend Code Structure); a
module that must tell a module above it about a change (for example a ticket Published) publishes an application
event instead of calling up the dependency graph.
**Between frontend and backend:** REST JSON under `/api/v1/**`; the agreed contract is `frontend/openapi/tsolve-api.yaml`.
The frontend client is generated from it (`npm run generate:api`) and never hand-written; a backend endpoint
changes the YAML in the same PR (`frontend/guidelines/06-data-layer.md` §2). `/api/v1/**` keeps the original Host,
from which the backend resolves the Workspace.
**Between web process and worker:** jobs in the shared database (see Background Jobs).

## Background Jobs

**Chosen:** JobRunr, open-source edition, with PostgreSQL storage (the application database), run by the
`worker` process (decision of 25/10). It gives retries, recurring jobs (JOB-02 at 00:00 per Workspace) and a
dashboard. T-Solve adds, in the `worker` module:
- every job carries its `workspaceId`, taken from `TenantContext` when it is enqueued;
- every job runs inside `runInTenant(workspaceId, …)`, so its queries, RLS and files are those of its Workspace;
- a job of a Suspended Workspace is postponed, not run, and runs after reactivation (D12, GBR-24);
- fairness between Workspaces: a configurable cap on the jobs of one Workspace that run at the same time, so one
  company cannot block another (GBR-37; replaces the round-robin of TS-130, D43).

**Alternatives considered:** our own job table polled with `SELECT … FOR UPDATE SKIP LOCKED` (full control and
true per-Workspace turns, but retries, scheduling and a dashboard to build and test ourselves); JobRunr Pro dynamic
queues (per-tenant queues out of the box, but a paid licence).
**Rationale:** JobRunr OSS covers retries, recurring jobs and monitoring with no new infrastructure (it uses the
database we already run); the one missing piece, per-Workspace queues, is covered well enough for the MVP by the
per-Workspace cap.

## Shared Infrastructure

| Resource | Shared or Per-Module | Detail |
|----------|---------------------|--------|
| Database | shared | PostgreSQL 16 + pgvector, one schema for every Workspace; `workspace_id` + `@TenantId` + row-level security (Multi-Tenancy) |
| Schema migrations | shared | Liquibase: `db/changelog/db.changelog-master.xml` includes formatted-SQL changesets in `db/changelog/changes/NNN-<slug>.sql`; never edit an applied changeset, add a new one |
| Auth/Identity | shared | Central Google OIDC sign-in on `login.`; the Workspace comes from the signed state value (D9); the `hd` claim must equal the Workspace's Google domain (MSG122); Platform Operators by an allow-list (D13) |
| Background jobs | shared | JobRunr OSS on PostgreSQL, `worker` process (Background Jobs) |
| File storage | shared | One folder per Workspace (`/{workspaceId}/…`) for Evidence and Wiki documents |
| AI | shared | Ollama on the AI VPS: the chat LLM and bge-m3 embeddings (1024 dimensions), for everything; no OpenRouter, no third-party AI service (D3, D41, GBR-12) |
| Email | shared | SMTP, sent by the worker |
| Logging | shared | SLF4J to stdout |
| API docs | shared | springdoc-openapi: `/v3/api-docs`, Swagger UI `/swagger-ui.html` |

## Repo Structure

**Chosen:** monorepo — `backend/` (Spring Boot), `frontend/` (React), `jira-app/` (Forge app), `infra/`
(deployment, Caddy, seed; *proposed*), `harnessclaude/` (this workspace).

## Deployment Topology

**Target:** containers · **Provider:** VPS (Vietnam) · **Orchestration:** Docker Compose + Caddy

| Server | Runs |
|--------|------|
| Application VPS | Caddy with the wildcard certificate `*.tsolve.io.vn`, routing by host: `{site name}.` → web app + `/api`, `admin.` → Platform Console, `login.` → central sign-in, `api.` → calls from the Jira App (Forge Remote); the web process; the worker; PostgreSQL 16 + pgvector |
| AI VPS | Ollama with the chat LLM and bge-m3; reachable only from the application VPS over a private, encrypted link, never from the internet (D41, GBR-12) |

The two servers exist because the models need their own CPU and RAM (D41; Jira KAN-194, KAN-200, KAN-203). On a
developer machine, Docker Compose also runs Ollama; every environment points the backend at it with
`OLLAMA_BASE_URL`. Demo data (two Workspaces) is seeded from `infra/seed/` on dev and staging only, never from the
production configuration (D42, GBR-24).

## Cross-Cutting Concerns

| Concern | Decision | Detail |
|---------|----------|--------|
| Configuration | env vars | Config files hold `${VAR}` placeholders. Secrets, credentials, hosts and URLs have **no** default — they come only from `.env` (git-ignored; Spring reads it via `spring.config.import`) or the server's environment. Non-sensitive values (a port, a feature flag) may carry a default: `${SERVER_PORT:8080}`. `.env.example` lists every name with no real value. Frontend: `VITE_*` values are build-time and end up in the browser, so they are never secret; `frontend/.env.example` lists them; Docker build args: `frontend/guidelines/11-code-quality-and-tooling.md` §6. |
| Tenancy | pool model | The Workspace only from host / verified `cloudId` / job; `workspace_id` + `@TenantId` + RLS; no API takes a Workspace from the request (Multi-Tenancy, GBR-37, ADR-0001) |
| Error handling | `common/exception` + `<module>/exception` | Module exceptions extend the base exceptions in `common/exception`; one global handler: domain exceptions → `GlobalExceptionHandler` → RFC 9457 `ProblemDetail` with an SRS `code` (MSGxx). The frontend shows it by its `code` through the MSG catalog (`frontend/src/shared/messages/catalog.ts`) and never types message text (`frontend/guidelines/06-data-layer.md` §6) |
| Security | deny by default | Every endpoint declares its roles with `@PreAuthorize`; the backend always re-checks permissions. A frontend route lists its roles and the guard does the rest; the UI only hides what a user cannot do (`frontend/guidelines/08-routing-and-access.md`) |
| Audit | listed actions | The actions in "REST, errors, security" record an audit event; `audit_event` is never updated or deleted |
| API docs | springdoc | Minimal annotations on controllers; the YAML contract is the source of truth |
| Testing strategy | JUnit 5 · Vitest | Unit tests per service/entity rule; test names cite the AC id. Frontend: Vitest + React Testing Library + MSW, a page test per feature and a test per mutation flow; the `it` name starts with the AC id, `it('AC-001.1 …')` (`frontend/guidelines/10-testing.md`). A tenant-isolation test is mandatory (Multi-Tenancy) |
| Code style | Spotless · Prettier + ESLint | Palantir Java Format, checked on changed files (`mvn spotless:check`). Frontend: `npm run format:check` and `npm run lint` (`frontend/guidelines/11-code-quality-and-tooling.md`) |

## Key Constraints

- No secret or environment-specific value in any committed file.
- The schema is owned by Liquibase; Hibernate only validates (`ddl-auto: validate`).
- No user, search result, triage suggestion, Chatbot answer, file, job or email ever crosses Workspaces (GBR-37).
- No Workspace or Admin is created at installation or from the deployment configuration; Workspaces are created
  only by a Platform Operator (UC-55, GBR-24). The demo seed runs on dev and staging only (D42).
- Only PII-masked text is sent to Ollama on the AI VPS; no content goes to a third-party AI service (GBR-12, D41).
- AI is optional: an AI failure never breaks the system (GBR-12).

## Coding Conventions

Team-owned, read by people and every AI tool:
- Backend: `backend/AGENTS.md` (`backend/CLAUDE.md` imports it).
- Frontend: `frontend/AGENTS.md` and `frontend/guidelines/` (`frontend/CLAUDE.md` imports it).

Bugs are logged in `docs/bug-log.md`.
