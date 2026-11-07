---
module: identity
feature: keycloak-prerequisites
task: 23a
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: tsolve-code-reviewer (Claude Opus 5.5, independent, read-only)
iteration: 1
---

# Review: identity — Task 23a: keycloak-prerequisites (KAN-287)

## Summary
Scope: the uncommitted diff in worktree `T-Solve-KAN-287` on `feat/KAN-287-identity-keycloak-prerequisites` (base
`b8ba9e0`). That is 12 modified files and 3 new ones: `identity/dev/{DevAccounts,DevWorkspaceLookup}.java`,
`DevWorkspaceLookupTest`, `RedisWiringIT`, plus design rows 125–130. The change matches tasks.md "Task 23a" and
decisions Q1–Q9 (all A). It does not touch sign-in behaviour, Keycloak, `identity/session`, `StartCookie` or
changesets 008/009.

I ran 176 targeted tests and all pass. I found no CRITICAL finding and no Critical or High security finding. There is
one Important finding: the documented local setup is stale, and the compose `:?` guard now stops even
`docker compose up -d db`. There are four Minor findings.

Verdict: **pass**. Before the commit, the Important doc fix should go into the same commit, and `mvn spotless:check`
has to run from a normal checkout, because Spotless cannot run in this worktree.

Feature status: the worktree's `progress.json` has no `keycloak-prerequisites` key. The card does not ask me to change
it. I propose `passing` through `sdd.ts feature identity keycloak-prerequisites …`, along the allowed transitions.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every item of the task's test scope is implemented and tested (table below) |
| Completeness | pass | Nothing is MISSING. Nothing is EXTRA beyond tasks.md. No separate Testcontainers Redis dependency was added, which is deliberate: Q7 A uses a `GenericContainer` |
| Correctness | pass | Targeted run: 172 unit tests and 4 ITs, 0 failures, BUILD SUCCESS. The developer's full verify was green on the same content. No CRITICAL finding |
| Integration | pass | `WorkspaceRef` has its two components appended (Q2 A). All 12 constructor calls changed only mechanically. In `dev`, exactly one `WorkspaceLookup` bean exists |

### Stage 1: task scope (no AC is traced here, per tasks.md "Test scope")

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | acme and beta resolve by host, with fixed id, slug and Organization id (edge 125) | PASS | `DevWorkspaceLookup.java:17-31,34-36`; `resolvesAcmeToItsDevWorkspace_edge125`, `resolvesBetaApartFromAcme_edge125` |
| 2 | An unknown host or id answers nothing (edge 126) | PASS | 11 hosts (including `acme.localhost.evil.example`, `xacme.localhost`, `""`) and a random id |
| 3 | DevAccounts agree with the dev Workspaces (edge 127) | PASS | `devAccountsAgreeWithDevWorkspaces_edge127`: the domain after `@` equals `googleDomain`, emails are unique, both Workspaces are covered |
| 4 | The dev lookup exists only under `dev` (edge 128) | PASS | `@Profile("dev")` at `DevWorkspaceLookup.java:13`, `@Profile("!dev")` at `NoWorkspaceLookup.java:12`. The `ApplicationContextRunner` tests break if either annotation is removed (two beans, or a dev bean outside `dev`) |
| 5 | Redis through `REDIS_*`: write, read, expire (edge 129) | PASS | `RedisWiringIT`: the Lettuce host and port equal the container's, the password-protected server is reached both ways through `redis-cli`, and a 1 s TTL key expires |
| 6 | Redis health UP (edge 130), and `SecurityConfigIT` stays green | PASS | `reportsRedisHealthUp_edge130`; `SecurityConfigIT` gets a password-protected Redis container |

