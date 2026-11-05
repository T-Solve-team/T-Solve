# Task 9: The callback refuses forged, replayed or cookie-less returns and Google failures — KAN-231

**Module:** identity
**Feature:** sign-in-callback
**Status:** passing
**Iteration:** 1

## Session 2026-11-04

### Hand-off
- done: login-CSRF binding (`__Host-TSOLVE_SIGNIN`, changeset 005), `CallbackController` on `login.` (state, cookie, Workspace, Google error, code exchange, ID token → MSG141 page or `/login?error=MSG06`), no-Google-domain refusal at sign-in start and authorize, `IdentityClock`; AC-001.4, .7, .8, .9, .44–.50 tested; BUG-103 (cancel → MSG06) fixed; identity plan aligned with the multi-tenant HLA (Console back, states → Registry DB in Task 14, sessions in Redis in Task 10, no `workspace_id` on account tables); review pass at attempt 1, should-fix items fixed
- open: no JWKS cache (one fetch per sign-in, decided 04/11); `azp` not checked and check order in the controller (minor, T9b); Testcontainers on Docker 29 needs `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` and jira-app tests need `SERVER_PORT` (shared setup, not fixed); `sdd.ts trace` still cannot see Java tests; reverse-proxy access log will record `code` / `state`
- next: Task 9b (KAN-235, `feat/KAN-235-identity-account-checks`)
- branch: `feat/KAN-231-identity-sign-in-callback`
- PR: (to open — "PR #<n> ready to merge" is filled in after push)
