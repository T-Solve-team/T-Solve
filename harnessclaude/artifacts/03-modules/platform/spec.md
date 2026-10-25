---
module: platform
date: 2026-10-25
owner: BA
---

# Spec: platform

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-25 | STORY-001..STORY-012 | Initial spec from Report3 (GBR-03, GBR-12, GBR-14, GBR-19, GBR-24, GBR-37; MSG119–MSG124, MSG125–MSG139; D6, D9, D11, D12, D20, D29, D41, D42, D43; ADR-0001), Stories TS-001…TS-005, TS-010…TS-013, TS-022, the Jira descriptions of KAN-94…KAN-101, KAN-105, KAN-200 (subtasks KAN-6, KAN-8…KAN-14), KAN-203, Phan_cong v6 §1 and the planning decisions Q1–Q15 of 25/10 | Plan the `platform` module (KAN-196): the tenant layer of the pool model and the infrastructure every module runs on |

## Requirements

### Functional
1. One deployment and one database serve every Workspace (ADR-0001). The Workspace of a request is decided only by
   the host of a Workspace site, the `cloudId` of a verified Forge Invocation Token, or an explicit scoped binding
   used by jobs, the central sign-in callback, the Platform Console and the demo seed. No API takes a Workspace from
   the request (GBR-37).
2. Every tenant record carries `workspace_id`. Isolation has three layers: the resolvers, Hibernate `@TenantId` on
   the base entity, and PostgreSQL row-level security as the last guard (ADR-0001).
3. The Workspace registry is the platform record of each Workspace (company name, site name, Google domain,
   status) and its Jira sites. It enforces the UC-55 rules (GBR-14, GBR-24, D11, D20; decision Q7). It is called by
   `platform-console`, the resolvers and the demo seed.
4. A Suspended Workspace is refused on its Workspace site (MSG120) and from its Jira sites (MSG121). Ticket tracker
   events from it are dropped (D29). Its jobs are paused by `worker` from the registry's status and events (D12,
   decision Q6).
5. File storage keeps the files of each Workspace in that Workspace's own folder, and no file key reaches another
   Workspace (TS-004, GBR-37).
6. The infrastructure covers these environments:
   - local: Docker Compose with Ollama;
   - staging: on the application VPS, deployed on every merge to `main`;
   - AI VPS: runs Ollama, reachable only privately (D41).

   Caddy routes by host. The demo seed runs on dev and staging only (D42).

### Non-Functional
1. Tenant isolation (ADR-0001, TS-163): an automated test with two Workspaces shows that nothing written in
   Workspace A can be read in Workspace B. It covers a JPA query, a native SQL query, a file and a call from the
   Jira App. The full end-to-end run (web app, Jira App, Search, Chatbot) is KAN-186. The isolation criterion lives
   in this spec.
2. Every acceptance criterion has a test whose name cites its id (`…_AC_002_3`); `sdd.ts trace platform` passes.
   Infrastructure criteria are checked by a workflow step or a script under `infra/` whose name or log line cites
   the id.
3. `mvn verify` passes on every commit. Database criteria run against PostgreSQL started by Testcontainers from
   `pgvector/pgvector:pg16`, with the Liquibase changelog applied as in production (be-foundation decision Q7).
4. No secret, credential, host or URL is in a committed file (`backend/AGENTS.md` §4). Environment values come from a
   per-environment `.env` (git-ignored, mode 600 on servers) or from GitHub secrets. gitleaks runs on every PR.
5. The application's database user does not own the tables, is not a superuser and has no BYPASSRLS. Migrations run
   as a separate owner user (KAN-11).
6. Only Caddy listens on the internet, on ports 80 and 443. SSH is the only other open port. The backend,
   PostgreSQL and Ollama ports are never reachable from the internet.
7. No log line contains a token, a secret, a file's content or personal data (`backend/AGENTS.md` §9).

## User Stories

### STORY-001: Tenant schema base and the convention every module follows (KAN-98)
- Owner: Trần Trung Hiếu. Sprint S1.
- As a developer of any module, I want one base entity and one changeset convention for tenant tables so that
  every package filters by Workspace the same way and no table is left unscoped.
