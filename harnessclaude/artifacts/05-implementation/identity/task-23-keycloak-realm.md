# Task 23: Keycloak and the fake Google on the local stack: the realm, compose and the start-up check — KAN-276

**Module:** identity
**Feature:** keycloak-realm
**Status:** passing
**Iteration:** 1

## Session 2026-11-07

### Hand-off
- done: Task 23 built (Keycloak 26.4.7 and the fake Google on the local stack, realm import `infra/keycloak/realm-tsolve.json`, discovery check, required `KEYCLOAK_WEB_CLIENT_SECRET`); 45 targeted tests green, identity package 387 tests green; reviewer verdict pass with fixes applied
- open: Tasks 23b (KAN-288) and 23c (KAN-289) still to do; for 23c confirm `sub` is in the ID token; `TSOLVE_LOGIN_ORIGIN` now serves both `login.` and Keycloak, so the old local sign-in is broken until 23b; production must override realm `sslRequired: none` and not use `start-dev` (Task 28); the discovery check also fails start-up on 3xx and on 4xx other than 404, accepted
- next: Task 23b, KAN-288
- branch: feat/KAN-276-identity-keycloak-sign-in
- PR: PR ready to merge (to be opened by the orchestrator)
