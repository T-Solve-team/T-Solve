---
module: platform
date: 2026-11-01
owner: BA
---

# Spec: platform

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-25 | STORY-001..STORY-012 | Initial spec from Report3 (GBR-03, GBR-12, GBR-14, GBR-19, GBR-24, GBR-37; MSG119–MSG124, MSG125–MSG139; D6, D9, D11, D12, D20, D29, D41, D42, D43; ADR-0001), Stories TS-001…TS-005, TS-010…TS-013, TS-022, the Jira descriptions of KAN-94…KAN-101, KAN-105, KAN-200 (subtasks KAN-6, KAN-8…KAN-14), KAN-203, Phan_cong v6 §1 and the planning decisions Q1–Q15 of 25/10 | Plan the `platform` module (KAN-196): the tenant layer of the pool model and the infrastructure every module runs on |
| 2026-10-25 | STORY-001 | AC-001.8: a join table keeps a composite primary key of its two `<entity>_id` columns plus `workspace_id`, with no `id`; an append-only table has `created_at` and no `updated_at`. AC-001.11: the legacy list names "at most" its tables, a table leaves it once its module brings it to AC-001.8, and `audit_event` leaves now. AC-001.13 refers to the tables of AC-001.11. New AC-001.14: UUID ids are generated both by JPA and by the database. The "Owner of changeset 001" line now also covers `audit_events` | Decisions X2 and U3 of the review of the UUID-convention change requests (`be-foundation` converts `audit_event` to `audit_events`) |
| 2026-10-25 | STORY-002, STORY-004, STORY-010, STORY-012 | New AC-002.19 (health and API-docs paths pass on any host) and AC-002.6 points to it; AC-004.3, AC-004.4: a missing or blank site name or Google domain → MSG01; AC-004.5: user info or a port → MSG136; AC-004.13: lookups return empty, only suspend / reactivate throw; AC-010.3, AC-010.5: the seed step in the deploy; AC-012.4 names what "the production configuration" is; two open questions at AC-010.6 and AC-012.5 | Builder check of the platform plan (KAN-196): contradictions with be-foundation AC-002.8, with the registry contract and with design decision P7 |
| 2026-10-25 | STORY-002, STORY-004, STORY-010, STORY-012 | Open questions answered. AC-010.6: `staging` is a reserved site name; AC-002.6 answers `staging.` + base domain with MSG119 without a lookup; AC-004.3 gives MSG139 for `staging`. AC-012.5 keeps its rule and points to the reset; new AC-012.8 (`run-seed.sh --reset` empties the dev or staging database and file storage, re-applies the changelog, re-seeds) and AC-012.9 (refused unless `dev` or `staging`) | User, 25/10: AC-010.6 = A (D44 (25/10), added to the baseline in parallel); AC-012.5 = B |
| 2026-11-01 | STORY-001…STORY-013 | **Database per Workspace (ADR-0002) replaces the pool model (ADR-0001).** STORY-001: the catalog database and the tenant-database convention replace `workspace_id` + `@TenantId` (AC-001.1…AC-001.14 removed; new AC-001.15…AC-001.27). STORY-002: binding a Workspace selects its database (new AC-002.20…AC-002.23); MSG119 → MSG151, MSG120 → MSG150; AC-002.15 and AC-002.18 rewritten. STORY-003 (row-level security, KAN-100) cancelled; its ids are retired. STORY-004: the Workspace registry becomes the read side of the catalog plus the Jira-site link of UC-10 and the email list of GBR-16 (AC-004.1…AC-004.16 removed; new AC-004.17…AC-004.28). STORY-005: AC-005.8 without `admin.`. STORY-006: MSG124 → MSG152; the install event no longer binds the `cloudId` (AC-006.6…AC-006.11 and AC-006.13 removed; new AC-006.15, AC-006.16). STORY-008, STORY-009, STORY-010: no `admin.` host; migrations run on the catalog and on every Workspace database. STORY-011: no Internet egress, no prompt logging. STORY-012: the seed creates its Workspaces through STORY-013. New STORY-013: the ops script that creates, suspends and reactivates a Workspace (KAN-240). The Google domain, the Platform Console and its messages MSG125…MSG141 are gone | Report3 split files of 31/10 (GBR-14, GBR-16, GBR-24, GBR-37, GBR-38; MSG150…MSG153, MSG155); team decisions of 29/10 (database per company, no PII masking) and 31/10 (no Platform Console, Workspace service account); Jira KAN-98 retitled, KAN-100 and KAN-113…116 cancelled, KAN-240 added |

## Requirements

### Functional
1. One deployment serves every Workspace; **each Workspace has its own PostgreSQL database** and its own file
   storage, and a catalog database holds only what is needed to find a Workspace (ADR-0002, GBR-37). The Workspace
   of a request is decided only by the host of a Workspace site, the `cloudId` of a verified Forge Invocation Token,
   or an explicit scoped binding used by jobs, the central sign-in callback, the ops script and the demo seed. No API
   takes a Workspace from the request (GBR-37).
2. Binding a Workspace selects that Workspace's database for every query of the request or job; with no Workspace
   bound, no tenant database can be reached. Business tables carry no `workspace_id`.
3. The catalog holds, per Workspace: site name, status, database connection, the Jira site and its `cloudId`, and
   the emails of its Accounts, so that one email belongs to one Workspace (GBR-16, GBR-37).
4. Workspaces are created, suspended and reactivated only by the T-Solve operations team with an ops script outside
   the application; there is no screen for it (GBR-24, GBR-38, KAN-240). Creation is all or nothing.
5. A Suspended Workspace keeps its database; its Workspace site is refused (MSG150), the T-Solve app in its Jira site
   returns an error, ticket tracker events from it are dropped (D29), and its jobs do not run (`worker`, GBR-38).
6. File storage keeps the files of each Workspace in that Workspace's own folder, and no file key reaches another
   Workspace (TS-004, GBR-37).
7. The infrastructure covers these environments:
   - local: Docker Compose with Ollama;
   - staging: on the application VPS, deployed on every merge to `main`;
   - AI VPS: runs Ollama, reachable only privately, with no Internet egress (D41).

   Caddy routes by host. The demo seed runs on dev and staging only (D42).

### Non-Functional
1. Tenant isolation (ADR-0002, TS-163): an automated test with two Workspaces shows that nothing written in
   Workspace A can be read in Workspace B. It covers a JPA query, a native SQL query, a file and a call from the
   Jira App. The full end-to-end run (web app, Jira App, Search, Chatbot) is KAN-186. The isolation criterion lives
   in this spec.
