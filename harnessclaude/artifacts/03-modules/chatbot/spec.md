---
module: chatbot
date: 2026-10-23
owner: BA
---

# Spec: chatbot

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-23 | STORY-001..STORY-004 | Initial spec from Report3 (UC-51–53, GBR-36), SRS §III.12.1 and Figma 12.1 | Plan the Knowledge Chatbot (KAN-69, KAN-48); the backend is mocked for now |
| 2026-10-23 | STORY-001 | AC-001.13: a 401 signs the user out (MSG07) with no MSG06; a field error on another field than the question shows MSG06 | Review of KAN-71 (findings 1 and 3) |

## Requirements

### Functional
1. Every signed-in user asks the Knowledge Chatbot questions and follow-up questions on screen 12.1 (UC-51).
2. Answers come only from the Solutions of all Published tickets in the Workspace and the Wiki documents the user
   can view (GBR-31), checked by the server on every question (GBR-36).
3. Every answer cites its sources with numbered citations. A ticket source shows only its Solution, never the
   ticket's details, and cannot be opened (GBR-36, Permission Matrix note ⁹).
4. With no source the Chatbot says so (MSG109) instead of answering; if the AI module fails, MSG06 and the question
   is kept (UC-51 alternative flows).
5. The conversations are kept for the session only and cleared at logout (UC-52, GBR-36, UC-02).
6. A user rates an answer Helpful or Not helpful; ratings are kept permanently (UC-53) — built after KAN-48.

### Non-Functional
1. Every acceptance criterion has a test whose `it` name starts with its id; `sdd.ts trace chatbot` passes for the
   stories that are built.
2. Each PR passes the frontend `verify`: `format:check`, `lint`, `typecheck`, `test`, `build`, `generate:api` with
   no diff.
3. No new npm dependency.
4. The screen depends only on the REST contract below: replacing the MSW mocks with the real backend needs no
   change in `frontend/src/features/chatbot/` other than its `mocks/` folder.
5. Changes to `app/`, `shared/`, `mocks/` or `test/` (fe-foundation) are not part of this module; if one is
   needed, it is a quick change or a change request decided by the FE Lead.

## User Stories

### STORY-001: Ask a question and read a cited answer
- As an Employee, I want to ask a question in my own words and get an answer built only from knowledge I may see,
  with its sources, so that I can solve a problem myself before creating an issue.
- Acceptance:
  - AC-001.1: WHEN a signed-in user opens `/chatbot`, THE web app SHALL show 12.1 Knowledge Chatbot with the
    conversation thread, the question box and the Send button (the conversation list: STORY-002).
  - AC-001.2: WHEN the user presses Enter in the question box, THE web app SHALL send the question; Shift+Enter SHALL
    add a new line instead.
  - AC-001.3: WHEN the user clicks Send, THE web app SHALL send the question.
  - AC-001.4: IF the question is empty or only white space, THEN THE web app SHALL show MSG01 under the box
    ("The Question field is required.") and SHALL not send it.
  - AC-001.5: THE question box SHALL show a character count with the maximum 1,000; IF the question is longer than
    1,000 characters, THEN THE web app SHALL show MSG02 ("Question must not exceed 1000 characters.") and SHALL not
    send it.
  - AC-001.6: WHEN a question is sent, THE web app SHALL call `POST /api/v1/chatbot/answers` with the question and
    the earlier turns of the conversation (STORY-002), show the question in the thread with a loading answer, and
    disable Send until the answer arrives.
  - AC-001.7: WHEN the answer arrives with `outcome` `ANSWERED`, THE web app SHALL show its text under the question,
    with the numbered markers (`[1]`, `[2]` …) as written, followed by the list of its citations in number order, and
    SHALL empty the question box.
  - AC-001.8: THE web app SHALL show the answer text and every citation field as plain text, never as HTML.
  - AC-001.9: WHEN a citation is a Wiki document, THE web app SHALL show "[n]", the document title and "· Wiki document"
    as a link that opens 11.2 Wiki Document of that document in the same tab.
  - AC-001.10: WHEN a citation is a ticket Solution, THE web app SHALL show "[n] Solution from a ticket" with a Show
    button, and SHALL show no ticket ID, link, title, Project, writer, snapshot or Evidence.
  - AC-001.11: WHEN the user clicks Show on a ticket Solution citation, THE web app SHALL expand the Solution inline
    under the citation — Symptoms, Root cause, Resolution steps, Workaround (only when it has one) and Scope of
    applicability — and the button SHALL become Hide; Hide SHALL collapse it again.
  - AC-001.12: WHEN the answer arrives with `outcome` `NO_SOURCE`, THE web app SHALL show MSG109 as the answer, with
    `{tracker_name}` filled as "Jira", and no citations.
  - AC-001.13: IF the request fails, not with a 401 or a field error on the question, THEN THE web app SHALL show
    MSG06 as a toast, remove the pending question from the thread, keep the question in the box so the user can send
    it again, and add nothing to the conversation. This covers the AI module being unavailable (503), network errors
    and field errors on other fields; a 401 signs the user out with MSG07 (fe-foundation), with no MSG06.
  - AC-001.14: IF the answer request fails with field errors (400), THEN THE web app SHALL show the field's MSG under
    the question box and keep the question in the box.