- Acceptance:
  - AC-001.1: THE schema SHALL hold the platform table `workspaces` with `id` (UUID, primary key), `company_name` (not null), `site_name` (not null, unique), `google_domain` (not null, unique, stored lower-case), `status` (`ACTIVE` or `SUSPENDED`, not null), `created_at` and `updated_at` (`TIMESTAMPTZ`, not null), and no table named `workspace` (decisions Q2, Q3).
  - AC-001.2: THE schema SHALL hold the platform table `jira_sites` with `id` (UUID, primary key), `workspace_id` (not null, foreign key to `workspaces`), `site_url` (not null, unique), `cloud_id` (nullable, unique), `created_at` and `updated_at` (GBR-14, D6).
  - AC-001.3: WHEN the whole Liquibase changelog is applied to an empty database in any environment, THE schema SHALL contain no row in `workspaces` or `jira_sites` (GBR-24, D42). Only the demo seed of STORY-012 creates Workspaces, on dev and staging.
  - AC-001.4: THE backend SHALL offer the base entity `TenantEntity` (`@MappedSuperclass` in `vn.tsolve.common.tenant`): its column `workspace_id` (UUID) is a Hibernate `@TenantId`, not updatable.
  - AC-001.5: WHEN an entity that extends `TenantEntity` is saved while a Workspace is bound, THE backend SHALL store the bound Workspace's id in `workspace_id`, whatever value the caller put on the entity.
  - AC-001.6: WHEN a JPQL or derived query reads an entity that extends `TenantEntity`, THE backend SHALL return only rows of the bound Workspace.
  - AC-001.7: IF an entity that extends `TenantEntity` is saved while no Workspace is bound, THEN THE backend SHALL throw and store nothing.
  - AC-001.8: THE tenant-table convention SHALL be, for every table that is not on the platform allow-list:
    - a plural snake_case name;
    - `id` UUID primary key;
    - `workspace_id UUID NOT NULL REFERENCES workspaces (id)`;
    - an index whose first column is `workspace_id`;
    - every uniqueness rule that holds within a Workspace declared as a unique constraint that includes
      `workspace_id`;
    - `created_at` and `updated_at` as `TIMESTAMPTZ NOT NULL`;
    - row-level security enabled and forced, with the policy of AC-003.1 (decision Q2; Phan_cong v6 §1).
  - AC-001.9: THE changesets SHALL keep the existing naming of `backend/AGENTS.md` §2: file `db/changelog/changes/NNN-<slug>.sql`, id `NNN-<slug>`, author = the module.
  - AC-001.10: THE platform allow-list SHALL name each table that has no tenant row-level security, each with its reason:
    - `workspaces` and `jira_sites`: platform data, read before a Workspace is known;
    - `databasechangelog` and `databasechangeloglock`: Liquibase;
    - the JobRunr tables of `worker`: a job's Workspace is in the job itself (D43).
  - AC-001.11: THE legacy allow-list SHALL name, each with its owning module, the tables that existed before this spec and do not yet meet AC-001.8:
    - `resolved_ticket_snapshot` and `solution` → `ticket-repository`;
    - `solution_source` → `ticket-repository`;
    - `solution_approval` → `ticket-review`;
    - `reuse_record` → `search`;
    - `audit_event` → `be-foundation`.

    Each owning module adds `workspace_id` and row-level security to its tables when it is built (decision Q4).
  - AC-001.12: WHEN the convention guard test runs against the applied changelog, THE test SHALL fail for every table in the schema that is on neither allow-list and breaks any part of AC-001.8, and name the table and the missing part.
  - AC-001.13: IF a table on the legacy allow-list meets AC-001.8, or a table that was not on the legacy allow-list when this spec was approved is added to it, THEN THE convention guard test SHALL fail and say that the allow-list only shrinks.

### STORY-002: TenantContext and the Tenant Resolver of the web app (KAN-99)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the system, I want the Workspace of every web request decided from the host name only so that no module ever
  takes a Workspace id from a request and a company never sees another company's data.
