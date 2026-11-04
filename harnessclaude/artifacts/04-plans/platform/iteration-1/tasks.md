---
module: platform
date: 2026-11-04
total_tasks: 22
owner: Dev
iteration: 1
---

# Tasks: platform — iteration 1 (local stack, Workspaces and the staging demo of 15/11; DevOps Phase A)

Spec: `artifacts/03-modules/platform/spec.md` · Design: `artifacts/04-plans/platform/iteration-1/design.md`
(master: `artifacts/04-plans/platform/design.md`).

Rewritten on 4/11 for the multi-tenant HLA (database per Workspace, Registry, Jobs database, row guard, Garage, Redis,
provisioning service; KAN-98). Task numbers are shared with iteration 2 (Tasks 23–26 are there). Backend tests name the
criterion: `…_AC_002_13`; infrastructure checks are scripts in `infra/tests/*.test.sh` whose log lines start with the AC
id. "Edge #n" refers to the Edge Cases table of the design.

**Size rule:** each task fits one QA card and one developer card, about 8 production files and 12 planned tests at most;
a bigger one is split here.

**Order rule (feedback first):** a task that a person can see or try comes before plumbing. Each task names "After this
task you can …"; a plumbing task names the visible task it serves. The sequence: the local data plane (1), CI (2), the
Registry and the row guard and routing (3–6), the first Workspace by script (7–9, with the invitation queue as 8), a Workspace site in the browser
(10–11), the demo Workspaces (12), staging (13–15), files and the Jira App (16–21), the AI VPS (22).

**Other modules (blockers, not tasks of this module):**
- `identity` (KAN-247) implements `FirstAdminCreator`. `InvitationJobQueue` has a minimal implementation in `platform`
  on JobRunr in the Jobs database (user decision, 4/11; `worker` takes the job handler over later): it is Task 8 here.
  Task 7 declares both ports and tests with doubles in test sources only. Tasks 9, 12 and 15 start the real backend in
  the `ops` / `seed` profile, which refuses to start without both beans (AC-013.15), so they wait for Task 8 and for
  `identity` KAN-247.
- `docker-compose.yml` and `.env.example` are edited by Tasks 1, 8, 10, 11, 14, 16 and 19 here (Tasks 8, 14 and 19 only add names to `.env.example`) and by `identity` Task 10 (Redis
  host, session variables): rebase on each other's branch and do not overwrite another module's variable.
- The Spring Boot bump to 3.5.x (DevOps A2) is not a task of this module.

---

## Task 1: Local data plane: PostgreSQL roles and databases, Redis, Garage

**Story:** STORY-008, STORY-001
**Feature:** local-data-plane
**Assignee:** Trần Trung Hiếu (KAN-94)
**After this task you can:** run `docker compose up db redis garage garage-init`, see all four healthy, open `psql` and
list the Registry, Jobs and template databases, and see the private bucket in Garage.
**Covers:** AC-008.10, AC-008.11, AC-008.6 (data-plane part), AC-001.32 (the Registry and Jobs users are neither
superuser nor `BYPASSRLS`), AC-001.33 (Jobs database, its user connects only to it), AC-007.15 (private bucket).
**Design:** Roles and grants; Redis; Infrastructure (Garage); Configuration.
**Description:**
- `infra/postgres/init/10-tsolve-roles.sh`: the owner role, the Registry and Jobs databases with their runtime users
  (`CONNECT` on their own database only), the template database `tsolve_template` with `vector` installed (decision R6).
  Replaces `TSOLVE_DATABASES` and the per-database app passwords of the pool model.
- `docker-compose.yml`: `db` (pgvector:pg16, `max_connections = 300`, init mounted), `redis` (pinned, `appendonly yes`,
  `requirepass`, `noeviction`, no published port, health check), `garage` (pinned, single node, volumes, no published
  port) and the one-shot `garage-init` (`infra/scripts/garage-init.sh`: layout, private bucket `FILE_STORAGE_BUCKET`, one
  access key limited to that bucket; exits non-zero on failure). Removes `FILE_STORAGE_ROOT`, `TSOLVE_DATABASES`,
  `DB_NAME`, `DB_USER`, `DB_PASSWORD` from the files.
- `.env.example` lists the new variable names with no value; `infra/README.md` (new) has the start command and the
  variable table of this task.
**Edge rows (test targets):** #46 (bucket init fails → `garage-init` exits non-zero, backend and worker do not start;
checked in Task 11).
**Tests (`infra/tests/data-plane.test.sh`, about 8 checks):** the three databases exist and the template has `vector`;
Registry and Jobs users cannot connect to each other's database and are not superuser or `BYPASSRLS` (AC-001.32,
AC-001.33); Redis refuses a client without the password and does not evict (AC-008.10); no port of `db`, `redis`,
`garage` is published; the bucket exists and an unsigned read gets 403 (AC-007.15); `garage-init` run twice changes
nothing; `.env.example` holds no value (AC-008.6).
**Files:**
- Create: `infra/postgres/init/10-tsolve-roles.sh`, `infra/scripts/garage-init.sh`, `infra/garage/garage.toml`
- Modify: `docker-compose.yml`, `.env.example`
- Create: `infra/README.md`
- Test: `infra/tests/data-plane.test.sh`
**Dependencies:** none (coordinate `docker-compose.yml` / `.env.example` with `identity` Task 10)
**Estimated size:** medium
**Done when:** `data-plane.test.sh` passes on a clean volume and a developer can do the walkthrough above from
`infra/README.md` alone.

---

## Task 2: CI for the backend and images on GHCR

**Story:** STORY-010
**Feature:** ci-images
**Assignee:** Trần Trung Hiếu (KAN-6, KAN-8, KAN-9)
**After this task you can:** open a pull request and see the `backend` check (`mvn -B verify`) beside the frontend one;
merge to `main` and find `tsolve-backend` and `tsolve-frontend` in GHCR tagged `sha-<commit>` and `staging`.
**Covers:** AC-010.1, AC-010.2.
**Design:** Infrastructure — Images; Key Decisions (P9).
**Description:** `backend` job in `ci.yml` (Temurin 21, Maven cache, `mvn -B verify` with Testcontainers; path filter
`backend/**`, `infra/postgres/**`). `backend/Dockerfile` + `.dockerignore` (Maven build stage, Java 21 JRE, non-root,
`HEALTHCHECK` on `/actuator/health`, `-XX:MaxRAMPercentage=75`). `images.yml` on `main`: build and push
`tsolve-backend` and `tsolve-frontend` (the `tsolve-caddy` job is added by Task 14) to `ghcr.io/<owner>`. Manual after the
merge (Hiếu): mark frontend, backend, PR title and gitleaks as required checks.
**Tests:** a pull request run shows the check; the image starts and answers `/actuator/health` (smoke step in
`images.yml`); a red backend test blocks the merge (manual check, recorded in the PR).
**Files:**
- Modify: `.github/workflows/ci.yml`
- Create: `.github/workflows/images.yml`
- Create: `backend/Dockerfile`, `backend/.dockerignore`
**Dependencies:** none
**Estimated size:** small
**Done when:** the PR of this task shows the backend check green and, after the merge, both images are in GHCR.

---

## Task 3: Registry database: changelog, password cipher, `registryDataSource`

