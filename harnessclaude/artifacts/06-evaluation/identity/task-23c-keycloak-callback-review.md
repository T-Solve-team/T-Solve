---
module: identity
feature: keycloak-callback
task: 23c
verdict: pass
attempt: 3
date: 2026-11-07
reviewer: tsolve-code-reviewer (Claude Opus 5.5, independent, read-only)
iteration: 1
---

# Review: identity — Task 23c: keycloak-callback (KAN-289)

## Summary
Scope: the uncommitted diff in the worktree `T-Solve-KAN-289` on `feat/KAN-289-identity-keycloak-callback` (base
`afbef16f`). The production change is `AuthSecurityConfig.java` (a new `@Order(0)` chain and the private record
`CheckedRequests`), plus the new `signin/OidcCallbackHandler.java` and `dev/DevClaimsController.java`. The tests are
QA's: the `AuthSecurityConfigTest` diff and the new `OidcCallbackHandlerTest`, `TwoInstanceCallbackIT`,
`KeycloakSignInEndToEndIT`, `FakeKeycloakRealm` and `TestBrowser`. `pom.xml` and `application.yml` are unchanged, and
the old `login.` classes are untouched.

The chain wiring is correct, and so are the order of the request check (before the Keycloak `error` and before the
code exchange), the Workspace match, the `email_verified` check, the cookie expiry and the no-session behaviour. I
found no open redirect and no reflected input. No email, `sub` or token value is logged.

Verdict: **fail (fix first)**. There are 2 blockers. First, `mvn spotless:check` fails on one production line and on
5 QA test files. Second, every successful exchange keeps the access and refresh token in a replica-local in-memory
store. That contradicts Q2 A ("nothing stored") and the intent of AC-001.55. There are also 2 should-fix items and
4 notes.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass, with exceptions | Each AC checked below. A Keycloak `error=invalid_request` maps to MSG141 instead of MSG06 (finding 3) |
| Completeness | pass | Every AC in scope has a test. There is no EXTRA behaviour, except the in-memory authorized-client store Spring adds (finding 2) |
| Correctness | fail | The 39 unit tests pass (re-run fresh). `spotless:check` would fail (finding 1). Redis down logs 2 ERROR entries instead of 1 (finding 4) |
| Integration | pass | The chain matches only Workspace hosts. `login.`, `admin.` and unknown hosts fall through to the old chain. Spring's default OAuth2 paths give 401 |

### Stage 1: spec compliance

