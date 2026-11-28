---
module: platform
feature: startup-migrator
task: 11a
verdict: pass
attempt: 1
date: 2026-11-27
reviewer: tsolve-code-reviewer
iteration: 1
currentIteration: 1
---

# Review: platform — Task 11a: startup-migrator

## Summary
PR #364 (`origin/main...f0b4e480`, KAN-475). `TenantMigrator` applies the tenant changelog to every Registry row
(`ACTIVE` and `SUSPENDED`, `site_name` order) as the row's `db_user`, writes `schema_version` after each success and
stops at the first failure naming the site; `TenantStartupMigrator` runs it after `registryLiquibase`, removes Boot's
`liquibase` bean and makes `dataSource` depend on itself; the worker turns Boot's Liquibase off. Every criterion of the
task has a passing test, no Critical or High finding. One Medium finding needs a decision (`schema_version` is always
the id of the `runAlways` changeset 053). CI has not run on the PR yet, so the full suites are unproven.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-008.4, AC-008.9, AC-001.15 (schema_version part) met; see Stage 1 |
| Completeness | pass | No MISSING. EXTRA: the migrator also runs in any non-`dev`, non-`worker` profile (`ops`, later `seed`/`migrate`) — finding 5 |
| Correctness | pass | 17 unit + 20 IT targeted, 0 failures; spotless pass. Full suites: CI not run yet |
| Integration | pass | `RegistryRepository.findAllConnections` / `updateSchemaVersion` added; `tenantLiquibaseConnection` removed with no other user; `dataSource` / `liquibase` bean surgery checked against `RegistryChangelogIT` and `TenantRoutingIT` |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-008.4 catalog changelog first, then tenant changelog on every Workspace database as owner, in the web process | PASS | `TenantStartupMigrator.java:19` `@DependsOn("registryLiquibase")`; `TenantStartupMigratorTest.webRunsAllMigrationsAfterTheRegistryChangelog_AC_008_4` (in-order verify); `TenantMigratorIT.migratesEmptyDatabasesAsTheirOwner…` (table owner = row `db_user`, ACTIVE + SUSPENDED) |
| 2 | AC-008.4 the worker never runs Liquibase | PASS (tenant, catalog) | `application-worker.yml:1-3`; `RegistryDataSourceConfig` `shouldRun` false in `worker`; `TenantStartupMigratorTest.workerNeverRunsLiquibase_AC_008_4`. Jobs changelog still runs in the worker: BUG-413, accepted by the card |
| 3 | AC-008.9 failure on one database stops the web process, names the site, leaves the others | PASS | `TenantMigrator.java:51-60`; `TenantMigratorTest.stopsAtTheFailingWorkspaceAndNamesIt…`, `refusesAnUndecryptablePassword…`, `refusesAnUnsafeDatabaseName…`; `TenantMigratorIT.realFailureNamesTheSite…` (real wrong password, earlier db migrated, later db untouched, no secret in trace or output); `TenantStartupMigratorTest.failedMigrationStopsStartup_AC_008_9` |
| 4 | AC-001.15 `schema_version` = version last applied, written only on success | PASS (literal) — see finding 1 | `TenantMigrator.java:69-71, 86-89`; `TenantMigratorTest.writesLastChangesetIdAfterEachSuccess_AC_001_15`; `TenantMigratorIT` (stale value replaced, failed row keeps `stale-version`) |
| 5 | Card: bluefin's run is a no-op | PASS | `TenantMigratorIT.leavesAnAlreadyMigratedDatabaseUnchanged_AC_008_4` (id, author, filename, md5sum unchanged after a SpringLiquibase run on the same path) |
| 6 | Card: `db_name` allowlisted `[A-Za-z0-9_-]+` before the JDBC URL | PASS | `TenantMigrator.java:23, 64-66`; parameterized test with `?`, `/`, `&`, `%3F`, empty |

### Answers to the card's "Check in particular"

