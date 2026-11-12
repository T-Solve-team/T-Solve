---
module: identity
feature: operator-session
task: 29b
verdict: fail
attempt: 1
date: 2026-11-12
reviewer: tsolve-code-reviewer (Fable 5.2, medium)
iteration: 1
---

# Review: identity — Task 29b: Console callback and the Operator session

PR #212, branch `feat/KAN-233-identity-operator-session`, range `origin/main...HEAD` (head `a1cbcddb`, clean tree; the same commits the developer's verify and CI ran on).

## Summary

Changes needed: 1 Critical, 2 Important, 4 Minor.

The callback on `admin.`, the allow-list, the session checks and the logout match AC-001.29 to .34 and AC-002.1, and the tests are strong for those paths. One hole blocks the PR: an Operator session is an authenticated caller with no Workspace, and four controllers have no Role check, so an Operator on `admin.` runs them. 29-QA Q7 ("the endpoint's own 403 MSG08, no new rule") holds only for endpoints that have a Role check; the edge 141 test pins one such endpoint (`GET /api/v1/users`) and misses the rest.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | AC-001.29 to .34 and AC-002.1 behave as written, but AC-001.29 "no Workspace, none of the five Roles" is not held on endpoints without a Role check (issue 1) |
| Completeness | pass | Every criterion of the task has code and a test; no MISSING. EXTRA: ID-token-only user for the Workspace callback (issue 2) |
| Correctness | fail | Targeted tests 129/129 pass; CI backend jobs pass; 1 Critical finding |
| Integration | fail | The shared web chain (`SecurityConfig`: `/api/**` authenticated) now admits a principal that is not a `TSolveUser`; `/api/solutions` and the chatbot endpoints run for it |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.29 Operator session at the callback, no hand-off code, cookie, `returnTo`; no Account, Workspace, Role | PASS, with issue 1 | `OidcCallbackHandler.java:206-216`, `228-264`; `OperatorSignInTest` edge 139, 35, 36, 122/140; `OperatorSessionTest` AC-001.29 |
| 2 | AC-001.30 not on the list → MSG135, no session | PASS | `OidcCallbackHandler.java:211`; `refusesAnEmailThatIsNotInTheAllowList_AC_001_30`, edge 35 near-miss rows |
| 3 | AC-001.31 Keycloak error / exchange / ID token → MSG06 | PASS | `OidcCallbackHandler.java:125-128`, `141-144`; 14 rows incl. the order row (unverified beats MSG135) |
| 4 | AC-001.32 Operator session on a Workspace site → 401 MSG123 | PASS | `WebSessionFilter.java:176-180`; two-Workspace test, session kept |
| 5 | AC-001.33 removed email → session ended, 401 MSG135 | PASS | `WebSessionFilter.java:189-193`; foreground and background rows |
| 6 | AC-001.34 8 h / 60 min, background not counted | PASS | `WebSessionFilter.java:181-186`, `194-196`; 3 + 2 rows, boundary at 480/481 min |
| 7 | AC-002.1 (`admin.`) and AC-002.2 | PASS | `OperatorSessionTest.anOperatorLogsOutOnAdmin_AC_002_1`; `OpsRealmImportIT` post-logout URI (CI it-keycloak) |

### Auth checks asked for by the card

- Fixation: a new session id at every sign-in, the old cookie's session ended first (`OidcCallbackHandler.java:238-241`); tested (edge 139 row 1). OK.
- Cross-realm / cross-site request: on `admin.` the saved request must carry `target=PLATFORM_CONSOLE`, on a Workspace site the Workspace id; the registration (realm, client) comes from the saved request, so a `tsolve` request cannot be exchanged on `admin.` and the reverse. Tested with no token request sent. OK.
- Allow-list timing: checked after `email_verified` at the callback and again at every request, background too. OK.
- Host check order: host first (MSG123, session kept), then the limits (MSG07), then the allow-list (MSG135). Matches 29-QA Q6. OK.
- Cookies: `__Host-` session cookie attributes asserted; the auth-request cookie expires on every answer. OK.

