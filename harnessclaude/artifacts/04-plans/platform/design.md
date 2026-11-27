---
module: platform
date: 2026-11-04
---

# Design: platform

Spec: `artifacts/03-modules/platform/spec.md` (database per Workspace, multi-tenant HLA sync of 4/11; waiting for
re-approval). Architecture: `artifacts/03-modules/architecture.md`; ADR: `docs/adr/0002-database-per-workspace.md`.
Code follows `backend/AGENTS.md`. This design replaces the 25/10 design of the shared-database pool model (a Workspace
id column on every table, a per-row filter and a row policy on that column).

## Architecture Overview

`platform` has two halves: the **tenant layer** in the backend (`common/tenant/` + `platform/`) and the
**infrastructure** (`infra/`, compose files, workflows). Every other module sees the tenant layer through
`TenantContext`, `WorkspaceCatalog`, `WorkspaceProvisioning`, `FirstAdminCreator`, `ForgeCallContext`, `FileStorage` and
`DemoSeedContributor` (spec, Interface Contracts), and uses the primary `DataSource` / JPA as usual: it points at the
bound Workspace's database without the module knowing.

Four stores, one PostgreSQL server (Docker Compose is the MVP and demo deployment; Kubernetes and high availability are
the target, not built here — H-Q1):

| Store | What it holds | Who reads it |
|-------|---------------|--------------|
| Tenant Registry DB `tsolve_<env>_registry` ("the Registry") | `tenant_workspace`, `workspace_email` | the Tenant Resolver, `WorkspaceCatalog`, the router, provisioning, `identity` |
| One Workspace DB `tsolve_<env>_ws_<id>` per company | all business tables, the one-row `tenant_identity`, the row policies | only through the routing `DataSource` (runtime user) or the owner user (migrations, provisioning) |
| Jobs DB `tsolve_<env>_jobs` | JobRunr's own tables only | `worker`; the `InvitationJobQueue` implementation of `platform` (enqueue) |
| Garage bucket `FILE_STORAGE_BUCKET` + Redis | files under `<workspaceId>/` keys; sessions of `identity` | `FileStorage`; `identity` |

### How a request or job gets its Workspace

| Entry | Path through the code | Workspace |
|-------|----------------------|-----------|
| Workspace site `<site>.<base>` `/api/**` | `TenantResolverFilter` (servlet filter, before Spring Security) → `HostClassifier` → `WorkspaceCatalog.findBySiteName` → `bindRequest` → security chain of `be-foundation` / `identity` → handler → unbind in `finally` | from the host (AC-002.2) |
| `api.<base>` `/api/v1/jira-app/**` | `TenantResolverFilter` (host check only) → `ForgeSecurityConfig` chain (ordered first, matches the `api.` host) → `ForgeTokenFilter` → `ForgeTokenVerifier` → `findByCloudId` → bind → handler → unbind | from the verified `cloudId` (AC-006.1) |
| `api.<base>` `POST /api/v1/jira-app/lifecycle` | as above, but `ForgeTokenFilter` skips the Workspace lookup → `ForgeLifecycleController` answers 204 and changes nothing | none (AC-006.12, AC-006.15, AC-006.16) |
| `login.<base>`, `admin.<base>` | `TenantResolverFilter` binds nothing → `login.` is Keycloak (Caddy, AC-009.4); `admin.` → `platform-console` chain | none; `runIn` where the spec allows it (the Console) |
| Job, Console, ops script, seed | `TenantContext.runIn(workspaceId, action)` → bind → action; its transactions run on that Workspace's database | the given Workspace (AC-002.12) |
| any host `/actuator/health`, `/v3/api-docs*`, Swagger UI | passes every host check (AC-002.19) | none |

### The binding

- `ThreadLocalTenantContext` (bean implementing `TenantContext`) keeps the bound Workspace id in a static `ThreadLocal`
  (package-private `TenantBinding`), so non-bean code (the routing `DataSource`, the transaction manager) reads the same
  value.
- The web resolver binds with the public `bindRequest(UUID)` of `ThreadLocalTenantContext`, which is not part of the
  interface (Task 10, Q2): it returns an `AutoCloseable` that unbinds, opens no transaction, checks no existence (the
  resolver has just read the catalog) and throws when a Workspace is already bound. A source-scan test allows only
  `TenantResolverFilter` to call it; modules bind only through `runIn`. Task 6 moves the static into `TenantBinding`
  and keeps the method.
- Other filters, jobs and modules all bind through `runIn`; there is no package-private `bind`/`unbind` pair (Decided in
  planning, Task 6, Q1 and Q9).
- `runIn` refuses a different Workspace when one is bound (AC-002.13), refuses an unknown id (AC-002.14, one existence
  check in the Registry, skipped when the same id is already bound, because whoever bound it checked it; Task 10, Q9),
  binds, runs the action, and restores the previous binding in `finally` (AC-002.12). It opens no transaction (decision
  P5); transactions stay with the services' `@Transactional`. A Suspended Workspace can still be bound (AC-002.15).
- Delivered in steps (wave order of 22/11, KAN-384; iteration-1 design "Wave order and Task 6a"): Tasks 6a-1 and 6a-2 make
  `ThreadLocalTenantContext` the only binding: 6a-1 adds the holder, 6a-2 removes the stub and the other fallbacks.
  Identity `TenantScope` and worker `runIn` delegate to it; no transaction yet; the existence check goes through the
  port `KnownWorkspaces` over the env stubs. Task 4 moves the existence check to
  the Registry; Task 6-1 binds the background database work (the publish listener runs in the publisher's Workspace;
  recurring jobs run once per Active Workspace, or once unbound with no catalog); Task 6-2 adds `TenantBinding` and the
  routing; Task 10 the resolver, which binds with `bindRequest`.

### The web resolver (Task 10; AC-002.2 – AC-002.11, AC-002.17, AC-002.19)

- **Base domain.** The host part after `{slug}.` in `TSOLVE_SITE_ORIGIN_TEMPLATE` (`localhost` locally, `tsolve.io.vn`
  on staging); `<base>` in this file means it. No variable of its own. `HostClassifier` parses the template itself
  (`platform` does not depend on `identity`).
