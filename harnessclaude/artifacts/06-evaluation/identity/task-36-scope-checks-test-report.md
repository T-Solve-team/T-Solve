# Test Report: identity — Task 36: scope-checks

- Code under test: uncommitted work on `feat/KAN-118-identity-scope-checks` (base `cbfdcadf`), after the seed fix of
  `ScopeAccessIT` (two `PROJECT_MANAGER` rows on P1 broke `uq_membership_one_manager`; DEACTIVATED now manages `PD`).
- Date: 2026-11-10 · Runs: main session (Claude), `mvn verify -DargLine=-Dapi.version=1.44` (BUG-058), Docker Desktop up.
- First reviewer pass (harnessclaude-reviewer) read stale reports with Docker down; this report replaces its IT section.

## Full `mvn verify` (final run, 2026-11-10 evening)

| Phase | Tests | Failures | Errors |
|-------|-------|----------|--------|
| Unit (surefire) | 1092 | 0 | 0 |
| Integration (failsafe) | 409 | 2 | 0 |

- `ScopeAccessServiceImplTest` 6/6 and `ScopeAccessIT` 14/14 pass in the full run.
- The 2 failures are in `vn.tsolve.review.ReviewQueueEventsIT` (AC-012.1, AC-012.2: a 5 s wait and a null event), code
  this change does not touch. An earlier full run failed a different test of the same class (BUG-172 test,
  `ConcurrentModificationException`). Logged as **BUG-197** (related to BUG-186). Run alone after the change:
  `ReviewQueueEventsIT` 18/18, `ScopeAccessIT` 14/14. CI on `main` is green.

## Format

`spotless:check` in the worktree fails with "Cannot find git repository in any parent directory" (BUG-130, linked
worktree). The six new files were checked separately with the same plugin (2.43.0, `palantirJavaFormat`,
`removeUnusedImports`, LF endings) in a temporary POM: no violation.

## Frontend contract

`cd frontend && npm ci && npm run generate:api` → exit 0, no diff in `src/shared/api/generated` (no contract change).

## Traceability

`bun harnessclaude/hooks/sdd.ts trace identity STORY-011` → 11/11 acceptance criteria have a test.

## Coverage

No coverage tool was run. Every branch of the service has a unit test (null, unbound, true / false, throw / pass); each
SQL predicate has a positive and a negative IT case, including cross-Department, cross-Project and cross-Workspace
refusals.
