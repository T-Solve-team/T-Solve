# Test Report: identity — Task 23a: keycloak-prerequisites (KAN-287)

**Date:** 2026-11-07
**State:** the uncommitted worktree `T-Solve-KAN-287` on `feat/KAN-287-identity-keycloak-prerequisites` (base `b8ba9e0`)
**Commands:**
- `cd backend && mvn -B -Dtest='DevWorkspaceLookupTest,AuthorizeControllerTest,CallbackControllerTest' -Dit.test='RedisWiringIT,SecurityConfigIT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -DargLine=-Dapi.version=1.44 -Dspotless.check.skip=true verify`. The log is outside the repo.
- `bun harnessclaude/hooks/sdd.ts trace identity STORY-001`

## Results
| Suite | Tests | Failures | Errors | Skipped | Time |
|---|---|---|---|---|---|
| DevWorkspaceLookupTest | 18 | 0 | 0 | 0 | 0.7 s |
| AuthorizeControllerTest | 30 | 0 | 0 | 0 | 11.6 s |
| CallbackControllerTest | 124 | 0 | 0 | 0 | 5.7 s |
| SecurityConfigIT | 1 | 0 | 0 | 0 | 27.7 s (container start) |
| RedisWiringIT | 3 | 0 | 0 | 0 | 8.8 s |

- Unit: 172/172 passed. IT: 4/4 passed. `BUILD SUCCESS`.
- Compile: pass. Spotless: not run (`-Dspotless.check.skip=true`, because JGit cannot resolve the worktree's `.git` file). It must run from a normal checkout before the commit.
- Full verify: not re-run, per the card. The developer's run on the same state: unit 608, IT 42, BUILD SUCCESS.

## Coverage
No coverage command is configured for the backend, and the spec gives no threshold.

## Acceptance Criteria → Tests
`sdd.ts trace identity STORY-001`: rc 1, `19/56 acceptance criteria have a test`. This is pre-existing. Task 23a traces
no AC by design. Its tests carry the edges 125–130 (design.md Edge Cases).

## Manual checks run by the reviewer
- Compose interpolation: `docker compose --env-file .env.example config --services` with `REDIS_PASSWORD` unset gives
  `required variable REDIS_PASSWORD is missing a value`. So `docker compose up -d db` is blocked too (review issue 1).
- Health check authentication, on a throwaway `redis:7.4-alpine --requirepass s3cr3t`: `redis-cli ping | grep -q PONG`
  gives rc 0 with `REDISCLI_AUTH=s3cr3t`. With an empty or wrong value it gives rc 1 (`WRONGPASS`, `NOAUTH`).

## Failures
None.

## Notes
- No flaky or skipped tests. The slowest step is SecurityConfigIT, which starts Postgres and Redis containers.
- The Docker 29 workaround `-Dapi.version=1.44` is passed only on the command line. It appears only in the ignored `backend/target/` reports.
