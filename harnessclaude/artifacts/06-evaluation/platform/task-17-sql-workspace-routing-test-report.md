# Test Report: platform — Task 17: sql-workspace-routing

**Date:** 2026-11-03 (Asia/Bangkok)
**Commands:** Maven `spotless:apply verify -Dapi.version=1.44`; frontend `format:check`, `lint`,
`typecheck`, `test`, `build`, `generate:api`; HarnessClaude `check`, `trace`; Gitleaks directory scans.

## Results

- Backend: BUILD SUCCESS, 268 unit tests (267 passed, one pre-existing disabled callback test),
  16 integration tests passed, zero failures/errors. Duration 2 min 52 sec for the final run.
- New feature: 12 unit tests and six real PostgreSQL integration tests, all passed.
- Spotless: passed, 24 changed Java files clean.
- Frontend format/typecheck/build passed. Lint passed with the pre-existing TicketListFilters fast-refresh warning.
- OpenAPI generation is reproducible: the generated users client SHA-256 was unchanged on a second invocation.
- Full frontend test result: 66 files / 1269 tests passed, zero failures/skips, exit 0;
  `npm run test -- --run --maxWorkers=2`, duration 611.07 sec.
- Gitleaks scans of the two new production Java packages: zero findings.
- No database on the user's running deployment was modified. All schema/privilege tests used Testcontainers.

## Coverage

No line/branch coverage command or new numeric threshold was introduced. Behavioral evidence includes the
actual servlet filter/security chain, catalog lookup, separate database users, qualified routing and transactions.
Do not equate an AC id in a test name with coverage without reading the assertion.

## Acceptance Criteria → Tests

| Criteria | Evidence |
|----------|----------|
| AC-001.15–.23, .27 | SqlBootstrapIT: independent empty catalog/tenant bootstrap, second Liquibase run, exact table sets, app_user columns, generated Long ids, implicit Employee, foreign keys and unique/check constraints |
| AC-001.24–.25 | TenantPoolProviderTest: missing/blank secret never opens a pool; config/connection projections redact secrets; no password column in supplied catalog |
| AC-001.26 | SqlWorkspaceIsolationIT: tenant A denied CONNECT to tenant B, catalog and legacy DB; catalog runtime denied tenant connection |
| AC-002.1, .11–.16 | ScopedTenantContextTest: missing/unknown/cross/same binding, restore after failures; existence check independent of status; no-binding call-site logging |
| AC-002.2–.4, .6, .10, .17 | WorkspaceHostTest: actual Host normalization, localhost/ports, RFC1123 limits, reserved labels and foreign/multiple-label hosts |
| AC-002.3, .5, .7–.9, .11, .19 | WorkspaceTenantFilterTest and real HTTP IT: ignore selectors/forwarding, unknown/Suspended before handler/security, coded sanitized response, cleanup/reused thread, unaffected health |
| AC-002.18, .20, .23–.25 | SqlWorkspaceIsolationIT and configuration tests: identical record ids return each database's rows; transaction rejects switch; legacy JPA/connection remains separate; disabled feature fails closed |
| AC-002.21 | TenantDataSourceTest and isolation IT: no default connection or caller-supplied credentials; missing binding fails before tenant access |
| AC-002.22, .26 | Pool provider tests: one lazy pool per Workspace under concurrency, hard cap, close/shutdown, missing secret and sanitized driver failure; live suspension is checked outside the pool cache |
| AC-002.27 | Distinct catalog/tenant runtime users and real PostgreSQL CONNECT denial |
| AC-002.28 | SQL copied byte-for-byte into new changelogs, legacy chain untouched, no original SQL/PDF modifications; hashes checked locally |

Fresh official command output after the user applied the existing Java trace correction:

```text
trace platform (STORY-001, STORY-002): 41/41 acceptance criteria have a test
```

The earlier 0/41 result was caused by known BUG-093 on main (no Java discovery or underscore-id recognition).
The user restored the reviewed correction from KAN-117 commit `21096b37c74f455a1f772d4391c1529f1072f7ac`.
The exact diff contains only Java discovery, AC normalization and regression tests. The agent did not edit
the protected hook files. `bun test harnessclaude/hooks/lib/spec.test.ts`: four passed, zero failed.
The user approved the foundation commit; code is recorded as `8e2c3ab` and the feature can pass its checkpoint.

## Failures found and fixed

| Bug | Failing evidence | Final evidence |
|-----|------------------|----------------|
| BUG-104 | Ambiguous `one`/`ONE`: HTTP expected 500 but got 200 | Cardinality-checked lookup; original HTTP regression now passes |
| BUG-105 | Nested read helper: expected `transaction_read_only=on`, got `off` | Read-only REQUIRES_NEW snapshot; real transaction regression passes |
| BUG-106 | Incomplete connection metadata: expected exception, factory returned a pool | Guard before driver/secret lookup; metadata regression passes |

## Frontend test execution notes

The initial restricted-process run failed before loading tests with ENOENT in a per-process temporary Vite
cache path. Running outside that process restriction loaded the suite, but the concurrent/default-worker run
reported hook/render timeouts under load and was stopped after failure. The bounded retry uses the same
suite/assertions/timeouts with `--maxWorkers=2`; it passed all 1269 tests in 66 files (exit 0).
No test was skipped, removed or given a longer timeout to make this feature pass.

## Source preservation

- Catalog original and versioned copy SHA-256: `20A5EED02997BC6FDAC439BBE662FC704A40D488D94D1CC4D5B2CC69EF7B3C7C`.
- Tenant original and versioned copy SHA-256: `F00E971AF1402347AE33452E25FFFA6D05C982BF015A47A054ACB11E46E4D39D`.
- Original PDF SHA-256: `90B752B6C6F1CA3BCF46BE306585A94BBF18E1DE2960CC7F46E5B8A2737E1A2C`.

## Notes

- The application is staged: only SQL Workspace infrastructure is delivered in this feature. User List read
  implementation and FE Last sign-in removal follow the feature commit checkpoint. Real OIDC/session issuance,
  full Jira integration, KAN-240 provisioning and conversion of legacy modules are not claimed complete.
- Graphify code update completed (7308 nodes, 15748 edges); SQL/PDF parsers are missing in that tool, so schema
  behavior was checked directly in PostgreSQL. No document semantic re-indexing claim is made.
- A supplementary tracked-env filename listing was blocked by the privacy hook; it was not retried and no env
  content was read. Production and test Java directory secret scans completed separately with zero findings.

## Superseded integration scope — 2026-11-05
User selected the current main UUID architecture for PR119. The earlier numeric SQL checkpoint above remains historical evidence only. The parallel catalog/router/bootstrap is retired from this PR. Current work is identity Task23 user-list-uuid-read, approved AC-004.9–.19; do not infer deployment readiness or current test results from this report. Original commits remain in history.