2. Every acceptance criterion has a test whose name cites its id (`…_AC_002_3`); `sdd.ts trace platform` passes.
   Infrastructure criteria are checked by a workflow step or a script under `infra/` whose name or log line cites
   the id.
3. `mvn verify` passes on every commit. Database criteria run against PostgreSQL started by Testcontainers from
   `pgvector/pgvector:pg16`, with the catalog and tenant changelogs applied as in production.
4. No secret, credential, host or URL is in a committed file (`backend/AGENTS.md` §4). Environment values come from a
   per-environment `.env` (git-ignored, mode 600 on servers) or from GitHub secrets. gitleaks runs on every PR.
5. Each Workspace database has its own runtime database user that can connect only to that database, does not own
   its tables and is not a superuser. Migrations run as a separate owner user (KAN-11). Each Workspace database is
   backed up separately (GBR-37; the backup itself is KAN-201).
6. Only Caddy listens on the internet, on ports 80 and 443. SSH is the only other open port. The backend,
   PostgreSQL and Ollama ports are never reachable from the internet.
7. No log line contains a token, a secret, a database password, a file's content or personal data
   (`backend/AGENTS.md` §9).

## User Stories

### STORY-001: Catalog database and the tenant-database convention (KAN-98)
- Owner: Trần Trung Hiếu. Sprint S1.
- As a developer of any module, I want one catalog of Workspaces and one convention for the tables of a Workspace
  database so that every module writes its tables the same way and no company's rows live in another company's
  database.
- Acceptance:
  - AC-001.15: THE catalog database SHALL hold the table `workspaces` with `id` (UUID, primary key), `site_name` (not null, unique), `status` (`ACTIVE` or `SUSPENDED`, not null), `db_name` (not null, unique), `db_user` (not null), `db_password` (not null, encrypted as AC-001.24 says), `jira_site_url` (nullable), `jira_cloud_id` (nullable, unique), `created_at` and `updated_at` (`TIMESTAMPTZ`, not null) (GBR-24, GBR-37).
  - AC-001.16: THE catalog database SHALL hold the table `workspace_emails` with `email` (primary key, stored lower-case) and `workspace_id` (not null, foreign key to `workspaces`), so that one email belongs to at most one Workspace (GBR-16, GBR-37).
  - AC-001.17: THE catalog SHALL hold no business data of a Workspace: no company information, Account name, role, ticket, Wiki document or chat (GBR-37, GBR-38).
  - AC-001.18: THE backend SHALL keep two Liquibase changelogs: the catalog changelog `db/changelog/catalog/catalog-master.xml` and the tenant changelog `db/changelog/db.changelog-master.xml`, which every module already extends. A changeset belongs to exactly one of them.
  - AC-001.19: WHEN the catalog changelog is applied to an empty catalog database in any environment, THE catalog SHALL contain no row in `workspaces` or `workspace_emails` (GBR-24, D42). Only STORY-013 creates Workspaces.
  - AC-001.20: THE tenant-table convention SHALL be, for every table of the tenant changelog that is not on the allow-list of AC-001.21:
    - a plural snake_case name;
    - `id` UUID primary key, generated as AC-001.23 says; a join table instead has a composite primary key of
      its two `<entity>_id` columns and no separate `id` (decision X2);
    - no `workspace_id` column (ADR-0002);
    - `created_at` and `updated_at` as `TIMESTAMPTZ NOT NULL`; an append-only table (rows never updated, for
      example `audit_events`) has `created_at` and no `updated_at` (decision U3).
  - AC-001.21: THE allow-list SHALL name each tenant-changelog table that does not follow AC-001.20, each with its reason: `databasechangelog` and `databasechangeloglock` (Liquibase), and the legacy tables `resolved_ticket_snapshot`, `solution`, `solution_source` (→ `ticket-repository`), `solution_approval` (→ `ticket-review`) and `reuse_record` (→ `search`), which their owning modules bring to AC-001.20 when they are built.
  - AC-001.22: WHEN the convention guard test runs against the applied tenant changelog, THE test SHALL fail for every table that is not on the allow-list and breaks any part of AC-001.20, and name the table and the missing part. IF a table on the allow-list meets AC-001.20, or a table not named in AC-001.21 is added to it, THEN the test SHALL fail and say that the allow-list only shrinks.
  - AC-001.23: THE primary key `id` of a tenant table SHALL be generated both by JPA (`@GeneratedValue(strategy = GenerationType.UUID)`) and by the database (`DEFAULT gen_random_uuid()`), so that application inserts and raw SQL inserts both get an id (decision X2).
  - AC-001.24: THE catalog SHALL store `db_password` encrypted with AES-GCM under the key `CATALOG_SECRET_KEY`, and the backend SHALL never log it or return it from any API.
  - AC-001.25: IF the key `CATALOG_SECRET_KEY` is missing or cannot decrypt a stored password, THEN THE backend SHALL refuse to open that Workspace's database and log which Workspace failed, without the password.
  - AC-001.26: THE runtime user of a Workspace database SHALL be able to connect to that database only; a connection with it to another Workspace database or to the catalog database SHALL be refused by PostgreSQL.
  - AC-001.27: THE tests of AC-001.15 to AC-001.26 SHALL run against PostgreSQL started by Testcontainers, with the changelogs applied by the owner user.

### STORY-002: TenantContext, the Tenant Resolver of the web app and database routing (KAN-99)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the system, I want the Workspace of every web request decided from the host name only, and every query sent to
  that Workspace's database, so that no module ever takes a Workspace from a request and a company never sees
  another company's data.
