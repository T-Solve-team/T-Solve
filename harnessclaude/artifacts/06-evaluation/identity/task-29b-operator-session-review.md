---
module: identity
feature: operator-session
task: 29b
verdict: pass
attempt: 3
date: 2026-11-13
reviewer: tsolve-code-reviewer (Fable 5.2, medium)
iteration: 1
---

# Review: identity — Task 29b: Console callback and the Operator session

PR #212, branch `feat/KAN-233-identity-operator-session`, head `75281b55` (clean tree, equal to the pushed head and to the PR's head). Attempt 3 covers `01c7a7ae..HEAD`: `757c1991` (tests), `8d699872` (fix), `75281b55` (review record only), plus `WebSessionFilter` read as a whole. CI on this head (`gh pr checks 212`): backend unit, it-keycloak-a, it-keycloak-b, it-other, gitleaks and pr-title pass; the frontend jobs are skipped (no frontend change).

## Attempt 3 (2026-11-13)

### Summary

Approve: 0 Critical, 0 Important, 2 Minor (new). Earlier findings still open: 1 (attempt-1 finding 7, Minor). Nothing open blocks the merge.

The Console rule now refuses every Operator request whose method and raw path are not exactly an entry of `ConsolePaths`, so no spelling of a path can pass it. The reviewer probe found no way around it and no second way to the logout endpoint.

### Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.29: every `/api/**` request of an Operator session outside the Console list gets 403 MSG08, in plain and encoded form; AC-001.34: limits apply, and limits come before the allow-list (decision of 2026-11-13) |
| Completeness | pass | No MISSING. One EXTRA that the user accepted: the rule also refuses non-API paths (A3-1) |
| Correctness | pass | Targeted tests 148/148 pass at this head; CI passes; no Critical finding |
| Integration | pass | No endpoint other than `POST /api/v1/auth/logout` runs for an Operator; sign-in and callback are on their own chains and do not pass this filter, so an Operator with a live session can sign in again |

### Earlier findings

| # | Finding | Status | Evidence |
|---|---------|--------|----------|
| 1 / A2-1 | Critical: Operator reaches endpoints with no Role check; encoded-path bypass | **closed** | `WebSessionFilter.java:207`: no prefix test, exact match on `ConsolePaths`; `OperatorSessionTest.java:282-293` (3 encoded rows, 403 MSG08, `scope.runIn` never called); probe below |
| 6 | Minor: no log line for a refused or successful Console sign-in | **closed** | `OidcCallbackHandler.java:177,181`: one INFO line each, outcome only; the success line is written only when the session was stored |
| 7 | Minor: unused `nullValues = "NONE"`; NFR 3 log check only on the refused path | **open** | `OperatorSignInTest.java:302,378` unchanged; the success path still has no `CapturedOutput` check, and it now has a log line of its own. By reading, that line carries no email, token or cookie |
| A2-2 | Minor: expiry rows do not assert that no `SessionEnded` is published | **closed** | `OperatorSessionTest.java:230,242,264` |
| A2-3 | Minor: no test for expired and off the allow-list | **closed** | `OperatorSessionTest.java:267-280` (401 MSG07, key gone, cookie expired, no event) |
| A2-4 | Minor: `userInfoUri` still set | **closed** | Line removed from `KeycloakClientRegistrations.java`; no other userinfo URI in `main` |
| A2-5 | Minor: Javadoc silent on the Operator order | **closed** | `WebSessionFilter.java:46-49` |

### New issues

#### Critical

None.

#### Important

None.

#### Minor

A3-1. `backend/src/main/java/vn/tsolve/identity/session/ConsolePaths.java:6-7` and spec.md AC-001.29 — both still say the rule covers "`/api/**` requests"; the code now refuses every request of an Operator session that is off the list (`/actuator/health` and `/v3/api-docs` give 403 MSG08 with an Operator cookie). The user accepted this on 2026-11-13, but it is written only in the card. Fix: correct the Javadoc; add one Changelog line to the spec (Change Mode) or a design note. Open question 1.
A3-2. `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java` (missing rows) — the tests pin the encoded `/api/solutions` forms only. Nothing pins that the logout entry is matched exactly (`GET /api/v1/auth/logout`, `POST /api/v1/auth/logout/`, `POST /api/v1/auth/logou%74` all give 403 today) or that a non-API path is refused. A later change of `ConsolePaths.allows` to a prefix or pattern match would fail no test. Fix: three or four rows in the edge 141 parameterized test.

