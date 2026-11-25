---
module: platform
date: 2026-11-04
total_tasks: 29
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
task you can …"; a plumbing task names the visible task it serves.

**Wave order (Hiếu, 22/11; KAN-384; replaces the old sequence for Tasks 3–10):** multi-tenant support comes first, and
it is tried on staging instead of the local stack — the staging site `bluefin` (slug in `infra/scripts/deploy.sh`,
domain `bluefin.tsolve.io.vn`). The local stack (Task 11), the wildcard DNS (Task 14) and the GHCR part of Task 2 wait.
Items of one wave run in parallel; "after" names the items that must be on `main` first.
- **Wave 1.** (1) Task 6a-1, one Workspace holder (KAN-384), then Task 6a-2, the Workspace fallbacks removed (KAN-385;
  after 6a-1 is merged; it can overlap with item 2) — nothing visible; staging smoke only: after 6a-1 sign in on
  bluefin, a wiki upload, a tracker suggestion job; after 6a-2 also a review email queued. (2) Task 3a, the Registry
  database (KAN-251; Task 3b is its staging script), with the local init-script part of Task 1 and a one-off staging script (the init script runs only
  on an empty volume) — `psql` shows `tenant_workspace` and `workspace_email`, locally and on staging.
- **Wave 2.** (3) Task 5, the row guard built and tested, not switched on (KAN-253; after item 2) — nothing changes on
  staging; `RowGuardIT` and `ConventionGuardIT` pass. (4) Task 4, the catalog with bluefin adopted (KAN-252; after
  items 1 (both 6a-1 and 6a-2) and 2), planned on 24/11 as four tasks, merged and deployed in this order: 4a-1 the catalog
  (`WorkspaceCatalog`, nothing visible), 4a-2 bluefin adopted (one Registry row copied from the env file and the tenant
  `workspace` table; wiki files open), 4b sign-in, Jira App and `/me` read the catalog (`NoWorkspaceLookup`,
  `ConfigJiraWorkspaceLookup`, `StubWorkspaceStatusLookup` go), 4c the worker reads the catalog (`worker-mock` goes) —
  bluefin still signs in, answers Jira App calls and runs its jobs, now found through its Registry row.
- **Wave 3** (both after items 1 and 4). (5) The rest of Task 6: the routing `DataSource` and the per-Workspace pools
  (KAN-254) — nothing visible on staging (bluefin keeps its one database); `TenantRoutingIT` shows two databases.
  (6) The rest of the web resolver, Task 10 (KAN-258): `MSG151` / `MSG150` before `MSG07`, anonymous site requests
  bound, `TenantIsolationIT` — on staging an unknown site answers 404 `MSG151`. `TenantIsolationIT` needs item 5's two
  databases, so that part merges after item 5.
- **Wave 4** (after waves 2 and 3). (7) The row guard switched on for bluefin on staging, a task planned when wave 3 is
  merged (no number yet): backup first; new owner and runtime roles (the app user owns the database today,
  `infra/postgres/init/10-tsolve-dbs.sh:14`); the `tenant_identity` row; the policies; the one-row `workspace` table
  (`035-workspace-info.sql`, primary key `workspace_id`) on the allow-list — bluefin works as before, and `psql` as the
  runtime user with no tenant setting sees no row. Before FORCE: re-check the Task 4a-2 adopt script's read of the tenant
  `workspace` row (jira_site_address, jira_cloud_id) — with the guard forced and no tenant bound, it reads 0 rows and
  bluefin would silently get no Jira site.
- **Wave 5.** (8) Task 7, provisioning, in parallel with the rest of `identity` KAN-247 (identity Task 14) — plumbing
  for items 9 and 10; (9) Task 8, the invitation queue; (10) Task 9, the ops script — create a Workspace by hand on
  staging; then Task 12, the demo seed — a second Workspace on staging.
- **Separate lane:** the Jira App Tasks 18–21, after items 4 and 5. Not reordered: Tasks 1, 2, 11, 13–17 and 22–24.

**Other modules (blockers, not tasks of this module):**
- `identity` (KAN-247) implements `FirstAdminCreator`. `InvitationJobQueue` has a minimal implementation in `platform`
  on JobRunr in the Jobs database (user decision, 4/11; `worker` takes the job handler over later): it is Task 8 here.
  Task 7 declares both ports and tests with doubles in test sources only. Tasks 9, 12 and 15 start the real backend in
  the `ops` / `seed` profile, which refuses to start without both beans (AC-013.15), so they wait for Task 8 and for
  `identity` KAN-247.
- `docker-compose.yml` and `.env.example` are edited by Tasks 1, 6a-2, 8, 10, 11, 14, 16 and 19 here (Tasks 8, 14 and 19 only add names to `.env.example`; Task 6a-2 only removes names) and by `identity` Task 10 (Redis
  host, session variables): rebase on each other's branch and do not overwrite another module's variable.
- The Spring Boot bump to 3.5.x (DevOps A2) is not a task of this module.

---

## Task 1: Local data plane: PostgreSQL roles and databases, Redis, Garage

**Story:** STORY-008, STORY-001
**Feature:** local-data-plane
**Assignee:** Trần Trung Hiếu (KAN-94); Jira sub-task KAN-249
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
  access key limited to that bucket; exits non-zero on failure). `infra/garage/garage.toml` sets no `root_domain` in
  `[s3_api]`, so Garage reads the Workspace hosts as path-style (RESEARCH-005, Task 17). Removes `FILE_STORAGE_ROOT`, `TSOLVE_DATABASES`,
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
**Assignee:** Trần Trung Hiếu (KAN-6, KAN-8, KAN-9); Jira sub-task KAN-250
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

## Task 3a: Registry schema, password cipher and `registryDataSource`

