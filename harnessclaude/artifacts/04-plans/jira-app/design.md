---
module: jira-app
date: 2026-11-08
---

# Design: jira-app (KAN-294 — "Hỏi T-Solve")

Spec: `artifacts/03-modules/jira-app/spec.md` (approved, version 5b0840de) · Module: `artifacts/03-modules/jira-app/module.md`
Research: `artifacts/01-research/RESEARCH-004-ask-ai-before-creating-a-jira-issue.md`
Conventions: `jira-app/AGENTS.md` (UI Kit only, one server client, no secrets, fewest scopes), `backend/AGENTS.md`.

This branch (`feat/KAN-294-jira-app-ask-tsolve`) covers **STORY-010 only**. KAN-126 (STORY-001..009) has its own
`design.md` on `feat/KAN-126-jira-app-solution-form`; when both reach `main` the two designs are joined into one
file (sections per story group) — the add/add conflict is expected and resolved by keeping both.

## Architecture Overview

```
Jira project → "Hỏi T-Solve" (jira:projectPage, UI Kit, render: native)
  AskTsolvePage.jsx
   ├─ conversation.js   state: turns[], status (idle | asking | answered | failed | solved), validation
   ├─ validation.js     MSG01 (blank) / MSG02 (> 1,000 code points) — nothing is sent
   ├─ api/tsolveClient.js  invokeRemote('tsolve-backend', POST /api/v1/jira-app/questions)
   │      body {question, history: last 10 turns}; 20 s client timeout; maps status → MSG code
   └─ prefill.js        CreateIssueModal({context:{projectId}, fields:{summary, description, labels:['tsolve-asked']}})

backend /api/v1/jira-app/** (FIT chain, ROLE_JIRA_APP, tracker STORY-001)
  JiraAppQuestionController  POST /api/v1/jira-app/questions
   └─ JiraAppQuestionService(Impl)
        ├─ JiraAskerLookup.find(cloudId, accountId) → Optional<TSolveUser>   empty → 403 MSG62, no AI call
        │     └─ impl/ConfigJiraAskerLookup  (stub: TSOLVE_STUB_JIRA_ASKERS → active Account)
        └─ ChatbotAnswerService.ask(user, ChatbotQuestionRequest)  run with its own deadline
              (JIRA_APP_ASK_TIMEOUT, default 18s) → past it / AiUnavailableException → 503 MSG06
```

- **Page → server**: the UI calls the backend with `invokeRemote` (no Forge resolver hop), as KAN-126 decided; Forge
  attaches a FIT whose `principal` is the viewer's accountId. Every call goes through `api/tsolveClient.js`
  (AGENTS rule 5).
- **The answer is the chatbot's**: the backend does not re-implement retrieval or prompts. It maps the FIT to a
  `TSolveUser` and calls `ChatbotAnswerService.ask`, returning its `ChatbotAnswerResponse` unchanged (AC-010.4).
  The chatbot's own validation (MSG01/MSG02) and its `ChatbotAnswer` row behave exactly as on the web.
- **Not linked** (AC-010.2, AC-010.11): AC-010.2 shows MSG62 **instead of** the question box, before any question
  is sent, so the page first calls `GET /api/v1/jira-app/questions/eligibility` → `{linked: boolean}` (spec Exposed
  table, 08/11). The POST refuses an unlinked user on its own (AC-010.11).
- **"Chưa được, tạo ticket"** (AC-010.9): `CreateIssueModal` from `@forge/jira-bridge`, prefilled; Jira itself
  creates the issue as the user — the backend is not involved. Unlinked users get the same button with nothing
  prefilled (AC-010.2).
- **"Đã giải quyết"** (AC-010.10): UI state only; no call (rating is KAN-182).
- **Nothing stored by the app** (AC-010.12): conversation lives in React state; closing the page drops it. No
  Forge storage, no `console.log` of question/answer text.

## Data Model

No new table. The chatbot already stores its `ChatbotAnswer` row per question (chatbot spec); the Jira App adds
nothing.

DTOs (backend, `vn.tsolve.jiraapp.dto`):
- Request: reuse `ChatbotQuestionRequest` (`question` @NotBlank MSG01, @Size 1000 MSG02; `history` list of
  `ChatbotTurnRequest`), wrapped by `JiraAppQuestionRequest` only to add `@Size(max = 10)` on history.
- Response: `ChatbotAnswerResponse` (answerId, outcome `ANSWERED|NO_SOURCE`, text, citations) — same schema in
  OpenAPI, so the generated types and the page read the same fields as the web chatbot.
