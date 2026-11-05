# Test Report: identity — Task 22: user-list-backend

**Date:** 2026-11-03 (Asia/Bangkok)
**Scope:** KAN-107 SQL-U1–U5, STORY-004; foundation already committed separately.

## Results

| Check | Result | Evidence |
|-------|--------|----------|
| Backend full verification | PASS | Java 21 Maven `-Dapi.version=1.44 spotless:apply verify`; BUILD SUCCESS |
| Unit tests | PASS | 275 run: 274 passed, one pre-existing disabled callback test; zero failures/errors |
| Integration tests | PASS | 26 real PostgreSQL tests, zero failures/errors |
| New User List backend tests | PASS | Seven unit tests and ten integration tests (four repository, six HTTP) |
| Spotless | PASS | 44 changed Java files clean |
| Frontend format:check | PASS | All matched files use Prettier style |
| Frontend lint | PASS | Zero errors; existing TicketListFilters fast-refresh warning |
| Frontend typecheck/build | PASS | TypeScript and production Vite build exit 0 |
| Frontend full run 1 | FAIL | 67 files: 66 passed, one failed; 1272/1273 tests passed; 620.40 sec |
| Frontend full recheck | PASS | 67 files / 1273 tests passed, zero failures/skips, exit 0; same maxWorkers=2 and no concurrent build/Graphify |
| API generation | PASS | Second Orval generation leaves hashes of all 129 generated files unchanged |
| Story trace | PASS | Official `trace identity STORY-004`: 19/19 ACs have tests |
| Spec check | PASS | identity: 9 stories, 140 acceptance criteria, OK |
| Secret scan | PASS | Gitleaks identity and common/security directories: no leaks |
| npm production audit | PASS | Zero vulnerabilities with --omit=dev |
| npm full audit | EXISTING ISSUE | BUG-069: 19 moderate/two high development-toolchain findings; manifests/lockfiles unchanged |

Commands requiring Docker/Maven cache and frontend process/network access ran with approved
execution permissions. Assertions, timeout thresholds and test configuration were not weakened.
No new numeric coverage threshold or line/branch coverage claim is made.

## Behavioral evidence

| Criteria | Tests and assertions |
|----------|----------------------|
| AC-004.1–.8 | Existing UserListPage/handler tests retain workspace isolation, filters, URL paging, loading/error/retry/MSG04, guards and 100-character input preservation |
| AC-004.9–.12 | UserListRepositoryIT + UserListIT run supplied schema on real PostgreSQL, preserve max BIGINT ids, avoid role-join duplicates, retain implicit Employee and nullable Department/status |
| AC-004.11, .14 | Query/repository/HTTP tests trim q, treat wildcards/injection text literally, combine filters with AND, reject invalid query/sort/id overflow and return field metadata |
| AC-004.13–.15 | Default/size bounds, stable numeric tie order, filtered totals independent of page, empty missing Department, retained inactive Department sorted lookup |
| AC-004.16 | Unit guard tests and actual Spring Security/MockMvc stack reject missing/untrusted/foreign/anonymous/deactivated/missing/non-Admin identities; role changes apply on next request |
| AC-004.17 | UserListSql UI tests omit column/response field and normalize every outgoing legacy-sort request before results; current q survives |
| AC-004.18 | Real catalog + two tenant DBs contain identical ids but separate rows/counts; foreign Workspace session rejected; context empty after success/failure |
| AC-004.19 | SQL resource hashes pinned in HTTP test; app_user/user_role rows unchanged by GET; root originals checked locally; no legacy Liquibase edit |

## Failures and diagnosis

- BUG-107: the pre-guard tenant read transaction previously acquired a failing connection before
  rejecting a missing/untrusted/foreign principal. Regression failed before the fix, then passed;
  final Maven verify also passes. Pure validation now precedes connection acquisition.
- Initial focused User Detail retry test returned no legacy Department metadata after the lookup
  split, producing two MSG04 empty states when Knowledge Manager was selected. Updated its fixture
  to supply existing Department options so it still specifically asserts empty Spaces.
- Lint found an unsafe `expect.stringMatching` assignment in the changed mock test. Replaced it
  with a separate id pattern assertion and complete response projection equality; lint now passes.
- Full frontend run 1: unchanged ReviewQueuePage Reject test fails at line 112 waiting for
  MSG16 "1 ticket(s) rejected." after clicking Reject; all User List/User Detail tests pass.
  Logged BUG-108, distinct from BUG-087's MSG19 dialog failure. No Review Queue code/test changed.
  Its exact root cause is unconfirmed. Full rerun without concurrent build/indexing passed
  1273/1273; BUG-108 remains open because this does not establish that the intermittent issue is fixed.
- Full recheck duration: 3831.98 sec (Start at 07:04:14). Vitest attributes 88% of tracked time
  to imports; the run had a long import interval. No assertion, timeout, isolation setting or
  retry policy was changed to get a pass. No further performance diagnosis is claimed here.

Raw execution logs are ignored local files under `backend/target/kan-107-*`; source tests and this
report are durable evidence. Earlier RED/GREEN logs are retained there, including the corrected
numeric-detail test that asserts account email rather than matching a Dev user switcher name.

## Browser verification

Browser/MSW at `http://one.localhost:5174/users`: switched the local Dev user to Admin, verified
five columns without Last sign-in and numeric row hrefs, opened the old URL
`?q=user1&sort=lastSignInAt,desc`, and observed `?q=user1&sort=fullName%2Casc` with filtered rows.
Opening the user1 email link navigated to `/users/1` and displayed `user1@company.example` in
User Detail with existing Department/role controls. No mutation was submitted.

Screenshot: `artifacts/assets/identity/kan-107-sql-user-list.png` (visually inspected).
Browser evidence uses existing mocks; it does not claim an OIDC browser-to-backend login flow.
Real backend/database/security integration is tested independently by UserListIT.

## Original database preservation

SHA-256 of original source files after implementation:

| File | Hash |
|------|------|
| 01_catalog.sql | 20A5EED02997BC6FDAC439BBE662FC704A40D488D94D1CC4D5B2CC69EF7B3C7C |
| 02_tenant.sql | F00E971AF1402347AE33452E25FFFA6D05C982BF015A47A054ACB11E46E4D39D |
| T-Solve Database Tables.pdf | 90B752B6C6F1CA3BCF46BE306585A94BBF18E1DE2960CC7F46E5B8A2737E1A2C |

Catalog/tenant SQL resource copies have the same hashes. Root sources remain untracked and are
excluded from proposed staging. No live database, Ticket/Import schema or applied changeset changed.

## Graphify and limits

Code graph updated and queried: 7485 nodes / 16399 edges. SQL parser dependency is absent;
supplied SQL was verified directly by real PostgreSQL bootstrap/query tests. Semantic documentation
indexing is not claimed. No package was installed to bypass the missing optional parser.

## Final gate

Required backend/frontend checks pass. Review attempt 2 is pass. The user approved the commit;
code is `b6b15970fb03ec8f979884af4c6ad4b40771a40c`, repository hooks passed, and official feature
tracking is passing. Harness records are committed separately. No push is authorized.

## Superseded integration scope — 2026-11-05
User selected the current main UUID architecture for PR119. The earlier numeric SQL checkpoint above remains historical evidence only. The parallel catalog/router/bootstrap is retired from this PR. Current work is identity Task23 user-list-uuid-read, approved AC-004.9–.19; do not infer deployment readiness or current test results from this report. Original commits remain in history.
