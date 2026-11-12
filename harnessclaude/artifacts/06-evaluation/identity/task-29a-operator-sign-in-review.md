---
module: identity
feature: operator-sign-in
task: 29a
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: tsolve-code-reviewer (Fable 5.2, medium)
iteration: 1
---

# Review: identity — Task 29a: realm `tsolve-ops` and the Console sign-in start

PR #209, branch `feat/KAN-233-identity-operator-sign-in`, range `origin/main...HEAD`
(655d8edf tests, a5f80492 code, 6b63ae65 docs). Graded verdict: **Pass with fixes**
(0 Critical, 2 Important, 4 Minor). Nothing blocks the merge; Important 1 must be closed before
Task 29b turns the callback on.

## Summary
AC-001.28 and AC-001.66 are met by the code and each clause has a test that fails if the clause breaks.
The Console start cannot be steered: the redirect URI comes from the client registration
(`TSOLVE_ADMIN_ORIGIN`), the resolver takes no request at all. A Console request cannot be used at a
Workspace callback (`AuthSecurityConfig.CheckedRequests` needs a matching `workspace_id`, a Console
request has none, so MSG141 before any token request), and the callback on `admin.` still answers 404.
No secret is committed: the client secret is an environment placeholder, the only literal is the
fake-Google dev secret. Two realm-hardening points that the criterion does not name are open
(Issues 1 and 2).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 table below: every clause PASS |
| Completeness | pass | No MISSING. No EXTRA behaviour; the 303 from Keycloak to the broker login is the user's decision (option a) and is in tasks.md and design row 136 |
| Correctness | pass | Targeted run by the reviewer: 58 unit tests and 11 IT, 0 failures. No Critical finding |
| Integration | pass | Second registration `tsolve-console` does not change the `tsolve-web` one (`{baseUrl}` redirect kept); `KeycloakSignInEndToEndIT` and `TwoInstanceCallbackIT` green in CI shards it-keycloak-a/b |

### Stage 1 — spec compliance

| # | Clause | Status | Evidence |
|---|--------|--------|----------|
| 1 | AC-001.28 saves a request naming the Platform Console | PASS | `SignInStartController.java:56-61`; `OperatorSignInTest` `startsAConsoleSignInAgainstTheOpsRealm_AC_001_28` reads Redis: `target=PLATFORM_CONSOLE`, no `workspace_id`, cookie attributes |
| 2 | AC-001.28 302 to the `tsolve-ops` authorization endpoint, client `tsolve-console` | PASS | same test: status 302, `Location` starts with the full endpoint, `client_id` |
| 3 | AC-001.28 PKCE S256, state, nonce | PASS | same test: challenge and nonce equal the hash of the saved values, state equals the saved state |
| 4 | AC-001.28 redirect URI on `TSOLVE_ADMIN_ORIGIN` | PASS | `KeycloakClientRegistrations.java:22-33`, `resolveConsole`; test `..._edge138` (forwarded host, proto, port; foreign `returnTo`) |
| 5 | AC-001.66 one Google IdP, no `hd`, no tenant IdP | PASS | `OpsRealmImportIT.hasOneGoogleIdp...edge136` (exact alias list, no `hd` in config, URL or mappers, redirector is the only IdP step) |
| 6 | AC-001.66 no self-registration, no username/password form | PASS | `allowsNoSelfRegistration...`: realm flags, browser flow has neither form, the start is a redirect and the broker login goes to fake Google |
| 7 | AC-001.66 brute force on, SSO Max at most 8 h, Idle at most 60 min | PASS | same test (static, as the task asks) |
| 8 | AC-001.66 required second factor for every user | PASS | `requiresASecondFactor...edge137`: OTP Form REQUIRED in the post-broker flow, and a real brokered first login ends on the TOTP setup page with no code issued |
| 9 | AC-001.66 confidential client, PKCE S256 | PASS | `makesTsolveConsoleConfidential...`: no PKCE and `plain` are refused with `error=invalid_request` and no `code` |
| 10 | AC-001.66 exact redirect URI | PASS | `acceptsOnlyTheExactRedirectUri...` (7 rows; the status and the `Location` are both checked) |
| 11 | Discovery check: wrong issuer fatal, unreachable one WARN | PASS | `SignInConfig.java:36-41`; `DiscoveryCheckTest.checksTheOpsRealmDiscoveryIssuer_AC_001_28_edge111` |

