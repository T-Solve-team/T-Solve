# Test Report: platform — Task 10b: web-tenant-isolation

**Date:** 2026-11-27
**State tested:** `C:\Users\ADMIN\Documents\GitHub\T-Solve`, branch `test/KAN-460-platform-web-isolation`, HEAD
`c17ca2b4` (= PR #362 head `c17ca2b41d8b…`), working tree clean.
**Commands:**
- `mvn -q -B spotless:check verify -Dtest=NoUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TenantIsolationIT,TenantResolverTomcatIT`
  (in `backend`, log outside the repo)
- `bun harnessclaude/hooks/sdd.ts trace platform STORY-002`
- `gh pr checks 362`
- Not run (card: targeted only): the full `mvn verify`, unit suite, coverage, frontend checks.

## Results
- Targeted run: exit 0 (Spotless check and Failsafe in the same command). Total 5 · Passed 5 · Failed 0 · Skipped 0.

| Test class | Tests | Failures | Errors | Time |
|------------|-------|----------|--------|------|
| `vn.tsolve.common.tenant.TenantIsolationIT` | 3 | 0 | 0 | 26.2 s (two Workspace DBs + Registry on the shared container, one routing context per test) |
| `vn.tsolve.platform.web.TenantResolverTomcatIT` | 2 | 0 | 0 | 3.8 s (Tomcat on a random port) |

- Matches the developer's verify on the card (`Tests run: 5, Failures: 0, Errors: 0`, `BUILD SUCCESS`, same command,
  same commit).
- Typecheck: test sources compiled for the run. Lint: `spotless:check` passed in the run.

## CI on PR #362 (`gh pr checks 362`, read 2026-11-27 ~20:50)
- pass: `changes`, `frontend`, `gitleaks`, `pr-title`. skipping: `frontend-checks`, `frontend-tests` (no frontend change).
- pending: `backend-tests (unit)`, `(it-keycloak-a)`, `(it-keycloak-b)`, `(it-other-a)`, `(it-other-b)`.
- CI placement: `TenantResolverTomcatIT` (package `vn/tsolve/platform/**`) runs in `it-other-a`; `TenantIsolationIT`
  (package `vn/tsolve/common/**`) falls to `it-other-b`. Both are covered by the complementary patterns, no `ci.yml`
  change needed.

## Coverage
Not run (test-only change; the spec sets no coverage target for it).

## Acceptance Criteria → Tests
`trace platform (STORY-002): 25/25 acceptance criteria have a test`. Rows this task adds to:
- ✓ AC-002.3 … `TenantIsolationIT.java` (with `HostClassifierTest`, `TenantResolverFilterTest`)
- ✓ AC-002.18 `TenantIsolationIT.java` (only test naming it)
- AC-005.8 (STORY-005, not in this trace): `TenantResolverTomcatIT.refusesAJiraAppPathOnASiteHost_AC_005_8`.

## Failures
None.

## Notes
- No flaky, skipped or ignored test. `TenantIsolationIT` is the slowest (26 s); each of its three tests starts its own
  routing context (`runner().run(...)`), which is the cost the card's Q1 accepted.
