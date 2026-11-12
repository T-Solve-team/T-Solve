# Task 31: keycloak theme — KAN-300

**Module:** identity
**Feature:** keycloak-theme
**Status:** passing
**Iteration:** 1

## Session 2026-11-09

### Hand-off
- done: `tsolve` login theme (login, error, page-expired and logout-confirm overrides) mounted read-only; review B1 spotless reflow; S1 `TSOLVE_WEB_BASE_URL` read by theme and realm; S2 unknown-client test; N1-N3 fixed; feature keycloak-theme passing
- open: production `TSOLVE_WEB_BASE_URL=https://tsolve.io.vn/` must be set by deploy; stale "cancel reaches the callback" mentions in Task 7/23 text, design line ~205, Task 9 notes; `KeycloakSignInEndToEndIT` and `TestBrowser` edits not spotless-checked individually; tasks.md Files list lacks `login.ftl`, `login-page-expired.ftl`, `logout-confirm.ftl` (N4); N5 theme must stay off realm `tsolve-ops`
- next: next task in the iteration plan (not named in this card)
- branch: feat/KAN-300-identity-keycloak-theme
- PR: none (not opened, branch not pushed)
