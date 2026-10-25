---
name: platform
owner: Trần Trung Hiếu
repos: [default]
dependencies: []
complexity: large
paths:
  - backend/src/main/java/vn/tsolve/platform/**
  - backend/src/test/java/vn/tsolve/platform/**
  - backend/src/main/java/vn/tsolve/common/tenant/**
  - backend/src/test/java/vn/tsolve/common/tenant/**
  - backend/src/main/resources/db/changelog/**
  - backend/Dockerfile
  - frontend/Dockerfile
  - frontend/Caddyfile
  - docker-compose*.yml
  - infra/**
  - .github/workflows/**
  - .env.example
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: platform

**Owner:** Trần Trung Hiếu

## Description
The tenant layer of the multi-tenant pool model (ADR-0001, GBR-37) and the infrastructure the whole system runs on
(SRS_Delta D4). One deployment and one database serve every Workspace: every record carries its `workspace_id`,
the Workspace of a request comes only from the Workspace site's host name, the verified `cloudId` of the Forge
Invocation Token, or the job's Workspace, and isolation is enforced by the resolvers, Hibernate `@TenantId` and
PostgreSQL row-level security. The module also holds the Workspace registry (the platform record of each
Workspace and its Jira sites), file storage per Workspace, and the deployment: Docker Compose, Caddy wildcard
routing by host, CI/CD, the application VPS and the AI VPS (D41), and the demo seed (D42). The backend packages
`platform` and `common/tenant` are *proposed*; the backend lead confirms them when the module is planned
(KAN-196).

## Responsibilities
- `TenantContext`, the base entity (`@MappedSuperclass`, `workspace_id` as `@TenantId`) and the Hibernate tenant
  resolver, in `common/tenant/` (KAN-98, KAN-99).
- Row-level security on every tenant table, set per transaction from `TenantContext` (KAN-100).
- Tenant Resolver for the web app: Workspace from the host `{site name}.tsolve.io.vn`; unknown site name → MSG119;
  Suspended → MSG120 (KAN-99).
- Tenant Resolver for the Jira App: Workspace from the `cloudId` of the verified Forge Invocation Token on `api.`;
  unregistered Jira site → MSG124; Suspended → MSG121 (KAN-101, KAN-105, GBR-14, GBR-19).
- Platform hosts `login.`, `admin.`, `api.` have no Workspace of their own; the signed state value that carries the
  Workspace to `login.` (D9) is created and checked with this module's support.
- Workspace registry: site name, Google domain, status (Active / Suspended), Jira site URLs and their `cloudId`;
  platform tables without `workspace_id`.
- File storage per Workspace: `/{workspaceId}/…`, no path traversal, size limit, upload / read / delete (KAN-96).
- Liquibase base and the base changesets of the tenant layer (KAN-98).
- Infrastructure: Docker Compose with PostgreSQL 16 + pgvector, Ollama and the worker (KAN-94); Caddy wildcard
  `*.tsolve.io.vn` and routing by host (KAN-95); staging and CI/CD (KAN-200); production, backup and restore
  (KAN-201); monitoring (KAN-202); the AI VPS for Ollama, reachable only privately from the application VPS
  (KAN-203, D41).
- Demo seed of two Workspaces under `infra/seed/`, run on dev and staging only, never from the production
  configuration (KAN-97, D42, GBR-24).
- The tenant-isolation acceptance criterion (an Account of Workspace A cannot read Workspace B), run by KAN-186
  (ADR-0001).

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| TenantContext | Java (`common/tenant`) | The Workspace of the current request or job; read it, or require it and fail when there is none |
| Base entity | Java `@MappedSuperclass` | `workspace_id` as `@TenantId`; every tenant entity extends it |
| Tenant Resolver | Servlet filter / Forge token check | Sets `TenantContext` from host or verified `cloudId`; MSG119, MSG120, MSG121, MSG124 |
| Workspace registry | Service interface | Find by site name or `cloudId`; status, Google domain; create, suspend, reactivate; bind a Jira site's `cloudId` |
| File storage | Service interface | Upload, read, delete a file of the current Workspace |
| Deployment | Compose, Caddy, CI | Hosts `{site name}.`, `admin.`, `login.`, `api.`; `OLLAMA_BASE_URL` to the AI VPS |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| `common/` (base exceptions) | Java | Errors with an MSG code (`backend/AGENTS.md` §1; not a module dependency) |

## Target Repos
- default: backend/ (`platform`, `common/tenant`, proposed), the repo root (`docker-compose*.yml`, `infra/`,
  `.github/workflows/`, `.env.example`), the web app image files.

## Related Artifacts
- Spec: `artifacts/03-modules/platform/spec.md` (written when the module is planned, KAN-196)
- Architecture: `artifacts/03-modules/architecture.md` (Multi-Tenancy, Deployment Topology)
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-94, KAN-95, KAN-96, KAN-97, KAN-98, KAN-99, KAN-100, KAN-101, KAN-105, KAN-200, KAN-201, KAN-202, KAN-203
