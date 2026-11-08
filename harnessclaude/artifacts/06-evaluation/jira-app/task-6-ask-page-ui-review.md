---
module: jira-app
feature: ask-page-ui
task: 6
verdict: pass
attempt: 1
date: 2026-11-08
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: jira-app — Task 6: Ask T-Solve page (whole of STORY-010, KAN-294)

Scope: one review covering STORY-010 end to end, by feature key: `ask-forge-spike` (Task 1, no code), `ask-contract` (Task 2), `ask-endpoint` (Task 3), `ask-forge-client` (Task 4), `ask-conversation-logic` (Task 5) and `ask-page-ui` (Task 6).
Branch `feat/KAN-294-jira-app-ask-tsolve`, range `a4818be..7f5620d`, repo `default`.
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md` (approved 3a593a33). Edge cases come from `harnessclaude/artifacts/04-plans/jira-app/design.md`.

## Summary

The story meets its spec. All 12 acceptance criteria have tests that name them, and every test I ran passes: 36 backend tests, 66 Forge Jest tests, ESLint, `forge lint`, spotless, and `generate:api` with no diff.

The page behaves as the spec describes:
- **AC-010.1 and AC-010.2:** the eligibility check runs first. An unlinked user sees MSG62 and a Create button with nothing prefilled.
- **AC-010.5:** the page validates the question before sending it.
- **AC-010.6 and AC-010.7:** answers are rendered with web-style source labels and no ticket id, link or Project (GBR-36).
- **AC-010.8:** after a failure the page shows MSG06, keeps the question, and keeps the Create button.
- **AC-010.9:** the Create dialog is prefilled using ADF, the label `tsolve-asked`, and a Summary cut to 255 code points.
- **AC-010.10:** "Đã giải quyết" is handled in the UI only.
- **AC-010.12:** nothing is logged or stored.

On the backend:
- **AC-010.11:** an unlinked caller gets 403 MSG62 before any AI call.
- **AC-010.5:** a bad question gets 400 MSG01 or MSG02.
- **AC-010.8:** a slow or failed answer gets 503 MSG06 within 18 s.

No CRITICAL finding, no failing test and no criterion without a test, so the verdict is **pass**.

One HIGH robustness issue remains in `ask-endpoint`: the request and the chatbot's AI calls share one thread pool, which can starve it (issue 1). It does not break any acceptance criterion, but it can take the web chatbot down under modest load. I recommend fixing it before the module is completed (see the judgment calls).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-010.1 to AC-010.12 are implemented as specified. Two edge-path deviations are LOW: issue 3 (a failed question is dropped from the Create dialog when a different question follows) and issue 4 (the server counts UTF-16 units, not characters). |
| Completeness | pass | Trace shows 12/12 criteria with tests. Both Exposed endpoints are in `tsolve-api.yaml` and match the controller. Two Definition of Done items are open: the dev-site check of AC-010.6, .7, .10 and .11 was skipped by the user's choice (design, "Dev-site check" section), and agreeing the chatbot call with its owner cannot be verified from the code. |
| Correctness | pass | Every test passes (see Test Report). The design's edge cases 1 to 8, 10 and 11 have tests. Edge case 9 was closed by the spike. |
| Integration | pass | `ChatbotAnswerResponse` is returned unchanged and `ChatbotQuestion` is reused, so the generated client has no diff. The FIT chain and `ROLE_JIRA_APP` guard both endpoints, and tests reject a call without a FIT. Issue 1 is a shared-resource risk for the `chatbot` module. |

## Traceability

```
$ bun harnessclaude/hooks/sdd.ts trace jira-app STORY-010
  ✓ AC-010.1  AskTsolvePage.test.jsx
  ✓ AC-010.2  AskTsolvePage.test.jsx, prefill.test.js, tsolveClient.test.js, JiraAppQuestionServiceImplTest.java, JiraAppQuestionControllerTest.java
  ✓ AC-010.3  AskTsolvePage.test.jsx, conversation.test.js, tsolveClient.test.js, JiraAppQuestionControllerTest.java
  ✓ AC-010.4  tsolveClient.test.js, JiraAppQuestionServiceImplTest.java, JiraAppQuestionControllerTest.java
  ✓ AC-010.5  messages.test.js, AskTsolvePage.test.jsx, validation.test.js, tsolveClient.test.js, JiraAppQuestionControllerTest.java
  ✓ AC-010.6  AskTsolvePage.test.jsx, conversation.test.js, prefill.test.js
  ✓ AC-010.7  messages.test.js, AskTsolvePage.test.jsx, conversation.test.js, JiraAppQuestionControllerTest.java
  ✓ AC-010.8  messages.test.js, AskTsolvePage.test.jsx, conversation.test.js, tsolveClient.test.js, JiraAppQuestionServiceImplTest.java, JiraAppQuestionControllerTest.java
  ✓ AC-010.9  AskTsolvePage.test.jsx, conversation.test.js, prefill.test.js
  ✓ AC-010.10 AskTsolvePage.test.jsx, conversation.test.js
  ✓ AC-010.11 messages.test.js, AskTsolvePage.test.jsx, tsolveClient.test.js, ConfigJiraAskerLookupTest.java, JiraAppQuestionServiceImplTest.java, JiraAppQuestionControllerTest.java
  ✓ AC-010.12 conversation.test.js, tsolveClient.test.js