- Acceptance:
  - AC-002.1: THE backend SHALL offer `TenantContext` in `vn.tsolve.common.tenant`:
    - `currentWorkspaceId()`: the bound Workspace, or empty;
    - `requireWorkspaceId()`: the bound Workspace, or throws when none is bound;
    - `runIn(workspaceId, action)`: runs the action with that Workspace bound (decision Q5).
  - AC-002.2: WHEN a request arrives whose host is `<label>.` followed by the configured base domain (`TSOLVE_BASE_DOMAIN`), and the label is the site name of a Workspace, THE Tenant Resolver SHALL bind that Workspace for the whole request.
  - AC-002.3: THE Tenant Resolver SHALL read the Workspace site from the `Host` header only, compared without case and without the port. It SHALL ignore `X-Forwarded-Host`, `Forwarded` and any Workspace id in a header, a parameter, a cookie or the body (GBR-37).
  - AC-002.4: IF the label is not an RFC 1123 label (lowercase letters, digits and hyphens, starting and ending with a letter or a digit, at most 63 characters), THEN THE Tenant Resolver SHALL answer 404 with a `ProblemDetail` with `code` `MSG119` without looking up the registry (D11).
  - AC-002.5: IF the label is the site name of no Workspace, THEN THE Tenant Resolver SHALL answer 404 with `code` `MSG119`.
  - AC-002.6: IF the host is the bare base domain, `www.` followed by the base domain, has more than one label before the base domain, or is not under the base domain, THEN THE backend SHALL answer 404 with `code` `MSG119` (decision Q15).
  - AC-002.7: IF the Workspace of the host is Suspended, THEN THE Tenant Resolver SHALL answer 403 with `code` `MSG120` and run no handler, whatever session the request carries (GBR-24, D16).
  - AC-002.8: THE Tenant Resolver SHALL run before every Spring Security check, so that MSG119 and MSG120 are answered before any 401 or 403 of sign-in or roles (agreed with `fe-foundation`, KAN-198).
  - AC-002.9: THE answers of AC-002.4 to AC-002.7 SHALL have content type `application/problem+json` and follow the contract's `ProblemDetail` (`status`, `code`), with no detail that names a Workspace.
  - AC-002.10: WHEN a request's host is `login.`, `admin.` or `api.` followed by the base domain, THE Tenant Resolver SHALL bind no Workspace for that host. The Workspace on `api.` comes only from STORY-006.
  - AC-002.11: WHEN a request ends, normally or with an exception, THE backend SHALL unbind its Workspace, so that the next request on the same thread starts with none.
  - AC-002.12: WHEN `runIn` is called while no Workspace is bound, or while the same Workspace is bound, THE backend SHALL run the action in that Workspace. After the action, it SHALL restore the binding as it was before, also when the action throws.
  - AC-002.13: IF `runIn` is called with a Workspace different from the bound one, THEN THE backend SHALL throw and not run the action, so that no request or job ever switches Workspace (GBR-37).
  - AC-002.14: IF `runIn` is called with an id that belongs to no Workspace, THEN THE backend SHALL throw and not run the action.
  - AC-002.15: WHILE a Workspace is Suspended, THE backend SHALL still let `runIn` bind it, so that the Platform Console can count its Accounts (UC-54). Pausing its jobs is done by `worker` (decision Q6).
  - AC-002.16: IF code calls `requireWorkspaceId()` while no Workspace is bound, THEN THE backend SHALL throw an exception that the global handler answers with 500 and `code` `MSG06` (be-foundation AC-001.7), and it SHALL log the call site.
  - AC-002.17: WHILE the development base domain is `localhost`, THE Tenant Resolver SHALL resolve `<site name>.localhost` with any port the same way as a production Workspace site (fe-foundation design Q9).
  - AC-002.18: THE tenant-isolation test SHALL create two Workspaces A and B with rows in a table that follows AC-001.8. It SHALL show, through the web resolver, that a request on A's Workspace site reads only A's rows with a JPA query and with a native SQL query, and never a row of B (TS-011, ADR-0001).

### STORY-003: PostgreSQL row-level security (KAN-100)
- Owner: Trần Hải Anh. Sprint S1.
- As the system, I want row-level security on every tenant table so that a query that forgets the Workspace still
  never returns or changes another Workspace's rows.
- Acceptance:
  - AC-003.1: THE schema SHALL enable and force row-level security on every tenant table, with one policy that allows a row in reads and writes only when its `workspace_id` equals the transaction's setting `app.workspace_id`.
  - AC-003.2: WHEN a transaction starts while a Workspace is bound, THE backend SHALL set `app.workspace_id` to that Workspace for that transaction only (local to the transaction). A pooled connection then never carries it into another transaction.
  - AC-003.3: WHILE no Workspace is bound, THE database SHALL return no row of a tenant table and SHALL refuse every insert, update and delete on it.
  - AC-003.4: WHEN a native SQL query without any `workspace_id` condition runs while Workspace A is bound, THE database SHALL return only rows of Workspace A.
  - AC-003.5: IF a native insert or update while Workspace A is bound writes a row with the `workspace_id` of Workspace B, THEN THE database SHALL refuse it and store nothing.
  - AC-003.6: THE backend SHALL connect for requests and jobs with a database user that does not own the tables, is not a superuser and has no BYPASSRLS. Liquibase SHALL run with a separate owner user (`DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD`) (KAN-11).
  - AC-003.7: IF the application user tries to disable row-level security or drop a policy, THEN THE database SHALL refuse it.
  - AC-003.8: THE tests of AC-003.1 to AC-003.7 SHALL run against PostgreSQL started by Testcontainers with the changelog applied by the owner user and the queries run as the application user.

### STORY-004: Workspace registry (KAN-98; used by KAN-113…KAN-116)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the Platform Console and the resolvers, we want one registry of Workspaces and their Jira sites that enforces
  the UC-55 rules so that every way of creating a Workspace obeys the same rules.
