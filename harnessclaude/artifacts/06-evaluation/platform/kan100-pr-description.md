## Jira issue

[KAN-100](https://t-solve.atlassian.net/browse/KAN-100), restored to implementation scope by the user. Implements the current database-per-Workspace row guard from platform AC-001.28–32 and AC-003.10.

## What & why

Add a singleton `tenant_identity`, a stable `tenant_guard_ok()` function and a repeatable policy installer that runs after every module's migrations. Runtime reads and writes require the transaction's `app.tenant_id` to match the database identity. The owner remains able to run migrations/backfills.

The installer uses an XML changeset with `runAlways` and `runOrder=last` because formatted SQL does not support runOrder (BUG-128). A real Liquibase parser regression verifies the installer follows a later module's table; removing runOrder makes it fail.

This is the database layer. KAN-98/99 and provisioning still need to create the restricted runtime roles, seed the identity, revoke identity writes and bind/reset the transaction tenant setting in the application. The tests apply those conditions inside isolated PostgreSQL databases. No frontend screen is specified for this infrastructure task; browser/API acceptance depends on the real router/provisioner.

## How to test

- `mvn -B -o test`: 531 passed, 0 failures/errors/skips.
- The new changelog parser suite has 3 passing cases, including the ordering regression and complete SQL block loading.
- `mvn -B -o test-compile failsafe:integration-test failsafe:verify '-Dit.test=RowGuardIT,RowGuardConventionIT' '-Dapi.version=1.44'`: 30 passed against real PostgreSQL, 0 failures/errors/skips.
- Coverage includes matching/missing/malformed/wrong Workspace settings; two databases and misrouting; CRUD; transaction reset; singleton identity; denied identity writes, DDL and TRUNCATE; altered/missing/extra policies; unsafe role attributes, table ownership and owner-role membership.
- All 4 new Java files are clean with the existing Spotless/Palantir formatter. The standard ratchet check still fails to discover Git in the local linked worktree (BUG-130); pom.xml and the check are unchanged.
- The Docker 29 host needs the per-run `api.version=1.44` property with the existing client (BUG-129); project dependencies and global Docker settings are unchanged.

Only the row-guard criteria are implemented here. The wider platform STORY-001 remains in progress. These tests do not establish the application's real request-to-database routing.

## Screenshots (UI changes)

Not applicable: database infrastructure.

## Checklist

- [ ] CI green
- [x] Tests added/updated
- [x] New migrations; merged scripts unchanged
- [x] Actual PostgreSQL row-guard tests passed
- [x] Bug history retained for the report
- [ ] Standard Spotless check in a supported checkout
- [ ] Real runtime/provisioning and browser/API integration