- **No test for R14 / blank `TENANT_DB_HOST`.** Confirmed: nothing asserts `dataSource`'s depends-on
  (`TenantStartupMigrator.java:34-36`) or the blank-host refusal (`TenantMigrator.java:41-43`). R14 holds anyway in
  practice: the migrator is a non-lazy singleton built during `refresh()`, and Tomcat, schedulers and
  `ApplicationRunner`s start only after it, so no request or job can reach a Workspace database before the migration
  ends. The depends-on only orders beans built during refresh. Low (findings 2, 3).
- **Gate on `TenantMigratorConfig`, also on in `ops`.** Safe for data today: no `ops` runner exists yet
  (`infra/ops/` and `OpsRunner` are not in the tree), Liquibase's lock serialises an `ops` run with a web start, and
  the changelog is idempotent. Two costs for Task 7: (a) one broken Workspace database (also a `SUSPENDED` one) makes
  every `ops` command fail at start, including the `suspend` that would take that Workspace out; (b) AC-008.4 names
  only the web process. Task 7 needs the `TenantMigrator` bean in `ops`, so keep the config gate and narrow only
  `TenantStartupMigrator` to `!dev & !worker & !ops` (finding 5) — decide in Task 7.
- **Worker started with or before the backend on a deploy with a new changeset.** `deploy.sh:132`
  `up -d --wait --no-deps backend worker web`: both services are selected, so the worker's `depends_on: backend:
  service_healthy` (compose.staging.yml:252-254) still holds (not tried here). The worker is recreated only after the
  backend is healthy, i.e. after every database is migrated. If the migration fails, the backend never becomes
  healthy, `up --wait` fails, and the old worker keeps running the old image against databases where the sites before
  the failure are already migrated: harmless for additive changesets, breaking for a rename or drop. If a new-image
  worker does start first (Docker restarting containers after a host reboot, or a hand-run `up worker`), it no longer
  migrates, so its jobs hit the old schema (missing column / table errors) until the backend finishes; JobRunr
  retries. Before this PR the worker ran the tenant changelog itself on `DB_*`, so this race is new but narrow.
- **First staging deploy, bluefin.** The row was written by `registry-adopt-workspace.sh:40-42` from `DB_NAME`,
  `DB_USER`, `DB_PASSWORD`, encrypted with `CATALOG_SECRET_KEY`, and `TENANT_DB_HOST` = `DB_HOST` = `db`. Startup stops
  only if `DB_PASSWORD` was rotated after adoption, `CATALOG_SECRET_KEY` changed, or `db_name` holds a character
  outside the allowlist. Since KAN-254 (#360) the runtime pools already connect with the same row credentials, so a
  bluefin that signs in today has a valid row. `db_user` = `DB_USER` is the user Boot's Liquibase ran as, so it owns the
  tables and the `runAlways` changeset 053 (policies, needs owner) runs as before. Healthcheck: `start_period` 90 s +
  18 × 10 s ≈ 270 s; bluefin is a no-op plus 053, a full empty database took about 2 s locally (TenantMigratorIT, 6
  databases in 11.9 s). A `databasechangeloglock` left by a killed run waits Liquibase's default 5 min, longer than the
  health window: CD goes red and the lock needs a manual release (true before this PR too).

## Traceability
```
✓ AC-001.15  infra/tests/registry-adopt.test.sh, infra/tests/registry-db.test.sh, backend/src/test/java/vn/tsolve/platform/tenantdb/TenantMigratorTest.java, backend/src/test/java/vn/tsolve/platform/tenantdb/TenantMigratorIT.java, backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
✓ AC-008.4  backend/src/test/java/vn/tsolve/platform/tenantdb/TenantMigratorTest.java, backend/src/test/java/vn/tsolve/platform/tenantdb/TenantStartupMigratorTest.java, backend/src/test/java/vn/tsolve/platform/tenantdb/TenantMigratorIT.java, backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
✓ AC-008.9  backend/src/test/java/vn/tsolve/platform/tenantdb/TenantMigratorTest.java, backend/src/test/java/vn/tsolve/platform/tenantdb/TenantStartupMigratorTest.java, backend/src/test/java/vn/tsolve/platform/tenantdb/TenantMigratorIT.java
trace platform (STORY-008, STORY-001): 21/31 acceptance criteria have a test (the other 10 belong to other tasks)
```

## Issues

CRITICAL (blocks pass): none.

Informational (does not block):

1. **Medium** — `TenantMigrator.java:86-89` (and `infra/scripts/registry-adopt-workspace.sh:191`): `schema_version`
   is always `053-platform-tenant-guard-policies`, because that changeset is `runAlways` + `runOrder="last"`
   (`db.changelog-master.xml:82-83`) and so always has the highest `orderexecuted`. It meets the card's wording ("last
   tenant changeset id") and AC-001.15 literally, but not the task's "deploy a merge that adds a tenant changeset and
   see … its id in that row's `schema_version`": the value never tells which version a database has.
   Fix (needs a decision; change both places together): take the highest `orderexecuted` among rows whose id is not a
   `runAlways` changeset (e.g. `WHERE id <> '053-platform-tenant-guard-policies'`, or the `runAlways` ids parsed from
   the changelog), pinned by a TenantMigratorIT assertion that `schema_version` equals the last non-`runAlways` id.
2. **Low** — `TenantStartupMigrator.java:34-36`: the R14 depends-on has no test, and `setDependsOn` replaces any
   depends-on `dataSource` already had. Fix: merge with `getDependsOn()`; assert in `TenantStartupMigratorTest` that
   `getBeanDefinition("dataSource").getDependsOn()` contains `tenantStartupMigrator`.
3. **Low** — `TenantMigrator.java:41-43`: the blank `TENANT_DB_HOST` refusal has no test. Fix: one unit test,
   `new TenantMigrator(…, " ", …)` throws naming `TENANT_DB_HOST`.
4. **Low** — `TenantStartupMigratorTest.java:89` and `:177`: the worker test's `if (context.containsBean("liquibase"))`
   assertion never runs (the bean is absent), and every test supplies the migrator with `withBean`, so the gate of
   `TenantMigratorConfig.java:13-14` is never exercised. Fix: assert `doesNotHaveBean("liquibase")` outright in the
   worker case, and add a runner with `TenantMigratorConfig` (no mock) for `worker`, `dev` and no Registry URL.
5. **Low** — `TenantStartupMigrator.java:18` / `TenantMigratorConfig.java:14`: the migrator runs in every profile but
   `dev` and `worker`, so in `ops` too (EXTRA against AC-008.4's "web process"). Fix in Task 7: `!dev & !worker & !ops`
   on `TenantStartupMigrator` only.
6. **Low** — `infra/scripts/deploy.sh:37, 132`: on a migration failure the CD log says only `FAILED at step 'up backend
   worker web'`; the site name is in the backend container's log, not the CD log that AC-010.5 later requires. Out of
   this task's files; for Task 15, print `docker compose logs --tail=100 backend` on that failure (TenantMigratorIT
   shows the failure output holds no secret).