- Acceptance:
  - AC-004.1: WHEN `WorkspaceRegistryService.create` is called with a company name, a site name, a Google domain and one or more Jira site URLs that pass AC-004.2 to AC-004.7, THE registry SHALL store an Active Workspace with its Jira sites, no `cloud_id` yet, and return it (GBR-24, D6).
  - AC-004.2: IF the company name is empty or blank, THEN THE registry SHALL refuse with `code` `MSG01` on field `companyName` and store nothing.
  - AC-004.3: IF the site name is not an RFC 1123 label (AC-002.4), THEN THE registry SHALL refuse with `code` `MSG126` on field `siteName`. IF it is `admin`, `login`, `api` or `www`, THEN `MSG139`. IF it is the site name of another Workspace, THEN `MSG125`. These are checked in that order and nothing is stored (GBR-24, D11).
  - AC-004.4: IF the Google domain is not a valid domain name, THEN THE registry SHALL refuse with `code` `MSG127` on field `googleDomain`. IF another Workspace has it (compared without case), THEN `MSG138`. Nothing is stored (D7, D22).
  - AC-004.5: IF no Jira site URL is given, THEN THE registry SHALL refuse with `code` `MSG01` on field `jiraSiteUrls`. IF a URL is not an `https` address with a host and no path other than `/`, no query and no fragment, THEN it SHALL refuse with `MSG136` on field `jiraSiteUrls[<index>]`. Nothing is stored (GBR-14).
  - AC-004.6: IF a Jira site URL, after it is normalised to `https://<host>` in lower case, is already registered to any Workspace or appears twice in the same request, THEN THE registry SHALL refuse with `code` `MSG137` on that field. It SHALL not say which Workspace has it and SHALL store nothing (GBR-14, D6).
  - AC-004.7: IF several rules of AC-004.2 to AC-004.6 fail, THEN THE registry SHALL report the first one, in the order company name, site name, Google domain, Jira site URLs.
  - AC-004.8: IF two creations race for the same site name, Google domain or Jira site URL, THEN THE registry SHALL store only one of them and refuse the other with the same code as AC-004.3, AC-004.4 or AC-004.6, never with a 500.
  - AC-004.9: WHEN a Workspace has been created, THE Tenant Resolver SHALL resolve its Workspace site on the next request, with no wait and no restart (MSG128).
  - AC-004.10: WHEN `suspend` is called on an Active Workspace, THE registry SHALL make it Suspended. After the commit it SHALL publish the application event `WorkspaceSuspended` with its id, and the next request to its Workspace site or from its Jira sites SHALL be refused (MSG120, MSG121).
  - AC-004.11: WHEN `reactivate` is called on a Suspended Workspace, THE registry SHALL make it Active. After the commit it SHALL publish `WorkspaceReactivated` with its id, and the next request SHALL be served.
  - AC-004.12: IF `suspend` is called on a Suspended Workspace or `reactivate` on an Active one, THEN THE registry SHALL change nothing, publish no event and return the Workspace as it is.
  - AC-004.13: IF `suspend`, `reactivate` or a lookup by id is called with an id of no Workspace, THEN THE registry SHALL throw `NotFoundException` with `code` `MSG06`.
  - AC-004.14: THE registry SHALL offer no way to delete a Workspace or to change its site name, Google domain or Jira sites after creation (GBR-24, D20).
  - AC-004.15: THE registry SHALL find a Workspace by id, by site name (without case) and by the `cloud_id` of one of its Jira sites, and SHALL list Workspaces page by page with their company name, site name, status and Jira sites.
  - AC-004.16: THE registry SHALL read and write only platform tables and SHALL never bind a Workspace itself (GBR-37).

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
  - AC-005.8: IF a request to a Workspace site, `login.` or `admin.` goes to a path under `/api/v1/jira-app/`, THEN THE backend SHALL answer 404 with `code` `MSG06`. A Forge token SHALL never authenticate a request on those hosts (GBR-37).

### STORY-006: Tenant Resolver of the Jira App and Jira site registration (KAN-101)
- Owner: Trần Trung Hiếu. Sprint S1 (registration agreed with Đặng Tấn Bình before 30/10).
- As the system, I want every verified call from the T-Solve app resolved to the Workspace of its Jira site, and
  each Jira site bound to its Workspace when the app is installed, so that a company's Jira site reaches only its
  own Workspace.
