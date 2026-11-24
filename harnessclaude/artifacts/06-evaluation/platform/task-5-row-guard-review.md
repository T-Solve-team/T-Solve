---
module: platform
feature: row-guard
task: 5
verdict: pass
attempt: 1
date: 2026-11-24
reviewer: tsolve-code-reviewer
iteration: 1
---

# Review: platform — Task 5: row-guard

## Summary
Independent evaluation of PR #322 (`60594a7c..cd001049`, branch `feat/KAN-253-platform-row-guard`, 14 files changed, +1324/-38) for module `platform`, feature `row-guard` (KAN-253, `STORY-001` & `STORY-003`).

**Verdict: PASS** (attempt 1). All 12 acceptance criteria in Task 5's scope (`AC-001.18`, `AC-001.20`–`.23`, `AC-001.27`–`.32`, `AC-003.10`) and all 6 Critical/High design edge cases (`#6`, `#7`, `#8`, `#9`, `#10`, `#12`) are implemented faithfully against the approved spec (`7f8f7b7e`) and verified by real PostgreSQL 16 integration tests. Targeted ITs (`ConventionGuardIT`, `RowGuardIT`: 17/17 pass) and PR #322 GitHub CI (`cd001049`: all required jobs green) pass with zero CRITICAL code-review findings and zero Critical/High security findings. Four non-blocking informational hardening notes are recorded below.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `tenant_identity` singleton PK/CHECK (`051`), fail-closed `tenant_guard_ok()` reading `public.tenant_identity` (`052`), `runAlways`/`runOnChange`/`runOrder="last"` XML policy installer (`053` + `<rollback>`), and `'tsolve:append-only'` table comment on `audit_log_entry` (`054`, approved exception) satisfy every Task 5 AC and edge case. |
| Completeness | pass | All 12 Task 5 ACs and edge rows `#6, #7, #8, #9, #10, #12` are covered by assertions in `ConventionGuardIT` and `RowGuardIT`. `isolation_probes` is absent from `backend/src/main`. |
| Correctness | pass | Fresh targeted IT run on `cd001049`: 17/17 pass (`ConventionGuardIT` 9/9, `RowGuardIT` 8/8). Developer verify on `cd001049` (3,242 unit + 44 ITs) and PR #322 CI (all required suites + `gitleaks`) are green. 0 CRITICAL findings. |
| Integration | pass | `053` resolves `db/changelog/changes/053-platform-tenant-guard-policies.sql` from classpath root (compatible with `ReviewStorageIT` temp-dir master re-inclusion), reorders after later included changesets (`guard-order-test.xml`), rolls back cleanly before `052`/`051` (`ChatbotRatingSchemaIT`, `WorkspaceInfoRepositoryIT`), and leaves RLS non-`FORCE` so owner-mode staging is unaffected until wave 4. |

## Stage 1 — Spec Compliance