- Eligibility: `JiraAskerEligibility(boolean linked)`.

Stub config (`.env.example`): `TSOLVE_STUB_JIRA_ASKERS=<accountId>=<accountUuid>;…` — the Account must exist and be
`ACTIVE`; anything else is "not linked".

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| FIT → T-Solve user | Port `JiraAskerLookup` + `ConfigJiraAskerLookup` reading `TSOLVE_STUB_JIRA_ASKERS` | fail-closed stand-in; wait for identity | User, 2026-11-08: demo on the dev site now; replaced by identity's `JiraAccountLinkLookup` (identity AC-001.69..71) |
| 20 s limit (NF 2) | Own deadline in the jira-app service, `JIRA_APP_ASK_TIMEOUT` default 18s → 503 MSG06 | deadline passed into `ChatbotAnswerService`; lower `CHATBOT_ASK_TIMEOUT` | User, 2026-11-08: no change to the chatbot module. Known cost: a late chatbot call keeps running and may still save its `ChatbotAnswer` row |
| Forge test setup | Jest + Babel + React Testing Library, `@forge/react` and `@forge/bridge` / `@forge/jira-bridge` mocked | Vitest + RTL; unit only | User, 2026-11-08 (matches the Forge testing approach they researched). Snapshot tests skipped; Playwright E2E later |
| UI → server | `invokeRemote` from the page | Forge resolver relay | Same as KAN-126; FIT carries the viewer, no extra hop |
| Answer engine | Call `ChatbotAnswerService` as is | own retrieval | Spec AC-010.4: same sources, citations and outcomes as the web chatbot |
| Backend package | `vn.tsolve.jiraapp` (controller, service, service/impl, dto) | `vn.tsolve.tracker` like KAN-126 | AC ids restart per module; a separate package lets `sdd.ts trace` attribute `AC_010_*` tests to jira-app via `paths` |

### Builder check — 2026-11-08 (inline)
- Resolved (user, 2026-11-08, option A): AC-010.2 needs "linked or not" before the first question; the spec now
  lists `GET /api/v1/jira-app/questions/eligibility` → `200 {linked}` (same lookup, no AI call; on failure the
  page shows the question box and the POST stays the guard). Spec re-approved as 5b0840de.

### Decided in planning (builder check)
- **History over 10 items** sent to the server → 400 (Problem, code MSG06), no AI call. The page never sends more.
- **Stub user roles**: `TSolveUser(account.id, email, fullName, {})` → EMPLOYEE only, until identity gives roles.
  Wiki access uses `NoWikiSources` today anyway.
- **Character count** for MSG02 is in code points on both sides (`[...text].length` in JS); the server's `@Size`
  counts UTF-16 units — an emoji-heavy question of ≤ 1,000 code points but > 1,000 units gets MSG02 from the server.
  Accepted (edge case 4).
- **Description format** for `CreateIssueModal`: ADF (`{version: 1, type: "doc", content: [paragraph…]}`), one
  paragraph per question, then `Đã thử theo T-Solve:`, the last answer and a `Nguồn:` paragraph per source — verified in
  the Task 1 spike (RESEARCH-004 Spike 2). A plain string also works but is undocumented, so it is not used.
- **`onClose`** returns only `issueId`s (no key); the page ignores it — Jira shows its own "Created …" toast. Cancel
  returns `[]` after Jira's own "changes won't be saved" prompt.
- **Summary** = first question, trimmed, cut to 255 code points.

## File Structure

