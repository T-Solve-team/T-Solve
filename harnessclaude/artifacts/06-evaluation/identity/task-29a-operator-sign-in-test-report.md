# Test Report: identity — Task 29a: realm `tsolve-ops` and the Console sign-in start

**Date:** 2026-11-12
**Commands:** `cd backend && mvn -q -Dtest='OperatorSignInTest,DiscoveryCheckTest,SignInStartControllerTest,SignInPropertiesTest' -Dit.test=OpsRealmImportIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify` (compile, Spotless check, Surefire, Failsafe) · `bun harnessclaude/hooks/sdd.ts trace identity STORY-001` · `gh pr checks 209`

State tested: HEAD 6b63ae65 on `feat/KAN-233-identity-operator-sign-in`, clean working tree, Docker 29.2.1.
The full `mvn verify` was not re-run locally (the card gives the developer's result; the full suite is CI's).

## Results
- Total: 69 · Passed: 69 · Failed: 0 · Skipped: 0 · exit code 0
- Compile: pass · Spotless (part of `verify`): pass

| Class | Tests | Failures | Errors | Time |
|-------|-------|----------|--------|------|
| `OperatorSignInTest` | 6 | 0 | 0 | 39.2 s |
| `DiscoveryCheckTest` | 13 | 0 | 0 | 10.3 s |
| `SignInStartControllerTest` | 26 | 0 | 0 | 3.7 s |
| `SignInPropertiesTest` | 13 | 0 | 0 | 0.2 s |
| `OpsRealmImportIT` (real Keycloak 26.4.7 and fake Google) | 11 | 0 | 0 | 52.8 s |

Source: `backend/target/surefire-reports` and `backend/target/failsafe-reports` of this run.

## CI on PR #209 (run 43229638059)
`backend-tests (unit)` pass · `it-keycloak-a` pass · `it-keycloak-b` pass · `it-other` pass (holds
`OpsRealmImportIT`) · `backend` pass · `gitleaks` pass · `pr-title` pass · frontend jobs skipped (no
frontend change).

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| not measured | — | the spec sets none for this task |

## Acceptance Criteria → Tests
```
✓ AC-001.28  backend/src/test/java/vn/tsolve/identity/signin/DiscoveryCheckTest.java, backend/src/test/java/vn/tsolve/identity/signin/OperatorSignInTest.java
✓ AC-001.66  backend/src/test/java/vn/tsolve/identity/signin/OpsRealmImportIT.java
```

## Failures
None.

## Notes
- No flaky or skipped test in this run. Slow (over 5 s): `OperatorSignInTest` and `OpsRealmImportIT`
  (container start), `DiscoveryCheckTest` (a 7 s timeout case).
- Test gaps against the spec, none of them an uncovered criterion (review Issues 2, 3, 4): no second
  sign-in is tried (SSO cookie, or a user who already has TOTP); the imported client secret is not
  compared with the environment value.
- `KeycloakSignInEndToEndIT`, `TwoInstanceCallbackIT`, `KeycloakThemeIT` and
  `KeycloakFailurePlacementIT` were not re-run locally; they passed in CI.