- Acceptance:
  - AC-002.1: THE backend SHALL offer `TenantContext` in `vn.tsolve.common.tenant`:
    - `currentWorkspaceId()`: the bound Workspace, or empty;
    - `requireWorkspaceId()`: the bound Workspace, or throws when none is bound;
    - `runIn(workspaceId, action)`: runs the action with that Workspace bound (decision Q5).
  - AC-002.2: WHEN a request arrives whose host is `<label>.` followed by the configured base domain (`TSOLVE_BASE_DOMAIN`), and the label is the site name of a Workspace, THE Tenant Resolver SHALL bind that Workspace for the whole request.
  - AC-002.3: THE Tenant Resolver SHALL read the Workspace site from the `Host` header only, compared without case and without the port. It SHALL ignore `X-Forwarded-Host`, `Forwarded` and any Workspace id in a header, a parameter, a cookie or the body (GBR-37).
  - AC-002.4: IF the label is not an RFC 1123 label (lowercase letters, digits and hyphens, starting and ending with a letter or a digit, at most 63 characters), THEN THE Tenant Resolver SHALL answer 404 with a `ProblemDetail` with `code` `MSG151` without looking up the catalog (D11).
  - AC-002.5: IF the label is the site name of no Workspace, THEN THE Tenant Resolver SHALL answer 404 with `code` `MSG151`.
  - AC-002.6: IF the host is the bare base domain, `www.` or `staging.` followed by the base domain, has more than one label before the base domain, or is not under the base domain, THEN THE backend SHALL answer 404 with `code` `MSG151` without looking up the catalog (decision Q15; `staging`: D44 (25/10)), except for the paths of AC-002.19.
  - AC-002.7: IF the Workspace of the host is Suspended, THEN THE Tenant Resolver SHALL answer 403 with `code` `MSG150` and run no handler, whatever session the request carries (GBR-38).
  - AC-002.8: THE Tenant Resolver SHALL run before every Spring Security check, so that MSG151 and MSG150 are answered before any 401 or 403 of sign-in or roles (agreed with `fe-foundation`, KAN-198).
  - AC-002.9: THE answers of AC-002.4 to AC-002.7 SHALL have content type `application/problem+json` and follow the contract's `ProblemDetail` (`status`, `code`), with no detail that names a Workspace.
  - AC-002.10: WHEN a request's host is `login.` or `api.` followed by the base domain, THE Tenant Resolver SHALL bind no Workspace for that host. The Workspace on `api.` comes only from STORY-006. IF the host is `admin.` followed by the base domain, THEN THE backend SHALL answer 404 with `code` `MSG151`, as for any unknown site.
  - AC-002.11: WHEN a request ends, normally or with an exception, THE backend SHALL unbind its Workspace, so that the next request on the same thread starts with none.
  - AC-002.12: WHEN `runIn` is called while no Workspace is bound, or while the same Workspace is bound, THE backend SHALL run the action in that Workspace. After the action, it SHALL restore the binding as it was before, also when the action throws.
  - AC-002.13: IF `runIn` is called with a Workspace different from the bound one, THEN THE backend SHALL throw and not run the action, so that no request or job ever switches Workspace (GBR-37).
  - AC-002.14: IF `runIn` is called with an id that belongs to no Workspace, THEN THE backend SHALL throw and not run the action.
  - AC-002.15: WHILE a Workspace is Suspended, THE backend SHALL still let `runIn` bind it, so that the ops script can reactivate it and the seed can reset it. Not running its jobs is done by `worker` (GBR-38, decision Q6).
  - AC-002.16: IF code calls `requireWorkspaceId()` while no Workspace is bound, THEN THE backend SHALL throw an exception that the global handler answers with 500 and `code` `MSG06` (be-foundation AC-001.7), and it SHALL log the call site.
  - AC-002.17: WHILE the development base domain is `localhost`, THE Tenant Resolver SHALL resolve `<site name>.localhost` with any port the same way as a production Workspace site (fe-foundation design Q9).
  - AC-002.18: THE tenant-isolation test SHALL create two Workspaces A and B, each with its own database and a row in the same tenant table. It SHALL show, through the web resolver, that a request on A's Workspace site reads only A's row with a JPA query and with a native SQL query, and never B's row (TS-011, ADR-0002).
  - AC-002.19: THE Tenant Resolver SHALL let `/actuator/health`, `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml` and the Swagger UI through on any host without binding a Workspace, so that Docker health checks and `npm run api:pull` on `localhost:8080` work (be-foundation AC-002.8; builder check).
  - AC-002.20: WHILE a Workspace is bound, THE backend SHALL send every JPA, JDBC and native query of the request or job to that Workspace's database, through one routing `DataSource` that the modules use without knowing the Workspace (ADR-0002).
  - AC-002.21: IF a query of the tenant `DataSource` runs while no Workspace is bound, THEN THE backend SHALL throw before opening any connection and store nothing.
  - AC-002.22: THE backend SHALL open the connection pool of a Workspace's database on the first use and keep at most `TENANT_DB_POOL_MAX` connections in it (default 5), so that the total number of connections is bounded by the number of Workspaces in use.
  - AC-002.23: WHEN a transaction is open, THE backend SHALL keep it on the database of the Workspace that was bound when it began; a `runIn` of another Workspace inside it is refused by AC-002.13.

### STORY-003: PostgreSQL row-level security (KAN-100) — cancelled
- Cancelled on 29/10 with the move to one database per Workspace (ADR-0002): the database boundary and the
  per-Workspace runtime user (AC-001.26) replace row-level security. Ids AC-003.1…AC-003.8 are retired.
- Acceptance:
  - AC-003.9: THE tenant changelog SHALL enable no row-level security policy that compares a Workspace id, so that no module relies on it.

### STORY-004: Catalog service: find a Workspace, link its Jira site, keep its emails (KAN-98)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the resolvers, `workspace` (UC-10) and `identity` (UC-05), we want one service over the catalog so that every
  lookup, Jira-site link and email check follows the same rules.
