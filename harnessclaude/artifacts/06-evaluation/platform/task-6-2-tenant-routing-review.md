---
module: platform
feature: tenant-routing
task: 6-2
verdict: pass
attempt: 1
date: 2026-11-27
reviewer: tsolve-code-reviewer
iteration: 1
currentIteration: 1
---

# Review: platform — Task 6-2: tenant-routing (PR #360, KAN-254)

## Summary

Verdict **pass**. Reviewed range `97e1b7e0..2cd63d61` (26 files, +1696 / -61): `a250a47a` (plan edits) and
`2cd63d61` (tests, 9 production files, config). The primary `DataSource` is a `TenantRoutingDataSource` exactly when
the Registry is configured and the profile is not `dev`. It throws `NoTenantException` before any pool is asked when
nothing is bound. `TenantPoolRegistry` opens one Hikari pool per Workspace on first use (max `TENANT_DB_POOL_MAX`,
`minimumIdle` 0, idle 10 min); a pool that fails to open is not cached, and the log names the site name, never the
password. `app.tenant_id` is always the **bound** id: set transaction-local in `TenantAwareJpaTransactionManager.doBegin`,
and session-level with a `RESET` on close for a connection taken outside a transaction. `runIn` is still bind only;
`WebSessionFilter`, `bindRequest` and `RequestBinding` are unchanged; `TenantDataSourceConfig` is not `@Import`ed.
`RegistryPasswordCli verify` prints only `OK` or `FAIL`. The decided `dbHealthContributor`, `KC_DB_POOL_MAX_SIZE: "15"`
and `max_connections=300` are in place.

The developer's local verify was incomplete: the full unit run was stopped, and `CatalogLookupsIT` and
`WorkspaceCatalogIT` were not run. GitHub CI on the PR head `2cd63d61` covers it: all backend jobs pass (unit,
it-keycloak-a/b, it-other-a/b), and so do gitleaks and pr-title. The code has not changed since (HEAD = PR head, clean
tree).

Findings: **0 Critical, 2 Important, 6 Minor.** None blocks the merge. I1 is a one-line README fix that belongs in this
PR before the deploy.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Each criterion of the task is checked in code and by a test that asserts it (Stage 1 below) |
| Completeness | pass | Nothing MISSING. EXTRA: `spring.jpa.database: postgresql` (accepted, M3) |
| Correctness | pass | My targeted units: 31 run, 0 failures. CI on `2cd63d61`: every backend job green. No Critical finding |
| Integration | pass | `TenantContext` is unchanged. The port `TenantDataSources` sits in `common/`, which depends on no module. `findConnection` stays package-private (AC-004.18). `CatalogLookupsIT` and `WorkspaceCatalogIT` are unchanged and green in CI |

### Stage 1: spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-002.1 `TenantContext` stays the one interface | PASS | `ThreadLocalTenantContext` over `TenantBinding`; `runInOpensNoTransactionAndRoutingReadsTheBinding_AC_002_1` |
| 2 | AC-002.13 `runIn` of another Workspace refused | PASS | `ThreadLocalTenantContext.java:70`; `transactionStaysOnItsDatabase_AC_002_23` (refused inside a transaction, action not run) |
| 3 | AC-002.20 every JPA/JDBC/native query of the bound Workspace reaches its database | PASS | `TenantRoutingDataSource.getConnection`; `queriesReachTheBoundWorkspacesDatabase_AC_002_20_edge_87` (JPQL, native and `JdbcTemplate`, A and B); gate: `routingOnlyWithRegistryOutsideDevAndNeedsHost_AC_002_20_AC_008_5` |
| 4 | AC-002.21 unbound: throw before any connection, store nothing | PASS | `TenantBinding.require()` before `poolOf`; `nothingBoundStartsHealthyAndRefusesQueries_…` (port called 0 times, 0 backends, rows unchanged) |
| 5 | AC-002.22 lazy pool, at most `TENANT_DB_POOL_MAX` | PASS | `TenantPoolRegistry.java:41-66`; `poolOpensLazilyIsCappedAndShared_AC_002_22_edge_16` (0 backends at start, 5/0/600000, 8 concurrent ≤ 5 backends, shared first use) |
| 6 | AC-002.23 a transaction stays on its database | PASS | `transactionStaysOnItsDatabase_AC_002_23` (same database and same backend pid after the refused switch; commit) |
| 7 | AC-002.24 tenant setting = bound id, transaction-local, also outside a transaction | PASS | `TenantAwareJpaTransactionManager.doBegin`; `withTenant`/`resetAndClose`; `tenantSettingSetAndCleared_AC_002_24_edge_3` (pool of one; cleared afterwards, 0 rows with no setting) |
| 8 | AC-002.25 a misrouted connection reads and writes nothing | PASS | `misrouteReadsAndWritesNothing_AC_002_25_edge_5`: JPQL, native and JDBC read nothing in and out of a transaction; three writes refused with `42501`; B keeps 2 rows. Closes accepted risk #87 |
| 9 | AC-001.25 a bad key refuses only that pool, the log names the Workspace without the password | PASS | `TenantPoolRegistry.open` log line; `wrongKeyRefusesOnlyThatPool_AC_001_25_edge_13_edge_14` (DEBUG capture, 500 `MSG06`, A still works) |
| 10 | AC-008.5 a missing `TENANT_DB_HOST` stops startup and is named | PASS | `TenantDataSourceConfig.java:41-43`; test 4 (named, no secret in the trace) |

