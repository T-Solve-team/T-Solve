# Test Report: platform — Task 11a: startup-migrator

**Date:** 2026-11-27
**Code:** `feat/KAN-475-platform-startup-migrator` @ f0b4e480 (PR #364), working tree clean
**Commands (reviewer, targeted only — the full suites run in CI):**
`mvn -B verify -Dtest=TenantMigratorTest,TenantStartupMigratorTest,TenantPoolRegistryTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TenantMigratorIT,RegistryChangelogIT,TenantRoutingIT -Dfailsafe.failIfNoSpecifiedTests=false`
(log outside the repo) · `bun harnessclaude/hooks/sdd.ts trace platform STORY-008 STORY-001` · `gh pr checks 364`

## Results
- Unit (surefire): Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
  - TenantMigratorTest 10/0 · TenantStartupMigratorTest 5/0 · TenantPoolRegistryTest 2/0
- IT (failsafe, Testcontainers PostgreSQL): Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
  - TenantMigratorIT 3/0 (11.9 s) · RegistryChangelogIT 9/0 (5.4 s) · TenantRoutingIT 8/0 (46.5 s)
- `spotless:check` ran in the same `verify`: pass. `BUILD SUCCESS`, total 01:37 min.
- ERROR lines in the log (RLS refusal on `isolation_probes`, `Could not open the database pool of Workspace site-b-…`)
  are the expected output of TenantRoutingIT's negative cases, not failures.

## Developer's verify (from the card, not re-run)
- `mvn test -Dtest=TenantMigratorTest,TenantStartupMigratorTest`: Tests run: 15, Failures: 0, BUILD SUCCESS
- `mvn spotless:check verify -Dit.test=TenantMigratorIT,RegistryChangelogIT,TenantRoutingIT,RowGuardIT,ConventionGuardIT,WorkspaceCatalogIT,CatalogLookupsIT`:
  unit 3461/0 fail, IT 53/0 fail, BUILD SUCCESS
- Run on the then-uncommitted code; HEAD f0b4e480 is the commit of that code and `git status` is clean.

## CI (full suites)
- `gh pr checks 364`: "no checks reported on the 'feat/KAN-475-platform-startup-migrator' branch";
  `statusCheckRollup: []`, `mergeable: UNKNOWN`, no workflow run for the branch. CI has not run yet.

## Coverage
Not measured (targeted run). The spec gives no coverage target for this task.

## Acceptance Criteria → Tests
```
✓ AC-001.15  infra/tests/registry-adopt.test.sh, infra/tests/registry-db.test.sh, TenantMigratorTest.java, TenantMigratorIT.java, RegistryChangelogIT.java
✓ AC-008.4   TenantMigratorTest.java, TenantStartupMigratorTest.java, TenantMigratorIT.java, RegistryChangelogIT.java
✓ AC-008.9   TenantMigratorTest.java, TenantStartupMigratorTest.java, TenantMigratorIT.java
trace platform (STORY-008, STORY-001): 21/31 acceptance criteria have a test   (the other 10 belong to other tasks)
```

## Failures
None.

## Notes
- No test checks that the routing `dataSource` depends on `TenantStartupMigrator` (R14), nor the blank
  `TENANT_DB_HOST` refusal in `TenantMigrator`, nor the gate of `TenantMigratorConfig` (every test supplies the
  migrator as a mock bean).
- The planned `TenantStartupMigratorIT` (real database, context start fails) is covered by two tests instead:
  `TenantStartupMigratorTest.failedMigrationStopsStartup_AC_008_9` (context fails, mock migrator) and
  `TenantMigratorIT.realFailureNamesTheSite…` (real database, migrator level).
- No flaky or skipped tests; no test over 5 s except the ITs' container work.