**Story:** STORY-001
**Feature:** registry-db
**Assignee:** Trần Trung Hiếu (KAN-98); Jira sub-task KAN-251
**After this task you can:** start the backend against the compose stack of Task 1 with `REGISTRY_DB_URL` set and see
`tenant_workspace` and `workspace_email` created, empty, in `tsolve_<env>_registry` (`psql \dt`).
**Covers:** AC-001.15, AC-001.16, AC-001.17, AC-001.18 (Registry changelog), AC-001.19, AC-001.24, AC-001.25,
AC-001.27 (Registry part), AC-001.33 (the Registry changelog creates nothing in the Jobs database), AC-008.4 (the
Registry changelog applied at startup, the worker never runs Liquibase).
**Design:** Data Model — Tenant Registry DB; Key Decisions R1, R8; Configuration.
**Description:** `catalog/catalog-master.xml`, `C001-registry-tenant-workspace.sql` (table `tenant_workspace`, key
`workspace_id`), `C002-registry-workspace-email.sql` (table `workspace_email`); `PasswordCipher` (AES-256-GCM, 12-byte
nonce, Workspace id as associated data, `v1:` prefix, key `CATALOG_SECRET_KEY`; a fixed failure message with no secret,
no ciphertext and no cause chain); `RegistryDataSourceConfig` (`registryDataSource`, `registryTransactionManager`,
`JdbcClient`, Liquibase for the catalog changelog as the Registry user at startup of the web process). Decided in
planning: the Registry beans load only when `REGISTRY_DB_URL` is not blank (like `StorageConfig`); once it is set, a
missing or invalid `CATALOG_SECRET_KEY` refuses startup and names the variable; Task 5 drops the gate when routing makes
the Registry required. One Registry user, `REGISTRY_DB_USER`, owner of `tsolve_<env>_registry`, migrates and reads; a
DML-only runtime user is split off in Task 1's role work. The beans must leave Boot's primary `DataSource` and the
tenant `liquibase` bean in place (edge #72; see `EmailOutboxConfiguration`). `RegistryRepository` is Task 4's.
`PostgresTestBase` is a new JVM-shared holder (Testcontainers `pgvector/pgvector:pg16` running the real
`infra/postgres/init` script; the existing Jobs and tenant fixtures cannot be reused). Adds `spring-boot-starter-jdbc`
and the Testcontainers artifacts to `backend/pom.xml` if missing.
**Edge rows:** #13 (key missing → startup refuses; wrong key → only that Workspace fails), #14 (no password in any log
line; the log-capture test is finished in Task 6), #72 (the Registry beans leave the primary `DataSource` and the tenant
Liquibase in place).
**Tests (13):** `PasswordCipherTest` (4): round trip with a `v1:` value and no plaintext, a ciphertext copied to another
row does not decrypt, wrong key or tampered or malformed value refuses with a message holding no secret, missing or
invalid key names `CATALOG_SECRET_KEY` (AC-001.24, AC-001.25, Edge #13, #14); `RegistryChangelogIT` (9, through
`PostgresTestBase`): `tenant_workspace` columns, types, keys, unique and status check (AC-001.15), `workspace_email`
lower-case key and foreign key (AC-001.16), only the allowed tables (AC-001.17), empty tables and a second start changes
nothing (AC-001.19), nothing created in the Jobs database (AC-001.33), the web process applies the catalog changelog as
the owner at startup (AC-008.4, AC-001.18, AC-001.27), the Registry beans leave the primary `DataSource` and the tenant
Liquibase in place (Edge #72), the worker never runs the catalog changelog (AC-008.4), and a blank `REGISTRY_DB_URL`
leaves the Registry beans off (the gate).
**Files:**
- Create: `backend/src/main/resources/db/changelog/catalog/catalog-master.xml`, `C001-registry-tenant-workspace.sql`,
  `C002-registry-workspace-email.sql`
- Create: `backend/src/main/java/vn/tsolve/platform/registry/PasswordCipher.java`, `RegistryDataSourceConfig.java`
- Modify: `backend/pom.xml` (only if missing)
- Modify: `infra/postgres/init/10-tsolve-dbs.sh` (the Registry part: the Registry database and its owner user, for local
  and test)
- Modify: `backend/src/main/resources/application.yml`, `.env.example` (`REGISTRY_DB_*` and `CATALOG_SECRET_KEY`)
- Test: `backend/src/test/java/vn/tsolve/support/PostgresTestBase.java`
- Test: `backend/src/test/java/vn/tsolve/platform/registry/PasswordCipherTest.java`, `RegistryChangelogIT.java` (same
  package as the classes they test)
**Dependencies:** Task 1 (init script)
**Estimated size:** medium (8 production files, 13 tests)
**Done when:** the test classes pass in `mvn verify` and the walkthrough above shows the two tables; the web process
applies the catalog changelog at startup and the worker never runs Liquibase; a missing `CATALOG_SECRET_KEY` fails
startup naming the variable once `REGISTRY_DB_URL` is set.

---

## Task 3b: Registry database on the existing staging volume

**Story:** STORY-001
**Feature:** registry-staging-script
**Assignee:** Trần Trung Hiếu (KAN-98); Jira sub-task: new, key to be added later
**After this task you can:** run the staging script against the existing staging volume and see
`tsolve_staging_registry` and its owner user created (`psql \l`, `\du`), with the other databases untouched; running it
again changes nothing.
**Covers:** AC-001.15 (the Registry database exists on a volume made before Task 3a), AC-001.32 (the Registry user has
no superuser and no `BYPASSRLS`), AC-008.5 (the new variables are set where staging reads them).
**Design:** Roles and grants; Configuration; Infrastructure.
**Description:** `infra/scripts/registry-db-init.sh`, an idempotent one-off script: it refuses to run without
`POSTGRES_USER` and `POSTGRES_PASSWORD` (the superuser variables), creates `REGISTRY_DB_NAME` (default
`tsolve_staging_registry`) owned by `REGISTRY_DB_USER`, and that user (LOGIN, no superuser, no `BYPASSRLS`, no
`CREATEDB`, no `CREATEROLE`), on the EXISTING volume, because the init script runs only on an empty one. A second run
leaves an existing role as it is, never alters its password and never prints it. Local developers run the same script
against the local `db` (a runbook line in `infra/README.md`; no new compose file).
**Edge rows:** #73 (refuses without the superuser variables), #74 (creates the Registry on an existing volume with the
same end state as the init script), #75 (a second run changes nothing), #14 (no password in the output).
**Tests (4 checks, run by hand with Docker; the output goes into the PR):** `infra/tests/registry-db.test.sh`: S1 refuses
without `POSTGRES_USER` or `POSTGRES_PASSWORD`, naming it, and creates nothing (edge #73); S2 creates the Registry
database and user on an existing volume, the user has the right attributes and connects, the other databases are
unchanged, no password in the output (edge #74); S3 a second run, even with another `REGISTRY_DB_PASSWORD`, leaves the
database and role rows identical and the original password working (edge #75); S4 the end state equals that of the init
script on a fresh volume (edge #74).
**Files:**
- Create: `infra/scripts/registry-db-init.sh`
- Modify: `infra/compose.staging.yml`, `infra/staging.env.example` (`REGISTRY_DB_*` and `CATALOG_SECRET_KEY`)
- Modify: `infra/README.md` (runbook line for the script, staging and local)
- Test: `infra/tests/registry-db.test.sh`
**Dependencies:** Task 3a (the init script's Registry part and the variable names)
**Estimated size:** small (4 production files, 4 checks)
**Done when:** `bash infra/tests/registry-db.test.sh` passes S1–S4 and its output is pasted in the PR; staging still
starts after the script has run and the variables are set.

---

## Task 4: The Workspace catalog, bluefin adopted, and every lookup reading the catalog (split into 4a-1, 4a-2, 4b and 4c)

Planned in two steps on 24/11 (KAN-252; step-1 questions Q1 to Q9 answered by Hiếu: Q1 to Q7 with the recommended
option, Q8 replaced, Q9 = A). Done as one task it would be about 17 production files and 40 tests, so it is four tasks.
They are merged and deployed **in this order**, so that the Registry row exists before anything reads it. Each is one QA
card and one developer card; 4a-1 stays on KAN-252 and branch `feat/KAN-252-platform-workspace-catalog`; 4a-2, 4b and 4c
get new Jira sub-tasks and their own branches (`<type>/KAN-<n>-platform-…`), created by the orchestrator. The adopt
script is its own task (4a-2) because 4a would otherwise hold 21 tests and 6 production files. A dependency line of another
task that says "Task 4" means 4a-1 to 4c unless it names one: Task 18 needs 4a-1 only; Tasks 6 and 10 need all four.

**Story:** STORY-004 (4a-1, 4b, 4c), STORY-001 (4a-2)
**Spec changed first (Change Mode, 24/11; Hiếu approves each before its code):** `platform` AC-004.29 and the contract
method `findActive()` (Q2); `worker` AC-002.11 and AC-003.2 retired, AC-002.12 reworded, AC-003.3 added (Q5);
`be-foundation` AC-003.7 retired (Q6). 4a-1 needs `sdd.ts approve platform`; 4b needs `sdd.ts approve be-foundation`;
4c needs `sdd.ts approve worker`.

**Decisions of the plan (design R17 to R20, Edge rows #76 to #85):** the adopt script is idempotent and runs once per
environment; its Jira site is copied from bluefin's own tenant `workspace` table, never typed (Q8); the lookups answer
empty when no Registry bean exists and the code gate stays until Task 6 (Q4); no cache (Task 10 may add one); `dev`
keeps `DevWorkspaceLookup`; `MockWorkerTenantService` moves to test sources as `FixedWorkerTenantService` (Q7).

**Pre-deploy checklist (Hiếu, on the VPS, before 4a-2 is run; a checklist, not a blocker for coding):**
1. `TSOLVE_DEMO_WORKSPACE_ID` = field 3 of the old `TSOLVE_STUB_WORKSPACES` value (a UUID, not a secret);
   `REGISTRY_DB_*` and `CATALOG_SECRET_KEY` are set (Task 3b); `DB_NAME`, `DB_USER`, `DB_PASSWORD` are bluefin's.
2. Does bluefin's tenant `workspace` table have its row? (`select jira_site_address, jira_cloud_id from workspace;` in
   bluefin's database.) If yes, the script copies both Jira columns. If no, bluefin's row has no Jira site, and Jira App
   calls for bluefin stay refused until the Admin does Setup (UC-10; linking is Task 18).
3. Is `TSOLVE_STUB_JIRA_SITES` set on the VPS? If it is set **and** the tenant row is empty, the Jira App works today
   and stops working at 4b: run Setup before 4b, or accept it. Never hand-type a cloud id into the Registry (Edge #79).
4. `TSOLVE_STUB_WORKSPACE_READY` is `false` or absent (4b removes the stub; with `true` and the real lookup both
   present `/me` fails today).
5. Before 4c: the worker container has `REGISTRY_DB_URL` and `CATALOG_SECRET_KEY` (4b makes them required in compose).

**Stale mentions to update outside the code PRs (listed here, not edited):** `.env.example:76` (inside the
`TSOLVE_STUB_WORKSPACES` comment; part of 4b); `docs/bug-log.md:344` (leave it as it is, the bug row is history);
`harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md:92,154` and `04-plans/workspace/design.md:585` (design
files of other modules; edit them through their own plan flow after 4b merges); `04-plans/platform/iteration-1/design.md:64`
(done in this plan).

**Existing tests that name the removed stand-ins** (each goes in the card of the task that removes the stand-in; the
dev card runs `grep -rn` for each removed name over `backend/src/test` before it finishes):
- 4b: delete `identity/signin/port/NoWorkspaceLookupTest`, `jiraapp/service/impl/ConfigJiraWorkspaceLookupTest`,
  `common/session/StubWorkspaceStatusLookupTest`; update `identity/dev/DevWorkspaceLookupTest`,
  `identity/keycloak/ProvisioningProfileTest`, `identity/mock/MockUserListSeederTest`,
  `jiraapp/service/impl/JiraIssueAccessImplTest` (it builds `ConfigJiraWorkspaceLookup` at about line 133, in
  `aJiraAppCallBoundByItsCloudIdIsSeenByTheHolder_AC_002_12`), `search/list/repository/TicketListRepositoryIT`
  (`TSOLVE_STUB_WORKSPACES`), `platform/NoWorkspaceFallbacksTest`. No change: `common/tenant/AcceptingKnownWorkspaces`
  and its three users, `ThreadLocalTenantContextTest`, `PlatformTenantScopeTest`.
- 4c: delete `worker/service/mock/MockWorkerTenantServiceTest` and `worker/config/WorkerMockConfigurationTest`; update the
  12 users of `MockWorkerTenantService` (`ReviewExpiryDispatchHandlerTest`, `ReviewExpiryJobHandlerTest`,
  `ReviewExpiryServiceImplTest`, `WorkerSuggestionJobQueueTest`, `EmailOutboxIT`, `ReviewNotificationEnqueueBugTest`,
  `ReviewNotificationEnqueuerTest`, `ReviewNotificationIT`, `TenantExecutionServiceImplTest`, `TenantJobServiceImplTest`,
  `WorkerJobTenantBindingTest`, `JobRunrTenantIT`) to the import and name of `FixedWorkerTenantService`; update
  `WorkerConfigurationTest`, `WebJobsIT`, `OutboxOnlyConfigurationTest`, `WebJobsConfigurationTest` (profile and
  `tsolve.worker.mock.*` / `TSOLVE_MOCK_ACTIVE_WORKSPACES`).

**Note for KAN-254 (Task 6), not decided here:** the adopted row's `db_user` is staging's app user, which owns the
tables; routing will connect with it, which conflicts with AC-001.32 (the runtime user must not own the tables). Task 6
(or Task 1's role split) gives bluefin a runtime user and updates the row.

**Note for identity Task 14 (KAN-247):** `claimEmail` has no caller until then (design R20). Its adapter for
`WorkspaceEmailRegistry` translates: identity's `claim` returns a boolean (`WorkspaceEmailRegistry.java:6`) while
`WorkspaceCatalog.claimEmail` returns void and throws `MSG155` (`WorkspaceCatalog.java:16`), so the adapter catches the
refusal and returns false; it also needs `release` (`UserCreationServiceImpl.java:151-154`), which the contract lacks
(a contract change by then). The backfill of bluefin's Account emails (lower-case `app_user` emails,
`INSERT … ON CONFLICT (email) DO NOTHING` for bluefin's id) is done at Task 14 time, in the same deploy as the adapter,
not in the adopt script: until the adapter is live `AcceptAllWorkspaceEmailRegistry` records nothing.

---

## Task 4a-1: `WorkspaceCatalog` over the Registry: lookups, the Active list and the email rule

**Story:** STORY-004
**Feature:** workspace-catalog
**Assignee:** Trần Trung Hiếu (KAN-98); Jira sub-task KAN-252
**After this task you can:** (plumbing; serves 4a-2, 4b, 4c and Tasks 7, 10 and 21) see in `WorkspaceCatalogIT` the lookups
by id, site name and cloud id, the Active list, and the email rule: one email in one Workspace, whatever the case. On
staging nothing changes yet: the catalog bean exists once `REGISTRY_DB_URL` is set, and the Registry has no row.
**Covers:** AC-004.17, AC-004.18, AC-004.25, AC-004.26, AC-004.27, AC-004.28 (no endpoint: asserted with Task 7's
source scan), AC-004.29.
**Design:** Registry rules; Key Decisions R1, R19; Interface Contracts.
**Description:** the contract files exist on `main` (`platform/service/WorkspaceCatalog.java`, `dto/WorkspaceSummary.java`,
`dto/WorkspaceStatus.java`); this task adds `findActive()` to `WorkspaceCatalog` and creates `RegistryRepository` and
`WorkspaceCatalogImpl`. `RegistryRepository`: `JdbcClient` on `registryJdbcClient`; only the reads and the claim:
`findById`, `findBySiteName` (on `lower(?)`), `findByCloudId`, `findByStatus(ACTIVE)`, `insertEmailIfAbsent`
(`ON CONFLICT (email) DO NOTHING`), `emailOwner(email)`. It has no `insert` and no `updateStatus`: the IT writes its
fixture rows with plain JDBC as the Registry user (as `RegistryChangelogIT.insertWorkspace` does), and Task 7 adds
`insert` and `updateStatus` with their tests (decided with Hiếu, Q1). `WorkspaceCatalogImpl` maps a row to
`WorkspaceSummary` with no connection data; `claimEmail` trims and lower-cases, takes the id from
`TenantContext.requireWorkspaceId()` (nothing bound: `NoTenantException`, `MSG06`, nothing recorded), inserts, then reads
the owner: the same Workspace succeeds, another throws `BusinessRuleException` `MSG155` on `email`; `linkJiraSite`
throws `UnsupportedOperationException` until Task 18. Both beans are registered inside the gated
`RegistryDataSourceConfig` (`registryJdbcClient` and `registryTransactionManager` are not autowire candidates, so the
config fetches the client by name). The catalog only reads `TenantContext` and never calls `runIn`. Decided in
planning: no cache; `ObjectFileStorage.signedUrl` already reads the catalog through an `ObjectProvider`.
**Not covered:** `claimEmail` with a null or blank email: not validated here; identity Task 14 precondition (PR #324 review Minor 4).
**Edge rows:** #26 (race), #76 (`claimEmail` with nothing bound).
**Tests (13 new, 2 updated):**
- `WorkspaceCatalogIT` (`PostgresTestBase`; fixture rows written with plain JDBC; the context has no business
  `DataSource`; a `TenantContext` fixture fails the test if the catalog calls `runIn`), 13 tests: (1) `findById` returns
  id, site name, status, Google domain, org id and Jira URL and cloud id, NULL columns map to empty, an unknown id is
  empty (AC-004.17); (2) `findBySiteName("BlueFin")` finds the row, an unknown name is empty (AC-004.17); (3)
  `findByCloudId` finds the row, an unknown or null cloud id is empty (AC-004.17); (4) no lookup returns connection
  data: `WorkspaceSummary` components are exactly the seven contract fields and no result's `toString()` holds the row's
  db name, user or password markers (AC-004.18); (5) `findActive` holds only ACTIVE rows and is empty for an empty
  Registry (AC-004.29); (6) `findActive` follows a status change (set by SQL) on the next call, no cache (AC-004.29);
  (7) the catalog never binds a Workspace: after each lookup, `findActive` and `claimEmail`, `currentWorkspaceId()` is
  unchanged (AC-004.27, AC-004.29); (8) a free email `" Ann@X.com "` is recorded as `ann@x.com` for the bound Workspace
  (AC-004.25); (9) the same email in another case for the same Workspace succeeds and changes nothing (AC-004.26); (10)
  the email in another Workspace gives `MSG155` on `email` and records nothing (AC-004.26); (11) race: two Workspaces
  claim one email in different cases at once (latch, 20 rounds): one row, the other `MSG155`, never
  `DataIntegrityViolationException` (Edge #26); (12) `claimEmail` with nothing bound throws `NoTenantException` and
  records nothing (Edge #76, AC-004.25); (13) a Registry failure (the `registryDataSource` closed) makes each lookup
  throw a `DataAccessException`, never an empty result: the catalog's part of Edge #80 (the existing
  `GlobalExceptionHandler.handleUnexpected` already answers 500 `MSG06`).
- No `WorkspaceCatalogImplTest`: its checks fold into `WorkspaceCatalogIT` (test 4) or are dropped (`linkJiraSite` refusing
  until Task 18 is no criterion).
- Update `RegistryChangelogIT`: the runner adds `AcceptingKnownWorkspaces` (a `TenantContext` bean, needed once the
  catalog bean exists); edge 72 also asserts a single `WorkspaceCatalog` bean; the blank-URL half of AC-001.25 edge 13
  also asserts there is no `WorkspaceCatalog` bean.
- Update `ObjectFileStorageIT`: its private `FakeWorkspaceCatalog` implements `findActive()`. QA's step adds
  `findActive()` to the `WorkspaceCatalog` contract so the tests compile.
**ITs to run:** `-Dit.test=WorkspaceCatalogIT,RegistryChangelogIT,ObjectFileStorageIT`, then `mvn spotless:check test`.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/registry/RegistryRepository.java`
- Create: `backend/src/main/java/vn/tsolve/platform/service/impl/WorkspaceCatalogImpl.java`
- Modify: `backend/src/main/java/vn/tsolve/platform/service/WorkspaceCatalog.java` (`findActive()`),
  `backend/src/main/java/vn/tsolve/platform/registry/RegistryDataSourceConfig.java` (the two beans, inside the gate)
- Test: `backend/src/test/java/vn/tsolve/platform/WorkspaceCatalogIT.java`; modify
  `RegistryChangelogIT.java`, `ObjectFileStorageIT.java`
**Dependencies:** Tasks 3a, 6a-1 and 6a-2 (merged); `be-foundation` Task 1 (`BusinessRuleException` with a code and a
field); the platform spec approved (AC-004.29)
**Estimated size:** small (4 production files, 13 new tests)
**Done when:** `WorkspaceCatalogIT` passes with the race case and the Active list; the PR says that on staging nothing
changes until 4a-2.

---

## Task 4a-2: Adopt bluefin: its Registry row, and its wiki files open again

**Story:** STORY-001
**Feature:** registry-adopt-bluefin
**Assignee:** Trần Trung Hiếu (KAN-98); Jira sub-task: new, key to be added by the orchestrator
**After this task you can:** run `infra/scripts/registry-adopt-workspace.sh` on the VPS and see bluefin's row in
`tenant_workspace` (`psql`: ACTIVE, the site name, the Google domain, the org id, `schema_version` = bluefin's last
changeset, a `v1:` password that no log shows), then **open a wiki file on bluefin**: `ObjectFileStorage.signedUrl`
fails today with "No Workspace catalog is available" and works as soon as the catalog bean and the row exist. A second
run changes nothing. (Add the wiki file view to the staging smoke.)
**Covers:** AC-001.15 (the row), AC-001.17 (no business data in the row), AC-001.19 (adopting is not creating; nothing
else writes a Workspace), AC-001.24 (the password is encrypted for this Workspace id only).
**Design:** Key Decisions R17, R18; Data Model — Tenant Registry DB.
**Description:**
- `RegistryPasswordCli`: a plain `main` with no Spring context, run with `java -cp app.jar -Dloader.main=… PropertiesLauncher`;
  reads `CATALOG_SECRET_KEY`, the Workspace id and `DB_PASSWORD` from the environment and writes only the `v1:` value
  to stdout (for the script's pipe); a missing variable is named and nothing secret is printed.
- `infra/scripts/registry-adopt-workspace.sh`, in the style of `registry-db-init.sh` and `seed-first-admin.sh` (reads the
  env file without sourcing it, prints no secret). Inputs: `TSOLVE_DEMO_WORKSPACE_ID`, the site name,
  `TSOLVE_DEMO_GOOGLE_DOMAIN`, `TSOLVE_DEMO_ORG_ID`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`. Reads `schema_version` from
  bluefin's `databasechangelog`. **Jira site (Q8):** it does not ask for it; it copies `jira_site_address` (written as
  `jira_site_url`) and `jira_cloud_id` from bluefin's tenant `workspace` table when that row exists, **both or neither**,
  the cloud id in lower case; with no tenant row it writes the row without a Jira site and prints one line saying so.
  It never writes a cloud id with no tenant row (else AC-004.23 refuses the Admin's Setup in Task 18). It inserts with
  `ON CONFLICT (workspace_id) DO NOTHING`; a row that exists with other values is refused, the field named, nothing
  changed.
- `infra/README.md` runbook line (staging and local), `infra/staging.env.example` (`TSOLVE_DEMO_WORKSPACE_ID`, script only).
**Edge rows:** #77 (second run changes nothing), #78 (a different existing row is refused, the field named), #79 (no tenant
row, no cloud id), #14 (no password in any output).
**Tests (2 Java, 6 script checks):**
- `RegistryPasswordCliTest` (2): the output decrypts with `PasswordCipher` for that Workspace id only (AC-001.24); a
  missing variable is named and nothing secret is printed (Edge #14).
- `infra/tests/registry-adopt.test.sh` (by hand with Docker; the output goes into the PR): A1 refuses without a required
  variable, names it, writes nothing; A2 writes the row (ACTIVE, the fields above, `schema_version` = last changeset, a
  `v1:` password that decrypts) and prints no password (AC-001.15, AC-001.24); A3 a second run changes nothing
  (Edge #77); A4 an existing row with other values is refused, the field named, nothing changed (Edge #78); A5 a tenant
  `workspace` row with `jira_site_address` and an upper-case `jira_cloud_id` gives both Jira columns, the cloud id in
  lower case, and a row with an address but no cloud id gives neither (Edge #79); A6 an empty tenant `workspace` table
  gives both columns NULL and one explaining line (Edge #79).
**ITs to run:** `-Dtest=RegistryPasswordCliTest` (a unit test), then `bash infra/tests/registry-adopt.test.sh`.
**Staging after merge:** the checklist above, then run the script on the VPS; smoke: sign-in, a wiki file view, `psql`
showing the row.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/platform/registry/RegistryPasswordCli.java`
- Create: `infra/scripts/registry-adopt-workspace.sh`
- Modify: `infra/README.md`, `infra/staging.env.example`
- Test: `backend/src/test/java/vn/tsolve/platform/registry/RegistryPasswordCliTest.java`, `infra/tests/registry-adopt.test.sh`
**Dependencies:** Task 4a-1 merged and deployed; Task 3b (the Registry database on staging)
**Estimated size:** small (2 production files and 2 docs, 2 tests and 6 checks)
**Done when:** `bash infra/tests/registry-adopt.test.sh` passes A1 to A6 and its output is in the PR; on staging the
row exists and a wiki file opens.

---

## Task 4b: Sign-in, Jira App and `/me` read the catalog; the Workspace stand-ins go

**Story:** STORY-004
**Feature:** catalog-lookups
**Assignee:** Trần Trung Hiếu (KAN-98); Jira sub-task: new, key to be added by the orchestrator
**After this task you can:** sign in on `bluefin.tsolve.io.vn` with bluefin found through its Registry row (and `/me`
answering `workspaceReady` from the real lookup), and, if the row has a cloud id, make a Jira App call that resolves to
bluefin. The env stand-ins `TSOLVE_STUB_WORKSPACES`, `TSOLVE_STUB_JIRA_SITES` and `TSOLVE_STUB_WORKSPACE_READY` no
longer exist. Staging smoke: sign-in on bluefin, a review email queued, a wiki upload and view, and a Jira App call only
if the row has a cloud id.
**Covers:** AC-004.17 (through the identity and jira-app ports), AC-002.14 (`runIn` accepts the catalog's id and refuses
another), be-foundation AC-003.4 (once AC-003.7 is retired, the only rule), jira-app AC-001.2 and AC-001.3 (traced
through the renamed tests below).
**Design:** Key Decisions R19; Registry rules.
**Description:**
- Create `identity/signin/port/PlatformWorkspaceLookup` (`@Profile("!dev")`, over `ObjectProvider<WorkspaceCatalog>`):
  `findBySiteHost` lower-cases the host, strips the port, gets the label through `TSOLVE_SITE_ORIGIN_TEMPLATE` (`{slug}`)
  and calls `findBySiteName`; `findById`; a `WorkspaceSummary` maps to `WorkspaceRef(id, host, googleDomain, status,
  slug = siteName, orgId or null)`; no catalog bean, or a host outside the template, answers empty; a Registry failure is
  not caught (Edge #80).
- Create `jiraapp/service/impl/CatalogJiraWorkspaceLookup`: `findByCloudId(cloudId).map(id)`; empty with no catalog or a
  null cloud id; a Suspended Workspace still resolves (the 403 of AC-006.4 is Task 7/10's). **It stays unconditional and
  takes `ObjectProvider<WorkspaceCatalog>`**: five jira-app services and `workspace.JiraAppTokenServiceImpl` inject
  `JiraWorkspaceLookup` in every context, and a required parameter would break every full-context test with the Registry
  gate off (Edge #83).
- `identity/signin/SignInConfig`: `KnownWorkspaces` stays over identity's `WorkspaceLookup`, which now reads the catalog
  outside `dev` (javadoc only). Delete `NoWorkspaceLookup`, `ConfigJiraWorkspaceLookup`, `StubWorkspaceStatusLookup`.
- Config: `application.yml` loses `tsolve.stub.workspaces` and `workspace-ready`; `.env.example` loses
  `TSOLVE_STUB_WORKSPACES`, `TSOLVE_STUB_JIRA_SITES`, `TSOLVE_STUB_WORKSPACE_READY` and the comment mentions (line 76
  too), and points to the local Registry and the adopt script; `infra/staging.env.example` loses `TSOLVE_STUB_WORKSPACES`;
  `infra/compose.staging.yml` makes `REGISTRY_DB_URL` and `CATALOG_SECRET_KEY` required (`:?`) in `backend` and `worker`
  (the code gate stays; Task 6 drops it); `infra/README.md`, `infra/keycloak/staging/README.md` (the org id lives in the
  Registry row), `e2e/README.md` (the READY line).
**Edge rows:** #80, #81, #83, #86; #87 is an accepted risk until Task 6 (no test here).
**Tests (15 new with #86 and the two pins, 8 updated or deleted):**
- `PlatformWorkspaceLookupTest` also holds `blankTemplateWithACatalogFailsStartup_edge86` (Edge #86): an
  `ApplicationContextRunner` with the real cycle (catalog, `TenantContext`, `KnownWorkspaces`, `WorkspaceLookup`) fails
  startup naming `TSOLVE_SITE_ORIGIN_TEMPLATE` when the template is blank or has no `{slug}`; it starts with a valid
  template; with no catalog bean nothing changes. A constructor-time check (circular reference) must stay red. Give
  the runners of existing ITs that create a `WorkspaceCatalog` bean the template property, as `CatalogLookupsIT` does.
- `PlatformWorkspaceLookupTest` (6): a host in any case and with a port maps every field; an unknown host, a host outside
  the template or a blank host is empty (Edge #81); a Suspended Workspace comes through as SUSPENDED; `findById` maps every
  field; with no catalog bean both methods are empty; a Registry failure propagates, never empty (Edge #80; the QA card
  checks that the existing handler turns it into 500 `MSG06` and adds the line to a filter or controller test if not).
- `CatalogJiraWorkspaceLookupTest` (4): keep the method suffixes `_AC_001_2` and `_AC_001_3` of
  `ConfigJiraWorkspaceLookupTest`, so that `sdd.ts trace jira-app` shows no test fewer: a known cloud id gives its id; an
  unknown or null one is empty; no catalog is empty; a Spring context with the Registry gate off has exactly one
  `JiraWorkspaceLookup` (Edge #83).
- `CatalogLookupsIT` (`PostgresTestBase`, one row; proves all lookups read the catalog), 3: `WorkspaceLookup.findBySiteHost`
  finds the row; `JiraWorkspaceLookup.workspaceOf(cloudId)` finds it; `TenantContext.runIn` accepts the row's id and
  refuses another (AC-002.14).
- `NoWorkspaceFallbacksTest` (1, updated): `REMOVED_NAMES` gets `NoWorkspaceLookup`, `ConfigJiraWorkspaceLookup`,
  `StubWorkspaceStatusLookup`, `TSOLVE_STUB_WORKSPACES`, `tsolve.stub.workspaces`, `TSOLVE_STUB_JIRA_SITES`,
  `TSOLVE_STUB_WORKSPACE_READY`, `tsolve.stub.workspace-ready`; the "(kept)" comment goes.
- Delete and update the others as listed under "Existing tests that name the removed stand-ins" (4b).
**ITs to run:** `-Dit.test=CatalogLookupsIT,TicketListRepositoryIT`, plus the sign-in and session ITs that start the full
context (`grep -l WorkspaceLookup` over `*IT.java`).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/identity/signin/port/PlatformWorkspaceLookup.java`,
  `backend/src/main/java/vn/tsolve/jiraapp/service/impl/CatalogJiraWorkspaceLookup.java` (QA step 2 adds both as
  unannotated skeletons throwing `UnsupportedOperationException("4b")`; the dev completes them)
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/SignInConfig.java`, `backend/src/main/resources/application.yml`
- Delete: `identity/signin/port/NoWorkspaceLookup.java`, `jiraapp/service/impl/ConfigJiraWorkspaceLookup.java`,
  `common/session/lookup/StubWorkspaceStatusLookup.java`
- Modify: `.env.example`, `infra/staging.env.example`, `infra/compose.staging.yml`, `infra/README.md`,
  `infra/keycloak/staging/README.md`, `e2e/README.md`
- Test: `backend/src/test/java/vn/tsolve/identity/signin/port/PlatformWorkspaceLookupTest.java`,
  `jiraapp/service/impl/CatalogJiraWorkspaceLookupTest.java`, `platform/CatalogLookupsIT.java`; update the tests listed above
**Dependencies:** Tasks 4a-1 and 4a-2 merged and deployed (the row exists); `be-foundation` spec re-approved (AC-003.7
retired)
**Estimated size:** medium (about 7 production files plus config and docs, 15-16 new tests, 8 updated). Size-rule
exception, kept as one task (QA answers Q1 = A): the production side stays within the rule; two of the extra tests are
one-method pins in existing classes (the Edge #83 full-context check and the Edge #86 startup check); one deploy removes
all three env names at once, so there is no deploy where the Jira App reads the catalog but sign-in still reads
`TSOLVE_STUB_WORKSPACES` (two sources of truth). The PR text explains the count.
**Skeletons (QA step 2, Q5 = A):** QA adds `PlatformWorkspaceLookup` and `CatalogJiraWorkspaceLookup` as unannotated
skeletons (no Spring annotation, no `@Profile`; constructors as above; methods throw `UnsupportedOperationException("4b")`)
so the tests fail for the real reason; the dev adds the annotations and the behaviour.
**Not covered here:** the callback's answer to a Registry failure (Task 10, Q3 = A); the single-database guard (#87, Task 6).
**Done when:** the three new test classes and `NoWorkspaceFallbacksTest` pass, the updated tests pass, and the staging
smoke above passes after the deploy.

---

## Task 4c: The worker reads the catalog; `worker-mock` goes

**Story:** STORY-004
**Feature:** catalog-worker-tenant
**Assignee:** Trần Trung Hiếu (KAN-98); Jira sub-task: new, key to be added by the orchestrator
**After this task you can:** on staging, see the worker container run a tracker suggestion job and send a review email
for bluefin, with the Workspace taken from the Registry: the profile `worker-mock` and the variables
`TSOLVE_MOCK_ACTIVE_WORKSPACES` and `TSOLVE_MOCK_SUSPENDED_WORKSPACES` no longer exist, and a Suspended Workspace gets
no job.
**Covers:** worker AC-002.12 (reworded: no real `WorkerTenantService` → startup fails), worker AC-003.1 and AC-003.3,
platform AC-004.29, AC-002.14, AC-002.15 (`runIn` through the holder), GBR-38 (a Suspended Workspace runs no job).
**Design:** Key Decisions R19; Edge rows.
**Description:** `CatalogWorkerTenantService` in `worker/service/impl`, `@Service` with
`@ConditionalOnProperty(prefix="tsolve.worker", name="enabled", havingValue="true")`, over `WorkspaceCatalog` and
`TenantContext`: `activeWorkspaceIds` comes from `findActive()` (a failure throws, never an empty list, worker
AC-003.3); `isWorkspaceActive` from `findById` with status ACTIVE; `enqueueWorkspaceId` returns a bound id only if it is
Active, on any thread, and when nothing is bound the single Active id, or empty when there is none or more than one
(the rule of Task 6a-2 and BUG-164; this replaces the earlier sentence of this task, "never the single Active one");
`enqueueWorkspaceId` also propagates a Registry failure (never empty); the constructor takes `WorkspaceCatalog` directly, so
without a catalog bean startup fails naming `WorkspaceCatalog` (R19, 24/11);
`runIn` and `currentWorkspaceId` delegate to `TenantContext`. A `WorkerTenantService` bean must never exist in the web
process (`OutboxOnlyConfiguration` and `WebJobsConfiguration` say so), hence the same condition as `WorkerConfiguration`;
on staging that is the worker container only. Delete `MockWorkerTenantService`, `WorkerMockConfiguration` and
`application-worker-mock.yml`; the mock moves to test sources as `vn.tsolve.worker.support.FixedWorkerTenantService` with
the same behaviour (the new name keeps `NoWorkspaceFallbacksTest`'s scan green); `WorkerTenantService` javadoc loses
"mock"; compose runs the worker with `SPRING_PROFILES_ACTIVE: worker`; `backend/Dockerfile` (comment),
`backend/README-worker.md`, `.env.example` and `infra/staging.env.example` lose the mock variables.
**Edge rows:** #82, #84, #85.
**Tests (12 new, 16 updated or deleted):**
- `CatalogWorkerTenantServiceTest` (10): `activeWorkspaceIds` equals the Active list and is empty when none (worker
  AC-003.1); it throws when the catalog cannot list (Edge #84, worker AC-003.3); `isWorkspaceActive` true for Active,
  false for Suspended or unknown, and it throws when `findById` fails; `enqueueWorkspaceId` throws the Registry failure
  too (unbound and bound); `enqueueWorkspaceId` with an Active Workspace bound returns it on a request thread and on
  a plain thread; with a Suspended one bound it is empty (Edge #82); with an unknown one bound it is empty; unbound with a
  single Active it returns that one (BUG-164); unbound with none or two it is empty; `runIn` refuses an unknown id
  (AC-002.14) and binds a Suspended one (AC-002.15); `currentWorkspaceId` is the holder's.
- `WorkerConfigurationTest` (2, replaces `WorkerMockConfigurationTest`): with the worker enabled and a catalog bean there is
  exactly one `WorkerTenantService`, the catalog one, and without a catalog startup fails (worker AC-002.12); with the web
  outbox only there is no `WorkerTenantService` bean (Edge #85).
- Update the 12 users of `MockWorkerTenantService` and the profile or property tests listed under "Existing tests that
  name the removed stand-ins" (4c). `JobRunrTenantIT`, `WebJobsIT` and `WorkerConfigurationTest` drop the profile and get
  a test `WorkspaceCatalog` bean.
- `WorkerJobTenantBindingTest` moves onto the catalog chain (not `FixedWorkerTenantService`): `CatalogWorkerTenantService`
  over a Mockito `WorkspaceCatalog`, the holder's `KnownWorkspaces` = `id -> catalog.findById(id).isPresent()`; the
  "two lists disagree" case becomes "a job for a Workspace the catalog does not know is refused and does not run".
- Not covered by a JUnit test: compose `SPRING_PROFILES_ACTIVE: worker` (manual `docker compose config`), doc wording, and
  the staging smoke.
**ITs to run:** `-Dit.test=EmailOutboxIT,JobRunrTenantIT,WebJobsIT,ReviewNotificationIT`.
**Staging after merge:** a tracker suggestion job and a review email sent by the worker.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/worker/service/impl/CatalogWorkerTenantService.java`; QA step 2 first adds it as
  an unannotated skeleton `(WorkspaceCatalog, TenantContext)` whose methods throw `UnsupportedOperationException("4c")`, so
  the tests compile and are red for the right reason; the developer adds the annotations and bodies
- Modify: `backend/src/main/java/vn/tsolve/worker/service/WorkerTenantService.java` (javadoc), `infra/compose.staging.yml`,
  `backend/Dockerfile` (comment), `backend/README-worker.md`, `.env.example`, `infra/staging.env.example`
- Delete: `worker/service/mock/MockWorkerTenantService.java`, `worker/config/WorkerMockConfiguration.java`,
  `backend/src/main/resources/application-worker-mock.yml`
- Test: `backend/src/test/java/vn/tsolve/worker/service/impl/CatalogWorkerTenantServiceTest.java`,
  `worker/support/FixedWorkerTenantService.java`, `worker/config/WorkerConfigurationTest.java`; update the tests listed above
**Dependencies:** Task 4b merged and deployed (compose requires the Registry variables in the worker); `worker` spec
re-approved
**Estimated size:** medium (1 production class plus deletions and config, 12 new tests, 16 updated)
**Done when:** the new tests and `-Dit.test=EmailOutboxIT,JobRunrTenantIT,WebJobsIT,ReviewNotificationIT` pass, and the
staging smoke above passes after the deploy.

---

## Task 5: Tenant changelog: `tenant_identity`, the row guard and the convention guard test

**Story:** STORY-001, STORY-003
**Feature:** row-guard
**Assignee:** Trần Trung Hiếu (KAN-98); Jira sub-task KAN-253
**After this task you can:** apply the tenant changelog to an empty database and see, with `\d+` and `pg_policies`, that every
table has the policy `tenant_guard` and that a runtime user sees no row until `app.tenant_id` equals `tenant_identity`.
**Covers:** AC-001.18 (tenant changelog), AC-001.20, AC-001.21, AC-001.22, AC-001.23, AC-001.27 (tenant part),
AC-001.28 (the table; Task 7 writes the row), AC-001.29, AC-001.30, AC-001.31, AC-001.32 (guard test side),
AC-003.10 (no policy that compares a `workspace_id` column).
**Design:** The row guard; Data Model — Workspace DB (changesets), Roles and grants, Convention guard test data;
Key Decisions R4, R5, R6.
**Description:** the three platform changesets in the tenant changelog: `tenant_identity` (singleton primary key),
`tenant_guard_ok()` (reads `public.tenant_identity` by its full name), and the last-run policy installer: an inline XML
changeSet at the end of the master (`runAlways`, `runOnChange`, `runOrder="last"`) that runs the SQL file
`053-platform-tenant-guard-policies.sql`, because Liquibase 4.27.0 reads `runOrder` only in XML. If a test shows that
`runOrder` does not reorder across includes, stop: the `TenantMigrator` fallback (Task 7) becomes its own task.
`ConventionGuardIT` with `ALLOW_LIST` and the fixed `LEGACY_ALLOW_LIST` (the 12 tables of AC-001.21 as changed 23/11);
`RowGuardIT` with a runtime user (neither owner nor `BYPASSRLS`, never granted TRUNCATE) made by a test-only fixture
(`TenantGuardDatabase`: the database owned by the app user, the runtime role and its grants as decision R6 says, the
`tenant_identity` row, then the revoke). The test-only table `isolation_probes` is created by the tests, not the
changelog. **Prerequisite (KAN-253, Q6):** `dashboard-audit` adds a one-line `COMMENT ON TABLE audit_log_entry IS
'tsolve:append-only'` changeset as a quick change before the guard test is written; it is not part of this task.
Staging does not change: Liquibase runs there as the table owner and there is no `FORCE`, so the guard filters nothing
until wave 4 (the three changesets do run at the next deploy; they fail only if a staging table has another owner).
**Edge rows (Critical/High):** #6 (runtime user cannot disable RLS, drop the policy or write `tenant_identity`),
#7 (role attributes), #8 (missing, renamed or second policy), #9 (empty setting is "unset"), #10 (no row or a second row
in `tenant_identity`), #12 (the allow-list only shrinks). #3 and #5 are in Task 6.
**Tests (12: 9 in `ConventionGuardIT`, 3 in `RowGuardIT`; the test plan is `prompts/handoffs/KAN-253-task-5-qa.md`):** guard fails for a scratch table with no policy, a policy named otherwise, another condition, a
second policy (AC-001.31, Edge #8); a table breaking AC-001.20 is named (AC-001.22); allow-list rule (Edge #12);
`id` defaulted by SQL and by JPA (AC-001.23); runtime user: no rows and refused writes when the setting is unset, empty
or another Workspace's, rows when it matches (AC-001.30, Edge #9); `tenant_identity` empty → no rows (Edge #10);
a malformed setting reads no row or raises 22P02, never a row (Edge #9, Q4); refusals in Edge #6, including a
`pg_temp.tenant_identity` that cannot shadow the table (Q5); superuser / `BYPASSRLS` / owner check (Edge #7, AC-001.32); no policy compares `workspace_id`
(AC-003.10).
**Files:**
- Create (3): `backend/src/main/resources/db/changelog/changes/051-platform-tenant-identity.sql`,
  `052-platform-tenant-guard-function.sql`, `053-platform-tenant-guard-policies.sql` (a plain SQL body, run by the
  XML changeSet; no formatted header)
- Modify (1): `backend/src/main/resources/db/changelog/db.changelog-master.xml` (includes for 051 and 052 after 050; the
  inline `053` changeSet last; the changeset owner is `be-foundation`, P1). Production total: 4 files
- Test: `backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java`, `RowGuardIT.java`,
  `TenantGuardDatabase.java` (the fixture), `backend/src/test/resources/db/changelog/guard-order-test.xml` (includes the
  main master, then one changeSet that creates a probe table, to prove the installer runs last)
**Wave order (22/11, KAN-384):** wave 2, item 3 — built and tested here, not switched on. It is switched on for
bluefin on staging in wave 4, after routing (Task 6).
**Dependencies:** Tasks 1 and 3 (init script, `PostgresTestBase`)
**Estimated size:** medium
**Done when:** both ITs pass and `isolation_probes` appears nowhere in `src/main`.

---

## Task 6a-1: One Workspace holder: `ThreadLocalTenantContext` for requests, Jira App calls and jobs (no routing)

**Story:** STORY-002
**Feature:** one-tenant-holder
**Assignee:** Trần Trung Hiếu (KAN-99); Jira sub-task KAN-384
**After this task you can:** (plumbing, wave 1 item 1; serves Tasks 4, 6, 10 and 6a-2; nothing visible) see on staging
that bluefin works as before — sign in on bluefin, upload a wiki file, run a tracker suggestion job — while the
request, the Jira App call and the job read their Workspace from the one holder.
**Covers:** AC-002.11 (holder side: the binding is restored in `finally`, so a request that ends with an exception
leaves none; the filter side stays in Task 10), AC-002.12, AC-002.13, AC-002.14 (against the interim Workspace list,
see the description), AC-002.15, AC-002.16.
**Design:** The binding; iteration-1 design "Wave order and Task 6a"; Key Decision P5 (comes with Task 6).
**Description:**
- **One holder.** `ThreadLocalTenantContext` (bean in `common/tenant`, implements the existing `TenantContext`, keeps
  the id in a static `ThreadLocal`; `@Primary`, so every injection of `TenantContext` gets it) is the only per-thread
  binding. `runIn` refuses another Workspace while one is bound and does not run the action (AC-002.13), refuses an id
  that `KnownWorkspaces` does not know (AC-002.14), binds, runs, and restores the previous binding in `finally`, also
  when the action throws (AC-002.12); a Suspended Workspace binds (AC-002.15). `requireWorkspaceId()` with none bound
  throws `NoTenantException` and logs the calling class and method (AC-002.16; the global handler answers 500 `MSG06`).
  No transaction in `runIn` and no package-private `bind`/`unbind` yet: they come with routing (Task 6) and the
  resolver (Task 10).
- **Delegates.** identity `TenantScope` is implemented by `PlatformTenantScope`, which calls `TenantContext`; it
  replaces `LocalTenantScope` (bean in `SignInConfig`; the ~30 callers keep their `runIn` calls). The worker's
  `MockWorkerTenantService.runIn` and `currentWorkspaceId` call `TenantContext`; its own `ThreadLocal` goes. Its
  request-thread check in `enqueueWorkspaceId` stays until Task 6a-2.
- **Who binds.** Requests: `WebSessionFilter` (`scope.runIn(session Workspace, chain)`, unchanged). Jira App calls:
  the cloudId path of `ConfigJiraWorkspaceLookup` (`tenantScope.runIn`, unchanged). Jobs: `TenantExecutionServiceImpl`
  → `WorkerTenantService.runIn` (unchanged; the job test covers it).
- **Kept until Task 6a-2.** `StubTenantContext` and `TSOLVE_STUB_WORKSPACE_ID` stay in the code: with the holder
  `@Primary` the stub is never injected, so its removal changes no behaviour; so do the `OutboxWorkspace` fallback,
  `TSOLVE_TRACKER_DEV_WORKSPACE_ID` and the other users of `TenantContext`. Kept until Task 4: the Workspace list on
  the env stubs (`TSOLVE_STUB_WORKSPACES`, exactly one entry, `TSOLVE_STUB_JIRA_SITES`, `TSOLVE_MOCK_ACTIVE_WORKSPACES`
  with the `worker-mock` profile). No database change, no routing, Registry, catalog or row guard.
- **Decided in planning (builder check):** `KnownWorkspaces` (`boolean exists(UUID)`, `common/tenant`) is the port of
  the existence check. Until Task 4 it is a bean in `SignInConfig` over identity's `WorkspaceLookup.findById`
  (`NoWorkspaceLookup` from `TSOLVE_STUB_WORKSPACES`; `DevWorkspaceLookup` in `dev`); both staging backends read that
  variable from the shared env file. Task 4 answers it from `WorkspaceCatalog.findById`.
- **Risk to check (QA card).** With the holder `@Primary`, code that reads `TenantContext` on a thread nobody binds
  (start-up, `@Async`, a scheduler) got the stub id before and throws `MSG06` now. Its users today:
  `ObjectFileStorage` / `StorageConfig`, `WikiUploadServiceImpl`,
  `common/session/lookup/{ViewableSpaceLookup,WrittenTicketLookup}`, `OutboxOnlyConfiguration`,
  `WorkerMockConfiguration`. The QA card checks that each is reached only from a bound request or job and hands the
  list to Task 6a-2, with a test for each user that runs on a thread with no binding (an unbound user would throw
  `MSG06` once the holder is `@Primary`). The staging smoke runs after the merge of 6a-1 (staging deploys from `main`);
  if it fails, the fix is one more commit on a new `fix/` branch, found by a debugger card.
**Edge rows (Critical):** #2 (a request throws after binding; the next request on the same thread starts unbound), #4
(`runIn(B, …)` inside A's request throws and the action does not run — also through identity `TenantScope`, since
there is one holder).
**Tests (about 12, four new classes):** `ThreadLocalTenantContextTest`: a different Workspace refused and the action
not run (AC-002.13, Edge #4); the same Workspace allowed, none bound → runs and restores (AC-002.12); restored after
an exception (AC-002.12, Edge #2); an unknown id refused (AC-002.14); Suspended allowed (AC-002.15); none bound →
`requireWorkspaceId` throws `NoTenantException` with the call site logged (AC-002.16). `PlatformTenantScopeTest`:
identity `runIn` / `currentWorkspaceId` and `TenantContext` read and write the same binding; `runIn(B)` through
`TenantScope` inside a `TenantContext` binding of A throws (Edge #4). `WorkerJobTenantBindingTest`: code inside a job
run by `TenantExecutionServiceImpl` reads `TenantContext` and identity `TenantScope` and sees the job's Workspace, and
none after the job (Edge #2). `WebSessionFilterTenantContextTest`: a request bound by `WebSessionFilter` is seen by
`TenantContext`; after a handler throws, the next request on the same thread sees none (AC-002.11 holder side,
Edge #2).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/tenant/ThreadLocalTenantContext.java`, `NoTenantException.java`,
  `KnownWorkspaces.java`; `backend/src/main/java/vn/tsolve/identity/signin/port/PlatformTenantScope.java`
- Modify: `backend/src/main/java/vn/tsolve/identity/signin/SignInConfig.java`;
  `backend/src/main/java/vn/tsolve/worker/service/mock/MockWorkerTenantService.java`,
  `worker/service/WorkerTenantService.java` (javadoc only)
- Delete: `backend/src/main/java/vn/tsolve/identity/signin/port/LocalTenantScope.java`
- Test: create `backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java`,
  `backend/src/test/java/vn/tsolve/identity/signin/port/PlatformTenantScopeTest.java`,
  `backend/src/test/java/vn/tsolve/worker/service/impl/WorkerJobTenantBindingTest.java`,
  `backend/src/test/java/vn/tsolve/identity/session/WebSessionFilterTenantContextTest.java`; delete
  `LocalTenantScopeTest`; update `MockWorkerTenantServiceTest` and the tests that build a `LocalTenantScope` double
  (`MockSignedInCallerFilterTest`, `UserListServiceImplTest`, `JiraAppQuestionServiceImplTest`,
  `ProvisioningProfileTest`)
**Dependencies:** none (wave 1, parallel with Task 3a, which shares `application.yml`, `.env.example` and
`infra/staging.env.example`: whoever merges second rebases and keeps the other's names)
**Estimated size:** medium — 7 production Java files (4 created, 3 modified) and 1 deleted; 4 new test classes, about
12 tests. Own branch and PR: `refactor/KAN-384-platform-one-tenant-binding` (branch name kept).
**Done when:** the four new test classes and the updated ones pass in the quick local checks and CI is green on the
PR; `LocalTenantScope` appears nowhere in `backend/`; after the merge the staging smoke on bluefin passes (sign-in, a
wiki upload, a tracker suggestion job).

---

## Task 6a-2: Remove the Workspace fallbacks: stub context, outbox Workspace, tracker dev id

**Story:** STORY-002
**Feature:** no-workspace-fallbacks
**Assignee:** Trần Trung Hiếu (KAN-99); Jira sub-task KAN-385
**After this task you can:** (plumbing, wave 1 item 2, after Task 6a-1 is merged; serves Tasks 4, 6 and 10) see on
staging that bluefin works as before — sign in on bluefin, queue a review email, run a tracker suggestion job, upload
a wiki file — with no Workspace id in the environment but the one entry of `TSOLVE_STUB_WORKSPACES`.
**Covers:** no criterion of its own. AC-002.12 – AC-002.16 are delivered by Task 6a-1; this task makes every
`TenantContext` user read that holder and none read a fallback, and its tests trace to the same ids.
**Design:** The binding; iteration-1 design "Wave order and Task 6a".
**Description:**
- **Removed.** `StubTenantContext` and `TSOLVE_STUB_WORKSPACE_ID` (`tsolve.stub.workspace-id`); the `OutboxWorkspace`
  fallback — the bean and the `RequestTenant` of `OutboxOnlyConfiguration` and its users `WebJobsConfiguration` and
  `ReviewNotificationEnqueuer` (on `main` since KAN-366 / KAN-370); `TSOLVE_TRACKER_DEV_WORKSPACE_ID`
  (`tsolve.tracker.dev-workspace-id`) — `WorkerSuggestionJobQueue` reads the bound Workspace from `TenantContext`; the
  request-thread check of `MockWorkerTenantService.enqueueWorkspaceId` (it existed only because the stub answered on
  every thread).
- **Moved to the bound Workspace.** Every remaining user of `TenantContext` — `ObjectFileStorage` / `StorageConfig`,
  `WikiUploadServiceImpl`, `common/session/lookup/{ViewableSpaceLookup,WrittenTicketLookup}`, `OutboxOnlyConfiguration`,
  `WorkerMockConfiguration`, `WebJobsConfiguration`, `ReviewNotificationEnqueuer`, the session lookups — is reached
  only from a bound request or job (the list from Task 6a-1's QA card); one that runs on an unbound thread gets its
  Workspace passed in or is bound by its caller. Most inject `TenantContext` by type and need no code change; the
  production files that change are the ones listed below.
- **Kept until Task 4.** The Workspace list on the env stubs, as in Task 6a-1. No database change.
**Edge rows (Critical):** none of its own (#2 and #4 are Task 6a-1). Check: no `TenantContext` user on an unbound
thread (start-up, `@Async`, a scheduler) is left, since the stub no longer answers there.
**Tests (about 8):** `OutboxOnlyConfigurationTest`, `WorkerMockConfigurationTest`: the beans start with no stub
Workspace and with none bound; `WorkerSuggestionJobQueueTest`, `ReviewNotificationEnqueueBugTest`: the Workspace comes
from the binding, not from an env id, and a queue call with nothing bound refuses (`MSG06`) instead of using a default;
`MockWorkerTenantServiceTest`: `enqueueWorkspaceId` always filters a bound id by Active on any thread (request or not),
and when unbound keeps the single-Active rule (returns the single Active id, else empty — required by
`ReviewNotificationEnqueueBugTest` BUG-164); a context test that no bean of
`StubTenantContext` or `OutboxWorkspace` exists and that the removed properties are read nowhere.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/worker/service/impl/ReviewNotificationEnqueuer.java`,
  `worker/config/OutboxOnlyConfiguration.java`, `worker/config/WorkerMockConfiguration.java`,
  `worker/config/WebJobsConfiguration.java`, `worker/service/impl/TenantExecutionServiceImpl.java` (only if it names a
  removed fallback), `worker/service/mock/MockWorkerTenantService.java`;
  `backend/src/main/java/vn/tsolve/tracker/service/impl/WorkerSuggestionJobQueue.java`;
  `backend/src/main/resources/application.yml` (and `application-worker*.yml` if they name a removed key);
  `.env.example`, `infra/staging.env.example`, `infra/README.md` (the removed names only); `infra/compose.staging.yml`
  only if it names a removed variable
- Delete: `backend/src/main/java/vn/tsolve/platform/service/impl/StubTenantContext.java`,
  `backend/src/main/java/vn/tsolve/worker/service/OutboxWorkspace.java`
- Test: delete `StubTenantContextTest`; update `OutboxOnlyConfigurationTest`, `WorkerMockConfigurationTest`,
  `WorkerSuggestionJobQueueTest`, `ReviewNotificationEnqueueBugTest`, `MockWorkerTenantServiceTest`
**Dependencies:** Task 6a-1 merged (wave 1; it may overlap with Task 3a / KAN-251, which shares `application.yml`,
`.env.example` and `infra/staging.env.example`: whoever merges second rebases and keeps the other's names)
**Estimated size:** medium — 7 production Java files modified and 2 deleted (9 touched, one over the 8-file rule; the
files of `TenantContext` users that need a real change, if the QA card of Task 6a-1 finds any, add to it: say so before
coding), 3 – 5 configuration files, about 8 tests. Own branch and PR: `refactor/KAN-385-platform-no-workspace-fallbacks`
(created after Task 6a-1 is merged).
**Done when:** the updated test classes pass in the quick local checks and CI is green on the PR; `StubTenantContext`,
`OutboxWorkspace`, `TSOLVE_STUB_WORKSPACE_ID` and `TSOLVE_TRACKER_DEV_WORKSPACE_ID` appear nowhere in `backend/`,
`.env.example` or `infra/`; staging still runs bluefin with one entry in `TSOLVE_STUB_WORKSPACES`; after the merge the
staging smoke on bluefin passes (sign-in, a review email queued, a tracker suggestion job, a wiki upload).

---

## Task 6: Tenant routing: one pool per Workspace, the tenant setting

**Story:** STORY-002
**Feature:** tenant-routing
**Assignee:** Trần Trung Hiếu (KAN-99); Jira sub-task KAN-254
**After this task you can:** (plumbing, wave 3 item 5; serves Tasks 7–10 and the wave-4 switch-on) run
`TenantRoutingIT` and see one JPA query go to Workspace A's database and another to B's, and a misrouted connection
read nothing.
**Covers:** AC-002.1 (`TenantContext` stays the one interface; its holder is Task 6a-1), AC-002.20 – AC-002.25,
AC-001.25 (the pool of a Workspace with a bad key is refused).
**Design:** The binding; Database routing; Key Decisions P5, R2, R3, R15.
**Description:** routing only — the binding itself is Task 6a-1. `TenantBinding` (the static holder that Task 6a-1's
`ThreadLocal` moves into, so non-bean code reads it), `runIn` of `ThreadLocalTenantContext` gains
`TransactionTemplate(PROPAGATION_REQUIRES_NEW)` (decision P5; see iteration-1 design "Open before coding" for
`WebSessionFilter`, which wraps the whole request in `runIn`), `TenantRoutingDataSource` (primary `DataSource`; throws
before opening a connection when none is bound; connection checkout outside a transaction sets and resets
`app.tenant_id`), `TenantPoolRegistry` (lazy Hikari pools, `TENANT_DB_POOL_MAX` default 5, `minimumIdle 0`),
`TenantAwareJpaTransactionManager` (`set_config('app.tenant_id', ?, true)` in `doBegin`), `TenantDataSourceConfig`
(JPA properties of R15: `allow_jdbc_metadata_access=false`, explicit dialect, `open-in-view=false`).
**Edge rows (Critical/High):** #3 (pooled connection of a finished transaction shows no row to a request with no
Workspace), #5 (injected misroute reads and writes nothing), #13 (wrong `CATALOG_SECRET_KEY`: only that Workspace
fails, others work), #14 (no password in any log line, log capture), #15 (25 Workspaces open pools below
`max_connections`), #17 (one Workspace database down, Registry down), #23 (Hibernate starts with nothing bound). #2 and
#4 are in Task 6a-1.
**Must close #87 (accepted Critical risk since Task 4b):** with routing in place, a second Registry row must reach only
its own database (edge #5 in `TenantRoutingIT`); the Task 6 hand-off repeats "one Registry row until routing; do not
insert a row by hand before Task 6".
**Tests (about 10):** `ThreadLocalTenantContextTest` extended: `runIn` runs the action in a new transaction (P5);
`TenantRoutingIT`: two Workspaces on two databases, no Workspace → throws before a connection (AC-002.21), pool size
and lazy open, two first requests open one pool, transaction stays on its database (AC-002.23), Edge #3, #5, #13, #14,
#15, #17, #23.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/tenant/TenantBinding.java`, `TenantRoutingDataSource.java`,
  `TenantPoolRegistry.java`, `TenantAwareJpaTransactionManager.java`, `TenantDataSourceConfig.java`
- Modify: `backend/src/main/java/vn/tsolve/common/tenant/ThreadLocalTenantContext.java` (Task 6a-1)
- Test: `backend/src/test/java/vn/tsolve/common/tenant/ThreadLocalTenantContextTest.java` (extend), `TenantRoutingIT.java`
**Dependencies:** Tasks 3a, 4, 6a-1 and 6a-2 (wave 3; the row guard of Task 5 is switched on after this task, in wave 4)
**Estimated size:** medium
**Done when:** `TenantRoutingIT` passes with a pool of one connection and with two databases.

---

## Task 7: Provisioning service: create a Workspace in one all-or-nothing saga

**Story:** STORY-013, STORY-001, STORY-004
**Feature:** provisioning-core
**Assignee:** Trần Trung Hiếu (KAN-240); Jira sub-task KAN-255
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
the last changeset id into `schema_version`; decide who keeps the adopted row's `schema_version` current after a deploy
(the adopt script sets it once)), `FirstAdminCreator` and `InvitationJobQueue` (ports, no implementation
here), `WorkspaceRuleException` (extends `BusinessRuleException`). A start-up check refuses the `ops` and Console profiles
without a bean for each port (AC-013.15). This task also adds `insert(row)` (password already encrypted) and
`updateStatus` to `RegistryRepository` (left out of Task 4a-1), with their tests.
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
- Modify: `backend/src/main/java/vn/tsolve/platform/registry/RegistryRepository.java` (`insert`, `updateStatus`)
- Test: `backend/src/test/java/vn/tsolve/platform/WorkspaceProvisioningIT.java`, `ProvisioningSourceScanTest.java`
**Dependencies:** Tasks 3a, 4, 5, 6; `be-foundation` Task 1. The real `FirstAdminCreator` (`identity`, KAN-247) and
`InvitationJobQueue` (`platform`, JobRunr; Task 8) are needed from Task 9 on.
**Estimated size:** medium
**Done when:** `WorkspaceProvisioningIT` passes including the compensation at every step and the race.

---

## Task 8: Invitation job queue: the minimal `InvitationJobQueue` on JobRunr in the Jobs database

**Story:** STORY-013, STORY-001
**Feature:** invitation-job-queue
**Assignee:** Trần Trung Hiếu (KAN-240; Jira sub-task KAN-256)
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
**Assignee:** Trần Trung Hiếu (KAN-240); Jira sub-task KAN-257
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
**Assignee:** Trần Trung Hiếu (KAN-99); Jira sub-task KAN-258
**After this task you can:** run the backend and `curl -H 'Host: acme.localhost' localhost:8080/api/v1/me` → 401 `MSG07` for a
real Workspace, 404 `MSG151` for an unknown one, 403 `MSG150` for a Suspended one.
**Covers:** AC-002.2 – AC-002.10, AC-002.11 (the filter unbinds when a request ends; the holder side is Task 6a-1), AC-002.17, AC-002.18, AC-002.19, AC-013.11, AC-005.7 and the host part of AC-005.8
(`/api/v1/jira-app/**` on any host but `api.` → 404 `MSG06`; the chain itself is Task 20).
**Design:** How a request or job gets its Workspace; Security chains (1); Key Decisions P5, R2.
**Description:** `PlatformProperties` (`tsolve.base-domain`, `tsolve.environment`), `HostClassifier` / `HostKind`,
`TenantResolverFilter` + `TenantFilterConfig` (order before Spring Security), `ProblemResponses`
(`application/problem+json`, `code`); `/actuator/health`, `/v3/api-docs*`, Swagger UI pass on any host (AC-002.19);
`admin.`, `login.`, `api.` bind nothing (AC-002.10).
**Edge rows (Critical/High):** #1 (forwarded headers and Workspace ids ignored), #2 (unbound after an exception), #18
(404 / 403 before any 401), #19 (host variants), #20 (health on `localhost:8080`). Medium: #22 (logs hold host and code only).
**From Task 4b (Q3 = A):** a Registry failure at the Keycloak callback (`/api/v1/auth/callback`) is answered here, since
the resolver runs before Spring Security and looks up the catalog first: with the Registry down the callback answers
`MSG06` (a test pins it); the body holds no JDBC URL (no `server.error.include-message` or `include-stacktrace`).
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
**Dependencies:** Tasks 4, 6a-1 and 6a-2 (wave 3 item 6, parallel with Task 6). `TenantIsolationIT` needs Task 6's two
databases, so that part merges after Task 6. Task 7 comes later (wave 5): the isolation test creates its two Workspace
databases with the test base, not through provisioning (wave order, 22/11).
**Estimated size:** medium
**Done when:** the three `curl` answers above are what the tests assert and `TenantIsolationIT` passes.

---

## Task 11: Local stack: the whole application behind Caddy on `*.localhost`

**Story:** STORY-008, STORY-009
**Feature:** local-compose
**Assignee:** Trần Trung Hiếu (KAN-94); Jira sub-task KAN-259
**After this task you can:** run `docker compose up`, create a Workspace with Task 9's script, and open
`http://acme.localhost` in a browser (and `login.`, `admin.`, `api.localhost` answering by their routing).
**Covers:** AC-008.1, AC-008.2, AC-008.3, AC-008.4 (tenant changelog applied to every Workspace database at startup of
the web process; the worker never runs Liquibase), AC-008.5, AC-008.6, AC-008.7, AC-008.9, AC-009.2, AC-009.4, AC-009.5,
AC-009.11 (local routing).
**Design:** Infrastructure — Local; Configuration; Key Decisions R14, R16.
**Description:** `docker-compose.yml`: add `backend`, `worker` (same image, `SPRING_PROFILES_ACTIVE=worker`, depends on
Redis and `garage-init`), `web`, `ollama` + one-shot `ollama-pull` (`bge-m3`, `OLLAMA_CHAT_MODEL`), `caddy`; health
checks; every credential from `.env`. `infra/caddy/Caddyfile.local`: `*.localhost`, `admin.localhost`, `login.localhost`,
`api.localhost` with the routing of the criteria, served on port 80 over HTTP only: site addresses written with the
scheme (`http://*.localhost`, …) or the global option `auto_https off`, because a bare `*.localhost` turns on automatic
HTTPS and redirects :80 to :443 (308), which breaks the signed URLs of Task 17 (`http://{site}.localhost`, no port). `application-worker.yml` (Liquibase off). `TenantStartupMigrator` (web
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
**Assignee:** Trần Trung Hiếu (KAN-97); Jira sub-task KAN-260
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
**Assignee:** Trần Trung Hiếu (KAN-10, KAN-11, KAN-12); Jira sub-task KAN-261
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
**Assignee:** Trần Trung Hiếu (KAN-13, KAN-95); Jira sub-task KAN-262
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
**Assignee:** Trần Trung Hiếu (KAN-14, KAN-200); Jira sub-task KAN-263
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
**Assignee:** Đặng Nhật Minh (KAN-96); Jira sub-task KAN-264
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
**Assignee:** Đặng Nhật Minh (KAN-96); Jira sub-task KAN-265
**After this task you can:** download a stored file in a browser from its signed URL on the Workspace site
(`http://acme.localhost/<bucket>/…`), on staging too; see a 405 for any other method and a download, not a rendered page,
for a stored HTML file.
**Covers:** AC-009.2 (the `/<bucket>/*` route on a Workspace site), AC-007.12 and AC-007.15 (through Caddy).
**Design:** Signed URL host and routing (R11, R12); Infrastructure — Edge limits. Research: RESEARCH-005 (with a draft
of the route).
**Description:** The Caddy route (GET/HEAD of `/{$FILE_STORAGE_BUCKET}/*` to `garage:3900` with the original `Host`, no
path rewrite, other methods 405 with `Allow: GET, HEAD`, `nosniff` by `header_down`) added to `Caddyfile.local` and
`Caddyfile`, before the web-app route. The `(common)` log switches to `format filter` (`wrap json`) deleting
`X-Amz-Signature` and `X-Amz-Credential` from `request>uri`. `FILE_STORAGE_BUCKET` is passed, required, to the `caddy`
service of `docker-compose.yml` and `infra/compose.staging.yml`.
**Edge rows:** #39 (through Caddy), #40 (signature not in the Caddy access log), #41 (HTML/SVG downloaded, not rendered).
**Tests (about 6 checks):** `infra/tests/caddy-routing.test.sh` additions. The object is put and the URL presigned with the
`amazon/aws-cli` image on the compose network (put on `http://garage:3900`, presign for `http://acme.localhost`, region
`garage`). `aws s3 presign` has no path-style flag, so the test mounts an AWS config file (`AWS_CONFIG_FILE`) with
`[default]`, `s3 =`, `  addressing_style = path`; without it the URL may be virtual-hosted (`<bucket>.acme.localhost`)
and the GET fails for a reason unrelated to the route. Checks: signed GET 200 with `nosniff`,
`application/octet-stream` and the same bytes (an HTML file, Edge #41); HEAD with that URL is passed through, not
served: not 405, refused by Garage with 400 or 403 (only `GetObject` is presigned and the signature covers the method;
design R11); PUT/POST/DELETE 405; an altered signature and an unsigned request refused with 400 or 403 and no content
(Edge #39, D16); no `X-Amz-Signature=` in `docker logs caddy` nor in `docker logs garage` (Edge #40).
**Files:**
- Modify: `infra/caddy/Caddyfile.local`, `infra/caddy/Caddyfile`, `docker-compose.yml`, `infra/compose.staging.yml`
- Test: `infra/tests/caddy-routing.test.sh`
**Dependencies:** Tasks 1 (Garage locally, no `root_domain`), 11 (local Caddy on port 80), 13 (Garage on staging), 14
(staging Caddy, `caddy-routing.test.sh`), 16
**Estimated size:** small
**Done when:** the signed-URL download works in a browser on a local Workspace site and `caddy-routing.test.sh` passes.

---

## Task 18: Link a Jira site to a Workspace

**Story:** STORY-004
**Feature:** jira-site-link
**Assignee:** Trần Trung Hiếu (KAN-98; used by UC-10 of `workspace`); Jira sub-task KAN-266
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
**Assignee:** Trần Trung Hiếu (KAN-105); Jira sub-task KAN-267
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
**Assignee:** Trần Trung Hiếu (KAN-105); Jira sub-task KAN-268
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
**Assignee:** Trần Trung Hiếu (KAN-101; contract agreed with Đặng Tấn Bình through the spec); Jira sub-task KAN-269
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
**Assignee:** Trần Trung Hiếu (KAN-203); Jira sub-task KAN-270
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

---

## Task 23: Cross-module tenant-isolation IT

**Story:** STORY-015
**Feature:** cross-module-isolation
**Assignee:** Phạm Tùng Dương (KAN-186)
**After this task you can:** run `mvn -Dit.test=CrossModuleIsolationIT verify` and see an ACME session read no BETA row
through the Ticket List, ticket detail and feedback, Search, Chatbot and Review Queue.
**Covers:** AC-015.1 – AC-015.7
**Design:** KAN-186, Part 1.
**Description:** One IT on `TwoTenantDatabases` with a Redis Testcontainer and a real ACME Workspace session; the same
data in `tenant_a` and `tenant_b`; AI clients mocked; row counts in `tenant_b` before and after each request.
**Tests (about 9):** one per AC-015.2 – AC-015.6, AC-015.3 for detail and feedback, AC-015.7 for each endpoint
(parameterised), all names citing the id; AC-015.1 in the class setup check (a request reaches `tenant_a`).
**Files:**
- Test: `backend/src/test/java/vn/tsolve/platform/isolation/CrossModuleIsolationIT.java` (and a fixture class beside it
  if the data set grows)
- No production code. If a test shows a leak, stop: it is a bug (log it in `docs/bug-log.md`), not a change here.
**Dependencies:** identity Task 26a (web session), search, chatbot, ticket-review APIs on `main`
**Estimated size:** medium
**Done when:** `mvn verify` passes with the IT and `sdd.ts trace platform` lists AC-015.1 – AC-015.7 as tested.

---

## Task 24: Playwright E2E smoke on the local stack

**Story:** STORY-015
**Feature:** e2e-smoke
**Assignee:** Phạm Tùng Dương (KAN-186)
**After this task you can:** start the local stack, run `cd e2e && npm ci && npm run e2e`, and open the HTML report.
**Covers:** AC-015.8 – AC-015.12
**Design:** KAN-186, Part 2.
**Description:** Standalone `e2e/` package; a global setup that checks the stack and writes the missing dev data; sign-in
through Keycloak and the fake Google as Alice; the five pages; ACME's cookie on the BETA host; the HTML report; the run
recorded in `artifacts/06-evaluation/platform/`.
**Tests (about 7):** `e2e/tests/smoke.spec.ts` (AC-015.9, one test per page), `e2e/tests/site-bound-session.spec.ts`
(AC-015.10), `e2e/global-setup.ts` check (AC-015.11) with a unit-style test that the missing-service message names the
service; test titles cite the id.
**Files:**
- Create: `e2e/package.json`, `e2e/package-lock.json`, `e2e/playwright.config.ts`, `e2e/global-setup.ts`,
  `e2e/fixtures/dev-data.ts`, `e2e/tests/*.spec.ts`, `e2e/README.md`, `e2e/.gitignore`
- Modify: root `.gitignore` only if `e2e/.gitignore` is not enough
**Dependencies:** Task 23 not required; the local stack of `README.md` (Keycloak, fake Google, Redis, db)
**Estimated size:** medium
**Done when:** `npm run e2e` passes on the local stack and the report of the run is in
`artifacts/06-evaluation/platform/task-24-e2e-smoke-test-report.md`.
