---
module: identity
feature: operator-session
task: 29b
verdict: fail
attempt: 2
date: 2026-11-13
reviewer: tsolve-code-reviewer (Fable 5.2, medium)
iteration: 1
---

# Review: identity — Task 29b: Console callback and the Operator session

PR #212, branch `feat/KAN-233-identity-operator-session`, range `origin/main...HEAD` (head `01c7a7ae`, clean tree, equal to the pushed head; `origin/main` = `db676c67`, #211, is merged in). CI on this head: backend unit, it-keycloak-a, it-keycloak-b, it-other, gitleaks and pr-title pass.

## Attempt 2 (2026-11-13)

### Summary

Changes needed: 1 Critical, 0 Important, 4 Minor (new). Attempt-1 findings: 4 closed, 3 open (1 Critical, 2 Minor).

The Console rule (decision A) is in place and the plain paths are refused, but it can be walked around with a percent-encoded path, so the attempt-1 Critical is not closed. Everything else asked for is done, and the merge of #211 is sound.

### Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | AC-001.29 "403 MSG08 to an Operator session on every `/api/**` request on `admin.` outside the Console list" does not hold for `/%61pi/solutions` (issue A2-1) |
| Completeness | pass | Every criterion of the task has code and a test; no MISSING, no new EXTRA |
| Correctness | fail | Targeted tests 187/187 pass; CI passes; 1 Critical finding |
| Integration | fail | Same as attempt 1: `/api/solutions` still runs for an Operator with nothing bound, through the encoded path |

### Attempt-1 findings

| # | Finding | Status | Evidence |
|---|---------|--------|----------|
| 1 | Critical: Operator reaches endpoints with no Role check | **open** | The rule exists (`WebSessionFilter.java:201-206`, `ConsolePaths.java`, `SessionConfig.java:51-54`) and `/api/solutions`, `/api/v1/chatbot/answers`, `/api/v1/users` answer 403 MSG08 with tests. Bypass: issue A2-1 |
| 2 | Important: ID-token-only Workspace sign-in without test or design note | closed | `OperatorSignInTest.java:552-570` ("no callback requests /userinfo", both callbacks); design.md note (2). One leftover: issue A2-4 |
| 3 | Important: `pr-title` check fails | closed | `gh pr checks`: pr-title SUCCESS; title "feat(identity): console callback and the Operator session (KAN-313)" |
| 4 | Minor: two constructors of `OidcCallbackHandler` | closed | One 8-argument constructor (`OidcCallbackHandler.java:68-86`); `AuthSecurityConfigTest` imports the bean |
| 5 | Minor: AC-001.31 silent on a bad saved request on `admin.` | closed | spec.md AC-001.31 and Changelog 2026-11-12; `OperatorSignInTest.java:501` (edge 122 / 140 rows); the spec is approved on this text (`01c7a7ae`) |
| 6 | Minor: no log line for a refused (MSG135) or a successful Console sign-in | **open** | `OidcCallbackHandler.java:171-180`: still only the "no email claim" ERROR line |
| 7 | Minor: unused `nullValues = "NONE"`; NFR 3 log check only on the refused path | **open** | `OperatorSignInTest.java:378` still has the attribute (attempt 1 called it unused; not re-checked row by row); the edge 139 test has no `CapturedOutput` check |

### New issues

#### Critical