### The card's questions

**Is the bypass closed for other forms?** Yes. The rule compares `METHOD + " " + getRequestURI()` with the list by string equality (`ConsolePaths.java:19-21`), so only the exact raw spelling passes and every other form is refused, by this filter or by Spring's `StrictHttpFirewall` before it. Reviewer probe, scratch copy of the head, whole application through MockMvc, an allow-listed Operator session on `admin.localhost`:

| Request | Answer |
|---------|--------|
| `GET /api/solutions`, `GET /api/solutions/` | 403 MSG08 |
| `GET /%2561pi/solutions` (double encoding) | 400 (firewall) |
| `GET /api/solutions;x=1`, `GET /api;x=1/solutions` | 400 (firewall) |
| `GET //api/solutions`, `GET /api//solutions` | 400 (firewall) |
| `GET /api/v1/../solutions`, `/x/../api/solutions`, `/api/%2e%2e/api/solutions`, `/api/./solutions` | 400 (firewall) |
| `GET /api/solutions%2f`, `GET /api/solutions%00` | 400 (firewall) |
| `GET /actuator/health`, `GET /v3/api-docs` | 403 MSG08 |
| `GET /api/solutions` with `X-HTTP-Method-Override: POST` | 403 MSG08 |

**Is the logout path matched exactly?** Yes, by method and by path.

| Request | Answer |
|---------|--------|
| `POST /api/v1/auth/logout` (and with `?x=1`; the query is not part of the path) | 200 `{ logoutUrl }` |
| `GET /api/v1/auth/logout`, `PUT /api/v1/auth/logout` | 403 MSG08 |
| `GET /api/v1/auth/logout` with `X-HTTP-Method-Override: POST` or `?_method=POST` | 403 MSG08 |
| `POST /api/v1/auth/logout/`, `POST /api/v1/auth/logou%74` | 403 MSG08 |
| `POST /api/v1/auth/logout;x=1`, `POST //api/v1/auth/logout`, `POST /api/v1/auth/../auth/logout` | 400 (firewall) |

With the test's second entry (`GET /api/v1/test/operator`): `HEAD`, a trailing slash, `operato%72`, `POST ?_method=GET` and `POST` with `X-HTTP-Method-Override: GET` all give 403 MSG08. No method override is active: `spring.mvc.hiddenmethod.filter.enabled` is not set, and Spring does not read the override header. If the hidden-method filter were turned on later it would run before the security chain, so the filter and Spring MVC would still see the same method. No `context-path` is set; one would make the logout entry miss and refuse (fail closed).

Limits of the probe: MockMvc, not a live Tomcat. The result does not depend on how a container normalises the path, since any raw path other than the exact entry is refused.

**Do the new log lines leak anything?** No. `Console sign-in refused (MSG135)` and `Console sign-in succeeded` are constant texts: no email, token, cookie, state or host, and no user input, so no log injection. They do not name the actor; that follows the "no email" decision (NFR 3) and is not a finding.

**`WebSessionFilter` as a whole.** Order for an Operator: host (MSG123, session kept), limits (MSG07, session ended, no event), allow-list (MSG135, session ended, no event), Console list (MSG08, session kept and not touched), then touch unless background. Read against the code at lines 173-215: it holds. A refused request never reaches `chain.doFilter`, so no CSRF, authorization or controller code runs for it. The filter is skipped on ERROR and ASYNC dispatches (`OncePerRequestFilter` defaults); on those the request is either public by rule or carries the saved context of a request that already passed the list. The Workspace path is unchanged by this range.

### Traceability

`bun harnessclaude/hooks/sdd.ts trace identity STORY-001` / `STORY-002`, run at this head (rows of this task):