- Acceptance:
  - AC-006.1: WHEN a verified call (STORY-005) carries a `cloudId` that is bound to a Jira site of an Active Workspace, THE backend SHALL bind that Workspace for the whole request (GBR-14, GBR-37).
  - AC-006.2: THE backend SHALL take the `cloudId` only from the verified token's claims, never from a header, a parameter or the body (GBR-19).
  - AC-006.3: IF the verified `cloudId` is bound to no Jira site, THEN THE backend SHALL answer 403 with a `ProblemDetail` with `code` `MSG124` and `params` `tracker_name` = `Jira`, and run no handler (decision Q8).
  - AC-006.4: IF the Workspace of the verified `cloudId` is Suspended, THEN THE backend SHALL answer 403 with `code` `MSG121` and run no handler (GBR-24).
  - AC-006.5: WHILE a Workspace is Suspended, THE backend SHALL record nothing for a ticket tracker event (issue created, issue moved to Done) that comes from its Jira sites. No job is queued and nothing is replayed after reactivation (D29).
  - AC-006.6: WHEN a verified Forge lifecycle event of type installed arrives at `POST /api/v1/jira-app/lifecycle`, THE backend SHALL handle it with no Workspace bound. If it finds the registered Jira site of that installation (AC-006.7), it SHALL store the verified `cloudId` on that site, then answer 204 (GBR-14, D6).
  - AC-006.7: THE backend SHALL find the registered Jira site of an installation only by these means, never from the unsigned request body (D6, decision Q9):
    - a site URL in the verified token's signed claims; or
    - when the token carries none, the `cloudId` that each unbound registered site URL gives through Atlassian's
      public tenant information at the address followed by `/_edge/tenant_info`, compared with the verified
      `cloudId`.
  - AC-006.8: IF no registered Jira site matches the installation, THEN THE backend SHALL store nothing, log the `cloudId`, and answer 204. Calls from that Jira site then get MSG124 (AC-006.3).
  - AC-006.9: IF the installation's `cloudId` is already stored on the matching Jira site, THEN THE backend SHALL change nothing and answer 204.
  - AC-006.10: IF the matching Jira site already has another `cloudId`, or the verified `cloudId` is already stored on another Jira site, THEN THE backend SHALL change nothing, log the conflict and answer 204. The platform team settles it by hand (D20).
  - AC-006.11: IF Atlassian's tenant information cannot be read during the matching of AC-006.7, THEN THE backend SHALL store nothing, log the failure and answer 503.
  - AC-006.12: WHEN a verified lifecycle event of type uninstalled arrives, THE backend SHALL change nothing and answer 204.
  - AC-006.13: WHILE the matching Workspace is Suspended, THE backend SHALL still store the `cloudId` of an installation; its calls stay refused with MSG121 until reactivation.
  - AC-006.14: THE tenant-isolation test SHALL show that a verified call from a Jira site of Workspace A reads only A's rows, and that a call whose token names B's `cloudId` reads only B's rows, never A's (ADR-0001).

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
  - AC-008.1: WHEN a developer runs `docker compose up` in the repository root with a filled `.env`, THE local stack SHALL start and report healthy: PostgreSQL 16 with pgvector, the backend web process, the worker (same backend image, `worker` profile), the web app, Ollama and Caddy (TS-001).
  - AC-008.2: WHEN the local stack is up, THE Caddy of the local stack SHALL serve `<site name>.localhost`, `admin.localhost`, `login.localhost` and `api.localhost` with the routing of AC-009.2 to AC-009.5, so that host-based resolution works locally.
  - AC-008.3: WHEN the local stack is up, THE backend and the worker SHALL reach Ollama at `OLLAMA_BASE_URL`, and the local Ollama SHALL have the embedding model `bge-m3` and the chat model named in `.env` pulled.
  - AC-008.4: THE web process SHALL run the Liquibase changelog as the owner user at startup, and the worker SHALL never run Liquibase.
  - AC-008.5: IF a required variable is missing from `.env`, THEN THE backend SHALL refuse to start and name the missing variable.
  - AC-008.6: THE compose files SHALL take every credential from `.env` and hold no password, token or secret value; `.env.example` SHALL list every variable name with no real value.
  - AC-008.7: THE `infra/README.md` SHALL list the variables each environment needs and the command that starts the local stack (TS-001).
  - AC-008.8: THE staging stack SHALL run no Ollama container; the backend and the worker SHALL reach the AI VPS through `OLLAMA_BASE_URL` (D41, KAN-12).

### STORY-009: Caddy wildcard and routing by host (KAN-95)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the system, I want Caddy to route by host so that every Workspace has its own Workspace site and the platform
  hosts reach their own parts.