Edge rows: #3, #5, #13, #14, #15, #17, #20, #23 and #89 each have a test that asserts them
(`connectionBudgetStaysBelowMaxConnections_edge_15`, `verifyPrintsOnlyOkOrFailWithoutSecrets_edge_89`, the IT methods
above). `RegistryChangelogIT` edge #72 is updated: the primary is the router, and Liquibase runs on its own connection
to `DB_*`. Edge #15 is checked at the default pool size of 5. Staging runs 10 and fits 12 Workspaces, as accepted in
iteration-1 Q7.

### Points the card asked about

- **`spring.jpa.database: postgresql`** (`application.yml:25`): accepted. It applies with routing off too, and there it
  only names the dialect Hibernate would detect anyway (every environment is PostgreSQL). See M3.
- **`ProvisioningLock`**: confirmed. `ProvisioningLock.java:54` calls `dataSource.getConnection()` with nothing bound.
  With routing on, `NoTenantException` (a `RuntimeException`, not caught by its `SQLException` handler) escapes `take()`;
  no connection leaks. **It breaks nothing on `main` today.** The bean exists only in `ops` with the three Keycloak
  secrets, its constructor touches no database, and no production code calls `WorkspaceSignInProvisioner` yet (only
  tests). It breaks Tasks 7/9. Also, once bound, the advisory lock would be taken per Workspace database, so it would no
  longer serialise provisioning "across all processes". Task 7 should take it on the Registry pool (`registryDataSource`).
- **Other unbound paths to the primary**: none found. Startup runners: `DevAccountSeeder` is `dev` only (routing off);
  `MockUserListSeeder` and `RagEvalRunner` bind through `runIn`. Scheduled jobs are bound since Task 6-1. JobRunr,
  outbox and the web-jobs pools are private pools, not the router. `SessionExpiryPublisher` uses Redis. The Review
  Queue listener (`ReviewQueueEventServiceImpl.java:519-525`) takes its connection inside `runIn` and keeps it; the
  connection is wrapped and is reset on close. Boot's DataSource metrics unwrap the router through
  `DataSourceUnwrapper`, which swallows the exception. The full application is never booted with routing on, though
  (I2).
- **`db_name` into the JDBC URL** (`TenantPoolRegistry.java:56`): concatenated without validation. M1.
- **`infra/README.md` pre-deploy commands**: no secret reaches a command line or the output. Values are exported in a
  subshell and passed to `docker run` by name, with `--network none`; only `OK`/`FAIL` is printed. A failing `psql`
  inside `export X="$(…)"` is masked by `export`, but that fails closed (the CLI names `STORED_DB_PASSWORD`, which is
  not `OK`). `-cp /app/app.jar … PropertiesLauncher` matches the Dockerfile. Mistakes: I1 and M4.
