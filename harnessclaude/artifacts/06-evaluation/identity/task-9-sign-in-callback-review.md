---
module: identity
feature: sign-in-callback
task: 9
verdict: pass
attempt: 1
date: 2026-11-03
reviewer: tsolve-code-reviewer (Claude Opus 5.5, independent read-only)
iteration: 1
---

# Review: identity — Task 9: sign-in-callback

## Summary
Range `74fef30..205578b` (`test(identity)` 69d4e52 and `feat(identity)` 205578b; the `docs(identity)` HLA commit
3725e7c read only for consistency — states stay in the current database, the `005` changeset goes into today's
changelog, which matches it). Every Task 9 criterion (AC-001.4, .7, .8, .9, .44–.50) is implemented and has tests
that would fail without the behaviour; 215/215 targeted tests pass on HEAD; Spotless clean. No CRITICAL finding and
no Critical/High security finding. Three should-fix items (JWKS timeouts not from the properties, silent Google
failures, stale callback 400 contract text) and four minor ones. Verdict **pass**; the should-fix items are cheap
and best done before the PR opens (Hiếu decides).

Feature status: `progress.json` shows `sign-in-callback: pending` (not `evaluating`). The card does not ask the
reviewer to change it; proposed: move it to `passing` through `sdd.ts feature identity sign-in-callback …` along the
allowed transitions.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 table below: all 11 criteria PASS |
| Completeness | pass | No MISSING. No EXTRA beyond the planned interim MSG141 answers (design "Sign-in and session rules", KAN-231 Q12) |
| Correctness | pass | 215/215 targeted; developer's full verify green on the same content (400 unit + 17 IT); no CRITICAL |
| Integration | pass | `WorkspaceLookup.findById` port only; redirect URL built from `TSOLVE_LOGIN_ORIGIN` + `siteHost` (Q1); OpenAPI updated for authorize/callback (one stale text, issue 3) |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | AC-001.4 (not yet authorized; Workspace/Jira-link needs a domain) | PASS | `AuthorizeController.java:62-78`; `sendsWorkspaceSignInToGoogle_AC_001_4`, `setsTheCookieForAJiraLinkState_AC_001_44` |
| 2 | AC-001.7 target only from the state | PASS | `CallbackController.java:81-84, 106-115` (host from `WorkspaceRef`, never the request); 4 two-Workspace tests incl. `X-Forwarded-Host` and `hd=beta` |
| 3 | AC-001.8 Google error → MSG06 on the Workspace site | PASS | `CallbackController.java:87-90`; `sendsAGoogleCancelBackWithMsg06_AC_001_8` (whole URL equal) + every error value |
| 4 | AC-001.9 exchange / ID-token failure → MSG06 | PASS | `googleAccepts`, `GoogleOidcClient.exchangeCode`, `IdTokenVerifier`; 24 tests (status codes, timeouts, faults, no `id_token`, 10 bad-token kinds, JWKS down, no email) |
| 5 | AC-001.44 cookie, attributes, hash only, authorized mark, `no-store` | PASS | `AuthorizeController.java:69-78`, `SignInCookie`, `markAuthorized` conditional UPDATE; attributes asserted one by one; IT for the SQL |
| 6 | AC-001.45 second authorize → 400, no cookie | PASS | `authorizeOnce` → `markAuthorized` returns 0; sequential and concurrent controller tests + IT `{1, 0}` |
| 7 | AC-001.46 no/wrong cookie or never authorized → consume + MSG141 | PASS | `CallbackController.java:72-80` consumes first, then `matches`; 13 tests incl. wrong name, one-char change, 8 KB, non-ASCII, `%$<>`, Console and Jira-link targets |
| 8 | AC-001.47 every answer expires the cookie | PASS | header added before any work (`CallbackController.java:70`); parameterized over 12 outcomes + the 500 (edge 42) |
| 9 | AC-001.48 sign-in start refuses a domain-less Workspace | PASS | `SignInStartController.java:57-65`; Location, no state saved, one WARN with the id only |
| 10 | AC-001.49 no Google domain at authorize and callback | PASS | null and blank, cleared after the state was made; consumed at the callback; no cookie at authorize |
| 11 | AC-001.50 Workspace gone → consume + MSG141 | PASS | Workspace and Jira-link targets; no Google call |

AC-001.14 (first-step order) is shown by `checksTheCookieBeforeGoogleError_AC_001_46` and
`checksTheCookieBeforeTheWorkspace_AC_001_14` (no Workspace lookup before the cookie check).

### Developer choices the card asked about
- **(c) `request.getCookies()` instead of `@CookieValue`** — accepted. Spring's `@CookieValue` URL-decodes the value
  and an invalid `%` sequence throws (the 500 the developer saw); a genuine value is base64url, so reading it raw
  loses nothing. Exact-name match, first cookie wins, `null` when absent; `matches` treats null/empty as no match.
