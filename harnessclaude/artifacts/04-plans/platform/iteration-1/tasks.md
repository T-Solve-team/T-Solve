---
module: platform
date: 2026-10-25
total_tasks: 12
owner: Dev
iteration: 1
---

# Tasks: platform — iteration 1 (to 30/10)

Spec: `artifacts/03-modules/platform/spec.md` · Design: `artifacts/04-plans/platform/iteration-1/design.md`
(master: `artifacts/04-plans/platform/design.md`).

Task numbers are shared with iteration 2 (Tasks 6, 7, 14, 16 are there). Backend tests name the criterion:
`…_AC_002_13`; infrastructure checks are scripts in `infra/tests/*.test.sh` whose log lines start with the AC id.
"Edge #n" refers to the Edge Cases table of the design.

**Blocker for Tasks 2–5 (decision P1):** the changeset that reshapes `workspace` into `workspaces` (UUID `id`,
`workspace_id` of `resolved_ticket_snapshot` and `solution` to UUID, the 4 rows deleted). Trần Hải Anh, owner of
changeset 001, decides who writes it; platform offers to draft it. Task 1 and Tasks 8–13 do not wait.

---

## Task 1: TenantContext and runIn

**Story:** STORY-002
**Feature:** tenant-context
**Assignee:** Trần Trung Hiếu (KAN-99)
**Description:** `TenantContext` interface, `ThreadLocalTenantContext` with package-private `bind`/`unbind`,
`TenantBinding` static holder, `NoTenantException` (answered 500 MSG06 through be-foundation's handler), `runIn` with
`TransactionTemplate(PROPAGATION_REQUIRES_NEW)`, restore in `finally`. Covers AC-002.1, AC-002.11 (holder side),
AC-002.12 – AC-002.16. Tests: different Workspace refused (Edge #4), unknown id refused, same Workspace allowed,
restore after an exception, Suspended allowed (Edge #40), binding gone after an exception on a one-thread pool
(Edge #2). Unknown-id check uses a small `WorkspaceExistence` port that Task 4 implements; until then a test double.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/tenant/TenantContext.java`
- Create: `backend/src/main/java/vn/tsolve/common/tenant/ThreadLocalTenantContext.java`
- Create: `backend/src/main/java/vn/tsolve/common/tenant/TenantBinding.java`
- Create: `backend/src/main/java/vn/tsolve/common/tenant/WorkspaceExistence.java`
- Create: `backend/src/main/java/vn/tsolve/common/tenant/NoTenantException.java`
- Test: `backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java`
**Dependencies:** none
**Estimated size:** small

---

## Task 2: Tenant schema, base entity and the convention guard

**Story:** STORY-001
**Feature:** tenant-schema
**Assignee:** Trần Trung Hiếu (KAN-98)
**Description:**
- The `workspaces` reshape changeset (author per P1) and `NNN-platform-jira-sites.sql` (design, Data Model).
- `TenantEntity` with `@TenantId workspace_id` and the `@PrePersist` callback (throws when none bound, otherwise
  sets the bound id); `WorkspaceTenantResolver` (placeholder UUID when none bound) and `TenantHibernateConfig`.
- The guard's rules of the design: join tables by structure, append-only tables by `COMMENT ON TABLE …
  'tsolve:append-only'`, a unique constraint led by `workspace_id` counts as the index, policy compared after
  normalisation. The test-only table `isolation_probes` for Tasks 3 and 5 is created by the tests, not the changelog.
- `PostgresTestBase`: Testcontainers `pgvector/pgvector:pg16`, mounts `infra/postgres/init/10-tsolve-roles.sh`,
  Liquibase as `<db>_owner`, application data source as `<db>_app`.
- `infra/postgres/init/10-tsolve-roles.sh` (owner + app roles, default privileges, `vector` extension; decision P6).
- `ConventionGuardIT` with `PLATFORM_ALLOW_LIST` and the fixed `LEGACY_ALLOW_LIST`.
- Add Testcontainers to `backend/pom.xml` if be-foundation has not yet (same artifacts and BOM versions).

Covers AC-001.1 – AC-001.14. Tests: empty `workspaces`/`jira_sites` after the changelog (AC-001.3); entity saved with a
foreign `workspaceId` gets the bound one (Edge #5); save with none bound throws (AC-001.7); guard fails on a scratch
table with RLS not forced or a wrong index (Edge #27) and on a table added to the legacy list (Edge #28); a legacy
table that meets AC-001.8 fails the guard (AC-001.13); a sample tenant table with `id` defaulted by SQL and by JPA
(AC-001.14).
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/NNN-platform-workspaces.sql` (if platform drafts it, P1)
- Create: `backend/src/main/resources/db/changelog/changes/NNN-platform-jira-sites.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `backend/src/main/java/vn/tsolve/common/tenant/TenantEntity.java`
- Create: `backend/src/main/java/vn/tsolve/common/tenant/WorkspaceTenantResolver.java`
- Create: `backend/src/main/java/vn/tsolve/common/tenant/TenantHibernateConfig.java`
- Create: `infra/postgres/init/10-tsolve-roles.sh`
- Modify: `backend/pom.xml` (Testcontainers, only if missing)
- Test: `backend/src/test/java/vn/tsolve/support/PostgresTestBase.java`
- Test: `backend/src/test/java/vn/tsolve/common/tenant/TenantEntityIT.java`
- Test: `backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java`
**Dependencies:** Task 1; the reshape changeset decision (P1)
**Estimated size:** medium

---

## Task 3: Row-level security

**Story:** STORY-003
**Feature:** row-level-security
**Assignee:** Trần Hải Anh (KAN-100)
**Description:** `TenantAwareJpaTransactionManager` (`set_config('app.workspace_id', ?, true)` in `doBegin`) and
`TenantTransactionConfig`; the policy template applied to platform's test table; `spring.liquibase.user/password` =
`DB_MIGRATION_USER`/`DB_MIGRATION_PASSWORD` in `application.yml`. Covers AC-003.1 – AC-003.8. Tests as the
application user: no rows and refused writes with none bound (AC-003.3); native query sees only A (AC-003.4); native
insert with B refused (Edge #6); `DISABLE ROW LEVEL SECURITY` and `DROP POLICY` refused (Edge #7); with a pool of one
connection, a transaction for A then one with none bound sees no rows (Edge #3).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/tenant/TenantAwareJpaTransactionManager.java`
- Create: `backend/src/main/java/vn/tsolve/common/tenant/TenantTransactionConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/vn/tsolve/common/tenant/RowLevelSecurityIT.java`
**Dependencies:** Task 2
**Estimated size:** medium

---

## Task 4: Workspace registry

**Story:** STORY-004
**Feature:** workspace-registry
**Assignee:** Trần Trung Hiếu (KAN-98, used by KAN-113…116)
**Description:** `Workspace`, `JiraSite`, `WorkspaceStatus`; repositories; `WorkspaceRegistryService` and its impl
with the rules table of the design (trimmed inputs; blank site name or Google domain → MSG01; reserved names
`admin`, `login`, `api`, `www`, `staging` → MSG139 (D44, Edge #44); user info or port in a site URL → MSG136; first
failure in field order), constraint-name mapping for races, after-commit
events, `list` paging; `WorkspaceMapper`; implements `WorkspaceExistence` (Task 1). Covers AC-004.1 – AC-004.16.
Tests: each rule and its code and field (Edge #12); concurrent creation → one MSG125 (Edge #11); suspend twice → one
event (Edge #14); events only after commit (rolled-back transaction publishes nothing); `findById`, `findBySiteName` and `findByCloudId` return empty for an unknown key (AC-004.13); an unknown id →
`NotFoundException` MSG06 for `suspend` and `reactivate` only; `list` empty and paging (Edge #42); a new Workspace resolves on the next request and a suspended one is refused
on the next request (AC-004.9, AC-004.10, with Task 5's filter).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/entity/Workspace.java`, `JiraSite.java`, `WorkspaceStatus.java`
- Create: `backend/src/main/java/vn/tsolve/platform/repository/WorkspaceRepository.java`, `JiraSiteRepository.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/WorkspaceRegistryService.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/impl/WorkspaceRegistryServiceImpl.java`
- Create: `backend/src/main/java/vn/tsolve/platform/dto/CreateWorkspaceRequest.java`, `WorkspaceResponse.java`,
  `JiraSiteResponse.java`, `WorkspaceSuspended.java`, `WorkspaceReactivated.java`
- Create: `backend/src/main/java/vn/tsolve/platform/mapper/WorkspaceMapper.java`
- Create: `backend/src/main/java/vn/tsolve/platform/exception/WorkspaceRuleException.java`
- Test: `backend/src/test/java/vn/tsolve/platform/WorkspaceRegistryServiceImplTest.java`
- Test: `backend/src/test/java/vn/tsolve/platform/WorkspaceRegistryIT.java`
**Dependencies:** Task 2; be-foundation Task 1 (`BusinessRuleException` with a code and a field)
**Estimated size:** medium

---

## Task 5: Tenant Resolver of the web app and the isolation test

**Story:** STORY-002
**Feature:** web-tenant-resolver
**Assignee:** Trần Trung Hiếu (KAN-99)
**Description:** `PlatformProperties` (`tsolve.base-domain`, `tsolve.environment`), `HostClassifier`/`HostKind`,
`TenantResolverFilter` and `TenantFilterConfig` (order before Spring Security), `ProblemResponses`
(`application/problem+json`, `code`, `params`); `/actuator/health`, `/v3/api-docs*` and the Swagger UI pass on any
host (AC-002.19); `/api/v1/jira-app/**` on a non-`api.`
host and non-Jira paths on `api.` → 404 MSG06 (the host part of AC-005.7 and AC-005.8). Covers AC-002.2 – AC-002.11,
AC-002.17 – AC-002.19. Tests: forwarded headers and Workspace ids in requests ignored (Edge #1); MSG119 before 401
(Edge #8); host variants (Edge #9); `www.` and `staging.` + base domain → MSG119 with no registry lookup (D44,
Edge #44); health on `localhost:8080` (Edge #10); unbound after an exception (Edge #2);
refused-request logs carry no token or cookie (Edge #41); `TenantIsolationIT` with Workspaces A and B through the
filter (JPA and native).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/config/PlatformProperties.java`, `TenantFilterConfig.java`
- Create: `backend/src/main/java/vn/tsolve/platform/web/HostClassifier.java`, `HostKind.java`,
  `TenantResolverFilter.java`, `ProblemResponses.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `.env.example` (`TSOLVE_BASE_DOMAIN`, `TSOLVE_ENVIRONMENT`)
- Test: `backend/src/test/java/vn/tsolve/platform/HostClassifierTest.java`
- Test: `backend/src/test/java/vn/tsolve/platform/TenantResolverFilterTest.java`
- Test: `backend/src/test/java/vn/tsolve/common/tenant/TenantIsolationIT.java`
**Dependencies:** Tasks 3, 4
**Estimated size:** medium

---

## Task 8: File storage per Workspace

**Story:** STORY-007
**Feature:** file-storage
**Assignee:** Đặng Nhật Minh (KAN-96)
**Description:** `FileStorage`, `LocalFileStorage` (layout of the design: temp files in `<root>/.tmp/` outside every
Workspace folder, moved in place; stale `.part` files removed at startup), `StoredFile`, `FileTooLargeException`,
`InvalidFileKeyException` (extends `NotFoundException`, 404 MSG06); `tsolve.storage.root` / `max-bytes` (`FILE_STORAGE_ROOT`,
`FILE_STORAGE_MAX_BYTES` default 10485760). Covers AC-007.1 – AC-007.11. Tests: traversal keys and a planted symlink
(Edge #24); 10 MB / +1 byte / 0 bytes (Edge #25); write failure leaves nothing (Edge #26); B's key while A is bound →
`NotFoundException`; none bound → throws; delete twice succeeds; isolation case AC-007.11.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/service/FileStorage.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/impl/LocalFileStorage.java`
- Create: `backend/src/main/java/vn/tsolve/platform/dto/StoredFile.java`
- Create: `backend/src/main/java/vn/tsolve/platform/exception/FileTooLargeException.java`, `InvalidFileKeyException.java`
- Modify: `backend/src/main/resources/application.yml`, `.env.example`
- Test: `backend/src/test/java/vn/tsolve/platform/LocalFileStorageTest.java`
**Dependencies:** Task 1
**Estimated size:** small

---

## Task 9: Local Docker Compose for the whole stack

**Story:** STORY-008
**Feature:** local-compose
**Assignee:** Trần Trung Hiếu (KAN-94)
**Description:** Root `docker-compose.yml`: `db` (pgvector:pg16 + `infra/postgres/init`), `ollama` + one-shot
`ollama-pull` (`bge-m3` and `OLLAMA_CHAT_MODEL`), `backend`, `worker` (same image, `SPRING_PROFILES_ACTIVE=worker`),
`web`, `caddy` (`infra/caddy/Caddyfile.local`: `*.localhost`, `admin.localhost`, `login.localhost`, `api.localhost`,
same routing as Task 12); health checks; every credential from `.env`. `application-worker.yml` turns Liquibase off.
`.env.example` complete; `infra/README.md` lists variables and the start command. Covers AC-008.1 – AC-008.8
(AC-008.8 checked again in Task 11). Checks: `infra/tests/local-stack.test.sh` (all healthy; a seeded or created site
resolves on `<site>.localhost`; backend reaches Ollama); missing variable refuses start (Edge #29).
**Files:**
- Modify: `docker-compose.yml`
- Create: `infra/caddy/Caddyfile.local`
- Create: `backend/src/main/resources/application-worker.yml`
- Modify: `.env.example`
- Create: `infra/README.md`
- Test: `infra/tests/local-stack.test.sh`
**Dependencies:** Task 2 (init script); Task 10 (backend Dockerfile)
**Estimated size:** medium

---

## Task 10: CI for the backend and images on GHCR

**Story:** STORY-010
**Feature:** ci-images
**Assignee:** Trần Trung Hiếu (KAN-6, KAN-8, KAN-9)
**Description:** `backend` job in `ci.yml` (Temurin 21, Maven cache, `mvn -B verify` with Testcontainers; path filter
`backend/**`, `infra/postgres/**`). `backend/Dockerfile` + `.dockerignore` (Maven build stage, Java 21 JRE, non-root,
`HEALTHCHECK` on `/actuator/health`, `-XX:MaxRAMPercentage=75`). `images.yml` on `main`: build and push
`tsolve-backend`, `tsolve-frontend`, `tsolve-caddy` (when `infra/caddy/**` changes) to `ghcr.io/<owner>` with
`sha-<commit>` and `staging`. Manual after merge (Hiếu): mark frontend, backend, PR title and gitleaks as required
checks. Covers AC-010.1, AC-010.2.
**Files:**
- Modify: `.github/workflows/ci.yml`
- Create: `.github/workflows/images.yml`
- Create: `backend/Dockerfile`, `backend/.dockerignore`
**Dependencies:** none
**Estimated size:** medium

---

## Task 11: Application VPS and the staging stack

**Story:** STORY-010
**Feature:** staging-servers
**Assignee:** Trần Trung Hiếu (KAN-10, KAN-11, KAN-12)
**Description:** Manual first (Hiếu): buy the application VPS (KVM, Ubuntu LTS, snapshots). `infra/scripts/bootstrap.sh`
(deploy user, key-only SSH, ufw 22/80/443, unattended upgrades, Docker + Compose, swap, log rotation, `/srv/tsolve`
layout; `--role ai` variant for Task 14). `infra/compose.edge.yml` (PostgreSQL with `TSOLVE_DATABASES=tsolve_staging`,
private network only, no published port; Caddy placeholder until Task 12) and `infra/compose.staging.yml` (backend,
worker, web by `:staging` tag, `env_file /srv/tsolve/staging/.env`, files at `/srv/tsolve/staging/files`, memory and
CPU limits, health checks, `restart: unless-stopped`, **no `ports:`**, no Ollama). Covers AC-010.7 – AC-010.10,
AC-008.8. Checks run by `infra-checks.yml`: `infra/tests/ports.test.sh` from a GitHub-hosted runner (only 22/80/443
open; Edge #34) and `infra/tests/server.test.sh` over SSH (password login refused, `.env` mode 600 and owner, each
database user reaches only its own database, no container publishes a port except Caddy).
**Files:**
- Create: `infra/scripts/bootstrap.sh`
- Create: `infra/compose.edge.yml`, `infra/compose.staging.yml`
- Create: `.github/workflows/infra-checks.yml`
- Test: `infra/tests/ports.test.sh`, `infra/tests/server.test.sh`
- Modify: `infra/README.md` (runbook)
**Dependencies:** Task 2 (init script), Task 10
**Estimated size:** medium

---

## Task 12: Main Caddy, Cloudflare DNS and wildcard certificates

**Story:** STORY-009
**Feature:** edge-caddy
**Assignee:** Trần Trung Hiếu (KAN-13, KAN-95)
**Description:** Manual prerequisites (whoever controls `tsolve.io.vn`): move the nameservers to Cloudflare (free
plan); create DNS-only records `tsolve.io.vn`, `*.tsolve.io.vn`, `*.staging.tsolve.io.vn` → application VPS; create an
API token with Zone:DNS:Edit + Zone:Read on that zone only and put it in `/srv/tsolve/edge/.env` as
`CLOUDFLARE_API_TOKEN`. Then: `infra/caddy/Dockerfile` (xcaddy + `caddy-dns/cloudflare`, pinned); `infra/caddy/Caddyfile`
with DNS-01 wildcards, a shared routing snippet per base domain (Workspace sites `/api/*` → backend, else web;
`admin.` same; `login.` `/api/*` only; `api.` `/api/v1/jira-app/*` and `/actuator/health` only; apex and `www.` →
404), `Host` passed unchanged, HTTP → HTTPS, HSTS, `lb_try_duration` retry window, staging `basic_auth` (hash from the
edge `.env`) on sites, `admin.`, `login.` but not `api.`. Covers AC-009.1 – AC-009.10 for staging (production hosts in
KAN-201). Checks: `infra/tests/caddy-routing.test.sh` (each host and path; a Workspace created after the deploy gets
HTTPS; Edge #31); runbook for token rotation (Edge #30).
**Files:**
- Create: `infra/caddy/Dockerfile`, `infra/caddy/Caddyfile`
- Modify: `infra/compose.edge.yml`
- Test: `infra/tests/caddy-routing.test.sh`
- Modify: `infra/README.md` (DNS table, token rotation)
**Dependencies:** Task 11; manual DNS move
**Estimated size:** medium

---

## Task 13: Deploy staging on merge, with the smoke test

**Story:** STORY-010
**Feature:** staging-cd
**Assignee:** Trần Trung Hiếu (KAN-14, KAN-200)
**Description:** `infra/scripts/install-runner.sh` (deploy-only runner, non-root service, CPU/RAM capped, label
`tsolve-deploy`, runner group limited to `cd-staging.yml`). `cd-staging.yml` triggered by `workflow_run` of
`images.yml` on `main` only: pull → `migrate` profile → `up -d` → wait for health → `seed` profile (Task 15) →
`infra/tests/smoke.test.sh` (`api.staging…/actuator/health` UP; the seed site's page 200 with basic auth; its
`/api/v1/me` → 401 MSG07; basic-auth credential from the GitHub secret `STAGING_BASIC_AUTH`).
`application-migrate.yml` + `MigrateRunner`. Covers AC-010.3 – AC-010.6. Tests: a failing
migration stops before `up` (Edge #33); a failing seed step fails the workflow and names the step, and the seed runs
before the smoke test (AC-010.3, AC-010.5); no trigger from pull requests (Edge #32).
**Files:**
- Create: `infra/scripts/install-runner.sh`
- Create: `.github/workflows/cd-staging.yml`
- Create: `backend/src/main/resources/application-migrate.yml`
- Create: `backend/src/main/java/vn/tsolve/platform/seed/MigrateRunner.java`
- Test: `infra/tests/smoke.test.sh`
- Test: `backend/src/test/java/vn/tsolve/platform/MigrateRunnerTest.java`
**Dependencies:** Tasks 5, 10, 11, 12, 15; be-foundation Task 2 (401 MSG07 on `/api/**` without a session)
**Estimated size:** medium

---

## Task 15: Demo seed — the two Workspaces (S3 data later)

**Story:** STORY-012
**Feature:** demo-seed-workspaces
**Assignee:** Trần Trung Hiếu (KAN-97, moved forward by decision P7)
**Description:** `SeedRunner` (`seed` profile, `application-seed.yml` without a web server) reads
`infra/seed/demo-workspaces.json` (`TSOLVE_SEED_FILE`), refuses unless `TSOLVE_ENVIRONMENT` is `dev` or `staging`,
creates each Workspace through `WorkspaceRegistryService.create`, skips existing site names, exits non-zero on a rule
failure naming the Workspace. `DemoSeedContributor` interface and `SeedWorkspace` exist but no contributor is called
yet (Task 16). `infra/seed/run-seed.sh`. Covers AC-012.1, AC-012.3 – AC-012.5, and AC-012.6 for registry failures.
Tests: production or unset → non-zero, nothing written (Edge #37); second run skips (Edge #38; the reset for S3 is
Task 16); the production configuration (AC-012.4: `infra/compose.prod.yml` when it exists, its `.env`
template and the production workflow) has no seed reference.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/seed/SeedRunner.java`, `SeedData.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/DemoSeedContributor.java`
- Create: `backend/src/main/java/vn/tsolve/platform/dto/SeedWorkspace.java`
- Create: `backend/src/main/resources/application-seed.yml`
- Create: `infra/seed/demo-workspaces.json`, `infra/seed/run-seed.sh`
- Test: `backend/src/test/java/vn/tsolve/platform/SeedRunnerTest.java`
**Dependencies:** Task 4
**Estimated size:** small