- **`infra/tests/registry-db.test.sh` "init script did not create the Registry"**: not caused by this change. The
  harness setup starts `pgvector/pgvector:pg16` directly with the unchanged `infra/scripts/registry-db-init.sh` (not in
  the diff). The changed compose file is read only by S5, which passes. Probably the local Windows/Docker setup; not
  investigated.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace platform STORY-002 STORY-001` → **42/45 acceptance criteria have a test**. All
criteria this task covers are ✓:

```
✓ AC-001.25  PasswordCipherTest, RegistryChangelogIT, TenantRoutingIT
✓ AC-002.1   ThreadLocalTenantContextTest
✓ AC-002.13  NoWorkspaceFallbacksTest, TenantResolverFilterTest, ThreadLocalTenantContextTest, TenantRoutingIT
✓ AC-002.20  TenantPoolRegistryTest, TenantRoutingIT
✓ AC-002.21 … AC-002.25  TenantRoutingIT
```

The three ✗ are outside this task: AC-001.26 (identity), AC-001.34, and AC-002.18 (Task 10's `TenantIsolationIT`).

## Issues

### Critical
None.

### Important (do not block; I1 belongs in this PR)

1. `infra/README.md:127`: the post-deploy step restarts only `db`. `KC_DB_POOL_MAX_SIZE: "15"` takes effect only when
   `keycloak` is recreated, and CD recreates only `backend worker web` (`infra/scripts/deploy.sh:132`). So Keycloak
   keeps its default of 100, and the edge #15 budget (289 ≤ 297) does not hold on staging. No effect with one
   Workspace. **Fix:** `dc up -d --wait db keycloak`, then check `SHOW max_connections`.
2. No test boots the whole application (web, or the `worker` profile) with routing on. Every `@SpringBootTest` runs
   without a Registry, and the routing ITs load a chosen set of auto-configurations (`RoutingRunner`). So the first
   full boot with the router as the primary is the staging deploy. I read the likely startup paths (above) and found
   nothing that asks the router, so the risk is low, and the plan defers moving the ITs to production routing. **Fix:**
   in the README's after-deploy step, add the rollback line (`deploy.sh` logs "to roll back: redeploy <previous>") and
   check `backend` and `worker` health. Keep the full-boot IT as the planned later task.

### Minor

1. `TenantPoolRegistry.java:56`: `db_name` goes into the JDBC URL unvalidated, so a Registry value with `?`/`&` would
   add pgjdbc parameters. Rows are written only by trusted ops/provisioning code, and Task 7 plans the validation. Fix
   (Task 7, or here): check `^[a-z][a-z0-9_]{0,62}$` at this sink, or set the database through
   `PGSimpleDataSource.setDatabaseName` instead of the URL.
2. `TenantRoutingDataSource.java:45`: the comment says only the transaction manager sets the value inside a
   transaction. In fact `doBegin` takes the connection before Spring marks the transaction active, so a JPA
   transaction's connection is also wrapped (a session-level set, then a `RESET` on close). That is correct and safe
   (test 8), but costs two extra statements per transaction. Fix: correct the comment.
3. `application.yml:25`: `spring.jpa.database: postgresql` is global, outside the routing gate. With routing off it
   names a dialect Hibernate would detect anyway; Hibernate 6 may log HHH90000025 at startup. Fix: none needed. Say so
   in the PR text, or move it under the gate if the customizer's `DIALECT` already covers it.
4. `infra/README.md:105`: `$(git … remote get-url origin)` puts the origin URL on the `git fetch` command line. If the
   VPS origin is HTTPS with an embedded token, `ps` shows the token. Fix: fetch through a named remote, or make sure the
   origin uses the deploy key (SSH).
5. `TenantPoolRegistry.java:41`: `computeIfAbsent` holds its bin lock while Hikari connects, for up to
   `connectionTimeout`. One unreachable database can delay first uses of other Workspaces in the same bin. This is
   known (`shortcut:` comment) and fine at today's scale.
6. `TenantDataSourceConfig.java:62-63`: with `allow_jdbc_metadata_access=false`, Hibernate cannot read the server
   version and assumes the dialect's minimum. Fix (optional): set `jakarta.persistence.database-product-version` (or
   `hibernate.dialect.version`) to 16 in the same customizer.

### Proposed status

`tenant-routing` is `pending` in `progress.json`; the review sees no reason to hold it. Proposed: `passing` (through
`implementing → evaluating → passing`, done by the orchestrator; not changed here). The task's "Done when" still needs
Hiếu's steps: `verify` prints `OK` before the merge, and after the deploy and the `db` restart bluefin signs in.

### Assumptions made

- The developer's incomplete local verify is replaced by green CI on the same head `2cd63d61` (the card and the
  coordinator agreed). My local IT run was stopped on the coordinator's instruction.
- Severity of I2 is "Important, not blocking", because the plan defers the full-boot IT and the deploy can be rolled
  back.

## Test Report

See `task-6-2-tenant-routing-test-report.md`. Summary: `mvn -B test
-Dtest=ThreadLocalTenantContextTest,RegistryPasswordCliTest,TenantPoolRegistryTest` → Tests run: 31, Failures: 0,
Errors: 0, BUILD SUCCESS (my run). `gh pr checks 360` → backend, backend-tests (unit, it-keycloak-a, it-keycloak-b,
it-other-a, it-other-b), frontend, changes, gitleaks, pr-title: pass; frontend-checks and frontend-tests skipped (no
frontend change).