**Story:** STORY-001
**Feature:** registry-db
**Assignee:** Trần Trung Hiếu (KAN-98)
**After this task you can:** start the backend against the compose stack of Task 1 and see `workspaces` and
`workspace_emails` created, empty, in `tsolve_<env>_registry` (`psql \dt`).
**Covers:** AC-001.15, AC-001.16, AC-001.17, AC-001.18 (Registry changelog), AC-001.19, AC-001.24, AC-001.25,
AC-001.27 (Registry part), AC-001.33 (the Registry changelog creates nothing in the Jobs database), AC-008.4 (the
Registry changelog applied at startup, the worker never runs Liquibase).
**Design:** Data Model — Tenant Registry DB; Key Decisions R1, R8; Configuration.
**Description:** `catalog/catalog-master.xml`, `C001-registry-workspaces.sql`, `C002-registry-workspace-emails.sql`;
`PasswordCipher` (AES-256-GCM, 12-byte nonce, Workspace id as associated data, `v1:` prefix, key `CATALOG_SECRET_KEY`);
`RegistryDataSourceConfig` (`registryDataSource`, `registryTransactionManager`, `JdbcClient`, Liquibase for the catalog
changelog as the owner user at startup of the web process); `RegistryRepository` (insert, find, update status); a
`PostgresTestBase` in test sources (Testcontainers `pgvector/pgvector:pg16` with `infra/postgres/init`, owner and
runtime users). Adds `spring-boot-starter-jdbc` and the Testcontainers artifacts to `backend/pom.xml` if missing.
**Edge rows:** #13 (key missing → startup refuses; wrong key → only that Workspace fails), #14 (no password in any log
line; the log-capture test is finished in Task 6).
**Tests (about 12):** `PasswordCipherTest`: round trip, a ciphertext copied to another row does not decrypt, wrong key,
missing key at startup names the variable (AC-001.24, AC-001.25, Edge #13); `RegistryChangelogIT`: tables and columns,
`jira_cloud_id` and `db_name` unique, email stored lower-case only, both tables empty (AC-001.19), the Jobs database
has no table from the Registry changelog (AC-001.33), the Registry holds no table other than the allowed set
(AC-001.17).
**Files:**
- Create: `backend/src/main/resources/db/changelog/catalog/catalog-master.xml`, `C001-registry-workspaces.sql`,
  `C002-registry-workspace-emails.sql`
- Create: `backend/src/main/java/vn/tsolve/platform/registry/PasswordCipher.java`, `RegistryDataSourceConfig.java`,
  `RegistryRepository.java`
- Modify: `backend/pom.xml` (only if missing)
- Test: `backend/src/test/java/vn/tsolve/support/PostgresTestBase.java`
- Test: `backend/src/test/java/vn/tsolve/platform/PasswordCipherTest.java`, `RegistryChangelogIT.java`
**Dependencies:** Task 1 (init script)
**Estimated size:** medium
**Done when:** both test classes pass in `mvn verify` and the walkthrough above shows the two tables.

---

## Task 4: `WorkspaceCatalog`: find a Workspace, keep its emails

**Story:** STORY-004
**Feature:** workspace-catalog
**Assignee:** Trần Trung Hiếu (KAN-98)
**After this task you can:** (plumbing, serves Tasks 7, 10 and 21 and the `identity` sign-in) see the lookups and the
email rule in `WorkspaceCatalogIT`: one email in one Workspace, whatever the case.
**Covers:** AC-004.17, AC-004.18, AC-004.25, AC-004.26, AC-004.27, AC-004.28 (no endpoint: asserted with Task 7's source scan).
**Design:** Registry rules; Interface Contracts.
**Description:** `WorkspaceCatalog` (`findById`, `findBySiteName` without case, `findByCloudId`, `claimEmail`; no connection
data returned), `WorkspaceCatalogImpl` over `RegistryRepository` (`INSERT … ON CONFLICT (email) DO NOTHING`, then read
the row), `WorkspaceSummary`, `WorkspaceStatus`. `linkJiraSite` is Task 18.
**Edge rows:** #26 (same email in another case or with spaces in two Workspaces at once → one row, the other `MSG155`).
**Tests (about 10):** each lookup finds and returns empty for an unknown key; the summary holds no password or database
name (AC-004.18); claim: free email recorded lower-case, same Workspace succeeds with no change, another Workspace
→ `MSG155` on `email` and nothing recorded (AC-004.26); concurrent claim (Edge #26); `WorkspaceCatalog` never binds a Workspace
(AC-004.27).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/service/WorkspaceCatalog.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/impl/WorkspaceCatalogImpl.java`
- Create: `backend/src/main/java/vn/tsolve/platform/dto/WorkspaceSummary.java`, `WorkspaceStatus.java`
- Test: `backend/src/test/java/vn/tsolve/platform/WorkspaceCatalogImplTest.java`, `WorkspaceCatalogIT.java`
**Dependencies:** Task 3; `be-foundation` Task 1 (`BusinessRuleException` with a code and a field)
**Estimated size:** small
**Done when:** `WorkspaceCatalogIT` passes with the race case.

---

## Task 5: Tenant changelog: `tenant_identity`, the row guard and the convention guard test

**Story:** STORY-001, STORY-003
**Feature:** row-guard
**Assignee:** Trần Trung Hiếu (KAN-98)
**After this task you can:** apply the tenant changelog to an empty database and see, with `\d+` and `pg_policies`, that every
table has the policy `tenant_guard` and that a runtime user sees no row until `app.tenant_id` equals `tenant_identity`.
**Covers:** AC-001.18 (tenant changelog), AC-001.20, AC-001.21, AC-001.22, AC-001.23, AC-001.27 (tenant part),
AC-001.28 (the table; Task 7 writes the row), AC-001.29, AC-001.30, AC-001.31, AC-001.32 (guard test side),
AC-003.10 (no policy that compares a `workspace_id` column).
**Design:** The row guard; Data Model — Workspace DB (changesets), Roles and grants, Convention guard test data;
Key Decisions R4, R5, R6.
**Description:** the three platform changesets in the tenant changelog: `tenant_identity` (singleton primary key),
`tenant_guard_ok()`, and the last-run policy installer (`runAlways`, `runOrder="last"`; if the Liquibase version
cannot, the same SQL runs as the last step of `TenantMigrator` — Task 7; Decided in planning). `ConventionGuardIT`
with `ALLOW_LIST` and the fixed `LEGACY_ALLOW_LIST`; `RowGuardIT` with a runtime user (neither owner nor
`BYPASSRLS`). The test-only table `isolation_probes` is created by the tests, not the changelog.
**Edge rows (Critical/High):** #6 (runtime user cannot disable RLS, drop the policy or write `tenant_identity`),
#7 (role attributes), #8 (missing, renamed or second policy), #9 (empty setting is "unset"), #10 (no row or a second row
in `tenant_identity`), #12 (the allow-list only shrinks). #3 and #5 are in Task 6.
**Tests (about 12):** guard fails for a scratch table with no policy, a policy named otherwise, another condition, a
second policy (AC-001.31, Edge #8); a table breaking AC-001.20 is named (AC-001.22); allow-list rule (Edge #12);
`id` defaulted by SQL and by JPA (AC-001.23); runtime user: no rows and refused writes when the setting is unset, empty
or another Workspace's, rows when it matches (AC-001.30, Edge #9); `tenant_identity` empty → no rows (Edge #10);
refusals in Edge #6; superuser / `BYPASSRLS` / owner check (Edge #7, AC-001.32); no policy compares `workspace_id`
(AC-003.10).
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/NNN-platform-tenant-identity.sql`,
  `NNN-platform-tenant-guard-function.sql`, `NNN-platform-tenant-guard-policies.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml` (the changeset owner is `be-foundation`, P1)
- Test: `backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java`, `RowGuardIT.java`
**Dependencies:** Tasks 1 and 3 (init script, `PostgresTestBase`)
**Estimated size:** medium
**Done when:** both ITs pass and `isolation_probes` appears nowhere in `src/main`.

---

## Task 6: Tenant routing: `TenantContext`, one pool per Workspace, the tenant setting

**Story:** STORY-002
**Feature:** tenant-routing
**Assignee:** Trần Trung Hiếu (KAN-99)
**After this task you can:** (plumbing, serves Tasks 7–10) run `TenantRoutingIT` and see one JPA query go to Workspace A's
database and another to B's, and a misrouted connection read nothing.
**Covers:** AC-002.1, AC-002.11 (holder side), AC-002.12 – AC-002.16, AC-002.20 – AC-002.25, AC-001.25 (the pool of
a Workspace with a bad key is refused).
**Design:** The binding; Database routing; Key Decisions R2, R3, R15.
**Description:** `TenantContext`, `ThreadLocalTenantContext` (package-private `bind`/`unbind`; `runIn` with
`TransactionTemplate(PROPAGATION_REQUIRES_NEW)`, restore in `finally`; the existence check reads the Registry),
`TenantBinding`, `NoTenantException`, `TenantRoutingDataSource` (primary `DataSource`; throws before opening a
connection when none is bound; connection checkout outside a transaction sets and resets `app.tenant_id`),
`TenantPoolRegistry` (lazy Hikari pools, `TENANT_DB_POOL_MAX` default 5, `minimumIdle 0`), `TenantAwareJpaTransactionManager`
(`set_config('app.tenant_id', ?, true)` in `doBegin`), `TenantDataSourceConfig` (JPA properties of R15:
`allow_jdbc_metadata_access=false`, explicit dialect, `open-in-view=false`).
**Edge rows (Critical/High):** #2 (binding gone after an exception on a one-thread pool), #3 (pooled connection of a
finished transaction shows no row to a request with no Workspace), #4 (`runIn(B)` while A is bound throws), #5 (injected
misroute reads and writes nothing), #13 (wrong `CATALOG_SECRET_KEY`: only that Workspace fails, others work), #14 (no
password in any log line, log capture), #15 (25 Workspaces open pools below `max_connections`), #17 (one Workspace
database down, Registry down), #23 (Hibernate starts with nothing bound).
**Tests (about 12):** `ThreadLocalTenantContextTest`: different Workspace refused, unknown id refused, same allowed,
restore after an exception, Suspended allowed (AC-002.12 – .15), no Workspace → `requireWorkspaceId` throws
with the call site logged (AC-002.16); `TenantRoutingIT`: two Workspaces on two databases, no Workspace → throws before a
connection (AC-002.21), pool size and lazy open, two first requests open one pool, transaction stays on its database
(AC-002.23), Edge #3, #5, #13, #14, #15, #17, #23.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/tenant/TenantContext.java`, `ThreadLocalTenantContext.java`,
  `TenantBinding.java`, `NoTenantException.java`, `TenantRoutingDataSource.java`, `TenantPoolRegistry.java`,
  `TenantAwareJpaTransactionManager.java`, `TenantDataSourceConfig.java`
