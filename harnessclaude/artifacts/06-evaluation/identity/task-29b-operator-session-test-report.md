# Test Report: identity — Task 29b: operator-session

**Date:** 2026-11-12
**Head:** `a1cbcddb` (PR #212, clean working tree)
**Commands:** per class, `cd backend && mvn -q -o -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-Dapi.version=1.44 test` (Docker 29.2.1), each to its own log outside the repo. Typecheck is the compile step of each run. Lint (`spotless:check`), the full unit suite and the ITs were not re-run: the developer's verify and CI cover them (reviewer rule).

## Results

- Total: 129 · Passed: 129 · Failed: 0 · Skipped: 0
- Compile: pass (every run exit 0)

| Class | Tests | Failures | Time |
|-------|-------|----------|------|
| `identity/signin/OperatorSignInTest` | 37 | 0 | 24 s |
| `identity/session/OperatorSessionTest` | 12 | 0 | 25 s |
| `identity/session/WorkspaceBindingTest` | 9 | 0 | 41 s |
| `identity/AuthSecurityConfigTest` | 14 | 0 | 14 s |
| `identity/signin/OidcCallbackHandlerTest` | 22 | 0 | 3 s |
| `identity/session/WebSessionFilterTest` | 35 | 0 | 47 s |

## Developer's verify and CI (not re-run)

- Developer (card): `mvn spotless:check test` 1425 tests, 0 failures; targeted tests, `TwoInstanceCallbackIT` and `OpsRealmImportIT` exit 0.
- `gh pr checks 212`: backend-tests unit, it-keycloak-a, it-keycloak-b, it-other pass; gitleaks pass; frontend-checks and frontend-tests skipped (no frontend change); **pr-title fails** ("subject must not be sentence-case").

## Coverage

Not measured (no coverage target in the spec for this task).

## Acceptance Criteria → Tests

```
✓ AC-001.29  signin/OperatorSignInTest.java, session/OperatorSessionTest.java
✓ AC-001.30  signin/OperatorSignInTest.java
✓ AC-001.31  signin/OperatorSignInTest.java
✓ AC-001.32  session/OperatorSessionTest.java
✓ AC-001.33  session/OperatorSessionTest.java
✓ AC-001.34  session/OperatorSessionTest.java
✗ AC-001.71  no test (Task 30b, not this task)
✓ AC-002.1   KeycloakLogoutUrlBuilderTest, LogoutControllerTest, OperatorSessionTest, OpsRealmImportIT
✓ AC-002.2   LogoutControllerTest, OperatorSessionTest
```

## Failures

None.

## Notes

- No flaky or skipped test seen; no single test over 5 s was checked for.
- Gap in the tests, not a failure: edge 141 pins only `GET /api/v1/users`; no test sends an Operator session to an endpoint without a Role check (`/api/solutions`, the chatbot endpoints). See review issue 1.
- No test asserts that the callbacks send no userinfo request (review issue 2).