- Acceptance:
  - AC-009.1: THE main Caddy (`infra/caddy/Caddyfile`) SHALL serve `api.`, `login.`, `admin.` and every Workspace site over HTTPS with a valid certificate. This includes a Workspace created after the deployment, with no manual certificate step (TS-002).
  - AC-009.2: WHEN a request comes to a Workspace site, THE Caddy SHALL send `/api/*` to the backend web process and every other path to the web app.
  - AC-009.3: WHEN a request comes to `admin.`, THE Caddy SHALL send `/api/*` to the backend web process and every other path to the web app, which chooses the Platform Console routes by host.
  - AC-009.4: WHEN a request comes to `login.`, THE Caddy SHALL send `/api/*` to the backend web process (the central sign-in of `identity`) and answer 404 to every other path.
  - AC-009.5: WHEN a request comes to `api.`, THE Caddy SHALL send `/api/v1/jira-app/*` and `/actuator/health` to the backend web process and answer 404 to every other path.
  - AC-009.6: THE Caddy SHALL pass the original `Host` header unchanged to the backend for every `/api/v1/**` request (TS-002).
  - AC-009.7: IF a request comes to the bare base domain or to `www.`, THEN THE Caddy SHALL answer 404 itself and pass nothing to the web app or the backend (decision Q15).
  - AC-009.8: IF a request comes over plain HTTP, THEN THE Caddy SHALL redirect it to HTTPS. Every HTTPS answer SHALL carry `Strict-Transport-Security` (KAN-13).
  - AC-009.9: WHILE the backend restarts during a deployment, THE Caddy SHALL retry the backend for a configured window before answering 502 (KAN-13).
  - AC-009.10: WHERE the environment is staging, THE Caddy SHALL put the staging Workspace sites, `admin.` and `login.` behind basic authentication or an IP allow-list, and SHALL leave `api.` open to Forge Remote, which is protected by the Forge token (decision Q11).

### STORY-010: Staging VPS and CI/CD: a merge to main deploys staging (KAN-200)
- Owner: Trần Trung Hiếu. Sprint S1 (due 30/10). Subtasks KAN-6, KAN-8…KAN-14.
- As the team, we want every merge to `main` deployed to staging automatically so that Forge Remote can call the
  backend and everyone tests the same build.
- Acceptance:
  - AC-010.1: WHEN a pull request into `main` is opened or updated, THE CI SHALL run the frontend job and the backend job (`mvn -B verify`, Testcontainers included). The required checks (frontend, backend, PR title, gitleaks) SHALL block the merge while one is red (KAN-6).
  - AC-010.2: WHEN a commit lands on `main`, THE CD workflow SHALL build the backend and web app images and push them to GHCR tagged `sha-` plus the commit SHA and `staging` (KAN-8, KAN-9).
  - AC-010.3: WHEN the images are pushed, THE CD workflow SHALL deploy staging through the self-hosted deploy runner, in this order (KAN-14):
    1. pull the `staging` images;
    2. apply the Liquibase changelog as the owner user;
    3. start the stack;
    4. wait for every health check;
    5. run the smoke test.
  - AC-010.4: THE smoke test SHALL call `https://api.` plus the staging base domain plus `/actuator/health` and expect UP. It SHALL load one staging Workspace site's page (200) and call its `/api/v1/me` expecting 401 with `code` `MSG07`, which proves the resolver found the Workspace.
  - AC-010.5: IF a build, the migration, a health check or the smoke test fails, THEN THE CD workflow SHALL fail with the failing step in its log.
  - AC-010.6: THE staging environment SHALL use the hosts `*.staging.tsolve.io.vn`, `api.staging.tsolve.io.vn`, `login.staging.tsolve.io.vn` and `admin.staging.tsolve.io.vn`, through `TSOLVE_BASE_DOMAIN` = `staging.tsolve.io.vn` (decision Q11).
  - AC-010.7: THE staging secrets SHALL live only in the server's `.env` (mode 600, owned by the deploy user) and in GitHub secrets, never in the repository.
  - AC-010.8: THE staging stack SHALL have a memory and CPU limit and a health check on every container, and restart `unless-stopped` (KAN-12).
  - AC-010.9: THE application VPS SHALL refuse password login over SSH and allow key login only. Only ports 22, 80 and 443 SHALL be open to the internet (KAN-10).
  - AC-010.10: THE staging database SHALL be reachable only on the private network of the stack, and each database user SHALL reach only its own database (KAN-11).

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

### STORY-012: Demo seed of two Workspaces on dev and staging (KAN-97)
- Owner: Trần Trung Hiếu. Sprint S3.
- As the team, we want demo data for two Workspaces on dev and staging so that the demo of 15/11 runs every flow,
  while production never gets a Workspace from the configuration.