- Test: `backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java`, `TenantRoutingIT.java`
**Dependencies:** Tasks 3, 4, 5
**Estimated size:** medium
**Done when:** `TenantRoutingIT` passes with a pool of one connection and with two databases.

---

## Task 7: Provisioning service: create a Workspace in one all-or-nothing saga

**Story:** STORY-013, STORY-001, STORY-004
**Feature:** provisioning-core
**Assignee:** Trần Trung Hiếu (KAN-240)
**After this task you can:** (plumbing, serves Task 9) call `WorkspaceProvisioning.create` in `WorkspaceProvisioningIT` and see
a new database, its runtime user, the applied changelog, `tenant_identity`, the Registry rows and the first Admin
Account; and see a forced failure leave nothing behind.
**Covers:** AC-013.1 (steps 1–5 and the print of the address, via the service), AC-013.2 – AC-013.6, AC-013.11 (resolution is
tested again in Task 10), AC-013.13, AC-013.14, AC-013.15 (the check; the real queue is Task 8), AC-013.16, AC-013.17 and AC-013.18 (with a double port; the real queue is Task 8),
AC-013.19, AC-013.20, AC-001.26 (the Workspace runtime user reaches only its database), AC-001.28 (the row is written, then writes revoked),
AC-001.32 (Workspace users), AC-004.28 (no REST endpoint creates a Workspace: source scan).
**Design:** Provisioning; Roles and grants; Key Decisions R9, R10, R6.
**Description:** `WorkspaceProvisioning` (interface), `WorkspaceProvisioningService` (`create` only; steps and compensation of the
design), `TenantDatabaseProvisioner` (role, `CREATE DATABASE … TEMPLATE tsolve_template`, grants, drop `WITH (FORCE)`),
`TenantMigrator` (owner user; `apply(workspace)`, `applyAll()` that stops and names the first failing Workspace; reads
the last changeset id into `schema_version`), `FirstAdminCreator` and `InvitationJobQueue` (ports, no implementation
here), `WorkspaceRuleException` (extends `BusinessRuleException`). A start-up check refuses the `ops` and Console profiles
without a bean for each port (AC-013.15).
**Edge rows (Critical/High):** #44 (changelog fails on one Workspace → stops, names the site name), #57 (any step or
`createFirstAdmin` fails → compensation, nothing visible), #58 (two creations for the same email or site name),
#62 (no password or email in logs or the Jobs database), #64 (no port bean → refuses to start). Medium: #60, #61.
**Tests (about 12; ports are doubles in test sources):** happy path (every step, `schema_version` set, the pool of the
new Workspace opens); each validation (label, reserved names including `grafana`, taken name, invalid email, email of
another Workspace before step 1); compensation after a failure at each of steps 2–6 and the failing step is named;
`createFirstAdmin` receives the lower-case email inside `runIn`; race for one email → one `MSG155` (AC-013.19);
invitation queued once after the commit with ids only, a queue failure keeps the Workspace (AC-013.17, AC-013.18);
log capture (Edge #62); a source scan fails when a class outside the Console, `ops` and `seed` calls the service
(AC-013.20, AC-004.28).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/service/WorkspaceProvisioning.java`, `FirstAdminCreator.java`,
  `InvitationJobQueue.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/impl/WorkspaceProvisioningService.java`
- Create: `backend/src/main/java/vn/tsolve/platform/tenantdb/TenantDatabaseProvisioner.java`, `TenantMigrator.java`
- Create: `backend/src/main/java/vn/tsolve/platform/exception/WorkspaceRuleException.java`
- Test: `backend/src/test/java/vn/tsolve/platform/WorkspaceProvisioningIT.java`, `ProvisioningSourceScanTest.java`
**Dependencies:** Tasks 3, 4, 5, 6; `be-foundation` Task 1. The real `FirstAdminCreator` (`identity`, KAN-247) and
`InvitationJobQueue` (`platform`, JobRunr; Task 8) are needed from Task 9 on.
**Estimated size:** medium
**Done when:** `WorkspaceProvisioningIT` passes including the compensation at every step and the race.

---

## Task 8: Invitation job queue: the minimal `InvitationJobQueue` on JobRunr in the Jobs database

**Story:** STORY-013, STORY-001
**Feature:** invitation-job-queue
**Assignee:** Trần Trung Hiếu (KAN-240; Jira sub-task to be created after this plan is reviewed)
**After this task you can:** (plumbing, serves Tasks 9, 12 and 15) call `WorkspaceProvisioning.create` in
`InvitationJobQueueIT` and see, in `psql` on `tsolve_<env>_jobs`, one waiting JobRunr job that holds only the Workspace id
and the Account id (no email), while the Registry database holds no JobRunr table.
**Covers:** AC-013.15 (the `InvitationJobQueue` bean exists, so the `ops` and Console profiles can start with it),
AC-013.17 (one job stored in the Jobs database after the commit, ids only; the email itself is `worker`'s), AC-013.18
(a failed enqueue is logged without the email and does not undo the Workspace; the retry and the send are `worker`'s),
AC-001.33 (JobRunr's tables appear in the Jobs database only; the web process connects to it as the Jobs user),
AC-001.32 (the Jobs user is neither superuser nor `BYPASSRLS`, re-checked through the real connection).
**Design:** Provisioning (step 7); Roles and grants (the Jobs user); Key Decisions R10, H-Q3; Configuration (`JOBS_DB_*`).
**Description:** `JobRunrInvitationJobQueue` implements `InvitationJobQueue`: one `enqueue` of a `FirstAdminInvitationJobRequest`
(record of the two ids; the job id is derived from the Workspace id, so a second call for the same Workspace stores no second
job), failures wrapped in `InvitationQueueException` without any email. `JobsDataSourceConfig`: `jobsDataSource` from
`JOBS_DB_URL`, `JOBS_DB_USER`, `JOBS_DB_PASSWORD` (its own small pool, separate from the Registry and Workspace pools) and
a JobRunr storage provider on it; the background job server is switched **off** in the web, `ops` and `seed` processes
(`jobrunr.background-job-server.enabled: false` in `application.yml`; this task only stores jobs). A JobRunr `JobRequest`
names its handler class in `getJobRequestHandler()`, so a handler class must exist in the web image: `FirstAdminInvitationJobHandler`
is a no-op placeholder that logs only the two ids (never an email). `worker` replaces it later and **its profile must set
`jobrunr.background-job-server.enabled: true`** (written in `worker/module.md`, Responsibilities). Assumption: JobRunr's
documentation was not available while planning; the builder confirms from the JobRunr documentation that enqueueing needs the
handler class on the classpath of the enqueuing process and that no handler runs while the server is off; a difference is a
note in the task's session log, not a silent change.
`jobrunr-spring-boot-3-starter` is added to `pom.xml` (named in the master design, External Dependencies, and in ADR-0002; the
user confirms it in the review of this plan). JobRunr creates its own tables with the Jobs user (design, Roles and grants); nothing is added to any changelog.
**Edge rows (test targets):** #62 (the Jobs database holds ids only), #64 (the bean now exists: the start-up check of Task 7
passes with it), #70 (the Jobs database down at enqueue), #71 (a second enqueue for the same Workspace).
**Tests (about 8):** `JobRunrInvitationJobQueueTest` (the request carries two ids and no other field; the same Workspace twice
→ one job; a storage failure → `InvitationQueueException` with no email in the message); `InvitationJobQueueIT` (Testcontainers
`PostgresTestBase`: JobRunr tables exist in the Jobs database and in no other; the stored job payload holds no email, AC-013.17,
Edge #62; the job stays waiting, no handler runs in the web process; with the Jobs database stopped, `create` still keeps the
Workspace and logs a line without the email, AC-013.18; `create` with the real queue and a double `FirstAdminCreator`
queues exactly one job after the commit; the Jobs user is neither superuser nor `BYPASSRLS`, AC-001.32).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/service/impl/JobRunrInvitationJobQueue.java`
- Create: `backend/src/main/java/vn/tsolve/platform/dto/FirstAdminInvitationJobRequest.java`
- Create: `backend/src/main/java/vn/tsolve/platform/exception/InvitationQueueException.java`
- Create: `backend/src/main/java/vn/tsolve/platform/config/JobsDataSourceConfig.java`
- Create: `backend/src/main/java/vn/tsolve/platform/job/FirstAdminInvitationJobHandler.java` (placeholder, see Description)
- Modify: `backend/pom.xml`, `backend/src/main/resources/application.yml` (`jobrunr.background-job-server.enabled: false`),
  `.env.example` (`JOBS_DB_URL`, `JOBS_DB_USER`, `JOBS_DB_PASSWORD` if missing)
- Test: `backend/src/test/java/vn/tsolve/platform/JobRunrInvitationJobQueueTest.java`, `InvitationJobQueueIT.java`
**Dependencies:** Tasks 1 (the Jobs database and user), 3 (`PostgresTestBase`), 7 (the port and the service). `worker` later
replaces the placeholder handler and starts the background job server; it does not block this task.
**Estimated size:** small
**Done when:** both test classes pass and the walkthrough above shows the one job with ids only.

---

## Task 9: Ops script: create, suspend and reactivate a Workspace by hand

**Story:** STORY-013
**Feature:** ops-script
**Assignee:** Trần Trung Hiếu (KAN-240)
**After this task you can:** run `infra/ops/workspace.sh create --site acme --admin-email admin@acme.test` on the local stack,
see the address printed and the database and the Registry row in `psql`; suspend and reactivate it.
**Covers:** AC-013.1 (the script and its printed address), AC-013.2 – AC-013.4 (script messages), AC-013.7, AC-013.8,
AC-013.9, AC-013.10, AC-013.11 (the next request after a create, with Task 10), AC-013.12, AC-013.13 (the script holds no logic).
**Design:** Provisioning; Security chains; Key Decisions R9.
**Description:** `OpsRunner` (profile `ops`, `web-application-type: none`, parses `create`/`suspend`/`reactivate` and the
two options, prints the address or the refusal, exit codes), `application-ops.yml`, `infra/ops/workspace.sh` (starts
the backend image's `ops` profile with the arguments; owner credentials from `.env`; prints no password), `suspend` and
`reactivate` in `WorkspaceProvisioningService` and the events `WorkspaceSuspended`, `WorkspaceReactivated` published after
the commit; the runbook paragraph for orphan databases (Edge #59) in `infra/README.md`.
**Edge rows:** #59 (runbook: lists databases not in the Registry), #62 (the script prints no password), #63 (`suspend`
twice, `reactivate` an Active one, an unknown site name → message, no second event).
**Tests (about 10):** `OpsRunnerIT`: create prints the address and exit 0; each refusal exit non-zero with the rule
named; no command deletes, renames or reads tenant data (AC-013.10); suspend and reactivate publish one event each
after the commit, none on a no-op (AC-013.9, Edge #63); output and logs hold no password (Edge #62). `infra/tests/ops-script.test.sh`
on the local stack: create → resolves → suspend → refused → reactivate → served (needs Tasks 10 and 11).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/seed/OpsRunner.java`
- Create: `backend/src/main/java/vn/tsolve/platform/dto/WorkspaceSuspended.java`, `WorkspaceReactivated.java`
- Modify: `backend/src/main/java/vn/tsolve/platform/service/impl/WorkspaceProvisioningService.java`
- Create: `backend/src/main/resources/application-ops.yml`, `infra/ops/workspace.sh`
- Modify: `infra/README.md`
- Test: `backend/src/test/java/vn/tsolve/platform/OpsRunnerIT.java`, `infra/tests/ops-script.test.sh`
**Dependencies:** Tasks 1, 2 (the stack and the backend Dockerfile that `workspace.sh` runs), 7, 8; `identity` KAN-247 (the real `FirstAdminCreator`). `infra/tests/ops-script.test.sh` needs Tasks 10 and 11 and runs once they
exist; they are not build dependencies of this task (Task 11 depends on Task 9)
**Estimated size:** medium
**Done when:** the walkthrough above works on the local stack and `OpsRunnerIT` passes.

---

## Task 10: Tenant Resolver of the web app and the isolation test

**Story:** STORY-002, STORY-005
**Feature:** web-tenant-resolver
**Assignee:** Trần Trung Hiếu (KAN-99)
**After this task you can:** run the backend and `curl -H 'Host: acme.localhost' localhost:8080/api/v1/me` → 401 `MSG07` for a
real Workspace, 404 `MSG151` for an unknown one, 403 `MSG150` for a Suspended one.
**Covers:** AC-002.2 – AC-002.10, AC-002.11 (the filter unbinds when a request ends; the holder side is Task 6), AC-002.17, AC-002.18, AC-002.19, AC-013.11, AC-005.7 and the host part of AC-005.8
(`/api/v1/jira-app/**` on any host but `api.` → 404 `MSG06`; the chain itself is Task 20).
**Design:** How a request or job gets its Workspace; Security chains (1); Key Decisions P5, R2.
**Description:** `PlatformProperties` (`tsolve.base-domain`, `tsolve.environment`), `HostClassifier` / `HostKind`,
`TenantResolverFilter` + `TenantFilterConfig` (order before Spring Security), `ProblemResponses`
(`application/problem+json`, `code`); `/actuator/health`, `/v3/api-docs*`, Swagger UI pass on any host (AC-002.19);
`admin.`, `login.`, `api.` bind nothing (AC-002.10).
**Edge rows (Critical/High):** #1 (forwarded headers and Workspace ids ignored), #2 (unbound after an exception), #18
(404 / 403 before any 401), #19 (host variants), #20 (health on `localhost:8080`). Medium: #22 (logs hold host and code only).
**Tests (about 12):** `HostClassifierTest`: Edge #19, `www.`/`staging.`/bare/deep hosts, reserved labels; `TenantResolverFilterTest`: Edge
#1, #2, #18, #20, #22, no Registry lookup for a bad label (AC-002.4, AC-002.6), `MSG150` for Suspended; `TenantIsolationIT`
(Workspaces A and B on two databases, JPA and native query through the filter, AC-002.18).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/config/PlatformProperties.java`, `TenantFilterConfig.java`
- Create: `backend/src/main/java/vn/tsolve/platform/web/HostClassifier.java`, `HostKind.java`, `TenantResolverFilter.java`,
  `ProblemResponses.java`
- Modify: `backend/src/main/resources/application.yml`, `.env.example` (`TSOLVE_BASE_DOMAIN`, `TSOLVE_ENVIRONMENT`)
- Test: `backend/src/test/java/vn/tsolve/platform/HostClassifierTest.java`, `TenantResolverFilterTest.java`,
  `backend/src/test/java/vn/tsolve/common/tenant/TenantIsolationIT.java`
**Dependencies:** Tasks 4, 6, 7 (a created Workspace for the isolation test)
**Estimated size:** medium
**Done when:** the three `curl` answers above are what the tests assert and `TenantIsolationIT` passes.

---

## Task 11: Local stack: the whole application behind Caddy on `*.localhost`

**Story:** STORY-008, STORY-009
**Feature:** local-compose
**Assignee:** Trần Trung Hiếu (KAN-94)
**After this task you can:** run `docker compose up`, create a Workspace with Task 9's script, and open
`http://acme.localhost` in a browser (and `login.`, `admin.`, `api.localhost` answering by their routing).
**Covers:** AC-008.1, AC-008.2, AC-008.3, AC-008.4 (tenant changelog applied to every Workspace database at startup of
the web process; the worker never runs Liquibase), AC-008.5, AC-008.6, AC-008.7, AC-008.9, AC-009.2, AC-009.4, AC-009.5,
AC-009.11 (local routing).
**Design:** Infrastructure — Local; Configuration; Key Decisions R14, R16.
**Description:** `docker-compose.yml`: add `backend`, `worker` (same image, `SPRING_PROFILES_ACTIVE=worker`, depends on
Redis and `garage-init`), `web`, `ollama` + one-shot `ollama-pull` (`bge-m3`, `OLLAMA_CHAT_MODEL`), `caddy`; health
checks; every credential from `.env`. `infra/caddy/Caddyfile.local`: `*.localhost`, `admin.localhost`, `login.localhost`,
`api.localhost` with the routing of the criteria. `application-worker.yml` (Liquibase off). `TenantStartupMigrator` (web
process only: runs `TenantMigrator.applyAll()` after the Registry changelog; stops naming the failing Workspace and
leaves the others as they are, AC-008.9). `.env.example` complete; `infra/README.md` lists variables and the start command.
**Edge rows:** #43 (a missing required variable → the backend refuses to start and names it), #44 (start-up migration
failure), #45 (the web process is not healthy without Redis), #46 (`garage-init` fails → backend and worker do not start).
**Tests:** `TenantStartupMigratorTest` (names the failing Workspace, others untouched, AC-008.9; not run in the `worker`
profile); `infra/tests/local-stack.test.sh` (about 10 checks: all healthy; a created Workspace resolves on
`<site>.localhost`; `login.` and `api.` answer 404 outside their paths; `admin.` reaches the web app; backend reaches
Ollama; Edge #43 and #45; `.env.example` has no value).
**Files:**
- Modify: `docker-compose.yml`, `.env.example`, `infra/README.md`
- Create: `infra/caddy/Caddyfile.local`, `backend/src/main/resources/application-worker.yml`
- Create: `backend/src/main/java/vn/tsolve/platform/tenantdb/TenantStartupMigrator.java`
- Test: `backend/src/test/java/vn/tsolve/platform/TenantStartupMigratorTest.java`, `infra/tests/local-stack.test.sh`
**Dependencies:** Tasks 1, 2 (Dockerfile), 7, 9, 10; `fe-foundation` web image; coordinate `docker-compose.yml` /
`.env.example` with `identity` Task 10
**Estimated size:** medium
**Done when:** a teammate starts the stack from `infra/README.md` and opens the Workspace site.

---

## Task 12: Demo seed: the two Workspaces, created through provisioning

**Story:** STORY-012
**Feature:** demo-seed-workspaces
**Assignee:** Trần Trung Hiếu (KAN-97)
**After this task you can:** run `infra/seed/run-seed.sh` on the local stack and open two Workspace sites; run it again and
see "already exists, skipped".
**Covers:** AC-012.1, AC-012.3, AC-012.4, AC-012.5 (skip part), AC-012.6 (provisioning failures).
**Design:** Seed data; Key Decisions P7.
**Description:** `SeedRunner` (profile `seed`, no web server) reads `infra/seed/demo-workspaces.json` (`TSOLVE_SEED_FILE`),
refuses unless `TSOLVE_ENVIRONMENT` is `dev` or `staging`, creates each Workspace through `WorkspaceProvisioning.create`,
skips existing site names, exits non-zero naming the Workspace on a rule failure. `DemoSeedContributor` and
`SeedWorkspace` exist but no contributor is called yet (Task 23). `infra/seed/run-seed.sh` without `--reset`.
**Edge rows:** #54 (production or unset → non-zero, nothing written), #55 (a second run skips both).
**Tests (about 8):** `SeedRunnerTest`: production, `prod`, unset → non-zero and `create` never called (AC-012.3); second run
skips and says so (AC-012.5); a provisioning failure names the Workspace (AC-012.6); the production compose file, its
`.env` template and workflow hold no seed reference (AC-012.4; passes trivially until those files exist).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/seed/SeedRunner.java`, `SeedData.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/DemoSeedContributor.java`, `backend/src/main/java/vn/tsolve/platform/dto/SeedWorkspace.java`
- Create: `backend/src/main/resources/application-seed.yml`, `infra/seed/demo-workspaces.json`, `infra/seed/run-seed.sh`
- Test: `backend/src/test/java/vn/tsolve/platform/SeedRunnerTest.java`
**Dependencies:** Tasks 7, 8, 9, 11; the real `FirstAdminCreator` (`identity` KAN-247) and `InvitationJobQueue` (Task 8) (AC-013.15)
**Estimated size:** small
**Done when:** the walkthrough above works on the local stack.

---

## Task 13: Application VPS and the staging stack

**Story:** STORY-010, STORY-008
**Feature:** staging-servers
**Assignee:** Trần Trung Hiếu (KAN-10, KAN-11, KAN-12)
**After this task you can:** SSH to the application VPS with a key, see the staging stack's containers healthy on the private
network, and see from the internet that only 22, 80 and 443 answer.
**Covers:** AC-008.8, AC-010.6 (hosts through `TSOLVE_BASE_DOMAIN`), AC-010.7, AC-010.8, AC-010.9, AC-010.10, AC-010.11,
AC-010.12, AC-001.33 and AC-001.26 (users reach only their own database, checked on the server).
**Design:** Infrastructure — Application VPS; Redis; Key Decisions P9, R14.
**Description:** Manual first (Hiếu): buy the application VPS (KVM, Ubuntu LTS, snapshots). `infra/scripts/bootstrap.sh`
(deploy user, key-only SSH, ufw 22/80/443, unattended upgrades, Docker + Compose, swap, log rotation, `/srv/tsolve`
layout; the `--role ai` variant is Task 22). `infra/compose.edge.yml` (PostgreSQL with the init script of Task 1,
private network only, no published port; **replaces `TSOLVE_DATABASES=tsolve_staging`**; Caddy placeholder until Task
14). `infra/compose.staging.yml` (backend, worker, web by `:staging`, **its own Redis and its own Garage** with
`garage-init`, `env_file /srv/tsolve/staging/.env`, memory and CPU limits, health checks, `restart: unless-stopped`,
**no `ports:`**, no Ollama). `infra-checks.yml` runs the two tests below.
**Edge rows (Critical/High):** #51 (Docker publishing PostgreSQL, Redis or Garage ports around ufw), #45 (staging
backend not healthy without Redis).
**Tests (about 12 checks):** `infra/tests/ports.test.sh` from a hosted runner (only 22/80/443 open, Edge #51);
`infra/tests/server.test.sh` over SSH (password login refused; `.env` mode 600 and owner; each database user reaches only
its own database; no container publishes a port except Caddy; Redis and Garage limits and health checks; the web
process does not start healthy with Redis stopped).
**Files:**
- Create: `infra/scripts/bootstrap.sh`, `infra/compose.edge.yml`, `infra/compose.staging.yml`,
  `.github/workflows/infra-checks.yml`
- Modify: `infra/README.md` (runbook, orphan-database check)
- Test: `infra/tests/ports.test.sh`, `infra/tests/server.test.sh`
**Dependencies:** Tasks 1, 2
**Estimated size:** medium
**Done when:** `infra-checks.yml` is green against the server.

---

## Task 14: Main Caddy: Cloudflare DNS, wildcard certificates, routing and edge limits

**Story:** STORY-009, STORY-014
**Feature:** edge-caddy
**Assignee:** Trần Trung Hiếu (KAN-13, KAN-95)
**After this task you can:** open `https://<seed site>.staging.tsolve.io.vn` over HTTPS behind basic authentication, see a
Workspace created after the deploy get HTTPS with no manual step, and get a 413 or 429 from a request over the edge limits.
**Covers:** AC-009.1, AC-009.2, AC-009.4, AC-009.5, AC-009.6, AC-009.7, AC-009.8, AC-009.9, AC-009.10, AC-009.11 (for staging;
production hosts later), AC-014.9, AC-014.10, AC-014.11 (DevOps step A5).
**Design:** Infrastructure — DNS, Edge limits; Signed URL host and routing (the route is added by Task 17); Tooling
decisions W2.
**Description:** Manual prerequisites (whoever controls `tsolve.io.vn`): move the nameservers to Cloudflare (free plan);
DNS-only records `tsolve.io.vn`, `*.tsolve.io.vn`, `*.staging.tsolve.io.vn` → application VPS; an API token with
Zone:DNS:Edit + Zone:Read on that zone only in `/srv/tsolve/edge/.env` as `CLOUDFLARE_API_TOKEN`. Then
`infra/caddy/Dockerfile` (xcaddy with `caddy-dns/cloudflare` and `mholt/caddy-ratelimit`, pinned); `infra/caddy/Caddyfile`
(DNS-01 wildcards, the shared routing snippet, `Host` passed unchanged, HTTP → HTTPS, HSTS, `lb_try_duration`, staging
`basic_auth` on sites, `admin.`, `login.` but not `api.`, `request_body max_size`, timeouts, rate-limit zones per client IP
and per site host from `EDGE_*`); `images.yml` builds `tsolve-caddy` when `infra/caddy/**` changes; the token-rotation runbook.
**Edge rows (Critical/High):** #47 (Cloudflare token expired at renewal), Medium: #48, #68, #69.
**Tests (about 12 checks):** `infra/tests/caddy-routing.test.sh` (each host and path, a Workspace created after the deploy
gets HTTPS, `www.`/`staging.` → 404, Edge #47, #48); `infra/tests/edge-limits.test.sh` (413 over `EDGE_MAX_BODY_BYTES`,
a file between the two limits passes the edge, a failing check if `EDGE_MAX_BODY_BYTES` ≤ `FILE_STORAGE_MAX_BYTES`, timeout,
429 with `Retry-After` per IP and per site, Edge #68, #69). If the checks exceed 12, move edge limits to a card of their own.
**Files:**
- Create: `infra/caddy/Dockerfile`, `infra/caddy/Caddyfile`
- Modify: `infra/compose.edge.yml`, `.github/workflows/images.yml`, `.env.example`, `infra/README.md` (DNS table, token rotation)
- Test: `infra/tests/caddy-routing.test.sh`, `infra/tests/edge-limits.test.sh`
**Dependencies:** Task 13; the manual DNS move
**Estimated size:** medium
**Done when:** both scripts pass against staging.

---

## Task 15: Deploy staging on every merge, with migrations, seed and the smoke test

**Story:** STORY-010
**Feature:** staging-cd
**Assignee:** Trần Trung Hiếu (KAN-14, KAN-200)
**After this task you can:** merge a pull request into `main` and watch staging update by itself, finishing with the smoke test.
**Covers:** AC-010.3, AC-010.4, AC-010.5, AC-010.6, AC-008.4 (tenant changelog on every Workspace before `up`).
**Design:** Infrastructure — Deploy; Key Decisions P9, R14.
**Description:** `infra/scripts/install-runner.sh` (deploy-only runner, non-root service, CPU/RAM capped, label
`tsolve-deploy`, runner group limited to `cd-staging.yml`). `cd-staging.yml` triggered by `workflow_run` of `images.yml` on
`main` only: pull → `migrate` profile (Registry changelog, then the tenant changelog for every Workspace through
`TenantMigrator.applyAll()`) → `up -d` → wait for health → `seed` profile → `infra/tests/smoke.test.sh` (`api.staging…/actuator/health`
UP; a seed site's page 200 with basic auth; its `/api/v1/me` → 401 `MSG07`; credential from a repository secret of the workflow, the plaintext of `STAGING_BASIC_AUTH_HASH`).
`application-migrate.yml` + `MigrateRunner`.
**Edge rows (Critical/High):** #49 (a pull request from a fork never reaches the self-hosted runner), #50 (a failing
migration stops before `up`, names the Workspace, old containers keep serving).
**Tests (about 8):** `MigrateRunnerTest` (stops at the first failing Workspace and names it); `cd-staging.yml` dry run for Edge #50;
a failing seed step fails the workflow and names the step, and the seed runs before the smoke test (AC-010.3, AC-010.5);
no trigger from pull requests (Edge #49).
**Files:**
- Create: `infra/scripts/install-runner.sh`, `.github/workflows/cd-staging.yml`
- Create: `backend/src/main/resources/application-migrate.yml`, `backend/src/main/java/vn/tsolve/platform/seed/MigrateRunner.java`
- Test: `infra/tests/smoke.test.sh`, `backend/src/test/java/vn/tsolve/platform/MigrateRunnerTest.java`
**Dependencies:** Tasks 2, 8, 10, 12, 13, 14; `be-foundation` Task 2 (401 `MSG07` on `/api/**` without a session)
**Estimated size:** medium
**Done when:** a merge to `main` ends with a green smoke test on staging.

---

## Task 16: File storage per Workspace in object storage, with signed URLs

**Story:** STORY-007
**Feature:** file-storage
**Assignee:** Đặng Nhật Minh (KAN-96)
**After this task you can:** store a file in a Workspace in `ObjectFileStorageIT` and read it back; get a signed URL for it
that the test downloads straight from Garage; the same URL fails after the TTL, and another Workspace's key gets nothing.
**Covers:** AC-007.1 – AC-007.15 (the signed URL as the service returns it; its route on a Workspace site is Task 17).
**Design:** Storage layout; Signed URL host and routing (R11); Tooling decisions W1; Key Decisions R10 – R13.
**Description:** `FileStorage`, `ObjectFileStorage` (AWS SDK v2 `s3`, path-style, one `PutObject` of known length,
object name `<workspaceId>/<uuid>`, key = the UUID only, `S3Presigner` signed for `FILE_STORAGE_PUBLIC_URL_PATTERN` with
the site name from `WorkspaceCatalog.findById`), `StoredFile`, `FileTooLargeException`, `InvalidFileKeyException` (extends
`NotFoundException`, 404 `MSG06`), `StorageConfig` (`tsolve.storage.*`). `GarageTestBase` starts the pinned Garage image
as a generic container with the bootstrap of `garage-init.sh`. Six production files.
**Edge rows (Critical/High):** #35 (traversal and malformed keys refused before any object call), #36 (A's key while B is
bound: `read`, `delete`, `signedUrl`), #37 (10 MB, +1 byte, 0 bytes), #38 (Garage down or broken connection during
`store`), #39 (expired or altered URL, unsigned request). Low: #42.
**Tests (about 10):** `ObjectFileStorageIT` (Edge #35, #37, #38, #39, #42; read and delete of a missing key; none bound throws;
no file on the local disk, AC-007.14; anonymous request refused, AC-007.15); `TenantIsolationIT` storage case (AC-007.11,
Edge #36).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/service/FileStorage.java`, `service/impl/ObjectFileStorage.java`,
  `dto/StoredFile.java`, `exception/FileTooLargeException.java`, `InvalidFileKeyException.java`, `config/StorageConfig.java`
- Modify: `backend/pom.xml` (W1), `backend/src/main/resources/application.yml`, `.env.example` (`FILE_STORAGE_PUBLIC_URL_PATTERN`)
- Test: `backend/src/test/java/vn/tsolve/platform/ObjectFileStorageIT.java`, `backend/src/test/java/vn/tsolve/support/GarageTestBase.java`
**Dependencies:** Tasks 1, 4, 6
**Estimated size:** medium
**Done when:** `ObjectFileStorageIT` passes and the isolation test shows A's file out of B's reach.

---

## Task 17: Signed-URL route on a Workspace site

**Story:** STORY-009, STORY-007
**Feature:** signed-url-route
**Assignee:** Đặng Nhật Minh (KAN-96)
**After this task you can:** download a stored file in a browser from its signed URL on the Workspace site
(`http://acme.localhost/<bucket>/…`), on staging too; see a 405 for any other method and a download, not a rendered page,
for a stored HTML file.
**Covers:** AC-009.2 (the `/<bucket>/*` route on a Workspace site), AC-007.12 and AC-007.15 (through Caddy).
**Design:** Signed URL host and routing (R11, R12); Infrastructure — Edge limits.
**Description:** The Caddy route (GET/HEAD of `/<bucket>/*` to `garage:3900` with the original `Host`, other methods 405,
`nosniff`, signature parameters out of the access log) added to `Caddyfile.local` and `Caddyfile`.
**Edge rows:** #40 (signature not in the Caddy access log), #41 (HTML/SVG downloaded, not rendered).
**Tests (about 5 checks):** `infra/tests/caddy-routing.test.sh` additions: signed GET 200 with `nosniff` and a download type,
HEAD allowed, PUT/POST/DELETE 405, an altered signature refused, Edge #40 and #41.
**Files:**
- Modify: `infra/caddy/Caddyfile.local`, `infra/caddy/Caddyfile`
- Test: `infra/tests/caddy-routing.test.sh`
**Dependencies:** Tasks 11 (local Caddy), 14 (staging Caddy), 16
**Estimated size:** small
**Done when:** the signed-URL download works in a browser on a local Workspace site and `caddy-routing.test.sh` passes.

---

## Task 18: Link a Jira site to a Workspace

**Story:** STORY-004
**Feature:** jira-site-link
**Assignee:** Trần Trung Hiếu (KAN-98; used by UC-10 of `workspace`)
**After this task you can:** (serves `workspace` UC-10) call `linkJiraSite` in `WorkspaceCatalogIT` and see the normalised URL and
the `cloudId` stored on the bound Workspace, and a second Workspace refused with `MSG153`.
**Covers:** AC-004.19, AC-004.20, AC-004.21, AC-004.22, AC-004.23, AC-004.24.
**Design:** Registry rules (`linkJiraSite`); Key Decisions Q9.
**Description:** `linkJiraSite(siteUrl)` in `WorkspaceCatalog` and its implementation; `TenantInfoClient` /
`RestTenantInfoClient` (public-address guard, `https`, no redirects, 5 s timeouts, 64 KB cap); conditional `UPDATE … WHERE
jira_cloud_id IS NULL`, a unique violation → `MSG153`.
**Edge rows (Critical/High):** #24 (two Admins link the same site at once → one stored, the other `MSG153`, never a 500),
#25 (`http://`, a path, user info, a port, a host resolving to a private address → `MSG156`, no outbound call). Medium: #27.
**Tests (about 10):** `WorkspaceCatalogImplTest` (every URL rule, Edge #25; tenant-info unreachable stores nothing,
AC-004.22; second link refused, Edge #27); `WorkspaceCatalogIT` (race, Edge #24; `MSG153` does not name the other Workspace).
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/platform/service/WorkspaceCatalog.java`, `service/impl/WorkspaceCatalogImpl.java`
- Create: `backend/src/main/java/vn/tsolve/platform/client/TenantInfoClient.java`, `RestTenantInfoClient.java`
- Test: `backend/src/test/java/vn/tsolve/platform/WorkspaceCatalogImplTest.java`, `WorkspaceCatalogIT.java`
**Dependencies:** Tasks 4, 6
**Estimated size:** small
**Done when:** both test classes pass.

---

## Task 19: Verify the Forge Invocation Token (the verifier)

**Story:** STORY-005
**Feature:** forge-token
**Assignee:** Trần Trung Hiếu (KAN-105)
**After this task you can:** run `NimbusForgeTokenVerifierTest` and see a valid test token accepted and a forged, expired,
unsigned, wrong-audience or wrong-issuer token refused, with the key set fetched again once for an unknown key id.
**Covers:** AC-005.2, AC-005.3 (the verifier's refusals), AC-005.4.
**Design:** Security chains (2); Key Decisions P3.
**Description:** `spring-security-oauth2-jose` in `pom.xml` (approved, P3). `ForgeTokenVerifier` and `NimbusForgeTokenVerifier`
(`NimbusJwtDecoder.withJwkSource` over a caching `JWKSource` from `FORGE_JWKS_URL`, an unknown key id refetches once;
validators: timestamp, issuer `forge/invocation-token`, audience `ari:cloud:ecosystem::app/` + `FORGE_APP_ID`) returning
`VerifiedForgeCall` (claims only). First step, before any code or fixture: confirm from Atlassian's Forge Remote documentation
the claim paths of the `cloudId`, the principal's account id and the lifecycle body's `eventType` values, and record them in
the master design.
**Edge rows (Critical/High):** #28 (`alg: none`, HS256 with the public key, expired, wrong audience or issuer), #29 (key set
unreachable, unknown key id). Medium: #22 (logs hold no token).
**Tests (about 7):** `NimbusForgeTokenVerifierTest` (Edge #28, #29; logs hold no token, Edge #22).
**Files:**
- Modify: `backend/pom.xml`, `backend/src/main/resources/application.yml`, `.env.example` (`FORGE_APP_ID`, `FORGE_JWKS_URL`)
- Create: `backend/src/main/java/vn/tsolve/platform/forge/ForgeTokenVerifier.java`, `NimbusForgeTokenVerifier.java`,
  `VerifiedForgeCall.java`
- Test: `backend/src/test/java/vn/tsolve/platform/NimbusForgeTokenVerifierTest.java`
**Dependencies:** Task 10
**Estimated size:** small
**Done when:** the test class passes and the claim paths are recorded in the design.

---

## Task 20: Forge security chain on `api.`: the filter, the call context, the 404s

**Story:** STORY-005
**Feature:** forge-security-chain
**Assignee:** Trần Trung Hiếu (KAN-105)
**After this task you can:** call `api.localhost/api/v1/jira-app/...` with `curl`: 401 for a missing, forged or expired token,
and a Forge token sent to a Workspace site gets 404; any other path on `api.` gets 404.
**Covers:** AC-005.1, AC-005.3 (the 401 `ProblemDetail`, no handler, no Workspace lookup), AC-005.5, AC-005.6, AC-005.7, AC-005.8.
**Design:** Security chains (2); Key Decisions P3.
**Description:** `ForgeTokenFilter` (reads the `Authorization: Bearer` header, calls `ForgeTokenVerifier`, refuses with 401
without `code`), `ForgeCallContext` with its request-scoped implementation (the `cloudId` and the principal's account id for
that request only), `ForgeCallAuthentication`, and `ForgeSecurityConfig` (`@Order(1)`, host `api.<base>`, stateless, no CSRF,
no cookie; other paths on `api.` → 404 `MSG06`).
**Edge rows (Critical/High):** #30 (Forge token on a Workspace site, `login.` or `admin.`). Verifier rows #28 and #29 are
re-asserted through the HTTP chain.
**Tests (about 6):** `ForgeSecurityIT` (Edge #30, a refused token reaches no handler and no Workspace lookup, no `Set-Cookie` on
`api.`, other paths on `api.` → 404 `MSG06`, AC-005.7; the claims are visible only for the request, AC-005.5).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/forge/ForgeTokenFilter.java`, `ForgeCallContext.java` (+ request-scoped
  impl), `ForgeCallAuthentication.java`
- Create: `backend/src/main/java/vn/tsolve/platform/config/ForgeSecurityConfig.java`
- Test: `backend/src/test/java/vn/tsolve/platform/ForgeSecurityIT.java`
**Dependencies:** Tasks 10, 19
**Estimated size:** small
**Done when:** the test class passes.

---

## Task 21: Tenant Resolver of the Jira App and the lifecycle endpoint

**Story:** STORY-006
**Feature:** jira-tenant-resolver
**Assignee:** Trần Trung Hiếu (KAN-101; contract agreed with Đặng Tấn Bình through the spec)
**After this task you can:** send a verified test token for a linked `cloudId` and see that Workspace's data; an unlinked one gets
403 `MSG152`, a Suspended one 403 `MSG150`; post an installed event and get 204 with nothing changed.
**Covers:** AC-006.1 – AC-006.5, AC-006.12, AC-006.14, AC-006.15, AC-006.16.
**Design:** How a request or job gets its Workspace (Jira rows); Lifecycle events.
**Description:** In `ForgeTokenFilter`: verified `cloudId` → `WorkspaceCatalog.findByCloudId` → bind; `MSG152` (`tracker_name` = `Jira`)
and `MSG150` as 403. `ForgeLifecycleController` (`POST /api/v1/jira-app/lifecycle`, no Workspace bound; installed and
uninstalled → 204, nothing changed; an installed event from an unlinked `cloudId` is logged). Tests use a test-only event
handler to prove nothing is recorded for a Suspended Workspace. Check Atlassian's retry rules for Forge Remote events: if a 4xx is
retried, tell Đặng Tấn Bình and raise a spec change before closing the task.
**Edge rows (Critical/High):** #31 (`cloudId` linked to no Workspace), #32 (Suspended Workspace: call 403, ticket event
recorded nowhere), #34 (calls from A's and B's Jira sites read only their own rows). Medium: #33.
**Tests (about 10):** `ForgeSecurityIT` additions (Edge #31, #32, `cloudId` only from the token, AC-006.2); `ForgeLifecycleIT` (Edge #33,
AC-006.12, AC-006.15, AC-006.16); `TenantIsolationIT` Jira App case (AC-006.14, Edge #34).
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/platform/forge/ForgeTokenFilter.java`, `frontend/openapi/tsolve-api.yaml`
- Create: `backend/src/main/java/vn/tsolve/platform/controller/ForgeLifecycleController.java`
- Test: `backend/src/test/java/vn/tsolve/platform/ForgeLifecycleIT.java`, `ForgeSecurityIT.java`,
  `backend/src/test/java/vn/tsolve/common/tenant/TenantIsolationIT.java`
**Dependencies:** Tasks 4, 7, 20
**Estimated size:** small
**Done when:** the three test classes pass and `npm run generate:api` leaves no diff.

---

## Task 22: AI VPS with Ollama behind WireGuard

**Story:** STORY-011
**Feature:** ai-vps
**Assignee:** Trần Trung Hiếu (KAN-203)
**After this task you can:** from the application VPS call the AI VPS and get a 1024-dimension embedding from `bge-m3`, also after
rebooting the AI VPS; see from the internet that port 11434 is closed.
**Covers:** AC-011.1 – AC-011.6.
**Design:** Infrastructure — Private link; Key Decisions P11, Q13.
**Description:** Manual first (Hiếu): buy the AI VPS sized for the KAN-140 models. `bootstrap.sh --role ai` (ufw: 22,
UDP 51820 only from the application VPS; no outgoing Internet except the setup downloads). `infra/scripts/wireguard-setup.sh
app|ai` (keys generated on the host in `/etc/wireguard`, mode 600, never committed; `wg0` 10.66.0.1 / 10.66.0.2;
`PersistentKeepalive`; `wg-quick@wg0` enabled). `infra/scripts/ollama-setup.sh` (pinned installer; `OLLAMA_HOST=10.66.0.2:11434`
written **before** the first start; ordering on `wg-quick@wg0`; ufw allows 11434 only on `wg0` from 10.66.0.1; pulls
`bge-m3` and `OLLAMA_CHAT_MODEL`; prompts and answers not logged). Staging `.env`: `OLLAMA_BASE_URL=http://10.66.0.2:11434`.
**Edge rows (Critical/High):** #52 (AI VPS reboot), #53 (the installer starts Ollama on `0.0.0.0` before the override).
**Tests (about 8 checks):** `infra/tests/ai-vps.test.sh` from the application VPS (1024-dimension embedding, a completion, again
after a reboot); `ports.test.sh` finds 11434 closed (Edge #53); `server.test.sh` on the AI VPS (AC-011.5, AC-011.6).
The chat-model pull and the completion check wait for KAN-140; the `bge-m3` parts do not.
**Files:**
- Modify: `infra/scripts/bootstrap.sh`, `infra/tests/ports.test.sh`, `infra/tests/server.test.sh`, `infra/README.md`
- Create: `infra/scripts/wireguard-setup.sh`, `infra/scripts/ollama-setup.sh`
- Test: `infra/tests/ai-vps.test.sh`
**Dependencies:** Task 13; KAN-140 model choice
**Estimated size:** medium
**Done when:** `ai-vps.test.sh` passes after a reboot.