```
✓ AC-001.29  signin/OperatorSignInTest.java, session/OperatorSessionTest.java
✓ AC-001.30  signin/OperatorSignInTest.java
✓ AC-001.31  signin/OperatorSignInTest.java
✓ AC-001.32  session/OperatorSessionTest.java
✓ AC-001.33  session/OperatorSessionTest.java
✓ AC-001.34  session/OperatorSessionTest.java
✓ AC-002.1   KeycloakLogoutUrlBuilderTest, LogoutControllerTest, OperatorSessionTest, OpsRealmImportIT
✓ AC-002.2   LogoutControllerTest, OperatorSessionTest
```

The tests named for AC-001.29 and AC-001.34 were read: they check the criterion (403 MSG08 with no Workspace bound; 401 MSG07 with the key gone).

### Security Review — identity (Task 29b) — 2026-11-13

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | not run (no dependency change in the range) | | | |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 0 | 0 |

Elevation of privilege: A2-1 is closed (see the probe). Repudiation: Console sign-in success and refusal are now logged. Spoofing, tampering, information disclosure, denial of service: nothing new in the range. gitleaks passes in CI; the secrets grep was not repeated by hand (the range adds no literal that looks like a secret).

### Assumptions made

- Refusing an Operator session on non-API paths is wider than the text of AC-001.29; taken as accepted on the card's decision of 2026-11-13 (A3-1, open question 1).
- The developer's verify ran before the two commits were made; the only commit after them (`75281b55`) changes this review file alone, and CI ran both full suites on the same head, so the verify result is taken as current.
- The probe's 400 answers are counted as refused: no controller ran.

### Open questions for the user (1)

1. The wider Console rule (every request, not only `/api/**`): add a Changelog line to AC-001.29 through Change Mode now (recommended; it locks the code until the spec is approved again, so do it as its own small step), or leave it as a design note only?

### Test Report

Head `75281b55`, scratch copy outside the repo (`git archive HEAD backend/pom.xml backend/src`), one run: `mvn -o -Dtest='OperatorSessionTest,OperatorSignInTest,WebSessionFilterTest,OidcCallbackHandlerTest,AuthSecurityConfigTest,LogoutControllerTest,WorkspaceBindingTest' test`, exit 0, BUILD SUCCESS.

| Class | Tests | Failures |
|-------|-------|----------|
| `identity/session/OperatorSessionTest` | 18 | 0 |
| `identity/signin/OperatorSignInTest` | 38 | 0 |
| `identity/session/WebSessionFilterTest` | 35 | 0 |
| `identity/signin/OidcCallbackHandlerTest` | 22 | 0 |
| `identity/AuthSecurityConfigTest` | 14 | 0 |
| `identity/session/LogoutControllerTest` | 12 | 0 |
| `identity/session/WorkspaceBindingTest` | 9 | 0 |

Total 148, 0 failures, 0 errors, 0 skipped. The reviewer probe was one more class in the same run (1 test, in the scratch copy only; not in the repo). Spotless, the full unit suite and the ITs were not re-run: the developer's verify (`mvn spotless:check test` 1454 tests, 0 failures; `TwoInstanceCallbackIT,OpsRealmImportIT` 43 tests, 0 failures) and CI on this head cover them. Details: `task-29b-operator-session-test-report.md`.

Proposed status: `operator-session` → `passing` (not changed; the card does not ask for it).

## Attempt 2 (2026-11-13, head `01c7a7ae`) — verdict fail, kept for reference