A2-1. `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java:202-203` — the Console rule tests the raw `request.getRequestURI()` with `startsWith("/api/")`. A percent-encoded letter makes the raw path miss the prefix, the rule is skipped, and the request goes on as an authenticated Operator; Spring Security (`/api/**` authenticated) and Spring MVC both match the decoded path, so the controller runs with no Workspace bound. `StrictHttpFirewall` does not refuse an encoded letter.
   - Evidence (reviewer probe in a scratch copy of the head, the whole application through MockMvc, an allow-listed Operator session on `admin.localhost`):
     - `GET /api/solutions` → 403 MSG08 (the rule)
     - `GET /%61pi/solutions` → **200 `[]`** (`SolutionController.list` ran)
     - `GET /ap%69/solutions` → **200 `[]`**
     - `GET /%61pi/v1/chatbot/unavailable-documents` → 400 from the controller layer (reached, not refused by the filter)
     - `GET /%61pi/v1/users` → 403 MSG08 from the endpoint's own Role check, not from the filter
     - `GET /API/solutions` → 403 (not `/api/**`, `denyAll`): case is not a way around
   - MockMvc keeps the raw path in `getRequestURI()` as a servlet container does; the probe was not run against a live Tomcat. The same route gives `POST`/`PUT` on `/api/solutions` (with a CSRF token).
   - Fix: make the rule fail closed and independent of the path's spelling: for an Operator session refuse every request that is not on `ConsolePaths` (drop the `startsWith("/api/")` test), or test the decoded path that Spring matches on. Add rows to `OperatorSessionTest` edge 141 with `get(URI.create(".../%61pi/solutions"))` and `/ap%69/solutions` (403 MSG08, `scope.runIn` never called). A `String` URL template would encode the `%`; the test must pass a `URI`.

#### Minor

A2-2. `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java:224-241` — card point (a): the Operator expiry rows (61 minutes idle, background only, 8 hours) do not assert that no `SessionEnded` was published; only the MSG135 row (line 205) and the logout (line 338) do. `SessionEnded` accepts null ids, so dropping the `kind != OPERATOR` guard at `WebSessionFilter.java:251` for the EXPIRED path would fail no assertion in this class (read from the code, not run as a mutation). Fix: add `assertThat(SessionEndedRecorder.all()).isEmpty()` to the three expiry rows.
A2-3. `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java` (missing row) — card point (b): no test pins the answer for an Operator session that is both past a limit and off the allow-list (code: 401 MSG07, `WebSessionFilter.java:189-200`). Fix: one row with `FORMER` and the clock moved 61 minutes: 401 MSG07, key gone, no event.
A2-4. `backend/src/main/java/vn/tsolve/identity/signin/KeycloakClientRegistrations.java:50` — `userInfoUri` is still set although design.md (Task 29b review fixes, point 2) says the dead userinfo pieces "are removed with it". Fix: remove the line, or correct the design sentence.
A2-5. `backend/src/main/java/vn/tsolve/identity/session/WebSessionFilter.java:41-48` — the class Javadoc describes only the Workspace order; it does not name the Operator order (host, limits, allow-list, Console list) or that an Operator session ends without an event. Fix: two lines in the Javadoc.

### The merge of #211 (card points a, b, c)

- **(a) No `SessionEnded` for an Operator.** Correct. `WebSessionFilter.end` publishes only when `claimed && kind != OPERATOR` (line 251), for both EXPIRED and the MSG135 end; `LogoutController.java:58` publishes only for `WORKSPACE`. AC-002.3 and AC-002.4 speak of Workspace sessions only, so no event is the spec's behaviour. Pinned for MSG135 and logout; not for expiry (A2-2).
- **(b) Order host, limits, allow-list, Console rule; expired and off the list gives 401 MSG07.** This agrees with the spec and with 29-QA Q6. AC-001.34 applies AC-001.23 (ended, MSG07) to an Operator session; AC-001.33 does not rank itself against it; the session is ended in both readings. Q6 (as the task text states it) fixes only that the host check is first (MSG123, session kept) and that a removed email ends with MSG135 on the next `admin.` request, which holds for a session that is still alive. Limits-first is also the only order that can be kept in production: the Redis key's time to live is the same limit, so an expired session is normally gone before the filter can read its email, and the answer is MSG07 whatever the code order. Not pinned by a test (A2-3).
- **(c) The sweep.** An Operator session is never indexed: `WebSessionStore.memberOf` returns empty text without a Workspace and Account id, so `create` and `touch` skip the `ZADD` and `end` only deletes the key. The sweep reads only the index, so it never sees an Operator key and cannot crash on one; the key leaves by its Redis time to live (60 minutes idle, cut at 8 hours). Pinned by `SessionExpiryPublisherIT` "edge 6 an OPERATOR session gets no index entry and the sweep publishes nothing for it" (CI it-other passes on this head; not re-run here).
- Conflict resolution in `WebSessionFilter` and `SessionConfig`: both sides' arguments kept; on the Workspace path the event is still published after the claim, with the reason; no null id can reach `SessionEnded`. `approvals.json`: the identity entry is re-approved on the merged spec (`01c7a7ae`).
- The Console rule sits after the allow-list re-check and before the touch, so a refused request does not restart the idle limit. Accepted.

