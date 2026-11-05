# Task 23: UUID User List read integration — KAN-107

**Module:** identity
**Assignee:** Phạm Tùng Dương
**Status:** passing
**Iteration:** 1

---

## Session 2026-11-05

### Working on: user-list-uuid-read
- User selected current main UUID architecture for PR #119 and manually approved the amended identity spec (version 898efa93). The implementation consumes platform's UUID contracts; it does not create another tenant runtime.
- Integrating main 9e85121 preserves both event histories and the current platform spec/approval. Original SQL feature commits remain in Git history; their reports are marked superseded integration evidence.
- Retire the branch-only numeric catalog, router, pool manager and bootstrap resources/tests. Keep the canonical UUID TenantContext port; schema provisioning, primary routing/transaction manager and real session issuance are explicit deployment prerequisites.
- Use shared TSolveUser plus server-created WorkspaceSessionBinding, primary JDBC/transaction resources, fail-closed row-guard checks, canonical SecurityProblemWriter and shared CORS. No SQL/PDF original or applied legacy changeset is changed.
- BUG-112/113/114 track CORS/error-writer/UUID integration. Old branch BUG-107/108 are retained as BUG-115/116 to avoid collisions with main.
- Frontend contract changes precede implementation. Numeric mock adapters are removed, UUID navigation is restored, and the list-only omission of Last sign-in and old-sort normalization are retained.

### Blockers
- The platform tenant router, role/Department schema and trusted Workspace session issuer remain prerequisites. The feature stays disabled pending integration.
- Main advanced to `ed34ba3` (KAN-235) during verification. The merged Account migration defines `id`, `email` and `status`; the earlier User List query and test fixture use different column names (BUG-118). AC-004.9 was updated and the merged spec requires a fresh approval before the repository and fixtures are edited.
- The local merge now targets `ed34ba3`. Git reports no unmerged files and the complete frontend tree matches the tested candidate.

### Verification before KAN-235
- Java 21 Maven verification passed with 437 unit and 29 PostgreSQL integration tests. Rerun after switching the fixture to the KAN-235 migration.
- Frontend formatting, lint, typecheck and build passed. The generated API was reproducible across 137 files. The focused User List/UUID/MSW run passed 38 tests. The first full run passed 1,240 of 1,313 tests and failed 73 (BUG-070). The complete two-worker recheck passed all 1,313 tests with zero failures or pending tests (`pr119-uuid-full-2workers.json`).

### Verification on ed34ba3
- The user approved the combined spec. The PostgreSQL fixture now executes Account changeset 006. The earlier repository returned MSG06 for all four repository tests on that schema. The updated queries use the migrated Account columns and both repository and HTTP test classes pass.
- Full Java 21 Maven verification passed 488 unit and 46 integration tests with zero failures, errors or skips. Frontend source matches the 1,313-test recheck. Story trace is 19/19.

### Next up
- The verified code is in `default@5a66616`. Push the branch and update PR #119 with the UUID scope and verification.
