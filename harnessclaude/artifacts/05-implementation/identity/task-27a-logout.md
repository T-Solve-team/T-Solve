# Task 27a: logout, RP-initiated sign-out — KAN-106

## Session

### Hand-off (Task 27a, KAN-106)
- done: Task 27a logout merged as PR #208 (squash a588761f): logout endpoint, LOGOUT event, Keycloak end-session URL, OpenAPI 200 { logoutUrl }, realm post-logout URIs; review verdict approve, attempt 1; CI green on the final head. Feature `logout-rp-initiated` (AC-002.1, .2, .3, .6, .8 logout side) not yet `passing` in progress.json (sdd.ts refused pending → passing).
- open: 6 Minor review items left open on purpose (`task-27a` review file); KAN-311 (useSignOut follows logoutUrl) not started; realm change needs the local Keycloak volume recreated; realm attribute untested (Docker-only IT); trace identity STORY-002 6/8 (AC-002.4 → Task 27b = KAN-312, AC-002.5 → Task 30b).
- next: Task 27b (KAN-312, EXPIRED, DEACTIVATED); KAN-311 must merge; Task 28 redirect URIs must include `<site origin>/login`.
- branch: feat/KAN-106-identity-logout (merged).
- PR: #208.
