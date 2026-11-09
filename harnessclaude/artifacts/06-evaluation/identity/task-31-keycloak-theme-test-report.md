# Test Report: identity — Task 31: keycloak-theme (KAN-300)

**Date:** 2026-11-09
**Scope:** the uncommitted worktree `T-Solve-KAN-300`, branch `feat/KAN-300-identity-keycloak-theme`, base `f70247a7`.
**Commands:** `cd backend && mvn -Dit.test='KeycloakThemeIT,KeycloakFailurePlacementIT' verify` (developer) ·
`mvn -Dit.test=KeycloakSignInEndToEndIT verify -Dspotless.check.skip=true` (developer) · `mvn -o -q spotless:check`
(reviewer) · a reviewer probe against a throwaway Keycloak 26.4.7 with the committed realm and theme ·
`bun harnessclaude/hooks/sdd.ts trace identity STORY-001` (reviewer).

## Results
- The ITs were not re-run. The card says not to, and no file under `infra/keycloak/`, `backend/src/` or
  `docker-compose.yml` is newer than the failsafe reports (`find -newer` returned nothing). Failsafe reports
  (`backend/target/failsafe-reports/*.txt`):
  - `KeycloakFailurePlacementIT`: Tests run 5, Failures 0, Errors 0, Skipped 0 (90.9 s)
  - `KeycloakThemeIT`: Tests run 3, Failures 0, Errors 0, Skipped 0 (0.4 s, on the shared stack)
  - `KeycloakSignInEndToEndIT`: Tests run 7, Failures 0, Errors 0, Skipped 0 (82.3 s). This shows the theme mount
    and the `login.ftl` override leave the real sign-in with a valid `kc_idp_hint` unchanged.
- Format check (`spotless:check`): **NOT VERIFIED**. In the worktree it exits 1 with "Cannot find git repository in
  any parent directory": `ratchetFrom origin/main` needs a git folder that JGit can read. The developer's
  `mvn verify` hit the same error. `KeycloakThemeIT.java:124` is a 123-character code line, and Palantir Java Format
  (120 columns) will re-wrap it, so the check is expected to fail in CI.
- Frontend (AC-001.40–.43): not run. The worktree has no `node_modules`, and no frontend file changed.

## Reviewer probe (Keycloak 26.4.7, realm and theme mounted, `start-dev --import-realm`)
| Request | Status | Page |
|---|---|---|
| `auth?client_id=nope` (Keycloak does not know the client) | 400 | MSG06 once, "Back to sign-in" link to `http://localhost:5173/`, the fallback from `theme.properties` |
| `auth?client_id=tsolve-web&redirect_uri=http://evil.test/"><script>…` | 400 | MSG06 page; the redirect URI is not echoed; no `<script` |
| `auth?client_id=tsolve-web&state="><script>…&kc_idp_hint="><script>…&login_hint="><img onerror…>&ui_locales=…` | 400 | MSG06 page; nothing echoed; no `<script`, no `alert(` |
| `auth?client_id=tsolve-web` with no hint (AC-001.64) | 400 | MSG06 page; no form, no identity provider |
| `logout?client_id=tsolve-web&state=<script>…` | 200 | Themed confirm form posting to Keycloak's own path; no `onsubmit`; one Back link |
| `logout?client_id=nope` | 200 | Themed confirm (Keycloak falls back to the `account` client); no Back link |
| POST logout-confirm → info page ("You are logged out") | 200 | Inherited `info.ftl`, themed, `tsolve.css` only, no script, one Back link |
| Admin API `tsolve-web` | — | `baseUrl` = `http://localhost:5173/`, `frontchannelLogout` = false |

## Acceptance Criteria → Tests (`sdd.ts trace identity STORY-001`, task rows)
```
✓ AC-001.8   OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, TwoInstanceCallbackIT
✓ AC-001.64  KeycloakSignInEndToEndIT, RealmImportIT
✓ AC-001.67  KeycloakFailurePlacementIT, KeycloakThemeIT
✓ AC-001.68  KeycloakThemeIT
✓ AC-001.40–.43  frontend/src/features/auth/pages/LoginPage.test.tsx
trace identity (STORY-001): 24/56 acceptance criteria have a test
```
The ✗ rows of the other STORY-001 criteria belong to other tasks.

## Failures
None in the test runs. The format check could not be run (see above).

## Notes
- No test covers the AC-001.67 clause "falls back to the neutral URL kept in the theme when Keycloak does not know
  the client". `client.baseUrl` and the fallback have the same value, so the asserted href cannot tell which branch
  produced it. The probe above shows the fallback branch works.
- The tests scan only three pages: login, error and logout-confirm. The inherited `info.ftl` was checked only by
  the probe.
- The `followUntilSettled` loop is capped at 25 hops. The expired-session test sleeps 4 s, which is slow but intended.