- **(d) JWKS** — the hard-coded 5 s / 10 s timeouts depart from Q5 ("Google timeouts from the
  `tsolve.oidc.google.*` properties") and leave design row 23's "JWKS slow" case untested: should-fix (issue 1),
  not must-fix, because the values equal the configured ones. No JWKS cache: not against the spec or design
  "Libraries" (which names no cache); a cost and availability trade-off → follow-up decision (issue 4).
- **(b)** `MessageDigest.isEqual` over two 32-byte SHA-256 digests, with null/empty guarded — correct.
  Cookie: `__Host-TSOLVE_SIGNIN`, HttpOnly, Secure, SameSite=Lax, Path `/`, no Domain, Max-Age 600 (issue) and
  Max-Age 0 (expire) — code and tests agree. `Cache-Control: no-store` on authorize's 302 — code and test.
- **Logging** — the only new log line is the AC-001.48 WARN (`Workspace {} has no Google domain`, id only, tested).
  No token, code, cookie value, hash or email is logged; the edge 33 tests check the callback and authorize output.
  The opposite problem exists: failures are not logged at all (issue 2).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace identity` → exit 1, `trace identity: 77/129 acceptance criteria have a test`.
All Task 9 criteria show ✗ only because the tool's TEST_GLOBS do not match Java test files (known gap; Task 8's
AC-001.1–.3 show ✗ too). The manual trace in `task-9-sign-in-callback-test-report.md` finds 2–24 named JUnit
methods for each of AC-001.4, .7, .8, .9, .44–.50, and I read each criterion's tests: each asserts the behaviour.

## Issues

Should-fix (non-blocking):
1. `backend/src/main/java/vn/tsolve/identity/signin/IdTokenVerifier.java:27-28` — the JWKS connect/read timeouts are
   constants, while Q5 says the Google timeouts come from `tsolve.oidc.google.connect-timeout` / `read-timeout`.
   Effect: they cannot be tuned per environment, and a slow-JWKS test (design row 23) would wait more than 10 s, so
   there is none. Fix: inject the same two `@Value` Durations (as `GoogleOidcClient` does) and add a CallbackControllerTest
   case with a JWKS delay above the 200 ms test timeout → MSG06.
2. `GoogleOidcClient.java:67` and `IdTokenVerifier.java:68` — every failure is swallowed without a log line. A wrong
   `GOOGLE_CLIENT_SECRET`, a Google outage or a JWKS outage in production turns every sign-in into MSG06 with no
   trace for the operator. Fix: one `WARN` with the failure kind only (HTTP status, or the exception class name) —
   never the message, body, code or token — and extend the edge 33 tests to show the line holds no secret.
3. `frontend/openapi/tsolve-api.yaml:441` (callback `400`) — still "the state value is missing, wrong or expired";
   it misses used state, no or another browser's sign-in cookie (AC-001.46), Workspace gone (AC-001.50) and no
   Google domain (AC-001.49), and the `400` / `200` answers do not declare the `Set-Cookie` expiry that AC-001.47
   puts on every answer (only the `302` does). The operation description (lines 397-400) also lists MSG141 among the
   `/login?error=` redirects, while the callback's MSG141 is the 400 page. Fix: reword both, add the header to the
   400 answer, run `generate:api` (AGENTS.md rule 8: contract first).

Minor:
4. `IdTokenVerifier.java:47` (`NoOpCache`) — one Google JWKS fetch on every sign-in: one more round trip, and sign-in
   depends on the certs endpoint each time. Not against spec or design. The code comment's reason ("shared per
   URI") is imprecise: Spring's default caches inside the decoder bean, which the shared Spring test context reuses,
   so `anUnreachableJwksGivesMsg06_AC_001_9_edge23` would see cached keys. Adding a cache later means reworking that
   test and `CallbackControllerTest.java:871` (`jwksRequestCount()` = 1). Decision for Hiếu; follow-up.
5. `IdTokenVerifierTest` — no boundary test of the 60 s `exp` skew (Q4): only `exp` = now − 5 min is tested. Add
   `exp` = now − 30 s → accepted and now − 90 s → rejected.
6. `IdTokenVerifier.java:79` — `aud` is accepted when it contains the client id among others, without checking
   `azp` (OIDC Core 3.1.3.7). Google issues single-audience ID tokens, so the risk is low. Fix: require exactly one
   audience, or `azp` = client id when there are several.
7. `CallbackController.java:60-93` — the check order (business rules) is in the controller, against
   `backend/AGENTS.md` §3; it follows Task 8's `AuthorizeController` pattern. T9b adds four more checks: move the
   order into a sign-in callback service then.

Informational (no change in this task): a reverse proxy access log records the callback URL with `code` and `state`
(design row 33 "never"); both are single-use, with PKCE, but the DevOps or Task 10 setup should drop the query
string from access logs for `/api/v1/auth/*`.

## Security Review — identity (sign-in flow, Task 9) — 2026-11-03

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | 0 | 0 | 0 | 0 |
| Code | 0 | 0 | 0 | 1 |
| STRIDE | 0 | 0 | 0 | 2 |

- Secrets: none in the diff (test placeholders such as `test-secret` only; the test RSA keys are made at run time).
  Tracked env files: `.env.example`, `frontend/.env.example` only.
- Dependencies: one new, test-scope `org.wiremock:wiremock-standalone` 3.9.2. No Maven audit tool is configured
  (no OWASP dependency-check); none run, none installed.
- Code patterns: no string-built query (JPQL with parameters), no new randomness other than `SecureRandom`.
- STRIDE:
  - Spoofing: login CSRF (row 45) closed. `authorize` binds once (conditional UPDATE), the callback consumes the state
    and then requires the matching `__Host-` cookie, for all three targets. ID token: RS256 only (alg none and HS256
    rejected, tested), issuer, audience, expiry with `IdentityClock`, nonce, `email_verified` true, email present.
    Low: `aud`/`azp` (issue 6).
  - Tampering: target only from the signed state; open-redirect impossible (host from `WorkspaceRef`).
  - Repudiation: Low, failed Google steps leave no log (issue 2).
  - Information disclosure: no secret logged; the 500 log has the class and an id only; the minimal page is fixed text.
  - Denial of service: outbound calls have timeouts; worst case about 30 s per callback (token 5+10 s, JWKS 5+10 s).
    Google is reached only after a valid, authorized state and the matching cookie.
  - Elevation of privilege: nothing is signed in by this task (no session, Account or link). Tests check that no
    session cookie is set.
  - SSRF: the token and JWKS URLs come only from configuration.
  - Low (informational above): access logs at the proxy.

## Test Report
`task-9-sign-in-callback-test-report.md`: targeted 7 classes, 215 run, 0 failures, 0 skipped, BUILD SUCCESS
(16.3 s) on HEAD `205578b`, clean tree; `spotless:check` exit 0; `trace` exit 1 (tool gap; manual trace complete).
The full verify and the IT were not re-run (card): the developer's run on the same content passed (400 unit,
17 IT). No finding depends on the IT.

## Assumptions made
- "Google timeouts" in Q5 is read as covering the JWKS fetch too, so issue 1 is a deviation. Should-fix, not must-fix,
  because the values equal the configured ones.
- The developer's full verify ran on exactly the committed content. The tree is clean and my targeted run on HEAD
  agrees with it.

## Re-check (6e11d54)

Verdict: **pass**. Commit `6e11d54` read in full; no finding.
- 1a: `IdTokenVerifier` takes `tsolve.oidc.google.connect-timeout` / `read-timeout` (same properties as
  `GoogleOidcClient`; `application.yml:65-66`); the hard-coded constants are gone. Slow-JWKS test
  `aSlowJwksGivesMsg06_AC_001_9_edge23_review` (1.5 s delay vs 200 ms timeout) added. No JWKS cache added.
- 1b: one WARN per failure. Token exchange: HTTP status only, or exception class name, or "no id_token". Verifier:
  "a claim was rejected" or the exception class name. No message, body, code, token, cookie or email is logged.
  `CallbackFailureLoggingTest` and `IdTokenVerifierTest` assert exactly one WARN and that code, cookie, token,
  `invalid_grant` and the email are absent.
- 1c: OpenAPI callback 400 text adds AC-001.46/.49/.50; `Set-Cookie` expiry documented on 200 and 400; MSG141 removed
  from the redirect list; `session.ts` regenerated (one comment line).
- 2: skew tests exp now-30 s accepted, now-90 s rejected.
- Existing tests: `CallbackControllerTest` gains one method and changes none; `IdTokenVerifierTest` changes only the
  `verifierAt` constructor args (two 200 ms Durations) plus new methods.
- `theCallbackLogsNoSecrets_edge33` no longer depends on method order: the two WARN tests live in
  `CallbackFailureLoggingTest` (own context and captured output).
- Targeted run (`IdTokenVerifierTest, CallbackControllerTest, CallbackFailureLoggingTest`): 118 run, 0 failures,
  0 errors, 0 skipped, BUILD SUCCESS. The ERROR line in the log is the intended edge 42 test. Full verify not re-run
  (card).
- Minor note: the "a claim was rejected" WARN has no test of its own; it holds no data, so it does not block.
