---
module: platform
date: 2026-10-25
---

# Design: platform

Spec: `artifacts/03-modules/platform/spec.md` (approved as 99bf1d6c; changed on 25/10 by decisions X2 and U3 and by
the builder check and the user's answers on AC-010.6 and AC-012.5; waiting for re-approval). Architecture: `artifacts/03-modules/architecture.md`. Code follows `backend/AGENTS.md`.

## Architecture Overview

`platform` has two halves: the **tenant layer** in the backend (`common/tenant/` + `platform/`) and the
**infrastructure** (`infra/`, compose files, workflows). Every other module sees the tenant layer through
`TenantContext`, `TenantEntity`, `WorkspaceRegistryService`, `ForgeCallContext`, `FileStorage` and
`DemoSeedContributor` (spec, Interface Contracts).

### How a request or job gets its Workspace

| Entry | Path through the code | Workspace |
|-------|----------------------|-----------|
| Workspace site `<site>.<base>` `/api/**` | `TenantResolverFilter` (servlet filter, before Spring Security) → `HostClassifier` → `WorkspaceRegistryService.findBySiteName` → bind → security chain of `be-foundation` / `identity` → handler → unbind in `finally` | from the host (AC-002.2) |
| `api.<base>` `/api/v1/jira-app/**` | `TenantResolverFilter` (host check only) → `ForgeSecurityConfig` chain (ordered first, matches the `api.` host) → `ForgeTokenFilter` → `ForgeTokenVerifier` → `findByCloudId` → bind → handler → unbind | from the verified `cloudId` (AC-006.1) |
| `api.<base>` `POST /api/v1/jira-app/lifecycle` | as above, but `ForgeTokenFilter` skips the Workspace lookup → `ForgeLifecycleController` → `JiraSiteMatcher` | none (AC-006.6) |
| `login.<base>`, `admin.<base>` | `TenantResolverFilter` binds nothing → `identity` / `platform-console` chains | none; `runIn` where the spec allows it |
| Job, `login.` callback, Platform Console count, seed | `TenantContext.runIn(workspaceId, action)` → new transaction → action | the given Workspace (AC-002.12) |
| any host `/actuator/health`, `/v3/api-docs*`, Swagger UI | passes every host check (AC-002.19; Docker health checks and `npm run api:pull` call `localhost:8080`) | none |

### The binding

- `ThreadLocalTenantContext` (bean implementing `TenantContext`) keeps the bound Workspace id in a static
  `ThreadLocal`, so that Hibernate's resolver and the entity listener, which are not Spring beans, read the same
  value.
- Resolvers bind with a package-private `bind`/`unbind` pair that is not part of the interface; modules can bind only
  through `runIn`.
- `runIn` refuses a different Workspace when one is bound (AC-002.13), refuses an unknown id (AC-002.14, one
  `existsById` on `workspaces`), binds, runs the action inside `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`
  (decision P5), and restores the previous binding in `finally` (AC-002.12).

### Hibernate and row-level security

- `WorkspaceTenantResolver implements CurrentTenantIdentifierResolver<UUID>` returns the bound id, or the
  placeholder `00000000-0000-0000-0000-000000000000` when none is bound (no Workspace has it, so a tenant query
  returns no rows). `validateExistingCurrentSessions()` is `true`, so a session opened for one Workspace is never
  reused for another. Registered with a `HibernatePropertiesCustomizer` (`hibernate.tenant_identifier_resolver`).
- `TenantEntity` has a `@PrePersist` callback that throws `NoTenantException` when no Workspace is bound
  (AC-001.7) and otherwise sets `workspaceId` to the bound id. It runs before Hibernate checks the `@TenantId` value
  at insert, so a value the caller set (only possible by reflection: the field has no setter) is replaced instead of
  making Hibernate throw on a mismatch (AC-001.5). `@TenantId` then filters reads (AC-001.6).
- Tenant data is read and written only inside transactions: `spring.jpa.open-in-view` is `false`, Spring Data
  repositories open their own transaction, and JDBC outside a transaction sees no rows (fail closed, AC-003.3).
- `TenantAwareJpaTransactionManager extends JpaTransactionManager` overrides `doBegin`: after the transaction
  starts, when a Workspace is bound, it runs `SELECT set_config('app.workspace_id', ?, true)` on the transaction's
  connection (decision P4). `true` makes the value local to the transaction, so a pooled connection never carries it
  further (AC-003.2). JDBC code in the same transaction shares the connection (`HibernateJpaDialect`).
- The policy reads `current_setting('app.workspace_id', true)`; unset → NULL → no row matches, writes fail the
  `WITH CHECK` (AC-003.3).

### Security chains (order)

1. `TenantResolverFilter` — `FilterRegistrationBean`, order `SecurityProperties.DEFAULT_FILTER_ORDER - 10`, so it runs
   before Spring Security's `DelegatingFilterProxy` (AC-002.8).
2. `ForgeSecurityConfig.forgeChain` — `@Order(1)`, `securityMatcher` = host is `api.<base>`; stateless, no CSRF, no
   session cookie (AC-005.6); `/actuator/health` permitted, `/api/v1/jira-app/**` authenticated by
   `ForgeTokenFilter`, everything else denied. Because it matches first, `be-foundation`'s and `identity`'s chains
   never see an `api.` request, so they need no change.
3. `identity`'s chain for `/api/v1/auth/**`, then `be-foundation`'s session chain (their specs).

### Infrastructure

| Environment | Runs | Files |
|-------------|------|-------|
| Local | PostgreSQL 16 + pgvector, backend, worker, web app, Ollama (+ one-shot model pull), Caddy (HTTP, `*.localhost`) | `docker-compose.yml`, `infra/caddy/Caddyfile.local` |
| Application VPS — edge | Caddy (custom image with the Cloudflare DNS module, wildcard certificates by DNS-01), PostgreSQL (one instance, databases `tsolve_staging` and later `tsolve_prod`) | `infra/compose.edge.yml`, `infra/caddy/Caddyfile`, `infra/postgres/init/` |
| Application VPS — staging | backend, worker, web app (images `:staging`), seed and migrate as one-off runs | `infra/compose.staging.yml` |
| AI VPS | Ollama as a native systemd service bound to the WireGuard address (decision P11) | `infra/scripts/ollama-setup.sh`, `infra/scripts/wireguard-setup.sh` |

- DNS: the `tsolve.io.vn` zone moves to Cloudflare (free plan). Records `tsolve.io.vn`, `*.tsolve.io.vn`,
  `*.staging.tsolve.io.vn` → application VPS, DNS only (not proxied), so Caddy terminates TLS. The API token
  `CLOUDFLARE_API_TOKEN` has Zone:DNS:Edit and Zone:Read on that zone only and lives only in the edge `.env` on the
  server (decision Q12).
- Private link: WireGuard `wg0` between the two VPS (`10.66.0.1` application, `10.66.0.2` AI). Keys are generated
  on each host (`/etc/wireguard`, mode 600), never in the repository; `wg-quick@wg0` is enabled so the tunnel comes
  back after a reboot. On the AI VPS, ufw allows UDP 51820 only from the application VPS's public address and TCP
  11434 only from `10.66.0.1` on `wg0`; Ollama listens on `10.66.0.2:11434` only. `OLLAMA_BASE_URL` =
  `http://10.66.0.2:11434` (decision Q13). Containers on the application VPS reach it through the host's route and
  Docker's masquerading.
- Images: `tsolve-backend` (also worker, migrate and seed, by profile), `tsolve-frontend`, `tsolve-caddy`, built in
  CI and pushed to GHCR under the repository owner (`ghcr.io/<owner>/…`, tags `sha-<commit>` and `staging`)
  (decision P9).
- Deploy: the `cd-staging` workflow runs on a self-hosted runner on the application VPS (label `tsolve-deploy`,
  non-root `deploy` user, systemd service with CPU and memory limits): pull → migrate (profile `migrate`) → up →
  health → seed (profile `seed`, Workspaces only until S3, decision P7) → smoke test.

## Data Model

### Platform tables (no row-level security; on the platform allow-list)

```sql
--changeset <owner of changeset 001, see Key Decisions P1>:NNN-platform-workspaces
-- Reshape of the legacy `workspace` table; method chosen by its owner (spec AC-001.1, AC-001.3).
CREATE TABLE workspaces (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name   TEXT         NOT NULL,
    site_name      VARCHAR(63)  NOT NULL,
    google_domain  VARCHAR(253) NOT NULL,
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_workspaces_site_name UNIQUE (site_name),
    CONSTRAINT uq_workspaces_google_domain UNIQUE (google_domain),
    CONSTRAINT ck_workspaces_google_domain_lower CHECK (google_domain = lower(google_domain))
);

--changeset platform:NNN-platform-jira-sites
CREATE TABLE jira_sites (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES workspaces (id),
    site_url      VARCHAR(300) NOT NULL,
    cloud_id      VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_jira_sites_site_url UNIQUE (site_url),
    CONSTRAINT uq_jira_sites_cloud_id UNIQUE (cloud_id)
);
CREATE INDEX idx_jira_sites_workspace ON jira_sites (workspace_id);
```

`company_name` is `TEXT` because the baseline sets no length limit (AC-004.2 only requires a value); a limit added
later is a spec change with `MSG02` (Decided in planning). The legacy `resolved_ticket_snapshot` and `solution` keep a foreign key to `workspaces(id)`
after the reshape (their `workspace_id` becomes UUID).

### Tenant-table template (spec AC-001.8, AC-001.14, AC-003.1)

```sql
--changeset <module>:NNN-<slug>
CREATE TABLE things (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID        NOT NULL REFERENCES workspaces (id),
    name          VARCHAR(150) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),    -- omitted on an append-only table
    CONSTRAINT uq_things_name UNIQUE (workspace_id, name)
);
CREATE INDEX idx_things_workspace ON things (workspace_id);
ALTER TABLE things ENABLE ROW LEVEL SECURITY;
ALTER TABLE things FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON things
    USING (workspace_id = current_setting('app.workspace_id', true)::uuid)
    WITH CHECK (workspace_id = current_setting('app.workspace_id', true)::uuid);
-- Join table: PRIMARY KEY (workspace_id, left_id, right_id), no id column.
```

Entity side: `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;` and `extends TenantEntity`.
No changeset writes a GRANT (decision P6).

### Database roles (decision P6)

`infra/postgres/init/10-tsolve-roles.sh` runs once when the PostgreSQL volume is new, for each database in
`TSOLVE_DATABASES`:
- owner role (`<db>_owner`, LOGIN, owns the database and the `public` schema) — the Liquibase user;
- application role (`<db>_app`, LOGIN, NOSUPERUSER, NOBYPASSRLS, not owner);
- `ALTER DEFAULT PRIVILEGES FOR ROLE <db>_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO
  <db>_app` (and `USAGE, SELECT ON SEQUENCES`), so every future table is reachable without a GRANT in a changeset;
- `CREATE EXTENSION IF NOT EXISTS vector` (needs the superuser, so it is here, not in a changeset).

The same script initialises the local database and the Testcontainers database (mounted into
`/docker-entrypoint-initdb.d/`), so tests run as the application user exactly as production does.

### Convention guard test data

- `PLATFORM_ALLOW_LIST` (permanent, with reasons): `workspaces`, `jira_sites`, `databasechangelog`,
  `databasechangeloglock`, and the JobRunr tables (`jobrunr_%`, reason D43).
- `LEGACY_ALLOW_LIST` (fixed set, can only lose entries): `resolved_ticket_snapshot`, `solution` →
  `ticket-repository`; `solution_source` → `ticket-repository`; `solution_approval` → `ticket-review`;
  `reuse_record` → `search` (AC-001.11, as changed by X2/U3).
- The test reads `information_schema` and `pg_catalog` (`relrowsecurity`, `relforcerowsecurity`, `pg_policies`,
  constraints, indexes, column defaults) after applying the changelog to an otherwise empty database.
- Join table: recognised by structure — a composite primary key made of `workspace_id` and two `<entity>_id`
  columns, and no `id` column.
- Append-only table: declared in its changeset with `COMMENT ON TABLE <t> IS 'tsolve:append-only'`; only such a
  table may lack `updated_at`. Every module writing an append-only table adds this comment (owners told).
- A unique constraint whose first column is `workspace_id` counts as the required index. A tenant table may also
  have a platform-wide unique column (for example `identity`'s account email, D7).
- The policy must be named `tenant_isolation`, be permissive for all commands, and its `USING` and `WITH CHECK`
  expressions must equal those of the template after PostgreSQL's normalisation in `pg_policies`.
- The isolation and row-level security tests use a test-only table (`isolation_probes`) that the test creates after
  the changelog, following the template; it is never in the production changelog, and the guard test runs before it
  exists.

### Registry rules (AC-004.2 – AC-004.7)

| Field | Check, in order | Code |
|-------|-----------------|------|
| `companyName` | trimmed; blank → | `MSG01` |
| `siteName` | trimmed; missing or blank → ; `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$` fails (no lower-casing: `Acme` → MSG126) → ; in `{admin, login, api, www, staging}` (D44) → ; taken → | `MSG01`; `MSG126`; `MSG139`; `MSG125` |
| `googleDomain` | trimmed and lower-cased; missing or blank → ; not 2+ RFC 1123 labels with an alphabetic last label, 253 max → ; taken → | `MSG01`; `MSG127`; `MSG138` |
| `jiraSiteUrls` | empty list → ; each: `java.net.URI` with scheme `https`, a host, no user info, no port, path empty or `/`, no query, no fragment, else → ; normalised `https://<host>` (stored in that form, so the unique constraint sees case variants) already registered, or repeated in the request (the later index gets the error) → | `MSG01`; `MSG136`; `MSG137` |

Error bodies carry `code` and `params` as be-foundation AC-001.1 defines them; `params` is a JSON object, for
example `{"tracker_name": "Jira"}` for MSG124.

A race lost at the unique constraints (`DataIntegrityViolationException` on `saveAndFlush`) is mapped by constraint
name to the same code (AC-004.8).

### Storage layout (STORY-007)

`<FILE_STORAGE_ROOT>/<workspaceId>/<key>` with `key` = a random UUID v4 string; writes go to
`<FILE_STORAGE_ROOT>/.tmp/<uuid>.part` (outside every Workspace folder, same file system) and are moved into place
with `ATOMIC_MOVE`, so even a crash leaves nothing in a Workspace's folder (AC-007.8). At startup the storage deletes
`.part` files older than one hour. `InvalidFileKeyException` extends `NotFoundException` (404 `MSG06`), so a bad key
tells the caller no more than a missing one. Local: named
volume `tsolve-files`; servers: `/srv/tsolve/<env>/files`, mounted at the same path in the backend and the worker.

### Seed data (STORY-012)

`infra/seed/demo-workspaces.json`:
```json
{ "workspaces": [ { "companyName": "…", "siteName": "…", "googleDomain": "…",
                    "jiraSiteUrls": ["https://…"], "modules": { "identity": { }, "workspace": { } } } ] }
```
The runner gives each `DemoSeedContributor` the `modules.<module>` part, where `<module>` is the contributor's
package segment after `vn.tsolve.` (Decided in planning: keeps the spec's contract unchanged).

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Spec Q1–Q15 | As recorded in the spec (KAN-96 in scope; UUID keys and plural tables; outcome-only reshape; legacy tables fixed by their owners; `runIn`; `worker` pauses jobs; registry enforces UC-55; 401 without code, 403 MSG124 / MSG121; install matching by signed claims or tenant information; `api.` serves only the Jira App paths; `*.staging.tsolve.io.vn`; seed by contributors; apex and `www` 404) | see the spec's Changelog | User, 25/10 |
| Spec X2, U3 | Join tables keep a composite key with `workspace_id`; append-only tables have no `updated_at`; ids by `@GeneratedValue(UUID)` and `DEFAULT gen_random_uuid()`; `audit_event` → `audit_events` leaves the legacy list | `id` on every table; `updated_at` everywhere | Review of the UUID change requests (user, 25/10) |
| P1 Reshape changeset | Hải Anh (owner of changeset 001) decides who writes it; platform offers to draft it as a fix-forward changeset. Tasks 2–5 wait on her answer | platform writes it alone; rewrite 001 | Ownership of 001 stays with `be-foundation` (user, 25/10) |
| P2 Shared files | `backend/pom.xml`, `backend/src/main/resources/application*.yml`, `backend/.dockerignore` added to platform `paths`; `be-foundation` stays the owner | be-foundation quick change for each edit | The gate needs them open for platform's tasks (user, 25/10) |
| P3 JWT library | `spring-security-oauth2-jose` (Nimbus `NimbusJwtDecoder.withJwkSetUri`), version from the Spring Boot BOM — **dependency approved by the user, 25/10** (`backend/AGENTS.md` "new dependency") | full resource-server starter; jjwt | Key-set cache with refetch on an unknown key id (AC-005.4); our own filter keeps the 401-without-code answer |
| P4 Row-level security setting | `TenantAwareJpaTransactionManager.doBegin` runs `set_config('app.workspace_id', ?, true)` | pool checkout wrapper; statement interception | Transaction-local value; covers JPA, native and JDBC in the transaction |
| P5 `runIn` transactions | Always a new transaction (`REQUIRES_NEW`) | bind only and refuse inside a transaction; join the caller's | Hibernate fixes the tenant when a session opens; a new transaction gets a session of the right Workspace |
| P6 Grants | Roles and default privileges in `infra/postgres/init`, reused by Testcontainers; no GRANT in changesets | GRANT per changeset with a Liquibase parameter | Role names differ per environment; every future table is covered |
| P7 Seed before S3 | The Workspaces-only seed (registry + runner, no contributors) runs in iteration 1; CD runs it on staging after each deploy | create the staging Workspace by hand; change AC-010.4 | AC-010.4 needs a staging Workspace in S1; D42 allows the seed on staging |
| P8 Iterations | Iteration 1 (to 30/10): Tasks 1–5, 8–13, 15. Iteration 2 (31/10–13/11): Tasks 6, 7, 14, 16 | follow the Jira sprints exactly; pull KAN-105 forward | KAN-101 needs the token check (KAN-105, S2); the contract with Bình is already in the spec |
| P9 Caddy image | Built in CI from `infra/caddy/Dockerfile` (xcaddy + `caddy-dns/cloudflare`), pushed to GHCR as `tsolve-caddy` | build on the VPS | Same path as the other images; pinned and reproducible |
| P10 Builder check | A fresh-context subagent, as the plan skill says | inline | Independent reading of a Deep, high-risk plan |
| P11 Ollama on the AI VPS | Native systemd service, `OLLAMA_HOST=10.66.0.2:11434`, `After=`/`Requires=wg-quick@wg0` | Docker container | No Docker networking to reconcile with ufw and `wg0` |
| Q12 Certificates | Cloudflare DNS (free), custom Caddy with the Cloudflare DNS module, DNS-01 wildcards `*.tsolve.io.vn` (+ apex) and `*.staging.tsolve.io.vn`; zone-scoped token out of the repo; moving the nameservers is a manual prerequisite (Task 12) | on-demand TLS with an ask endpoint | Matches TS-002; no per-Workspace step (AC-009.1) (user, 25/10) |
| Q13 Private link | WireGuard `wg0`; Ollama only on the tunnel address; ufw 11434 only from the peer; keys on the hosts; tunnel enabled at boot | Tailscale; provider network | Encrypted, no third party (D41) (user, 25/10) |
| Tenant context storage | Static `ThreadLocal` behind the `TenantContext` bean | `ScopedValue` (preview in Java 21); request attribute | Hibernate's resolver and the entity listener need it outside Spring (Decided in planning) |
| Resolver cache | None: one lookup by the unique `site_name` index per request | cache with eviction | AC-004.9 and AC-004.10 need the next request to see changes; the seed and the web run in different processes (Decided in planning) |
| No tenant bound in Hibernate | Placeholder UUID of zeros + `@PrePersist` guard | return null | Hibernate requires a value; the placeholder matches no row and no foreign key (Decided in planning) |
| Health and API docs | `/actuator/health`, `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml` and the Swagger UI pass every host check (spec AC-002.19) | classify them like other paths | Docker health checks and `npm run api:pull` call `localhost:8080` (builder check; spec changed) |
| Migrations | Web process runs Liquibase at startup with `DB_MIGRATION_*` (AC-008.4); CD also runs a one-off `migrate` profile before `up` (AC-010.3); the worker never runs it (`spring.liquibase.enabled=false` under `worker`) | migrations only in CD | Both criteria hold; a failed migration stops the deploy before new containers start (Decided in planning) |
| Tenant information lookup | `RestClient`, `https` only, 5 s connect and read timeouts, no redirects, host must resolve to public addresses only (no IP literal, private, loopback or link-local), response capped at 64 KB; unbound sites probed in parallel with an overall deadline of 10 s; any failed probe → store nothing, 503 (AC-006.11) | open call; sequential probes | Operator-entered URLs must not turn into requests to internal hosts; the deadline bounds a lifecycle call however many sites are unbound (Decided in planning; builder check) |
| Forge key set and expiry | `NimbusJwtDecoder.withJwkSource` over a Nimbus `JWKSource` built with caching and **no rate limit**, so an unknown key id triggers exactly one refetch (AC-005.4); `JwtTimestampValidator(Duration.ZERO)` — no clock skew, servers run NTP | Spring defaults (60 s skew, rate-limited refresh) | A token expired by 1 s must be refused (AC-005.2, Edge #15) (Decided in planning; builder check) |
| Forge claims | `cloudId` from `context.cloudId`, the account from `principal`; the builder confirms the claim paths against Atlassian's Forge Remote documentation before Task 6 | — | The spec says "the token's claims"; paths are an implementation detail (Decided in planning) |
| Lifecycle event type | Read from the request body (`eventType`); everything that decides a Workspace comes from the token or tenant information | — | The type alone changes nothing an attacker could use (AC-006.7) (Decided in planning) |
| Events | `WorkspaceSuspended` / `WorkspaceReactivated` records in `platform/dto`, published from an after-commit `TransactionSynchronization` | `@TransactionalEventListener` on the consumer side only | Guarantees "after the commit" for every listener (AC-004.10) (Decided in planning) |
| GHCR namespace | `ghcr.io/${{ github.repository_owner }}` lower-cased (the repository is `T-Solve-team/T-Solve`), not the `bluebearccc` of the KAN-8/KAN-9 descriptions | hard-coded owner | `GITHUB_TOKEN` can push only to the repository owner's namespace (Decided in planning; Jira descriptions to be corrected) |
| Staging basic auth | Caddy `basic_auth` with a bcrypt hash from the edge `.env` on staging Workspace sites, `admin.`, `login.`; not on `api.` | IP allow-list | Team members work from changing addresses; Forge Remote must reach `api.` (AC-009.10) |
| Reserved `staging` (D44) | `staging` joins `admin`, `login`, `api`, `www`: registry MSG139; resolver MSG119 without lookup | own staging domain; accept the collision | User, 25/10 (AC-010.6 = A) |
| Seed after S3 | AC-012.5 kept; `infra/seed/run-seed.sh --reset` (no argument; reads `TSOLVE_ENVIRONMENT`): checks `TSOLVE_ENVIRONMENT` is `dev` or `staging` first, then stops the backend and worker, drops and recreates the environment's database as the PostgreSQL superuser and re-runs `infra/postgres/init` for it, empties `/srv/tsolve/<env>/files` (local: the `tsolve-files` volume), runs the `migrate` profile, starts the stack and runs the seed. Run once by hand on the staging server before the S3 seed. No argument: one source for the environment (review Q1 = A, user, 25/10) | contributors re-run idempotently; completion records | User, 25/10 (AC-012.5 = B; AC-012.8, AC-012.9) |
| Seed data format | JSON | YAML (needs `jackson-dataformat-yaml`) | No new dependency; `JsonNode` already in the contract (Decided in planning) |

## File Structure

```
backend/
├── pom.xml                                   + spring-security-oauth2-jose; Testcontainers if be-foundation has not added it yet
├── Dockerfile, .dockerignore                 KAN-9: Maven build stage, Java 21 JRE, non-root, health check
└── src/
    ├── main/java/vn/tsolve/
    │   ├── common/tenant/
    │   │   ├── TenantContext.java                     interface (spec contract)
    │   │   ├── ThreadLocalTenantContext.java          bean; bind/unbind package-private; runIn with REQUIRES_NEW
    │   │   ├── TenantBinding.java                     static holder used by Hibernate and the listener
    │   │   ├── TenantEntity.java                      @MappedSuperclass, @TenantId workspace_id, @PrePersist guard
    │   │   ├── WorkspaceTenantResolver.java           CurrentTenantIdentifierResolver<UUID>
    │   │   ├── TenantHibernateConfig.java             HibernatePropertiesCustomizer
    │   │   ├── TenantAwareJpaTransactionManager.java  set_config on doBegin
    │   │   ├── TenantTransactionConfig.java           exposes the transaction manager
    │   │   └── NoTenantException.java
    │   └── platform/
    │       ├── config/       PlatformProperties (base domain, environment, Forge, storage), ForgeSecurityConfig, TenantFilterConfig
    │       ├── web/          TenantResolverFilter, HostClassifier, HostKind, ProblemResponses
    │       ├── forge/        ForgeTokenVerifier (+ NimbusForgeTokenVerifier), ForgeTokenFilter, ForgeCallContext (+ impl),
    │       │                 VerifiedForgeCall, ForgeCallAuthentication
    │       ├── controller/   ForgeLifecycleController
    │       ├── service/      WorkspaceRegistryService, FileStorage, DemoSeedContributor, JiraSiteMatcher
    │       │   └── impl/     WorkspaceRegistryServiceImpl, LocalFileStorage, JiraSiteMatcherImpl
    │       ├── client/       TenantInfoClient (+ RestTenantInfoClient)
    │       ├── repository/   WorkspaceRepository, JiraSiteRepository
    │       ├── entity/       Workspace, JiraSite, WorkspaceStatus
    │       ├── dto/          CreateWorkspaceRequest, WorkspaceResponse, JiraSiteResponse, WorkspaceSuspended,
    │       │                 WorkspaceReactivated, StoredFile, SeedWorkspace
    │       ├── mapper/       WorkspaceMapper
    │       ├── exception/    FileTooLargeException, InvalidFileKeyException, WorkspaceRuleException (extends BusinessRuleException)
    │       └── seed/         SeedRunner, SeedData, MigrateRunner
    ├── main/resources/
    │   ├── application.yml            + tsolve.base-domain, tsolve.environment, tsolve.forge.*, tsolve.storage.*, spring.liquibase.user/password
    │   ├── application-worker.yml     spring.liquibase.enabled=false (worker profile)
    │   ├── application-seed.yml       web-application-type none
    │   ├── application-migrate.yml    web-application-type none, exit after Liquibase
    │   └── db/changelog/changes/NNN-platform-workspaces.sql (owner per P1), NNN-platform-jira-sites.sql
    └── test/java/vn/tsolve/
        ├── common/tenant/   ThreadLocalTenantContextTest, TenantEntityIT, RowLevelSecurityIT, ConventionGuardIT, TenantIsolationIT
        ├── platform/        HostClassifierTest, TenantResolverFilterTest, WorkspaceRegistryServiceImplTest, WorkspaceRegistryIT,
        │                    NimbusForgeTokenVerifierTest, ForgeSecurityIT, JiraSiteMatcherImplTest, ForgeLifecycleIT,
        │                    LocalFileStorageTest, SeedRunnerTest
        └── support/         PostgresTestBase (Testcontainers pgvector:pg16 + infra/postgres/init, owner and app users)
infra/
├── README.md                          variables per environment, local start, DNS table, runbooks
├── caddy/Dockerfile, Caddyfile, Caddyfile.local
├── compose.edge.yml, compose.staging.yml
├── postgres/init/10-tsolve-roles.sh
├── scripts/bootstrap.sh, install-runner.sh, wireguard-setup.sh, ollama-setup.sh
├── seed/demo-workspaces.json, run-seed.sh
└── tests/  smoke.test.sh, ports.test.sh, caddy-routing.test.sh, ai-vps.test.sh      (names cite the AC ids they check)
docker-compose.yml                      local stack (KAN-94)
.env.example                            every variable, no values
.github/workflows/ci.yml (+ backend job), images.yml (backend, frontend, caddy → GHCR), cd-staging.yml, infra-checks.yml
```

## External Dependencies

- `org.springframework.security:spring-security-oauth2-jose` (Spring Boot BOM): verifying Forge Invocation Tokens
  against Atlassian's key set. **Approved by the user on 25/10.**
- Testcontainers (`spring-boot-testcontainers`, `org.testcontainers:postgresql`, Spring Boot BOM): already decided by
  `be-foundation` (its Q7); platform adds it only if be-foundation has not yet.
- Caddy 2 built with `github.com/caddy-dns/cloudflare` via `xcaddy` (pinned versions in `infra/caddy/Dockerfile`).
- Cloudflare DNS (free plan) for `tsolve.io.vn`; GHCR; GitHub Actions self-hosted runner.
- WireGuard (Ubuntu package), Ollama (official installer, pinned version) with `bge-m3` and the chat model of KAN-140.
- Atlassian: the Forge key set (`FORGE_JWKS_URL`) and the public `/_edge/tenant_info` of a Jira site.

## Edge Cases & Risks

Dimensions analyzed: 1 User types, 2 Input extremes, 3 Timing, 4 Scale, 5 State transitions, 7 Error cascades,
8 Authorization, 9 Data integrity, 10 Integration, 11 Compliance, 12 Business logic · Skipped: 6 Environment — no
user interface in this module; browsers are `fe-foundation`'s.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-002 | Authorization | Request on `a.<base>` carries `X-Forwarded-Host: b.<base>` or `?workspaceId=<B>` | Critical | Bound to A; B never read | T5 |
| 2 | STORY-002 | Timing | A request throws after binding; the next request on the same pooled thread | Critical | Next request starts unbound | T1, T5 |
| 3 | STORY-003 | Data integrity | Transaction for A ends; the same pooled connection serves a request with no Workspace | Critical | No `app.workspace_id`; no rows | T3 |
| 4 | STORY-002 | Authorization | Code inside A's request calls `runIn(B, …)` | Critical | Throws, action not run | T1 |
| 5 | STORY-001 | Data integrity | Entity saved with `workspaceId` set to B by the caller while A is bound | Critical | Stored with A | T2 |
| 6 | STORY-003 | Authorization | Native `INSERT … workspace_id = B` while A is bound | Critical | Refused by `WITH CHECK` | T3 |
| 7 | STORY-003 | Authorization | Application user runs `ALTER TABLE … DISABLE ROW LEVEL SECURITY` | Critical | Refused (not owner) | T3 |
| 8 | STORY-002 | Authorization | MSG119/MSG120 must come before a 401: unknown site, no session | High | 404 MSG119, not 401 | T5 |
| 9 | STORY-002 | Input extremes | Host `ACME.tsolve.io.vn:443`, `acme.tsolve.io.vn.` (trailing dot), `xn--…` label, 64-char label, `-acme` | High | Lower-cased, port stripped; trailing dot → MSG119; 64 chars / leading hyphen → MSG119 without lookup | T5 |
| 10 | STORY-002 | Environment of hosts | Docker health check on `localhost:8080/actuator/health` | High | 200, not MSG119 | T5 |
| 11 | STORY-004 | Timing | Two Operators create the same site name at once | High | One stored, other MSG125 | T4 |
| 12 | STORY-004 | Input extremes | Google domain `Acme.COM`, `acme`, `acme..com`, 300 chars; site URL `http://x.atlassian.net`, `https://x.atlassian.net/jira`, `https://user@x`, `https://x:8443` | High | Lower-cased; MSG127 / MSG136 as the rules table | T4 |
| 13 | STORY-004 | State transitions | Suspend, then a request already past the resolver finishes | Medium | That request completes; the next is refused | T4 |
| 14 | STORY-004 | State transitions | Suspend twice | Low | No second event | T4 |
| 15 | STORY-005 | Authorization | Token with `alg: none`, HS256 signed with the public key, expired by 1 s, wrong audience, wrong issuer | Critical | 401 without code, no lookup | T6 |
| 16 | STORY-005 | Error cascades | Atlassian key set unreachable at startup / on an unknown key id | High | Fail closed (401), logged; recovers when reachable | T6 |
| 17 | STORY-005 | Authorization | Valid Forge token sent to a Workspace site's `/api/v1/me` | Critical | Not authenticated by it (session chain decides) | T6 |
| 18 | STORY-006 | Authorization | Verified token from site X, body claims site URL of victim Workspace V (not yet installed) | Critical | Ignored; X's `cloudId` bound only if X's tenant information matches a registered URL | T7 |
| 19 | STORY-006 | Integration | Operator registered `https://10.0.0.5` or a host resolving to `127.0.0.1` | High | No outbound call to it; site never matches | T7 |
| 20 | STORY-006 | Timing | Two installed events for the same site arrive together | High | One binds; the other changes nothing (conditional update + unique `cloud_id`) | T7 |
| 21 | STORY-006 | Integration | Lifecycle event replayed by Forge | Medium | 204, no change | T7 |
| 22 | STORY-006 | Error cascades | Tenant information times out for one of three unbound sites | Medium | Store nothing, 503 (AC-006.11), even if another site matched; Forge's retry or the platform team settles it | T7 |
| 23 | STORY-006 | Business logic | Issue-created event from a Suspended Workspace | High | 403 MSG121, nothing recorded, no job | T7 |
| 24 | STORY-007 | Input extremes | Key `../b/…`, `C:\x`, `%2e%2e/`, NUL, symlink planted in the Workspace folder | Critical | Refused or not followed; no file outside the folder touched | T8 |
| 25 | STORY-007 | Scale | File of exactly 10 MB, 10 MB + 1 byte, 0 bytes | High | 10 MB stored; +1 refused, no part left; 0 bytes stored | T8 |
| 26 | STORY-007 | Error cascades | Disk full midway | High | Temp file removed; no file under the key | T8 |
| 27 | STORY-001 | Data integrity | A new table with RLS enabled but not forced, or the index not led by `workspace_id` | High | Guard test fails naming the table and the part | T2 |
| 28 | STORY-001 | Data integrity | Someone adds a new table to the legacy list | High | Guard test fails ("only shrinks") | T2 |
| 29 | STORY-008 | Environment | `.env` missing `DB_MIGRATION_PASSWORD` | Medium | Backend refuses to start naming it | T9 |
| 30 | STORY-009 | Integration | Cloudflare token expired at renewal time | High | Existing certificates keep serving (renewal starts 30 days early); Caddy logs the failure; runbook rotates the token | T12 |
| 31 | STORY-009 | Authorization | `www.staging.tsolve.io.vn`, `staging.tsolve.io.vn`, `a.b.staging.tsolve.io.vn` | Medium | 404 from Caddy (first two); third has no certificate | T12 |
| 32 | STORY-010 | Authorization | A pull request from a fork tries to run on the self-hosted runner | Critical | Deploy job only on `push`/`workflow_run` of `main`; runner group restricted to `cd-staging.yml` | T13 |
| 33 | STORY-010 | Error cascades | Migration fails mid-deploy | High | Workflow fails before `up`; old containers keep serving | T13 |
| 34 | STORY-010 | Authorization | Docker publishes PostgreSQL's port, bypassing ufw | Critical | No `ports:` on db/backend/worker in server compose files; port test from the internet | T11, T13 |
| 35 | STORY-011 | Environment | AI VPS reboots | High | `wg0` and Ollama come back (systemd order); `ai-vps.test.sh` passes after reboot | T14 |
| 36 | STORY-011 | Authorization | Ollama started by the installer on `0.0.0.0:11434` before the override | Critical | Override applied before first start; ufw denies 11434 on the public interface anyway | T14 |
| 37 | STORY-012 | Business logic | Seed run with `TSOLVE_ENVIRONMENT` unset or `production` | Critical | Exit non-zero, nothing written | T15 |
| 38 | STORY-012 | State transitions | Seed run twice | Medium | Second run skips both Workspaces | T15 |
| 39 | STORY-012 | Error cascades | Contributor of module X fails for Workspace 2 | Medium | Stops, non-zero, names Workspace 2 and X | T16 |
| 40 | STORY-002 | User types | Platform Console counts Accounts of a Suspended Workspace with `runIn` | Medium | Allowed (AC-002.15) | T1 |
| 41 | STORY-002 | Compliance | Logs of refused requests | Medium | Log the host and code only; no token, cookie or body | T5, T6 |
| 42 | STORY-004 | Scale | `list` with 0 Workspaces and page size boundaries | Low | Empty page; correct totals | T4 |
| 43 | STORY-012 | Business logic | `run-seed.sh --reset` with `TSOLVE_ENVIRONMENT` unset, `production` or `prod` | Critical | Exit non-zero before any command touches the database or files | T16 |
| 44 | STORY-002, STORY-004 | Input extremes | Site name `staging` at creation; request to `staging.tsolve.io.vn` reaching the production backend | High | MSG139; MSG119 without a registry lookup (D44) | T4, T5 |

Open questions for the spec owner: none.

## Risk Review — 2026-10-25 — Verdict: CAUTION

Agreements (four or more personas):
- The order "resolve → bind → security → handler → unbind in `finally`" is the core invariant; it needs its own tests
  (filter order, unbind after an exception, connection reuse).
- Row-level security as the application user is what makes a forgotten filter harmless; tests must run as that user,
  not as the owner.
- The install registration must never trust the request body.

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| `ThreadLocal` binding | Fits Servlet + JobRunr threads | Leak across requests if not cleared | Free | — | Async code loses it silently | Clear in `finally` (rows 2); async callers must use `runIn` (contract note) |
| No resolver cache | Simple | — | One indexed query per request (~1 ms) | Suspension is immediate | A cache would be premature | Keep; revisit only with measurements |
| `REQUIRES_NEW` in `runIn` | Clear unit of work | — | One more transaction per call | — | UC-56 cannot be atomic with Workspace creation | Acceptable: UC-55 and UC-56 are separate steps in the baseline |
| Web process holds migration credentials | Needed for AC-008.4 | A compromised web process could alter tables | — | — | Run migrations only in CD | CAUTION: keep AC-008.4; the owner credentials are used only by Liquibase's own data source at startup; propose a spec change to drop startup migrations on servers if the team wants least privilege |
| Tenant information lookup | Extra dependency on Atlassian | Request forgery to internal hosts | 5 s per unbound site, once per install | — | Ask Bình to send the site URL in the token | Public-address guard + timeouts (row 19); signed-claim path first |
| Self-hosted runner | Needed to deploy behind the firewall | Fork PR code on the server | — | — | Deploy over SSH from a hosted runner | Restrict triggers and runner group (row 32) |
| Forge key set outage | — | Must fail closed | Cached keys keep working | Jira panel shows MSG06 during the outage | — | Accept; log and alert (KAN-202, S3) |

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Workspace binding leaks to the next request | Critical | `finally` unbind; test with a thread pool of one | T1, T5 |
| Pooled connection keeps `app.workspace_id` | Critical | `set_config(…, true)`; test with a pool of one connection | T3 |
| Tests pass as owner, fail to catch RLS bypass | Critical | `PostgresTestBase` connects as `<db>_app`; Liquibase as owner | T2, T3 |
| Install binds a foreign `cloudId` to a Workspace | Critical | Only signed claims or tenant information; never the body | T7 |
| Request forgery through tenant information | High | Public-address guard, `https`, no redirects, timeouts | T7 |
| Fork PRs on the self-hosted runner | Critical | Triggers limited to `main`; runner group per workflow | T13 |
| Published container ports bypass ufw | Critical | No `ports:` except Caddy; `ports.test.sh` from a hosted runner | T11, T13 |
| Ollama exposed before ufw/override | Critical | Override before first start; ufw default deny; `ports.test.sh` | T14 |
| Migration credentials in the web process | Medium | Separate Liquibase data source; noted for a possible spec change | T9 |
| Platform Tasks 2–5 blocked by the reshape changeset | High | Ask Hải Anh now; platform offers a draft (P1) | T2 |

## Builder Check

Run with a fresh-context subagent on the spec, this design, the iteration task files, `module.md` and
`architecture.md` (decision P10). Results and how each was settled are listed below.

Run on 25/10: 25 items. Two were blocking and went to the user (answered the same day: items 7 and 14); six were
contradictions with an obvious answer, fixed in the spec (Changelog row "Builder check"); the rest are settled here.

| # | Item | Settled |
|---|------|---------|
| 1 | Is the spec approved? | Answered: changed by X2/U3 and the builder check; the user re-approves before any code |
| 2 | Content of the `workspaces` reshape | Waiting on Trần Hải Anh (P1). Platform's draft, if she wants it: delete the 4 legacy rows and every row that references them (no environment holds real data; developers reset), drop the old foreign keys, recreate `workspaces` as the Data Model says, change `workspace_id` of `resolved_ticket_snapshot` and `solution` to `UUID` with a foreign key to `workspaces` |
| 3 | AC-006.11 vs Edge #22 | The spec wins: any failed probe → nothing stored, 503. Parallel probes, overall deadline 10 s (Key Decisions) |
| 4 | `findById` Optional vs AC-004.13 | Spec fixed: lookups return empty; only suspend / reactivate throw |
| 5 | Hibernate refuses a preset different tenant id | `@PrePersist` overwrites the field with the bound id before Hibernate's check (Hibernate and row-level security) |
| 6 | Forge claim paths and lifecycle body | Research step at the start of Task 6 (Atlassian Forge Remote documentation); the paths and the `eventType` values are recorded in this design before the token fixtures are written |
| 7 | Seed skip keeps S3 data off staging | User (B): AC-012.5 kept; staging is reset once before the S3 seed with `run-seed.sh --reset` (new AC-012.8, AC-012.9), Task 16 |
| 8 | "Production configuration" vs the shared image | Spec fixed (AC-012.4 names the production compose file, its environment template and the production workflow); Task 15's test scans only those |
| 9 | Health on `localhost:8080` vs AC-002.6; where API docs live | Spec fixed: new AC-002.19 |
| 10 | Guard cannot tell join / append-only tables | Join tables by structure; append-only by `COMMENT ON TABLE … 'tsolve:append-only'`; unique led by `workspace_id` counts as the index; platform-wide unique columns allowed; policy compared after normalisation (Convention guard test data) |
| 11 | Reads outside a transaction | `open-in-view` false; tenant access only inside transactions; outside one, no rows (fail closed) |
| 12 | Forge retrying a 403 event after reactivation (D29) | Risk: Task 7 checks Atlassian's retry rules for Forge Remote events; if a 4xx is retried, the event endpoints answer the Suspended case with 2xx and drop it — a spec change raised with Đặng Tấn Bình then. Tests use a test-only event handler to prove nothing runs |
| 13 | Empty site name or Google domain | Spec fixed: MSG01; inputs trimmed (rules table) |
| 14 | A Workspace named `staging` on production | User (A): `staging` is reserved (D44): MSG139 in the registry, MSG119 without lookup in the resolver (AC-002.6, AC-004.3). Caddy serves only the staging base domain this iteration; production hosts come with KAN-201 |
| 15 | Shape of `params` | be-foundation AC-001.1: a JSON object (rules table note) |
| 16 | `/api/v1/me` for the smoke test by 30/10 | Any `/api/**` path without a session answers 401 MSG07 once be-foundation Task 2 lands (its AC-002.4); Task 13 depends on it. The basic-auth credential is the GitHub secret `STAGING_BASIC_AUTH` |
| 17 | Frontend Dockerfile, Caddyfile, frontend CI, PR title, gitleaks | Already in the repository (`frontend/Dockerfile`, `frontend/Caddyfile`, `ci.yml` frontend job, `pr-checks.yml`); Task 10 only adds the backend job and the images |
| 18 | Chat model undecided (KAN-140) | `OLLAMA_CHAT_MODEL` (owned by `ai`) parameterises the pulls and checks; the chat-model parts of Tasks 9 and 14 (pull, completion check) wait for KAN-140; the `bge-m3` parts do not |
| 19 | Variables not named in the spec | Configuration table below; every variable without a default is required in every environment (AC-008.5); dev uses the development Forge app's id |
| 20 | Who creates the JobRunr tables | `worker` (Phạm Tùng Dương): JobRunr's own table creation needs DDL the application user lacks, so `worker` adds JobRunr's SQL as a changeset run by the owner user, or runs JobRunr's migration with the owner credentials (told to the owner) |
| 21 | A tenant table for the tests | Test-only `isolation_probes`, created by the tests after the changelog |
| 22 | Clock skew and key refresh | Zero skew; refetch once without rate limit (Key Decisions) |
| 23 | Port and user info; storage of `site_url` | Spec fixed (MSG136); stored normalised; the later index gets MSG137 |
| 24 | Partial files after a crash; HTTP answer of a bad key | Temp files outside Workspace folders, cleaned at startup; bad key = 404 MSG06 (Storage layout) |
| 25 | Trace of infrastructure checks; server criteria | `infra/tests/*.test.sh` match the harness test pattern; AC-010.7, AC-010.9, AC-010.10 and AC-011.5 get `infra/tests/server.test.sh`, run over SSH by `infra-checks.yml` (Tasks 11, 14). The seed step is now in AC-010.3. Java tests named `*Test.java` / `*IT.java` do not match the harness's test globs: reported as framework friction |

### Configuration (all names in `.env.example`, no values)

| Variable | Used by | Default |
|----------|---------|---------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | backend, worker (application user) | none |
| `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD` | Liquibase (owner user) | none |
| `TSOLVE_DATABASES`, `POSTGRES_PASSWORD`, `<DB>_OWNER_PASSWORD`, `<DB>_APP_PASSWORD` | `infra/postgres/init` | none |
| `TSOLVE_BASE_DOMAIN`, `TSOLVE_ENVIRONMENT` | backend, worker, seed | none |
| `FORGE_APP_ID`, `FORGE_JWKS_URL` | backend | none |
| `FILE_STORAGE_ROOT` · `FILE_STORAGE_MAX_BYTES` | backend, worker | none · 10485760 |
| `TSOLVE_SEED_FILE` | seed | none |
| `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL` | `ai`; local `ollama-pull` | none |
| `CLOUDFLARE_API_TOKEN`, `STAGING_BASIC_AUTH_HASH` | edge Caddy (server `.env` only) | none |
| `SERVER_PORT`, `CORS_ALLOWED_ORIGINS` | backend (existing) | as today |
