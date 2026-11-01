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
  - backend/src/main/resources/application*.yml
  - backend/pom.xml
  - backend/Dockerfile
  - backend/.dockerignore
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
The tenant layer of the multi-tenant model and the infrastructure the whole system runs on. One deployment serves
every Workspace, and **each Workspace has its own PostgreSQL database** and its own file storage (ADR-0002, GBR-37;
decided 29/10, replacing the shared database with `workspace_id` and row-level security of ADR-0001). A catalog
database holds only what is needed to find a Workspace: site name, status, database connection, the Jira site and
its `cloudId`, and the emails of its Accounts (GBR-16). The Workspace of a request comes only from the Workspace
site's host name, the verified `cloudId` of the Forge Invocation Token, or the job's Workspace; binding it routes
every query to that Workspace's database. Workspaces are created, suspended and reactivated by the operations team
with an ops script, not in the application (GBR-24, GBR-38, KAN-240; no Platform Console since 31/10). The module
also holds file storage per Workspace and the deployment: Docker Compose, Caddy wildcard routing by host, CI/CD, the
application VPS and the AI VPS (D41), and the demo seed (D42). The backend packages `platform` and `common/tenant`
are *proposed*.

## Responsibilities
- `TenantContext` and the routing `DataSource` that sends every query to the bound Workspace's database, with a
  lazily opened, bounded pool per Workspace, in `common/tenant/` (KAN-98, KAN-99).
- The catalog database and its changelog; the tenant-database convention every module's tables follow (no
  `workspace_id`) and its guard test (KAN-98).
- Tenant Resolver for the web app: Workspace from the host `{site name}.tsolve.io.vn`; unknown site → MSG151;
  Suspended → MSG150 (KAN-99, GBR-38).
- Forge Invocation Token check and the Tenant Resolver for the Jira App: Workspace from the verified `cloudId` on
  `api.`; Jira site linked to no Workspace → MSG152 (KAN-101, KAN-105, GBR-14, GBR-19).
- `login.` and `api.` have no Workspace of their own; the signed state value that carries the Workspace to `login.`
  (D9) is created and checked with this module's support.
- Catalog service: find a Workspace; link its Jira site when the Admin enters it at UC-10 (MSG153, GBR-24); keep
  each email in one Workspace (MSG155, GBR-16).
- Ops script `infra/ops/workspace.sh`: create a Workspace (database, runtime user, migrations, catalog row, first
  Admin Account — all or nothing), suspend and reactivate it (KAN-240, GBR-24, GBR-38).
- File storage per Workspace: `/{workspaceId}/…`, no path traversal, size limit, upload / read / delete (KAN-96).
- Migrations: the catalog changelog, then the tenant changelog on every Workspace database, at startup and in the
  deploy.
- Infrastructure: Docker Compose with PostgreSQL 16 + pgvector (catalog and Workspace databases), Ollama and the
  worker (KAN-94); Caddy wildcard `*.tsolve.io.vn` and routing by host (KAN-95); staging and CI/CD (KAN-200,
  subtasks KAN-1…14); production, backup and restore of each Workspace database (KAN-201, KAN-15…18, KAN-21);
  monitoring (KAN-202, KAN-7, KAN-19, KAN-20); the AI VPS for Ollama, private link only and no Internet egress
  (KAN-203, D41).
- Demo seed of two Workspaces under `infra/seed/`, run on dev and staging only, never from the production
  configuration (KAN-97, KAN-46, D42, GBR-24).
- The tenant-isolation acceptance criterion (an Account of Workspace A cannot read Workspace B), run by KAN-186.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| TenantContext | Java (`common/tenant`) | The Workspace of the current request or job; read it, or require it and fail when there is none |
| Tenant DataSource | Java (`common/tenant`) | The application's DataSource; every connection goes to the bound Workspace's database |
| Tenant Resolver | Servlet filter / Forge token check | Sets `TenantContext` from host or verified `cloudId`; MSG150, MSG151, MSG152 |
| WorkspaceCatalog | Service interface | Find by id, site name or `cloudId`; link the Jira site (UC-10); claim an email (UC-05) |
| Workspace events | Application events | `WorkspaceSuspended`, `WorkspaceReactivated` |
| Ops script | Shell + backend `ops` profile | Create, suspend, reactivate a Workspace |
| File storage | Service interface | Upload, read, delete a file of the current Workspace |
| Deployment | Compose, Caddy, CI | Hosts `{site name}.`, `login.`, `api.`; `OLLAMA_BASE_URL` to the AI VPS |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| `common/` (base exceptions) | Java | Errors with an MSG code (`backend/AGENTS.md` §1; not a module dependency) |
| identity | `FirstAdminCreator` | Creates the first Admin Account inside a new Workspace (ops script) |

## Target Repos
- default: backend/ (`platform`, `common/tenant`, proposed), the repo root (`docker-compose*.yml`, `infra/`,
  `.github/workflows/`, `.env.example`), the web app image files.
- Shared files that `be-foundation` owns and `platform` also changes (25/10, KAN-196 plan decision P2):
  `backend/pom.xml`, `backend/src/main/resources/application*.yml` and the Liquibase changelogs;
  `backend/.dockerignore` comes with the backend image (KAN-9).
- Design and tasks: `artifacts/04-plans/platform/design.md`, `iteration-1/` and `iteration-2/` — written for the
  pool model on 25/10; re-plan them from the spec of 01/11 before building.

## Related Artifacts
- Spec: `artifacts/03-modules/platform/spec.md` (database per Workspace, 01/11; needs re-approval)
- Architecture: `artifacts/03-modules/architecture.md` (Multi-Tenancy, Deployment Topology)
- ADR: `docs/adr/0002-database-per-workspace.md` (supersedes ADR-0001 of the Report3 baseline)
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-94, KAN-95, KAN-96, KAN-97, KAN-98, KAN-99, KAN-101, KAN-105, KAN-200, KAN-201, KAN-202, KAN-203,
  KAN-240 (KAN-100 cancelled); DevOps subtasks KAN-1…21; sample data KAN-46
