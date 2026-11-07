# Test Report: worker — Task 1: tenant-jobs

**Date:** 2026-11-07
**Branch:** feat/KAN-102-worker-tenant-context
**Code commit:** 68bd287b663f5dd4bacb533c5883a981e4abbf11 (same source as the verified tree)
**Environment:** Temurin JDK 21.0.13+9, Maven 3.9.9, Docker Desktop Engine 29.9.2, PostgreSQL 16.

## Commands and results

- `mvn -f .kan-102-verify-pom.xml spotless:apply`: BUILD SUCCESS; 17 Java files checked, 9 formatted, 8 already clean.
- `mvn -f .kan-102-verify-pom.xml "-DargLine=-Dapi.version=1.44" verify`: BUILD SUCCESS, 193 seconds; 608 unit tests and 40 integration tests; zero failures, errors or skips. Completed at 11:55:45 local time.
- `mvn -f .kan-102-verify-pom.xml spotless:check`: BUILD SUCCESS, all 17 Java files clean. Completed at 11:56:32.
- `bun harnessclaude/hooks/sdd.ts check worker`: 1 story, 16 criteria — OK.
- `bun harnessclaude/hooks/sdd.ts trace worker STORY-002`: 16/16 criteria have a test.
- `bun harnessclaude/hooks/sdd.ts audit`: OK, 17 existing/framework warnings; no worker contract error.
- `git diff --check`: pass.
- Graphify incremental extraction and subsequent query: updated worker symbols present; 5281 nodes, 15830 edges. SQL parser unavailable; no SQL schema changes in this feature.

## Worktree verification limitation

Plain `mvn spotless:apply` cannot discover this linked Git worktree with Spotless 2.43.0 (upstream https://github.com/diffplug/spotless/issues/2209). Setting GIT_DIR/GIT_WORK_TREE did not solve it. This is not a source-format pass claim for that failing command.

For final checks, the disposable `.kan-102-verify-pom.xml` was copied from the actual backend POM in the same directory. It differed **only** in replacing ratchetFrom with NONE and explicitly including all 17 changed worker Java files; LF was set to match `.gitattributes`. The same Spotless 2.43.0 / Palantir 2.39.0 formatter and removeUnusedImports ran. All compile, unit and integration test configuration was identical. A reverse comparison confirmed these were the only differences. The temporary POM was then removed; the actual POM still has the team's origin/main ratchet. No skip flags or test suppressions were added.

The original JBR 25 could run tests with --release 21 and Byte Buddy's experimental flag, but its javac API was incompatible with the pinned formatter. Final checks used the project's Java version, with an official Temurin download verified against its published SHA-256 in ignored build output.

## Feature evidence

18 focused unit/config tests plus 1 real PostgreSQL JobRunr integration test pass. The integration test uses a dedicated Jobs database and a separate business database in an isolated container. It persists three jobs in an enqueue-only context, closes that context, then starts a new worker context. A and B execute under their stored workspaces; one job fails intentionally and succeeds on native JobRunr retry with its original workspace/record ids. No JobRunr table is created in the business database.

## Acceptance Criteria → Tests

| AC | Evidence |
|----|----------|
| AC-002.1 | TenantJobServiceImplTest payload round trip; JobRunrTenantIT persisted request after context restart |
| AC-002.2 | rejectsUnboundAndInvalidEnqueueWithoutPersisting_AC_002_2 |
| AC-002.3 | bindsOnlyTheJobWorkspace_AC_002_3; actual JobRunr dispatch IT |
| AC-002.4 | rejectsSuspendedUnknownAndNullWorkspace_AC_002_4_AC_002_11 |
| AC-002.5 | failsClosedWhenCatalogIsUnavailable_AC_002_5 |
| AC-002.6 | restoresContextAndPreservesOriginalFailure_AC_002_6 |
| AC-002.7 | rejectsCrossWorkspaceNestingWithoutChangingOuterBinding_AC_002_7 |
| AC-002.8 | restoresSameWorkspaceNestedScopeIncludingFailure_AC_002_8 |
| AC-002.9 | isolatesConcurrentAndReusedThreads_AC_002_9 |
| AC-002.10 | rechecksWorkspaceStatusAndPreservesFailureForRetry_AC_002_10; native retry IT |
| AC-002.11 | explicit mock profile, unknown/Suspended allow-list and empty-list tests |
| AC-002.12 | default no mock; enabled integration without platform adapter fails startup |
| AC-002.13 | missing Jobs settings rejects fallback; IT verifies distinct business DataSource |
| AC-002.14 | default-disabled configuration/scan regression; actual worker profile starts server in IT |
| AC-002.15 | rejectsMalformedPersistedJobs_AC_002_15 |
| AC-002.16 | JobRunrTenantIT uses actual PostgreSQL storage, serialization, server and retries |

## Defects discovered and resolved

| Bug | Root cause | Verification |
|-----|------------|--------------|
| BUG-135 | Spring empty Set-of-UUID conversion supplied null | Named regression failed before string-list parsing fix, then passed. Empty now denies all. |
| BUG-136 | Global ConfigurationPropertiesScan eagerly registered validated worker properties while disabled | Named scan regression failed before conditional properties annotation, then passed. The final full suite's existing application ITs pass. |

Both rows are in docs/bug-log.md. User appended BUG-136 manually after the harness privacy hook blocked the agent's metadata-only logging command.

## Notes

No frontend or OpenAPI changes; frontend checks are not applicable. No coverage instrumentation is configured for this Maven feature, so no numeric coverage claim is made. The 16/16 trace is criterion traceability, not line/branch coverage. The only compiler warning concerns existing MapStruct options in test compilation. Native retry IT intentionally logs its first-attempt failure. No remaining product test failure.

Mock context propagation is verified; actual tenant database routing remains platform's responsibility. Non-web deployment, per-workspace concurrency caps, recurring jobs and SMTP remain KAN-161/162. User confirmed feature and harness commits; the code commit passed the repository's secret scan and Git hooks.
