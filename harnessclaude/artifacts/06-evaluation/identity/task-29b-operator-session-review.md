---
module: identity
feature: operator-session
task: 29b
verdict: pass
attempt: 4
date: 2026-11-13
reviewer: tsolve-code-reviewer (Fable 5.2, medium)
iteration: 1
---

# Review: identity — Task 29b: Console callback and the Operator session

PR #212, branch `feat/KAN-233-identity-operator-session`, head `fa3c7a5f` (clean tree, equal to HEAD). Attempt 4 covers only the merge of main (KAN-314, #214): merge commit `85fa2676` against its first parent, and `fa3c7a5f` (the approval record). CI on this head (`gh pr checks 212`): backend, backend-tests unit, it-keycloak-a, it-keycloak-b, it-other, gitleaks and pr-title pass; the frontend jobs are skipped (no frontend change).

## Attempt 4 (2026-11-13) — the merge of main (KAN-314, #214) into PR #212

### Summary

Approve: 0 Critical, 0 Important, 2 Minor (new). Earlier findings are out of scope (approved at attempt 3 on `75281b55`).

The merge keeps both behaviours: the old session is ended on both callback paths before the new one is created, and the `LOGOUT` event depends only on the kind of the old session, never on which callback runs.

### Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.3: a live Workspace session replaced at the callback publishes one `LOGOUT` with its own ids; AC-002.4: a past-limit one is left to the sweep; AC-001.29 and AC-001.31 are not changed by the merge |
| Completeness | pass | No MISSING, no EXTRA |
| Correctness | pass | Targeted run 108/108 at this head; CI passes; no Critical finding |
| Integration | pass | One public constructor (9 parameters); Spring injects `ApplicationEventPublisher`; no caller of the removed constructors is left |

### The card's questions

- **(a) `replaceOld` on both paths.** `OidcCallbackHandler.java:245-267`: the event is published only when the old session has `kind == WORKSPACE` and both ids, and `end` claimed it. The kind of the new sign-in is not read. So a Workspace callback never skips the event because of the Operator branch, and an Operator session (old or new) never produces one: it has no ids and no index member (`WebSessionStore.java:202-206`). One case stays: A4-2.
- **(b) Fixation.** `OidcCallbackHandler.java:211-216`: `replaceOld(old)` runs before `sessions.create`, and the cookie is set at line 233. `startSession` is shared, so this holds on both paths. Both branches of `replaceOld` delete the old key (`deleteKeyOnly` or `end`). A Redis failure gives MSG06 and no new session, as before the merge.
- **(c) Constructor and wiring.** One constructor at `OidcCallbackHandler.java:71`, so no `@Autowired` is needed. The only two `new OidcCallbackHandler(` calls are `CallbackAccountChecksTest.java:84` and `OidcCallbackHandlerTest.java:67`, both with `event -> {}`. `SessionReplacedEventTest` and `OperatorSignInTest` use the Spring bean and pass, so the publisher is wired.
- **(d) design.md and tasks.md.** Row 141: main's version was the older text ("High", "the endpoint's own Role check answers 403 MSG08"). Ours replaces it and keeps Q7 and AC-001.29, so nothing of main's row is lost. Main's rows 142 to 147 are all present. tasks.md holds Task 27c in full and the new dependency sentence of Task 29b. No conflict markers in spec, design, tasks, progress or the bug log.
- **(e) Approval.** The merge commit itself carried main's approval hash, which did not match the merged spec; `fa3c7a5f` fixes it. `sdd.ts diff identity`: "the spec is unchanged since it was approved (2026-11-12T18:30:09.009Z)". The approved text holds the AC-001.29, AC-001.31 and AC-002.3 wording of both sides.

### New issues

#### Critical

None.

#### Important

None.

#### Minor

A4-1. `backend/src/test/java/vn/tsolve/identity/signin/OperatorSignInTest.java:359` — the Console callback with an old Operator cookie checks that the old key is gone, but not that no `SessionEnded` is published. The only "Operator publishes nothing" test (`SessionReplacedEventTest.java:181`) goes through the Workspace callback. Fix: import `SessionEndedRecorder` in `OperatorSignInTest` and assert that it stays empty.
A4-2. `backend/src/main/java/vn/tsolve/identity/signin/OidcCallbackHandler.java:252` — a Console callback whose request carries the cookie value of a live Workspace session ends that session and publishes its `LOGOUT`. A browser never sends this (`__Host-` cookie, host-only); it needs a hand-made request by an allow-listed Operator who also holds that cookie value. The event is true (the Workspace session did end, AC-002.3), so the code needs no change; no test covers the case. Fix: none in code; one test or one design row if the user wants it pinned (open question 1).