trace jira-app (STORY-010): 12/12 acceptance criteria have a test
```

## Issues

### ask-endpoint (Task 3)

1. **HIGH — the request and the chatbot's AI calls share one thread pool, which can starve it.**
   - **Where:** `backend/src/main/java/vn/tsolve/jiraapp/service/impl/JiraAppQuestionServiceImpl.java:44` and `:73`, together with `backend/src/main/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImpl.java:54` and `:163`.
   - **Actual:** the whole `ChatbotAnswerService.ask` call runs on `applicationTaskExecutor`. Inside it, the chatbot submits its embed and chat calls to the same executor. No `spring.task.execution` setting exists in `application.yml`, so Boot's default applies: 8 core threads and an unbounded queue, which means the pool never grows past 8. With 8 Jira questions in flight, every pool thread is blocked in the chatbot's `join()`, and their inner AI tasks wait in the queue behind them. The Jira callers get 503 MSG06 at 18 s, as the spec requires. But the worker threads stay blocked until the chatbot's own 60 s budget (`CHATBOT_ASK_TIMEOUT`) expires.
   - **Impact:** during that time, every web Knowledge Chatbot question (which uses the same pool for its AI calls) also fails with MSG06. The queued AI calls still run later, which wastes Ollama capacity. A slow Ollama and users who retry reach the 8-thread limit at much lower concurrency, because each abandoned call keeps holding a thread.
   - **Expected:** a Jira App deadline does not tie up the chatbot's executor, and a late call is not left running against shared capacity.
   - **Suggested fix:** use a dedicated bounded executor for the Jira App outer call, separate from `applicationTaskExecutor`. Give it a small pool and a `SynchronousQueue` or short queue that rejects to 503 MSG06 (this matches the existing `RejectedExecutionException` branch). Alternatively, agree with the chatbot owner to take a deadline parameter, so the call stays on the request thread. Add a test that runs more concurrent asks than pool threads and checks that the web chatbot's executor is still usable.

2. **MEDIUM — database access moves off the request thread, against the chatbot's stated rule.**
   - **Where:** `JiraAppQuestionServiceImpl.java:69-75`, against `ChatbotAnswerServiceImpl.java:108` ("Keep database access on the request thread so it retains the request's Workspace database").
   - **Actual:** `solutions.nearest`, `wiki.nearest` and `answers.save` now run on a pool thread. This works today only because no per-thread Workspace routing is active for FIT calls; the code comment at `:71` acknowledges this. When Workspace binding arrives (`TenantScope` is a `ThreadLocal`, see `identity/signin/port/LocalTenantScope.java:12`), these calls will fail ("no Workspace is bound") or reach the wrong database.
   - **Suggested fix:** this is resolved by the same change as issue 1 (keep the call on the request thread with a passed deadline). Otherwise, add a test or a `TODO` linked to the Workspace-binding story, so this cannot be missed.

3. **LOW — the server counts length differently from the page and the spec.**
   - **Where:** `backend/src/main/java/vn/tsolve/jiraapp/dto/JiraAppQuestionRequest.java:13`.
   - **Expected:** AC-010.5 says "longer than 1,000 characters", and the page counts code points (`validation.js:13`).
   - **Actual:** `@Size(max = 1000)` counts UTF-16 units. A question of 600 emoji passes the page check, then gets a server 400 MSG02. The design accepts this (edge case 4); it is listed here because the spec, not the design, is the contract. The web chatbot behaves the same way.
   - **Suggested fix:** use a code-point length constraint on both DTOs, or reword AC-010.5 to say "UTF-16 units". This is a spec decision for the user.

4. **LOW — no limits on the turns inside `history`.**
   - **Where:** `JiraAppQuestionRequest.java:14` (`ChatbotTurnRequest` at `chatbot/answer/dto/ChatbotTurnRequest.java:3` has no constraints, and the list has no `@Valid`).
   - **Actual:** a direct FIT caller can send 10 turns of any length. This is harmless today, because the chatbot does not use the history yet (`ChatbotAnswerServiceImpl.java:103`). It will matter when the chatbot's Task 10 sends history to the LLM.
   - **Suggested fix:** raise this with the chatbot owner, and add `@Valid` plus size limits on the turn fields.

5. **INFO — every asker gets only the Employee role.**
   - **Where:** `backend/src/main/java/vn/tsolve/identity/account/service/impl/AccountLookupServiceImpl.java:31` builds the user with roles `Set.of()`.
   - **Impact:** AC-010.4 says "every Solution and Wiki document the user may see". Once Wiki access depends on roles, a Manager asking from Jira could see fewer sources than on the web. This is design-acknowledged (the stub keeps Employee only until identity provides roles), and Wiki sources are `NoWikiSources` today.

6. **INFO — the stub ignores the site.**
   - **Where:** `ConfigJiraAskerLookup.java:31-33` ignores `cloudId`.
   - This is documented as a single-site stub, and Atlassian accountIds are global. Remove the stub when identity's `JiraAccountLinkLookup` arrives.

### ask-contract (Task 2) and ask-endpoint (Task 3)

7. **INFO — the old page name "Hỏi T-Solve" remains after the rename.**
   - **Where:** `frontend/openapi/tsolve-api.yaml:828` and `:849`, `JiraAppQuestionController.java:42`, `JiraAppQuestionService.java:8`, `JiraAskerLookup.java:7`, `.env.example:63`.
   - **Expected:** the spec changelog of 2026-11-08 renamed the page to "Ask T-Solve" and says the Exposed table follows.
   - **Impact:** none for users. The text appears only in API docs and comments.
   - **Suggested fix:** rename it in the next change that touches these files. Changing the OpenAPI summary means running `generate:api` again.

### ask-conversation-logic (Task 5)

8. **LOW — a failed question is lost when a different question follows it.**
   - **Where:** `jira-app/TSolve_JiraApp/src/frontend/askTsolve/conversation.js:217` (`send` overwrites `pending`) and `:252-255` (`questions()` adds only the current `pending`).
   - **Scenario:** Q1 fails (MSG06), the user edits the box and sends Q2, and Q2 is answered.
   - **Expected (AC-010.9):** "every question of the conversation", with Summary = "the first question". Q1 was asked on the page.
   - **Actual:** Q1 is dropped. The Description lists only Q2, and Summary = Q2. The test `AC-010.9 lists every question asked, the failed one too` covers only the last question when it is the one that failed.
   - **Suggested fix:** keep failed questions as their own list (or as turns with no answer, left out of `history()`), and add a test for this sequence. This depends on judgment call 2 below.

### ask-page-ui (Task 6)

9. **LOW — the bug log names a test that no longer exists.**
   - **Where:** `docs/bug-log.md:177` (BUG-182) names the test `AC-010.1 titles the project page "Hỏi T-Solve" through the manifest, once (BUG-182)`.
   - **Actual:** the test is now `AC-010.1 titles the project page "Ask T-Solve" through the manifest, once (BUG-182)` (`AskTsolvePage.test.jsx:65`). The row also still describes the title as "Hỏi T-Solve".
   - **Suggested fix:** update the test name in the row. You can keep the original symptom wording, with a note about the rename.

### Process (no feature key)

10. **INFO — the story changes code in the `identity` module.**
    - **Where:** `backend/src/main/java/vn/tsolve/identity/account/service/AccountLookupService.java` and its implementation.
    - These files are outside the jira-app module's `paths` (`harnessclaude/artifacts/03-modules/jira-app/module.md`). It is a small read-only API, but it is a change to another module, and the identity owner should know about it.

11. **INFO — the features were marked passing before this review.** `harnessclaude/progress.json:243-248` already shows all six `ask-*` features as `passing`, set by the implementer's own "pass" commits (for example `7f5620d`). I made no status change, as the dispatcher asked.

12. **INFO — Definition of Done items not verifiable here.**
    - The dev-site check of AC-010.6, .7, .10 and .11 (the problem+json 403 path through Forge) was skipped by the user's choice (design, "Dev-site check" section).
    - Agreeing the chatbot call with Trần Hải Anh cannot be verified from the code.

### What was checked and found sound
- **Client (`tsolveClient.js`):**
  - A 20 s `Promise.race` with the timer cleared.
  - Only 400 MSG01/MSG02 and 403 MSG62 map to their own codes; everything else maps to MSG06.
  - `history.slice(-10)`.
  - No `console` calls.
- **Page:**
  - The send button is disabled while waiting (edge case 6).
  - If the eligibility check fails, the box is shown (edge case 11).
  - A 403 MSG62 on the POST switches the page to the unlinked view.
  - An unlinked user's Create dialog is prefilled with nothing but the project.
  - After "solved", the conversation is cleared.
- **Description:** the ADF has no empty text nodes. The Summary is cut by code points.
- **Backend:**
  - The lookup runs before any executor or AI use (edge cases 1 and 2).
  - A missing or blank principal counts as not linked.
  - A history longer than 10 gets 400 MSG02.
  - Logs carry only the error type (Non-Functional 3).
  - A malformed `TSOLVE_STUB_JIRA_ASKERS` stops start-up.
- **Messages:** the MSG01, MSG02, MSG06, MSG62 and MSG109 texts match `frontend/src/shared/messages/catalog.ts` word for word.
- **Manifest:**
  - The `jira:projectPage` title is "Ask T-Solve".
  - No new scopes (only `read:jira-work`).
  - The UI endpoint has no fixed route, so the client sets the path.

## Assumptions made / open judgment calls

1. **Issue 1 (HIGH, not security, no criterion broken) does not block.** The evaluate rules fail only on CRITICAL code findings or Critical/High security findings, so the verdict is pass. The user should decide whether issue 1 must be fixed before the module is completed. I recommend fixing it, because it affects the web chatbot.
2. **"Every question of the conversation" (AC-010.9).** I read it as including a failed question that was followed by a different one (issue 8). If the user means only answered questions plus the last failed one, issue 8 is not a defect.
3. **"Characters" in AC-010.5.** I read it as user-visible characters (code points), as the page does, so the server's UTF-16 count is a LOW deviation (issue 3) rather than a pass.
4. **The "Ask T-Solve" rename applies to the page title only (AC-010.1).** I treated the old name left in the API summary and comments as INFO, not as a breach of the spec.
5. **Environment limits.** The full `mvn verify` (WireMock and Keycloak sockets, Docker) and the web `npm run lint`, `test` and `build` were not run. I judged the targeted suites, the typecheck and the zero-diff client generation sufficient for this story.

## Test Report

See `harnessclaude/artifacts/06-evaluation/jira-app/task-6-ask-page-ui-test-report.md`.

| Check | Result |
|-------|--------|
| Backend tests: JiraAppQuestionControllerTest 13, JiraAppQuestionServiceImplTest 10, ConfigJiraAskerLookupTest 9, AccountLookupServiceImplTest 4 | 36/36 pass |
| `mvn spotless:check` | pass |
| Forge `npx jest` | 6 suites, 66/66 pass |
| Forge `npm run lint` (ESLint) | pass |
| `forge lint` | pass ("No issues found.") |
| `frontend` `npm run generate:api` | no diff |
| `frontend` `npm run typecheck`, `format:check` | pass |
| Full `mvn verify` | not run (environment: sockets and Docker) |
| Coverage | not measured; trace shows 12/12 criteria with tests |
