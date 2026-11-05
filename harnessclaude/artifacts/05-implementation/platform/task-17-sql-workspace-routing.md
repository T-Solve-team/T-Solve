# Task 17: SQL Workspace routing required by KAN-107

**Module:** platform
**Assignee:** Phạm Tùng Dương
**Status:** passing
**Iteration:** 1

---

## Session 2026-11-03

### Working on: sql-workspace-routing (SQL-P1–P4)

- User approved the platform/identity SQL amendments and manually ran both HarnessClaude approval commands.
  `sdd.ts audit` then passed with 11 existing warnings. The active branch is
  `feat/KAN-107-identity-user-list-backend`, following the team Jira convention; the harness still records its
  old module-branch convention. No branch or merge was performed during implementation.
- Added independent catalog and tenant Liquibase bootstraps with byte-identical copies of the supplied SQL.
  Root SQL/PDF files remain unmodified and untracked. No live database was migrated or provisioned.
- Added Long-based scoped context, no-fallback routing, lazy/capped Hikari pools and server-side secret
  reference resolution. Unknown/missing/mismatched bindings and incomplete connection metadata fail closed.
- Added Host-only resolution before Spring Security on user paths, with 404 MSG151 / 403 MSG150;
  disabled feature returns 503 MSG06. Forwarding headers and Workspace request selectors cannot choose a tenant.
- Published the qualified `sqlTenantDatabase` resource holder instead of separate DataSource/JdbcTemplate/
  transaction-manager beans. This is an internal design refinement: separate beans of those types would disable
  Boot's legacy auto-configuration. Full HTTP/database tests prove that old JPA still uses the legacy database.
- Updated only User List/Department read error contracts and regenerated users.ts. The list API, Last sign-in
  removal and numeric list UI remain the next feature (`identity/user-list-backend`), not claimed complete here.
- No OIDC/session issuance, Jira linking, full provisioning script or legacy schema conversion was added.

### Tests and fixes

- RED: missing SQL bootstrap resource failed `SqlBootstrapIT` before adding resources.
- RED/GREEN: valid context, pool creation and host resolution tests failed before their implementations.
- Real PostgreSQL tests use owner bootstrap and separate non-owner catalog/tenant users, two tenant databases
  containing the same account id, and the actual registered filter/security chain.
- BUG-104: case-insensitive catalog resolution returned 200 for ambiguous `one`/`ONE` site names. Reproduced
  500-expected/200-actual, then fixed with cardinality-checked lookup; SQL is unchanged.
- BUG-105: read helper joined a write transaction (`transaction_read_only=off`). Reproduced and fixed using
  a separate read-only REPEATABLE READ transaction while keeping the same Workspace binding.
- BUG-106: incomplete catalog metadata reached the pool factory. Reproduced and fixed by validating
  metadata before secret lookup or connection construction.
- Final backend command: Java 21, Docker Desktop pipe, Maven `-Dapi.version=1.44 spotless:apply verify`.
  `backend/target/kan-107-verify-final.log`: BUILD SUCCESS; 268 unit tests (one pre-existing disabled test),
  16 integration tests, zero failures/errors; Spotless passed. New feature: 12 unit + 6 integration tests.
- Frontend format:check, lint, typecheck and build passed. Lint retains the existing TicketListFilters fast-refresh
  warning. Generated users client is byte-identical on a second `npm run generate:api` invocation.
- Full frontend bounded run: `npm run test -- --run --maxWorkers=2`, 66 files / 1269 tests passed,
  exit 0, duration 611.07 sec. Assertions and timeouts unchanged. The earlier restricted/default-worker
  runs failed under process/temp-cache restrictions and concurrent load, as recorded in the report.
- User restored the existing BUG-093 correction into the three protected hook files. Fresh official trace:
  `trace platform (STORY-001, STORY-002): 41/41 acceptance criteria have a test`, exit 0.
  `bun test harnessclaude/hooks/lib/spec.test.ts`: four passed, zero failed.
- Gitleaks directory scans of both new production Java packages: no leaks.
- Graphify updated the code graph to 7308 nodes / 15748 edges and the TenantDataSource query finds the new
  dependencies. SQL extraction remains unavailable (`tree_sitter_sql` missing); SQL was verified directly in
  PostgreSQL. Document semantic re-indexing is not claimed by the code-only update.

### Files

- Production Java: `backend/src/main/java/vn/tsolve/common/tenant/` and `vn/tsolve/platform/`.
- Tests: corresponding `backend/src/test/java/vn/tsolve/common/tenant/` and `vn/tsolve/platform/`.
- Bootstrap: `backend/src/main/resources/db/changelog/catalog/` and `tenant/`, including deployment notes.
- Configuration: `.env.example`, `backend/src/main/resources/application.yml`.
- Contract: `frontend/openapi/tsolve-api.yaml`, generated `users/users.ts`.
- Bug tracking: `docs/bug-log.md`; plans, approvals and feature registration remain separate harness changes.

### Blockers / checkpoint

The original 0/41 result was caused by known BUG-093 on main. The user applied the reviewed KAN-117
correction (`21096b37c74f455a1f772d4391c1529f1072f7ac`) manually; fresh trace is now 41/41 and its
four regression tests pass. No technical blocker remains. The user approved the code commit and continuation.

### Commit per repo

`default@8e2c3ab8f1841db03148f52322d7f1b37b61b724`:
`feat(platform): route SQL workspace databases (KAN-107)`. Hooks passed; no push.

### Next up

Commit the separate harness record, then proceed to KAN-107 User List backend (SQL-U1–U5).
The next feature has its own verification and user commit checkpoint. Push only on request.

## Superseded integration scope — 2026-11-05
User selected the current main UUID architecture for PR119. The earlier numeric SQL checkpoint above remains historical evidence only. The parallel catalog/router/bootstrap is retired from this PR. Current work is identity Task23 user-list-uuid-read, approved AC-004.9–.19; do not infer deployment readiness or current test results from this report. Original commits remain in history.