### The four flagged points

1. **ID token only, no userinfo call (both callbacks).** Safe: the ID token is verified (signature, issuer, audience, expiry, nonce) and the handler never read a userinfo claim (email, `email_verified`, `organization`, `hd`, `name` and `sub` all come from the ID token). No criterion mentions userinfo; AC-001.9 lists only the exchange and the ID token as MSG06 causes, so the change moves the code towards the spec and removes one round trip. Not needed for 29b, though: the stated reason is a test fixture (the fake's userinfo `sub` is fixed). It changes Workspace sign-in in an Operator PR with no test and no design note. See issue 2.
2. **Two constructors of `OidcCallbackHandler`.** Safe (the fallback list is empty, so it fails closed) but production code shaped by a test slice. See issue 4.
3. **Every callback failure on `admin.` is MSG06.** Agrees with the task text ("every other Console failure goes to MSG06") and leaks nothing. The spec does not say it: AC-001.6 covers Workspace sites only and AC-001.31 covers a valid saved request. See issue 5.
4. **`SessionConfig`.** Needed (the filter's constructor gained the allow-list); no behaviour of its own. Accepted.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace identity STORY-001` (and STORY-002 for AC-002.1, .2):

```
✓ AC-001.29  signin/OperatorSignInTest.java, session/OperatorSessionTest.java
✓ AC-001.30  signin/OperatorSignInTest.java
✓ AC-001.31  signin/OperatorSignInTest.java
✓ AC-001.32  session/OperatorSessionTest.java
✓ AC-001.33  session/OperatorSessionTest.java
✓ AC-001.34  session/OperatorSessionTest.java
✗ AC-001.71  no test (Task 30b, not this task)
✓ AC-002.1   KeycloakLogoutUrlBuilderTest, LogoutControllerTest, OperatorSessionTest, OpsRealmImportIT
✓ AC-002.2   LogoutControllerTest, OperatorSessionTest
```

## Issues

### Critical

1. `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java:124-133` — an Operator session becomes an authenticated caller for every `/api/**` endpoint, and four controllers have no Role check, so they run for an Operator on `admin.` with no Workspace bound:
   - `knowledge/SolutionController.java:9-43` (`/api/solutions`, no `@PreAuthorize`): list, read, create, update and transition Solutions. With nothing bound the queries go to the application's own database (there is no fail-closed router in `main`), so the Operator reads and writes Workspace knowledge.
   - `chatbot/answer/controller/ChatbotAnswerController.java:30`, `chatbot/rating/controller/ChatbotRatingController.java:30,44`, `chatbot/source/controller/UnavailableDocumentsController.java:30` (`isAuthenticated()`): they run with a null `TSolveUser`; `askChatbot` does the embedding call and the Solution vector search before it fails at `ChatbotAnswerServiceImpl.java:157`.
   - Evidence in the PR's own tests: `OperatorProbeTestController` has no Role check and answers 200 to the Operator.
   - This breaks AC-001.29 ("no Account, no Workspace and none of the five Roles") and the claim of edge 141. Until now "authenticated" on the web chain always meant an Employee of a bound Workspace.
   - Fix: refuse by default. For an Operator outcome in `WebSessionFilter` (or one rule in `SecurityConfig`), answer 403 MSG08 unless the path is on a short Console list (today `POST /api/v1/auth/logout`, the CSRF bootstrap and the test probe path), and add rows to `OperatorSessionTest` edge 141 for `GET /api/solutions` and `POST /api/v1/chatbot/answers` (403 MSG08, service not called). This replaces "no new rule" of 29-QA Q7, so it needs the user's decision (open question 1).

### Important

2. `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java:77,87-91` — the Workspace sign-in no longer calls userinfo; the change is safe (see flagged point 1) but out of the task's scope, has no test and no design record, and leaves dead pieces (`KeycloakClientRegistrations.java:50` `userInfoUri`, the `invalid_user_info_response` row at `OidcCallbackHandlerTest.java:205`, the fake's userinfo stub). Fix: keep it, add one assertion that a Workspace and a Console callback send no request to `/userinfo`, and record the decision in the design (open question 2).
3. PR #212 title — the `pr-title` check fails: "subject must not be sentence-case" ("Console callback and ..."). Fix: rename to `feat(identity): add the Console callback and the Operator session (KAN-313)`.

### Minor

4. `backend/src/main/java/vn/tsolve/identity/signin/OidcCallbackHandler.java:70-101` — two public constructors and an `ObjectProvider` fallback exist only for tests; in the application the bean is always there (`SessionConfig` requires it). Fix: one constructor taking `OperatorAllowList`; add the bean to `AuthSecurityConfigTest`'s `@Import` and pass `new OperatorAllowList("")` in `OidcCallbackHandlerTest`.
5. `harnessclaude/artifacts/03-modules/identity/spec.md` AC-001.31 — the spec does not say what a missing, unknown or other-site saved request gives on `admin.` (code: MSG06, `OidcCallbackHandler.java:125-128`). Fix: one spec line through Change Mode (open question 3).
6. `backend/src/main/java/vn/tsolve/identity/signin/OidcCallbackHandler.java:211-214` — a refused (MSG135) and a successful Console sign-in write no log line, so attempts on the Console leave no trace. Fix: one INFO/WARN line each with the outcome only (no token, no cookie; the email only if NFR 3 allows it).
7. `backend/src/test/java/vn/tsolve/identity/signin/OperatorSignInTest.java:260` (edge 35) — `nullValues = "NONE"` is unused, and the NFR 3 log check runs only on the refused path. Fix: drop the attribute; add the `CapturedOutput` check to the edge 139 test.

## Security Review — identity (Task 29b) — 2026-11-12

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | not run (no dependency change in the diff) | | | |
| Code | 0 | 0 | 0 | 1 (issue 4) |
| STRIDE | 0 | 1 (issue 1, elevation of privilege) | 0 | 1 (issue 6, repudiation) |

Spoofing: state, nonce, PKCE, realm and client bound to the saved request; cookie flags asserted. Tampering: logout needs CSRF. Information disclosure: failures answer one code, logs carry class names only. Denial of service: nothing new. gitleaks passes in CI.

## Assumptions made

- An Operator reaching an endpoint without a Role check is a defect of this task (it creates the principal), not only an older gap of those controllers.
- MSG06 for a bad saved request on `admin.` is accepted behaviour (task text), pending the spec line.
- The ITs `TwoInstanceCallbackIT` and `OpsRealmImportIT` were not re-run locally; CI ran them on the same head commit.

## Open questions for the user (3)

1. Operator on an endpoint with no Role check: (A, recommended) refuse every `/api/**` path outside a short Console list with 403 MSG08; (B) add a Role check to the four controllers and keep "no new rule"; (C) both.
2. Keep the ID-token-only user for the Workspace sign-in in this PR (recommended, with a test and a design note), or revert it and fix the test fixture instead?
3. Add to AC-001.31 that a missing, unknown, used or other-site saved request on `admin.` also gives MSG06?

## Test Report

See `task-29b-operator-session-test-report.md`. Targeted run on head `a1cbcddb`: 6 classes, 129 tests, 0 failures. CI on PR #212: backend unit, it-keycloak-a, it-keycloak-b, it-other and gitleaks pass; frontend jobs skipped; `pr-title` fails (issue 3). Developer's verify (card): `mvn spotless:check test` 1425 tests, 0 failures. The full verify was not re-run.

Proposed status: `operator-session` → `fixing` (not changed; the card does not ask for it).
