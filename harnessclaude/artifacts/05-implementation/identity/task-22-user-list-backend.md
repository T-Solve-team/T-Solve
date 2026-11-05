# Task 22: SQL User List backend — KAN-107

**Module:** identity
**Assignee:** Phạm Tùng Dương
**Status:** passing
**Iteration:** 1

---

## Session 2026-11-03

### Working on: user-list-backend (SQL-U1–U5, Tasks 22–26)

- Implemented the approved STORY-004 read slice after foundation commits `8e2c3ab` and
  `9ec70c6`, on `feat/KAN-107-identity-user-list-backend`.
- Updated OpenAPI first: numeric-string list ids, dedicated UserListItem/Department DTOs,
  validated paging/filter/sort contract, and no Last sign-in value in the list response.
  Generated clients are produced by Orval, not edited manually.
- Added the trusted WorkspaceAccountPrincipal and a GET-only security chain for the two
  read endpoints. Validate the session binding before opening a tenant transaction;
  reload Active account/Admin roles, then recheck access inside the response snapshot.
  Original session authorities are not mutated or persisted by the filter.
- Added layered controller/service/repository reads using the qualified SQL tenant holder.
  Literal ILIKE search escapes wildcard characters; filters use parameters and EXISTS;
  count/page/one bounded roles query avoid duplicated rows and N+1 queries. Requested
  sorts are allow-listed and numeric user_id ascending breaks ties.
- Reused Role/Roles definitions from the reviewed KAN-117 branch rather than duplicating
  role vocabulary. This does not import its UUID principal or global security chain and
  does not mark the separate roles-security feature complete.
- Removed User List's Last sign-in column. An old lastSignInAt URL is normalized before
  requests, preserving search/filter state. API ids stay strings through navigation.
- Internal compatibility refinement: User List departments now have numeric ids; existing
  creation/role forms still use their unported UUID write contracts. Their already-used
  creation-options response therefore supplies optional legacy Department metadata.
  UUID/numeric mapping exists only at the MSW boundary; backend schemas are not bridged.
- Reused SqlBootstrapIT's existing bootstrap helpers by exposing them to the new SQL tests;
  the original test behavior is unchanged. No duplicate migration helper was introduced.
- Corrected the creation-options retry fixture to include legacy Department metadata,
  keeping its original empty-Spaces assertion meaningful after the lookup split.
- Added deployment/session prerequisites to the SQL bootstrap README. Real OIDC/session
  issuance, creation/detail/mutations, and legacy module migration remain separate work.
  The feature remains disabled by default; no deployed database was modified.

### Verification evidence

- RED/GREEN: query validation, live authorization, SQL repository, full HTTP stack and
  numeric UI contract tests were introduced before implementation. Logs are under
  ignored `backend/target/kan-107-*`.
- BUG-107: a failing tenant connection previously ran before pure session validation,
  returning 500 for untrusted/foreign sessions. The regression now proves 401 MSG07
  without a connection attempt, while SQL authorization remains current.
- Final backend Maven `-Dapi.version=1.44 spotless:apply verify`: BUILD SUCCESS;
  275 unit tests (274 passed, one pre-existing disabled), 26 integration tests passed,
  zero failures/errors. New slice: seven unit tests and ten real PostgreSQL integration tests.
- Frontend format, lint, typecheck and build passed. Lint retains the existing
  TicketListFilters fast-refresh warning. Final full suite: 67 files / 1273 tests passed,
  exit 0, duration 3831.98 sec (88% tracked import time). All 129 generated files have
  unchanged hashes on a second Orval invocation.
- Official `trace identity STORY-004`: 19/19 criteria have tests. Reviewed the named
  assertions against the approved SQL amendment, rather than relying only on trace labels.
- Browser/MSW check on one.localhost:5174: five list columns, numeric row links, old
  sort normalized to fullName,asc with q retained; /users/1 opens user1@company.example.
  This is frontend mock evidence; real backend HTTP/database behavior is covered separately.
- Gitleaks identity/common-security scans found no leaks. npm audit reproduces known
  BUG-069: 19 moderate and two high development-toolchain findings; production-only
  audit reports zero. Dependency manifests/lockfiles are unchanged; no audit-fix applied.
- Graphify code graph refreshed to 7485 nodes / 16399 edges and queried for new reads,
  session filter and principal. SQL parsing is unavailable (missing tree_sitter_sql), so
  SQL behavior was verified directly against PostgreSQL. No semantic documentation index claimed.
- Root SQL and database PDF hashes remain unchanged; classpath SQL copies match originals.
- First full frontend run passed 1272/1273 (620.40 sec), failing the unchanged Review Queue
  Reject test's MSG16 wait. Logged BUG-108, distinct from BUG-087. The full recheck passed
  with no concurrent build/Graphify; no Review Queue source/test, timeout, assertion or
  retry/isolation setting changed. BUG-108 remains open rather than claimed fixed.
- Final `sdd.ts audit`: OK with 12 existing tracking warnings. Git whitespace check is clean.

### Files

- Backend: `identity/{config,controller,dto,exception,repository,service,session}/`,
  common Role/Roles, SQL bootstrap README and corresponding tests.
- Frontend: OpenAPI/generated client, User List/toolbar/tests, mock boundary adapter,
  existing creation/role form Department lookup and its retry fixture.
- Tracking: BUG-107 and this feature's implementation/evaluation records.

### Blockers

No implementation or verification blocker remains in this feature. Production activation
still requires the separately scoped trusted session issuance and database provisioning.

### Next up

The user approved the feature commit. Code committed with repository hooks as
`default@b6b15970fb03ec8f979884af4c6ad4b40771a40c`:
`feat(identity): implement SQL user list (KAN-107)` (44 files).
Official tracking recorded that commit, then moved user-list-backend through evaluating to passing.
Save the separate harness commit. No push is authorized for this branch.

## Superseded integration scope — 2026-11-05
User selected the current main UUID architecture for PR119. The earlier numeric SQL checkpoint above remains historical evidence only. The parallel catalog/router/bootstrap is retired from this PR. Current work is identity Task23 user-list-uuid-read, approved AC-004.9–.19; do not infer deployment readiness or current test results from this report. Original commits remain in history.