| AC | Status | Evidence (test) |
|----|--------|-----------------|
| AC-001.6 | PASS | H1 `mapsAMissingOrUnmatchedRequestToMsg141_AC_001_6`; A1 `answersAWorkspaceCallbackStatelessly_AC_001_55` (no cookie gives MSG141); T2 `refusesACallbackThatMatchesNoSavedRequest_AC_001_6_edge122` (rows: no cookie, attacker's state, replay, key gone, other Workspace; 0 token requests); E2 `refusesTheCallbackInASecondBrowserWithMsg141_AC_001_6_edge122` |
| AC-001.8 | PASS, with one gap | H2, T3 `answersMsg06ToAKeycloakErrorOnlyWithAMatchingRequest_AC_001_8`, E4 `answersMsg06ToAKeycloakErrorReturnedToTheCallback_AC_001_8`. Gap: a Keycloak `error=invalid_request` with a valid request answers MSG141 (finding 3) |
| AC-001.9 | PASS | H3, H4 `refusesAnUnverifiedEmailWithMsg06_AC_001_9`, and T4 `answersMsg06WhenTheExchangeOrTheIdTokenFails_AC_001_9` with 10 rows: token 400, 500 and reset; another key; `iss` of `tsolve-ops`; `aud` `tsolve-console`; expired; another nonce; `email_verified` false or missing |
| AC-001.14 (first two stages only) | PASS | T3 row `false`: an error with a non-matching state gives MSG141 before the error is mapped. `CheckedRequests.removeAuthorizationRequest` runs before Spring's exchange validator |
| AC-001.53 (end to end) | PASS | H5 `expiresTheAuthRequestCookieInEveryAnswer_AC_001_53` (5 answers); `assertRefusal` in every IT refusal; E1 `signsInThroughKeycloakAndShowsTheVerifiedClaims_AC_001_53_AC_001_65_edge122`. Every answer has exactly one `Set-Cookie`: empty, `Max-Age=0`, `Path=/`, `Secure`, `HttpOnly`, `SameSite=Lax`, no `Domain`. Only the 500 paths are not covered (note 7) |
| AC-001.54 (end to end) | PASS | A2 `leavesTheCallbackOnLoginAdminAndUnknownHostsToTheOldChain_AC_001_54`, A3 (Spring's default paths give 401), T5 `answers404ToTheCallbackOnAdminAndOnAHostWithNoWorkspace_AC_001_54` (with no Redis read), E1 (Keycloak's 302 goes to exactly `http://<slug>.localhost:8080/api/v1/auth/callback`), E3 (an unknown port is refused) |
| AC-001.55 | PASS on its wording; fails Q2 A | A1 and T1 `answersOnBTheCallbackOfASignInStartedOnA_AC_001_55_edge115`: B finishes A's sign-in, the PKCE verifier matches, 0 Tomcat sessions on A and B, no `JSESSIONID`, no new Redis key, and a later request gets 401 on both. Neither test sees the in-memory `OAuth2AuthorizedClientService` (finding 2) |
| AC-001.63 (end to end) | PASS (config only) | No new test; none can wait 8 h or 60 min. `RealmImportIT.limitsSsoSessionTo8HoursAnd60MinutesIdle_AC_001_63_edge114` and manual check M5 |
| AC-001.64 (end to end) | PASS | E5 `showsNoCompanyListAndNoPasswordFormWithoutTheHint_AC_001_64_edge104` (guard); E6 `createsAFirstTimeGoogleAccountWithNoReviewAndJoinsTheOrganization_AC_001_64_AC_001_65` (no review page, user added to the Organization); `RealmImportIT` |
| AC-001.65 (end to end) | PASS | E1 (the `organization` and `hd` claims come through the real Keycloak, and `sub` equals the Keycloak user id); E6; `RealmImportIT` (confidential client, PKCE S256) |
| Q2 A / edge 122 | PASS, except finding 2 | H6 (forward, exactly 4 claims, no token values), H7 (MSG06 and one WARN, no PII), T1, E1, and T6 (a direct request gets no claims) |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace identity`, run from the worktree root (the lines for this task):

```
✓ AC-001.6   AuthSecurityConfigTest, AuthorizeControllerTest, CallbackControllerTest, OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, TwoInstanceCallbackIT
✓ AC-001.8   CallbackControllerTest, OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, TwoInstanceCallbackIT
✓ AC-001.9   CallbackControllerTest, CallbackFailureLoggingTest, IdTokenVerifierTest, OidcCallbackHandlerTest, TwoInstanceCallbackIT
✓ AC-001.53  KeycloakAuthorizationRequestResolverTest, OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, RedisAuthorizationRequestRepositoryIT
✓ AC-001.54  AuthSecurityConfigTest, KeycloakSignInEndToEndIT, RealmImportIT, TwoInstanceCallbackIT
✓ AC-001.55  AuthSecurityConfigTest, TwoInstanceCallbackIT
✓ AC-001.63  RealmImportIT
✓ AC-001.64  KeycloakSignInEndToEndIT, RealmImportIT
✓ AC-001.65  KeycloakSignInEndToEndIT, RealmImportIT
```

## Issues

1. **Blocker: `spotless:check` fails.**
   - Where: `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java:62` reads `&&workspaces.findBySiteHost(...)`; palantir wants `&& workspaces`.
   - The QA test files are not palantir-formatted either. The line diffs are: `OidcCallbackHandlerTest` 17, `TwoInstanceCallbackIT` 56, `KeycloakSignInEndToEndIT` 91, `FakeKeycloakRealm` 4, `TestBrowser` 7. `AuthSecurityConfigTest` is clean.
   - Proof: I ran palantir-java-format 2.39.0 `--palantir` (the pom's formatter) on each file in a scratch dir. The production diff is exactly that one line, so the hand-applied wrap lost a space.
   - Fix: add the space (dev). Run `mvn spotless:apply` on the 5 test files from a normal checkout before the `test(identity)` commit (QA/orchestrator, since the dev card cannot touch tests).
2. **Blocker: access and refresh tokens kept per replica, never removed.**
   - Where: `AuthSecurityConfig.java:64` (the `oauth2Login` block).
   - Problem: `OAuth2LoginAuthenticationFilter` calls `OAuth2AuthorizedClientRepository.saveAuthorizedClient` after every successful exchange. I confirmed this in the 6.3.4 bytecode. Boot's `OAuth2WebSecurityConfiguration` supplies `AuthenticatedPrincipalOAuth2AuthorizedClientRepository` over `InMemoryOAuth2AuthorizedClientService` (a `ConcurrentHashMap` with no eviction), so the replica that answered keeps the access and refresh token per (registration, `sub`). This also happens for sign-ins the handler then refuses (`email_verified` false) and for the non-dev MSG06 answer.
   - Q2 A says "nothing stored". AC-001.55 and the K7 intent say there is no replica-local state. T1 checks Redis and Tomcat sessions, so it cannot see this store.
   - Fix: `login.authorizedClientRepository(...)` with a no-op repository (save and remove do nothing, load returns null). Ask QA for a T1 assertion that `instanceB.getBean(OAuth2AuthorizedClientService.class).loadAuthorizedClient("tsolve-web", sub)` is null.
3. **Should-fix: a Keycloak `error=invalid_request` answers MSG141, but AC-001.8 says MSG06.**
   - Where: `backend/src/main/java/vn/tsolve/identity/signin/OidcCallbackHandler.java:38-42` and `:53-55`.
   - Problem: `invalid_request` is in `REQUEST_CHECK_FAILURES` so that a callback with neither `code` nor `error` maps to MSG141 (Q2). But `invalid_request` is also a standard error that Keycloak returns to the redirect URI (RFC 6749 §4.1.2.1). With a valid saved request, the provider then throws `OAuth2AuthenticationException("invalid_request")`, and the handler answers MSG141. AC-001.8 and AC-001.14 require MSG06.
   - Fix: decide by whether the request check passed, not by the error code. Have `CheckedRequests` set a request attribute once the saved request matches. In the handler, answer MSG141 when that attribute is absent and MSG06 otherwise; the code list then goes. H1's `invalid_request` row still holds, because no check ran. Ask QA for a T3 or H2 row: `error=invalid_request` with the matching state gives MSG06.
4. **Should-fix: Redis down writes 2 ERROR entries, but Q5 B says one.**
   - Where: `AuthSecurityConfig.java:104-105`.
   - Problem: `CheckedRequests` logs ERROR and then throws `InternalAuthenticationServiceException`. `AbstractAuthenticationProcessingFilter` logs that type again at ERROR ("An internal error occurred while trying to authenticate the user."), each with a full stack trace. My fresh run of `AuthSecurityConfigTest` shows both lines. The test `answersMsg06WhenRedisIsDownDuringTheCallback_AC_001_6` does not count ERROR lines.
   - Fix: throw `AuthenticationServiceException` instead (the handler still maps it to MSG06, and Spring does not log it at ERROR), and keep the one own `log.error`. Ask QA to assert exactly one ERROR line, with `OutputCaptureExtension` as in H7.
5. **Note: why the forward to the claims page passes is described wrongly.**
   - The hand-off says the security filter is not registered for FORWARD. In Boot 3.3.5, `spring.security.filter.dispatcher-types` defaults to all types (`EnumSet.allOf`), so the forward does run the security chains.
   - It reaches `DevClaimsController` because the `webSecurityChain` (`/api/** authenticated()`) sees the `OAuth2AuthenticationToken` still in the `SecurityContextHolder`. The second `SecurityContextHolderFilter` is skipped by its applied-attribute.
   - The `DevClaimsController` Javadoc ("a direct request ... gets a 400") is also inaccurate: a direct request gets 401 MSG07 from the web chain.
   - Fix: correct both comments. T1 and E1 are the guards.
6. **Note: test quality.**
   - The Redis-down test is named `…_AC_001_6`, but it pins Q5 B (MSG06), not AC-001.6, so `trace` counts it under the wrong criterion.
   - The ID-token rows of T4 (another key, `iss`, `aud`, `exp`, nonce, `email_verified`) assert only MSG06. They do not assert that the exchange happened (`tokenRequests` has size 1), so a row could pass because of an unrelated exchange failure. T1 is the positive control on the same fixture, which limits the risk.
   - The `FakeKeycloakRealm` userinfo has no `email_verified`. That is harmless, because the check reads `getIdToken()`.
   - Fix (QA): rename the Redis-down test to `…_edge_q5` (or similar), and add `hasSize(1)` on the token requests in the ID-token rows.
7. **Note: a few answers can be a 500 without the expiry cookie (AC-001.53, "every answer").**
   - Case 1: `WorkspaceLookup.findBySiteHost` throws. In `CheckedRequests` (`AuthSecurityConfig.java:110-114`) it is outside the `try`; it also runs in the chain matcher.
   - Case 2: the principal is not an `OidcUser`, so `OidcCallbackHandler.java:62` casts and fails.
   - Neither can happen with today's in-memory dev lookup and the fixed `openid` scope.
   - Fix: for case 1, wrap the lookup in `CheckedRequests` in the same `try`, once the platform lookup (DB) exists. Not needed for this slice.
8. **Note, accepted under Q2:** a callback with neither `code` nor `error` leaves the saved request in Redis until its 10-minute TTL (its cookie is expired). No action.

## Assumptions made
- I rate finding 2 a blocker because Q2 A says "nothing stored" in so many words, and the store holds refresh tokens. AC-001.55's wording names only the HTTP session and the `SecurityContext`, so the orchestrator may downgrade it to should-fix.
- The developer's verify result for the ITs (TwoInstanceCallbackIT 21, KeycloakSignInEndToEndIT 7 green) is taken from the card. I did not re-run the ITs, because no finding needed them.

## Test Report
`backend: mvn -B test -Dtest=OidcCallbackHandlerTest,AuthSecurityConfigTest`, run fresh on the uncommitted state:
`Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS. Details are in `task-23c-keycloak-callback-test-report.md`.

---

# Attempt 2 (2026-11-07): re-review of the fixes for findings 1–5

## Summary (attempt 2)
Scope: only the fixes for findings 1–5, in the uncommitted worktree state. The 3 production files were last written at
19:31 (the developer's Spotless copy-back); the QA test files at 19:21–19:25. What attempt 1 passed is not re-reviewed.

Verdict: **fail (fix first)**, with 1 blocker that is format-only and sits in 3 QA test files (finding 9). The
production code passes all four checks: findings 1 (production part), 2, 3, 4 and 5 are fixed, and I found no
regression. There are 2 new notes.

## Checks (attempt 2)

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | No-op authorized-client repository; no other token store | PASS | See 1 below |
| 2 | `invalid_request` split | PASS | See 2 below |
| 3 | Redis down: one ERROR, 302 MSG06, expiry cookie, no `JSESSIONID`, no token in logs | PASS on Q5 B; one judgment call (finding 10) | See 3 below |
| 4 | Formatting and the two comments | PASS for the 3 production files and the comments; FAIL for 3 QA test files (finding 9) | See 4 below |
| R | Regression from the new `AuthSecurityConfig` code | PASS | See R below |

1. **The no-op repository leaves Boot's store empty.**
   - `AuthSecurityConfig.java:68` sets `NoAuthorizedClients` (`:131-153`) on this chain only. It is a private class built inline, not a bean, so Boot's `OAuth2AuthorizedClientService` and `OAuth2AuthorizedClientRepository` beans stay in place, unused. A grep of `src/main` finds no other `oauth2Login` or `oauth2Client`.
   - `OAuth2LoginAuthenticationFilter` saves the client before it calls the success handler, so the no-op also covers the sign-ins the handler refuses (`email_verified` false or missing) and the non-dev MSG06.
   - T1 and all 10 T4 rows call `assertNoAuthorizedClient()` on both instances for (`tsolve-web`, `kc-user-alice`). Those match the registration id and `userNameAttributeName("sub")` in `KeycloakClientRegistrations.java:24-36`. QA saw these rows fail before the fix, so the assertion is not vacuous.
   - Nothing else keeps token values:
     - The callback chain is STATELESS and uses `NullSecurityContextRepository`.
     - On the forward, the web chain's `SecurityContextHolderFilter` is skipped, and Spring Security 6 saves no context implicitly. The web chain also has no `SessionManagementFilter`, because explicit strategies are the default.
     - T1 asserts 0 Tomcat sessions, no `JSESSIONID`, no new Redis key, and a 401 afterwards on both instances.
     - The request attribute and the body hold only `sub`, `email`, `organization` and `hd` (`OidcCallbackHandler.java:68-72`; T1 asserts exactly those 4 names).
     - No log statement takes a token.
2. **The `invalid_request` split holds.**
   - The attribute is set at `AuthSecurityConfig.java:126`. That is after the Redis read (`:106`), after the early return when nothing is saved (`:112`), and after the state and `workspace_id` match (`:120-125`). It is never set before the Redis read.
   - Spring throws its own `invalid_request` before it calls `removeAuthorizationRequest` (a callback with neither `code` nor `error`, or one without `state`). The attribute is then absent, and the answer is MSG141 (`OidcCallbackHandler.java:53-56`; H1 row `invalid_request`).
   - An unmatched request throws `invalid_state_parameter` and answers MSG141 (T2; new T3 row `false`). A Keycloak `invalid_request` with a matching request answers MSG06 (new H row `mapsInvalidRequestFromKeycloakWithAMatchingRequestToMsg06_AC_001_8`; new T3 row `true`).
   - The test literal `vn.tsolve.identity.signin.requestChecked` equals the constant (`OidcCallbackHandler.java:39`).
   - Side effect, correct under AC-001.9: a token-endpoint 400 with body `{"error":"invalid_request"}` now also answers MSG06 (it was MSG141). No row covers it (finding 11).
3. **Redis down.** I ran `answersMsg06WithOneErrorWhenRedisIsDownDuringTheCallback_edgeQ5B` alone:
   - It logs exactly one ERROR line, `vn.tsolve.identity.AuthSecurityConfig : Redis failed while reading the saved authorization request`. Spring's "An internal error occurred" line is gone.
   - The test asserts 302 `/login?error=MSG06`, the expiry cookie, no HTTP session, no `JSESSIONID`, no controller call, and exactly one line that contains ` ERROR `.
   - The cookie value (`a-cookie-value`) does not appear in the log.
   - However, that one entry carries the full stack trace (`log.error(msg, e)` at `:108`). The card asks for "no stack trace", but Q5 B only asks for one ERROR line (finding 10).
4. **Formatting and comments.** I ran palantir-java-format 2.39.0 `--palantir` on copies of the files with the CRs stripped.
   - Clean, 0 diff lines: `AuthSecurityConfig`, `OidcCallbackHandler`, `DevClaimsController`, `AuthSecurityConfigTest`, `OidcCallbackHandlerTest`, `TwoInstanceCallbackIT`.
   - `&& workspaces` is at `AuthSecurityConfig.java:65`. The longest production line is 120 columns (`OidcCallbackHandler.java:38`).
   - Still not formatted, with the same counts as in attempt 1: `KeycloakSignInEndToEndIT` 91, `TestBrowser` 7, `FakeKeycloakRealm` 4 (finding 9).
   - The comments now state the truth (Boot 3.3.5 in the pom):
     - `DevClaimsController.java:10-14`: forwards are filtered, the sign-in is still in the security context, and a direct request gets 401.
     - `OidcCallbackHandler.java:24-28` and `:38`.
     - `AuthSecurityConfig.java:109`.
- **R. No regression.**
  - No bean is added or overridden: `NoAuthorizedClients` and `CheckedRequests` are private and built inline.
  - The chain orders are unchanged: `keycloakCallbackChain` 0, `signInRedirectChain` 1, `jiraAppSecurityChain` 1 (this tie existed before), `webSecurityChain` lowest.
  - The Workspace-host match is unchanged in the security matcher (`:63-65`) and in `CheckedRequests` (`:115-122`).
  - T5 (404 on `admin.` and on an unknown host, Redis key untouched) passes in my IT run.

## Issues (attempt 2)

9. **Blocker (format only; QA or orchestrator): 3 QA test files are not palantir-formatted.**
   - Impact: `mvn verify` (`spotless:check`, ratchet from `origin/main`) fails, so the `test(identity)` commit cannot pass the project's verify.
   - Where:
     - `backend/src/test/java/vn/tsolve/identity/signin/KeycloakSignInEndToEndIT.java`: 91 diff lines; for example `:207` is 124 columns and `:249` is 123.
     - `backend/src/test/java/vn/tsolve/identity/signin/TestBrowser.java`: 7 lines (`:133` is 121 columns, and `:149`).
     - `backend/src/test/java/vn/tsolve/identity/signin/FakeKeycloakRealm.java`: 4 lines (`:95-96`).
   - The QA hand-off says these files were reformatted. The worktree copies (written at 19:21:33) were not, and their diff counts are the same as in attempt 1.
   - Fix: run `mvn spotless:apply` on these 3 files (from a checkout with git, or the scratch copy), copy them back, and confirm `spotless:check` exits 0. The developer has nothing to do.
10. **Note, a judgment call: the single Redis-down ERROR entry includes a stack trace** (`AuthSecurityConfig.java:108`).
    - It holds no cookie or token value.
    - Option A (recommended): keep it. The trace names the Redis failure for operations, and Q5 B counts entries, not lines.
    - Option B: log `e.toString()` without the throwable.
    - The user decides.
11. **Note (QA, optional): two rows are missing.**
    - A token-endpoint 400 with `{"error":"invalid_request"}`, which now answers MSG06 because the attribute is set.
    - A chain-level row for a callback with neither `code` nor `error` (`AuthSecurityConfigTest` or T3). MSG141 there relies on Spring's order and on H1.

Earlier notes:
- 6 is fixed by QA: the test is renamed to `…_edgeQ5B`, and `hasSize(1)` is on the token requests in every T4 row (`TwoInstanceCallbackIT.java:411`).
- 7 and 8 are unchanged, and no worse.

## Traceability (attempt 2)
`bun harnessclaude/hooks/sdd.ts trace identity`, run from the worktree root (the lines for the fixed criteria):

```
✓ AC-001.6   AuthorizeControllerTest, CallbackControllerTest, OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, TwoInstanceCallbackIT
✓ AC-001.8   CallbackControllerTest, OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, TwoInstanceCallbackIT
✓ AC-001.9   CallbackControllerTest, CallbackFailureLoggingTest, IdTokenVerifierTest, OidcCallbackHandlerTest, TwoInstanceCallbackIT
✓ AC-001.53  KeycloakAuthorizationRequestResolverTest, OidcCallbackHandlerTest, KeycloakSignInEndToEndIT, RedisAuthorizationRequestRepositoryIT
✓ AC-001.54  AuthSecurityConfigTest, KeycloakSignInEndToEndIT, RealmImportIT, TwoInstanceCallbackIT
✓ AC-001.55  AuthSecurityConfigTest, TwoInstanceCallbackIT
```

`AuthSecurityConfigTest` has left AC-001.6 because the Redis-down test was renamed to `_edgeQ5B` (note 6). That is
correct, and AC-001.6 still has its own tests.

## Assumptions made (attempt 2)
- I keep finding 9 a blocker, as in attempt 1, because the project's verify includes `spotless:check` before a commit.
  It is format-only and needs no third code review: a `spotless:check` exit 0 on all files can close it.
- I re-ran `TwoInstanceCallbackIT` because the production files were rewritten (the Spotless copy-back at 19:31) after
  the developer's IT run. I did not re-run `KeycloakSignInEndToEndIT` (7/7 is the developer's run from before that
  copy-back). The developer reports that copy-back as whitespace-only.
- Proposed feature status (I did not change it): `fixing` now, then `passing` once finding 9 is closed.

## Test Report (attempt 2)
- `cd backend && mvn -B test -Dtest=OidcCallbackHandlerTest,AuthSecurityConfigTest`: `Tests run: 40, Failures: 0,
  Errors: 0, Skipped: 0` (15 + 25), BUILD SUCCESS.
- `cd backend && mvn -B verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TwoInstanceCallbackIT
  -DargLine=-Dapi.version=1.44 -Dspotless.check.skip=true`: `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`,
  BUILD SUCCESS.
- `mvn -B test -Dtest='AuthSecurityConfigTest#answersMsg06WithOneErrorWhenRedisIsDownDuringTheCallback_edgeQ5B'`: 1/1,
  with one ERROR line in the output.
- Details are in `task-23c-keycloak-callback-test-report.md`.

---

# Attempt 3 (2026-11-07): verdict re-issued as pass

## Summary (attempt 3)
Verdict: **pass**. Finding 9 (the only blocker left after attempt 2) is closed by `d8d94a50`. The attempt 2 check
was a palantir-java-format run on scratch copies, not a `mvn spotless:check` on the committed state. Earlier claims
were wrong in two ways:
- The developer's hand-off said finding 9 "did not reproduce". It reproduces on `7f5e87cf`. An earlier format card's
  `spotless:check` exit 0 ran on a scratch copy that was not the committed state, so it was no evidence for the commit.
- `.husky/pre-commit` runs only gitleaks and lint-staged (frontend). The earlier commits passing that hook proves
  nothing about backend formatting.

## Checks (attempt 3)
A fresh reviewer ran `mvn spotless:check` in `backend` on its own fresh clone (`origin/main` present, ratchet active),
over the whole backend:

| Commit | Result | Evidence |
|--------|--------|----------|
| `7f5e87cf` | exit 1 | lists `TestBrowser`, `KeycloakSignInEndToEndIT`, `FakeKeycloakRealm` (finding 9 reproduced) |
| `d8d94a50` | exit 0 | 7 s |

Production code was already judged ready in attempt 2; no production file changed in attempt 3, and no code was reviewed
again.

## Issues (attempt 3)
- Finding 9: fixed in `d8d94a50`; closed by the `spotless:check` exit 0 above.
- Notes 7, 8, 10 and 11 stay open as notes. None of them blocks the verdict.
- Findings 1 to 5 were closed in attempt 2 and are not re-reviewed here.
