---
module: jira-app
date: 2026-11-08
total_tasks: 6
owner: Dev
---

# Tasks: jira-app — iteration 1 (KAN-294)

Spec: `artifacts/03-modules/jira-app/spec.md` · Design: `artifacts/04-plans/jira-app/iteration-1/design.md`
(master: `artifacts/04-plans/jira-app/design.md`). Branch `feat/KAN-294-jira-app-ask-tsolve`.
Backend tests name their AC id (`refusesUnlinkedAsker_AC_010_11`); Forge tests start with it
(`it('AC-010.5 shows MSG01 for a blank question', …)`). Each task ends with `mvn spotless:apply` + `mvn verify`
(backend), and `npm test` + `forge lint` in `jira-app/TSolve_JiraApp` (Forge).

## Task 1: Spike — project page and prefilled Create dialog

**Story:** STORY-010
**Feature:** ask-forge-spike
**Description:** On the dev site (`forge tunnel`), add a throw-away `jira:projectPage` that opens
`CreateIssueModal` with summary, a multi-line description and `labels: ['tsolve-asked']`. Record in RESEARCH-004
(Open Questions): where the page shows in the new navigation; whether labels prefill; plain text vs ADF for the
description; whether `onClose` returns the created issue key; scopes needed. If labels cannot be prefilled, stop
and raise a spec change before Task 6.
**Files:**
- Modify: `harnessclaude/artifacts/01-research/RESEARCH-004-ask-ai-before-creating-a-jira-issue.md`
**Dependencies:** none
**Estimated size:** small

---

## Task 2: Contract

**Story:** STORY-010
**Feature:** ask-contract
**Description:** Add `POST /api/v1/jira-app/questions` (request: question + history ≤ 10; responses 200
`ChatbotAnswerResponse`, 400 MSG01/MSG02/MSG06, 401 MSG08, 403 MSG62, 503 MSG06) and
`GET /api/v1/jira-app/questions/eligibility` (200 `{linked}`) to the OpenAPI file, tag `jira-app`; run
`npm run generate:api`. Add `TSOLVE_STUB_JIRA_ASKERS` and `JIRA_APP_ASK_TIMEOUT` to `.env.example`.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (generated), `.env.example`
**Dependencies:** none
**Estimated size:** small

---

## Task 3: Backend — asker lookup and question endpoint