### STORY-002: Session conversations
- As an Employee, I want to see and continue my earlier conversations of this session so that I do not have to ask
  the same question again, and nothing of them stays after I log out.
- Acceptance:
  - AC-002.1: WHILE the session has no conversation, THE web app SHALL show an empty conversation, titled "New
    conversation" and selected in the list, ready for a first question.
  - AC-002.2: WHEN the first answer of a conversation arrives, THE web app SHALL save the conversation with its
    question and answer and title it with its first question.
  - AC-002.3: THE conversation list SHALL show the conversations of the session newest first, by the time each was
    started, and highlight the open one.
  - AC-002.4: WHEN the user opens a conversation in the list, THE web app SHALL show its questions and answers in
    order, each answer with its citations as in STORY-001.
  - AC-002.5: WHEN the user asks a follow-up question in an open conversation, THE web app SHALL send the earlier
    turns of that conversation (question and answer text, oldest first, at most the last 10) with it and SHALL add
    the new turn to that conversation.
  - AC-002.6: WHEN the user clicks New conversation, THE web app SHALL open an empty conversation at the top of the
    list, titled "New conversation"; IF the open conversation is already empty, THEN THE web app SHALL keep it
    instead of adding another.
  - AC-002.7: THE web app SHALL keep the conversations in `sessionStorage` under a key that starts with
    `tsolve.session.chatbot.`, so they stay while the browser tab is open, survive a page reload and are removed at
    logout and on a global 401 (fe-foundation AC-008.1).
  - AC-002.8: IF the stored conversations belong to another user or cannot be read, THEN THE web app SHALL drop them
    and start with an empty conversation.
  - AC-002.9: WHEN a conversation with Wiki document citations is shown, THE web app SHALL ask
    `GET /api/v1/chatbot/unavailable-documents` with their ids, and SHALL show MSG110 in place of the link of each
    document the response lists.
  - AC-002.10: IF that availability request fails, THEN THE web app SHALL show the Wiki citations as links.
  - AC-002.11: IF storage is blocked, THEN THE web app SHALL still answer questions and keep the conversations
    of the open page in memory.

### STORY-003: Rate an answer
- As an Employee, I want to say whether an answer helped me so that weak answers and missing knowledge can be
  found. Built after KAN-48 (not in its tasks).
- Acceptance:
  - AC-003.1: THE web app SHALL show Helpful and Not helpful buttons under every answer, the MSG109 answer included.
  - AC-003.2: WHEN the user clicks Helpful or Not helpful, THE web app SHALL call
    `PUT /api/v1/chatbot/answers/{answerId}/rating` with that rating and highlight the chosen button.
  - AC-003.3: WHEN the user rates an answer again, THE web app SHALL send the new rating, which replaces the previous
    one, and highlight only the new button.
  - AC-003.4: WHEN a rated answer is shown again in the session (STORY-002), THE web app SHALL highlight its rating.
  - AC-003.5: IF the rating request fails, THEN THE web app SHALL show MSG06 and keep the previous highlight.

### STORY-004: Mock answers
- As a developer and a test writer, I want the screen to work in mock mode with answers that show every state, so
  that it can be built and demonstrated before the backend exists.
- Acceptance:
  - AC-004.1: WHEN a question contains a keyword of a mock answer, THE mock SHALL answer `ANSWERED` with text that
    holds numbered markers and at least one Wiki document and one ticket Solution citation. The keywords include
    "VPN", "leave" and "expense".
  - AC-004.2: IF a question matches no mock answer, THEN THE mock SHALL answer `NO_SOURCE` with no citations.
  - AC-004.3: IF a question is empty or longer than 1,000 characters, THEN THE mock SHALL answer 400 with a field
    error MSG01 or MSG02 on `question`.
  - AC-004.4: WHEN the mock is asked which Wiki documents are unavailable, THE mock SHALL list the ids of its
    documents that are marked unavailable (at least one cited document) and no others.
  - AC-004.5: WHEN `resetMockData()` runs, THE mock SHALL return to its initial data.

