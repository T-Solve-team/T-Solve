---
module: platform
date: 2026-11-04
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
| 2026-11-04 | STORY-002, STORY-013 | AC-002.10: `admin.` binds no Workspace and serves the Platform Console (it no longer answers MSG151). Requirement 4 and AC-013.1 step 4: the Platform Console and the ops script share one provisioning service; the first Admin is invited. Out of Scope: the Console screens belong to `platform-console`, not to this module. Dependencies on `identity`: no `FirstAdminCreator` is assumed; the first-Admin service is settled in the platform plan. The rest of the HLA sync (Registry name, Jobs database, tenant row guard, object storage, Redis) is left to the platform Change Mode session | KAN-231 HLA sync, 4/11 (HLA Q1 = A, H-Q4, H-Q5, H-Q6; step 1 items L3–L5) |
| 2026-11-04 | STORY-001, STORY-002, STORY-004, STORY-005, STORY-006, STORY-007, STORY-008, STORY-009, STORY-010, STORY-012, STORY-013, new STORY-014 | **Multi-tenant HLA sync (KAN-98; items 1–12 and 19 of the KAN-231 list).** STORY-001: AC-001.15 adds `schema_version` and names the catalog the Platform / Tenant Registry (the Registry); AC-001.17 says identity's short-lived sign-in states and hand-off codes are not business data and live in the Registry; AC-001.21 and AC-001.27 reworded; new AC-001.28…AC-001.33 (per-database tenant row guard: the one-row table `tenant_identity`, a row policy on every tenant table, its guard test, no BYPASSRLS; the Jobs database). **AC-003.9 is retired** (it forbade row-level security policies; the guard of AC-001.29 reverses it); STORY-003 stays cancelled and its ids AC-003.1…AC-003.9 are never reused; its single criterion is the new AC-003.10 (no policy on `workspace_id`). STORY-002: AC-002.5 leaves `admin.` to AC-002.10; new AC-002.24, AC-002.25 (each transaction sets the tenant setting the guard checks; a misrouted connection sees nothing). STORY-004: AC-004.21 answered (MSG04, Q9); AC-004.28 reworded (the only endpoint that creates, suspends or reactivates a Workspace is the Console's on `admin.`, Q5). STORY-005: AC-005.8 adds `admin.`. STORY-006: AC-006.4 answered (403 with `MSG150`, Q10). STORY-007 rewritten for object storage (MinIO, H-Q4): AC-007.1, .3, .5, .7, .8, .9, .10 and .11 reworded; new AC-007.12…AC-007.15 (signed URL, no local files). STORY-008: AC-008.1 and AC-008.2 reworded; new AC-008.10, AC-008.11 (Redis, MinIO). STORY-009: AC-009.1 and AC-009.10 reworded; **AC-009.3 for `admin.` returns as AC-009.11** (removed ids are not reused). STORY-010: AC-010.6 and AC-010.10 reworded; new AC-010.11, AC-010.12 (Redis, MinIO and Jobs database in the staging stack). STORY-012: AC-012.8 reworded (object storage). STORY-013: retitled "Provisioning service and ops script"; AC-013.1 and AC-013.5 reworded; new AC-013.13…AC-013.20 (one `WorkspaceProvisioning` service for the Console and the script, the `FirstAdminCreator` port implemented by `identity`, the first Admin created Active and invited by email, the email-claim race, the invitation outside the all-or-nothing rule). **New STORY-014: operations readiness** (backup and restore drill, monitoring with Redis metrics and alert, edge limits on Caddy, load-test script and one measured staging run); (AC-014.1…AC-014.13); KAN-201 and KAN-202 move from Out of Scope into it. Requirements 1, 3, 4, 6, 7 and 8, NFR 3, 5 and 6, Out of Scope, Interface Contracts, Required from other modules and Definition of Done updated; JobRunr's tables move from the catalog to the Jobs database `tsolve_<env>_jobs` | Hiếu's decisions of 4/11 (H-Q1 A: Compose for the MVP and the demo, one Redis container with persistence; H-Q3 A; H-Q4 B; H-Q5 A; H-Q6 A) and the HLA Q1–Q4 of 3/11; step-1 questions Q1…Q10 of KAN-98 answered with the recommended option (user: "all recommendations"); sync list `KAN-231-hla-sync-step2.md`, items 1–12 and 19 |
| 2026-11-04 | STORY-004 | AC-004.21: the invalid Jira site URL refusal uses the new code `MSG156` instead of `MSG04`; `MSG156` added to the baseline System_Messages. | `MSG04` reads "No results found", which does not describe the error (user, 4/11) |
| 2026-11-04 | STORY-007, STORY-008, STORY-010, STORY-013, STORY-014 | **DevOps sync (KAN-98).** The object store is Garage (S3-compatible; ADR-014), not MinIO: AC-007 wording, AC-008.1, AC-008.11, AC-010.11, AC-010.12, AC-014.1, AC-014.6, AC-014.8, NFR 3 and 6, Requirement 6 and 7, the interface comment and the Definition of Done name Garage; the keys, signed URLs, "no local files" and the `FILE_STORAGE_*` names are unchanged. Redis and Garage run one per environment (Requirement 7). AC-014.1: the backup goes to a Cloudflare R2 bucket with `restic` (encrypted, deduplicated; ADR-015), not `age`; no new id, no id removed. STORY-014 is scheduled after 15/11 (DevOps Phase B), not in "Sprint S3"; its edge limits (AC-014.9 – AC-014.11) follow the DevOps design's step A5 default (proposed there in the Phase A edge stack); Out of Scope reworded the same way. AC-013.3: `grafana` joins the reserved site names (the DevOps design serves Grafana on `grafana.<base>`). Monitoring is Grafana Alloy, Prometheus, Grafana and Grafana alerts, not Alertmanager, and Loki stays for logs (design only; AC-014.6 – AC-014.8 keep their text). No acceptance criterion is added or removed | Hiếu's answers of 4/11 (Q1 Garage, Q2 restic to R2, Q3 STORY-014 after 15/11, Q4 Loki kept; W3 Alloy, no Alertmanager); DevOps design 4/11 and ADR-014, -015, -016; aligns the platform spec with the DevOps design |
| 2026-11-04 | STORY-013, STORY-008, STORY-014 | AC-013.15 now refuses to start without a bean for either port, `FirstAdminCreator` or `InvitationJobQueue` (reworded; no id added). Interface Contracts declare `InvitationJobQueue.queueFirstAdminInvitation(workspaceId, accountId)`, which the design already used. Configuration adds `FILE_STORAGE_PUBLIC_URL_PATTERN`, `CLOUDFLARE_API_TOKEN`, `STAGING_BASIC_AUTH_HASH` and the restic, R2 and Grafana variable names. The extra column of the DevOps sync row is merged into its Reason | Review of the platform plan (KAN-98): the design named a second port the spec did not; user decision of 4/11: platform provides a minimal `InvitationJobQueue` on JobRunr in the Jobs database and `worker` takes the job handler over later |
| 2026-11-04 | STORY-013, STORY-001 | Queue ownership made consistent (review 2, KAN-98): `platform` provides a minimal `InvitationJobQueue` on JobRunr in the Jobs database and owns the Jobs database and its tables; `worker` takes over the job handler and the background job server later. Out of Scope, Configuration (`JOBS_DB_*` also used by `platform` processes), Required from other modules (`worker`) and Interface Contracts reworded; AC-013.17 reworded (the job is queued through `InvitationJobQueue`, not "on `worker`"). No criterion added or removed | User decision of 4/11 after review 2 (finding M1): the first Admin's invitation does not wait for `worker`; the spec still gave the queue to `worker` |
| 2026-11-04 | STORY-001, STORY-013 | Table names follow `01_catalog.sql` / `02_tenant.sql`: AC-001.15, AC-001.16, AC-001.19 and the step that records a Workspace name the catalog tables `tenant_workspace` and `workspace_email`, with the key `workspace_id`. AC-001.20 and AC-001.23: a tenant table has a singular name and the UUID key `<table>_id`. AC-001.20: a tenant table has only the timestamp columns the DDL gives it. The other catalog columns stay as AC-001.15 gives them, and the DDL was changed to match | Schema decisions of 4/11 (KAN-33) |
| 2026-11-04 | STORY-001, STORY-013 | Merge of `main` (#112, KAN-33) into the HLA change: the schema names of #112 are applied to the HLA wording. AC-001.15, AC-001.16, AC-001.19, AC-013.1 step 4, AC-013.19 and the Schema contract name the catalog tables `tenant_workspace` (key `workspace_id`; `schema_version` kept) and `workspace_email`; AC-001.20 and AC-001.23 take the singular names, the `<table>_id` key and the DDL's timestamp columns; `audit_log_entry` replaces `audit_events` in the changeset-001 line. AC-001.17 (sign-in states in the Registry), AC-001.21 (`tenant_identity` on the allow-list), the Registry, `schema_version`, the tenant row guard and Garage keep the HLA wording. No criterion added or removed | Merge conflict of PR #114 with `main` (KAN-98, KAN-33) |
| 2026-11-05 | STORY-001, STORY-009, STORY-013 | **Keycloak sign-in (ADR-0003, KAN-275; identity design, "Platform change requests").** New AC-001.34 (`tenant_workspace` gets `google_domain` and `keycloak_organization_id`; `WorkspaceSummary` carries both). AC-001.17 no longer says the Registry hosts `identity`'s sign-in states and hand-off codes (identity deletes them, K12). AC-009.4: `login.` goes to Keycloak, not to the backend. STORY-013: `create` takes the Google domain (AC-013.1, AC-013.13 reworded; steps renumbered, AC-013.14 and AC-013.19 follow); new AC-013.21 (Google domain refused if missing or invalid), AC-013.22 (provisioning port call at creation), AC-013.23 (undo calls `remove`), AC-013.24 (a Workspace is Active at creation; the sign-in test is a runbook step, `docs/runbooks/onboard-company.md`). Requirements 1, 3 and 4, Out of Scope, Interface Contracts, Required from other modules and Schema reworded: no state value, hand-off code or sign-in table is mentioned any more | Decisions K1–K14 of ADR-0003 (4/11, 5/11), Q4 revised: no `PENDING_SIGN_IN` status, no `activate` (AC-013.1 keeps Active at creation); Hiếu's request that identity's old sign-in text leave the platform spec |

## Requirements

### Functional
1. One deployment serves every Workspace; **each Workspace has its own PostgreSQL database** and its own object-key
   prefix in object storage, and a catalog database (the HLA's Platform / Tenant Registry, "the Registry") holds only
   what is needed to find a Workspace (ADR-0002, GBR-37). JobRunr's tables live in a separate Jobs database. The Workspace
   of a request is decided only by the host of a Workspace site, the `cloudId` of a verified Forge Invocation Token,
   or an explicit scoped binding used by jobs, the sign-in callback of a Workspace site, the ops script and the demo seed. No API
   takes a Workspace from the request (GBR-37).
2. Binding a Workspace selects that Workspace's database for every query of the request or job; with no Workspace
   bound, no tenant database can be reached. Business tables carry no `workspace_id`.
3. The catalog holds, per Workspace: site name, status, database connection, the Google domain, the Keycloak
   Organization id, the Jira site and its `cloudId`, and the emails of its Accounts, so that one email belongs to one
   Workspace (GBR-16, GBR-37). It holds no sign-in data: sign-in is done by Keycloak (ADR-0003) and sessions are in Redis.
4. Workspaces are created, suspended and reactivated by a Platform Operator in the Platform Console, or by the
   operations team with the ops script, which is the hand-run form of the same provisioning service (GBR-24, GBR-38,
   KAN-240; HLA, 3/11; H-Q5). Creation takes the company's Google domain and has `identity` set up its sign-in in
   Keycloak through a provisioning port (ADR-0003, K6). The first Admin Account is created by `identity` through the port
   `FirstAdminCreator` and invited by email. Creation of the Workspace, its Keycloak sign-in setup and its Admin Account
   is all or nothing; the invitation email is sent after the commit and is not part of it. A Workspace is Active at
   creation; before telling the company, the platform admin follows the runbook `docs/runbooks/onboard-company.md`
   (Google redirect URI, test sign-in), which is not a status of the Workspace.
5. A Suspended Workspace keeps its database; its Workspace site is refused (MSG150), the T-Solve app in its Jira site
   returns an error, ticket tracker events from it are dropped (D29), and its jobs do not run (`worker`, GBR-38).
6. File storage keeps the files in object storage (Garage, S3-compatible), under the object-key prefix of each
   Workspace, serves them by signed URLs and keeps no file on the local disk of the backend or the worker; no file key
   reaches another Workspace (TS-004, GBR-37; H-Q4).
7. The infrastructure covers these environments. Docker Compose on a VPS is the deployment of the MVP and the demo
   (H-Q1); the HLA's Kubernetes and high-availability deployment is the target and is not built in this module:
   - local: Docker Compose with Ollama, Redis and Garage;
   - staging: on the application VPS, deployed on every merge to `main`;
   - AI VPS: runs Ollama, reachable only privately, with no Internet egress (D41).

   Caddy routes by host. Redis (the session store, one container with persistence) and Garage run in every stack, one of each per environment (staging and production never share them). The
   demo seed runs on dev and staging only (D42).
8. Operations: every database and the object storage are backed up and the restore is practised; the monitoring
   covers Redis; Caddy limits request size, time and request rate; a load test shows the stated target on staging
   (STORY-014; KAN-201, KAN-202).

### Non-Functional
1. Tenant isolation (ADR-0002, TS-163): an automated test with two Workspaces shows that nothing written in
   Workspace A can be read in Workspace B. It covers a JPA query, a native SQL query, a file and a call from the
   Jira App. The full end-to-end run (web app, Jira App, Search, Chatbot) is KAN-186. The isolation criterion lives
   in this spec.
2. Every acceptance criterion has a test whose name cites its id (`…_AC_002_3`); `sdd.ts trace platform` passes.
   Infrastructure criteria are checked by a workflow step or a script under `infra/` whose name or log line cites
   the id.
3. `mvn verify` passes on every commit. Database criteria run against PostgreSQL started by Testcontainers from
   `pgvector/pgvector:pg16`, with the catalog and tenant changelogs applied as in production. Object-storage criteria
   run against Garage started by Testcontainers (the pinned image of the stack, with its bucket bootstrap).
4. No secret, credential, host or URL is in a committed file (`backend/AGENTS.md` §4). Environment values come from a
   per-environment `.env` (git-ignored, mode 600 on servers) or from GitHub secrets. gitleaks runs on every PR.
5. Each Workspace database has its own runtime database user that can connect only to that database, does not own
   its tables, is not a superuser and has no `BYPASSRLS`. Migrations run as a separate owner user (KAN-11). Each
   Workspace database is backed up separately (GBR-37; STORY-014).
6. Only Caddy listens on the internet, on ports 80 and 443. SSH is the only other open port. The backend,
   PostgreSQL, Redis, Garage and Ollama ports are never reachable from the internet.
7. No log line contains a token, a secret, a database password, a file's content or personal data
   (`backend/AGENTS.md` §9).

## User Stories

### STORY-001: Catalog database (Platform / Tenant Registry), the tenant-database convention and the row guard (KAN-98)
- Owner: Trần Trung Hiếu. Sprint S1.
- As a developer of any module, I want one catalog of Workspaces and one convention for the tables of a Workspace
  database so that every module writes its tables the same way and no company's rows live in another company's
  database.
- Acceptance:
  - AC-001.15: THE catalog database (the Platform / Tenant Registry of the HLA, named `tsolve_<env>_registry`) SHALL hold the table `tenant_workspace` with `workspace_id` (UUID, primary key), `site_name` (not null, unique), `status` (`ACTIVE` or `SUSPENDED`, not null), `db_name` (not null, unique), `db_user` (not null), `db_password` (not null, encrypted as AC-001.24 says), `schema_version` (the tenant changelog version last applied to that database, not null), `jira_site_url` (nullable), `jira_cloud_id` (nullable, unique), `created_at` and `updated_at` (`TIMESTAMPTZ`, not null) (GBR-24, GBR-37).
  - AC-001.16: THE catalog database SHALL hold the table `workspace_email` with `email` (primary key, stored lower-case) and `workspace_id` (not null, foreign key to `tenant_workspace`), so that one email belongs to at most one Workspace (GBR-16, GBR-37).
  - AC-001.17: THE catalog SHALL hold no business data of a Workspace: no company information, Account name, role, ticket, Wiki document or chat (GBR-37, GBR-38). The Google domain and the Keycloak Organization id of AC-001.34 are catalog data, not business data.
  - AC-001.34: THE table `tenant_workspace` SHALL also have `google_domain` (the Workspace's Google domain, stored lower-case, not null) and `keycloak_organization_id` (the id of the Workspace's Keycloak Organization, unique, null until the sign-in setup of AC-013.22 has returned it), and `WorkspaceSummary` SHALL carry both as `googleDomain` and `keycloakOrganizationId` (ADR-0003, K2).
  - AC-001.18: THE backend SHALL keep two Liquibase changelogs: the catalog changelog `db/changelog/catalog/catalog-master.xml` and the tenant changelog `db/changelog/db.changelog-master.xml`, which every module already extends. A changeset belongs to exactly one of them.
  - AC-001.19: WHEN the catalog changelog is applied to an empty catalog database in any environment, THE catalog SHALL contain no row in `tenant_workspace` or `workspace_email` (GBR-24, D42). Only STORY-013 creates Workspaces.
  - AC-001.20: THE tenant-table convention SHALL be, for every table of the tenant changelog that is not on the allow-list of AC-001.21:
    - a singular snake_case name;
    - `<table>_id` UUID primary key, generated as AC-001.23 says; a join table instead has a composite primary key
      of its two `<entity>_id` columns and no separate key (decision X2);
    - no `workspace_id` column (ADR-0002);
    - only the timestamp columns that `02_tenant.sql` gives the table; a `created_at` or `updated_at` column is
      `TIMESTAMPTZ NOT NULL`, and an append-only table (rows never updated, for example `audit_log_entry`) has no
      `updated_at` (decision U3).
  - AC-001.21: THE allow-list SHALL name each tenant-changelog table that does not follow AC-001.20, each with its reason: `databasechangelog` and `databasechangeloglock` (Liquibase), `tenant_identity` (the one-row table of AC-001.28, which stays on the list), and the legacy tables `resolved_ticket_snapshot`, `solution`, `solution_source` (→ `ticket-repository`), `solution_approval` (→ `ticket-review`) and `reuse_record` (→ `search`), which their owning modules bring to AC-001.20 when they are built.
  - AC-001.22: WHEN the convention guard test runs against the applied tenant changelog, THE test SHALL fail for every table that is not on the allow-list and breaks any part of AC-001.20, and name the table and the missing part. IF a table on the allow-list meets AC-001.20, or a table not named in AC-001.21 is added to it, THEN the test SHALL fail and say that the allow-list only shrinks.
  - AC-001.23: THE primary key `<table>_id` of a tenant table SHALL be generated both by JPA (`@GeneratedValue(strategy = GenerationType.UUID)`) and by the database (`DEFAULT gen_random_uuid()`), so that application inserts and raw SQL inserts both get an id (decision X2).
  - AC-001.24: THE catalog SHALL store `db_password` encrypted with AES-GCM under the key `CATALOG_SECRET_KEY`, and the backend SHALL never log it or return it from any API.
  - AC-001.25: IF the key `CATALOG_SECRET_KEY` is missing or cannot decrypt a stored password, THEN THE backend SHALL refuse to open that Workspace's database and log which Workspace failed, without the password.
  - AC-001.26: THE runtime user of a Workspace database SHALL be able to connect to that database only; a connection with it to another Workspace database or to the catalog database SHALL be refused by PostgreSQL.
  - AC-001.27: THE tests of AC-001.15 to AC-001.26 and AC-001.28 to AC-001.33 SHALL run against PostgreSQL started by Testcontainers, with the changelogs applied by the owner user.
  - AC-001.28: WHEN a Workspace database is provisioned, THE provisioning SHALL write the id of that Workspace into the table `tenant_identity` (one row, column `tenant_id`) after the tenant changelog is applied. THE table SHALL be able to hold at most one row, and the runtime user SHALL have the right to read it and no right to change it (HLA isolation layer ③; H-Q6).
  - AC-001.29: THE tenant changelog SHALL enable row-level security and give every table of the tenant changelog, except `tenant_identity` and the two Liquibase tables, a row policy for reading and writing that lets a row through only when the transaction's tenant setting equals the `tenant_id` in `tenant_identity`. This includes the legacy tables of AC-001.21 and every table a module adds.
  - AC-001.30: IF a query of the runtime user runs while the transaction's tenant setting is unset or differs from the `tenant_id` of its database, THEN THE database SHALL return no row for a read and refuse a write, so that a connection routed to the wrong database sees and changes nothing.
  - AC-001.31: WHEN the convention guard test runs against the applied tenant changelog, THE test SHALL fail for every table that needs the policy of AC-001.29 and has none, and name the table. A table with the policy but a different condition SHALL fail the same way.
  - AC-001.32: THE runtime user of a Workspace database, the catalog user and the Jobs database user SHALL be neither superuser nor `BYPASSRLS`, and SHALL not own the tables on which a policy depends (HLA isolation layer ③).
  - AC-001.33: THE Jobs database `tsolve_<env>_jobs`, on the same PostgreSQL server as the catalog, SHALL hold only JobRunr's own tables, and its user SHALL be able to connect to that database only. It SHALL hold no Workspace data (H-Q3). The catalog changelog and the tenant changelog SHALL create nothing in it.

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
  - AC-002.5: IF the label is not `login`, `api` or `admin` (AC-002.10) and is the site name of no Workspace, THEN THE Tenant Resolver SHALL answer 404 with `code` `MSG151`.
  - AC-002.6: IF the host is the bare base domain, `www.` or `staging.` followed by the base domain, has more than one label before the base domain, or is not under the base domain, THEN THE backend SHALL answer 404 with `code` `MSG151` without looking up the catalog (decision Q15; `staging`: D44 (25/10)), except for the paths of AC-002.19.
  - AC-002.7: IF the Workspace of the host is Suspended, THEN THE Tenant Resolver SHALL answer 403 with `code` `MSG150` and run no handler, whatever session the request carries (GBR-38).
  - AC-002.8: THE Tenant Resolver SHALL run before every Spring Security check, so that MSG151 and MSG150 are answered before any 401 or 403 of sign-in or roles (agreed with `fe-foundation`, KAN-198).
  - AC-002.9: THE answers of AC-002.4 to AC-002.7 SHALL have content type `application/problem+json` and follow the contract's `ProblemDetail` (`status`, `code`), with no detail that names a Workspace.
  - AC-002.10: WHEN a request's host is `login.` or `api.` followed by the base domain, THE Tenant Resolver SHALL bind no Workspace for that host. The Workspace on `api.` comes only from STORY-006. The same holds for `admin.` followed by the base domain, which serves the Platform Console and binds no Workspace (HLA, 3/11; the Console and its sign-in belong to `platform-console` and `identity`).
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
  - AC-002.24: WHEN a transaction begins on a Workspace's database, THE backend SHALL set the tenant setting that the row policies of AC-001.29 check to the id of the bound Workspace, limited to that transaction, before the first statement; the same applies to a statement run outside a transaction.
  - AC-002.25: THE tenant-isolation test SHALL show that a connection which the router sends to Workspace B's database while Workspace A is bound reads no row and writes none, and that a statement with no tenant setting reads no row (KAN-99; AC-001.30).

### STORY-003: PostgreSQL row-level security on `workspace_id` (KAN-100) — cancelled
- Cancelled on 29/10 with the move to one database per Workspace (ADR-0002): the database boundary and the
  per-Workspace runtime user (AC-001.26) replace row-level security on `workspace_id`. Ids AC-003.1…AC-003.8 are
  retired.
- On 4/11 the last criterion of this story ("no row-level security policy", id 9) is retired too: the per-database
  tenant row guard of AC-001.28 to AC-001.32 reverses it (HLA isolation layer ③, H-Q6). No id of this story is reused.
- The row guard that replaces it is specified in STORY-001 and STORY-002. The ids AC-003.1…AC-003.9 are never
  reused. The story is kept only so that the stale tasks of the 25/10 plan still name a story the spec has; the tasks
  rewrite may drop it.
- Acceptance:
  - AC-003.10: THE tenant changelog SHALL create no row policy that compares a `workspace_id` column, because no tenant table has one (ADR-0002, AC-001.20); the only row policy is the guard of AC-001.29.

### STORY-004: Catalog service: find a Workspace, link its Jira site, keep its emails (KAN-98)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the resolvers, `workspace` (UC-10) and `identity` (UC-05), we want one service over the catalog so that every
  lookup, Jira-site link and email check follows the same rules.
- Acceptance:
  - AC-004.17: THE `WorkspaceCatalog` SHALL find a Workspace by id, by site name (without case) and by `jira_cloud_id`, and return its id, site name, status and Jira site; a lookup that finds nothing SHALL return empty.
  - AC-004.18: THE `WorkspaceCatalog` SHALL never return the database connection or password to a module; only the routing `DataSource` of STORY-002 reads them.
  - AC-004.19: WHEN `linkJiraSite(siteUrl)` is called while a Workspace with no Jira site is bound, THE catalog SHALL normalise the URL to `https://<host>` in lower case, read its `cloudId` from Atlassian's public tenant information at the address followed by `/_edge/tenant_info`, and store both on the bound Workspace (GBR-24; used by UC-10 of `workspace`).
  - AC-004.20: IF the `cloudId` of that site is already stored on another Workspace, THEN THE catalog SHALL refuse with `code` `MSG153` and `params` `tracker_name` = `Jira` on field `jiraSiteUrl`, store nothing and not name the other Workspace (GBR-14).
  - AC-004.21: IF the URL is not an `https` address with a host and no user info, no port, no path other than `/`, no query and no fragment, THEN THE catalog SHALL refuse with `code` `MSG156` on field `jiraSiteUrl` and store nothing (decision Q9, 4/11; `MSG156` is new, because the baseline text of `MSG04` is "No results found").
  - AC-004.22: IF Atlassian's tenant information cannot be read, THEN THE catalog SHALL store nothing, log the failure and throw an exception that the caller reports.
  - AC-004.23: IF `linkJiraSite` is called on a Workspace that already has a Jira site, THEN THE catalog SHALL change nothing and refuse, because the Jira site cannot be changed after the first sign-in (GBR-24).
  - AC-004.24: IF two Workspaces race to link the same `cloudId`, THEN THE catalog SHALL store it for only one and refuse the other with `MSG153`, never with a 500.
  - AC-004.25: WHEN `claimEmail(email)` is called while a Workspace is bound and the email (compared lower-case) is in no Workspace, THE catalog SHALL record it for the bound Workspace (GBR-16; used by UC-05 of `identity`).
  - AC-004.26: IF the email is already recorded for another Workspace, THEN THE catalog SHALL refuse with `code` `MSG155` on field `email` and record nothing; IF it is already recorded for the bound Workspace, THEN it SHALL change nothing and succeed.
  - AC-004.27: THE catalog service SHALL read and write only catalog tables and SHALL never bind a Workspace itself (GBR-37).
  - AC-004.28: THE catalog service SHALL offer no REST endpoint to create, suspend, reactivate or delete a Workspace or to change its site name. The only such endpoint is the Platform Console's, in `platform-console`, on `admin.` for an allow-listed Platform Operator, and it calls the `WorkspaceProvisioning` service of STORY-013; no endpoint deletes a Workspace or changes its site name (GBR-38; decision Q5, 4/11).

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

### STORY-006: Tenant Resolver of the Jira App (KAN-101)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the system, I want every verified call from the T-Solve app resolved to the Workspace whose Jira site it comes
  from, so that a company's Jira site reaches only its own Workspace.
- Acceptance:
  - AC-006.1: WHEN a verified call (STORY-005) carries a `cloudId` that the catalog links to an Active Workspace, THE backend SHALL bind that Workspace for the whole request (GBR-14, GBR-37).
  - AC-006.2: THE backend SHALL take the `cloudId` only from the verified token's claims, never from a header, a parameter or the body (GBR-19).
  - AC-006.3: IF the verified `cloudId` is linked to no Workspace, THEN THE backend SHALL answer 403 with a `ProblemDetail` with `code` `MSG152` and `params` `tracker_name` = `Jira`, and run no handler (decision Q8).
  - AC-006.4: IF the Workspace of the verified `cloudId` is Suspended, THEN THE backend SHALL answer 403 with a `ProblemDetail` with `code` `MSG150`, the code the web resolver answers (AC-002.7), and run no handler (GBR-38; decision Q10, 4/11).
  - AC-006.5: WHILE a Workspace is Suspended, THE backend SHALL record nothing for a ticket tracker event (issue created, issue moved to Done) that comes from its Jira site. No job is queued and nothing is replayed after reactivation (D29).
  - AC-006.12: WHEN a verified lifecycle event of type uninstalled arrives, THE backend SHALL change nothing and answer 204.
  - AC-006.14: THE tenant-isolation test SHALL show that a verified call from the Jira site of Workspace A reads only A's rows, and that a call whose token names B's `cloudId` reads only B's rows, never A's (ADR-0002).
  - AC-006.15: WHEN a verified lifecycle event of type installed arrives at `POST /api/v1/jira-app/lifecycle`, THE backend SHALL change nothing in the catalog and answer 204; the `cloudId` is linked only by the Admin at UC-10 (AC-004.19, GBR-24).
  - AC-006.16: IF an installed event comes from a `cloudId` linked to no Workspace, THEN THE backend SHALL log the `cloudId` and answer 204; calls from that site then get MSG152 (AC-006.3).

### STORY-007: File storage per Workspace in object storage (KAN-96)
- Owner: Đặng Nhật Minh. Sprint S1.
- As a developer of Evidence (UC-19, UC-22) and Wiki documents (UC-48), I want one file storage service over object
  storage so that every file is kept under its Workspace's prefix, any API replica can serve it, and no file of one
  company is ever read or deleted by another.
- Acceptance:
  - AC-007.1: WHEN `FileStorage.store` is called with content while a Workspace is bound, THE storage SHALL write the content as one object in the bucket `FILE_STORAGE_BUCKET`, under an object key that starts with that Workspace's id taken from `TenantContext`, and return a new key it generated itself and the stored size in bytes (TS-004, H-Q4).
  - AC-007.2: THE storage SHALL never use a caller's file name or path to build where a file is written; the key is the only name of a stored file.
  - AC-007.3: WHEN `read`, `delete` or `signedUrl` is called with a key while a Workspace is bound, THE storage SHALL look for the key only under that Workspace's prefix.
  - AC-007.4: IF a key of Workspace B is used to `read` while Workspace A is bound, THEN THE storage SHALL throw `NotFoundException` and return no content of B (GBR-37).
  - AC-007.5: IF a key is not in the format the storage generates, for example it contains `..`, a `/` or any path separator, a drive letter or a NUL character, or is absolute, THEN THE storage SHALL refuse it, touch no object and log the attempt, so that no key can reach another Workspace's prefix.
  - AC-007.6: IF the content is larger than `FILE_STORAGE_MAX_BYTES` (default 10 MB = 10 × 1024 × 1024 bytes, the limit of MSG28, MSG34 and MSG89), THEN THE storage SHALL throw `FileTooLargeException` and keep no object or part of one. The caller shows its own message (MSG34 for Evidence, MSG89 for Wiki documents).
  - AC-007.7: IF `store`, `read`, `delete` or `signedUrl` is called while no Workspace is bound, THEN THE storage SHALL throw and touch no object.
  - AC-007.8: IF writing fails midway, THEN THE storage SHALL leave no object under the key and no unfinished upload in the bucket.
  - AC-007.9: IF `read` is called with a well-formed key that has no object under the bound Workspace's prefix, THEN THE storage SHALL throw `NotFoundException`.
  - AC-007.10: WHEN `delete` is called with a key, THE storage SHALL remove that object for good. A well-formed key with no object SHALL change nothing and raise no error, so a retried delete succeeds.
  - AC-007.11: THE tenant-isolation test SHALL store a file in Workspace A and show that, while Workspace B is bound, it can be neither read, deleted nor given a signed URL with A's key.
  - AC-007.12: WHEN `signedUrl(key)` is called with a well-formed key whose object exists under the bound Workspace's prefix, THE storage SHALL return a URL that allows reading that one object only and stops working after `FILE_STORAGE_URL_TTL_SECONDS` seconds (H-Q4).
  - AC-007.13: IF `signedUrl` is called with a key that has no object under the bound Workspace's prefix, THEN THE storage SHALL throw `NotFoundException` and return no URL.
  - AC-007.14: THE storage SHALL keep no file on the local disk of the backend or the worker, whether the content is complete or partial, so that any API replica can serve any file (HLA, no pod-local files).
  - AC-007.15: THE bucket SHALL allow no anonymous access, and an object SHALL be readable only through the backend or a signed URL of AC-007.12.

### STORY-008: Local Docker Compose for the whole stack (KAN-94)
- Owner: Trần Trung Hiếu. Sprint S1.
- As a developer, I want the whole stack started with one command so that dev and demo behave the same.
- Acceptance:
  - AC-008.1: WHEN a developer runs `docker compose up` in the repository root with a filled `.env`, THE local stack SHALL start and report healthy: PostgreSQL 16 with pgvector (the catalog database, the Jobs database and the Workspace databases), Redis, Garage, the backend web process, the worker (same backend image, `worker` profile), the web app, Ollama and Caddy (TS-001, H-Q1).
  - AC-008.2: WHEN the local stack is up, THE Caddy of the local stack SHALL serve `<site name>.localhost`, `admin.localhost`, `login.localhost` and `api.localhost` with the routing of AC-009.2, AC-009.11, AC-009.4 and AC-009.5, so that host-based resolution works locally.
  - AC-008.3: WHEN the local stack is up, THE backend and the worker SHALL reach Ollama at `OLLAMA_BASE_URL`, and the local Ollama SHALL have the embedding model `bge-m3` and the chat model named in `.env` pulled.
  - AC-008.4: THE web process SHALL apply the catalog changelog to the catalog database and then the tenant changelog to every Workspace database as the owner user at startup, and the worker SHALL never run Liquibase.
  - AC-008.5: IF a required variable is missing from `.env`, THEN THE backend SHALL refuse to start and name the missing variable.
  - AC-008.6: THE compose files SHALL take every credential from `.env` and hold no password, token or secret value; `.env.example` SHALL list every variable name with no real value.
  - AC-008.7: THE `infra/README.md` SHALL list the variables each environment needs and the command that starts the local stack (TS-001).
  - AC-008.8: THE staging stack SHALL run no Ollama container; the backend and the worker SHALL reach the AI VPS through `OLLAMA_BASE_URL` (D41, KAN-12).
  - AC-008.9: IF the tenant changelog fails on one Workspace database at startup, THEN THE web process SHALL stop with an error that names that Workspace's site name, and leave the other databases as they are.
  - AC-008.10: THE Redis of every stack SHALL be one container with persistence on, a password from `.env`, the eviction policy `noeviction` and no published port. It SHALL hold the sessions of `identity` and no cache that may be evicted (H-Q1).
  - AC-008.11: THE Garage object store of every stack SHALL run as one container with a persistent volume, credentials from `.env`, the bucket `FILE_STORAGE_BUCKET` created at startup if absent, and no published port except through the routing of the signed URLs that `design.md` settles (H-Q4).

### STORY-009: Caddy wildcard and routing by host (KAN-95)
- Owner: Trần Trung Hiếu. Sprint S1.
- As the system, I want Caddy to route by host so that every Workspace has its own Workspace site and the platform
  hosts reach their own parts.
- Acceptance:
  - AC-009.1: THE main Caddy (`infra/caddy/Caddyfile`) SHALL serve `api.`, `admin.`, `login.` and every Workspace site over HTTPS with a valid certificate. This includes a Workspace created after the deployment, with no manual certificate step (TS-002).
  - AC-009.2: WHEN a request comes to a Workspace site, THE Caddy SHALL send `/api/*` to the backend web process and every other path to the web app.
  - AC-009.4: WHEN a request comes to `login.`, THE Caddy SHALL send the request to Keycloak (the sign-in of ADR-0003, realm `tsolve`) and to no backend process; the backend no longer serves `login.`.
  - AC-009.5: WHEN a request comes to `api.`, THE Caddy SHALL send `/api/v1/jira-app/*` and `/actuator/health` to the backend web process and answer 404 to every other path.
  - AC-009.6: THE Caddy SHALL pass the original `Host` header unchanged to the backend for every `/api/v1/**` request (TS-002).
  - AC-009.7: IF a request comes to the bare base domain or to `www.`, THEN THE Caddy SHALL answer 404 itself and pass nothing to the web app or the backend (decision Q15).
  - AC-009.8: IF a request comes over plain HTTP, THEN THE Caddy SHALL redirect it to HTTPS. Every HTTPS answer SHALL carry `Strict-Transport-Security` (KAN-13).
  - AC-009.9: WHILE the backend restarts during a deployment, THE Caddy SHALL retry the backend for a configured window before answering 502 (KAN-13).
  - AC-009.10: WHERE the environment is staging, THE Caddy SHALL put the staging Workspace sites, `admin.` and `login.` behind basic authentication or an IP allow-list, and SHALL leave `api.` open to Forge Remote, which is protected by the Forge token (decision Q11).
  - AC-009.11: WHEN a request comes to `admin.`, THE Caddy SHALL send `/api/*` to the backend web process and every other path to the web app, which serves the Platform Console for that host (HLA, 3/11; this restores AC-009.3 under a new id).

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
  - AC-010.6: THE staging environment SHALL use the hosts `*.staging.tsolve.io.vn`, `admin.staging.tsolve.io.vn`, `api.staging.tsolve.io.vn` and `login.staging.tsolve.io.vn`, through `TSOLVE_BASE_DOMAIN` = `staging.tsolve.io.vn` (decision Q11). The site name `staging` is reserved (D44 (25/10); AC-002.6, AC-013.3), so no production Workspace can own `staging.tsolve.io.vn`.
  - AC-010.7: THE staging secrets SHALL live only in the server's `.env` (mode 600, owned by the deploy user) and in GitHub secrets, never in the repository.
  - AC-010.8: THE staging stack SHALL have a memory and CPU limit and a health check on every container, and restart `unless-stopped` (KAN-12).
  - AC-010.9: THE application VPS SHALL refuse password login over SSH and allow key login only. Only ports 22, 80 and 443 SHALL be open to the internet (KAN-10).
  - AC-010.10: THE staging databases SHALL be reachable only on the private network of the stack, and each Workspace's runtime user, the catalog user and the Jobs database user SHALL each reach only their own database (KAN-11, AC-001.26, AC-001.33).
  - AC-010.11: THE staging stack SHALL run Redis as AC-008.10 says and Garage as AC-008.11 says, each with a memory and CPU limit and a health check, and the web process SHALL not start healthy while Redis is unreachable (KAN-12).
  - AC-010.12: IF anyone connects from the internet to the port of PostgreSQL, Redis or Garage of the application VPS, THEN THE server SHALL refuse the connection (NFR 6).

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
  - AC-012.8: WHEN `infra/seed/run-seed.sh --reset` runs on dev or staging, THE script SHALL drop every Workspace database, empty the catalog and the object storage (every Workspace prefix) of that environment, and then run the seed, so that every seed Workspace is created anew and all contributors run for it (user, 25/10).
  - AC-012.9: IF `run-seed.sh --reset` runs while `TSOLVE_ENVIRONMENT` is `production`, not set, or anything other than `dev` or `staging`, THEN THE script SHALL change nothing and exit with a non-zero status (GBR-24, D42).

### STORY-013: Provisioning service and ops script: create, suspend and reactivate a Workspace (KAN-240)
- Owner: Trần Trung Hiếu. Sprint S2.
- As the T-Solve operations team and the Platform Operator, we want one provisioning service that sets up a company's
  Workspace, and suspends or reactivates it, called by the Platform Console and by a hand-run script, so that every
  Workspace is created the same way whichever way it is started (GBR-24, GBR-38).
- Acceptance:
  - AC-013.1: WHEN `infra/ops/workspace.sh create --site <site name> --admin-email <email> --google-domain <domain>` runs with values that pass AC-013.2 to AC-013.5 and AC-013.21, THE script SHALL, through the backend's `ops` profile and the `WorkspaceProvisioning` service of AC-013.13:
    1. create the Workspace database and its runtime user (AC-001.26);
    2. apply the tenant changelog to it as the owner user;
    3. write the Workspace's id into `tenant_identity` (AC-001.28);
    4. record the Workspace in the catalog as Active, with the Google domain and the email in `workspace_email`;
    5. set up its sign-in in Keycloak through the provisioning port (AC-013.22) and record the Organization id it returns;
    6. create the first Admin Account by calling `FirstAdminCreator` (AC-013.14);

    and print the Workspace site address (GBR-24). After the commit, the service queues the invitation email (AC-013.17).
  - AC-013.2: IF the site name is missing or not an RFC 1123 label (AC-002.4), THEN THE script SHALL refuse with an error naming the rule and create nothing.
  - AC-013.3: IF the site name is `admin`, `login`, `api`, `www`, `grafana` or `staging`, or is the site name of another Workspace, THEN THE script SHALL refuse with an error naming the rule and create nothing (D11, D44 (25/10)).
  - AC-013.4: IF the email is missing or not a valid email address, THEN THE script SHALL refuse and create nothing.
  - AC-013.5: IF the email already belongs to another Workspace in the catalog, THEN THE script SHALL refuse before step 1, with an error that cites GBR-16, and create nothing. The Console gets `MSG155` on field `email` from the same check (AC-013.13).
  - AC-013.6: IF any step of AC-013.1 fails, THEN THE script SHALL undo the steps already done (drop the database and its user, remove the catalog rows), exit with a non-zero status and name the failing step, so that nothing is left created (GBR-24).
  - AC-013.7: WHEN `workspace.sh suspend --site <site name>` runs on an Active Workspace, THE script SHALL set it Suspended in the catalog. After the commit the backend SHALL publish the application event `WorkspaceSuspended` with its id, and the next request to its Workspace site or from its Jira site SHALL be refused (MSG150, AC-006.4).
  - AC-013.8: WHEN `workspace.sh reactivate --site <site name>` runs on a Suspended Workspace, THE script SHALL set it Active, the backend SHALL publish `WorkspaceReactivated` with its id, and the next request SHALL be served.
  - AC-013.9: IF `suspend` is run on a Suspended Workspace, `reactivate` on an Active one, or either on a site name of no Workspace, THEN THE script SHALL change nothing and exit with a message that says so.
  - AC-013.10: THE script SHALL offer no command that deletes a Workspace, changes its site name or reads data inside its database (GBR-38).
  - AC-013.11: WHEN a Workspace has been created, THE Tenant Resolver SHALL resolve its Workspace site on the next request, with no wait and no restart.
  - AC-013.12: THE script SHALL run only on the application VPS (or a developer machine), with the owner database credentials from the server's `.env`, and SHALL print no password.
  - AC-013.13: THE platform SHALL offer one Java service `WorkspaceProvisioning` with `create(siteName, adminEmail, googleDomain)`, `suspend(siteName)` and `reactivate(siteName)`, used by the backend's `ops` profile and by `platform-console`. THE script SHALL hold no provisioning logic of its own, and the rules of AC-013.2 to AC-013.11 SHALL be enforced by the service, so that the Console gets the same refusals: a `BusinessRuleException` with the field and, for an email of another Workspace, `code` `MSG155` on field `email` (H-Q5).
  - AC-013.14: THE platform SHALL declare in its public API the port `FirstAdminCreator` with `UUID createFirstAdmin(UUID workspaceId, String email)`, implemented by `identity`. WHEN provisioning reaches step 6 of AC-013.1, THE service SHALL call it inside `TenantContext.runIn(workspaceId)`, passing the Workspace id and the email in lower case and nothing else. The Account is created Active with the role Admin, and its id is returned (decision Q1, Q2, 4/11).
  - AC-013.15: IF the backend starts in the `ops` profile or in a profile that serves the Platform Console and no bean implements `FirstAdminCreator` or no bean implements `InvitationJobQueue`, THEN THE backend SHALL refuse to start and name the missing port.
  - AC-013.16: IF `createFirstAdmin` throws, THEN THE service SHALL undo as AC-013.6 says, so that no Workspace is left without its first Admin Account.
  - AC-013.17: WHEN the creation has been committed, THE service SHALL queue, through `InvitationJobQueue` (JobRunr), one invitation-email job for the new Workspace in the Jobs database, for `worker` to run, and the job SHALL carry only the Workspace id and the id of the first Admin Account. The email SHALL tell the Admin the address of the Workspace site and to sign in with Google (decision Q2, Q4).
  - AC-013.18: IF queueing the job fails, or the mail cannot be sent, THEN THE service SHALL keep the Workspace and its Admin Account, THE failure SHALL be logged without the email address, and `worker` SHALL retry the job. The MVP has no command to send the invitation again.
  - AC-013.19: IF two creations race for the same email, THEN THE unique constraint on `workspace_email` SHALL let only one claim it, and the other SHALL fail at step 4 of AC-013.1 (before step 5, so no Keycloak object is created for it) with the `MSG155` refusal and be undone as AC-013.6 says (decision Q3).
  - AC-013.20: THE Platform Console SHALL be the only caller of `WorkspaceProvisioning` over HTTP, and only on `admin.` for an allow-listed Platform Operator (AC-004.28); no other module SHALL call `create`, `suspend` or `reactivate`, except the seed through AC-012.1.
  - AC-013.21: IF the Google domain is missing or not a valid domain name, THEN THE service SHALL refuse with a `BusinessRuleException` on field `googleDomain` and create nothing; the domain is stored in lower case (ADR-0003, K2, K3).
  - AC-013.22: WHEN provisioning reaches step 5 of AC-013.1, THE service SHALL call the port `WorkspaceSignInProvisioner.provision(workspaceId, siteName, googleDomain)`, which sets up the Keycloak Organization, the Google identity provider `google-<site name>`, the domain and the client redirect URI of the Workspace site (K6), and SHALL store the Organization id it returns in `keycloak_organization_id` (AC-001.34). IF the port throws, THEN THE service SHALL undo as AC-013.23 says.
  - AC-013.23: IF any step after step 5 of AC-013.1 fails, or step 5 itself fails, THEN THE undo of AC-013.6 SHALL also call `WorkspaceSignInProvisioner.remove(workspaceId)`, so that no Keycloak object is left for a Workspace that does not exist. THE service SHALL offer a removal of a Workspace's Keycloak objects for the runbook "remove a company" through the same port, and SHALL still offer no command that deletes a Workspace (AC-013.10).
  - AC-013.24: THE service SHALL record a new Workspace as Active at step 4 of AC-013.1 and SHALL have no pending-sign-in status and no `activate` operation; the Google redirect URI and the test sign-in are steps of the runbook `docs/runbooks/onboard-company.md`, done by the platform admin before the company is told (decision Q4, revised 5/11).

### STORY-014: Operations readiness: backup, restore, monitoring, edge limits and load test (KAN-201, KAN-202, KAN-95)
- Owner: Trần Trung Hiếu. Scheduled after 15/11 (DevOps Phase B); the edge limits (AC-014.9 – AC-014.11) follow the DevOps design's step A5.
- As the operations team, we want every database and the object storage backed up and restorable, Redis and the other
  services watched, the edge limiting what one client can send, and a measured load test, so that a Workspace can be
  recovered, an outage is noticed, and the HLA's load target has evidence behind it (GBR-37; HLA 3/11 notes).
- Acceptance:
  - AC-014.1: WHEN the nightly backup runs on an environment, THE backup SHALL write one compressed dump of the catalog database, one of the Jobs database and one of each Workspace database as its own file (the list comes from the catalog), and a copy of each Workspace's object-key prefix in Garage, keep seven days of them, and send them with `restic` (encrypted and deduplicated) to a Cloudflare R2 bucket, so that no file leaves the server unencrypted (KAN-17; H-Q3, H-Q4; ADR-015).
  - AC-014.2: IF a dump or a copy fails, THEN THE backup SHALL exit non-zero, name the database or prefix that failed and raise an alert (KAN-17, KAN-20).
  - AC-014.3: THE backup SHALL store `CATALOG_SECRET_KEY` separately from the dumps, never in the same place, and SHALL not back up Redis, whose content is sessions only.
  - AC-014.4: WHEN the restore procedure `infra/RESTORE.md` is followed for the last night's dump of one production Workspace, THE restore SHALL bring that Workspace database and its object prefix up on staging with new database names, users and passwords, with its catalog rows rewritten for staging, without touching any other Workspace, and the web app SHALL work on it. THE time taken SHALL be recorded in the document (KAN-18).
  - AC-014.5: IF a restore targets staging from a production backup, THEN THE restored catalog SHALL never point at a production database, user or password.
  - AC-014.6: THE monitoring SHALL collect metrics from the backend, the worker, PostgreSQL, Redis and Garage and show them in one dashboard, including for Redis its memory use, connected clients and command rate (KAN-19, KAN-202).
  - AC-014.7: IF Redis cannot be reached by the monitoring's health check, THEN THE monitoring SHALL raise the alert "Redis unreachable" on the alert channel of KAN-20, because nobody can sign in without it.
  - AC-014.8: THE alert rules of KAN-20 SHALL also cover the Jobs database and Garage being unreachable and the failure of a nightly backup.
  - AC-014.9: THE Caddy SHALL reject a request whose body is larger than `EDGE_MAX_BODY_BYTES` with 413, and `EDGE_MAX_BODY_BYTES` SHALL be larger than `FILE_STORAGE_MAX_BYTES`, so that an allowed file always passes the edge.
  - AC-014.10: THE Caddy SHALL close a request that exceeds the read, write or idle time set by `EDGE_TIMEOUT_SECONDS`.
  - AC-014.11: IF one client IP address sends more than `EDGE_RATE_PER_IP` requests within `EDGE_RATE_WINDOW_SECONDS`, or the requests to one Workspace site (all its clients together) exceed `EDGE_RATE_PER_SITE` within it, THEN THE Caddy SHALL answer 429 with a `Retry-After` header and pass nothing to the backend. The rates are values in `.env`; Caddy runs as an image with the rate-limit module (decision Q8).
  - AC-014.12: THE repository SHALL hold a load-test script under `infra/loadtest/` for sign-in, the ticket list and a call of the Jira App path, with the thresholds in the script: 500 concurrent users, 95th percentile of the response time under 1 second, error rate under 1 % (decision Q7; Hiếu confirms the numbers).
  - AC-014.13: WHEN the load-test script runs against the staging environment, THE script SHALL exit non-zero if a threshold of AC-014.12 is missed, and the result of one run SHALL be recorded in a report under `infra/loadtest/`.

## Out of Scope
- The production environment itself (its compose file, deploy workflow and server setup) and code quality (KAN-15,
  KAN-16, KAN-21, KAN-7): after 15/11 (DevOps Phase B), outside this iteration. Backup, restore and monitoring are in STORY-014.
- The Platform Console screens and its REST endpoint for creating, suspending or reactivating a Workspace
  (`platform-console`); this module offers the `WorkspaceProvisioning` service they call. Deleting a Workspace (GBR-38).
- A command or button to send the first Admin's invitation again (decision Q4; the Operator tells the Admin by hand).
- A tenant-scoped cache or quota on Redis: it waits until the `ai` and chatbot specs ask for one.
- Rate limits per user (the edge cannot see the session; decision Q8), and proof of 10,000 concurrent users on the HLA
  target: Kubernetes and high availability are the target of the HLA, not the MVP (H-Q1); STORY-014 measures the
  staging stack against the target of AC-014.12.
- The address and routing of the signed URLs of AC-007.12 (which host Caddy serves them on) → `design.md`.
- `runInTenant`, the background job server, the handler of the first-Admin invitation job, the per-Workspace cap and not
  running a Suspended Workspace's jobs (D12, D43, GBR-38) → `worker` (Phạm Tùng Dương). This module owns the Jobs database
  `tsolve_<env>_jobs` and JobRunr's tables in it (AC-001.33) and provides the minimal `InvitationJobQueue` on JobRunr that
  only stores the invitation job (AC-013.17); a job carries only its Workspace id and record ids.
- The sign-in: UC-01, Keycloak and its realm, the Organization and Google identity provider of each Workspace,
  sessions (kept in Redis), the check that an email was added by the Admin (GBR-03), the Jira account link (UC-59), the
  Platform Operator allow-list → `identity` (ADR-0003). The Keycloak provisioning code behind the port of AC-013.22 is
  `identity`'s; the manual Google redirect-URI step and the test sign-in are the runbook
  `docs/runbooks/onboard-company.md`, not code of this module.
- The Admin Account itself (including the first Admin created through `FirstAdminCreator`), the UC-05 screen and its
  messages (MSG111, MSG155 on screen) → `identity`; this module only keeps the email list (AC-004.25, AC-004.26).
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
record WorkspaceSummary(UUID id, String siteName, WorkspaceStatus status, String googleDomain,
                        Optional<String> keycloakOrganizationId,
                        Optional<String> jiraSiteUrl, Optional<String> jiraCloudId) {}   // AC-001.34
enum WorkspaceStatus { ACTIVE, SUSPENDED }
record WorkspaceSuspended(UUID workspaceId) {}                  // published after commit (AC-013.7)
record WorkspaceReactivated(UUID workspaceId) {}

public interface ForgeTokenVerifier { VerifiedForgeCall verify(String bearerToken); }   // throws when invalid
public interface ForgeCallContext { Optional<VerifiedForgeCall> current(); }
record VerifiedForgeCall(String cloudId, Optional<String> atlassianAccountId) {}

public interface FileStorage {                                   // STORY-007, object storage (Garage)
    StoredFile store(InputStream content);
    InputStream read(String key);
    URI signedUrl(String key);                                  // AC-007.12, expires after FILE_STORAGE_URL_TTL_SECONDS
    void delete(String key);
}
record StoredFile(String key, long sizeBytes) {}

public interface WorkspaceProvisioning {                        // STORY-013; the Console and the ops profile
    WorkspaceSummary create(String siteName, String adminEmail, String googleDomain);   // AC-013.1, AC-013.21
    void suspend(String siteName);
    void reactivate(String siteName);
}
public interface WorkspaceSignInProvisioner {                   // K6; owned by identity for now (AC-013.22, AC-013.23)
    String provision(UUID workspaceId, String siteName, String googleDomain);   // returns the Keycloak Organization id
    void remove(UUID workspaceId);
}   // Open note: the port may move into platform's public API once `WorkspaceProvisioning` exists (the pattern of
    // `FirstAdminCreator`), because platform cannot depend on identity; to confirm with the platform owner.
public interface FirstAdminCreator {                            // declared here, implemented by identity (AC-013.14)
    UUID createFirstAdmin(UUID workspaceId, String email);      // called inside TenantContext.runIn(workspaceId)
}
public interface InvitationJobQueue {                           // declared here, minimal implementation here on JobRunr (AC-013.15, AC-013.17), job stored in the Jobs database
    void queueFirstAdminInvitation(UUID workspaceId, UUID accountId);   // after the commit; ids only, no email address
}

public interface DemoSeedContributor {                           // STORY-012, profile "seed"
    int order();
    void seed(SeedWorkspace workspace);   // runs inside TenantContext.runIn(workspace.id())
}
record SeedWorkspace(UUID id, String siteName, JsonNode data) {}  // data = the module's part
```
Catalog errors are `BusinessRuleException` with a field (be-foundation AC-001.3). Storage errors are
`FileTooLargeException` and `NotFoundException`, and an invalid key is refused with an exception that logs the
attempt.

**Ops script** (`infra/ops/workspace.sh`, backend profile `ops`): the hand-run form of `WorkspaceProvisioning`:
`create --site --admin-email --google-domain`, `suspend --site`, `reactivate --site` (STORY-013). The Platform Console reaches the same
service through its own REST endpoint on `admin.`, which belongs to `platform-console` (AC-004.28, AC-013.20).

**Operations** (`infra/`, STORY-014): `infra/scripts/backup-db.sh`, `infra/RESTORE.md`, the monitoring and alert
rules, `infra/loadtest/`.

**REST (host `api.` only)**

| Endpoint | Auth | Answer |
|----------|------|--------|
| `POST /api/v1/jira-app/lifecycle` | Forge Invocation Token | 204 (AC-006.12, AC-006.15, AC-006.16) |
| every other `/api/v1/jira-app/**` path | Forge Invocation Token, then the Workspace of the `cloudId` | the handler of its module; 401 without `code`, 403 `MSG152`, 403 for a Suspended Workspace (AC-006.4) |
| `/actuator/health` | none | health |

**Web resolver answers** (every host that is not `login.` (Keycloak), `api.` or `admin.`; before Spring Security):
404 `MSG151`, 403 `MSG150`, as `application/problem+json`.

**Schema:** catalog (Registry) tables `tenant_workspace` (with `schema_version`, `google_domain` and
`keycloak_organization_id`, AC-001.34) and `workspace_email`; in each Workspace database the one-row table
`tenant_identity` and a row policy on every tenant table (AC-001.28, AC-001.29); the tenant-table convention
(AC-001.20), its allow-list (AC-001.21) and the guard test (AC-001.22, AC-001.31). The Jobs database holds JobRunr's
tables only (AC-001.33).

**Configuration** (names in `.env.example`, no real values):
- `TSOLVE_BASE_DOMAIN`: `tsolve.io.vn`, `staging.tsolve.io.vn` or `localhost`.
- `TSOLVE_ENVIRONMENT`: `dev`, `staging` or `production`.
- `CATALOG_DB_URL`, `CATALOG_DB_USER`, `CATALOG_DB_PASSWORD`; `TENANT_DB_HOST`; `TENANT_DB_POOL_MAX` (default 5).
- `JOBS_DB_URL`, `JOBS_DB_USER`, `JOBS_DB_PASSWORD` (the Jobs database: the web, `ops` and `seed` processes of this module store the invitation job in it through `InvitationJobQueue`; `worker` runs jobs from it).
- `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD` (owner user, migrations and the ops script).
- `CATALOG_SECRET_KEY` (encrypts the Workspace database passwords).
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` (sessions; used by `identity`).
- `FORGE_APP_ID`, `FORGE_JWKS_URL`.
- `FILE_STORAGE_ENDPOINT`, `FILE_STORAGE_ACCESS_KEY`, `FILE_STORAGE_SECRET_KEY`, `FILE_STORAGE_BUCKET`,
  `FILE_STORAGE_URL_TTL_SECONDS`, `FILE_STORAGE_MAX_BYTES` (default 10485760) and `FILE_STORAGE_PUBLIC_URL_PATTERN` (the
  public address pattern the signed URLs are signed for; required in every environment). `FILE_STORAGE_ROOT` is removed.
- `EDGE_MAX_BODY_BYTES`, `EDGE_TIMEOUT_SECONDS`, `EDGE_RATE_PER_IP`, `EDGE_RATE_PER_SITE`, `EDGE_RATE_WINDOW_SECONDS`
  (Caddy, STORY-014).
- `OLLAMA_BASE_URL`: read by `ai`.
- `CLOUDFLARE_API_TOKEN` (DNS challenge), `STAGING_BASIC_AUTH_HASH` (staging basic authentication): edge Caddy, server `.env` only.
- `RESTIC_PASSWORD`, `BACKUP_R2_ENDPOINT`, `BACKUP_R2_BUCKET`, `BACKUP_R2_ACCESS_KEY`, `BACKUP_R2_SECRET_KEY`: the nightly
  backup to Cloudflare R2 (STORY-014).
- `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`, `GRAFANA_BASIC_AUTH_HASH`, `GRAFANA_ALERT_WEBHOOK_URL`: monitoring
  and the alert channel (STORY-014).

**Deployment:**
- Hosts: `<site name>.`, `admin.`, `login.` and `api.`, under the base domain.
- Images on GHCR: `tsolve-backend` (also the worker, `worker` profile, and the ops script, `ops` profile) and
  `tsolve-frontend`. Caddy runs as an image with the rate-limit module.
- Compose and Caddy files under `infra/`, plus `docker-compose.yml` for local use. Services: PostgreSQL 16 with
  pgvector, Redis (one container, persistence), Garage, backend, worker, web app, Caddy, and Ollama (local only).
  Docker Compose is the deployment of the MVP and the demo; the HLA's Kubernetes deployment is the target (H-Q1).

### Required from other modules
- `be-foundation` (Trần Hải Anh):
  - the `ProblemDetail` format and `GlobalExceptionHandler` (MSG06 on an unhandled exception);
  - `NotFoundException` and `BusinessRuleException` with a field;
  - the tenant changelog `db.changelog-master.xml` as the one entry point of module changesets;
  - its session security chain and its CSRF rule (AC-002.11) must not apply to `api.`, which uses this module's
    chain (AC-005.6).
- Owner of changeset 001 (`be-foundation`): the existing `workspace` table moves out of the tenant changelog (the
  company information stays in the Workspace database, owned by `workspace`; the catalog row is this module's), and
  `audit_log_entry` meets AC-001.20 as an append-only table.
- `identity`:
  - calls `TenantContext.runIn` with the Workspace of the host on the sign-in callback of a Workspace site;
  - owns, for now, the port `WorkspaceSignInProvisioner` (Keycloak Organization, Google identity provider, redirect URI) that
    `WorkspaceProvisioning` calls at creation and undo (AC-013.22, AC-013.23); a no-op bean until the Keycloak
    implementation exists;
  - takes the ticket tracker account only from `ForgeCallContext`;
  - calls `WorkspaceCatalog.claimEmail` when an Account is added (UC-05);
  - contributes the Accounts with the five Roles to the seed;
  - implements `FirstAdminCreator` (AC-013.14): creates the first Admin Account Active with the role Admin inside the
    Workspace's own database and returns its id; the first-Admin invitation email is sent by `worker` (AC-013.17);
  - reads the Workspace's Google domain and Keycloak Organization id from `WorkspaceSummary` (AC-001.34).
- `platform-console` (the Console on `admin.`): exposes the REST endpoint that creates, suspends and
  reactivates a Workspace, only for an allow-listed Platform Operator, and calls `WorkspaceProvisioning`
  (AC-004.28, AC-013.20).
- `workspace` (Đặng Tấn Bình): calls `WorkspaceCatalog.linkJiraSite` when the Admin enters the Jira site at UC-10.
- `worker` (Phạm Tùng Dương):
  - `runInTenant` built on `TenantContext.runIn`;
  - does not run a Suspended Workspace's jobs, from the catalog status and the events `WorkspaceSuspended` /
    `WorkspaceReactivated` (GBR-38);
  - runs JOB-02 once per Active Workspace (GBR-37);
  - starts the JobRunr background job server on the Jobs database `tsolve_<env>_jobs` (the `worker` profile sets
    `jobrunr.background-job-server.enabled: true`; `platform` switches it off in its own processes), supplies the handler of
    the first-Admin invitation job in place of the placeholder in the web image, and never runs Liquibase; the database and
    JobRunr's tables in it (AC-001.33) belong to `platform`;
  - sends the invitation email of AC-013.17.
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
- [ ] `workspace.sh create --google-domain …` then `suspend` then `reactivate` runs on staging (STORY-013)
- [ ] A restore of one Workspace into staging is done once and timed, and one load-test run against staging is
      recorded (STORY-014)
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