**Story:** STORY-010
**Feature:** ask-endpoint
**Description:** `JiraAskerLookup` + `ConfigJiraAskerLookup` (stub: accountId → active Account → `TSolveUser`,
EMPLOYEE only). `JiraAppQuestionService.ask(principal, request)`: not linked / no principal → `JiraAskerNotLinkedException`
(403 MSG62) before any AI call; else `ChatbotAnswerService.ask` under the `JIRA_APP_ASK_TIMEOUT` (18s) deadline →
`AiUnavailableException` / timeout → 503 MSG06. Controller: POST + eligibility GET, `@PreAuthorize` JIRA_APP,
`@Valid` (MSG01/MSG02, history ≤ 10). Logs: ids, outcome, error type only.
Tests: AC-010.4 (returns the chatbot answer for the mapped user), AC-010.5 (400 codes server-side), AC-010.8
(503 MSG06 on timeout / AI failure), AC-010.11 (403 MSG62, AI never called; no principal; inactive Account),
history > 10 → 400, eligibility true/false.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/jiraapp/{controller/JiraAppQuestionController,service/JiraAppQuestionService,service/JiraAskerLookup,service/impl/JiraAppQuestionServiceImpl,service/impl/ConfigJiraAskerLookup,dto/JiraAppQuestionRequest,dto/JiraAskerEligibility,exception/JiraAskerNotLinkedException}.java`
- Modify: the global exception handler (MSG62 → 403)
- Test: `backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppQuestionControllerTest.java`, `service/impl/JiraAppQuestionServiceImplTest.java`, `service/impl/ConfigJiraAskerLookupTest.java`
**Dependencies:** Task 2
**Estimated size:** medium

---

## Task 4: Forge test setup and server client

**Story:** STORY-010
**Feature:** ask-forge-client
**Description:** Add Jest + babel-jest + React Testing Library + jsdom (devDependencies), `babel.config.js`,
`jest.config.js`, mocks for `@forge/react`, `@forge/bridge`, `@forge/jira-bridge`; `npm test`. Add
`@forge/jira-bridge`. Write `api/tsolveClient.js`: `getEligibility()`, `askQuestion(question, history)` via
`invokeRemote` with a 20 s timeout; maps 400 → MSG01/MSG02, 403 → MSG62, timeout / network / 5xx → MSG06; never
logs bodies. `messages.js` with MSG01, MSG02, MSG06, MSG62, MSG109 (SRS System Messages, exact text).
Tests: AC-010.3 (path, body, last 10 history turns), AC-010.8 (timeout / 5xx → MSG06), AC-010.11 (403 → MSG62),
AC-010.12 (no console output with question/answer text).
**Files:**
- Modify: `jira-app/TSolve_JiraApp/package.json`, `package-lock.json`
- Create: `jira-app/TSolve_JiraApp/{babel.config.js,jest.config.js,test/mocks/*}`, `src/frontend/api/tsolveClient.js`, `src/frontend/messages.js`
- Test: `src/frontend/api/tsolveClient.test.js`
**Dependencies:** Task 2
**Estimated size:** medium

---

## Task 5: Conversation logic, validation and prefill

**Story:** STORY-010
**Feature:** ask-conversation-logic
**Description:** Pure modules: `validation.js` (blank → MSG01, > 1,000 code points → MSG02), `conversation.js`
(reducer: idle → asking → answered / no-source / failed / solved; history = last 10 earlier turns; failed keeps
the question), `prefill.js` (summary = first question cut to 255 code points; description = every question, then
"Đã thử theo T-Solve" with the last answer and its sources; labels `['tsolve-asked']`; empty prefill when not
linked). Format from the Task 1 spike.
Tests: AC-010.5, AC-010.6/7 (state after ANSWERED / NO_SOURCE), AC-010.8 (failed keeps the question),
AC-010.9 (prefill content, 255 cut, surrogate pairs), AC-010.10 (solved ends the conversation, no call),
AC-010.12 (state only in memory).
**Files:**
- Create: `jira-app/TSolve_JiraApp/src/frontend/askTsolve/{validation,conversation,prefill}.js`
- Test: `src/frontend/askTsolve/{validation,conversation,prefill}.test.js`
**Dependencies:** Task 1, Task 4
**Estimated size:** medium

---

## Task 6: "Hỏi T-Solve" page

**Story:** STORY-010
**Feature:** ask-page-ui
**Description:** `manifest.yml`: `jira:projectPage` "Hỏi T-Solve" (resource `askTsolve/index.jsx`), the remote
endpoint/route for `/api/v1/jira-app/questions*`, scopes only as the spike found. `AskTsolvePage.jsx` (UI Kit):
eligibility on load; question box, send (disabled while asking), note "Trả lời từ Solution và Wiki của T-Solve";
answer text + sources (Ticket ID + title / Wiki title) or MSG109; MSG06 on failure; "Đã giải quyết" and
"Chưa được, tạo ticket" (`CreateIssueModal` with `prefill.js`); MSG62 view for unlinked users.
RTL tests with the mocks: AC-010.1, AC-010.2, AC-010.6, AC-010.7, AC-010.8, AC-010.9 (modal opened with the
prefill and project id), AC-010.10. Then check the flow by hand in `forge tunnel` on the dev site (linked and
unlinked account) and note it in the session log.
**Files:**
- Modify: `jira-app/TSolve_JiraApp/manifest.yml`
- Create: `jira-app/TSolve_JiraApp/src/frontend/askTsolve/{index.jsx,AskTsolvePage.jsx}`
- Test: `src/frontend/askTsolve/AskTsolvePage.test.jsx`
**Dependencies:** Task 3, Task 5
**Estimated size:** medium
