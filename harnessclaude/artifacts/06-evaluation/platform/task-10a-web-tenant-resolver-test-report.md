# Test Report: platform — Task 10a: web-tenant-resolver

**Date:** 2026-11-26
**State tested:** worktree `C:\Users\ADMIN\Documents\GitHub\T-Solve-KAN-258`, branch `feat/KAN-258-platform-web-resolver`,
HEAD `c381a7f8` (= PR #354 head `c381a7f8b0eb…`), working tree clean.
**Commands:**
- `mvn -q test -Dtest=HostClassifierTest,TenantResolverFilterTest,TenantFilterOrderTest,TenantFilterConfigTest,ThreadLocalTenantContextTest,RequestBindingSourceScanTest -Dsurefire.failIfNoSpecifiedTests=false` (in `backend`, log outside the repo)
- `bun harnessclaude/hooks/sdd.ts trace platform STORY-002 STORY-005` and `… trace platform STORY-013`
- `gh pr checks 354`
- Not run (card: targeted only, no full verify): `mvn verify`, `spotless:check`, coverage, frontend checks.

## Results
- Targeted run: exit 0. Total 95 · Passed 95 · Failed 0 · Skipped 0.

| Test class | Tests | Failures | Errors | Time |
|------------|-------|----------|--------|------|
| `ThreadLocalTenantContextTest` | 10 | 0 | 0 | 1.4 s |
| `TenantFilterConfigTest` | 1 | 0 | 0 | 1.6 s |
| `HostClassifierTest` | 50 | 0 | 0 | 0.3 s |
| `RequestBindingSourceScanTest` | 1 | 0 | 0 | 0.4 s |
| `TenantFilterOrderTest` | 1 | 0 | 0 | 7.6 s (Spring slice start-up) |
| `TenantResolverFilterTest` | 32 | 0 | 0 | 0.3 s |

- The one `ERROR` line in the log (`GlobalExceptionHandler -- Unhandled exception NoTenantException`) is the expected
  output of the AC-002.16 test in `ThreadLocalTenantContextTest`, not a failure.
- Typecheck: the classes compiled for the run. Lint (`spotless:check`): not run here; developer's run and CI `backend`
  job are green.

### Developer's verify (from the card, not re-run)
- `mvn spotless:check test` 3343 passed on `a8b2df00` (BUG-404 state); 5 targeted ITs 27 passed on `c608475b`;
  `generate:api` no diff; `format:check` clean. Commits after it: the merge of `main` (`2ea35697`, plan docs only) and
  the bug-log PR number (`c381a7f8`), both covered by CI below.

### CI on PR #354 (`gh pr checks 354`, head `c381a7f8`)
All 14 checks pass: backend, backend-tests (unit 2m23s, it-keycloak-a 2m12s, it-keycloak-b 3m0s, it-other 5m58s),
changes, frontend, frontend-checks, frontend-tests 1/4 – 4/4, gitleaks, pr-title.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no coverage run asked by the card) | spec sets none |

## Acceptance Criteria → Tests
`trace platform STORY-002 STORY-005` (exit 1 is module-wide; Task 10a rows only, plus the AC-013 rows):

```
  ✓ AC-002.2  HostClassifierTest, RequestBindingSourceScanTest, TenantResolverFilterTest, TenantFilterConfigTest
  ✓ AC-002.3  HostClassifierTest, TenantResolverFilterTest
  ✓ AC-002.4  HostClassifierTest, TenantResolverFilterTest
  ✓ AC-002.5  TenantResolverFilterTest
  ✓ AC-002.6  HostClassifierTest
  ✓ AC-002.7  TenantResolverFilterTest
  ✓ AC-002.8  TenantFilterOrderTest
  ✓ AC-002.9  TenantResolverFilterTest
  ✓ AC-002.10 HostClassifierTest, TenantResolverFilterTest
  ✓ AC-002.11 TenantResolverFilterTest, ThreadLocalTenantContextTest, WebSessionFilterTenantContextTest
  ✓ AC-002.13 NoWorkspaceFallbacksTest, TenantResolverFilterTest, ThreadLocalTenantContextTest
  ✓ AC-002.17 HostClassifierTest
  ✓ AC-002.19 TenantResolverFilterTest
  ✓ AC-005.7  TenantResolverFilterTest
  ✓ AC-005.8  TenantResolverFilterTest
  ✓ AC-013.11 TenantResolverFilterTest
trace platform (STORY-002, STORY-005): 19/33 acceptance criteria have a test
trace platform (STORY-013): 1/24 acceptance criteria have a test
```

The ✗ rows (AC-002.1, AC-002.18, AC-002.20 – AC-002.25, AC-005.1 – AC-005.6, AC-013.7) are not in Task 10a's
"Covers" list (Tasks 6, 10b, 20 and STORY-013 own them). Every criterion Task 10a covers has a test.

## Failures
None.

## Notes
- No flaky test seen; nothing re-run.
- No skipped test.
- The servlet container is mimicked: tests set the decoded `servletPath` next to the raw URI by hand
  (`TenantResolverFilterTest.request`), and no test runs the resolver behind Spring's `ForwardedHeaderFilter` or on a
  real Tomcat. See review findings 2 and 4.