### Specific checks from the card
- **No secret committed:** pass. `.env.example` has empty values. The compose file holds no password, only `${REDIS_PASSWORD:?…}`. The test config has `unused` and a closed port 1. The ITs use a random UUID password on each run. `.env` is ignored (`.gitignore:16`).
- **The dev lookup cannot be active without `dev`:** pass. No file in the repo sets `spring.profiles.active`: no yml, no pom plugin config, no Dockerfile. The non-dev `@SpringBootTest` contexts in the full suite start with a single `NoWorkspaceLookup`.
- **`repositories.enabled: false` hides nothing:** pass. `main` has no `@RedisHash` and no Redis repository interface. Task 23b's authorization-request store and Task 26a's session do not need Spring Data repositories. The comment's wording is inaccurate (Minor 3).
- **The compose health check really authenticates:** pass, checked by hand. On a throwaway `redis:7.4-alpine` with `--requirepass`, `redis-cli ping | grep -q PONG` gives rc 0 when `REDISCLI_AUTH` is correct. It gives rc 1 (`WRONGPASS` / `NOAUTH`) when the value is empty or wrong. So a healthy service proves that the health check authenticated.
- **The tests assert behaviour:** pass. Each test would fail if its target were broken, for example a wrong mapping, a missing `@Profile`, a lost TTL, or a mismatched domain. One assertion is a no-op: the "second instance" check in L1 is always true with a static list.
- **The 12 constructor edits are mechanical:** pass. That is 5 constants in `InMemoryWorkspaceLookup`, 1 call in `AuthorizeControllerTest:503` and 6 in `CallbackControllerTest`. Each copies `slug()` and `organizationId()` from the original and changes no other argument. AuthorizeControllerTest (30) and CallbackControllerTest (124) are green.
- **No Docker `api.version` in a committed file:** pass. It appears only in `backend/target/failsafe-reports/*` (ignored), not in `.mvn`, the pom or the test config.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace identity STORY-001`: rc 1, `19/56 acceptance criteria have a test` for STORY-001.
This is pre-existing (the Keycloak re-plan). Task 23a traces no AC by design (tasks.md "Test scope"), and its tests
are named after edges 125–130, which the design adds.

## Issues

**Important**
1. `README.md:70-73` and `backend/AGENTS.md:38`: the local setup no longer works as written. `docker-compose.yml:22`
   uses `${REDIS_PASSWORD:?…}`, and Compose resolves it for the whole file. So `docker compose up -d db` fails
   (`required variable REDIS_PASSWORD is missing a value`; checked with `docker compose --env-file .env.example config`).
   This hits every teammate whose `.env` has no Redis values yet. `mvn spring-boot:run` also fails on
   `${REDIS_HOST}` until `REDIS_*` are set, and `/actuator/health` answers 503 until Redis runs.
   Fix: in step 0, say to set `REDIS_HOST=localhost`, `REDIS_PORT=6379` and a local `REDIS_PASSWORD` of your choice.
   Change step 1 to `docker compose up -d db redis`, and update AGENTS.md:38 the same way. Put this in the same commit.

**Minor**
2. `WorkspaceRef.java:5-6`: the port contract is not written down. Per Q2 A, `slug` is never null, and a null
   `organizationId` means "no Organization" (AC-001.48, .60). This record is what the platform adapter will
   implement. Fix: add a short Javadoc on the record.
3. `application.yml:18`: the comment "keeps them from claiming the JPA ones" is inaccurate. With two Spring Data
   modules, Spring Data uses strict mode and Redis would not claim JPA repositories. The real effect is no Redis
   repository scan and no multi-store log noise. Fix: reword the comment.
4. `RedisWiringIT.java:125-126`: the variable `seconds` holds milliseconds. Lines 114 and 125 use the fully qualified
   names `java.util.List` and `java.util.concurrent.TimeUnit` instead of imports. Fix: rename it `millis` and import
   both.
5. Manual check M1 and the compose file: the "NOAUTH without `-a`" check cannot show NOAUTH inside the container,
   because `REDISCLI_AUTH` is set in the container's environment. That probably explains the "blank" result.
   Fix: run the check as `docker compose exec -e REDISCLI_AUTH= redis redis-cli ping`. Also, a password containing
   spaces or `$` breaks the `command:` string. A note in `.env.example` ("letters and digits") avoids that.

Residual (process, not code): `mvn spotless:check` did not run (the worktree's `.git` file defeats JGit
`ratchetFrom`). Run it from a normal checkout of the branch before the commit, as project rules require.

## Assumptions made
- File name: I used the evaluate skill's `task-{N}-{slug}` form (`task-23a-keycloak-prerequisites-review.md`), as the
  card's parenthetical asks, instead of the bare `task-23a-review.md`.
- Severity of issue 1: rated Important, not Critical. It breaks no code or test, the error message says exactly what
  to set, and the fix is documentation only.

## Test Report
See `task-23a-keycloak-prerequisites-test-report.md`. Targeted run (flags `-DargLine=-Dapi.version=1.44
-Dspotless.check.skip=true`): DevWorkspaceLookupTest 18, AuthorizeControllerTest 30, CallbackControllerTest 124
(unit 172/172), SecurityConfigIT 1, RedisWiringIT 3 (IT 4/4), `BUILD SUCCESS`. I did not re-run the full verify:
the card names the developer's green full verify on the same uncommitted state.