```
jira-app/TSolve_JiraApp/
├── manifest.yml                    + jira:projectPage "Hỏi T-Solve"; endpoint/remote route for /api/v1/jira-app/questions*
├── package.json                    + jest, babel-jest, @babel/preset-env, @babel/preset-react,
│                                     @testing-library/react, @testing-library/jest-dom, jest-environment-jsdom,
│                                     @forge/jira-bridge; script "test": "jest"
├── babel.config.js                 test-only (Forge bundles on its own)
├── jest.config.js                  jsdom, moduleNameMapper for the Forge mocks
├── test/mocks/forge-react.jsx      UI Kit stand-ins (Button, TextArea, Text, SectionMessage, Stack, Link…)
└── src/frontend/
    ├── api/tsolveClient.js         only server access point: askQuestion, getEligibility; timeout + error map
    ├── askTsolve/index.jsx         ForgeReconciler.render(<AskTsolvePage/>) — the projectPage resource
    ├── askTsolve/AskTsolvePage.jsx
    ├── askTsolve/conversation.js   reducer + history (last 10)
    ├── askTsolve/validation.js
    ├── askTsolve/prefill.js        summary / description / labels for CreateIssueModal
    ├── messages.js                 MSG01, MSG02, MSG06, MSG62, MSG109 texts (SRS System Messages)
    └── **/*.test.js(x)             AC-010.x tests
backend/src/main/java/vn/tsolve/jiraapp/
├── controller/JiraAppQuestionController.java
├── service/JiraAppQuestionService.java, JiraAskerLookup.java
├── service/impl/JiraAppQuestionServiceImpl.java, ConfigJiraAskerLookup.java
├── dto/JiraAppQuestionRequest.java, JiraAskerEligibility.java
└── exception/JiraAskerNotLinkedException.java   → 403 MSG62 (global handler)
backend/src/test/java/vn/tsolve/jiraapp/…          controller (MockMvc + FIT auth) and service tests
frontend/openapi/tsolve-api.yaml                   two jira-app paths; reuse ChatbotAnswerResponse schema
.env.example                                       TSOLVE_STUB_JIRA_ASKERS, JIRA_APP_ASK_TIMEOUT
```

## External Dependencies
- `@forge/react` (UI Kit), `@forge/bridge` (`invokeRemote`), `@forge/jira-bridge` (`CreateIssueModal`) — new.
- Scopes: none new — confirmed in the Task 1 spike (`CreateIssueModal` and `jira:projectPage` deploy with only
  `read:jira-work`). Any added scope is justified in the PR (AGENTS rule 3).
- Backend: `ChatbotAnswerService` (chatbot, Trần Hải Anh — call agreed before Task 4 is closed), `AccountRepository`
  (identity, read only, in the stub).
- Test: Jest, babel-jest, React Testing Library, jsdom (devDependencies only).
- Follow-ups outside this module: tracker AC-011.8 change (skip the suggestion job for label `tsolve-asked`) — a
  tracker spec change request; identity's `JiraAccountLinkLookup` replaces the stub.

## Edge Cases & Risks

Dimensions analyzed: 1 User types, 2 Input extremes, 3 Timing, 7 Error cascades, 8 Authorization, 9 Data
integrity, 10 Integration, 11 Compliance · Skipped: 4 Scale (one conversation per page), 5 State (no stored state),
6 Environment (Jira renders UI Kit), 12 Business logic (no amounts).

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-010 | Authorization | Unlinked or inactive account calls the POST directly (skipping the page) | Critical | 403 MSG62, `ChatbotAnswerService` never called | Task 3 |
| 2 | STORY-010 | Integration | FIT without `principal` (product event token) on the POST | High | 403 MSG62 (treated as not linked), no AI call | Task 3 |
| 3 | STORY-010 | Error cascades | Chatbot slower than 18 s, or AI down | High | 503 MSG06 within ~18 s; page shows MSG06, keeps the question, keeps "Chưa được, tạo ticket" | Task 3, Task 5 |
| 4 | STORY-010 | Input extremes | Exactly 1,000 code points; 1,001; only spaces/newlines; emoji | Medium | 1,000 sent; 1,001 MSG02; blank MSG01; nothing sent | Task 5 |
| 5 | STORY-010 | Input extremes | Conversation longer than 10 turns | Medium | Only the last 10 earlier turns sent; server rejects > 10 with 400 | Task 3, Task 5 |
| 6 | STORY-010 | Timing | Double click on send while asking | Medium | Send disabled while `asking`; one request | Task 6 |
| 7 | STORY-010 | Data integrity | First question longer than 255 characters → Summary | Medium | Cut to 255 code points, no broken surrogate | Task 5 |
| 8 | STORY-010 | Compliance | Question or answer text in logs (Forge or backend) | High | Logs carry ids / status / error type only | Task 3, Task 5 |
| 9 | STORY-010 | Integration | `CreateIssueModal` cannot prefill labels or needs ADF description | High | Spike (Task 1, 08/11): labels and ADF description prefill and save; no new scope — closed | Task 1 |
| 10 | STORY-010 | User types | Unlinked user | Medium | MSG62 instead of the box; Create button with nothing prefilled | Task 6 |
| 11 | STORY-010 | Error cascades | Eligibility call fails | Low | Show the question box; POST stays the guard | Task 6 |