### Traceability

`bun harnessclaude/hooks/sdd.ts trace identity STORY-002`, run at this head (the criteria #214 touched):

```
✓ AC-002.3  signin/SessionReplacedEventTest.java, session/LogoutControllerTest.java, session/SessionEndedEventTest.java, session/SessionExpiryPublisherIT.java
✓ AC-002.4  signin/SessionReplacedEventTest.java, session/SessionExpiryPublisherTest.java, session/SessionExpiryPublisherIT.java
```

`SessionReplacedEventTest` was read: it checks the old session's own ids, the session id as a hash, and that the key is gone at publish.

### Security Review — identity (the merge) — 2026-11-13

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Secrets | 0 | 0 | 0 | 0 |
| Dependencies | not run (no dependency change in the merge) | | | |
| Code | 0 | 0 | 0 | 0 |
| STRIDE | 0 | 0 | 0 | 0 |

Spoofing: a new session id for every sign-in on both paths; the old one is ended first. Repudiation and information disclosure: the new log lines hold a class name only, with no id, hash, email or token. Elevation of privilege: an Operator session still has no ids, so it has no index member and no event. gitleaks passes in CI.

### Assumptions made

- The event belongs to the session that ended, not to the sign-in that ended it. So A4-2 follows AC-002.3 and does not go against the decision of 2026-11-13, which names an Operator sign-in and an old Operator session.
- The developer's verify and CI ran on `fa3c7a5f`; the tree is unchanged since, so both results are taken as current.

### Open questions for the user (1)

1. A4-2: is a `LOGOUT` for a Workspace session ended by a Console callback (hand-made request only) the wanted behaviour? Recommended: yes, keep the code; optionally one test.

### Test Report

Head `fa3c7a5f`, one run, log outside the repo: `mvn -o -Dtest='SessionReplacedEventTest,OidcCallbackHandlerTest,CallbackAccountChecksTest,OperatorSignInTest,SessionExpiryPublisherTest' test`, exit 0, BUILD SUCCESS.

| Class | Tests | Failures |
|-------|-------|----------|
| `identity/signin/SessionReplacedEventTest` | 8 | 0 |
| `identity/signin/OidcCallbackHandlerTest` | 22 | 0 |
| `identity/signin/CallbackAccountChecksTest` | 38 | 0 |
| `identity/signin/OperatorSignInTest` | 38 | 0 |
| `identity/session/SessionExpiryPublisherTest` | 2 | 0 |

Total 108, 0 failures, 0 errors, 0 skipped. The full verify was not re-run: the developer's verify (Spotless and 9 unit classes, 0 failures) and CI on this head cover it.

Proposed status: `operator-session` → `passing` (not changed; the card does not ask for it).

## Attempt 3 (2026-11-13, head `75281b55`) — verdict pass, kept for reference

Approve: 0 Critical, 0 Important, 2 Minor (new); attempt-1 finding 7 (Minor) still open. The full attempt-3 text (criteria, the path probe tables, traceability, security review, test report 148/148) is in git: `git show fa3c7a5f:harnessclaude/artifacts/06-evaluation/identity/task-29b-operator-session-review.md`.

### Issues (attempt 3)

#### Minor

A3-1. `backend/src/main/java/vn/tsolve/identity/session/ConsolePaths.java:6-7` and spec.md AC-001.29 — both said the rule covers "`/api/**` requests", while the code refuses every request of an Operator session that is off the list. Fix: correct the Javadoc; add one Changelog line to the spec (Change Mode) or a design note.
A3-2. `backend/src/test/java/vn/tsolve/identity/session/OperatorSessionTest.java` (missing rows) — nothing pins that the logout entry is matched exactly or that a non-API path is refused. Fix: three or four rows in the edge 141 parameterized test.

Open from attempt 1: finding 7 (`OperatorSignInTest.java:302,378`: unused `nullValues = "NONE"`; the NFR 3 log check runs only on the refused path).

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