- Acceptance:
  - AC-004.17: THE `WorkspaceCatalog` SHALL find a Workspace by id, by site name (without case) and by `jira_cloud_id`, and return its id, site name, status and Jira site; a lookup that finds nothing SHALL return empty.
  - AC-004.18: THE `WorkspaceCatalog` SHALL never return the database connection or password to a module; only the routing `DataSource` of STORY-002 reads them.
  - AC-004.19: WHEN `linkJiraSite(siteUrl)` is called while a Workspace with no Jira site is bound, THE catalog SHALL normalise the URL to `https://<host>` in lower case, read its `cloudId` from Atlassian's public tenant information at the address followed by `/_edge/tenant_info`, and store both on the bound Workspace (GBR-24; used by UC-10 of `workspace`).
  - AC-004.20: IF the `cloudId` of that site is already stored on another Workspace, THEN THE catalog SHALL refuse with `code` `MSG153` and `params` `tracker_name` = `Jira` on field `jiraSiteUrl`, store nothing and not name the other Workspace (GBR-14).
  - AC-004.21: IF the URL is not an `https` address with a host and no user info, no port, no path other than `/`, no query and no fragment, THEN THE catalog SHALL refuse with `code` `MSG04` on field `jiraSiteUrl` and store nothing. [NEEDS CLARIFICATION: Report3 of 31/10 has no message for a malformed Jira site URL (MSG136 was removed); which MSG does UC-10 show?]
  - AC-004.22: IF Atlassian's tenant information cannot be read, THEN THE catalog SHALL store nothing, log the failure and throw an exception that the caller reports.
  - AC-004.23: IF `linkJiraSite` is called on a Workspace that already has a Jira site, THEN THE catalog SHALL change nothing and refuse, because the Jira site cannot be changed after the first sign-in (GBR-24).
  - AC-004.24: IF two Workspaces race to link the same `cloudId`, THEN THE catalog SHALL store it for only one and refuse the other with `MSG153`, never with a 500.
  - AC-004.25: WHEN `claimEmail(email)` is called while a Workspace is bound and the email (compared lower-case) is in no Workspace, THE catalog SHALL record it for the bound Workspace (GBR-16; used by UC-05 of `identity`).
  - AC-004.26: IF the email is already recorded for another Workspace, THEN THE catalog SHALL refuse with `code` `MSG155` on field `email` and record nothing; IF it is already recorded for the bound Workspace, THEN it SHALL change nothing and succeed.
  - AC-004.27: THE catalog service SHALL read and write only catalog tables and SHALL never bind a Workspace itself (GBR-37).
  - AC-004.28: THE catalog service SHALL offer no way, through any REST endpoint, to create, suspend, reactivate or delete a Workspace or to change its site name; only STORY-013 does that (GBR-38).

### STORY-005: Verify the Forge Invocation Token (KAN-105)
- Owner: Trần Trung Hiếu. Sprint S2.
- As the system, I want every call from the T-Solve app in Jira checked by its Forge Invocation Token, before
  anything else, so that a forged call never reaches a Workspace.
- Acceptance:
  - AC-005.1: WHEN a request comes to `api.` (followed by the base domain) on a path under `/api/v1/jira-app/`, THE backend SHALL require an `Authorization: Bearer <token>` header and verify the token before any Workspace lookup or handler (GBR-14, GBR-19).
  - AC-005.2: THE `ForgeTokenVerifier` SHALL accept a token only when it is signed by one of the keys Atlassian publishes for Forge Invocation Tokens (key set from `FORGE_JWKS_URL`), has not expired, has issuer `forge/invocation-token`, and has audience `ari:cloud:ecosystem::app/` followed by `FORGE_APP_ID`.
  - AC-005.3: IF the header is missing, the token is unsigned, signed by another key, expired, or has another issuer or audience, THEN THE backend SHALL answer 401 with a `ProblemDetail` without `code`. It SHALL run no handler and look up no Workspace (TS-022, decision Q8).
  - AC-005.4: IF the token names a key id that is not in the cached key set, THEN THE backend SHALL fetch the key set again once before refusing. IF the key set cannot be fetched, THEN it SHALL refuse as in AC-005.3 and log the failure.
  - AC-005.5: WHEN a token is verified, THE backend SHALL make its claims available to the handler for that request only, through `ForgeCallContext`: the `cloudId`, and the Atlassian account id of the principal when the call has one.
  - AC-005.6: THE requests to `api.` SHALL use a security chain of this module that creates no session and sets no cookie. It SHALL need no CSRF token, because the Forge token authenticates each call (decision Q10).
  - AC-005.7: IF a request to `api.` goes to any path other than `/api/v1/jira-app/` paths and `/actuator/health`, THEN THE backend SHALL answer 404 with `code` `MSG06`, as be-foundation AC-001.8 answers an unknown path (decision Q10).
  - AC-005.8: IF a request to a Workspace site or `login.` goes to a path under `/api/v1/jira-app/`, THEN THE backend SHALL answer 404 with `code` `MSG06`. A Forge token SHALL never authenticate a request on those hosts (GBR-37).

### STORY-006: Tenant Resolver of the Jira App (KAN-101)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the system, I want every verified call from the T-Solve app resolved to the Workspace whose Jira site it comes
  from, so that a company's Jira site reaches only its own Workspace.
- Acceptance:
  - AC-006.1: WHEN a verified call (STORY-005) carries a `cloudId` that the catalog links to an Active Workspace, THE backend SHALL bind that Workspace for the whole request (GBR-14, GBR-37).
  - AC-006.2: THE backend SHALL take the `cloudId` only from the verified token's claims, never from a header, a parameter or the body (GBR-19).
  - AC-006.3: IF the verified `cloudId` is linked to no Workspace, THEN THE backend SHALL answer 403 with a `ProblemDetail` with `code` `MSG152` and `params` `tracker_name` = `Jira`, and run no handler (decision Q8).
  - AC-006.4: IF the Workspace of the verified `cloudId` is Suspended, THEN THE backend SHALL answer 403 and run no handler (GBR-38). [NEEDS CLARIFICATION: Report3 of 31/10 says only that the T-Solve app "returns an error"; which MSG code does the Jira App show for a Suspended Workspace (MSG121 was removed)?]
  - AC-006.5: WHILE a Workspace is Suspended, THE backend SHALL record nothing for a ticket tracker event (issue created, issue moved to Done) that comes from its Jira site. No job is queued and nothing is replayed after reactivation (D29).
  - AC-006.12: WHEN a verified lifecycle event of type uninstalled arrives, THE backend SHALL change nothing and answer 204.
  - AC-006.14: THE tenant-isolation test SHALL show that a verified call from the Jira site of Workspace A reads only A's rows, and that a call whose token names B's `cloudId` reads only B's rows, never A's (ADR-0002).
  - AC-006.15: WHEN a verified lifecycle event of type installed arrives at `POST /api/v1/jira-app/lifecycle`, THE backend SHALL change nothing in the catalog and answer 204; the `cloudId` is linked only by the Admin at UC-10 (AC-004.19, GBR-24).
  - AC-006.16: IF an installed event comes from a `cloudId` linked to no Workspace, THEN THE backend SHALL log the `cloudId` and answer 204; calls from that site then get MSG152 (AC-006.3).

### STORY-007: File storage per Workspace (KAN-96)
- Owner: Đặng Nhật Minh. Sprint S1.
- As a developer of Evidence (UC-19, UC-22) and Wiki documents (UC-48), I want one file storage service so that
  every file is kept in its Workspace's folder and no file of one company is ever read or deleted by another.
