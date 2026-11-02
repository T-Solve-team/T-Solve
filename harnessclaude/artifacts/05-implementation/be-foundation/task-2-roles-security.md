# Task 2: Five roles, method security and CSRF — KAN-117

**Module:** be-foundation
**Assignee:** Duong pham tung (with Codex)
**Status:** evaluating
**Iteration:** 1

## Session 2026-11-03

### Working on: roles-security
- Branch: `feat/KAN-117-be-foundation-roles-security`, created from freshly fetched `origin/main`.
- Preserved the user's untracked database SQL/PDF files and existing `graphify-out/`.
- Added five workspace roles, immutable UUID principal with Employee included, and `@WithTSolveUser`.
- Replaced the two catch-all security chains with one web chain. Identity keeps its redirect chain and Jira App keeps its FIT chain and separate machine authority (`JIRA_APP`, not a workspace role).
- Enabled the documented Spring SPA cookie/header CSRF recipe, CORS integration, public health/docs, deny-all outside permitted paths, MSG07/MSG08 errors and no Basic/form/generated-password login.
- Kept the container ERROR-dispatch exception required by identity's existing regression tests.
- Existing `MethodSecurityConfig` remains the single method-security configuration, imported by the web configuration.
- Updated the two Jira App tests to reference the replacement web configuration.
- Added principal and MockMvc tests, including actual cookie/header CSRF round-trip and an invalid token.
- No new Maven dependencies, frontend contract changes, commits or pushes.

### Verification
- RED: Maven test compilation failed because the new role, principal, configuration and test annotation did not exist.
- Java 25 bundled with IntelliJ cannot run the pinned Spotless formatter. Downloaded official Eclipse Adoptium JDK 21 into ignored `backend/target/kan-117-tools/`; did not change system Java.
- First JDK 21 verification: 271 unit tests, 0 failures/errors, 1 existing skip (`AuthorizeControllerTest.handlesCallbackOnLogin_AC_001_5`, disabled pending callback implementation). Two database integration classes could not initialize because Docker was stopped.
- Started installed Docker Desktop. Final verification with JDK 21 and process-local Docker compatibility settings: `mvn verify -Dapi.version=1.44 -B -ntp` → BUILD SUCCESS (58.081 s). Unit tests: 271, 0 failures/errors, 1 existing callback skip. PostgreSQL integration tests: 10, 0 failures/errors/skips. Spotless check passed. Exact log: `backend/target/kan-117-verify-final.log`.
- Docker 29.9.2 rejected the pinned Testcontainers default API (BUG-094); a process-local `-Dapi.version=1.44` and `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` allow PostgreSQL to start. No system config or Maven dependency was changed.
- Full-context integration tests also require the tracker placeholders, so the new test-only configuration supplies a dummy app id and reserved `.example.test` JWKS address. The JWT tests retain their own local keys/decoder.
- New test classes: `SecurityConfigTest` (12 tests) and `TSolveUserTest` (3 tests), all passing. A direct source check finds all 13 STORY-002 AC ids; this is supplementary evidence and does not replace the failed formal trace command.
- Manual review checked configuration precedence, the FIT exception, method security import, immutable principal, anonymous/authenticated CSRF error distinction, CORS and error serialization. `git diff --check` passes.
- `sdd.ts check be-foundation`: 4 stories, 42 criteria, OK; audit confirms this module's approval remains valid.
- `sdd.ts trace be-foundation STORY-002`: fails at 0/13 because the framework excludes `*Test.java` and does not normalize Java AC ids. Existing FEEDBACK entries dated 2026-10-25/28 describe both limitations; logged BUG-093. Framework and gate were not modified or bypassed. Every STORY-002 id is referenced in the new Java test method names.
- Graphify refreshed code into ignored `backend/target/kan-117-graph/graphify-out/graph.json` (3976 nodes, 10711 edges), preserving the existing mixed code/document graph. SQL indexing unavailable (`tree_sitter_sql` missing); code-only run intentionally skipped documents. New security nodes verified through a graph query and source reads.

### Earlier blockers
- Formal AC trace remains blocked by BUG-093. Leave feature `in-progress`; do not transition to evaluating/passing until the required trace gate works.
- After the user authorized BUG-093, the PreToolUse hook rejected the test-file addition: `harnessclaude/hooks/ enforces the workflow, so the agent never changes it. If a hook is wrong, tell the user; framework files are refreshed by harnessclaude upgrade.` No hook file was modified. The quick command also explicitly reports that harness files never need a quick change.
- Prepared `BUG-093-proposed.patch` for human review/application: Java Test/IT discovery, underscore-id normalization, and four regression tests covering normalization, ownership/unknown ids, malformed ids/story selection, and CLI success/failure. `git apply --check --ignore-space-change <patch>` passes (ignore whitespace accommodates the existing Windows CRLF files). The proposed tests have not run because the patch is unapplied.

### Integration notes
- Identity should construct `TSolveUser` and use its `authorities()` when filling the session security context.
- Feature controllers should declare `@PreAuthorize` with workspace `Roles` constants. Data scope checks remain KAN-118.
- Browser mutations must send the readable `XSRF-TOKEN` cookie value as `X-XSRF-TOKEN`.

### Next up
- User applied the patch. Regression verification: `bun test harnessclaude/hooks/lib/spec.test.ts` → 4 pass, 0 fail; `sdd.ts trace be-foundation STORY-002` → 13/13, exit 0. Running the three parser regression tests against an isolated copy of HEAD's original `spec.ts` produces 2 failures and 1 pass, proving the new cases detect BUG-093; the temporary baseline copies were then removed. No live hook was reverted by the agent.
- BUG-093 is fixed in the working tree; the formal trace blocker is resolved. `sdd.ts check be-foundation` and `git diff --check` pass. Await user approval of the code and separate harness commits; leave tracking `in-progress` until the commit step is performed in order.
- After the user's patch application, the full backend verification was rerun: `mvn verify -Dapi.version=1.44 -B -ntp` → BUILD SUCCESS (1 min 11 s, 2026-11-03 02:34 Asia/Bangkok), with the same 271 unit tests (1 existing callback skip), 10 passing integration tests and passing Spotless check. Log: `backend/target/kan-117-verify-final.log`. The code-only Graphify refresh now contains 3979 nodes and 10717 edges; SQL parser limitation remains unchanged.
- Proposed code commit after required gates pass and the user agrees: `feat(auth): implement role union and method security (KAN-117)`; body `STORY-002`, with AI co-author trailer. Commit harness tracking separately per project rules.

### Commit and push hand-off
- User authorized both commits and push to the current branch.
- Code commit: `default@673f3abcfd893c972558b3e8630b9e2bba859bac` (`feat(auth): implement role union and method security (KAN-117)`). Secret scan, pre-commit and commit-msg hooks passed.
- Recorded the code commit through `sdd.ts commit be-foundation default`; reran STORY-002 trace (13/13), then moved the feature to `evaluating` through `sdd.ts feature`.
- The second commit contains the user-applied BUG-093 fix, regression tests, bug log and feature tracking. The feature awaits evaluation; the module is not complete. No next feature started.
- Push destination: `origin/feat/KAN-117-be-foundation-roles-security`. Database reference files and the original graph remain untracked and excluded.