### Traceability

`bun harnessclaude/hooks/sdd.ts trace identity STORY-001` / `STORY-002` (rows of this task):

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

The AC-001.29 to .34 rows are carried over from attempt 1 (this run's output was cut before them; the test files still name them); AC-002.1 and .2 are from this run. AC-001.29's new 403 clause is checked only for plain paths (A2-1).

### Security Review — identity (Task 29b) — 2026-11-13

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | not run (no dependency change in the diff) | | | |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 1 (A2-1, elevation of privilege) | 0 | 0 | 1 (attempt-1 issue 6, repudiation) |

Spoofing, tampering, information disclosure and denial of service: nothing new in the fixes or the merge. gitleaks passes in CI. The secrets grep was not repeated by hand.

### Assumptions made

- `/%61pi/solutions` is an `/api/**` request in the sense of AC-001.29, because Spring Security and Spring MVC both treat it as one.
- MSG07 for an Operator session that is both expired and off the allow-list is the intended answer (see (b)); the spec does not rank AC-001.33 against AC-001.34. Open question 1.
- A refused Operator request not restarting the idle limit is acceptable (no criterion covers it).
- The ITs (`SessionExpiryPublisherIT`, `WebSessionStoreIT`, `TwoInstanceCallbackIT`, `OpsRealmImportIT`) were not re-run locally; CI ran them on this head.

### Open questions for the user (1)

1. Expired and off the allow-list at once: keep 401 MSG07 (recommended; the Redis time to live makes it the only answer that can be kept) and pin it with a test, or put the allow-list first so the answer is MSG135 while the key still exists?

### Test Report

Head `01c7a7ae`, run in a scratch copy of the head outside the repo (`git archive HEAD backend/pom.xml backend/src`), one run: `mvn -q -Dtest='OperatorSessionTest,OperatorSignInTest,WebSessionFilterTest,WorkspaceBindingTest,AuthSecurityConfigTest,OidcCallbackHandlerTest,CallbackAccountChecksTest,LogoutControllerTest,SessionEndedEventTest,SessionExpiryPublisherTest' test`, exit 0 (Docker 29.2.1).

| Class | Tests | Failures |
|-------|-------|----------|
| `identity/session/OperatorSessionTest` | 14 | 0 |
| `identity/signin/OperatorSignInTest` | 38 | 0 |
| `identity/session/WebSessionFilterTest` | 35 | 0 |
| `identity/session/WorkspaceBindingTest` | 9 | 0 |
| `identity/AuthSecurityConfigTest` | 14 | 0 |
| `identity/signin/OidcCallbackHandlerTest` | 22 | 0 |
| `identity/signin/CallbackAccountChecksTest` | 38 | 0 |
| `identity/session/LogoutControllerTest` | 12 | 0 |
| `identity/session/SessionEndedEventTest` | 4 | 0 |
| `identity/session/SessionExpiryPublisherTest` | 1 | 0 |

Total 187, 0 failures, 0 skipped. The reviewer probe for A2-1 was one more class in the same run, in the scratch copy only; it is not in the repo. Spotless, the full unit suite and the ITs were not re-run: the developer's verify and CI on this head cover them. `task-29b-operator-session-test-report.md` still describes attempt 1 (head `a1cbcddb`); the card limits this attempt's writing to this file.

Proposed status: `operator-session` → `fixing` (not changed; the card does not ask for it).

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