## Out of Scope
- The backend `chatbot` package — added later by a change to this spec once JDK 21 and Maven are set up: the answer
  prompt, the Ollama client (GBR-12), PII masking of questions before the AI module (GBR-36), the search adapter
  (`SearchService` with scope `WorkspaceSolutions`, PR #30), the Wiki sources, the knowledge gap of a `NO_SOURCE`
  question, the availability check and the stored ratings. Until then MSW serves the contract.
- The real search (`search` module) and the Wiki (`wiki` module); 11.2 Wiki Document stays their placeholder.
- Counting ratings on the Knowledge Dashboard (`dashboard-audit`).
- Streaming answers; answers are one response.
- Kit components in `shared/ui`: the chat parts are built in `features/chatbot/components/` (rule of two).

## Interface Contracts

### Exposed by this module
REST, in `frontend/openapi/tsolve-api.yaml`, tag `chatbot` (served by the backend `chatbot` package later):

```yaml
POST /api/v1/chatbot/answers            # operationId askChatbot
  request:  ChatbotQuestion
  200:      ChatbotAnswer
  400:      ProblemDetail, fieldErrors on question (MSG01, MSG02)
  401, 403: ProblemDetail
  503:      ProblemDetail code MSG06       # AI module unavailable

GET /api/v1/chatbot/unavailable-documents?wikiDocumentIds=1&wikiDocumentIds=2   # operationId listUnavailableCitedDocuments
  200:      UnavailableDocuments          # the given ids the user can no longer view

PUT /api/v1/chatbot/answers/{answerId}/rating   # operationId rateChatbotAnswer — STORY-003, added with it
  request:  { rating: HELPFUL | NOT_HELPFUL }
  204

ChatbotQuestion:
  required: [question, history]
  question: string, 1..1000 characters
  history:  ChatbotTurn[], at most 10, oldest first   # earlier turns of the conversation
ChatbotTurn:
  required: [question, answer]
  question: string
  answer:   string                         # the answer text; '' for a NO_SOURCE turn
ChatbotAnswer:
  required: [answerId, outcome, text, citations]
  answerId:  string (uuid)                 # for the rating (STORY-003)
  outcome:   ANSWERED | NO_SOURCE
  text:      string                        # plain text with [n] markers; '' when NO_SOURCE
  citations: ChatbotCitation[]             # number order; [] when NO_SOURCE
ChatbotCitation: oneOf, discriminator type
  WikiDocumentCitation:   { number: int, type: WIKI_DOCUMENT, wikiDocumentId: int64, title: string }
  TicketSolutionCitation: { number: int, type: TICKET_SOLUTION, solution: CitedSolution }
CitedSolution:
  required: [symptoms, rootCause, resolutionSteps, workaround, scopeOfApplicability]
  workaround: string | null                # the other four: string
UnavailableDocuments:
  required: [wikiDocumentIds]
  wikiDocumentIds: int64[]
```

A ticket citation carries no ticket id, title, link, Project, writer, snapshot or Evidence (GBR-36).

Session storage: `sessionStorage['tsolve.session.chatbot.conversations']`, owned by this feature only.

### Required from other modules
- `fe-foundation` (done): route `/chatbot` for every signed-in user, `paths.wikiDocument`, MSG01, MSG02, MSG06,
  MSG109, MSG110 in the catalog, `rules.required` / `rules.maxLength`, and removal of `tsolve.session.*` keys at
  logout and on a global 401.
- `search` (later, backend): `SearchService.search(query, WorkspaceSolutions(userId), limit)` returning hits with
  the Solution by section (Symptoms, Root cause, Resolution steps, Workaround, Scope of applicability) — to agree with
  Hải Anh on PR #30, whose `SearchHit` has one `solutionText` today.
- `wiki` (later, backend): the Wiki documents the user can view (GBR-31), as search hits or a source port.
- The backend (later): only sources the user may see; never an answer without a source; PII masked before the AI
  module.

## Definition of Done
- [ ] Every acceptance criterion of STORY-001, STORY-002 and STORY-004 has a test whose name starts with its AC id;
      `sdd.ts trace chatbot STORY-001 STORY-002 STORY-004` passes (STORY-003 when it is built)
- [ ] The frontend `verify` passes on every PR (KAN-71, KAN-73, KAN-72)
- [ ] The search contract (Solution by section) is raised with Hải Anh on PR #30
- [ ] Each PR is reviewed by the FE Lead