- Acceptance:
  - AC-012.1: WHEN the backend runs with the `seed` profile on dev or staging, THE seed SHALL read the demo data from `infra/seed/` and create two Workspaces through `WorkspaceRegistryService.create`, with all the rules of STORY-004, as UC-55 does (D42).
  - AC-012.2: WHEN a seed Workspace is created, THE seed SHALL call every `DemoSeedContributor` in its declared order, each inside `TenantContext.runIn` of that Workspace. Each contributor writes its own module's data (decision Q14).
  - AC-012.3: IF `TSOLVE_ENVIRONMENT` is `production` or not set, THEN THE seed SHALL write nothing and exit with a non-zero status (GBR-24, D42).
  - AC-012.4: THE production configuration SHALL contain no seed profile, seed command or seed data reference.
  - AC-012.5: IF a seed Workspace's site name already exists, THEN THE seed SHALL leave that Workspace unchanged, run no contributor for it, and say so in its output.
  - AC-012.6: IF a registry rule or a contributor fails, THEN THE seed SHALL stop, exit with a non-zero status and name the Workspace and the contributor that failed.
  - AC-012.7: THE demo data in `infra/seed/` SHALL describe, for each of the two Workspaces, the data of TS-005:
    - a company name, a site name, a Google domain and Jira site URLs;
    - Accounts that hold all five Roles;
    - Departments;
    - a Project linked to a Jira project;
    - Published tickets;
    - Wiki documents.

    Each part is written by the module that owns it.

## Out of Scope
- Production environment, backup and restore (KAN-201) and monitoring, alerts and code quality (KAN-202): Sprint S3,
  outside this iteration. KAN-12 may prepare the production compose file; it is checked under KAN-201.
- The Platform Console's REST endpoints, screens, Operator session and messages MSG128–MSG135, MSG140 (UC-54…56)
  → `platform-console` (Đặng Tấn Bình). This module gives it the registry and `runIn`.
- `runInTenant`, the job queue on JobRunr, the per-Workspace cap and postponing a Suspended Workspace's jobs (D12,
  D43) → `worker` (Phạm Tùng Dương).
- The central sign-in: UC-01, the D9 state value, the `hd` check, sessions, MSG09, MSG10, MSG122, MSG123, MSG135,
  MSG141, the Jira account link → `identity` (KAN-197).
- The Jira App panel, its manifest and its messages shown to users (MSG62, MSG74, MSG121, MSG124 on screen), and
  checking that the linked Account is Active on each call (GBR-19) → `jira-app` / `identity`.
- The web app's handling of MSG119 and MSG120 and the route tree by host → `fe-foundation` (KAN-198).
- The LLM and embedding clients and the AI fallback (GBR-12) → `ai`.
- The demo data each module contributes to the seed → that module's `DemoSeedContributor`.
- Adding or removing a Jira site after creation (D20): done by the platform team by hand.
- Adding `workspace_id` and row-level security to the legacy tables of AC-001.11 → their owning modules.
- The method that reshapes the existing `workspace` table into `workspaces` (a new fix-forward changeset, which we
  recommend, or rewriting changeset 001) → the owner of changeset 001. This spec states only the outcome
  (AC-001.1, AC-001.3).
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

@MappedSuperclass
public abstract class TenantEntity {               // AC-001.4
    @TenantId @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;
}
```
- Only the resolvers (STORY-002, STORY-006) and `runIn` bind a Workspace. A thread started by a module has no
  Workspace until it calls `runIn` (`worker`'s `runInTenant` wraps it).

**`vn.tsolve.platform` (service and dto)**
```java
public interface WorkspaceRegistryService {
    WorkspaceResponse create(CreateWorkspaceRequest request);   // AC-004.1 – AC-004.8
    WorkspaceResponse suspend(UUID workspaceId);                // AC-004.10, AC-004.12
    WorkspaceResponse reactivate(UUID workspaceId);             // AC-004.11, AC-004.12
    Optional<WorkspaceResponse> findById(UUID workspaceId);
    Optional<WorkspaceResponse> findBySiteName(String siteName);
    Optional<WorkspaceResponse> findByCloudId(String cloudId);
    Page<WorkspaceResponse> list(Pageable pageable);
}
record CreateWorkspaceRequest(String companyName, String siteName, String googleDomain, List<String> jiraSiteUrls) {}
record WorkspaceResponse(UUID id, String companyName, String siteName, String googleDomain,
                         WorkspaceStatus status, List<JiraSiteResponse> jiraSites, Instant createdAt) {}
