# Test Report: identity — Task 35: user-status-backend

- Commit under test: `2432988d` (code range `2ad8f6ce..2432988d`); harness commit `3e8cd951` not evaluated as code.
- Date: 2026-11-10 · Reviewer: harnessclaude-reviewer (subagent)

## Full suite (`mvn verify`, run by the dispatcher on this commit; reports read by the reviewer)

Totals summed from `backend/target/surefire-reports` and `backend/target/failsafe-reports`:

| Runner | Classes | Tests | Failures | Errors |
|--------|---------|-------|----------|--------|
| Surefire (unit, `*Test`) | 108 | 1086 | 0 | 0 |
| Failsafe (IT, `*IT`, `-DargLine=-Dapi.version=1.44`, BUG-058) | 51 | 391 | 0 | 0 |

Relevant classes:

| Class | Tests | Failures | Errors |
|-------|-------|----------|--------|
| `UserStatusIT` | 14 | 0 | 0 |
| `UserRoleUpdateIT` (KAN-110 regression) | 13 | 0 | 0 |
| `MockSignedInCallerIT` (AC-006.19 seed) | 7 | 0 | 0 |

## Rerun by the reviewer

`mvn -q -B test -Dtest=UserStatusUpdateServiceImplTest,UserDetailControllerTest,UserRoleUpdateServiceImplTest`:

| Class | Tests | Failures | Errors |
|-------|-------|----------|--------|
| `UserStatusUpdateServiceImplTest` | 43 | 0 | 0 |
| `UserDetailControllerTest` | 25 | 0 | 0 |
| `UserRoleUpdateServiceImplTest` (KAN-110 regression after the `HandoverChoices` extraction) | 46 | 0 | 0 |

The ITs were not rerun (about 30 min with Docker); their reports from the dispatcher's `mvn verify` run on this
commit are listed above.

## Format and contract

- `mvn spotless:check`: cannot run in a linked worktree (BUG-130, `docs/bug-log.md`). The dispatcher reports the
  same Palantir formatter passing on the 11 changed Java files through a temporary POM without `ratchetFrom`. Not
  re-checked by the reviewer.
- `npm run generate:api`: no diff (dispatcher). No contract change in the range (`frontend/openapi/tsolve-api.yaml`
  untouched).

## Traceability

`bun harnessclaude/hooks/sdd.ts trace identity STORY-006` → 19/19 acceptance criteria have a test (AC-006.9 to
AC-006.19 in `UserStatusUpdateServiceImplTest`, `UserDetailControllerTest`, `UserStatusIT`, `MockSignedInCallerIT`).

## Coverage

No coverage tool is configured for the backend; coverage was judged by reading the tests against each criterion
(see the review).

## Defects

No new defect found. Known environment bugs: BUG-058 (Docker 29 API version for Testcontainers), BUG-130 (Spotless
`ratchetFrom` in a linked worktree).
