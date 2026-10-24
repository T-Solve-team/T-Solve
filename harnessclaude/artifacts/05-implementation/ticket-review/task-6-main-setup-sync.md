# Main setup and rules integration — 23 October 2026

Integrated main `9fbc7dc` into `feat/ticket-review`, based on branch `3d3c26e`. User approved commit and push. Code merge committed as `default@02393e1`; this companion harness commit records verification and bug-reference updates.

## Applied changes

- Imported main's frontend foundation: five roles, multi-role session, Project memberships, workspace readiness, managed Spaces, guards, menus, route map, logout and full Report3 catalog with its committed reference and generator.
- Kept the approved review Project filter, transactional API, one-comment batch Request Changes (D1), and no empty-Solution handling (D2). Regenerated the merged OpenAPI contract using orval; preserved review detail invalidation after decisions.
- Adapted the test-only manual session endpoint to the new CurrentUser shape. This does not implement production identity, Project provisioning or email delivery. Main's backend foundation is a plan, not a delivered implementation.
- Removed retired STAFF/DEPARTMENT_MANAGER reader authorities (BUG-033). Red test returned 200 instead of 403; the named regression now passes.
- Corrected the Project filter regression to select managed ITNET, assert unmanaged HRHELP is absent, and check the URL, filtered row and all-selected count (BUG-034). Red full suite: 413 passed, 1 failed; green: 414 passed.
- Preserved all five main bug rows exactly. Per the user's explicit collision-resolution instruction, shifted every branch review bug from old BUG-003…027 to BUG-010…030 and updated references; new findings are BUG-033/030. Main BUG-004 and historical branch BUG-031 describe the same suite stability issue; imported main #42 remediation and verified it with default workers.
- Merged framework registries, existing approval records, chronological event history and module states without inventing approvals or resetting active review work.

## Setup implications

Use Node 24 / Java 21. Vitest now uses main's `maxWorkers: '50%'`; the queue test preloads its lazy module and waits up to 5 seconds for first-render UI, as required by the incoming testing guideline. No test is skipped or assertion suppressed.

`npm run messages:report3` requires the team Markdown source at `$TSOLVE_DOCS/Report3/System_Messages.md` (or an explicit source argument). The available local team copy contains a DOCX rather than that Markdown source. The incoming committed reference/catalog were preserved; builds and tests do not require regeneration from external documents. No personal absolute path was added to the manifest.

## Verification

- `mvn -o spotless:apply verify -B`: 42 tests, 0 failures/errors/skips; BUILD SUCCESS on isolated PostgreSQL 16, 23 October 2026. Initial execution failed because Docker/database was stopped; started the existing test container and reran successfully. Evidence: ignored `backend/target/main-sync-verify.log`.
- Frontend format:check, lint, typecheck and build passed; default `npm test`: 25 files, 414 tests, no unhandled errors. jsdom's existing pseudo-element getComputedStyle warnings remain visible.
- Repeated `npm run generate:api`: all generated file SHA-256 values unchanged.
- Bug ledger script: main BUG-001…005 exact, IDs 001…030 consecutive, review Found by/Owner full names verified.
- `sdd.ts audit`: OK. Both staged and unstaged `git diff --check`: clean. Review spec unchanged.
- Refreshed local Graphify: 1,877 nodes, 4,124 edges. Two SQL files could not be indexed because tree_sitter_sql is unavailable; SQL source remains directly inspectable. Graph output stays local/untracked.

No new live browser/HTTP smoke run was performed for this integration; the prior 19-check result remains historical. Current session adapter behavior is covered by two new JUnit tests. Existing open framework trace/privacy issues and narrow Ticket Detail layout remain in BUG-017, BUG-029 and BUG-032.

Local commit tooling: installed the locked root dev tools and enabled the existing Husky hooks; installed required Gitleaks. Secret scan, lint-staged and commitlint passed for code commit 02393e1. Initial commit message body exceeded 100 characters; wrapped it and committed through the same hooks without bypass.
