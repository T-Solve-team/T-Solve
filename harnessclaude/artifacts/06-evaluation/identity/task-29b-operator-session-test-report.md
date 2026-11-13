# Test Report: identity — Task 29b: operator-session

**Date:** 2026-11-13 (review attempt 3)
**Head:** `75281b55` (PR #212, clean working tree, equal to the pushed head)
**Commands:** in a scratch copy of the head outside the repo (`git archive HEAD backend/pom.xml backend/src`), one run, log outside the repo: `mvn -o -Dtest='OperatorSessionTest,OperatorSignInTest,WebSessionFilterTest,OidcCallbackHandlerTest,AuthSecurityConfigTest,LogoutControllerTest,WorkspaceBindingTest' -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-Dapi.version=1.44 test`. Typecheck is the compile step of the run. Lint (`spotless:check`), the full unit suite and the ITs were not re-run: the developer's verify and CI cover them (reviewer rule).

## Results

- Total: 148 · Passed: 148 · Failed: 0 · Skipped: 0 · exit 0, BUILD SUCCESS
- Compile: pass

| Class | Tests | Failures | Time |
|-------|-------|----------|------|
| `identity/session/OperatorSessionTest` | 18 | 0 | 5 s |
| `identity/signin/OperatorSignInTest` | 38 | 0 | 8 s |
| `identity/session/WebSessionFilterTest` | 35 | 0 | 5 s |
| `identity/signin/OidcCallbackHandlerTest` | 22 | 0 | under 1 s |
| `identity/AuthSecurityConfigTest` | 14 | 0 | 8 s |
| `identity/session/LogoutControllerTest` | 12 | 0 | 23 s |
| `identity/session/WorkspaceBindingTest` | 9 | 0 | 3 s |

A reviewer probe class (1 test, scratch copy only, not in the repo) ran in the same run; its results are in the review, "The card's questions".

## Developer's verify and CI (not re-run)

- Developer (card): `mvn spotless:check test` 1454 tests, 0 failures; `TwoInstanceCallbackIT,OpsRealmImportIT` 43 tests, 0 failures. Run before the two commits of this range were made; the one commit after them changes only the review file.
- `gh pr checks 212` on `75281b55`: backend, backend-tests unit, it-keycloak-a, it-keycloak-b, it-other pass; gitleaks pass; pr-title pass; frontend-checks and frontend-tests skipped (no frontend change).

## Coverage

Not measured (no coverage target in the spec for this task).

## Acceptance Criteria → Tests

`bun harnessclaude/hooks/sdd.ts trace identity STORY-001` / `STORY-002` at this head (rows of this task):

```
✓ AC-001.29  signin/OperatorSignInTest.java, session/OperatorSessionTest.java
✓ AC-001.30  signin/OperatorSignInTest.java
✓ AC-001.31  signin/OperatorSignInTest.java
✓ AC-001.32  session/OperatorSessionTest.java
✓ AC-001.33  session/OperatorSessionTest.java
✓ AC-001.34  session/OperatorSessionTest.java
✓ AC-002.1   KeycloakLogoutUrlBuilderTest, LogoutControllerTest, OperatorSessionTest, OpsRealmImportIT
✓ AC-002.2   LogoutControllerTest, OperatorSessionTest
```

## Failures

None.

## Notes

- No flaky or skipped test seen; no single test over 5 s was checked for.
- New since attempt 2 in `OperatorSessionTest` (14 → 18 tests): three percent-encoded `/api/solutions` rows (403 MSG08) and the row for a session that is past the limit and off the allow-list (401 MSG07); the expiry rows now assert that no `SessionEnded` is published.
- Gaps in the tests, not failures (review A3-2 and attempt-1 finding 7): no row pins the exact match of the logout entry or the refusal of a non-API path; the successful Console sign-in has no log-content check.
