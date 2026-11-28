# Test Report: platform — Task 6-2: tenant-routing (PR #360)

**Date:** 2026-11-27
**Head:** `2cd63d61` (HEAD of `feat/KAN-254-platform-tenant-routing` = PR head; clean tree)
**Commands:** `mvn -B test -Dtest=ThreadLocalTenantContextTest,RegistryPasswordCliTest,TenantPoolRegistryTest` (reviewer,
local) · `gh pr checks 360` (full suites, GitHub CI) · `bun harnessclaude/hooks/sdd.ts trace platform STORY-002 STORY-001`

## Results

- Reviewer, targeted units: Total 31 · Passed 31 · Failed 0 · Skipped 0 · BUILD SUCCESS. `TenantPoolRegistryTest`
  2 tests in 8.6 s, because the context runner opens no connection.
- Reviewer, targeted ITs (`TenantRoutingIT`, `RegistryChangelogIT`, `CatalogLookupsIT`, `WorkspaceCatalogIT`): started,
  then **stopped on the coordinator's instruction** (exit 255 is intentional, not a test failure). CI covers them.
- Developer (from the card, on the content of `2cd63d61`): targeted units exit 0. `TenantRoutingIT` 8 +
  `RegistryChangelogIT` 9: 17 run, 0 failures. `spotless:check` passed. The full unit run was stopped partway (low
  memory; 0 failures among the 122 finished report files). `CatalogLookupsIT` and `WorkspaceCatalogIT` were not run
  locally.
- GitHub CI on `2cd63d61` (`gh pr checks 360`):
  - pass: backend (6 s); backend-tests unit (3m16s), it-keycloak-a (3m28s), it-keycloak-b (2m58s), it-other-a (3m16s),
    it-other-b (4m25s); frontend; changes; gitleaks; pr-title
  - skipped: frontend-checks and frontend-tests (no frontend change)
- Typecheck/compile: pass (CI unit and IT jobs compile main and test). Lint: Spotless passed (developer; CI backend job
  green).

## Coverage

No coverage command was run, and the spec sets no threshold.

## Acceptance Criteria → Tests

```
trace platform (STORY-002, STORY-001): 42/45 acceptance criteria have a test
✓ AC-001.25  PasswordCipherTest, RegistryChangelogIT, TenantRoutingIT
✓ AC-002.1   ThreadLocalTenantContextTest
✓ AC-002.13  NoWorkspaceFallbacksTest, TenantResolverFilterTest, ThreadLocalTenantContextTest, TenantRoutingIT
✓ AC-002.20  TenantPoolRegistryTest, TenantRoutingIT
✓ AC-002.21  TenantRoutingIT
✓ AC-002.22  TenantRoutingIT
✓ AC-002.23  TenantRoutingIT
✓ AC-002.24  TenantRoutingIT
✓ AC-002.25  TenantRoutingIT
✗ AC-001.26, AC-001.34, AC-002.18  not in this task (identity; Task 10's TenantIsolationIT)
```

## Failures

None.

## Notes

- `infra/tests/registry-db.test.sh` stopped locally for the developer ("init script did not create the Registry"). The
  cause is outside this diff: its setup uses only the unchanged `infra/scripts/registry-db-init.sh`, and S5 (the only
  part that reads the changed compose file) passed. `deploy-docker-config.test.sh` exits 0 (developer).
- No full local suite was run, as the card and `CLAUDE.local.md` §3 require.
