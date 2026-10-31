# Test Report: identity — Task 8: sign-in-start

**Date:** 2026-10-29
**Scope:** uncommitted working tree on `feat/KAN-230-identity-sign-in-start` vs `main` (7296269)
**Env:** `JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1`, Maven 3.10.0 (chocolatey), Docker Desktop 4.65.0 (Engine 29.2.1)
**Commands (from `backend/`):**
- `mvn -B verify` → log `scratchpad/mvn-verify.log` (outside the repo)
- `DOCKER_API_VERSION=1.44 mvn -B -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dapi.version=1.44 verify` → `scratchpad/mvn-it.log`
- Start-up probe (key never printed): `java -jar target/t-solve-backend-0.1.0-SNAPSHOT.jar` with a 19-byte `TSOLVE_SIGNING_KEY` and the DB auto-configs excluded → `scratchpad/startup-shortkey2.log`
- Coverage: none — the backend has no coverage plugin (no JaCoCo in `pom.xml`); the spec sets no threshold.

## Results
- Surefire (unit + slice): Total 200 · Passed 200 · Failed 0 · Skipped 0
  - identity: AuthSecurityConfigTest 3, AuthorizeControllerTest 18, LoginPageTest 4, SignedTokenTest 13,
    SignInPropertiesTest 17, SignInStartControllerTest 12, SignInStateServiceTest 40 (107)
  - ticket regression: ImportControllerTest 11 (unchanged file, green under the new chains), the other ticket classes green
- Failsafe (ITs), first run: 2 errors — `SignInStateRepositoryIT`, `ImportConfirmIT` — both `ExceptionInInitializerError`,
  Testcontainers "Could not find a valid Docker environment" (`NpipeSocketClientProviderStrategy: BadRequestException (Status 400)`).
- Failsafe, re-run with `DOCKER_API_VERSION=1.44`: Total 10 · Passed 10 · Failed 0 — SignInStateRepositoryIT 4 (28 s), ImportConfirmIT 6.
  `BUILD SUCCESS`.
- Compile: pass · Spotless (`spotless:check`, part of verify): pass ("29 files clean").
- Start-up failure with a short key: `Failed to bind properties under 'tsolve.auth' … Reason: java.lang.IllegalArgumentException: TSOLVE_SIGNING_KEY must be at least 32 bytes`; the key text occurs 0 times in the log.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no coverage plugin) | none in spec |

## Acceptance Criteria → Tests
`sdd.ts trace` cannot see Java tests (its globs match `*.test.*`, `*.spec.*`, `*_test.*` only). Its last line:
`trace identity (STORY-001): 4/43 acceptance criteria have a test` (the four are the T7 frontend ACs .40–.43).

Manual trace (grep of JUnit names / `@DisplayName`, each test read):

| AC / row | Tests |
|---|---|
| AC-001.1 | SignInStartControllerTest `startsSignInForTheWorkspaceOfTheHost_AC_001_1`, `resolvesAnotherWorkspaceFromItsOwnHost_AC_001_1`, `takesTheWorkspaceOnlyFromTheHost_AC_001_1`, `startsSignInForWorkspaceThatIsNotActive_AC_001_1` |
| AC-001.2 | SignedTokenTest `signsWithHmacSha256…_AC_001_2`; SignInStateServiceTest 9 methods `…_AC_001_2`; SignInStartControllerTest `keepsNothingInTheBrowserButTheState_AC_001_2`; AuthorizeControllerTest `usesTheNonceAndVerifierKeptOnTheServer_AC_001_2`; SignInStateRepositoryIT 4 methods (check constraint ×2, `markUsed` 1→0, 0 after expiry) |
| AC-001.3 | SignInStateServiceTest `keepsSameSiteRelativeReturnTo_AC_001_3`, `replacesAnyOtherReturnToWithRoot_AC_001_3_edge19`; SignInStartControllerTest `passesReturnToThroughTheFilter_AC_001_3` |
| AC-001.4 | AuthorizeControllerTest `sendsWorkspaceSignInToGoogle_AC_001_4`, `setsHdToTheStatesOwnWorkspaceDomain_AC_001_4` |
| AC-001.5 | AuthorizeControllerTest `answers404ForAuthorizeOffLogin_AC_001_5`, `answers404ForCallbackOffLogin_AC_001_5` (vacuous until T9, see review I-1), `redirect_uri` asserted in the AC-001.4/.28 tests |
| AC-001.6 | AuthorizeControllerTest `answersMsg141WhenStateIsMissing_AC_001_6`, `answersMsg141ForUntrustedState_AC_001_6_edge20`, `…ExpiredState…`, `…UsedState…` (authorize half; the callback half is T9) |
| AC-001.28 | SignInStartControllerTest `startsPlatformConsoleSignInOnAdmin_AC_001_28`; SignInStateServiceTest `createsPlatformConsoleState_AC_001_28`; AuthorizeControllerTest `sendsPlatformConsoleSignInWithoutHd_AC_001_28` |
| AC-001.39 | LoginPageTest 4 methods `…_AC_001_39` |
| Row 19 | SignInStateServiceTest `replacesAnyOtherReturnToWithRoot_AC_001_3_edge19` (19 cases incl. `//evil`, `/\evil`, `https://evil`, tab, 3,000 chars) |
| Row 20 | SignedTokenTest 5 `…_edge20`; SignInStateServiceTest 3; AuthorizeControllerTest 3; SignInStateRepositoryIT 1 |
| Row 38 | SignInPropertiesTest 5 methods (17 cases) `…_edge38` |
| Row 43 | AuthSecurityConfigTest `keepsOtherPathsBehindAuthentication_edge43` (×2), `letsSignedInUserReachOtherPaths_edge43` |
| Row 44 | SignInStartControllerTest `answers404OnHostThatIsNoWorkspaceSite_edge44` (unknown, `login.`, `api.`) |

Every T8 criterion and row has at least one test that would fail if the rule broke (exception: the callback half of AC-001.5, which T9 builds).

## Failures
### `SignInStateRepositoryIT`, `ImportConfirmIT` — class init (first run only)
- Error: `ExceptionInInitializerError` ← Testcontainers `Could not find a valid Docker environment … BadRequestException (Status 400)`.
- Cause: environment, not this change. Docker Engine 29 accepts API ≥ 1.44; the docker-java client inside the
  project's Testcontainers asks with an older default API version and gets 400. `ImportConfirmIT` (on `main`) fails the same way.
  Not a row in `docs/bug-log.md`.
- Proof: with `DOCKER_API_VERSION=1.44` both ITs pass (10/10).
- Suggested fix (separate change, not this PR): bump Testcontainers to a release that negotiates the API version, or
  document `api.version=1.44` in `~/.docker-java.properties`; log a bug row.

## Notes
- Not flaky: the IT failure flips only with the env value, deterministically.
- Slow: SignInStateRepositoryIT 28 s (container start), AuthSecurityConfigTest 6.8 s (first context). Nothing else > 5 s.
- Skipped tests: none.
- Start-up prints Spring Boot's "Using generated security password" (pre-existing on `main`; no form login / Basic uses it).