Changes needed: 1 Critical, 0 Important, 4 Minor (new); attempt-1 findings 4 closed, 3 open. The full attempt-2 text (criteria, the merge of #211, the probe evidence, the open question on MSG07 against MSG135) is in git: `git show 75281b55:harnessclaude/artifacts/06-evaluation/identity/task-29b-operator-session-review.md`.

### Issues (attempt 2)

#### Critical

A2-1. `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java:202-203` — the Console rule tested the raw `request.getRequestURI()` with `startsWith("/api/")`; a percent-encoded letter (`/%61pi/solutions`, `/ap%69/solutions`) missed the prefix, the rule was skipped, and `SolutionController.list` ran for an Operator with no Workspace bound (200 `[]`). Fix: refuse every Operator request that is not on `ConsolePaths`, with encoded rows in `OperatorSessionTest` edge 141.

#### Minor

A2-2. `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java:224-241` — the Operator expiry rows did not assert that no `SessionEnded` was published.
A2-3. `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java` (missing row) — no test for an Operator session both past a limit and off the allow-list (401 MSG07).
A2-4. `backend/src/main/java/vn/tsolve/identity/signin/KeycloakClientRegistrations.java:50` — `userInfoUri` still set although the design says the dead userinfo pieces are removed.
A2-5. `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java:41-48` — the class Javadoc did not name the Operator order or the silent end of an Operator session.

The merge of #211 was judged sound in attempt 2: no `SessionEnded` for an Operator; order host, limits, allow-list, Console rule; an Operator session is never indexed, so the expiry sweep never sees it.

## Attempt 1 (2026-11-12, head `a1cbcddb`) — verdict fail, kept for reference

Changes needed: 1 Critical, 2 Important, 4 Minor. The full attempt-1 text (criteria tables, auth checks, the four flagged points) is in git: `git show 3a3b7aee:harnessclaude/artifacts/06-evaluation/identity/task-29b-operator-session-review.md`.

### Issues (attempt 1)

#### Critical

1. `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java:124-133` — an Operator session becomes an authenticated caller for every `/api/**` endpoint, and four controllers have no Role check, so they run for an Operator on `admin.` with no Workspace bound:
   - `knowledge/SolutionController.java:9-43` (`/api/solutions`, no `@PreAuthorize`): list, read, create, update and transition Solutions. With nothing bound the queries go to the application's own database (there is no fail-closed router in `main`), so the Operator reads and writes Workspace knowledge.
   - `chatbot/answer/controller/ChatbotAnswerController.java:30`, `chatbot/rating/controller/ChatbotRatingController.java:30,44`, `chatbot/source/controller/UnavailableDocumentsController.java:30` (`isAuthenticated()`): they run with a null `TSolveUser`; `askChatbot` does the embedding call and the Solution vector search before it fails at `ChatbotAnswerServiceImpl.java:157`.
   - This breaks AC-001.29 ("no Account, no Workspace and none of the five Roles") and the claim of edge 141.
   - Fix: refuse by default. For an Operator outcome in `WebSessionFilter`, answer 403 MSG08 unless the path is on a short Console list, and add rows to `OperatorSessionTest` edge 141 for `GET /api/solutions` and `POST /api/v1/chatbot/answers`.

#### Important

2. `backend/src/main/java/vn/tsolve/identity/AuthSecurityConfig.java:77,87-91` — the Workspace sign-in no longer calls userinfo; safe, but out of the task's scope, with no test and no design record, and it leaves dead pieces (`KeycloakClientRegistrations.java:50` `userInfoUri`, the `invalid_user_info_response` row in `OidcCallbackHandlerTest`, the fake's userinfo stub). Fix: keep it, add an assertion that neither callback sends a request to `/userinfo`, and record the decision in the design.
3. PR #212 title — the `pr-title` check fails ("subject must not be sentence-case"). Fix: rename.

#### Minor

4. `backend/src/main/java/vn/tsolve/identity/signin/OidcCallbackHandler.java:70-101` — two public constructors and an `ObjectProvider` fallback exist only for tests. Fix: one constructor taking `OperatorAllowList`.
5. `harnessclaude/artifacts/03-modules/identity/spec.md` AC-001.31 — the spec does not say what a missing, unknown or other-site saved request gives on `admin.` (code: MSG06). Fix: one spec line through Change Mode.
6. `backend/src/main/java/vn/tsolve/identity/signin/OidcCallbackHandler.java:211-214` — a refused (MSG135) and a successful Console sign-in write no log line. Fix: one INFO/WARN line each with the outcome only (no token, no cookie; the email only if NFR 3 allows it).
7. `backend/src/test/java/vn/tsolve/identity/signin/OperatorSignInTest.java:260` (edge 35) — `nullValues = "NONE"` is unused, and the NFR 3 log check runs only on the refused path. Fix: drop the attribute; add the `CapturedOutput` check to the edge 139 test.