- **Active only with a catalog.** The filter asks for `WorkspaceCatalog` through `ObjectProvider`. With no catalog bean
  (empty `REGISTRY_DB_URL`: `dev`, Playwright, most ITs) it passes every request through and logs one line at start-up.
  With a catalog and a template that gives no base domain, start-up fails naming the variable (Edge #86).
- **Order of checks:** (1) the AC-002.19 paths pass; (2) the raw `Host` header, lower-cased, port removed; a host that
  is no Workspace site → 404 `MSG151`, no lookup; (3) a site label: one `findBySiteName`; unknown → 404 `MSG151`,
  Suspended → 403 `MSG150`; (4) `/api/v1/jira-app/**` on any host but `api.` → 404 `MSG06` (AC-005.8); (5) on `api.`
  any other path → 404 `MSG06` (AC-005.7); (6) a site binds for the chain; `login.`, `api.`, `admin.` bind nothing.
- **Refusals** are `application/problem+json` from `SecurityProblemWriter` on every path, sign-in and callback
  included. A failing lookup answers 500 `MSG06`; the log holds the host, the code and the exception class, never its
  message (it can hold the JDBC URL).
- **`Host` against `X-Forwarded-Host`.** The resolver reads `getHeader("Host")`. With
  `server.forward-headers-strategy: framework`, `getServerName()` (identity's `AuthHosts`) follows `X-Forwarded-Host`;
  behind Caddy both match. If a client makes them differ on `:8080` while `Host` is a site (or `api.`), it fails
  closed: `runIn` of the other Workspace throws (AC-002.13) or the session check answers `MSG123`. It does not hold for
  `Host` = `login.` or `admin.` with a forged `X-Forwarded-Host` naming a Suspended site plus that site's session
  cookie: the request reaches the handler (Edge #92). The control there is the network boundary (Caddy overwrites
  forwarded headers; staging publishes no backend port). Follow-up (`identity`, not Task 10): its session check should
  refuse a Suspended site.
- **Queries per signed-in request:** the resolver's lookup and `WebSessionFilter`'s; `runIn` on the bound id asks
  nothing. No cache, so a suspend applies on the next request.
- Decided in planning: `/api/v1/jira-app/**` is matched on the decoded path with the same pattern as the Jira App chain
  (`JiraAppSecurityConfig.PATHS`), and the AC-002.19 list the same way, so an encoded path cannot pass one and reach the
  other.
- Decided in planning: on a Suspended or unknown site the tenant answer (`MSG150` / `MSG151`) comes before `MSG06` of
  AC-005.8, because the resolver runs before every security check (AC-002.8); a host that is no Workspace site answers
  `MSG151` (AC-002.6).
- Decided in planning: an ASYNC dispatch (SSE) binds the id resolved on the first dispatch, kept as a request
  attribute, with no second lookup; an ERROR dispatch passes, since the binding is already gone.
- Decided in planning: the filter resolves the catalog bean after start-up, never in a constructor (Edge #86).
- The `identity-mock` profile filter (order -1) runs after the resolver; a mock caller of another Workspace makes
  `runIn` throw (AC-002.13).

### Database routing (STORY-002; AC-002.20 – AC-002.25)

- Routing is on exactly when the Registry is configured and the profile is not `dev` (`TenantDataSourceConfig`).
  Otherwise Boot's single `DataSource` stays (most ITs, the default local run). Dropping this gate, after the ITs run on
  production routing, is a later task.
- `TenantRoutingDataSource extends AbstractRoutingDataSource` is then the **primary** `DataSource`; JPA and
  `JdbcTemplate` use it. It overrides `determineTargetDataSource()`: no Workspace bound → throws `NoTenantException`
  before any connection is opened (AC-002.21); else it returns the pool of that Workspace from the port
  `TenantDataSources` (`DataSource poolOf(UUID)`), which `platform/registry/TenantPoolRegistry` implements. Boot's health
  check must not call it with nothing bound: a `dbHealthContributor` bean on the Registry pool (Boot's own contributor
  backs off).
- `TenantPoolRegistry` opens a HikariCP pool for a Workspace on first use (`ConcurrentHashMap.computeIfAbsent`, so two
  first requests share one pool): `maximumPoolSize = TENANT_DB_POOL_MAX` (default 5), `minimumIdle = 0`,
  `idleTimeout` 10 minutes, so an idle Workspace keeps no connection (AC-002.22). Connection data comes from the
  Registry row (`db_name`, `db_user`, decrypted `db_password`, read by the platform-internal
  `RegistryRepository.findConnection`, AC-004.18), `TENANT_DB_HOST` and `TENANT_DB_PORT`; a pool that fails to open is not
  cached. The Registry's `schema_version` is not read here.
- The connection setting the row policies check is `app.tenant_id`. Two places set it, both from the **bound**
  Workspace id, never from the pool's own Workspace, so a misrouted connection mismatches `tenant_identity`
  (AC-002.24, AC-002.25):
  1. `TenantAwareJpaTransactionManager extends JpaTransactionManager`: in `doBegin`, after the transaction starts,
     `SELECT set_config('app.tenant_id', ?, true)` on the transaction's connection (transaction-local, so a pooled
     connection never carries it on). JDBC code in the same transaction shares the connection (`HibernateJpaDialect`).
  2. For a connection checked out with no transaction active, `TenantRoutingDataSource` wraps it: it sets the value at
     checkout (`set_config(..., false)`) and runs `RESET app.tenant_id` when the connection is closed.
- Tenant data is read and written in transactions (`spring.jpa.open-in-view` is `false`, Spring Data repositories open
  their own). Hibernate must not ask the database for metadata at startup, when no Workspace is bound:
  `hibernate.boot.allow_jdbc_metadata_access=false`, `hibernate.dialect` set to `PostgreSQLDialect` explicitly, and
  `ddl-auto: none`. With routing off, `ddl-auto: validate` stays.
- Until `TenantStartupMigrator` (R14) or wave 4's owner role, Boot's tenant Liquibase has its own connection where
  routing is on: `spring.liquibase.url/user/password` from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
  never set unconditionally (the ITs migrate through the primary `DataSource`).
- A transaction stays on the database it began on (AC-002.23): the pool is chosen when the connection is acquired in
  `doBegin`, and `runIn` of another Workspace inside it is refused.

### The row guard (STORY-001; AC-001.28 – AC-001.32)

Defence in depth behind the database boundary (HLA isolation layer 3; H-Q6). The tenant changelog holds, in this order:

1. `tenant_identity` — one row, written at provisioning, readable but not writable by the runtime user.
2. `tenant_guard_ok()` — a stable SQL function: the `app.tenant_id` setting (empty string treated as unset) equals
   `tenant_identity.tenant_id`.
3. Per table: `ENABLE ROW LEVEL SECURITY` and one policy `tenant_guard`, `FOR ALL USING (tenant_guard_ok()) WITH CHECK
   (tenant_guard_ok())`. Modules write **nothing** for this. A final changeset with `runAlways="true"`,
   `runOnChange="true"` and `runOrder="last"` (an inline XML changeSet at the end of `db.changelog-master.xml`, so it
   follows every module's changesets; it runs the SQL file `053-platform-tenant-guard-policies.sql`) installs the
   policy on every table of `public` except `tenant_identity` and the two Liquibase tables, replacing any existing
   `tenant_guard` (decision R4).
4. The convention guard test fails for a table without the policy, with a policy of another name or condition, or with
   a second policy (policies are permissive and would OR together).

No `FORCE ROW LEVEL SECURITY`: the owner user (migrations, provisioning, backfills) must not be limited; the runtime
user is not the owner (AC-001.32), so its queries are always filtered.

## Data Model

### Tenant Registry DB (Registry changelog `db/changelog/catalog/catalog-master.xml`; no row policy)

```sql
--changeset platform:C001-registry-tenant-workspace
CREATE TABLE tenant_workspace (
    workspace_id    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    site_name       VARCHAR(63)  NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    db_name         VARCHAR(63)  NOT NULL,
    db_user         VARCHAR(63)  NOT NULL,
    db_password     TEXT         NOT NULL,                  -- 'v1:' + base64(nonce || AES-GCM ciphertext), AAD = workspace_id
    schema_version  VARCHAR(255) NOT NULL,                  -- id of the last tenant changeset applied to that database
    google_domain   VARCHAR(253) NOT NULL,                  -- lower case (AC-001.34, AC-013.21)
    keycloak_organization_id VARCHAR(100),                  -- null until the sign-in setup returns it; unique (AC-001.34)
    jira_site_url   VARCHAR(300),
    jira_cloud_id   VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_workspace_site_name UNIQUE (site_name),
    CONSTRAINT uq_tenant_workspace_db_name UNIQUE (db_name),
    CONSTRAINT uq_tenant_workspace_jira_cloud_id UNIQUE (jira_cloud_id),
    CONSTRAINT uq_tenant_workspace_keycloak_org UNIQUE (keycloak_organization_id),
    CONSTRAINT ck_tenant_workspace_site_name CHECK (site_name ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$')
);

--changeset platform:C002-registry-workspace-email
CREATE TABLE workspace_email (
    email         VARCHAR(320) PRIMARY KEY,
    workspace_id  UUID         NOT NULL REFERENCES tenant_workspace (workspace_id),
    CONSTRAINT ck_workspace_email_lower CHECK (email = lower(email))
);
CREATE INDEX idx_workspace_email_workspace ON workspace_email (workspace_id);
```

The seed or any environment starts with both tables empty (AC-001.19). A Workspace id in these tables names a
row of `tenant_workspace`; it is not a tenant column. `identity` adds no table to the Registry (ADR-0003, K12).

Registry access is `JdbcClient` on the `registryDataSource` bean with its own `registryTransactionManager` (decision
R1), not a second JPA unit. The Registry has one user, `REGISTRY_DB_USER`, the owner of `tsolve_<env>_registry`: it applies the
changelog and reads and writes the two tables (decided in planning, Q3). A DML-only runtime user is split off later, in
Task 1's role work. The Registry beans load only when `REGISTRY_DB_URL` is not blank, and once it is set a missing or
invalid `CATALOG_SECRET_KEY` refuses startup naming the variable; the gate stays with routing (Task 6-2) and is
dropped by a later task, once the ITs run on production routing. `db_password` encryption: AES-256-GCM, a random 12-byte nonce per value, the Workspace id as associated
data (a copied ciphertext does not decrypt on another row), key `CATALOG_SECRET_KEY` (base64, 32 bytes). A missing key
fails at startup for the Registry as a whole; a wrong key or a bad value fails only the pool of that Workspace and logs
its site name, never the password (AC-001.25).

### Workspace DB (tenant changelog `db.changelog-master.xml`, applied per Workspace by the owner user)

Tenant-table template (spec AC-001.20 – AC-001.23):

```sql
--changeset <module>:NNN-<slug>
CREATE TABLE things (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),       -- omitted on an append-only table
    CONSTRAINT uq_things_name UNIQUE (name)
);
-- No Workspace-id column, no index on one, no policy: the database is the boundary and the guard changeset adds the policy.
-- Join table: PRIMARY KEY (left_id, right_id), no id column.
```

Entity side: `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;` on a plain `@MappedSuperclass`-free
entity (there is no `TenantEntity`). No changeset writes a GRANT (decision R6).

Platform's own changesets in the tenant changelog (owner of the master: `be-foundation`, decision P1):

```sql
--changeset platform:051-platform-tenant-identity
CREATE TABLE tenant_identity (
    singleton  BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),   -- at most one row (AC-001.28)
    tenant_id  UUID    NOT NULL
);

--changeset platform:052-platform-tenant-guard-function splitStatements:false
CREATE FUNCTION tenant_guard_ok() RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid = (SELECT tenant_id FROM public.tenant_identity)
$$;

-- 053-platform-tenant-guard-policies.sql: a plain SQL file, no formatted header; the XML changeSet below runs it
DO $$ DECLARE t record; BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public'
           AND tablename NOT IN ('tenant_identity', 'databasechangelog', 'databasechangeloglock') LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
    EXECUTE format('DROP POLICY IF EXISTS tenant_guard ON public.%I', t.tablename);
    EXECUTE format('CREATE POLICY tenant_guard ON public.%I FOR ALL USING (tenant_guard_ok()) WITH CHECK (tenant_guard_ok())', t.tablename);
  END LOOP;
END $$;
```

```xml
<!-- last element of db.changelog-master.xml; Liquibase 4.27.0 reads runOrder only in XML -->
<changeSet id="053-platform-tenant-guard-policies" author="platform" runAlways="true" runOnChange="true" runOrder="last">
    <sqlFile path="changes/053-platform-tenant-guard-policies.sql" relativeToChangelogFile="true" splitStatements="false"/>
    <rollback>(drop tenant_guard and disable RLS on the same tables)</rollback>
</changeSet>
```

An unset or empty setting, or a missing `tenant_identity` row, makes the function return NULL: no row is visible and
every write fails the `WITH CHECK` (fail closed; AC-001.30). The legacy tables of AC-001.21 get the policy too.
Decided in planning (KAN-253, Q3): the installer is an XML changeSet because Liquibase 4.27.0 (Spring Boot 3.3.5 BOM)
reads `runOrder` only in XML; its formatted-SQL parser has none. `runOnChange` keeps a later edit of the installer from
failing checksum validation on existing databases. If a test shows that `runOrder="last"` does not reorder across
includes, the same SQL runs as the last step of `TenantMigrator` (Task 7), as its own task.
Decided in planning (KAN-253, Q5): the function reads `public.tenant_identity` by its full name, so a `pg_temp`
table of the same name cannot shadow it. Numbers 051 - 053 are the next free ones at 60594a7c; renumber on rebase if a
parallel branch takes them.
Decided in planning (KAN-253, Q4): a malformed `app.tenant_id` (not a UUID, not empty) makes the function raise SQLState
22P02, which is a refusal: no row is ever returned. The app always sets a UUID from the bound id.

### Roles and grants (decision R6)

- `infra/postgres/init/10-tsolve-roles.sh` runs once on a new PostgreSQL volume (and in Testcontainers): the owner role
  `tsolve_<env>_owner` (LOGIN, `CREATEDB`, `CREATEROLE`, not superuser; it owns every database it creates and runs
  Liquibase); the Registry DB with its one owner user (`REGISTRY_DB_USER`, no runtime split yet) and the Jobs DB with its user
  (`JOBS_DB_USER`); neither user is a
  superuser nor `BYPASSRLS`, each `CONNECT` on its own database only); the **template database** `tsolve_template` with
  `CREATE EXTENSION vector` already done (only the superuser can, so a Workspace database is created `TEMPLATE
  tsolve_template` and the owner never needs superuser).
- The Jobs user owns the tables JobRunr creates in the Jobs DB (JobRunr needs DDL; that database has no policy, so
  AC-001.32 does not apply to ownership there; `worker` leaves table creation to JobRunr — this replaces the old
  "who creates the JobRunr tables" item).
- Per Workspace, `TenantDatabaseProvisioner` (as the owner user) runs the SQL of provisioning, in order: `CREATE ROLE
  <db_user> LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE`; `CREATE DATABASE <db_name> OWNER <owner> TEMPLATE
  tsolve_template`; `REVOKE ALL ON DATABASE … FROM PUBLIC; GRANT CONNECT ON DATABASE … TO <db_user>` (AC-001.26);
  inside the new database `GRANT USAGE ON SCHEMA public`, `ALTER DEFAULT PRIVILEGES FOR ROLE <owner> IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES` and `GRANT USAGE, SELECT ON SEQUENCES` to `<db_user>` (so every
  future table is reachable without a GRANT in a changeset; the runtime user is never granted TRUNCATE, which ignores row
  policies); then the tenant changelog; then `INSERT INTO
  tenant_identity` and `REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON tenant_identity FROM <db_user>` (AC-001.28).
- `db_name = db_user = tsolve_<env>_ws_<32 hex of the Workspace id>` (50 characters, within PostgreSQL's 63).

### Convention guard test data

- `ALLOW_LIST` (permanent, with reasons): `databasechangelog`, `databasechangeloglock` (Liquibase), `tenant_identity`
  (AC-001.28). `LEGACY_ALLOW_LIST` (fixed set, can only lose entries; AC-001.21, changed 23/11): `legacy_workspace`,
  `workspace`, `department` → `workspace`; `app_user`, `user_role`, `space`, `jira_account_links` → `identity`;
  `import_batch`, `import_preview`, `import_preview_row` → `import-ticket-list`; `project` → `project-integration`;
  `suggestion_result` → `tracker`. The five tables of changeset 039 are not on it; an entry that names a table that does
  not exist fails the test.
- The test checks the `TIMESTAMPTZ NOT NULL` rule and the append-only rule only; which timestamp columns a table has
  stays a review rule (AC-001.20, Q7). The key `workspace_id` of the table `workspace` is not a "Workspace-id column".
- The test applies the tenant changelog to an empty database and reads `information_schema` and `pg_catalog`: names,
  keys, defaults, `relrowsecurity`, `pg_policies` (one policy `tenant_guard` per table, `cmd = ALL`, permissive, its
  `USING` and `WITH CHECK` equal to `tenant_guard_ok()`), role attributes (`rolsuper`, `rolbypassrls`) and table owners.
- A join table is recognised by structure (a composite primary key of two `<entity>_id` columns and no `id`); an
  append-only table by `COMMENT ON TABLE <t> IS 'tsolve:append-only'`; only such a table may lack `updated_at`.
  `audit_log_entry` has no such comment yet: its owner (`dashboard-audit`) adds a one-line COMMENT changeset as a quick
  change before the guard test is written (KAN-253, Q6); until then it would fail the test and the list may not grow. A table
  with a column named like a Workspace id fails ("no Workspace-id column", AC-001.20).
- The isolation and guard tests add a test-only table `isolation_probes` after the changelog (never in production) and
  run the guard installer again, so the policy is applied to it. The guard test runs before it exists.

### Registry rules (WorkspaceCatalog; AC-004.17 – AC-004.29)

| Operation | Check, in order | Code |
|-----------|-----------------|------|
| `linkJiraSite(url)` | bound Workspace has no Jira site (else refuse, AC-004.23); URL parsed with `java.net.URI`: scheme `https`, a host, no user info, no port, path empty or `/`, no query, no fragment (else `MSG156` on `jiraSiteUrl`); normalised to `https://<host>` lower-case; `cloudId` read from `<url>/_edge/tenant_info` (failure → nothing stored, logged, exception); conditional `UPDATE … WHERE jira_cloud_id IS NULL`, a unique violation on `jira_cloud_id` → `MSG153` with `tracker_name` = `Jira` on `jiraSiteUrl` (never a 500, AC-004.24) | `MSG156`; `MSG153` |
| `claimEmail(email)` | lower-case, trimmed; `INSERT … ON CONFLICT (email) DO NOTHING`, then read the row: same Workspace → success, another → `MSG155` on `email`, nothing recorded | `MSG155` |
| lookups | `findById`, `findBySiteName` (compared without case through the lower-case column), `findByCloudId`: one indexed query, empty when none; they return no connection data | — |
| `findActive()` | `SELECT … WHERE status = 'ACTIVE'`; one query, returns `WorkspaceSummary` (no connection data), empty list when none; binds nothing (AC-004.29) | — |

`WorkspaceCatalog` reads and writes Registry tables only and never binds a Workspace (AC-004.27). Error bodies carry
`code` and `params` as `be-foundation` AC-001.1 defines them; `params` is a JSON object, for example `{"tracker_name":
"Jira"}`.

### Provisioning (STORY-013; AC-013.1 – AC-013.24)

`WorkspaceProvisioning` (spec contract) is implemented by `WorkspaceProvisioningService`. The script
`infra/ops/workspace.sh` only starts the backend's `ops` profile with the arguments; it holds no logic (AC-013.13).

`create(siteName, adminEmail, googleDomain)`:

1. Validate (AC-013.2 – AC-013.4, AC-013.21): label rule, reserved names, site name taken, email format, Google domain. Pre-check that the
   lower-case email is in no Workspace → else `MSG155` before anything is created (AC-013.5). Generate the Workspace id.
2. `TenantDatabaseProvisioner`: create role and database, grants (above), generate a random password.
3. `TenantMigrator`: apply the tenant changelog as the owner user; read the last changeset id.
4. Write `tenant_identity` (AC-001.28) and revoke writes (above).
5. Registry transaction: insert `tenant_workspace` (ACTIVE, encrypted password, `schema_version`, `google_domain`) and
   `workspace_email` together. A unique violation on `site_name` or the email means a lost race → the same refusal as
   step 1 (AC-013.19). A Workspace is Active here; there is no `PENDING_SIGN_IN` and no `activate` (AC-013.24).
5b. `WorkspaceSignInProvisioner.provision(workspaceId, siteName, googleDomain)` (Keycloak Organization, IdP
   `google-<slug>`, domain, redirect URI; identity-owned port, a no-op bean until its Keycloak implementation exists);
   update `keycloak_organization_id` with the id it returns (AC-013.22).
6. `TenantContext.runIn(workspaceId, () -> firstAdminCreator.createFirstAdmin(workspaceId, email))`, which returns the
   Account `UUID` (AC-013.14). `identity` also calls `claimEmail`; it succeeds without change because step 5 already
   recorded it.
7. After the commit: `InvitationJobQueue.queueFirstAdminInvitation(workspaceId, accountId)` — carries ids only
   (AC-013.17). A failure here is logged without the email and does not undo anything (AC-013.18).

Any failure in steps 2–6 runs the compensation (AC-013.6, AC-013.16, AC-013.23): call `WorkspaceSignInProvisioner.remove(workspaceId)`
when step 5b was reached, close and evict the pool of that Workspace, delete
the Registry rows (`workspace_email`, `tenant_workspace`) in one transaction, `DROP DATABASE … WITH (FORCE)`, `DROP ROLE`;
the failing step is named in the error. `suspend` / `reactivate` change the Registry `status` in one transaction and
publish `WorkspaceSuspended` / `WorkspaceReactivated` after the commit (a `TransactionSynchronization`).

`InvitationJobQueue` is a port declared in `platform`. User decision of 4/11: `platform` provides a minimal implementation
on JobRunr in the Jobs DB (it owns the Jobs DB) and `worker` takes the job handler over later. The
`ops` and Console profiles refuse to start without a bean for it and for `FirstAdminCreator` (AC-013.15).

### Storage layout (STORY-007)

One bucket `FILE_STORAGE_BUCKET`. Object name: `<workspaceId>/<uuid v4>`; the **key** the caller holds is only the UUID.
`store` reads the stream into memory up to `FILE_STORAGE_MAX_BYTES` (+1 byte to detect the overflow), throws
`FileTooLargeException` before sending anything, and otherwise writes it with one `PutObject` of known length: a single
request is atomic, so a failed write leaves no object and no multipart upload (AC-007.6, AC-007.8) and the backend
writes no file to disk (AC-007.14). Every object is stored with content type `application/octet-stream`. A key is
valid only if it is a canonical lower-case UUID string; anything else is refused with `InvalidFileKeyException`
(extends `NotFoundException`, 404 `MSG06`), logged, before the object store is touched (AC-007.5). `read`, `delete` and
`signedUrl` always build the object name from the bound Workspace's id (AC-007.3, AC-007.4, AC-007.7). `signedUrl`
first confirms the object exists (`HeadObject`; AC-007.13), then signs offline.

#### Signed URL host and routing (decision R11)

The signature covers the host and path Garage sees, so the URL must be signed for the address the browser uses, and
Caddy must forward it unchanged.
- The signed URL is `FILE_STORAGE_PUBLIC_URL_PATTERN` with the bound Workspace's site name, path-style:
  `https://<site>.<base>/<bucket>/<workspaceId>/<uuid>?X-Amz-…` (e.g. pattern `https://{site}.tsolve.io.vn`;
  locally `http://{site}.localhost`). The backend finds the site name through `WorkspaceCatalog.findById`.
- The signer is created with this public endpoint (signing needs no network call); reads and writes use the internal
  endpoint `FILE_STORAGE_ENDPOINT`.
- Caddy, on every Workspace site, routes `GET`/`HEAD` of `/<bucket>/*` to `garage:3900` with the original `Host` header
  (Caddy's default), before the web-app route; other methods get 405. Garage has no published port. The route adds
  `X-Content-Type-Options: nosniff` and removes the signature parameters from the access log. The bucket name is
  reserved as a path on the Workspace site (the web app has no such route).
- What keeps the signature valid (RESEARCH-005): the route never rewrites the path (`handle`, not `handle_path`); the
  host and port of `FILE_STORAGE_PUBLIC_URL_PATTERN` are the ones the browser sends (locally Caddy on port 80 and
  `http://{site}.localhost`, no port); `garage.toml` sets no `root_domain`, so Garage reads every host as path-style.
- Locally the Caddy site address is `http://*.localhost` (scheme written out), or the global option `auto_https off`.
  A bare `*.localhost` turns on Caddy's automatic HTTPS (internal CA) and redirects port 80 to 443, so a signed `GET`
  on `http://acme.localhost/…` gets a 308 instead of the file.
- `HEAD` is only passed through, not served: `signedUrl` presigns `GetObject` only and the signature covers the
  method, so a `HEAD` with that URL reaches Garage and is refused there (400 or 403). AC-009.2 asks for no `HEAD`
  download; Caddy lets `HEAD` through so that it is not answered 405 like a write. Presign `HeadObject` too only if a
  criterion ever asks for a working `HEAD`.
- Caddy details: `nosniff` is set with `header_down` inside `reverse_proxy`; the access log of every site uses the
  `filter` format (`wrap json`) with `request>uri query { delete X-Amz-Signature; delete X-Amz-Credential }`; the bucket
  path comes from `{$FILE_STORAGE_BUCKET}`, which the `caddy` service of both compose files receives as a required
  variable.
- No new host, no new certificate (the Workspace wildcard covers it) and no new reserved site name. TTL is
  `FILE_STORAGE_URL_TTL_SECONDS`.

### Seed data (STORY-012)

`infra/seed/demo-workspaces.json`: `{ "workspaces": [ { "siteName": "…", "adminEmail": "…", "modules": { "identity": {…},
"workspace": {…} } } ] }`. The runner creates each Workspace through `WorkspaceProvisioning.create` (AC-012.1) and gives
each `DemoSeedContributor` the `modules.<module>` part, where `<module>` is the contributor's package segment after
`vn.tsolve.` (Decided in planning). `run-seed.sh --reset` (AC-012.8, AC-012.9): checks `TSOLVE_ENVIRONMENT` is `dev` or
`staging` first, stops the backend and worker, drops every Workspace database named in the Registry, empties the
Registry tables and the bucket (`aws s3 rm --recursive` with `--endpoint-url $FILE_STORAGE_ENDPOINT`; the AWS CLI speaks the same S3 API as W1), re-applies the Registry changelog, starts the stack and
runs the seed. No argument: one source for the environment.

## Redis (HLA Q3; AC-008.10, AC-010.11)

One container (`redis:7`, pinned), `appendonly yes`, `requirepass` from `REDIS_PASSWORD`, `maxmemory-policy noeviction`,
no published port, volume, memory limit, health check (`redis-cli ping`). It holds the sessions of `identity`
(`spring.data.redis.*` from `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`); `platform` adds no Redis code. The backend
and worker containers `depends_on` Redis `service_healthy`, so the web process does not start healthy without it; the
Redis health indicator that reports it while running arrives with `identity`'s Redis dependency. Convention for any
other use of Redis: a key starts with `t:<workspaceId>:` for tenant data, so one Workspace's keys never collide with
another's; no such use exists yet (see Out of Scope of the spec).

## Security chains (order)

1. `TenantResolverFilter` — `FilterRegistrationBean`, order `SecurityProperties.DEFAULT_FILTER_ORDER - 10`, REQUEST and
   ASYNC dispatch, so it runs before Spring Security's `DelegatingFilterProxy` (AC-002.8). Active only with a catalog
   bean (The web resolver, Task 10).
2. `ForgeSecurityConfig.forgeChain` — `@Order(1)`, `securityMatcher` = host is `api.<base>`; stateless, no CSRF, no
   session cookie (AC-005.6); `/actuator/health` permitted, `/api/v1/jira-app/**` authenticated by `ForgeTokenFilter`,
   everything else denied (404 `MSG06`, AC-005.7). On a Workspace site, `login.` or `admin.` a path under
   `/api/v1/jira-app/` answers 404 `MSG06` and no Forge token authenticates (AC-005.8).
3. `identity`'s chain for `/api/v1/auth/**`, then `be-foundation`'s session chain (their specs).

## Infrastructure

| Environment | Runs | Files |
|-------------|------|-------|
| Local (Docker Compose) | PostgreSQL 16 + pgvector (Registry, Jobs and Workspace databases), Redis, Garage (+ one-shot bucket init), backend, worker, web app, Ollama (+ one-shot model pull), Caddy (HTTP only, site address `http://*.localhost`) | `docker-compose.yml`, `infra/caddy/Caddyfile.local` |
| Application VPS — edge | Caddy (custom image: Cloudflare DNS module for wildcard certificates by DNS-01, and a rate-limit module), PostgreSQL (one instance, shared by both environments); Redis and Garage are **not** here | `infra/compose.edge.yml`, `infra/caddy/Caddyfile`, `infra/postgres/init/` |
| Application VPS — staging (production the same, later) | backend, worker, web app (images `:staging`), **its own Redis and its own Garage** (+ one-shot `garage-init`); migrate, seed and ops as one-off runs | `infra/compose.staging.yml` (`compose.prod.yml` later) |
| AI VPS | Ollama as a native systemd service bound to the WireGuard address (decision P11) | `infra/scripts/ollama-setup.sh`, `infra/scripts/wireguard-setup.sh` |

- PostgreSQL: `max_connections = 300` in the compose file (decision R2: Workspaces in use × `TENANT_DB_POOL_MAX` × 2
  (backend and worker) + 2 × 5 Registry + Jobs (web 2 × 3, worker `TSOLVE_WORKER_COUNT` + 2) + Keycloak
  (`KC_DB_POOL_MAX_SIZE` 15) + migration (1 per process) must stay within `max_connections` − 3 reserved: 289 ≤ 297),
  `shared_buffers` and memory limit as the staging VPS allows.
  `infra/compose.staging.yml` still has 100; Task 6-2 raises it to 300, and the `db` container is recreated (restarts)
  by hand right after that deploy, because CD does not apply it.
- Redis and Garage run **one per environment** (in the staging or production compose file, not in the shared
  `compose.edge.yml`), so that sessions and files of staging and production never mix (AC-008.10, AC-008.11, AC-010.11).
- Garage (S3-compatible, ADR-014): one container (pinned `dxflrs/garage` image), single node with
  `replication_factor = 1`, persistent data and metadata volumes, no published port (the S3 API is internal). A one-shot
  `garage-init` (`infra/scripts/garage-init.sh`) assigns and applies the node layout, creates `FILE_STORAGE_BUCKET` if
  absent (private, no anonymous access) and one access key with read/write on that bucket only; the backend never uses
  the admin credentials (`FILE_STORAGE_ACCESS_KEY`, `FILE_STORAGE_SECRET_KEY`). Backend and worker `depends_on`
  `garage-init` completed. The S3 client uses path-style addressing and a fixed region name.
- DNS: the `tsolve.io.vn` zone moves to Cloudflare (free plan). Records `tsolve.io.vn`, `*.tsolve.io.vn`,
  `*.staging.tsolve.io.vn` → application VPS, DNS only (not proxied), so Caddy terminates TLS and sees client addresses.
  The API token `CLOUDFLARE_API_TOKEN` has Zone:DNS:Edit and Zone:Read on that zone only and lives only in the edge
  `.env` on the server (decision Q12).
- Private link: WireGuard `wg0` between the two VPS (`10.66.0.1` application, `10.66.0.2` AI). Keys are generated on
  each host (`/etc/wireguard`, mode 600), never in the repository; `wg-quick@wg0` is enabled so the tunnel comes back
  after a reboot. On the AI VPS, ufw allows UDP 51820 only from the application VPS's public address and TCP 11434 only
  from `10.66.0.1` on `wg0`; Ollama listens on `10.66.0.2:11434` only. `OLLAMA_BASE_URL` = `http://10.66.0.2:11434`
  (decision Q13).
- Images: `tsolve-backend` (also worker, migrate, seed and ops, by profile), `tsolve-frontend`, `tsolve-caddy`, built in
  CI and pushed to GHCR under the repository owner (`ghcr.io/<owner>/…`, tags `sha-<commit>` and `staging`) (decision
  P9).
- Deploy (as built for KAN-14, user decision 13/11: an SSH forced-command deploy key instead of the runner below, build
  on the VPS instead of GHCR, no migrate or seed step; see Key Decisions "KAN-14 CD deviation"): the `cd-staging`
  workflow runs on a self-hosted runner on the application VPS (label `tsolve-deploy`,
  non-root `deploy` user, systemd service with CPU and memory limits): pull → migrate (profile `migrate`: Registry
  changelog, then the tenant changelog for every Workspace, stopping and naming the first failing Workspace) → up →
  health → seed → smoke test.
- Edge limits (STORY-014; AC-014.9 – AC-014.11): Caddy `request_body { max_size {$EDGE_MAX_BODY_BYTES} }` (413),
  `servers { timeouts { read_body, read_header, write, idle } }` from `EDGE_TIMEOUT_SECONDS`, and a rate-limit zone per
  client IP and one per Workspace site host (429 with `Retry-After`), values from `EDGE_*`. An infra check fails if
  `EDGE_MAX_BODY_BYTES` is not larger than `FILE_STORAGE_MAX_BYTES`.
- Operations (STORY-014; AC-014.1 – AC-014.8, AC-014.12, AC-014.13): `infra/scripts/backup-db.sh` lists the Workspace
  databases from the Registry and writes one compressed custom-format dump per database (Registry, Jobs, each
  Workspace) plus a copy of each Workspace prefix of the bucket, sends them with `restic` (encrypted, deduplicated) to a
  Cloudflare R2 bucket limited by its own token (W5, ADR-015), keeps seven days (`restic forget`), exits non-zero naming the database or prefix that failed, and raises the alert. `CATALOG_SECRET_KEY` is
  kept apart from the dumps and left out of the backed-up `.env` copy (the restic password is kept offline by two people); Redis is not backed up. `infra/RESTORE.md` restores one Workspace onto staging under new
  database name, user and password and rewrites its Registry row for staging (the restored row never carries a
  production name, user or password, AC-014.5), and records the time. Monitoring (W3): the backend and worker expose metrics; Grafana
  Alloy is the one collector (the backend and worker metrics, host metrics, PostgreSQL, Redis and the Garage metrics
  endpoint, and container logs to Loki), Prometheus stores the metrics, one Grafana dashboard shows them including
  Redis memory, clients and command rate, and Grafana-managed alert rules (no separate alert-routing service) cover Redis, the Jobs DB
  and Garage unreachable and a failed backup. Grafana is served on `grafana.<base>` (reserved site name, AC-013.3).
  A load-test script (k6, W4) under `infra/loadtest/` with the thresholds in the script. The stack runs in
  `compose.monitoring.yml`; STORY-014 is built after 15/11 (DevOps Phase B), except the edge limits (DevOps step A5).
  The tools are the decisions W1 – W5 below.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Model | One database per Workspace, a Registry DB, a Jobs DB; no Workspace-id column on tenant tables (ADR-0002, GBR-37) | Shared database with a per-row filter and a row policy on a Workspace-id column | User, 29/10; HLA, 3/11 |
| Spec Q1–Q10 of 4/11 | As recorded in the spec Changelog (recommended options; Q9 answered: the invalid-URL refusal uses the new code `MSG156` (AC-004.21), not `MSG04`; Q10 403 `MSG150` for a Suspended Workspace on the Jira path) | see the spec | User, 4/11 ("all recommendations") |
| H-Q1 Deployment | Docker Compose on a VPS for the MVP and the demo; Kubernetes and high availability are the target only; Redis is one container with persistence | Build for Kubernetes now | Hiếu, 4/11 |
| H-Q3 Jobs DB | `tsolve_<env>_jobs` on the same server, own user, JobRunr's tables only | JobRunr tables in the Registry DB | Hiếu, 4/11; the Registry stays small and restorable on its own |
| H-Q4 Object storage | Garage (S3-compatible; ADR-014); one per environment; object names `<workspaceId>/<uuid>`; signed URLs; no local files | Local volume; a cloud bucket; the store first named on 4/11 (its community images stopped) | Hiếu, 4/11 and the user, 4/11 (Q1); any API replica can serve any file |
| H-Q5 Provisioning | One `WorkspaceProvisioning` service; the script and the Console both call it; the first Admin is invited | Script with its own SQL | Hiếu, 4/11 |
| H-Q6 Row guard | One-row `tenant_identity`; policy `tenant_guard` on every tenant table comparing it with the transaction's `app.tenant_id`; no `BYPASSRLS`; guard test enforces it | Boundary only | Hiếu, 4/11 |
| KAN-14 CD deviation | The `cd-staging` workflow SSHes from a GitHub-hosted job to the VPS with a dedicated deploy key whose forced command is `/usr/local/sbin/tsolve-deploy` (root-owned, installed by hand); images are built on the VPS, not pushed to GHCR; CD runs no migrate or seed step and no automatic rollback | Self-hosted runner and GHCR (DevOps design §5.5, P9) | User, 13/11 (KAN-14). STORY-010 AC-010.2 – AC-010.6 are knowingly not met yet; the spec is unchanged by the user's choice |
| P1 Shared changeset 001 | `be-foundation` (Trần Hải Anh) owns the master and decides who edits changeset 001; the legacy `workspace` table leaves the tenant changelog, its company data stays with `workspace`; platform's changesets are added after it | platform rewrites 001 | Ownership stays with `be-foundation` (user, 25/10) |
| P2 Shared files | `backend/pom.xml`, `backend/src/main/resources/application*.yml`, `backend/.dockerignore` stay in platform `paths`; `be-foundation` stays the owner | quick change per edit | The gate needs them open (user, 25/10) |
| P3 JWT library | `spring-security-oauth2-jose` (Nimbus `NimbusJwtDecoder.withJwkSource`), version from the Spring Boot BOM — approved by the user, 25/10 | full resource-server starter; jjwt | Key-set cache with one refetch on an unknown key id (AC-005.4) |
| P5 `runIn` transactions | Bind only; transactions stay with the services' `@Transactional` (changed for Task 6, Hiếu, Q1 = A) | Always a new transaction (`REQUIRES_NEW`); join the caller's | A new transaction would hold a pool connection through AI calls and whole requests. AC-002.23 still holds: the pool is chosen when `doBegin` takes the connection, an unbound transaction throws, a switch is refused |
| P7 Seed | The seed runner creates Workspaces through provisioning; CD runs it on staging after each deploy; contributors come with S3 | create the staging Workspace by hand | AC-010.4 needs a staging Workspace; D42 allows the seed on staging |
| P9 Caddy image | Built in CI from `infra/caddy/Dockerfile` (xcaddy with the Cloudflare DNS module and the rate-limit module), pushed to GHCR as `tsolve-caddy` | build on the VPS | Same path as the other images; pinned and reproducible |
| P11 Ollama | Native systemd service, `OLLAMA_HOST=10.66.0.2:11434`, `After=`/`Requires=wg-quick@wg0` | Docker container | No Docker networking to reconcile with ufw and `wg0` |
| Q12 / Q13 | Cloudflare DNS-01 wildcards `*.tsolve.io.vn` (+ apex) and `*.staging.tsolve.io.vn`; WireGuard `wg0` for Ollama | on-demand TLS; Tailscale | User, 25/10 |
| R1 Registry access | `JdbcClient` on a `registryDataSource` bean with its own transaction manager; the primary `DataSource` is the routing one where routing is on. `identity`'s repositories for its Registry tables use `registryDataSource` | A second JPA persistence unit | A few small tables, no entities; two JPA units multiply Boot configuration |
| R2 Routing and pools | `AbstractRoutingDataSource` subclass overriding `determineTargetDataSource()`; one Hikari pool per Workspace opened on first use, `minimumIdle` 0, `idleTimeout` 10 min, `TENANT_DB_POOL_MAX` 5 (staging 10); PostgreSQL `max_connections` 300 | pgbouncer; one pool for all with `SET search_path` | Bounded by Workspaces in use (AC-002.22); an idle Workspace holds nothing |
| R3 Tenant setting | `app.tenant_id` set from the **bound** id: `set_config(…, true)` in `doBegin`; checkout-and-reset wrapper outside a transaction | Set once per pool from its own id | A guard that copies the pool's own id would never catch a misroute (AC-002.25) |
| R4 Policy installation | One `runAlways`, `runOrder="last"` changeset in the master installs policy `tenant_guard` on every table; `tenant_guard_ok()` holds the condition; no module writes a policy | Each module writes its policy; trigger on `ddl_command_end` | A forgotten policy cannot happen; the condition lives in one function; the guard test proves it |
| R5 No `FORCE` | The owner is not forced; the runtime user is never the owner | `FORCE ROW LEVEL SECURITY` | Migrations and backfills must run unfiltered |
| R6 Roles and grants | Owner role with `CREATEDB`/`CREATEROLE`; a template database with `vector`; per-Workspace runtime user created by provisioning with default privileges; no GRANT in changesets | Superuser for provisioning; GRANT per changeset | No superuser at runtime; every future table is reachable |
| R7 Names | `tsolve_<env>_ws_<32 hex>` for database and user | Site name in the database name | A site name never changes the database; no reserved-word or length trouble |
| R8 Password storage | AES-256-GCM, random nonce, Workspace id as associated data, key `CATALOG_SECRET_KEY` | Application-level hash; pgcrypto | The password must be recoverable to connect |
| R9 Provisioning order | As AC-013.1: database, changelog, `tenant_identity`, Registry rows (the first and only commit that makes the Workspace visible), first Admin, then the invitation after the commit; saga compensation drops everything | Registry row first with a "provisioning" status | The spec's status set is ACTIVE or SUSPENDED only; a failed run leaves no visible Workspace |
| R10 Invitation | `InvitationJobQueue` port declared in `platform`, with a minimal implementation in `platform` on JobRunr in the Jobs DB; `worker` takes the job handler over later; the job carries the Workspace id and the Account id | Implemented by `worker` only (blocks the ops script until `worker` is built); a table of our own | User decision, 4/11: `platform` owns the Jobs DB, so the first Admin's invitation does not wait for `worker`; the job holds no personal data. Its task is Task 8 of iteration 1 |
| R11 Signed-URL host | Path-style URL on the Workspace's own site: `/<bucket>/<workspaceId>/<uuid>`, signed for `FILE_STORAGE_PUBLIC_URL_PATTERN`; Caddy forwards `GET`/`HEAD` of `/<bucket>/*` to Garage with `Host` unchanged | A dedicated `files.` host (a new reserved site name); streaming every file through the backend; a rewritten path (breaks the signature) | No new host, certificate or reserved name; same origin; Garage stays private; signature remains valid |
| R12 Object content | Stored as `application/octet-stream`; Caddy adds `nosniff` on that route | Content type from the caller | A stored HTML file is downloaded, never rendered on a Workspace site |
| R13 In-memory upload | `store` buffers up to 10 MB in memory and writes with one `PutObject` | Multipart upload; spool to disk | Atomic, no partial object, no local file (AC-007.8, AC-007.14) |
| R14 Migrations | The web process applies the Registry changelog, then `TenantMigrator` for every Workspace (a bean the routing `DataSource` depends on, so it finishes before the server accepts requests); after each success it writes `schema_version`; a failure stops startup naming the Workspace (AC-008.9). CD also runs the `migrate` profile before `up` (AC-010.3). The worker never runs Liquibase | Migrations only in CD | Both criteria hold; a failed migration stops the deploy before new containers start |
| R15 Hibernate startup | `hibernate.boot.allow_jdbc_metadata_access=false`, explicit dialect, `open-in-view=false`; `ddl-auto: none` where routing is on, `validate` where it is off | Bind a dummy Workspace at startup; `none` everywhere | No Workspace is bound at startup; the routing `DataSource` throws without one |
| R16 Redis | One container, persistence, `noeviction`; sessions only; `t:<workspaceId>:` key convention for any later tenant use | Two Redis (cache apart) | HLA Q3 and H-Q1; there is no cache yet |
| R17 Adopting bluefin | One idempotent script `infra/scripts/registry-adopt-workspace.sh`, run once per environment on the VPS or locally; the AES-GCM password comes from `RegistryPasswordCli` (a plain `main` in the backend image, no Spring context); `INSERT … ON CONFLICT (workspace_id) DO NOTHING`, and an existing row with other values is refused naming the field. The Workspace UUID comes from the script-only variable `TSOLVE_DEMO_WORKSPACE_ID`. Adopting an existing Workspace is not "creating" one, so AC-001.19 ("only STORY-013 creates Workspaces") is untouched | A Spring one-shot runner (boots the whole app for one row); adoption at startup from env (an env stand-in under a new name) | `openssl` cannot do GCM and the images have no Python; the script leaves nothing in the runtime path (Task 4a, Hiếu answered Q1 = A, 24/11) |
| R18 Jira site of the adopted row | The script copies `jira_site_address` and `jira_cloud_id` (lower-case) from bluefin's own tenant `workspace` table (`WorkspaceInfo`, written by Setup) when that row exists, both or neither; with no tenant row it writes the bluefin row without a Jira site and prints one line saying so | Ask for the site URL and cloud id (a hand-typed value can mismatch or be upper-case); write a cloud id with no tenant row (AC-004.23 would then refuse the Admin's Setup in Task 18) | The cloud id already lives in the tenant database; copying it avoids a variable and a dead end (Q8, replaced by Hiếu, 24/11) |
| R19 Catalog lookups outside `dev` | `PlatformWorkspaceLookup` (identity port), `CatalogJiraWorkspaceLookup` (jira-app port) and `CatalogWorkerTenantService` (worker port, only when `tsolve.worker.enabled=true`) read `WorkspaceCatalog`; the first two through `ObjectProvider`: with no Registry bean they answer empty (the code gate stays, a later task drops it; `dev` keeps `DevWorkspaceLookup`). The worker service is the exception: its constructor takes `WorkspaceCatalog` directly (not an `ObjectProvider`), so a worker with job processing on and no catalog bean (Registry gate off) FAILS STARTUP with an error naming `WorkspaceCatalog` (worker AC-002.12, AC-003.3: never a silent empty list that skips every Workspace; on staging compose already requires `REGISTRY_DB_URL` in the worker). A Registry failure at run time propagates from every method, `enqueueWorkspaceId` included (never "no Workspace"; the enqueuer logs and queues nothing). Decided 24/11 (Q1, Q2 = recommended, 4c). `CatalogJiraWorkspaceLookup` is unconditional, because five jira-app services and `workspace.JiraAppTokenServiceImpl` inject `JiraWorkspaceLookup` in every context. The host maps to the site name through `TSOLVE_SITE_ORIGIN_TEMPLATE` (`{slug}`). No cache | A required `WorkspaceCatalog` parameter (breaks every full-context test with the gate off); a new host variable; an in-memory catalog for local runs (an env stand-in again) | One indexed query per lookup on the Registry pool; Task 10 may add a cache (Q3, Q4 = recommended, 24/11) |
| R20 `claimEmail` users | None in Task 4: `claimEmail` is tested only in `WorkspaceCatalogIT`. identity Task 14 wires `WorkspaceEmailRegistry` to the catalog and backfills bluefin's Account emails then (lower-case `app_user` emails, `ON CONFLICT (email) DO NOTHING`); its adapter translates `claim` (boolean) to `claimEmail` (void, throws `MSG155`) and needs a `release` the contract lacks | A backfill in the adopt script (stale: until the adapter is live `AcceptAllWorkspaceEmailRegistry` records nothing) | Q9 = A (Hiếu, 24/11) |
| T10 request binding (Task 10, Q2) | Public `bindRequest(UUID)` returning an `AutoCloseable`, no transaction, no existence check; only `TenantResolverFilter` calls it (source-scan test) | `runIn` around the whole request (Task 6 would rework two filters for P5); the filter in `common/tenant` behind a new port | The filter is in `platform/web`, so a package-private pair cannot be reached (Hiếu, 25/11) |
| T10 resolver activation (Q3) | Only with a `WorkspaceCatalog` bean (`ObjectProvider`) | always on behind a second lookup port; always on and refusing every host | Staging always has the Registry; local runs, Playwright and about 50 ITs keep working (Hiếu, 25/11) |
| T10 base domain (Q4) | The host part after `{slug}.` of `TSOLVE_SITE_ORIGIN_TEMPLATE` (AC-002.2) | a `TSOLVE_BASE_DOMAIN` variable; passing `TSOLVE_DOMAIN` to the backend | One source of truth (Hiếu, 25/11) |
| T10 host header (Q5) | The raw `Host` header; a differing `X-Forwarded-Host` fails closed later (for a site or `api.` `Host`; Edge #92) | refuse such a request in the resolver; switch the forwarded-header strategy off | AC-002.3; https redirect URIs need the strategy (Hiếu, 25/11) |
| T10 refusals (Q6, Q7) | problem+json on every path; `MSG150` / `MSG151` with the SRS texts in `SecurityProblemWriter` | redirects on the sign-in paths; a second writer `ProblemResponses` | AC-002.7, AC-002.9; one writer (Hiếu, 25/11) |
| T10 Jira App paths (Q8) | Closed on every host but `api.` in the resolver (404 `MSG06`); the staging Forge `baseUrl` is checked before the deploy | leave to Task 20 | AC-005.8 is open today (Hiếu, 25/11) |
| Tenant context storage | Static `ThreadLocal` behind the `TenantContext` bean | `ScopedValue` (preview in Java 21) | The routing `DataSource` and the transaction manager are not Spring-injected into every caller (Decided in planning) |
| Resolver cache | None: one lookup by the unique `site_name` index per request | cache with eviction | The next request must see a suspend at once; the seed and the web run in different processes (Decided in planning) |
| Health and API docs | `/actuator/health`, `/v3/api-docs*` and the Swagger UI pass every host check (AC-002.19) | classify like other paths | Docker health checks and `npm run api:pull` call `localhost:8080` |
| Tenant information lookup | `RestClient`, `https` only, 5 s connect and read timeouts, no redirects, host must resolve to public addresses only (no IP literal, private, loopback or link-local), response capped at 64 KB; any failure → store nothing, logged, exception to the caller (AC-004.22) | open call | An Admin-entered URL must not turn into a request to an internal host (Decided in planning) |
| Forge key set and expiry | `NimbusJwtDecoder.withJwkSource` over a `JWKSource` with caching and no rate limit, so an unknown key id triggers exactly one refetch (AC-005.4); `JwtTimestampValidator(Duration.ZERO)` | Spring defaults (60 s skew, rate-limited) | A token expired by 1 s must be refused (AC-005.2) |
| Forge claims | `cloudId` from `context.cloudId`, the account from `principal`; the builder confirms the claim paths against Atlassian's Forge Remote documentation before the token task | — | Paths are an implementation detail (Decided in planning) |
| Lifecycle events | Installed and uninstalled events change nothing and answer 204; the installed event from an unlinked `cloudId` is logged (AC-006.15, AC-006.16); the type is read from the body | binding the `cloudId` at install (removed) | The `cloudId` is linked only by the Admin at UC-10 (AC-004.19) |
| Events | `WorkspaceSuspended` / `WorkspaceReactivated` records in `platform/dto`, published from an after-commit `TransactionSynchronization` | `@TransactionalEventListener` on the consumer side | Guarantees "after the commit" for every listener |
| GHCR namespace | `ghcr.io/${{ github.repository_owner }}` lower-cased | hard-coded owner | `GITHUB_TOKEN` can push only to the repository owner's namespace |
| Staging basic auth | Caddy `basic_auth` with a bcrypt hash from the edge `.env` on staging Workspace sites, `admin.`, `login.`; not on `api.` | IP allow-list | Team members work from changing addresses; Forge Remote must reach `api.` (AC-009.10) |
| Reserved `staging` (D44) | `staging` joins `admin`, `login`, `api`, `www`: refused at provisioning; resolver 404 `MSG151` without a lookup. `grafana` is reserved too (AC-013.3), because Grafana is served on `grafana.<base>` (DevOps design) | own staging domain | User, 25/10 |
| Seed data format | JSON | YAML | No new dependency |

### Tooling decisions (W1 – W5)

Decided on 4/11 (the user, with the DevOps design and ADR-014, -015, -016); each is a new dependency or tool in the
sense of `backend/AGENTS.md`.

| # | Question | Chosen | Alternatives | Rationale |
|---|----------|--------|--------------|-----------|
| W1 | Client library for object storage | AWS SDK v2 `software.amazon.awssdk:s3` (BOM-managed; `S3Presigner` signs offline; path-style addressing) | a vendor-specific Java SDK (`io.minio:minio`) | Works with any S3 store (Garage now, the HLA target later), offline presigning for a public endpoint, maintained by Amazon |
| W2 | Rate-limit module for the custom Caddy image | `github.com/mholt/caddy-ratelimit` (ADR-016) | `github.com/RussellLuo/caddy-ext/ratelimit` | Maintained by the Caddy author, per-key zones |
| W3 | Monitoring and alerts (KAN-19, KAN-20) | Grafana Alloy (the one collector) + Prometheus + Grafana + Grafana-managed alerts; Loki for logs; no separate alert-routing service (user, 4/11) | Prometheus + Grafana + a separate alert-routing service; Uptime Kuma only | One collector for metrics and logs, alerts in Grafana, one dashboard for Redis, the Jobs DB and Garage; matches the DevOps design §10 and ADR-010 |
| W4 | Load-test tool | k6 (ADR-016) | Gatling | One script file, thresholds and non-zero exit built in |
| W5 | Backup encryption and off-server copy | `restic` (encrypted, deduplicated) to a Cloudflare R2 bucket (ADR-015; user, 4/11) | file-level encryption of each dump plus a second target; GPG | One tool for encryption, deduplication and retention; R2 has a free tier, an S3 API and no egress fees; replaces file-level encryption |

## File Structure

```
backend/
├── pom.xml                                   + spring-security-oauth2-jose, spring-boot-starter-jdbc if absent; W1 library; Testcontainers (postgresql)
├── Dockerfile, .dockerignore                 Maven build stage, Java 21 JRE, non-root, health check
└── src/
    ├── main/java/vn/tsolve/
    │   ├── common/tenant/
    │   │   ├── TenantContext.java                     interface (spec contract)
    │   │   ├── ThreadLocalTenantContext.java          bean; public bindRequest (resolver only); runIn binds only
    │   │   ├── TenantBinding.java                     package-private static holder
    │   │   ├── TenantDataSources.java                 port: DataSource poolOf(UUID)
    │   │   ├── TenantRoutingDataSource.java           AbstractRoutingDataSource subclass; checkout wrapper outside a transaction
    │   │   ├── TenantAwareJpaTransactionManager.java  set_config('app.tenant_id') in doBegin
    │   │   └── NoTenantException.java
    │   └── platform/
    │       ├── config/       ForgeSecurityConfig, TenantFilterConfig, StorageConfig (`tsolve.storage.*`),
    │       │                 JobsDataSourceConfig (Task 8)
    │       ├── web/          TenantResolverFilter, HostClassifier, HostKind
    │       ├── forge/        ForgeTokenVerifier (+ NimbusForgeTokenVerifier), ForgeTokenFilter, ForgeCallContext (+ impl),
    │       │                 VerifiedForgeCall, ForgeCallAuthentication
    │       ├── controller/   ForgeLifecycleController
    │       ├── service/      WorkspaceCatalog, WorkspaceProvisioning, FirstAdminCreator, InvitationJobQueue, FileStorage,
    │       │                 DemoSeedContributor
    │       │   └── impl/     WorkspaceCatalogImpl, WorkspaceProvisioningService, ObjectFileStorage,
    │       │                 JobRunrInvitationJobQueue (Task 8)
    │       ├── job/          FirstAdminInvitationJobHandler (Task 8: no-op placeholder that logs the two ids, in the web
    │       │                 image; `worker` replaces it)
    │       ├── registry/     RegistryRepository (JdbcClient), PasswordCipher (AES-GCM), RegistryDataSourceConfig,
    │       │                 TenantPoolRegistry (lazy Hikari pools), TenantDataSourceConfig (primary DataSource, R15)
    │       ├── tenantdb/     TenantDatabaseProvisioner, TenantMigrator, TenantStartupMigrator (web process only)
    │       ├── client/       TenantInfoClient (+ RestTenantInfoClient)
    │       ├── dto/          WorkspaceSummary, WorkspaceSuspended, WorkspaceReactivated, StoredFile, SeedWorkspace,
    │       │                 FirstAdminInvitationJobRequest (Task 8)
    │       ├── exception/    FileTooLargeException, InvalidFileKeyException, InvitationQueueException,
    │       │                 WorkspaceRuleException (extends BusinessRuleException)
    │       └── seed/         SeedRunner, SeedData, MigrateRunner, OpsRunner (profile ops)
    ├── main/resources/
    │   ├── application.yml            + tsolve.forge.*, tsolve.storage.*, tsolve.tenant-db.*
    │   ├── application-worker.yml     spring.liquibase.enabled=false (worker profile)
    │   ├── application-seed.yml, application-ops.yml, application-migrate.yml   web-application-type none
    │   └── db/changelog/
    │       ├── catalog/catalog-master.xml, catalog/C001-…, C002-…       Registry changelog
    │       └── changes/NNN-platform-tenant-identity.sql, NNN-platform-tenant-guard-function.sql,
    │                         NNN-platform-tenant-guard-policies.sql   (platform's tenant changesets, Task 5)
    └── test/java/vn/tsolve/
        ├── common/tenant/   ThreadLocalTenantContextTest, TenantRoutingIT, TwoWorkspaceDatabases (fixture), RowGuardIT, ConventionGuardIT, TenantIsolationIT
        ├── platform/        HostClassifierTest, TenantResolverFilterTest, WorkspaceCatalogImplTest, WorkspaceCatalogIT,
        │                    WorkspaceProvisioningIT, InvitationJobQueueIT, JobRunrInvitationJobQueueTest, PasswordCipherTest, NimbusForgeTokenVerifierTest, ForgeSecurityIT,
        │                    ForgeLifecycleIT, ObjectFileStorageIT (Garage), SeedRunnerTest
        └── support/         PostgresTestBase (Testcontainers pgvector:pg16 + infra/postgres/init, owner and runtime users),
                             GarageTestBase
infra/
├── README.md                          variables per environment, local start, DNS table, runbooks
├── caddy/Dockerfile, Caddyfile, Caddyfile.local
├── compose.edge.yml, compose.staging.yml
├── postgres/init/10-tsolve-roles.sh   roles, Registry and Jobs databases, template database
├── scripts/garage-init.sh             layout, private bucket, bucket-limited access key (Garage)
├── ops/workspace.sh                   create | suspend | reactivate (starts the ops profile)
├── scripts/bootstrap.sh, install-runner.sh, wireguard-setup.sh, ollama-setup.sh, backup-db.sh
├── RESTORE.md
├── loadtest/                          script, thresholds, report of one run
├── seed/demo-workspaces.json, run-seed.sh
└── tests/  smoke.test.sh, ports.test.sh, caddy-routing.test.sh, edge-limits.test.sh, backup.test.sh, ai-vps.test.sh  (names cite the AC ids they check)
docker-compose.yml                      local stack (KAN-94)
.env.example                            every variable, no values
.github/workflows/ci.yml (+ backend job), images.yml (backend, frontend, caddy → GHCR), cd-staging.yml, infra-checks.yml
```

## External Dependencies

- `org.springframework.security:spring-security-oauth2-jose` (Spring Boot BOM): Forge Invocation Tokens. **Approved by
  the user on 25/10.**
- Testcontainers (`spring-boot-testcontainers`, `org.testcontainers:postgresql`, Spring Boot BOM): decided by
  `be-foundation` (its Q7). Garage has no Testcontainers module: `GarageTestBase` starts the pinned Garage image as a generic container and runs the same bootstrap (Decided in planning: builder check).
- `org.jobrunr:jobrunr-spring-boot-3-starter` (JobRunr OSS; named in ADR-0002): the `InvitationJobQueue` implementation of Task 8 stores the invitation job in the Jobs DB. The web, `ops` and `seed` processes set `jobrunr.background-job-server.enabled: false`; `worker` sets it to `true`. **To be confirmed by the user in the plan review** (Task 8). Assumption (JobRunr's documentation was not available while planning): the enqueue call needs the handler class named by `JobRequest#getJobRequestHandler()` on the web classpath, hence the placeholder handler.
- S3 client library: AWS SDK v2 `software.amazon.awssdk:s3` (W1, decided).
- Caddy 2 built with `github.com/caddy-dns/cloudflare` and `github.com/mholt/caddy-ratelimit` (**W2**) via `xcaddy` (pinned versions in
  `infra/caddy/Dockerfile`).
- Garage, Redis 7, PostgreSQL 16 with pgvector (pinned images); the AWS CLI v2 for the seed reset of the bucket only; Grafana Alloy, Prometheus, Loki and Grafana (**W3**); k6 (**W4**); `restic` with Cloudflare R2 (**W5**).
- Cloudflare DNS (free plan) for `tsolve.io.vn`; GHCR; GitHub Actions self-hosted runner.
- WireGuard (Ubuntu package), Ollama (official installer, pinned version) with `bge-m3` and the chat model of KAN-140.
- Atlassian: the Forge key set (`FORGE_JWKS_URL`) and the public `/_edge/tenant_info` of a Jira site.

### Configuration (all names in `.env.example`, no values)

| Variable | Used by | Default |
|----------|---------|---------|
| `REGISTRY_DB_URL`, `REGISTRY_DB_USER`, `REGISTRY_DB_PASSWORD`, `REGISTRY_DB_NAME` | backend, worker (the Registry owner user; `REGISTRY_DB_NAME` also for the init and staging scripts, default `tsolve_staging_registry`; the beans load only when `REGISTRY_DB_URL` is not blank) | none |
| `TENANT_DB_HOST`, `TENANT_DB_PORT`, `TENANT_DB_POOL_MAX` | backend, worker (Workspace databases; the host is required where routing is on) | none · 5432 · 5 (staging 10) |
| `JOBS_DB_URL`, `JOBS_DB_USER`, `JOBS_DB_PASSWORD` | web, `ops` and `seed` processes (JobRunr enqueue, Task 8); `worker` (job server) | none |
| `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD` | tenant Liquibase, provisioning, ops (the Workspace owner role; the Registry uses `REGISTRY_DB_*`) | none |
| `CATALOG_SECRET_KEY` | backend, ops (encrypts Workspace database passwords) | none |
| `POSTGRES_PASSWORD` and the passwords of the roles | `infra/postgres/init` | none |
| `TSOLVE_SITE_ORIGIN_TEMPLATE` (existing) | backend (the base domain of the web resolver is the host part after `{slug}.`), `identity`, `worker` | none |
| `TSOLVE_ENVIRONMENT` | seed and reset only (AC-012.3, AC-012.9; added by Task 12, not by Task 10) | none |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `identity` sessions; the Redis container | none |
| `FILE_STORAGE_ENDPOINT`, `FILE_STORAGE_ACCESS_KEY`, `FILE_STORAGE_SECRET_KEY`, `FILE_STORAGE_BUCKET`, `FILE_STORAGE_URL_TTL_SECONDS` (default 300), `FILE_STORAGE_MAX_BYTES` (default 10485760) | backend, worker, Garage init | none (as listed) |
| `FILE_STORAGE_PUBLIC_URL_PATTERN` (new, R11) | backend (signed URLs) | none |
| `FORGE_APP_ID`, `FORGE_JWKS_URL` | backend | none |
| `EDGE_MAX_BODY_BYTES`, `EDGE_TIMEOUT_SECONDS`, `EDGE_RATE_PER_IP`, `EDGE_RATE_PER_SITE`, `EDGE_RATE_WINDOW_SECONDS` | edge Caddy | none |
| `TSOLVE_SEED_FILE` | seed | none |
| `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL` | `ai`; local `ollama-pull` | none |
| `CLOUDFLARE_API_TOKEN`, `STAGING_BASIC_AUTH_HASH` | edge Caddy (server `.env` only); the smoke test's plaintext credential is a repository secret of `cd-staging.yml`, not a variable | none |
| `RESTIC_PASSWORD`, `BACKUP_R2_ENDPOINT`, `BACKUP_R2_BUCKET`, `BACKUP_R2_ACCESS_KEY`, `BACKUP_R2_SECRET_KEY` | `backup-db.sh` (server `.env` only; Task 24) | none |
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`, `GRAFANA_BASIC_AUTH_HASH`, `GRAFANA_ALERT_WEBHOOK_URL` | monitoring stack, edge Caddy (Task 25) | none |
| `SERVER_PORT`, `CORS_ALLOWED_ORIGINS` | backend (existing) | as today |

`FILE_STORAGE_ROOT`, `TSOLVE_DATABASES`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` and the per-database `<DB>_APP_PASSWORD`
of the pool model are removed; `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD` stay while Boot's tenant
Liquibase uses them (Task 6-2). Every variable without a default is required in every environment (AC-008.5).

## Edge Cases & Risks

Dimensions analyzed: 1 User types, 2 Input extremes, 3 Timing, 4 Scale, 5 State transitions, 7 Error cascades,
8 Authorization, 9 Data integrity, 10 Integration, 11 Compliance, 12 Business logic · Skipped: 6 Environment — no
user interface in this module; browsers are `fe-foundation`'s.

The "Test target" column names the test class or script that covers the row; the tasks card turns each Critical and
High row into a task.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test target |
|---|-------|-----------|----------|----------|-------------------|-------------|
| 1 | STORY-002 | Authorization | Request on `a.<base>` carries `X-Forwarded-Host: b.<base>` or `?workspaceId=<B>` | Critical | Bound to A; B never read | TenantResolverFilterTest |
| 2 | STORY-002 | Timing | A request throws after binding; the next request on the same pooled thread | Critical | Next request starts unbound | ThreadLocalTenantContextTest, TenantResolverFilterTest |
| 3 | STORY-002 | Data integrity | Transaction for A ends; the same pooled connection serves a request with no Workspace | Critical | Setting cleared by the transaction end or the reset; no row visible | RowGuardIT (pool of one) |
| 4 | STORY-002 | Authorization | Code inside A's request calls `runIn(B, …)` | Critical | Throws, action not run | ThreadLocalTenantContextTest |
| 5 | STORY-002 | Data integrity | The router sends a connection of B's database while A is bound (injected misroute) | Critical | Reads no row; every write refused by `WITH CHECK` | TenantRoutingIT (AC-002.25) |
| 6 | STORY-001 | Authorization | Runtime user runs `ALTER TABLE … DISABLE ROW LEVEL SECURITY`, `DROP POLICY`, or writes `tenant_identity` | Critical | Refused (not owner, no privilege) | RowGuardIT |
| 7 | STORY-001 | Authorization | Role attributes: runtime, Registry and Jobs users are superuser or `BYPASSRLS` | Critical | Guard test fails | ConventionGuardIT (AC-001.32) |
| 8 | STORY-001 | Data integrity | A module adds a table: policy missing, named otherwise, with another condition, or a second policy | High | The installer adds `tenant_guard`; the guard test fails for the others, naming the table | ConventionGuardIT (AC-001.31) |
| 9 | STORY-001 | Data integrity | Setting is the empty string (after a `RESET`) or malformed | High | Treated as unset: no rows, no cast error on the empty string. A malformed value raises 22P02 (a refusal, never a row; KAN-253, Q4) | RowGuardIT |
| 10 | STORY-001 | Data integrity | `tenant_identity` has no row (provisioning died between steps) or a second row is inserted | High | No rows visible (fail closed); second insert refused by the primary key | RowGuardIT |
| 11 | STORY-001 | Data integrity | SQL injection in the module's code runs `set_config('app.tenant_id', <own id>)` | Medium | Accepted: it can only reach its own Workspace's database; the guard defends misrouting, not injection (the database boundary and the runtime user are the defence) | documented in ADR-0002 |
| 12 | STORY-001 | Data integrity | A table is added to the legacy allow-list, or one meeting AC-001.20 stays on it | High | Guard test fails ("the allow-list only shrinks"); so does an entry naming a table that does not exist | ConventionGuardIT (AC-001.22) |
| 13 | STORY-001 | Error cascades | `CATALOG_SECRET_KEY` missing or wrong | High | Missing: startup refuses; wrong: that Workspace's pool refuses, logs the site name without the password, others keep working | PasswordCipherTest, TenantRoutingIT |
| 14 | STORY-001 | Compliance | A connection URL or exception with the database password reaches the log | High | No password in any log line | TenantRoutingIT (log capture) |
| 15 | STORY-002 | Scale | 25 Workspaces open their pools at once in both routing processes (25 × 5 × 2 + 10 Registry + 12 Jobs + 15 Keycloak + 2 migration = 289) | High | Stays within `max_connections` 300 − 3 reserved; idle pools release their connections | TenantPoolRegistryTest (reads compose, staging env example, application.yml), TenantRoutingIT |
| 16 | STORY-002 | Timing | Two first requests for the same Workspace | Medium | One pool is opened | TenantRoutingIT |
| 17 | STORY-002 | Error cascades | One Workspace database is down; the Registry is down | High | That Workspace answers 500 `MSG06`, the others work; Registry down fails every resolution closed, logged | TenantRoutingIT |
| 18 | STORY-002 | Authorization | Unknown site or Suspended Workspace with no session | High | 404 `MSG151` / 403 `MSG150`, not 401 | TenantResolverFilterTest |
| 19 | STORY-002 | Input extremes | Host `ACME.tsolve.io.vn:443`, trailing dot, `xn--…` label, 64-char label, `-acme` | High | Lower-cased, port stripped; trailing dot and invalid labels `MSG151` without a lookup | HostClassifierTest |
| 20 | STORY-002 | Environment of hosts | Docker health check on `localhost:8080/actuator/health` | High | 200; the routing `DataSource` is not asked (Registry `dbHealthContributor`, Task 6-2) | TenantRoutingIT |
| 21 | STORY-002 | User types | Console reads a Suspended Workspace with `runIn` | Medium | Allowed (AC-002.15) | ThreadLocalTenantContextTest |
| 22 | STORY-002 | Compliance | Logs of refused requests | Medium | Host and code only; no token, cookie or body | TenantResolverFilterTest, NimbusForgeTokenVerifierTest |
| 23 | STORY-002 | Environment | Hibernate asks the database for metadata at startup with no Workspace bound | High | Startup succeeds (R15) | TenantRoutingIT |
| 24 | STORY-004 | Timing | Two Admins link the same Jira site to different Workspaces at once | High | One stored; the other `MSG153`, never a 500 | WorkspaceCatalogIT |
| 25 | STORY-004 | Input extremes | `http://x.atlassian.net`, `https://x.atlassian.net/jira`, `https://user@x`, `https://x:8443`, a host resolving to a private address | High | `MSG156`; no outbound call to an internal address | WorkspaceCatalogImplTest |
| 26 | STORY-004 | Data integrity | Same email in other case or with spaces in two Workspaces at once | High | One row (lower-case primary key); the other `MSG155` | WorkspaceCatalogIT |
| 27 | STORY-004 | State transitions | Link a second Jira site to a linked Workspace | Medium | Refused, nothing changed | WorkspaceCatalogIT |
| 28 | STORY-005 | Authorization | Token with `alg: none`, HS256 signed with the public key, expired by 1 s, wrong audience or issuer | Critical | 401 without code, no lookup | NimbusForgeTokenVerifierTest |
| 29 | STORY-005 | Error cascades | Atlassian key set unreachable at startup or on an unknown key id | High | Fail closed (401), logged; recovers when reachable | NimbusForgeTokenVerifierTest |
| 30 | STORY-005 | Authorization | Valid Forge token sent to a Workspace site, `login.` or `admin.` under `/api/v1/jira-app/` | Critical | 404 `MSG06`; the token authenticates nothing | ForgeSecurityIT (AC-005.8) |
| 31 | STORY-006 | Authorization | Verified token whose `cloudId` is linked to no Workspace | High | 403 `MSG152`, no handler | ForgeSecurityIT |
| 32 | STORY-006 | Business logic | Verified call or issue event from a Suspended Workspace's Jira site | High | Call: 403 `MSG150`; ticket event: nothing recorded, no job | ForgeSecurityIT |
| 33 | STORY-006 | Integration | Installed event replayed, or from an unlinked `cloudId` | Medium | 204, nothing changed; the unlinked one is logged | ForgeLifecycleIT |
| 34 | STORY-006 | Authorization | Call from A's Jira site, then from B's (isolation) | Critical | Each reads only its own rows | TenantIsolationIT (AC-006.14) |
| 35 | STORY-007 | Input extremes | Key `../b/…`, `C:\x`, `%2e%2e/`, NUL, `a/b`, upper-case UUID, absolute | Critical | Refused before any object call; logged | ObjectFileStorageIT |
| 36 | STORY-007 | Authorization | A's key used while B is bound: `read`, `delete`, `signedUrl` | Critical | `NotFoundException`, no content, no URL, nothing deleted | TenantIsolationIT (AC-007.11) |
| 37 | STORY-007 | Scale | File of exactly 10 MB, 10 MB + 1 byte, 0 bytes | High | 10 MB stored; +1 refused with no object; 0 bytes stored | ObjectFileStorageIT |
| 38 | STORY-007 | Error cascades | Garage down or the connection breaks during `store` | High | Exception, no object, no multipart upload; `read` fails closed | ObjectFileStorageIT |
| 39 | STORY-007 | Authorization | Signed URL after the TTL; URL with a changed key or method; unsigned request to `/<bucket>/…` | High | Refused by Garage (400 or 403, no content; KAN-96 D16); bucket has no anonymous access | ObjectFileStorageIT, `caddy-routing.test.sh` |
| 40 | STORY-007 | Compliance | The signature in the query string reaches the Caddy access log or Garage's log | High | Signature parameters removed from the Caddy log; Garage's log holds no `X-Amz-Signature=` either | `caddy-routing.test.sh` |
| 41 | STORY-007 | Input extremes | An uploaded HTML or SVG file opened through its signed URL | High | Stored as `application/octet-stream` with `nosniff`: downloaded, not rendered | `caddy-routing.test.sh` |
| 42 | STORY-007 | Timing | Two `delete` calls for one key | Low | Both succeed (AC-007.10) | ObjectFileStorageIT |
| 43 | STORY-008 | Environment | `.env` missing a required variable (for example `DB_MIGRATION_PASSWORD`, `REDIS_PASSWORD`) | Medium | Backend refuses to start naming it | start-up test |
| 44 | STORY-008 | Error cascades | Tenant changelog fails on one Workspace at startup | High | Stops, names that site name, leaves the others untouched | WorkspaceProvisioningIT / TenantMigrator test |
| 45 | STORY-008 | Error cascades | Redis down at start; Redis full under `noeviction` | High | Backend is not healthy without Redis; a full Redis fails writes loudly and the memory alert fires | `infra/tests` health check, monitoring rule |
| 46 | STORY-008 | State transitions | Bucket init fails or the bucket is missing | Medium | `garage-init` exits non-zero; backend and worker do not start | `infra/tests` |
| 47 | STORY-009 | Integration | Cloudflare token expired at renewal time | High | Existing certificates keep serving (renewal starts 30 days early); Caddy logs it; runbook rotates the token | `caddy-routing.test.sh` |
| 48 | STORY-009 | Authorization | `www.staging.…`, `staging.…`, `a.b.staging.…`; `POST` to `/<bucket>/…` on a Workspace site | Medium | 404 from Caddy for the first two; third has no certificate; `POST` gets 405 | `caddy-routing.test.sh` |
| 49 | STORY-010 | Authorization | A pull request from a fork tries to run on the self-hosted runner | Critical | Deploy job only on `push`/`workflow_run` of `main`; runner group restricted to `cd-staging.yml` | `infra-checks.yml` |
| 50 | STORY-010 | Error cascades | Migration fails for one Workspace mid-deploy | High | Workflow fails before `up`, naming the Workspace; old containers keep serving | `cd-staging.yml` dry run |
| 51 | STORY-010 | Authorization | Docker publishes PostgreSQL, Redis or Garage ports, bypassing ufw | Critical | No `ports:` on those services in server compose files; port test from the internet | `ports.test.sh` |
| 52 | STORY-011 | Environment | AI VPS reboots | High | `wg0` and Ollama come back (systemd order) | `ai-vps.test.sh` |
| 53 | STORY-011 | Authorization | Ollama started by the installer on `0.0.0.0:11434` before the override | Critical | Override applied before first start; ufw denies 11434 on the public interface anyway | `ai-vps.test.sh` |
| 54 | STORY-012 | Business logic | Seed or `run-seed.sh --reset` with `TSOLVE_ENVIRONMENT` unset, `production` or `prod` | Critical | Exit non-zero before anything is touched | SeedRunnerTest, `run-seed` test |
| 55 | STORY-012 | State transitions | Seed run twice | Medium | Second run skips both Workspaces | SeedRunnerTest |
| 56 | STORY-012 | Error cascades | A contributor fails for Workspace 2 | Medium | Stops, non-zero, names Workspace 2 and the contributor | SeedRunnerTest |
| 57 | STORY-013 | Error cascades | `createFirstAdmin` throws, or any step fails | High | Compensation: pool closed, Registry rows deleted, database dropped `WITH (FORCE)`, role dropped; nothing visible | WorkspaceProvisioningIT |
| 58 | STORY-013 | Timing | Two creations for the same email or the same site name | High | One wins at step 5; the other is refused (`MSG155` for the email) and its database is dropped | WorkspaceProvisioningIT |
| 59 | STORY-013 | State transitions | Process killed mid-provisioning | Medium | An orphan database with no Registry row stays; invisible; the runbook lists databases not in the Registry and removes them by hand | runbook in `infra/README.md` |
| 60 | STORY-013 | Error cascades | Queueing the invitation fails or the mail cannot be sent | Medium | Workspace and Admin kept; failure logged without the email; the job is retried by `worker` | WorkspaceProvisioningIT |
| 61 | STORY-013 | Authorization | A module other than the Console, the ops profile and the seed calls `create`, `suspend` or `reactivate` | Medium | A source-scan test fails (AC-013.20) | source-scan test |
| 62 | STORY-013 | Compliance | Script output, logs and the Jobs DB contain a password or an email | High | None: the job holds ids; the script prints no password | WorkspaceProvisioningIT (log capture) |
| 63 | STORY-013 | State transitions | `suspend` twice, `reactivate` an Active one, an unknown site name | Low | No change, message, no second event | WorkspaceProvisioningIT |
| 64 | STORY-013 | Business logic | `ops` or Console profile starts with no `FirstAdminCreator` or `InvitationJobQueue` bean | High | Refuses to start naming the port | start-up test (AC-013.15) |
| 65 | STORY-014 | Error cascades | One dump or one prefix copy fails in the nightly backup | High | Non-zero exit naming it; alert; other files still written | `backup.test.sh` |
| 66 | STORY-014 | Authorization | A staging restore of a production Workspace keeps the production database name, user or password | Critical | Rewritten on restore; never points at production | `RESTORE.md` drill (AC-014.5) |
| 67 | STORY-014 | Data integrity | Database dump and its bucket prefix are taken at different moments | Medium | Accepted; documented in `RESTORE.md` | runbook |
| 68 | STORY-014 | Scale | Many users behind one office address hit `EDGE_RATE_PER_IP` | Medium | Values are generous; the per-site limit is the second guard; 429 carries `Retry-After` | `edge-limits.test.sh` |
| 69 | STORY-014 | Input extremes | Body over `EDGE_MAX_BODY_BYTES`; file between `FILE_STORAGE_MAX_BYTES` and the edge limit | Medium | 413 at the edge; an allowed file always passes; the backend still refuses a file over its limit | `edge-limits.test.sh` |
| 70 | STORY-013 | Error cascades | The Jobs database is down when the invitation is enqueued | High | `create` still keeps the Workspace and the Admin; the failure is logged without the email; `worker` retries the send later (AC-013.18) | InvitationJobQueueIT (Jobs database stopped) |
| 71 | STORY-013 | Timing | A second enqueue for the same Workspace (retry, double call) | Medium | The job id derives from the Workspace id: one waiting job, no second one (AC-013.17) | InvitationJobQueueIT, JobRunrInvitationJobQueueTest (same Workspace twice) |
| 72 | STORY-001 | Data integrity | The Registry beans (`registryDataSource`, a second `SpringLiquibase`) are defined next to Boot's auto-configuration | Critical | The primary `DataSource` still connects to the business database, not the Registry (JPA never writes to it), and Boot's tenant `liquibase` bean still runs `db/changelog/db.changelog-master.xml` | RegistryChangelogIT (`keepsPrimaryDataSourceAndTenantLiquibase_edge_72`) |
| 73 | STORY-001 | Environment | The staging Registry script runs without `POSTGRES_USER` or `POSTGRES_PASSWORD` | High | Exits non-zero naming the missing variable; no Registry database or user is created | `infra/tests/registry-db.test.sh` (S1) |
| 74 | STORY-001 | State transitions | The staging volume already exists (the init script runs only on an empty volume), so it has no Registry | High | The script creates the Registry database (owned by `REGISTRY_DB_USER`) and the user (LOGIN, no superuser, no `BYPASSRLS`, no `CREATEDB`, no `CREATEROLE`) with the same end state as the init script on a fresh volume; the other databases are untouched; the output holds no password | `infra/tests/registry-db.test.sh` (S2, S4) |
| 75 | STORY-001 | State transitions | The staging script runs a second time, even with another `REGISTRY_DB_PASSWORD` | Medium | Nothing changes: an existing role is left as it is, its password is never altered and never printed | `infra/tests/registry-db.test.sh` (S3) |
| 76 | STORY-004 | State transitions | `claimEmail` is called with no Workspace bound | Medium | `NoTenantException` (`MSG06`); no email recorded (AC-004.25 "while a Workspace is bound") | WorkspaceCatalogIT (T4a) |
| 77 | STORY-001 | State transitions | The adopt script runs a second time with the same values | High | The row is unchanged (same `created_at` and `updated_at`); exit 0; no password in the output | `infra/tests/registry-adopt.test.sh` (A3) |
| 78 | STORY-001 | Data integrity | The adopt script meets a bluefin row whose site name, Google domain, organization id, database name or Jira site differ from the inputs | High | Exits non-zero, names the field, changes nothing | `infra/tests/registry-adopt.test.sh` (A4) |
| 79 | STORY-004 | Data integrity | bluefin's tenant `workspace` table is empty (Setup not done, for example `TSOLVE_STUB_WORKSPACE_READY` was on) and the script writes a cloud id | High | The script never writes a cloud id then: both Jira columns stay NULL and one line says so; else AC-004.23 would refuse the Admin's Setup in Task 18. With a tenant row: `jira_site_url` and a lower-case `jira_cloud_id` are copied, both or neither | `infra/tests/registry-adopt.test.sh` (A5, A6) |
| 80 | STORY-002 | Error cascades | The Registry is down at sign-in or on a Workspace host | High | `PlatformWorkspaceLookup` does not catch the failure: the request answers 500 `MSG06`, never 404 "no Workspace" (an outage must not look like an unknown site) | PlatformWorkspaceLookupTest (T4b) |
| 81 | STORY-002 | Input extremes | A host in another case or with a port; a host outside `TSOLVE_SITE_ORIGIN_TEMPLATE`; a blank host | Medium | The same Workspace for case and port; empty for the other two; no query on a blank host | PlatformWorkspaceLookupTest (T4b) |
| 82 | STORY-002 | State transitions | A Suspended Workspace is bound and a job is enqueued, on a request thread or a plain thread | High | `enqueueWorkspaceId` is empty on any thread; nothing is enqueued for a Suspended Workspace (GBR-38) | CatalogWorkerTenantServiceTest (T4c) |
| 83 | STORY-004 | Integration | A full application context starts with the Registry gate off | High | Exactly one `JiraWorkspaceLookup` bean exists (`CatalogJiraWorkspaceLookup`, answering empty); no context fails for a missing `WorkspaceCatalog` | CatalogJiraWorkspaceLookupTest (T4b) |
| 84 | STORY-004 | Error cascades | The catalog cannot list the Active Workspaces (Registry down) during a recurring job | Medium | `activeWorkspaceIds` throws; no empty list (worker AC-003.3) | CatalogWorkerTenantServiceTest (T4c) |
| 85 | STORY-002 | Authorization | A `WorkerTenantService` bean is registered in the web process (`tsolve.worker.enabled` false) | High | It is not: the catalog service loads only with `tsolve.worker.enabled=true` (with it on and no catalog bean the worker fails startup, R19); with the web outbox only, no `WorkerTenantService` bean exists | WorkerConfigurationTest (T4c) |
| 86 | STORY-002 | Environment | A `WorkspaceCatalog` bean exists and `TSOLVE_SITE_ORIGIN_TEMPLATE` is blank or has no `{slug}` | High | Startup fails, naming `TSOLVE_SITE_ORIGIN_TEMPLATE` only (no other variable or value); with the Registry gate off (no catalog bean) nothing changes; with a valid template it starts. Otherwise every host would answer 404 "unknown site", an outage that looks like an unknown site (Edge #80). The check must not run in a constructor (circular reference) | PlatformWorkspaceLookupTest `blankTemplateWithACatalogFailsStartup_edge86` (T4b) |
| 87 | STORY-002 | Authorization | A second Registry row exists after the 4b deploy and before routing (Task 6-2): sign-in and the Jira App would bind its users to bluefin's database | Critical | **Accepted risk until Task 6-2** (Hiếu, 24/11): the single-database guard goes away in 4b. Controls: no code path creates a second row before Task 7 (which depends on Task 6); the adopt script writes bluefin only and refuses differing values (#78); `RegistryRepository` has no insert method for `tenant_workspace` until Task 7 (a code control only: the app's Registry database user owns the Registry database, and the adopt script inserts as that user); process rule "one Registry row until routing; do not insert a row by hand before Task 6-2" in the PR text, the pre-deploy checklist and the Task 6 hand-off. No test or code in 4b. **Closer: Task 6-2** (edge #5 in `TenantRoutingIT`: a second row reaches only its own database) | none in 4b; closed by `TenantRoutingIT` (T6-2) |
| 88 | STORY-002 | Error cascades | Once routing is on, the publish listener thread or a recurring job (embedding catch-up, conversation retention, preview cleanup) touches the tenant database with nothing bound | High | Task 6-1: the listener runs in the publisher's Workspace; each job runs once per Active Workspace, bound; one Workspace's failure is logged without ids and the others run; with no catalog, one unbound run | TicketsPublishedListenerTest, EmbeddingCatchUpJobTest, ConversationRetentionJobTest, PreviewCleanupJobTest (T6-1) |
| 89 | STORY-001 | Environment | At the routing deploy bluefin's row password does not decrypt with the deployed `CATALOG_SECRET_KEY`: every bluefin query answers 500 `MSG06` | High | Checked before the merge (CD deploys it) with `RegistryPasswordCli verify`, which prints only OK or FAIL and never the password; on FAIL, Task 6-2 is not merged until the row or the key is corrected | RegistryPasswordCliTest; pre-deploy check (`infra/README.md`, T6-2) |
| 90 | STORY-005 | Authorization | `/api/v1/jira-app/**`, plain or percent-encoded, on a site, `login.` or `admin.` host; any other path on `api.` | Critical | 404 `MSG06` from the resolver, the chain never called (a Suspended or unknown site answers `MSG150` / `MSG151` first) | TenantResolverFilterTest (T10a) |
| 91 | STORY-002 | Environment of hosts | No `WorkspaceCatalog` bean (empty `REGISTRY_DB_URL`) | Medium | The resolver passes every request through and logs one line at start-up | TenantResolverFilterTest (T10a) |
| 92 | STORY-002 | Authorization | Direct call to the backend: `Host` = `login.` or `admin.`, a forged `X-Forwarded-Host` naming a Suspended site, and that site's session cookie | Medium | **Accepted:** the resolver binds and looks up nothing and the request reaches the handler. Control: the network boundary (Caddy overwrites forwarded headers; staging publishes no backend port). Follow-up (`identity`): the session check refuses a Suspended site | none (network boundary) |

Open questions for the spec owner: none (the spec settles the Redis cache as out of scope and defines `schema_version` in AC-001.15).

## Traceability

| AC family (new or changed on 4/11) | Design section |
|------------------------------------|----------------|
| AC-001.15, .16, .17, .19, .24, .25 (Registry tables, password encryption) | Data Model — Tenant Registry DB; R1, R8 |
| AC-001.18, .20 – .23, .27 (changelogs, tenant convention, guard test) | Data Model — Workspace DB, Convention guard test data |
| AC-001.28 – .32 (`tenant_identity`, policy, guard test, roles) | The row guard; Roles and grants; R4, R5, R6 |
| AC-001.26, .33 (runtime user, Jobs DB) | Roles and grants; Infrastructure; H-Q3 |
| AC-002.1 – .19 (context, resolver) | How a request or job gets its Workspace; The binding |
| AC-002.20 – .25 (routing, pools, tenant setting) | Database routing; R2, R3, R15 |
| AC-003.10 | The row guard (only the policy `tenant_guard` exists) |
| AC-004.17 – .29 (WorkspaceCatalog) | Registry rules |
| AC-005.1 – .8 (Forge token, `admin.` on the Jira path) | Security chains; Key Decisions (P3, Forge) |
| AC-006.1 – .16 (Jira App resolver, Suspended 403 `MSG150`, lifecycle) | How a request or job gets its Workspace; Lifecycle events |
| AC-007.1 – .15 (object storage, signed URL) | Storage layout; Signed URL host and routing; R10–R13 |
| AC-008.1 – .11 (stack, Redis, Garage) | Infrastructure; Redis |
| AC-009.1 – .11 (Caddy, `admin.`, signed-URL route) | Infrastructure; Signed URL host and routing |
| AC-010.1 – .12 (CD, migrations per Workspace, Redis, Garage, Jobs DB in staging) | Infrastructure — Deploy; R14 |
| AC-011.1 – .6 (AI VPS) | Infrastructure — Private link; P11, Q13 |
| AC-012.1 – .9 (seed through provisioning, reset) | Seed data; P7 |
| AC-013.1 – .20 (provisioning, first Admin, invitation, race) | Provisioning; R9, R10 |
| AC-014.1 – .13 (backup, restore, monitoring, edge limits, load test) | Infrastructure — Edge limits, Operations; Tooling decisions (W1 – W5) |
| Non-functional 1 – 7 | Data Model; Edge Cases & Risks rows 1 – 15 |

## KAN-186: cross-module isolation tests and the E2E smoke (STORY-015; AC-015.1 – AC-015.12)

Added 14/11 (Change Mode, user decisions of 14/11). Two parts, one Jira task, one branch
`test/KAN-186-platform-e2e-isolation`.

### Part 1 — `CrossModuleIsolationIT` (AC-015.1 – AC-015.7)
- **Chosen:** one Spring Boot IT, `backend/src/test/java/vn/tsolve/platform/isolation/CrossModuleIsolationIT.java`,
  built like the identity session ITs (`AbstractSessionIT`): `TwoTenantDatabases` (ACME → `tenant_a`, BETA →
  `tenant_b`), `SessionTestFixtures.Beans`, a Redis Testcontainer through `@DynamicPropertySource`, and a **real
  Workspace session** (the cookie of the web session of identity Task 26a) on the ACME site host for every request, so
  the binding comes from `WebSessionFilter` (identity AC-001.76), not from `@WithTSolveUser`. **Alternatives:**
  `@WithTSolveUser` + `TenantScope.runIn` (skips the session binding the AC asks for); one IT per module (more start-ups,
  no shared fixture). **Rationale:** AC-015.1 says "a Workspace session of ACME"; one context keeps the run short.
- **Data:** written with `TwoTenantDatabases.tenantA()` / `tenantB()` (JDBC), the same shape in both databases:
  Accounts with their `user_role` rows (Ticket Resolver, Project Manager), a Department and a Project with the
  membership the module scope rules need, a Published ticket with the same title in both, a ticket waiting for review
  in both, and the ticket embeddings Search and the Chatbot retrieve. Ids differ per database so a leak is visible.
- **AI:** `@MockitoBean` for `vn.tsolve.ai` `LlmClient` and `EmbeddingClient`; the test captures the prompt / context
  the chat model receives (AC-015.5) and returns a fixed vector, so ranking works on both databases.
- **"Nothing written in BETA"** (AC-015.3, .4, .5, .7): count rows of the touched tables (`ticket_feedback`, the search
  log, the chatbot answer table) in `tenant_b` before and after the request.
- **Unknown-ticket answer** (AC-015.3): the test first asks with a random UUID, records status and `code`, then asks with
  BETA's id and compares — no hard-coded code.
- Test names cite the id (`ticketList_listsOnlyAcmeTickets_AC_015_2`); the class lives under `platform/` so
  `sdd.ts trace platform` finds it.

### Part 2 — Playwright suite under `e2e/` (AC-015.8 – AC-015.12)
- **Chosen:** a standalone npm package `e2e/` (`@playwright/test`, exact version pinned, Chromium only, `pg` for the
  fixture), `npm run e2e` = `playwright test`. **Alternatives:** inside `frontend/` (mixes Vitest and Playwright, the
  frontend verify would pick it up); Cypress (no reason to add a second tool). **Rationale:** AC-015.8.
- **Global setup** (AC-015.11): checks, in order, PostgreSQL `localhost:5432`, Redis `6379`, Keycloak `8180`, the fake
  Google, the backend `GET /actuator/health` on `8080` and the web app on `5173`; the first one that does not answer
  stops the run with `local stack not running: <service>`. Then it makes the dev data the pages need, only what is
  missing: `alice@acme.test` gets the Ticket Resolver and Project Manager Roles in addition to Admin, one Department
  and one Published ticket for ACME (`Decided in planning: the fixture writes the dev database with SQL, because the
  demo seed KAN-97 is not built (builder check)`).
- **Sign-in** (AC-015.9): through Keycloak and the fake Google as in `docs/authentication.md` (organization `acme`);
  the authenticated storage state is saved once and reused. Pages: home, Ticket List, Search, Chatbot, User List — each
  asserts a heading or table of the page and no MSG error banner.
- **Site-bound session** (AC-015.10): with ACME's cookies, `GET /api/v1/me` sent with the BETA site host is answered
  as a request without a session (401), and contains no BETA data.
- **Report** (AC-015.12): Playwright HTML reporter to `e2e/playwright-report/` (git-ignored, with `test-results/`).
  The run of 14/11 is written to `artifacts/06-evaluation/platform/task-24-e2e-smoke-test-report.md`.
- Out of this part: the Jira App, CI, staging, two databases in the browser run (spec Out of Scope).
