# Test Report: identity — Task 23c: keycloak-callback (KAN-289)

**Date:** 2026-11-07 (attempt 2; attempt 1 is summarised at the end)
**Commands (reviewer, fresh, on the uncommitted state after the 19:31 Spotless copy-back):**
- `cd backend && mvn -B test -Dtest=OidcCallbackHandlerTest,AuthSecurityConfigTest`
- `cd backend && mvn -B verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TwoInstanceCallbackIT -DargLine=-Dapi.version=1.44 -Dspotless.check.skip=true`
- `cd backend && mvn -B test -Dtest='AuthSecurityConfigTest#answersMsg06WithOneErrorWhenRedisIsDownDuringTheCallback_edgeQ5B'`
- palantir-java-format 2.39.0 `--palantir`, run directly on the 9 changed files (CR-stripped scratch copies; Spotless cannot run in the worktree)
- `bun harnessclaude/hooks/sdd.ts trace identity`
- `KeycloakSignInEndToEndIT`: the developer's result, from the card

## Results
- Unit tests: `AuthSecurityConfigTest` 15 and `OidcCallbackHandlerTest` 25. Total 40, passed 40, failed 0, skipped 0; BUILD SUCCESS.
- `TwoInstanceCallbackIT`: 23, passed 23, failed 0, skipped 0 (40 s); BUILD SUCCESS.
- Redis-down test alone: 1/1. The output has exactly one ERROR line (`vn.tsolve.identity.AuthSecurityConfig : Redis failed while reading the saved authorization request`), followed by its stack trace. Spring's second ERROR entry is gone, and the cookie value is not in the log.
- `KeycloakSignInEndToEndIT` 7/7: the developer's run (from the card), made before the formatting copy-back. I did not re-run it.
- Typecheck: compiles (part of the test runs).
- Format: **fail**, in 3 QA test files only.
  - 0 diff lines: `AuthSecurityConfig`, `OidcCallbackHandler`, `DevClaimsController`, `AuthSecurityConfigTest`, `OidcCallbackHandlerTest` and `TwoInstanceCallbackIT`.
  - Still to format: `KeycloakSignInEndToEndIT` 91, `TestBrowser` 7 and `FakeKeycloakRealm` 4 (review finding 9).
- Full suite: not run, as the card says.

## Coverage
No coverage command was run, and the spec sets no threshold.

## Acceptance Criteria → Tests
```
✓ AC-001.6   AuthorizeControllerTest, CallbackControllerTest, OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, TwoInstanceCallbackIT
✓ AC-001.8   CallbackControllerTest, OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, TwoInstanceCallbackIT
✓ AC-001.9   CallbackControllerTest, CallbackFailureLoggingTest, IdTokenVerifierTest, OidcCallbackHandlerTest, TwoInstanceCallbackIT
✓ AC-001.53  KeycloakAuthorizationRequestResolverTest, OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, RedisAuthorizationRequestRepositoryIT
✓ AC-001.54  AuthSecurityConfigTest, KeycloakSignInEndToEndIT, RealmImportIT, TwoInstanceCallbackIT
✓ AC-001.55  AuthSecurityConfigTest, TwoInstanceCallbackIT
✓ AC-001.63  RealmImportIT
✓ AC-001.64  KeycloakSignInEndToEndIT, RealmImportIT
✓ AC-001.65  KeycloakSignInEndToEndIT, RealmImportIT
```

## Failures
No test fails. The format check fails on 3 test files (see Results).

## Notes
- None of the tests run is skipped. The slowest is `TwoInstanceCallbackIT`, at 40 s for its 2 contexts and containers.
- Logs (outside the repo):
  - `C:\Users\ADMIN\AppData\Local\Temp\claude\5794e928a0a4597c\e62e6cda-f88d-498b-8bd4-adda028ce39a\scratchpad\unit2.log`
  - `...\scratchpad\it2.log`
  - `...\scratchpad\redisdown.log`
  - Formatter output: `...\scratchpad\fmt\`
- Attempt 1:
  - Results: 39 unit tests green; `TwoInstanceCallbackIT` 21 and `KeycloakSignInEndToEndIT` 7 green (the developer's run).
  - Failures found: the format failed on `AuthSecurityConfig.java:62` and on 5 test files, and the Redis-down test logged 2 ERROR entries.
