---
module: identity
feature: sign-in-start
task: 8
verdict: pass
attempt: 1
date: 2026-10-29
reviewer: tsolve-code-reviewer (Claude Opus 5.5, independent read-only)
iteration: 1
---

# Review: identity — Task 8: sign-in-start

## Summary
Reviewed the uncommitted changes on `feat/KAN-230-identity-sign-in-start` against `main` (7296269): identity
`signin` code, `AuthSecurityConfig`, migration 004, `application.yml`, `pom.xml`, `.env.example`, the QA tests and
`ImportConfirmIT` properties. Judged from spec AC-001.1–.6, .28, .39, design rows 19, 20, 38, 43, 44 and the
Q1–Q5/Q7/Q11 decisions. Every T8 criterion is met by the code and pinned by a test that would fail if it broke;
the security items of the card all hold. 200/200 unit tests and 10/10 ITs pass (the ITs need
`DOCKER_API_VERSION=1.44` on this machine — an environment issue that also breaks `main`'s `ImportConfirmIT`).
No blocking finding. Nine non-blocking findings, mostly for T9 or a later quick change.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 below: AC-001.1–.6 (authorize half of .5/.6), .28, .39 and rows 19, 20, 38, 43, 44 all PASS |
| Completeness | pass | No MISSING. EXTRA, accepted: sign-in start on a non-Active Workspace still makes a state (the callback answers MSG120, AC-001.10); `spring-security-oauth2-jose` added unused (design Q9 / tasks.md list the pom for T8) |
| Correctness | pass | 200 unit + 10 IT green, spotless clean; no CRITICAL code-review finding; no Critical/High security finding |
| Integration | pass | Endpoints, statuses and `text/html` 400 match `frontend/openapi/tsolve-api.yaml` (T7 contract); ports `WorkspaceLookup`/`TenantScope` match design "Ports (Q11)"; full context starts (ImportConfirmIT) with the `NoWorkspaceLookup` stand-in |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.1 Workspace start → 302 to `authorize` on `TSOLVE_LOGIN_ORIGIN` | PASS | `SignInStartController.java:40-66`; target only from host (`takesTheWorkspaceOnlyFromTheHost_AC_001_1`) |
| 2 | AC-001.2 server-only, signed, 10 min, single use, one target, nonce/verifier on server | PASS | `SignedToken.java` (HMAC-SHA256, `MessageDigest.isEqual` at :43), `SignInStateService.java:49-61`, `SignInStateRepository.java:18-22` (conditional UPDATE), migration check constraint `004…sql:20-24`; IT proves 1→0 and 0 after `expires_at` |
| 3 | AC-001.3 `returnTo` filter | PASS | `SignInStateService.java:97-112`; 4 kept + 19 rejected cases incl. row 19 |
| 4 | AC-001.4 Google URL: scope, state, nonce, S256, redirect URI, `hd` | PASS | `GoogleOidcClient.java:35-49`; tests decode every parameter and recompute the S256 challenge |
| 5 | AC-001.5 redirect URI only from config; authorize off `login.` → 404 | PASS (authorize) | `GoogleOidcClient.java:62-69`, `AuthorizeController.java:43-45`, state not even looked up off `login.`; callback half is T9 (I-1) |
| 6 | AC-001.6 missing/unsigned/expired/used → 400 MSG141 page, no session | PASS (authorize) | `AuthorizeController.java:46-49`; 8 bad-state shapes, expiry +10:01, consumed state; no `Set-Cookie` |
| 7 | AC-001.28 Console start, no `hd` | PASS | `SignInStartController.java:57-58`, `AuthorizeController.java:53`; Console host never passed to `WorkspaceLookup` |
| 8 | AC-001.39 minimal page: exact Report3 text, no link, no script | PASS | `LoginPage.java`; text equals `report3-messages.json` MSG141 verbatim; escaped; CSP `default-src 'none'`; `no-store` |
| 9 | Row 38 required env, key ≥ 32 bytes, names value, never prints key | PASS | `SignInProperties.java:24-63`; re-checked by starting the jar with a 19-byte key: message names `TSOLVE_SIGNING_KEY`, key text absent from the log |
| 10 | Row 43 everything else 401, no form login, no Basic | PASS | `AuthSecurityConfig.java:47-56`; tested with `Accept: */*` and `text/html` |
| 11 | Row 44 start on unknown / `login.` / `api.` host → 404, no state | PASS | `SignInStartController.java:60-66`; `save` never called |

Security checklist of the card: HMAC + constant-time compare ✓ · key length checked, not in `toString` or start-up
message ✓ · single use by atomic UPDATE ✓ · expiry checked in `check` and in the UPDATE ✓ · nonce / PKCE verifier
kept in the row, only the S256 challenge leaves ✓ · `returnTo` filter ✓ · host compared without port, 404 off `login.` ✓ ·
redirect URI from `TSOLVE_LOGIN_ORIGIN` only ✓ · `hd` only for Workspace (and Jira-link) targets ✓ · minimal page ✓ ·
fallback chain 401 ✓ · four paths GET-only handlers, CSRF off, stateless, no cookie ✓ (see I-2 and the open call on login CSRF).

Scope: nothing from Tasks 9–12 built (only the `JIRA_LINK` enum value / constraint shape and `TenantScope` interface,
which the design's T8 data model and ports name). `ImportConfirmIT` diff is 6 property lines; `ImportControllerTest`
unchanged. The QA test files are untracked, so "no edit beyond formatting" cannot be proved from git; their content
matches the QA plan tables (rename "edge 41a" → "edge 43" as the plan said it would).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace identity STORY-001` — last line:
`trace identity (STORY-001): 4/43 acceptance criteria have a test` (only the T7 frontend ACs; the tool's globs do not
match `*Test.java`, a known harness gap). Manual trace in the test report: AC-001.1, .2, .3, .4, .5, .6, .28, .39 and
rows 19, 20, 38, 43, 44 each have named tests; none uncovered for T8.

## Issues
No blocking issue. Informational (does not block):

1. `backend/src/test/java/vn/tsolve/identity/signin/AuthorizeControllerTest.java:221-232` — `answers404ForCallbackOffLogin_AC_001_5`
   passes only because no callback handler exists (MockMvc 404 for any host). It cannot fail today. Fix (T9 QA): keep it,
   and add a positive control that the same request on `login.` does not answer 404.
2. `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java:47-56` — the ERROR dispatch to `/error` is not in the
   first chain, so in the running app an exception (e.g. DB down) or a no-handler 404 on the four paths reaches the
   second chain and an anonymous browser gets a bare 401 instead of 500/404 (by Spring Security 6 behaviour; MockMvc
   does not dispatch errors, so the tests cannot show it; not reproduced here — needs a DB). Fix: permit
   `DispatcherType.ERROR` in the second chain, or settle it when `roles-security` replaces the chain (design row 42).
3. `backend/src/main/java/vn/tsolve/identity/signin/AuthorizeController.java:52-59` — a Workspace whose
   `googleDomain` is null/blank gets no `hd` (fails open for AC-001.4); the `findById` empty → MSG141 branch has no
   test. Fix: treat a blank domain like a missing Workspace (MSG141) and add a test with the platform adapter.
4. `backend/src/main/java/vn/tsolve/identity/signin/SignInProperties.java:33-41` — an origin with a path, query or
   user-info (`https://login.x/a`, `https://u@login.x`) is accepted; `.env.example` says "no path". Fix: require
   path empty or `/`, no query, fragment or user-info.
5. `backend/src/main/java/vn/tsolve/identity/signin/SignInStateService.java:30` — identity injects the `Clock` bean
   declared in ticket's `TicketClockConfig`; ticket can change it without identity noticing. Fix: move the `Clock`
   bean to `common/config` (quick change).
6. `SignInStateService.java:16-61`, `AuthorizeController.java:46-52` — backend `AGENTS.md` rules 1, 3, 4: a concrete
   service (no interface / `service/impl`), the `SignInState` entity returned to the controller and to
   `GoogleOidcClient`, a flat `signin` package. The approved design names these classes and package, and the QA
   tests construct `new SignInStateService(...)`, so the developer followed the plan. Needs a recorded exception in
   the design or a reshape in T9 (judgment call below).
7. `backend/src/test/java/vn/tsolve/identity/signin/SignInPropertiesTest.java:83-88` — no test pins "the key is never
   printed" (Q7); I proved it once by starting the jar. Fix (QA): assert the failure text does not contain the bad key.
8. `SignInStartController.java:56-58` — every anonymous GET on `admin.` writes a `sign_in_states` row; no rate limit
   and no cleanup until T10 (`SessionCleanup`). Accepted for now; T10 should land the cleanup.
9. Environment — Testcontainers vs Docker Engine 29 (minimum API 1.44): both ITs fail without `DOCKER_API_VERSION=1.44`;
   `main`'s `ImportConfirmIT` too. Not in `docs/bug-log.md`. Fix: separate change — bump Testcontainers or document
   `api.version`; log a bug row.

### Assumptions made
- AC-001.2 "the OIDC nonce … SHALL stay on the server" is read as "kept in the server row, not in a cookie or
  browser storage"; OIDC requires sending the nonce in the authorization request, which the code does.
- AC-001.1 in the running app: with the Q11 stand-in `NoWorkspaceLookup`, every Workspace-site start answers 404
  until the platform adapter lands; accepted by Q11.
- Starting a sign-in on a suspended Workspace is allowed (spec silent; AC-001.10 rejects it at the callback).

## Test Report
See `task-8-sign-in-start-test-report.md`. `mvn -B verify`: surefire 200 run, 0 failed; failsafe 2 errors (Docker
API version, environment). Re-run `DOCKER_API_VERSION=1.44 mvn -Dtest=none … verify`: failsafe 10 run, 0 failed
(SignInStateRepositoryIT 4, ImportConfirmIT 6), `spotless:check` clean, BUILD SUCCESS. No coverage plugin.

Proposed status (not changed by the reviewer): `sign-in-start` is `pending` in progress.json; after the commit move it
`implementing` → `evaluating` → `passing`.