- Acceptance:
  - AC-007.1: WHEN `FileStorage.store` is called with content while a Workspace is bound, THE storage SHALL write the content under `FILE_STORAGE_ROOT`, in the folder named by that Workspace's id, and return a new key it generated itself and the stored size in bytes (TS-004).
  - AC-007.2: THE storage SHALL never use a caller's file name or path to build where a file is written; the key is the only name of a stored file.
  - AC-007.3: WHEN `read` or `delete` is called with a key while a Workspace is bound, THE storage SHALL look for the key only in that Workspace's folder.
  - AC-007.4: IF a key of Workspace B is used to `read` while Workspace A is bound, THEN THE storage SHALL throw `NotFoundException` and return no content of B (GBR-37).
  - AC-007.5: IF a key is not in the format the storage generates, for example it contains `..`, a path separator, a drive letter or a NUL character, or is absolute, THEN THE storage SHALL refuse it, touch no file and log the attempt (path traversal).
  - AC-007.6: IF the content is larger than `FILE_STORAGE_MAX_BYTES` (default 10 MB = 10 × 1024 × 1024 bytes, the limit of MSG28, MSG34 and MSG89), THEN THE storage SHALL throw `FileTooLargeException` and keep no file or part of one. The caller shows its own message (MSG34 for Evidence, MSG89 for Wiki documents).
  - AC-007.7: IF `store`, `read` or `delete` is called while no Workspace is bound, THEN THE storage SHALL throw and touch no file.
  - AC-007.8: IF writing fails midway, THEN THE storage SHALL leave no file under the key and no partial file in the Workspace's folder.
  - AC-007.9: IF `read` is called with a well-formed key that has no file in the bound Workspace's folder, THEN THE storage SHALL throw `NotFoundException`.
  - AC-007.10: WHEN `delete` is called with a key, THE storage SHALL remove that file for good. A well-formed key with no file SHALL change nothing and raise no error, so a retried delete succeeds.
  - AC-007.11: THE tenant-isolation test SHALL store a file in Workspace A and show that, while Workspace B is bound, it can be neither read nor deleted with A's key.

### STORY-008: Local Docker Compose for the whole stack (KAN-94)
- Owner: Trần Trung Hiếu. Sprint S1.
- As a developer, I want the whole stack started with one command so that dev and demo behave the same.
- Acceptance:
  - AC-008.1: WHEN a developer runs `docker compose up` in the repository root with a filled `.env`, THE local stack SHALL start and report healthy: PostgreSQL 16 with pgvector (the catalog database and the Workspace databases), the backend web process, the worker (same backend image, `worker` profile), the web app, Ollama and Caddy (TS-001).
  - AC-008.2: WHEN the local stack is up, THE Caddy of the local stack SHALL serve `<site name>.localhost`, `login.localhost` and `api.localhost` with the routing of AC-009.2, AC-009.4 and AC-009.5, so that host-based resolution works locally.
  - AC-008.3: WHEN the local stack is up, THE backend and the worker SHALL reach Ollama at `OLLAMA_BASE_URL`, and the local Ollama SHALL have the embedding model `bge-m3` and the chat model named in `.env` pulled.
  - AC-008.4: THE web process SHALL apply the catalog changelog to the catalog database and then the tenant changelog to every Workspace database as the owner user at startup, and the worker SHALL never run Liquibase.
  - AC-008.5: IF a required variable is missing from `.env`, THEN THE backend SHALL refuse to start and name the missing variable.
  - AC-008.6: THE compose files SHALL take every credential from `.env` and hold no password, token or secret value; `.env.example` SHALL list every variable name with no real value.
  - AC-008.7: THE `infra/README.md` SHALL list the variables each environment needs and the command that starts the local stack (TS-001).
  - AC-008.8: THE staging stack SHALL run no Ollama container; the backend and the worker SHALL reach the AI VPS through `OLLAMA_BASE_URL` (D41, KAN-12).
  - AC-008.9: IF the tenant changelog fails on one Workspace database at startup, THEN THE web process SHALL stop with an error that names that Workspace's site name, and leave the other databases as they are.

### STORY-009: Caddy wildcard and routing by host (KAN-95)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the system, I want Caddy to route by host so that every Workspace has its own Workspace site and the platform
  hosts reach their own parts.
- Acceptance:
  - AC-009.1: THE main Caddy (`infra/caddy/Caddyfile`) SHALL serve `api.`, `login.` and every Workspace site over HTTPS with a valid certificate. This includes a Workspace created after the deployment, with no manual certificate step (TS-002).
  - AC-009.2: WHEN a request comes to a Workspace site, THE Caddy SHALL send `/api/*` to the backend web process and every other path to the web app.
  - AC-009.4: WHEN a request comes to `login.`, THE Caddy SHALL send `/api/*` to the backend web process (the central sign-in of `identity`) and answer 404 to every other path.
  - AC-009.5: WHEN a request comes to `api.`, THE Caddy SHALL send `/api/v1/jira-app/*` and `/actuator/health` to the backend web process and answer 404 to every other path.
  - AC-009.6: THE Caddy SHALL pass the original `Host` header unchanged to the backend for every `/api/v1/**` request (TS-002).
  - AC-009.7: IF a request comes to the bare base domain or to `www.`, THEN THE Caddy SHALL answer 404 itself and pass nothing to the web app or the backend (decision Q15).
  - AC-009.8: IF a request comes over plain HTTP, THEN THE Caddy SHALL redirect it to HTTPS. Every HTTPS answer SHALL carry `Strict-Transport-Security` (KAN-13).
  - AC-009.9: WHILE the backend restarts during a deployment, THE Caddy SHALL retry the backend for a configured window before answering 502 (KAN-13).
  - AC-009.10: WHERE the environment is staging, THE Caddy SHALL put the staging Workspace sites and `login.` behind basic authentication or an IP allow-list, and SHALL leave `api.` open to Forge Remote, which is protected by the Forge token (decision Q11).

### STORY-010: Staging VPS and CI/CD: a merge to main deploys staging (KAN-200)
- Owner: Trần Trung Hiếu. Sprint S1 (due 30/10). Subtasks KAN-6, KAN-8…KAN-14.
- As the team, we want every merge to `main` deployed to staging automatically so that Forge Remote can call the
  backend and everyone tests the same build.
