# Test Report: identity — Task 9: sign-in-callback

**Date:** 2026-11-03
**State tested:** branch `feat/KAN-231-identity-sign-in-callback`, HEAD `205578b`, working tree clean
**Commands (from `backend/`, env `JAVA_HOME=jdk-21.0.12.1`, `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`, `SERVER_PORT=0`):**
`mvn "-Dtest=CallbackControllerTest,IdTokenVerifierTest,SignInCookieTest,IdentityClockTest,AuthorizeControllerTest,SignInStartControllerTest,SignInStateServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` ·
`mvn -q spotless:check` · `bun harnessclaude/hooks/sdd.ts trace identity` (repo root)

## Results
- Targeted run: Total 215 · Passed 215 · Failed 0 · Skipped 0 · BUILD SUCCESS · 16.3 s
  - AuthorizeControllerTest 30 · CallbackControllerTest 87 · IdentityClockTest 2 · IdTokenVerifierTest 25 ·
    SignInCookieTest 7 · SignInStartControllerTest 17 · SignInStateServiceTest 47
- Spotless check: exit 0
- Not re-run (card): full `mvn verify`, `SignInStateRepositoryIT` (Docker), frontend checks. The developer's verify
  (card): IT red first 7/11 for the planned reason, then 11/11; `mvn verify` BUILD SUCCESS (400 unit, 17 IT, 74 s);
  `generate:api` one comment-line diff (committed); frontend typecheck ok, test 1285/1285, build ok. My targeted
  run on the committed HEAD agrees with the developer's targeted result (215/215).
- One expected `ERROR` log line in the run: `GlobalExceptionHandler : Unhandled exception java.lang.IllegalStateException`
  from the edge 42 test (the forced database failure); it logs the class and an id only.

## Coverage
No coverage command is configured for the backend; the spec sets no threshold. Not measured.

## Acceptance Criteria → Tests
`sdd.ts trace identity` → exit 1, `trace identity: 77/129 acceptance criteria have a test`. Every Task 9 criterion
(AC-001.4, .7, .8, .9, .44–.50) is listed as ✗ because sdd.ts TEST_GLOBS do not match Java `*Test.java` / `*IT.java`
(known tool gap; Task 8's AC-001.1–.3 show ✗ the same way). Manual trace (JUnit method names ending `_AC_001_<n>`):

| Criterion | Methods | Classes |
|---|---|---|
| AC-001.4 | 2 | AuthorizeControllerTest |
| AC-001.7 | 4 | CallbackControllerTest (two Workspaces) |
| AC-001.8 | 3 | CallbackControllerTest |
| AC-001.9 | 24 | CallbackControllerTest, IdTokenVerifierTest |
| AC-001.44 | 13 | AuthorizeControllerTest, SignInCookieTest, SignInStateServiceTest, SignInStateRepositoryIT |
| AC-001.45 | 6 | AuthorizeControllerTest, SignInStateServiceTest, SignInStateRepositoryIT |
| AC-001.46 | 13 | CallbackControllerTest, SignInCookieTest, SignInStateServiceTest |
| AC-001.47 | 3 (one parameterized over 12 outcomes) | CallbackControllerTest, SignInCookieTest |
| AC-001.48 | 3 | SignInStartControllerTest |
| AC-001.49 | 4 | AuthorizeControllerTest, CallbackControllerTest |
| AC-001.50 | 2 | CallbackControllerTest |

No Task 9 criterion is without a test.

## Failures
None.

## Notes
- Not testable, checked by review: constant-time compare (`MessageDigest.isEqual` in `SignInCookie.matches`);
  `004-identity-sign-in-states.sql` unchanged (`git diff` shows only the new `005` changeset and its include).
- Gaps (minor, see review): no test of the 60 s `exp` skew boundary (Q4); no slow-JWKS test (edge 23 "slow"),
  because the JWKS timeouts are constants of 5 s / 10 s.
- No slow tests (> 5 s each); no skipped tests; no flaky behaviour seen in one run.