| # | Criterion (AC id) | Status | Evidence |
|---|-------------------|--------|----------|
| 1 | `AC-001.18` (tenant changelog separation) | PASS | `db.changelog-master.xml:75-103`; `ConventionGuardIT.platformChangesetsAreInTheTenantChangelogOnly_AC_001_18` checks `051`/`052`/`053` presence, `053` as last element with `runAlways`/`runOnChange`/`runOrder="last"`, zero `platform:05*` in `catalog-master.xml`, and disjoint `author:id` sets across changelogs. |
| 2 | `AC-001.20` (tenant-table convention) | PASS | `054-platform-audit-log-entry-append-only.sql:6`; `ConventionGuardIT.conventionViolations` (`:492-536`) enforces singular `snake_case`, `<table>_id` UUID PK + `DEFAULT gen_random_uuid()` (join-table composite PK exemption), no `workspace_id` except `workspace.workspace_id`, `created_at`/`updated_at` as `TIMESTAMPTZ NOT NULL`, and no `updated_at` on `tsolve:append-only` tables. Negative scratch tests + real changelog check pass (`:207-275`). |
| 3 | `AC-001.21` (allow-list exact match) | PASS | `ConventionGuardIT:49-78` matches spec verbatim: 3 permanent (`databasechangelog`, `databasechangeloglock`, `tenant_identity`) and 12 legacy tables (`legacy_workspace`, `workspace`, `department`, `app_user`, `user_role`, `space`, `jira_account_links`, `import_batch`, `import_preview`, `import_preview_row`, `project`, `suggestion_result`). |
| 4 | `AC-001.22` (convention guard & shrinking allow-list) | PASS | `ConventionGuardIT:207-312, 539-550` fails with table name + missing part for non-allow-listed violators, fails on added entries (`FIXED_LEGACY_SET.containsAll(LEGACY_ALLOW_LIST)`), and fails with `"the allow-list only shrinks"` for conforming (`scratch_fine`) or missing (`no_such_table`) allow-list entries. |
| 5 | `AC-001.23` (DB + JPA UUID key generation) | PASS | `ConventionGuardIT.keysAreGeneratedByTheDatabaseAndByJpa_AC_001_23` (`:317-364`) verifies `DEFAULT gen_random_uuid()` on every non-legacy non-join table and `@GeneratedValue(strategy = GenerationType.UUID)` on every matching `@Entity` under `vn.tsolve` (verified all 25 entities in `src/main`; `@Subselect` `SearchTicket` and join-table `ProjectMembership` properly skipped). |
| 6 | `AC-001.27` (Testcontainers PG, owner applies changelog) | PASS | `TenantGuardDatabase.java:45-82` uses shared `PostgresTestBase` (`pgvector/pgvector:pg16`) and runs Liquibase as non-superuser owner `APP_USER`; tested by `ConventionGuardIT` and `RowGuardIT`. |
| 7 | `AC-001.28` (`tenant_identity` singleton & runtime read-only) | PASS | `051-platform-tenant-identity.sql:7-10` (`singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton)`, `tenant_id UUID NOT NULL`); `RowGuardIT:40-77` proves 2nd row (`23505`), `singleton = false` (`23514`), runtime SELECT returns `A`, runtime INSERT/UPDATE/DELETE/TRUNCATE fail (`42501`), and empty `tenant_identity` fails closed (Edge #10). |
| 8 | `AC-001.29` (RLS + `tenant_guard` on every tenant table) | PASS | `052-platform-tenant-guard-function.sql`, `053-platform-tenant-guard-policies.sql`, `db.changelog-master.xml:80-103`; `ConventionGuardIT:129-138` confirms RLS + single `PERMISSIVE FOR ALL` policy `tenant_guard` (`USING (tenant_guard_ok()) WITH CHECK (tenant_guard_ok())`) on all public tables (including all 12 legacy tables) except `tenant_identity` and Liquibase tables; `guard-order-test.xml` + `:184-202` prove `runOrder="last"` reorders across includes and replaces idempotently. |
| 9 | `AC-001.30` (fail-closed reads and writes) | PASS | `RowGuardIT:79-149` proves unset, empty string, `SET`+`RESET`, wrong UUID B, and malformed (`not-a-uuid`, returning `0` or `22P02` per Q4) yield 0 rows / refused `INSERT` (`42501` or `22P02`) / 0 updated / 0 deleted, while owner still sees unchanged `'probe'`; matching `app.tenant_id` reads and writes cleanly inside a transaction (`set_config(..., true)`). |
| 10 | `AC-001.31` (guard check catches missing/wrong/extra policy) | PASS | `ConventionGuardIT:143-179, 428-489` proves scratch tables with no policy, renamed policy (`other`), wrong condition (`true`), and second policy (`second`) are each flagged by table name and specific reason; `guardViolations` also catches disabled RLS (`relrowsecurity = false`). |
| 11 | `AC-001.32` (role attributes & table ownership) | PASS | `ConventionGuardIT:369-402` verifies `REGISTRY_USER`, `JOBS_USER` (from real `10-tsolve-dbs.sh` in `PostgresTestBase`), and `db.runtimeRole` have `rolsuper = false`, `rolbypassrls = false`, and own no `tenant_guard`-protected table. |
| 12 | `AC-003.10` (no `workspace_id` row policy) | PASS | `ConventionGuardIT:406-423` checks every row in `pg_policies` in `public`, asserting `count > 0`, `policyname = 'tenant_guard'`, and neither `qual` nor `with_check` mentions `workspace_id`. |

Note on `EXTRA`: `054-platform-audit-log-entry-append-only.sql` adds `COMMENT ON TABLE audit_log_entry IS 'tsolve:append-only'` (originally planned as a `dashboard-audit` prerequisite); explicitly approved by Hiếu as a §S exception for this PR so CI is green.

## Traceability
Output of `bun harnessclaude/hooks/sdd.ts trace platform STORY-001 STORY-003`:
```
  ✓ AC-001.15  infra/tests/registry-db.test.sh, backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✓ AC-001.16  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✓ AC-001.17  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✗ AC-001.34  no test under this module's paths (...)
  ✓ AC-001.18  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java, backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.19  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✓ AC-001.20  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.21  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.22  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.23  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.24  backend/src/test/java/vn/tsolve/platform/registry/PasswordCipherTest.java
  ✓ AC-001.25  backend/src/test/java/vn/tsolve/platform/registry/PasswordCipherTest.java, backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✗ AC-001.26  no test under this module's paths (...)
  ✓ AC-001.27  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java, backend/src/test/java/vn/tsolve/common/tenant/RowGuardIT.java
  ✓ AC-001.28  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java, backend/src/test/java/vn/tsolve/common/tenant/RowGuardIT.java
  ✓ AC-001.29  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java, backend/src/test/java/vn/tsolve/common/tenant/RowGuardIT.java
  ✓ AC-001.30  backend/src/test/java/vn/tsolve/common/tenant/RowGuardIT.java
  ✓ AC-001.31  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.32  infra/tests/registry-db.test.sh, backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
  ✓ AC-001.33  backend/src/test/java/vn/tsolve/platform/registry/RegistryChangelogIT.java
  ✓ AC-003.10  backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java
trace platform (STORY-001, STORY-003): 19/21 acceptance criteria have a test
```
All 12 Task 5 ACs are `✓`. The two `✗` entries (`AC-001.26`, `AC-001.34`) belong to Tasks 4 and 7.

## Stage 2 — Code Quality & Impact

**Impact scout:** `db.changelog-master.xml` is loaded by `application.yml` and 17 backend IT/test classes (including `ReviewStorageIT`, which copies the master XML to a temp directory, and `ChatbotRatingSchemaIT`/`WorkspaceInfoRepositoryIT`, which execute Liquibase rollbacks across recent changesets). Classpath-root `<sqlFile path="db/changelog/changes/053-platform-tenant-guard-policies.sql"/>` preserves `ReviewStorageIT` compatibility, and `<rollback>` on `053` drops dependent policies before `052` drops `tenant_guard_ok()`. Transactional DDL inside `053`'s `DO` block ensures `DROP POLICY` + `CREATE POLICY` are atomic on re-runs.

**CRITICAL (blocks pass):**
No issues found.

**Informational (does not block):**
- `backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java:608` — `TableFacts.isJoinTable()` checks only two `_id` PK columns without checking absence of a stray `id` or `<table>_id` column (`AC-001.20` "and no separate key", `design.md:251`).
  Fix: Add `&& !columns.containsKey("id") && !columns.containsKey(name + "_id")` to `isJoinTable()`.
- `backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java:391` — `AC-001.32` table-ownership check queries `pg_tables` for `protectedTables` only, omitting `tenant_identity` (which policies depend on) and partitioned tables (`relkind = 'p'`).
  Fix: Query `pg_class` (`relkind IN ('r', 'p')`, `pg_get_userbyid(relowner)`) across `protectedTables` union `"tenant_identity"`.
- `backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java:280-284` — Fixed-set assertions on `ALLOW_LIST` and `LEGACY_ALLOW_LIST` do not include the literal phrase `"the allow-list only shrinks"` in their `.as(...)` failure descriptions (`AC-001.22` wording; `staleEntries` does include it).
  Fix: Add `.as("...; the allow-list only shrinks")` to both assertions.
- `backend/src/test/java/vn/tsolve/common/tenant/ConventionGuardIT.java:444-472` — `guardViolations()` does not inspect `pg_policies.roles` to assert `{public}` (mitigated in practice because `053` runs last and recreates `tenant_guard` for `PUBLIC`).
  Fix: Select `roles` from `pg_policies` and verify it equals `{public}`.

## Security Review — platform — 2026-11-24

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 1 |
| STRIDE | 0 | 0 | 0 | 1 |

- **Secrets & env files:** Clean. `gitleaks` CI passed; only `.env.example` and `frontend/.env.example` are tracked; `.gitignore` ignores `.env` and `.env.*`. Only test fixture constant `RUNTIME_PASSWORD = "fake-runtime-password"` in `TenantGuardDatabase.java:32`.
- **Dependencies:** No `pom.xml` or `package.json` changes in PR #322.
- **SQL & DDL safety:** `053` and its `<rollback>` safely use PostgreSQL `format('... public.%I ...', t)` with identifier quoting. `tenant_guard_ok()` is `STABLE` (re-evaluated per statement execution, safe under JDBC server-side prepared statements), `SECURITY INVOKER` (default), fail-closed on unset/empty/mismatch/empty-table, and raises `22P02` on malformed input.
- **STRIDE (Tenant isolation / RLS):**
  - Spoofing / shadowing: `public.tenant_identity` is schema-qualified (`RowGuardIT:190-211` proves `pg_temp.tenant_identity` cannot shadow it).
  - Tampering & EoP: Runtime user cannot mutate/truncate `tenant_identity`, disable RLS, drop/alter/add policies, or replace `tenant_guard_ok()` (`42501` verified in `RowGuardIT`). Runtime/Registry/Jobs roles are neither superuser nor `BYPASSRLS`. No `TRUNCATE` granted to runtime user. Single-policy rule blocks permissive OR-bypass policies.
  - Information disclosure: Unset, empty, reset, wrong, malformed, or unprovisioned-identity states all return 0 rows or error (`22P02`).
  - DoS: `tenant_identity` has at most 1 row and `tenant_guard_ok()` is `STABLE` (InitPlan once per statement). `053` (`runAlways="true"`) briefly takes `ACCESS EXCLUSIVE` locks across `public` tables during deploy-time Liquibase updates; acceptable at deploy time.
  - OWASP A10 (SSRF): N/A.

### Findings
1. **[LOW][CODE/STRIDE]** `backend/src/main/resources/db/changelog/changes/052-platform-tenant-guard-function.sql:14` — `current_setting('app.tenant_id', true)` is unqualified rather than `pg_catalog.current_setting(...)` (if a runtime role ever held `TEMP` and reordered `pg_catalog` after `pg_temp`, `pg_temp.current_setting` could shadow it; mitigated today because `REVOKE ALL ON DATABASE ... FROM PUBLIC` removes `TEMP` and SQL-injection inside a Workspace DB is already accepted under Edge #11 / ADR-0002).
   - Fix: Qualify `pg_catalog.current_setting('app.tenant_id', true)` in a future follow-up changeset if desired (not blocking).

## Issues
None blocking (0 CRITICAL code findings, 0 Critical/High security findings, 0 failing tests). See Informational notes above.

## Assumptions made
- `054-platform-audit-log-entry-append-only.sql` is accepted in this PR per Hiếu's explicit §S exception (original `tasks.md` / `design.md:253` wording treating it as a `dashboard-audit` prerequisite is acknowledged as stale/unscheduled).
- `ENABLE ROW LEVEL SECURITY` without `FORCE ROW LEVEL SECURITY` is intentional per spec/design/card so table owners (Liquibase and pre-wave-4 staging) bypass RLS while non-owner runtime users are enforced.
- `AC-001.26` (cross-DB connection refusal) and `AC-001.34` (`google_domain` / `keycloak_organization_id`) remain uncovered in `sdd.ts trace` for `STORY-001` because they belong to Tasks 4 and 7, not Task 5.
- Proposed feature status for `platform` / `row-guard`: `passing` (not modified in `progress.json` per card instruction).

## Test Report
See [task-5-row-guard-test-report.md](task-5-row-guard-test-report.md).
- Reviewer targeted ITs (`cd001049`): `ConventionGuardIT` 9/9 (10.80s) + `RowGuardIT` 8/8 (6.965s) = 17/17 pass, `BUILD SUCCESS`.
- Developer verify (`cd001049`): `mvn spotless:check test` 3,242/3,242 pass; 7 targeted IT classes 44/44 pass.
- PR #322 CI (`gh pr checks 322`, `cd001049`): all required checks green (`backend`, `backend-tests (unit)`, `it-keycloak-a`, `it-keycloak-b`, `it-other`, `frontend`, `changes`, `gitleaks`, `pr-title`).