- Acceptance:
  - AC-010.1: WHEN a pull request into `main` is opened or updated, THE CI SHALL run the frontend job and the backend job (`mvn -B verify`, Testcontainers included). The required checks (frontend, backend, PR title, gitleaks) SHALL block the merge while one is red (KAN-6).
  - AC-010.2: WHEN a commit lands on `main`, THE CD workflow SHALL build the backend and web app images and push them to GHCR tagged `sha-` plus the commit SHA and `staging` (KAN-8, KAN-9).
  - AC-010.3: WHEN the images are pushed, THE CD workflow SHALL deploy staging through the self-hosted deploy runner, in this order (KAN-14):
    1. pull the `staging` images;
    2. apply the catalog changelog, then the tenant changelog to every Workspace database, as the owner user;
    3. start the stack;
    4. wait for every health check;
    5. run the demo seed (STORY-012; staging only, design decision P7);
    6. run the smoke test.
  - AC-010.4: THE smoke test SHALL call `https://api.` plus the staging base domain plus `/actuator/health` and expect UP. It SHALL load one staging Workspace site's page (200) and call its `/api/v1/me` expecting 401 with `code` `MSG07`, which proves the resolver found the Workspace.
  - AC-010.5: IF a build, a migration, a health check, the seed or the smoke test fails, THEN THE CD workflow SHALL fail with the failing step, and for a migration the failing Workspace, in its log.
  - AC-010.6: THE staging environment SHALL use the hosts `*.staging.tsolve.io.vn`, `api.staging.tsolve.io.vn` and `login.staging.tsolve.io.vn`, through `TSOLVE_BASE_DOMAIN` = `staging.tsolve.io.vn` (decision Q11). The site name `staging` is reserved (D44 (25/10); AC-002.6, AC-013.3), so no production Workspace can own `staging.tsolve.io.vn`.
  - AC-010.7: THE staging secrets SHALL live only in the server's `.env` (mode 600, owned by the deploy user) and in GitHub secrets, never in the repository.
  - AC-010.8: THE staging stack SHALL have a memory and CPU limit and a health check on every container, and restart `unless-stopped` (KAN-12).
  - AC-010.9: THE application VPS SHALL refuse password login over SSH and allow key login only. Only ports 22, 80 and 443 SHALL be open to the internet (KAN-10).
  - AC-010.10: THE staging databases SHALL be reachable only on the private network of the stack, and each Workspace's runtime user SHALL reach only its own database (KAN-11, AC-001.26).

### STORY-011: AI VPS for Ollama (KAN-203)
- Owner: Trần Trung Hiếu. Sprint S1 (may land after 30/10).
- As the team, we want Ollama on its own VPS, reachable only privately from the application VPS, so that the models
  get their own CPU and RAM and company content never leaves the servers the team operates.
- Acceptance:
  - AC-011.1: THE AI VPS SHALL run Ollama as a service with `bge-m3` and the chat model chosen by the benchmark (KAN-140), with a health check (D41).
  - AC-011.2: THE Ollama of the AI VPS SHALL be reachable only from the application VPS, over a private, encrypted link (D41, GBR-12).
  - AC-011.3: IF anyone connects from the internet to the Ollama port of the AI VPS, THEN THE AI VPS SHALL refuse the connection.
  - AC-011.4: WHEN the staging backend calls `OLLAMA_BASE_URL`, THE AI VPS SHALL return an embedding of 1024 dimensions from `bge-m3` and a completion from the chat model.
  - AC-011.5: THE AI VPS SHALL be set up with the same bootstrap as the application VPS: key-only SSH, firewall, unattended security upgrades (KAN-10).
  - AC-011.6: THE AI VPS SHALL allow no outgoing connection to the Internet except the package and model downloads of its setup, and Ollama SHALL not log prompts or answers (GBR-12; PII masking dropped on 29/10).

### STORY-012: Demo seed of two Workspaces on dev and staging (KAN-97, KAN-46)
- Owner: Trần Trung Hiếu. Sprint S3.
- As the team, we want demo data for two Workspaces on dev and staging so that the demo of 15/11 runs every flow,
  while production never gets a Workspace from the configuration.
- Acceptance:
  - AC-012.1: WHEN the backend runs with the `seed` profile on dev or staging, THE seed SHALL read the demo data from `infra/seed/` and create two Workspaces through the provisioning of STORY-013, with all its rules (D42).
  - AC-012.2: WHEN a seed Workspace is created, THE seed SHALL call every `DemoSeedContributor` in its declared order, each inside `TenantContext.runIn` of that Workspace. Each contributor writes its own module's data (decision Q14).
  - AC-012.3: IF `TSOLVE_ENVIRONMENT` is `production` or not set, THEN THE seed SHALL write nothing and exit with a non-zero status (GBR-24, D42).
  - AC-012.4: THE production configuration (the production compose file, its environment file template and the production deploy workflow) SHALL contain no seed profile, seed command or seed data reference. The `seed` profile shipped inside the backend image stays inert there, because AC-012.3 refuses to run in production.
  - AC-012.5: IF a seed Workspace's site name already exists, THEN THE seed SHALL leave that Workspace unchanged, run no contributor for it, and say so in its output. Before the S3 seed, staging is reset once with AC-012.8, so the contributors run on freshly created Workspaces (user, 25/10).
  - AC-012.6: IF a provisioning rule or a contributor fails, THEN THE seed SHALL stop, exit with a non-zero status and name the Workspace and the contributor that failed.
  - AC-012.7: THE demo data in `infra/seed/` SHALL describe, for each of the two Workspaces, the data of TS-005:
    - a site name, the Admin email, the company information and the Jira site URL;
    - Accounts that hold all five Roles;
    - Departments;
    - a Project linked to a Jira project;
    - Published tickets;
    - Wiki documents.

    Each part is written by the module that owns it.
  - AC-012.8: WHEN `infra/seed/run-seed.sh --reset` runs on dev or staging, THE script SHALL drop every Workspace database, empty the catalog and the file storage of that environment, and then run the seed, so that every seed Workspace is created anew and all contributors run for it (user, 25/10).
  - AC-012.9: IF `run-seed.sh --reset` runs while `TSOLVE_ENVIRONMENT` is `production`, not set, or anything other than `dev` or `staging`, THEN THE script SHALL change nothing and exit with a non-zero status (GBR-24, D42).

### STORY-013: Ops script: create, suspend and reactivate a Workspace (KAN-240)
- Owner: Trần Trung Hiếu. Sprint S2.
- As the T-Solve operations team, we want one script that sets up a company's Workspace, and suspends or reactivates
  it, so that every Workspace is created the same way without a screen in the application (GBR-24, GBR-38).