record JiraSiteResponse(String siteUrl, boolean connected) {}   // connected = a cloudId is stored
enum WorkspaceStatus { ACTIVE, SUSPENDED }
record WorkspaceSuspended(UUID workspaceId) {}                  // published after commit
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
record SeedWorkspace(UUID id, String siteName, String googleDomain, JsonNode data) {}  // data = the module's part
```
Registry errors are `BusinessRuleException` with a field (be-foundation AC-001.3). Unknown ids are
`NotFoundException`. Storage errors are `FileTooLargeException` and `NotFoundException`, and an invalid key is
refused with an exception that logs the attempt.

**REST (host `api.` only)**

| Endpoint | Auth | Answer |
|----------|------|--------|
| `POST /api/v1/jira-app/lifecycle` | Forge Invocation Token | 204; 503 when Atlassian's tenant information cannot be read (AC-006.11) |
| every other `/api/v1/jira-app/**` path | Forge Invocation Token, then the Workspace of the `cloudId` | the handler of its module; 401 without `code`, 403 `MSG124`, 403 `MSG121` |
| `/actuator/health` | none | health |

**Web resolver answers** (every host that is not `login.`, `admin.` or `api.`; before Spring Security):
404 `MSG119`, 403 `MSG120`, as `application/problem+json`.

**Schema:** `workspaces`, `jira_sites`, the tenant-table convention (AC-001.8), the two allow-lists (AC-001.10,
AC-001.11) and the guard test.

**Configuration** (names in `.env.example`, no real values):
- `TSOLVE_BASE_DOMAIN`: `tsolve.io.vn`, `staging.tsolve.io.vn` or `localhost`.
- `TSOLVE_ENVIRONMENT`: `dev`, `staging` or `production`.
- `FORGE_APP_ID`, `FORGE_JWKS_URL`.
- `FILE_STORAGE_ROOT`, and `FILE_STORAGE_MAX_BYTES` (default 10485760).
- `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD`.
- `OLLAMA_BASE_URL`: read by `ai`.

**Deployment:**
- Hosts: `<site name>.`, `admin.`, `login.` and `api.`, under the base domain.
- Images on GHCR: `tsolve-backend` (also the worker, `worker` profile) and `tsolve-frontend`.
- Compose and Caddy files under `infra/`, plus `docker-compose.yml` for local use.

### Required from other modules
- `be-foundation` (Trần Hải Anh):
  - the `ProblemDetail` format and `GlobalExceptionHandler` (MSG06 on an unhandled exception);
  - `NotFoundException` and `BusinessRuleException` with a field;
  - the Liquibase master changelog as the one entry point;
  - its session security chain and its CSRF rule (AC-002.11) must not apply to `api.`, which uses this module's
    chain (AC-005.6).
- Owner of changeset 001 (`be-foundation`): the reshaped `workspaces` table of AC-001.1 and AC-001.3.
- `identity` (KAN-197):
  - calls `TenantContext.runIn` with the Workspace of the checked state value on the `login.` callback;
  - takes the ticket tracker account only from `ForgeCallContext`;
  - contributes the Accounts with the five Roles to the seed, as UC-56 creates them.
- `worker` (Phạm Tùng Dương):
  - `runInTenant` built on `TenantContext.runIn`;
  - postpones a Suspended Workspace's jobs, from the registry status and the events `WorkspaceSuspended` /
    `WorkspaceReactivated` (D12);
  - lists the JobRunr tables on the platform allow-list;
  - never runs Liquibase.
- `platform-console` (Đặng Tấn Bình):
  - its REST endpoints call `WorkspaceRegistryService`;
  - UC-54 user counts and the UC-56 first Admin run inside `TenantContext.runIn` of the target Workspace (GBR-37).
- `jira-app` (Đặng Tấn Bình):
  - sends the Forge Invocation Token on every Forge Remote call to `api.`;
  - points the installed and uninstalled lifecycle events at `POST /api/v1/jira-app/lifecycle`;
  - uses one remote base URL per Forge environment (staging: `api.staging.tsolve.io.vn`).
- Every module with tables: follows AC-001.8 for each new table and, when built, takes its legacy tables off the
  allow-list (AC-001.11). It contributes its demo data through a `DemoSeedContributor`.
- `ai` (Trần Hải Anh): reads `OLLAMA_BASE_URL`; the model names come from the benchmark (KAN-140).

## Definition of Done
- [ ] Every acceptance criterion has a test, workflow step or `infra/` script check that cites its AC id;
      `sdd.ts trace platform` passes
- [ ] `mvn verify` passes, with the Testcontainers tests of STORY-001, STORY-003 and the tenant-isolation test
- [ ] A merge to `main` deploys staging and the smoke test passes in the workflow log (AC-010.4)
- [ ] The Ollama port of the AI VPS is shown closed from the internet (AC-011.3)
- [ ] The owners of `be-foundation`, `identity`, `worker`, `platform-console`, `jira-app`, `ticket-repository`,
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