7. **Info** — `backend/README-worker.md:9`: a worker started alone (`profiles=worker`) no longer creates the tenant
   schema, even without a Registry; the README does not say so (out of scope, the developer flagged it).

Security (STRIDE / OWASP, this diff): no Critical, High or Medium. SQL is constant or parameterised; `db_name` is
allowlisted before it enters the URL; `db_user` goes as a driver property, not into the URL; the decrypted password
goes only to `DriverManagerDataSource` and is not logged; failure messages carry the site name and the cause but no
secret (tested on a real wrong password, including captured output). The migrator connects as the database owner until
wave 4 (by design). No dependency added.

## Assumptions made
- "Owner user" of AC-008.4 = the row's `db_user` until provisioning / wave 4 (card decision, design.md "Startup
  migration").
- The Jobs changelog in the worker (BUG-413) does not count against AC-008.4 for this task (card decision).
- AC-010.5 is not in this task's Covers list; finding 6 is advice for Task 15, not a gap of this task.

## Test Report
`task-11-startup-migrator-test-report.md`: reviewer's targeted run 17 unit + 20 IT, 0 failures, spotless pass,
BUILD SUCCESS (01:37 min). Developer's verify (card): unit 3461/0, IT 53/0. `gh pr checks 364`: no checks reported —
the full suites have not run in CI yet; merge only after they are green.
Proposed status: `startup-migrator` is still `pending` in progress.json; move it `in-progress → evaluating → passing`
once CI is green (the card does not let the reviewer change it).