## Traceability
```
✓ AC-001.28  backend/src/test/java/vn/tsolve/identity/signin/DiscoveryCheckTest.java, backend/src/test/java/vn/tsolve/identity/signin/OperatorSignInTest.java
✓ AC-001.66  backend/src/test/java/vn/tsolve/identity/signin/OpsRealmImportIT.java
```

## Issues

CRITICAL (blocks pass): none.

Important (fix before Task 29b merges):

1. `infra/keycloak/realm-tsolve-ops.json` (whole file; compare `infra/keycloak/realm-tsolve.json:14-27`) —
   the realm has no user-profile rule, so a user can edit their own email in Keycloak's account console
   (the built-in `account` / `account-console` clients are created at import). Any Google user gets a
   `tsolve-ops` user and an SSO session after the TOTP setup, and Task 29b's allow-list reads the email
   claim. The only thing left between a self-edited email and the allow-list is Keycloak resetting
   `emailVerified`. Not reproduced here; `realm-tsolve.json` already closes it by making `email`
   admin-only.
   Fix: add the same `components` user-profile block (email: edit `admin` only) or disable the two
   account clients; add an `OpsRealmImportIT` assertion on it.
2. `infra/keycloak/realm-tsolve-ops.json:28` — `auth-cookie` is ALTERNATIVE in the browser flow, so a
   sign-in inside a live Keycloak SSO session (at most 8 h / 60 min) gets a code with no Google step
   and no OTP prompt. AC-001.66 is still met (the session was made with the second factor), but
   tasks.md Task 29a says "the code is asked at every login" and no test signs in a second time.
   Fix: decide — keep it and reword tasks.md to "at every Google login", or remove `auth-cookie` from
   the `tsolve-ops` browser flow; either way add a test of the second sign-in.

Minor:

3. `backend/src/test/java/vn/tsolve/identity/signin/OpsRealmImportIT.java:297-326` — only the first
   login is tried; a user who already has TOTP is checked only statically (REQUIRED).
   Fix: with Issue 2, enrol a TOTP and assert the next broker login shows the code form.
4. `OpsRealmImportIT.java` (`makesTsolveConsoleConfidential...`) — nothing checks that the imported
   secret is the environment value; a failed `${KEYCLOAK_CONSOLE_CLIENT_SECRET}` substitution would
   leave a publicly known literal as the secret.
   Fix: assert `consoleClient().getSecret().getValue()` equals the IT secret.
5. `README.md:92` and `.env.example:32` — the README's environment list does not name
   `KEYCLOAK_CONSOLE_CLIENT_SECRET`; the `.env.example` line lacks "Never commit a real one". The
   backend now refuses to start when `tsolve-ops` is missing (a 404 is fatal) and there is no runbook
   step for creating that realm.
   Fix: add both lines; add the realm to the deploy runbook before this version is deployed.
6. Stale wording: `OpsRealmImportIT.java:178-181` says "302" (Keycloak answers 303), `:245-247`
   "hideOnLogin: the Google button is shown" (no page is shown), `SignInConfig.java:29-33` Javadoc
   names only the realm `tsolve`.
   Fix: reword.

## Assumptions made
- "Required second factor for every user" is read as: no SSO session exists without a second factor.
  Under this reading Issue 2 does not fail AC-001.66.
- A row-140 test (Console request at a Workspace callback) is Task 29b's; the code already refuses it.

## Security Review — identity — 2026-11-12

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | not run (no dependency changed) | | | |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 2 (Issues 1, 2) | 2 (Issues 4, 5) |

Spoofing: state, nonce and PKCE on the Console request; cookie `__Host-`, Secure, HttpOnly, Lax.
Elevation: no non-Operator or tenant IdP path to a session in this PR (callback on `admin.` is 404;
Console request refused on Workspace hosts). `sslRequired: none` and the fake-Google values are dev
values, same as `realm-tsolve.json`; production comes from the runbook.

## Test Report
See `task-29a-operator-sign-in-test-report.md`. Reviewer's targeted run on HEAD 6b63ae65 (clean tree):
`OperatorSignInTest` 6/0, `DiscoveryCheckTest` 13/0, `SignInStartControllerTest` 26/0,
`SignInPropertiesTest` 13/0, `OpsRealmImportIT` 11/0, exit 0. Full verify not re-run (card).

Proposed status: `operator-sign-in` stays as it is until Task 29b (the feature covers both); no status
changed by the reviewer.