- Acceptance:
  - AC-013.1: WHEN `infra/ops/workspace.sh create --site <site name> --admin-email <email>` runs with values that pass AC-013.2 to AC-013.5, THE script SHALL, through the backend's `ops` profile:
    1. create the Workspace database and its runtime user (AC-001.26);
    2. apply the tenant changelog to it as the owner user;
    3. record the Workspace in the catalog as Active, with the email in `workspace_emails`;
    4. inside `TenantContext.runIn` of the new Workspace, create the Active Admin Account with that email
       through `identity`'s `FirstAdminCreator`;

    and print the Workspace site address (GBR-24).
  - AC-013.2: IF the site name is missing or not an RFC 1123 label (AC-002.4), THEN THE script SHALL refuse with an error naming the rule and create nothing.
  - AC-013.3: IF the site name is `admin`, `login`, `api`, `www` or `staging`, or is the site name of another Workspace, THEN THE script SHALL refuse with an error naming the rule and create nothing (D11, D44 (25/10)).
  - AC-013.4: IF the email is missing or not a valid email address, THEN THE script SHALL refuse and create nothing.
  - AC-013.5: IF the email already belongs to another Workspace in the catalog, THEN THE script SHALL refuse with an error that cites GBR-16 and create nothing.
  - AC-013.6: IF any step of AC-013.1 fails, THEN THE script SHALL undo the steps already done (drop the database and its user, remove the catalog rows), exit with a non-zero status and name the failing step, so that nothing is left created (GBR-24).
  - AC-013.7: WHEN `workspace.sh suspend --site <site name>` runs on an Active Workspace, THE script SHALL set it Suspended in the catalog. After the commit the backend SHALL publish the application event `WorkspaceSuspended` with its id, and the next request to its Workspace site or from its Jira site SHALL be refused (MSG150, AC-006.4).
  - AC-013.8: WHEN `workspace.sh reactivate --site <site name>` runs on a Suspended Workspace, THE script SHALL set it Active, the backend SHALL publish `WorkspaceReactivated` with its id, and the next request SHALL be served.
  - AC-013.9: IF `suspend` is run on a Suspended Workspace, `reactivate` on an Active one, or either on a site name of no Workspace, THEN THE script SHALL change nothing and exit with a message that says so.
  - AC-013.10: THE script SHALL offer no command that deletes a Workspace, changes its site name or reads data inside its database (GBR-38).
  - AC-013.11: WHEN a Workspace has been created, THE Tenant Resolver SHALL resolve its Workspace site on the next request, with no wait and no restart.
  - AC-013.12: THE script SHALL run only on the application VPS (or a developer machine), with the owner database credentials from the server's `.env`, and SHALL print no password.

## Out of Scope
- Production environment, backup and restore of every Workspace database (KAN-201) and monitoring, alerts and code
  quality (KAN-202): Sprint S3, outside this iteration. KAN-12 may prepare the production compose file; it is checked
  under KAN-201.
- Any screen for creating, suspending or reactivating a Workspace; deleting a Workspace (GBR-38).
- `runInTenant`, the job queue on JobRunr, the per-Workspace cap and not running a Suspended Workspace's jobs (D12,
  D43, GBR-38) → `worker` (Phạm Tùng Dương). JobRunr's own tables live in the catalog database; a job carries only
  its Workspace id and record ids.
- The central sign-in: UC-01, the D9 state value, sessions, the check that an email was added by the Admin (GBR-03),
  the Jira account link (UC-59) → `identity`.
- The Admin Account itself, the UC-05 screen and its messages (MSG111, MSG155 on screen) → `identity`; this module
  only keeps the email list (AC-004.25, AC-004.26).
- The company information, the service account and the UC-10 screen (MSG153 on screen) → `workspace`; this module
  only links the Jira site (AC-004.19…AC-004.24).
- The Jira App panel, its manifest and its messages shown to users (MSG152 on screen), and checking that the linked
  Account is Active on each call (GBR-19) → `jira-app` / `identity`.
- The web app's handling of MSG150 and MSG151 → `fe-foundation`.
- The LLM and embedding clients and the AI fallback (GBR-12) → `ai`.
- The demo data each module contributes to the seed → that module's `DemoSeedContributor`.
- Bringing the legacy tables of AC-001.21 to AC-001.20 → their owning modules.
- The certificate mechanism of AC-009.1 and the private-link mechanism of AC-011.2 → `design.md`, once the user
  decides them.

## Interface Contracts

### Exposed by this module

