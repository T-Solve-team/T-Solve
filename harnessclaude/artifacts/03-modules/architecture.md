---
project: T-Solve
date: 2026-10-19
---

# Architecture Overview

System-level decisions that apply across all modules. Rows marked TBD are settled in the decompose phase.
Every backend design and task follows the team's backend conventions in `backend/AGENTS.md` — the one
source for code structure, naming, layers, API, database, tests and bugs. Every frontend design and task follows
`frontend/AGENTS.md` and the guidelines it links (`frontend/guidelines/`) — the one source for structure, naming,
data layer, UI kit, routing, forms, tests and tooling. This file keeps only system-level decisions.

## Deployment Model

**Chosen:** modular monolith (one Spring Boot 3 app, Java 21, Maven)
**Rationale:** one team, one deployable on one VPS; module boundaries live in packages, not services.

## Backend Code Structure

**Chosen:** layered architecture, folders split by module (package-by-module, then by layer).
Folder tree and dependency rules: `backend/AGENTS.md` §1.

## Frontend Code Structure

**Chosen:** React single-page app, one folder per feature under `frontend/src/features/`; other code imports a
feature only through its `index.ts`; `src/app/` and `src/shared/` change only with the FE Lead's review.
Folder tree and import rules: `frontend/guidelines/01-project-structure.md`; ownership: `02-conflict-avoidance.md`.
A module that owns frontend code lists its `frontend/src/features/<feature>/**` folders in `paths` (module.md),
so the gate opens them for it and `sdd.ts trace` counts their tests.

## Communication Patterns

**Between modules:** in-process calls to the other module's service interfaces (see Backend Code Structure)
**Between frontend and backend:** REST JSON under `/api/v1/**`; the agreed contract is `frontend/openapi/tsolve-api.yaml`.
The frontend client is generated from it (`npm run generate:api`) and never hand-written; a backend endpoint
changes the YAML in the same PR (`frontend/guidelines/06-data-layer.md` §2).

## Shared Infrastructure

| Resource | Shared or Per-Module | Detail |
|----------|---------------------|--------|
| Database | shared | PostgreSQL 16 + pgvector, one schema |
| Schema migrations | shared | Liquibase: `db/changelog/db.changelog-master.xml` includes formatted-SQL changesets in `db/changelog/changes/NNN-<slug>.sql`; never edit an applied changeset, add a new one |
| Auth/Identity | shared, deferred | Google OIDC, settled when the identity module is decomposed. Until then, modules read the current user and role from Spring Security's `Authentication`, and tests use `@WithMockUser` (decided 2026-10-21) |
| Logging | shared | SLF4J to stdout |
| API docs | shared | springdoc-openapi: `/v3/api-docs`, Swagger UI `/swagger-ui.html` |

## Repo Structure

**Chosen:** monorepo — `backend/` (Spring Boot), `frontend/` (React), `harnessclaude/` (this workspace).

## Deployment Topology

**Target:** containers · **Provider:** VPS (Vietnam) · **Orchestration:** Docker Compose + Caddy

## Cross-Cutting Concerns

| Concern | Decision | Detail |
|---------|----------|--------|
| Configuration | env vars | Config files hold `${VAR}` placeholders. Secrets, credentials, hosts and URLs have **no** default — they come only from `.env` (git-ignored; Spring reads it via `spring.config.import`) or the server's environment. Non-sensitive values (a port, a feature flag) may carry a default: `${SERVER_PORT:8080}`. `.env.example` lists every name with no real value. Frontend: `VITE_*` values are build-time and end up in the browser, so they are never secret; `frontend/.env.example` lists them; Docker build args: `frontend/guidelines/11-code-quality-and-tooling.md` §6. |
| Error handling | `common/exception` + `<module>/exception` | Module exceptions extend the base exceptions in `common/exception`; one global handler: domain exceptions → `GlobalExceptionHandler` → RFC 9457 `ProblemDetail` with an SRS `code` (MSGxx). The frontend shows it by its `code` through the MSG catalog (`frontend/src/shared/messages/catalog.ts`) and never types message text (`frontend/guidelines/06-data-layer.md` §6) |
| Security | deny by default | Every endpoint declares its roles with `@PreAuthorize`; the backend always re-checks permissions. A frontend route lists its roles and the guard does the rest; the UI only hides what a user cannot do (`frontend/guidelines/08-routing-and-access.md`) |
| Audit | listed actions | The actions in "REST, errors, security" record an audit event; `audit_event` is never updated or deleted |
| API docs | springdoc | Minimal annotations on controllers; the YAML contract is the source of truth |
| Testing strategy | JUnit 5 · Vitest | Unit tests per service/entity rule; test names cite the AC id. Frontend: Vitest + React Testing Library + MSW, a page test per feature and a test per mutation flow; the `it` name starts with the AC id, `it('AC-001.1 …')` (`frontend/guidelines/10-testing.md`) |
| Code style | Spotless · Prettier + ESLint | Palantir Java Format, checked on changed files (`mvn spotless:check`). Frontend: `npm run format:check` and `npm run lint` (`frontend/guidelines/11-code-quality-and-tooling.md`) |

## Key Constraints

- No secret or environment-specific value in any committed file.
- The schema is owned by Liquibase; Hibernate only validates (`ddl-auto: validate`).
- Personal data is masked before any text leaves the server for an external AI service.

## Coding Conventions

Team-owned, read by people and every AI tool:
- Backend: `backend/AGENTS.md` (`backend/CLAUDE.md` imports it).
- Frontend: `frontend/AGENTS.md` and `frontend/guidelines/` (`frontend/CLAUDE.md` imports it).

Bugs are logged in `docs/bug-log.md`.
