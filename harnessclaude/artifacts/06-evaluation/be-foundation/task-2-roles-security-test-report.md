# Test Report: be-foundation — Task 2: roles-security

**Date:** 2026-11-04 (Asia/Bangkok)
**Scope:** PR #107 BE lead review repair after merging main `3ff7218`.

## Results

| Check | Result | Evidence |
|-------|--------|----------|
| Full backend verify | PASS | Java 21, Maven verify with Docker API compatibility flag; BUILD SUCCESS in 1 min 29 sec |
| Unit tests | PASS | 427 tests, zero failures/errors/skips |
| PostgreSQL integration tests | PASS | 18 tests, zero failures/errors/skips |
| Focused security tests | PASS | 31 tests across TSolveUser, writer, web and FIT chains |
| Spotless | PASS | 23 changed Java files clean |
| Hook regressions | PASS | bun test harnessclaude/hooks/lib/spec.test.ts: 4/4 |
| Acceptance trace | PASS | trace be-foundation STORY-002: 13/13 |
| Spec check | PASS | 4 stories / 42 acceptance criteria, OK |
| Secret scan | PASS | Gitleaks security production directory, no findings |
| Whitespace | PASS | git diff --check |
| Frontend | No unique change | No frontend diff versus merged main; no frontend suite rerun needed for this backend repair |

Portable verification commands: `mvn verify -Dapi.version=1.44` in backend, then the
Harness hook tests, spec check and STORY-002 trace from repository root. Requires JDK 21
and a running Docker engine. The API-version flag is the existing Docker 29 workaround.
On Windows this run selected Docker Desktop's Linux pipe process-locally; no system or
dependency configuration was modified. Raw logs remain ignored under backend/target.

## RED to GREEN

- BUG-109 test failed before implementation: Authentication.getName returned the complete
  TSolveUser record instead of UUID; immutable ordered role assertion also failed.
- BUG-110: the first standalone test omitted Spring's ProblemDetail Jackson mixin and
  therefore initially checked the wrong JSON layout. Corrected test setup, reran against
  unchanged production writer and confirmed the actual missing params failure. The final
  unit test and FIT HTTP test both assert the canonical envelope and token exclusion.
- Focused tests pass after implementing the principal and shared writer repairs.
- First full verify: all 427 unit tests passed, but all integration classes could not
  initialize because Docker Desktop was stopped. Started the installed Docker Desktop,
  then reran the same full verification: all 427 unit and 18 integration tests pass.
- Real full-context health test asserts 200 and UP, with PostgreSQL and actuator loaded;
  the previous 404-acceptable slice assertion is removed. Generated UserDetailsService
  absence is asserted against the application context.
- Mutation tests retain actual cookie/header round trips for POST/PUT/PATCH/DELETE;
  missing and invalid tokens yield the specified 403 MSG07. Anonymous valid-CSRF POST
  and outside-path requests yield 401 MSG07; authenticated outside path yields 403 MSG08.
- Existing identity redirect/callback, Jira FIT and import tests pass after moving the
  shared-security import from production identity configuration to its five test slices.

## Review and merge boundaries

Only docs/bug-log.md and Harness event history conflicted with main; both branches' entries
were preserved in `9b4abdc`. No protected Harness hook changed. The redundant proposed patch
is deleted; `21096b3` retains its applied correction in history. Existing published commits
were not rewritten. No original SQL/PDF, schema, API YAML or dependency manifest changed.

No numeric coverage threshold or blanket production-security certification is claimed.
Deployment proxy/cookie policy, FE CSRF transport and sign-in token rotation are follow-ups
owned outside STORY-002's repair scope.

## Subsequent main update — 2026-11-04

Main advanced to `969fa18` during the earlier verification window. The PR's reported base
SHA lagged the live main ref, so comparing that SHA alone missed a new append conflict in
`harnessclaude/events/events.jsonl`. The browser conflict editor identified the two new
identity approval events overlapping the four role-security repair events.

Fetched the live main ref and preserved both complete histories. All 534 resulting JSONL
lines parse; every original event line from HEAD and main is retained. Audit passes with
12 pre-existing warnings; identity spec check passes (10 stories/137 ACs); STORY-002 trace
remains 13/13. This merge changes only Harness documentation/approval metadata, so the
427 unit/18 integration result remains applicable without repeating unchanged code tests.
