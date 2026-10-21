---
project: T-Solve
date: 2026-10-19
---

# Architecture Overview

System-level decisions that apply across all modules. Rows marked TBD are settled in the decompose phase.
Every backend design and task follows the team's backend conventions in `backend/AGENTS.md` — the one
source for code structure, naming, layers, API, database, tests and bugs. This file keeps only system-level decisions.

## Deployment Model

**Chosen:** modular monolith (one Spring Boot 3 app, Java 21, Maven)
**Rationale:** one team, one deployable on one VPS; module boundaries live in packages, not services.

## Backend Code Structure

**Chosen:** layered architecture, folders split by module (package-by-module, then by layer).
Folder tree and dependency rules: `backend/AGENTS.md` §1.

## Communication Patterns

**Between modules:** in-process calls to the other module's service interfaces (see Backend Code Structure)
**Between frontend and backend:** REST JSON under `/api/v1/**`; the agreed contract is `frontend/openapi/tsolve-api.yaml`
**Between the Jira App and backend:** Forge Remote calls to the Jira App API (REST JSON, same contract file),
authenticated by the Forge Invocation Token (FIT); the backend reads Jira with the Forge app system token
through the `tracker` package (`TicketTrackerClientFactory` → `JiraClient`), never with stored Jira API tokens.

## Shared Infrastructure

| Resource | Shared or Per-Module | Detail |
|----------|---------------------|--------|
| Database | shared | PostgreSQL 16 + pgvector, one schema |
| Schema migrations | shared | Liquibase: `db/changelog/db.changelog-master.xml` includes formatted-SQL changesets in `db/changelog/changes/NNN-<slug>.sql`; never edit an applied changeset, add a new one |
| Auth/Identity | TBD | Google OIDC (decompose) |
| Logging | shared | SLF4J to stdout |
| API docs | shared | springdoc-openapi: `/v3/api-docs`, Swagger UI `/swagger-ui.html` |

## Repo Structure

**Chosen:** monorepo — `backend/` (Spring Boot), `frontend/` (React), `jira-app/` (Atlassian Forge, UI Kit),
`harnessclaude/` (this workspace).

## Deployment Topology

**Target:** containers · **Provider:** VPS (Vietnam) · **Orchestration:** Docker Compose + Caddy

## Cross-Cutting Concerns

| Concern | Decision | Detail |
|---------|----------|--------|
| Configuration | env vars | Config files hold `${VAR}` placeholders. Secrets, credentials, hosts and URLs have **no** default — they come only from `.env` (git-ignored; Spring reads it via `spring.config.import`) or the server's environment. Non-sensitive values (a port, a feature flag) may carry a default: `${SERVER_PORT:8080}`. `.env.example` lists every name with no real value. |
| Error handling | `common/error` | Domain exceptions → `GlobalExceptionHandler` → RFC 9457 `ProblemDetail` with an SRS `code` (MSGxx) |
| Security | deny by default | Every endpoint declares its roles with `@PreAuthorize`; the backend always re-checks permissions |
| Audit | listed actions | The actions in "REST, errors, security" record an audit event; `audit_event` is never updated or deleted |
| API docs | springdoc | Minimal annotations on controllers; the YAML contract is the source of truth |
| Testing strategy | JUnit 5 | Unit tests per service/entity rule; test names cite the AC id |
| Code style | Spotless | Palantir Java Format, checked on changed files (`mvn spotless:check`) |

## Key Constraints

- No secret or environment-specific value in any committed file.
- The schema is owned by Liquibase; Hibernate only validates (`ddl-auto: validate`).
- Personal data is masked before any text leaves the server for an external AI service.

## Backend Coding Conventions

Moved to `backend/AGENTS.md` (team-owned, read by people and every AI tool; `backend/CLAUDE.md` imports it).
Bugs are logged in `docs/bug-log.md`.
