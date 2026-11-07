# Test Report: identity — Task 23: keycloak-realm (KAN-276)

**Date:** 2026-11-07
**Worktree:** `T-Solve-KAN-276`, branch `feat/KAN-276-identity-keycloak-sign-in`, base `7e2e4c6`, all Task 23 work uncommitted
**Commands (reviewer, fresh run):**
- `cd backend && mvn -DargLine=-Dapi.version=1.44 -Dspotless.check.skip=true -Dtest='DiscoveryCheckTest,SignInPropertiesTest,IdentityClockTest' -Dit.test=RealmImportIT -Dsurefire.failIfNoSpecifiedTests=false verify` (exit 0)
- `bun harnessclaude/hooks/sdd.ts trace identity STORY-001`
- `mvn -q spotless:check`: could not run here. Spotless reports "Cannot find git repository in any parent directory" in a git worktree. This is a tool limit, not a finding. The developer's scratch-clone run (exit 0) is the evidence.

Per the card, the full verify was not re-run. The developer's run on the same tree was `vn.tsolve.identity` Test+IT 387, 0 failures, BUILD SUCCESS. The only edits after that run were format-only.

## Results
| Class | Run | Failed | Errors | Skipped | Time |
|-------|-----|--------|--------|---------|------|
| DiscoveryCheckTest | 11 | 0 | 0 | 0 | 8.8 s |
| SignInPropertiesTest | 19 | 0 | 0 | 0 | 0.3 s |
| IdentityClockTest | 2 | 0 | 0 | 0 | 0.1 s |
| RealmImportIT (Docker, Keycloak 26.4) | 13 | 0 | 0 | 0 | 41.8 s |
| **Total** | **45** | **0** | **0** | **0** | BUILD SUCCESS |

- Compile: pass. Lint/format: not run here (see above).
- This run includes RealmImportIT after the format-only edits. The developer had not re-run it after those edits.

## Coverage
No coverage command is wired for this slice, and the spec sets no threshold. Every changed production branch of
`SignInConfig.checkDiscovery` has a test case: match, trailing slash, mismatch, connection refused, unknown host,
500, 503, slow reply, 404, non-JSON and missing issuer.

## Acceptance Criteria → Tests
`sdd.ts trace identity STORY-001`: 23/56 criteria of the story have a test (the whole story; most are other tasks).
For this task:
```
  ✓ AC-001.54  backend/src/test/java/vn/tsolve/identity/signin/RealmImportIT.java
  ✓ AC-001.63  backend/src/test/java/vn/tsolve/identity/signin/RealmImportIT.java
  ✓ AC-001.64  backend/src/test/java/vn/tsolve/identity/signin/RealmImportIT.java
  ✓ AC-001.65  backend/src/test/java/vn/tsolve/identity/signin/RealmImportIT.java
```
AC-001.54 is covered only on the Keycloak side: the exact redirect URIs. Sending that URI is Task 23b. The 404 on other hosts is Tasks 23b/23c.

## Failures
None.

## Notes
- Slow: `DiscoveryCheckTest` "reply slower than 5 s" waits about 5 s by design. `RealmImportIT` starts a container (about 30 s).
- The IT container image is the library default `quay.io/keycloak/keycloak:26.4`. Compose pins `26.4.7` (review finding 4).
- No flaky or skipped tests.
- Keycloak's own log in the IT confirms that the refusals are real. It shows `invalid_redirect_uri` for all five mutated
  URIs, `PKCE enforced Client without code challenge method` for no challenge, `code challenge method is not matching`
  for `plain`, and `Provider not found or not enabled for realm google-nope` for the unknown hint.
