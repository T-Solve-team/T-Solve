# Task 23b: Keycloak sign-in start — KAN-288

**Module:** identity
**Feature:** keycloak-sign-in-start
**Status:** passing
**Iteration:** 1

## Session 2026-11-07

### Hand-off
- done: Task 23b: sign-in start through Keycloak, `KeycloakAuthorizationRequestResolver` with PKCE S256 and `kc_idp_hint`, `RedisAuthorizationRequestRepository` with one JSON string, `SET EX 600` and `GETDEL`, cookie `__Host-TSOLVE_AUTHREQ`; `KeycloakClientRegistrations` built in code with `issuerUri`; `admin.` keeps the old state path; 42 targeted tests green: resolver 8, controller 22, Redis IT 12; review pass with the forwarded-headers comment fixed; browser walk in Chrome reached the acme chooser and the expected callback 404
- open: Task 23c KAN-289: expire the `__Host-TSOLVE_AUTHREQ` cookie on every callback answer; confirm Chrome keeps the Secure cookie on plain `http://acme.localhost`; confirm `sub` is in the ID token; Caddy must strip `Forwarded`, `X-Forwarded-*` and `X-Forwarded-Prefix` because `server.forward-headers-strategy=framework` trusts them (Task 28); a second tab's start overwrites the first tab's cookie; the anonymous start has no rate limit; `SignInStateService.createForWorkspace` has no caller, remove in Task 24; AC-001.28 test checks the kept `login.` behaviour, so the trace overstates coverage until Task 29; 25b needs the S9 refusal for a null Organization id
- next: Task 23c (KAN-289)
- branch: feat/KAN-288-identity-keycloak-sign-in-start
- PR: opened by the orchestrator; state "PR ready to merge"
