---
module: platform
feature: registry-db
task: 3a
verdict: pass
attempt: 1
date: 2026-11-22
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: platform — Task 3a: Registry schema, password cipher and `registryDataSource`

## Summary

Reviewed the uncommitted tree on `feat/KAN-251-platform-registry-db` (8 production files, 3 QA test files; the plan edits in `harnessclaude/**` read only). No critical finding. Every AC of the task has a test that checks it, both targeted test classes pass on a fresh run, and the cipher and the startup refusal never put a secret, ciphertext or cause chain in a message. One important finding: the shipped Registry DDL drops several constraints and defaults that the design lists, with no recorded decision (I1); settle it before the commit, because a merged changeset cannot be edited. Proposed status: `passing` after I1 is decided. No feature status was changed.

Scope check: `RegistryRepository`, `infra/scripts/registry-db-init.sh`, `compose.staging.yml`, `staging.env.example`, `infra/README.md` are all absent (3b / Task 4). No existing test file is modified (`git status` shows only new test files); `pom.xml` is unchanged.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC rows below all PASS. Gate, owner-run changelog, worker skip, primary DataSource and tenant Liquibase kept. |
| Completeness | pass | No MISSING, no EXTRA. Two plan-text gaps (JdbcClient, I1 DDL deltas) are informational/important, not spec criteria. |
| Correctness | pass | `PasswordCipherTest` 15/15, `RegistryChangelogIT` 9/9 on my run. No CRITICAL code-review finding. |
| Integration | pass | Beans are not autowire candidates (same trap as `EmailOutboxConfiguration`); `liquibase` bean and primary DataSource proven by `keepsPrimaryDataSourceAndTenantLiquibase_edge_72`. |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.15 `tenant_workspace` columns, keys, unique, status check | PASS | `C001-...sql:5-19`; `createsWorkspaceTable_AC_001_15` checks exact column set, types, nullability, PK, 4 unique, status CHECK (23514), NOT NULL (23502) |
| 2 | AC-001.16 `workspace_email` lower-case PK, FK | PASS | `C002-...sql:5-8`; test checks PK, NOT NULL FK, 23514 / 23505 / 23503 |
| 3 | AC-001.17 no business data, only allowed tables | PASS | `holdsOnlyTheAllowedTables_AC_001_17` (tables, no view/sequence, column sets) |
| 4 | AC-001.18 two changelogs, a changeset in exactly one | PASS | `catalog-master.xml`; test asserts every filename holds `db/changelog/catalog/` and the tenant master does not mention `catalog/` |
| 5 | AC-001.19 empty, second start changes nothing | PASS | `appliesToEmptyRegistryWithNoRowsTwice_AC_001_19` |
| 6 | AC-001.24 AES-GCM, never logged | PASS | `PasswordCipher.java:42-56`; round trip, `v1:`, size 12+n+16, AAD binding test; no logger in the class |
| 7 | AC-001.25 missing key refuses, names variable, no secret | PASS | `PasswordCipher.java:37-38`, fixed messages at `:54`, `:73`; 7 bad-value and 6 bad-key cases, `hasNoCause()`; IT `missingKeyRefusesStartupNamingVariable` checks stack trace holds `CATALOG_SECRET_KEY`, not the password or the key. (Naming the failing Workspace is the caller's, Task 5/6.) |
| 8 | AC-001.27 Registry part on Testcontainers as owner | PASS | `PostgresTestBase` runs the real init script; test checks `tableowner` = Registry user |
| 9 | AC-001.33 catalog changelog creates nothing in Jobs DB | PASS (weak) | `createsNothingInTheJobsDatabase_AC_001_33` compares Jobs DB snapshot before/after; it can only prove the Registry run does not reach Jobs, which is all the catalog side can show |
| 10 | AC-008.4 web applies catalog changelog as owner; worker never | PASS | `RegistryDataSourceConfig.java:64`; web test and `workerNeverRunsRegistryLiquibase_AC_008_4` (no `databasechangelog`, no table, bean still present) |

## Traceability

```
trace platform (STORY-001): 9/20 acceptance criteria have a test
  ✓ AC-001.15, .16, .17, .18, .19, .24, .27, .33 -> RegistryChangelogIT / PasswordCipherTest
  ✓ AC-001.25 -> PasswordCipherTest, RegistryChangelogIT
trace platform (STORY-008): 1/11 acceptance criteria have a test
  ✓ AC-008.4  RegistryChangelogIT
```

All 10 criteria of Task 3a are covered. The other 11 STORY-001 criteria and 10 STORY-008 criteria belong to later tasks.

## Issues

Critical: none.

### Important

I1. `backend/src/main/resources/db/changelog/catalog/C001-registry-tenant-workspace.sql:5-19` — the DDL differs from the design's "Tenant Registry DB" block (`design.md` lines ~100-125, which the same plan edit renamed to `tenant_workspace`) and the difference is not recorded anywhere: `site_name VARCHAR(63)` and the `ck_tenant_workspace_site_name` CHECK `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$` are missing (shipped: `VARCHAR(255)`, no check); `workspace_id DEFAULT gen_random_uuid()` and `created_at/updated_at DEFAULT now()` are missing; `db_user`, `db_name`, `jira_cloud_id`, `keycloak_organization_id` lengths differ; constraints are unnamed (`uq_tenant_workspace_*` in the design). The spec is silent on all of it and the tests accept either, so this is not a spec failure. It matters because the site name is the host label the Tenant Resolver trusts (AC-002.2, AC-004.17 "without case"): without the check, `Acme` and `acme` are two rows. A merged changeset cannot be edited (AGENTS §7); a fix then costs a new changeset. Fix: either add the design's check, defaults and names to C001 (the 9 IT cases still pass: their site names match the regex, the null case still raises 23502), or change the design to the shipped DDL and say why in the plan's decision log. Needs Hieu's call.

### Minor

M1. `backend/src/main/java/vn/tsolve/platform/registry/RegistryDataSourceConfig.java` — the plan's Description (`tasks.md` Task 3a) and design R1 list a `JdbcClient` bean; none is defined and no test needs it. Fix: add `JdbcClient.create(registryDataSource)` as a non-candidate bean, or drop it from the Task 3a text and leave it to Task 4. Note for Task 4: `registryTransactionManager` is deliberately not an autowire candidate, so `@Transactional` without a name still uses the JPA manager; Task 4 must name it (`@Transactional("registryTransactionManager")`) or use a `TransactionTemplate`.

M2. `backend/src/test/java/vn/tsolve/support/PostgresTestBase.java:27,44` and `RegistryChangelogIT.java:370` — the init script's own Registry part is only proven by the role existing: `REGISTRY_DB` / `registryUrl()` (the database the script creates) is never used; every test migrates a database the superuser creates with `OWNER registry_user`. Fix: in the AC-001.27 test also assert `SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = 'tsolve_test_registry'` equals the Registry user, and optionally run the changelog on `registryUrl()` once.

M3. `backend/src/main/java/vn/tsolve/platform/registry/PasswordCipher.java:28` — nothing stops `CATALOG_SECRET_KEY` equal to `WORKSPACE_SECRET_KEY`. Both ciphers use `v1:`, the Workspace id as AAD and the same layout, so under a shared key a Jira-token ciphertext would decrypt as a database password. `.env.example` already says "a separate key". Informational; a startup check is optional (it would need the workspace key in the same bean).

M4. `infra/postgres/init/10-tsolve-dbs.sh:22` — an unset `REGISTRY_DB_USER` ends in the bare shell message "parameter not set" (see the init-script judgment below). Optional fix: `: "${REGISTRY_DB_USER:?set REGISTRY_DB_USER ...}"` lines before the `create_db` calls so the message names what to set.

### Judgment call: init script and unset `REGISTRY_DB_*` until 3b

Keep the hard failure; do not make the script skip the Registry when the variables are unset. The script runs only on an empty volume, and the existing staging volume never re-runs it, so nothing breaks on the next deploy. A skip would hide the gap exactly where it is expensive: a fresh volume would come up with no Registry and the script would never run again (the 3b script is the only repair). A hard failure is loud on day one. Condition: 3b (compose passing `REGISTRY_DB_*` with `:?` guards) merges before any fresh-volume deploy; `deploy.sh` already treats `postgres/init/` changes as a manual deploy (`MANUAL_RE`), so the change is not applied silently. Say this in the 3a PR text.

### Security review (cipher, secrets in messages, startup refusal)

- Messages: `PasswordCipher` throws only fixed strings, drops every cause (`catch ... { throw new IllegalStateException("...") }` without `exception`), has no logger. The key is read with `environment.getProperty`, so a bad value is never echoed by a binding error; an unresolved `${CATALOG_SECRET_KEY}` placeholder raises `IllegalArgumentException`, which is caught and replaced by the variable-naming message. Confirmed by the IT: stack trace contains the variable name and neither the key nor the password.
- Algorithm: AES-256-GCM, 128-bit tag, 12-byte `SecureRandom` nonce per value, AAD = Workspace id, key must decode to exactly 32 bytes. Same construction as `TokenCipher`. No finding.
- No secret in committed files: `application.yml` keeps `${VAR}` placeholders; `.env.example` values are empty; `CATALOG_SECRET_KEY` has no default. The key and password defaults empty only for `REGISTRY_DB_URL/USER/PASSWORD` behind the URL gate (same pattern as `StorageConfig`).
- Test logs: grep of both Maven logs for the test password and key finds nothing.
- Hikari: `setPassword` only; no pool configuration is logged with the password.
- No Critical/High security finding.

## Test Report

Fresh run in `backend/` on the uncommitted tree (Docker up):

- `mvn test -Dtest=PasswordCipherTest` -> `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS.
- `mvn test -Dtest=RegistryChangelogIT` -> `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` (16.4 s), BUILD SUCCESS.
- `sdd.ts trace platform STORY-001` -> 9/20; `STORY-008` -> 1/11 (AC-008.4 covered).
- Developer's verify (card, not re-run): `mvn spotless:check test` 3208 tests, 0 failures, BUILD SUCCESS. The code did not change after it: `git status` shows only the Task 3a files. Full backend and frontend suites run in CI on the PR (not pushed yet; `gh pr checks` not applicable).

Full detail: `task-3a-registry-db-test-report.md`.
