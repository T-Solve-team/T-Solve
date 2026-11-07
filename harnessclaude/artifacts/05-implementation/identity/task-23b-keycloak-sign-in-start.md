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

### Hand-off
- done: Task 23c (KAN-289): Keycloak callback on Workspace sites: `OidcCallbackHandler` + `AuthSecurityConfig` stateless `@Order(0)` chain, no authorized client kept, request check before Keycloak error mapping and before the code exchange, `__Host-TSOLVE_AUTHREQ` expired on every answer, dev claims page via forward; 23 planned tests + 2 more rows, all four test files green, review attempt 2 with only a formatting blocker that did not reproduce; browser walk passed incl. second browser, replay, hint removed, beta; review attempt 2 verdict "fail" was a formatting finding: reproduced on 7f5e87cf (the earlier exit 0 ran on a scratch copy that was not the committed state), fixed in d8d94a50, review re-issued as pass in attempt 3; production code ready; PR #161 open, d8d94a50 not yet pushed
- open: review notes 7: `WorkspaceLookup` throwing in `CheckedRequests` and a non-OidcUser principal skip the cookie expiry; 10: Redis-down ERROR line keeps a stack trace; 11: optional rows; Task 24 removes the old `login.` classes; Task 28 Caddy must strip `Forwarded`, `X-Forwarded-*`, `X-Forwarded-Prefix`; Keycloak code lifespan is 60 s; `sub` binding is Task 25a
- next: Task 24, remove the custom sign-in (new Jira issue)
- branch: feat/KAN-289-identity-keycloak-callback
- PR: not opened yet
