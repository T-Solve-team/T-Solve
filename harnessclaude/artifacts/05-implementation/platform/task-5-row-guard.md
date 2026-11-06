# Task 5: tenant row guard — KAN-100

Module: platform · Assignee: Trần Hải Anh · Iteration: 1 · Status: in progress

## Session 2026-11-06

The user restored cancelled tasks to scope, requested smallest-to-largest KAN order and authorized continuing after preparing KAN-77 for publication. KAN-100 implements the current per-database row guard of approved platform STORY-001, AC-001.28–32, and the row-guard part of iteration 1 Task 5. It does not restore retired `workspace_id` policies or reuse retired AC-003 ids. Platform routing/provisioning (KAN-98/99, Task 6/7) remain their own integrations.

Branch: `feat/KAN-100-platform-tenant-row-guard`, worktree `T-Solve-kan100`, based on cached `origin/main` 62bd608. Remote freshness is not verified because the environment cannot reach GitHub. Existing approved commit 7c6920e was applied without committing to repair BUG-122; approval records were not recreated. `sdd.ts diff platform` confirms the current spec is unchanged since approval (05/11 15:23:53Z).

### Implementation scope

- Singleton `tenant_identity`, stable guard function checking `app.tenant_id`, and one policy installer rerun after all module migrations.
- Real PostgreSQL tests for the runtime role, missing/wrong settings, read/write protection, singleton identity and policy/role audit failures.
- Existing tenant schema conventions/UUID migration remain owned by their planned platform/schema tasks; this task does not rewrite merged legacy migrations.
- No new frontend screen is specified for this infrastructure task. Acceptance concerns the database protection behind the existing API consumers; real host-to-database routing and request tests depend on KAN-99.
- Provisioning must seed the identity, revoke identity writes and provide a non-owner, non-superuser, non-BYPASSRLS runtime role, as decision R6 specifies.

### Bug investigation

BUG-128: the design's formatted-SQL example uses `runOrder:last`, which Liquibase does not support for formatted SQL. Use an XML changeset with `sqlFile`, preserving the approved last-run behavior. A real Liquibase parser regression includes a later module to verify actual changeset ordering.

Sources: [Liquibase runOrder](https://docs.liquibase.com/reference-guide/changelog-attributes/runorder), [PostgreSQL 16 row policies](https://www.postgresql.org/docs/16/ddl-rowsecurity.html). Runtime roles must not own protected tables or bypass RLS; the owner remains able to migrate/backfill as approved decision R5 requires.

### Verification

- Red: the real Liquibase parser test failed with `later-module-table` last instead of the expected policy installer; 1 failure, no errors. After implementation, it passed. Two additional parser checks load the SQL resources and emit each PostgreSQL function/DO block as one JDBC statement.
- Mutation check: removing only XML `runOrder="last"` reproduced the ordering failure (1 assertion failure, no errors). The changeset was restored byte-for-byte (SHA-256 A48B18DCEB38011678B69EF1AD9B1F4A5349BD978B10E4F0FA2459BDE932C248); the final targeted run passed all 3 parser cases. No mutant remains in the worktree.
- `mvn -B -o test`: **531 passed, 0 failures/errors/skips**, BUILD SUCCESS (13:34:55 +07). This includes all 3 new changelog parser cases, not PostgreSQL policy behavior.
- Final `mvn -B -o test-compile`: passed after adding the AC-003.10 integration assertion. All test source compiles.
- Database attempt: `mvn -B -o test-compile failsafe:integration-test failsafe:verify '-Dit.test=RowGuardIT,RowGuardConventionIT'` stopped in both classes' setup because no valid Docker environment is accessible. **2 setup errors; no SQL business assertions executed.** The corrected command quotes the dotted Maven property for PowerShell.
- The same Spotless 2.43.0/Palantir formatter through an isolated temporary formatter POM checked all 4 new Java files successfully. Standard `mvn -B -o spotless:check` fails to discover the Git repository from this worktree. Application pom.xml is unchanged; no gate was disabled.
- `sdd.ts trace platform STORY-001 STORY-003`: 6/21 AC IDs have source tests. All 5 row-guard IDs AC-001.28–32 and AC-003.10 are covered. Remaining STORY-001 items concern other platform tasks; the module is not fully implemented. Test-name trace does not prove database execution.
- `gitleaks dir backend/src/test/java/vn/tsolve/common/tenant --redact --no-banner --log-level warn`: passed. `git diff --check`: passed.

### Integration and publication

The current runtime datasource still depends on KAN-99 for transaction-local `app.tenant_id` and correct database selection, and on provisioning for seeding identity and applying decision R6 grants. The tests implement these conditions only inside isolated Testcontainers databases; no application runtime adapter is fabricated. Registry/Jobs role provisioning and the wider legacy-schema convention migration remain with their planned tasks.

No new commit, push or PR has been created for KAN-100. Leave the row-guard feature in progress until the real database assertions and integrations are verified. Proposed eventual code commit: `feat(platform): add the per-workspace tenant row guard (KAN-100)`.

Next numeric task is KAN-138. Its Jira description requires masking email, phone, person names, IP and tokens before Ollama, with Vietnamese unit cases. A question about person-name detection scope is pending; no masking behavior has been guessed or coded.

### Docker recheck after the user's startup confirmation

The user reported that Docker/PostgreSQL was started. A fresh `docker info --format '{{.ServerVersion}}'` still returned `permission denied` for `npipe:////./pipe/dockerDesktopLinuxEngine` from the Codex sandbox. This is an access failure, not evidence that Docker is stopped. No repeated integration run was launched against the same denied API, and no SQL result is claimed.

Prepared `Capstone/test-kan77-kan100-db.ps1` for normal PowerShell outside Codex. It checks Docker access, runs only the new KAN-100 and KAN-77 database suites sequentially, and saves logs plus exit codes in `Capstone/database-test-results/`. Its syntax is checked; it has not been executed outside the sandbox, and results are pending.

### Full access: real PostgreSQL verification

The user's access change made Docker, GitHub and subprocesses available. The first unrestricted run uncovered a separate old docker-java client/API discovery failure (HTTP 400, BUG-129). The documented per-run system property `-Dapi.version=1.44` resolved that mismatch without a dependency or global configuration change.

`mvn -B -o test-compile failsafe:integration-test failsafe:verify '-Dit.test=RowGuardIT,RowGuardConventionIT' '-Dapi.version=1.44'`: **30 tests passed, 0 failures/errors/skips**, BUILD SUCCESS at 13:58:10 (+07), 46.690 s. The actual database assertions above now have evidence. Log: `backend/.kan100-postgres-api144.log`.

The standard Spotless linked-worktree discovery issue is retained as BUG-130. Real application routing/provisioning remains a separate integration dependency; no application identity/router or membership adapter was fabricated. No KAN-100 commit or PR has been created.

### Publication requested, 06/11 14:28 (+07)

The user explicitly authorized pushing KAN-100 and creating a pull request, then continuing the next tasks. Publish a draft against main with the successful 531-unit/30-PostgreSQL results and the remaining routing/provisioning/formatter integration limits. Preserve every bug row. Separate the carried approval-JSON repair, row-guard product/test changes and harness tracking into scoped commits; use normal hooks.

Prepared PR description: `artifacts/06-evaluation/platform/kan100-pr-description.md`. Leave feature status in progress; a draft PR is not an end-to-end acceptance claim.

Code commit: default@d1f95c7 (tenant row guard); prerequisite approval-JSON repair: ea0ec33. Both commits used the normal hooks. The user authorized a draft PR with remaining integration dependencies recorded.