**`vn.tsolve.common.tenant`**
```java
public interface TenantContext {
    Optional<UUID> currentWorkspaceId();
    UUID requireWorkspaceId();                      // throws when none is bound (AC-002.16)
    <T> T runIn(UUID workspaceId, Supplier<T> action); // AC-002.12 – AC-002.15
    void runIn(UUID workspaceId, Runnable action);
}
// The tenant DataSource is the application's primary DataSource: modules inject it (or use JPA) as usual,
// and every connection goes to the bound Workspace's database (AC-002.20 – AC-002.23).
```
- Only the resolvers (STORY-002, STORY-006) and `runIn` bind a Workspace. A thread started by a module has no
  Workspace until it calls `runIn` (`worker`'s `runInTenant` wraps it).

**`vn.tsolve.platform` (service and dto)**
```java
public interface WorkspaceCatalog {                             // STORY-004
    Optional<WorkspaceSummary> findById(UUID workspaceId);
    Optional<WorkspaceSummary> findBySiteName(String siteName);
    Optional<WorkspaceSummary> findByCloudId(String cloudId);
    WorkspaceSummary linkJiraSite(String siteUrl);              // bound Workspace; AC-004.19 – AC-004.24
    void claimEmail(String email);                              // bound Workspace; AC-004.25, AC-004.26
}
record WorkspaceSummary(UUID id, String siteName, WorkspaceStatus status,
                        Optional<String> jiraSiteUrl, Optional<String> jiraCloudId) {}
enum WorkspaceStatus { ACTIVE, SUSPENDED }
record WorkspaceSuspended(UUID workspaceId) {}                  // published after commit (AC-013.7)
record WorkspaceReactivated(UUID workspaceId) {}

public interface ForgeTokenVerifier { VerifiedForgeCall verify(String bearerToken); }   // throws when invalid
public interface ForgeCallContext { Optional<VerifiedForgeCall> current(); }
record VerifiedForgeCall(String cloudId, Optional<String> atlassianAccountId) {}

public interface FileStorage {                                   // STORY-007
    StoredFile store(InputStream content);
    InputStream read(String key);
    void delete(String key);
}
record StoredFile(String key, long sizeBytes) {}

public interface DemoSeedContributor {                           // STORY-012, profile "seed"
    int order();
    void seed(SeedWorkspace workspace);   // runs inside TenantContext.runIn(workspace.id())
}
record SeedWorkspace(UUID id, String siteName, JsonNode data) {}  // data = the module's part
```
Catalog errors are `BusinessRuleException` with a field (be-foundation AC-001.3). Storage errors are
`FileTooLargeException` and `NotFoundException`, and an invalid key is refused with an exception that logs the
attempt.

**Ops script** (`infra/ops/workspace.sh`, backend profile `ops`): `create --site --admin-email`, `suspend --site`,
`reactivate --site` (STORY-013).

**REST (host `api.` only)**

| Endpoint | Auth | Answer |
|----------|------|--------|
| `POST /api/v1/jira-app/lifecycle` | Forge Invocation Token | 204 (AC-006.12, AC-006.15, AC-006.16) |
| every other `/api/v1/jira-app/**` path | Forge Invocation Token, then the Workspace of the `cloudId` | the handler of its module; 401 without `code`, 403 `MSG152`, 403 for a Suspended Workspace (AC-006.4) |
| `/actuator/health` | none | health |

**Web resolver answers** (every host that is not `login.` or `api.`; before Spring Security):
404 `MSG151`, 403 `MSG150`, as `application/problem+json`.

**Schema:** catalog tables `workspaces` and `workspace_emails`; the tenant-table convention (AC-001.20), its
allow-list (AC-001.21) and the guard test.

**Configuration** (names in `.env.example`, no real values):
- `TSOLVE_BASE_DOMAIN`: `tsolve.io.vn`, `staging.tsolve.io.vn` or `localhost`.
- `TSOLVE_ENVIRONMENT`: `dev`, `staging` or `production`.
- `CATALOG_DB_URL`, `CATALOG_DB_USER`, `CATALOG_DB_PASSWORD`; `TENANT_DB_HOST`; `TENANT_DB_POOL_MAX` (default 5).
- `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD` (owner user, migrations and the ops script).
- `CATALOG_SECRET_KEY` (encrypts the Workspace database passwords).
- `FORGE_APP_ID`, `FORGE_JWKS_URL`.
- `FILE_STORAGE_ROOT`, and `FILE_STORAGE_MAX_BYTES` (default 10485760).
- `OLLAMA_BASE_URL`: read by `ai`.

**Deployment:**
- Hosts: `<site name>.`, `login.` and `api.`, under the base domain.
- Images on GHCR: `tsolve-backend` (also the worker, `worker` profile, and the ops script, `ops` profile) and
  `tsolve-frontend`.
- Compose and Caddy files under `infra/`, plus `docker-compose.yml` for local use.

### Required from other modules
- `be-foundation` (Trần Hải Anh):
  - the `ProblemDetail` format and `GlobalExceptionHandler` (MSG06 on an unhandled exception);
  - `NotFoundException` and `BusinessRuleException` with a field;
  - the tenant changelog `db.changelog-master.xml` as the one entry point of module changesets;
  - its session security chain and its CSRF rule (AC-002.11) must not apply to `api.`, which uses this module's
    chain (AC-005.6).
- Owner of changeset 001 (`be-foundation`): the existing `workspace` table moves out of the tenant changelog (the
  company information stays in the Workspace database, owned by `workspace`; the catalog row is this module's), and
  `audit_events` meets AC-001.20 as an append-only table.
- `identity`:
  - calls `TenantContext.runIn` with the Workspace of the checked state value on the `login.` callback;
  - takes the ticket tracker account only from `ForgeCallContext`;
  - calls `WorkspaceCatalog.claimEmail` when an Account is added (UC-05);
  - offers `FirstAdminCreator.create(email)` for STORY-013, and contributes the Accounts with the five Roles to the
    seed.
- `workspace` (Đặng Tấn Bình): calls `WorkspaceCatalog.linkJiraSite` when the Admin enters the Jira site at UC-10.
- `worker` (Phạm Tùng Dương):
  - `runInTenant` built on `TenantContext.runIn`;
  - does not run a Suspended Workspace's jobs, from the catalog status and the events `WorkspaceSuspended` /
    `WorkspaceReactivated` (GBR-38);
  - runs JOB-02 once per Active Workspace (GBR-37);
  - keeps JobRunr's tables in the catalog database and never runs Liquibase.
- `jira-app` (Đặng Tấn Bình):
  - sends the Forge Invocation Token on every Forge Remote call to `api.`;
  - points the installed and uninstalled lifecycle events at `POST /api/v1/jira-app/lifecycle`;
  - uses one remote base URL per Forge environment (staging: `api.staging.tsolve.io.vn`).
- Every module with tables: follows AC-001.20 for each new table and, when built, takes its legacy tables off the
  allow-list (AC-001.21). It contributes its demo data through a `DemoSeedContributor`.
- `ai` (Trần Hải Anh): reads `OLLAMA_BASE_URL`; the model names come from the benchmark (KAN-140).

## Definition of Done
- [ ] Every acceptance criterion has a test, workflow step or `infra/` script check that cites its AC id;
      `sdd.ts trace platform` passes
- [ ] `mvn verify` passes, with the Testcontainers tests of STORY-001, STORY-002 and the tenant-isolation test
- [ ] A merge to `main` deploys staging and the smoke test passes in the workflow log (AC-010.4)
- [ ] The Ollama port of the AI VPS is shown closed from the internet (AC-011.3)
- [ ] `workspace.sh create` then `suspend` then `reactivate` runs on staging (STORY-013)
- [ ] The owners of `be-foundation`, `identity`, `workspace`, `worker`, `jira-app`, `ticket-repository`,
      `ticket-review`, `search`, `ai` and `fe-foundation` are told what they call, implement or change

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     OUT OF SCOPE: name what is excluded, so it is not built by accident; adding it later is a
     spec change (Changelog row, re-approval).
     OPEN QUESTIONS: where the answer is missing, write [NEEDS CLARIFICATION: <question>] and ask
     the user — never fill the gap with a guess. `bun harnessclaude/hooks/sdd.ts check <module>`
     fails while any remain.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above (Date, Story if
     applicable, Change summary, Reason). Once the user has approved the spec, any edit locks the
     module's code until they approve the new version (`sdd.ts approve <module>`). -->
