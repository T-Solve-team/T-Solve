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
| 2026-10-23 | STORY-001 | AC-001.7: the box is emptied only if it still holds the sent question | Review of PR #51 (finding 1): a follow-up typed while waiting was erased |
| 2026-10-24 | STORY-003, STORY-004 | STORY-003: highlight after the save, remove a rating, ratings kept with the conversation, no comment; retention rule; AC-004.6 rating mocks; contract gains `DELETE` | Plan KAN-75 (UC-53, GBR-36); FE Lead 24/10: the two buttons of Figma 12.1 (c) only, no comment box |
| 2026-10-25 | — (Requirements) | Requirements 2, 4 and 7 reworded and Requirement 8 added: Workspace scope decided by the server, knowledge gap, stored records carry their Workspace, Ollama on the T-Solve AI server with graceful failure; Out of Scope and Definition of Done point to the backend change; Interface Contracts: no endpoint takes a Workspace, `platform` and `ai` required. No acceptance criterion added, reworded or removed | Change request KAN-199 (GBR-12, GBR-36, GBR-37, D3, D28, D41); the user, 25/10: the server-side criteria are written by Hải Anh in her backend change (KAN-179…183) |

## Requirements

### Functional
1. Every signed-in user asks the Knowledge Chatbot questions and follow-up questions on screen 12.1 (UC-51).
2. Answers come only from the Solutions of all Published tickets of the user's Workspace and the Wiki documents of
   that Workspace the user can view (GBR-31), checked by the server on every question (GBR-36). The server decides
   the Workspace from the Workspace site of the request; no request can choose it, and no answer source,
   conversation, answer or rating ever crosses Workspaces (GBR-37).
3. Every answer cites its sources with numbered citations. A ticket source shows only its Solution, never the
   ticket's details, and cannot be opened (GBR-36, Permission Matrix note ⁹).
4. With no source the Chatbot says so (MSG109) instead of answering and records a knowledge gap; if the AI module
   fails, MSG06 and the question is kept (UC-51 alternative flows). A knowledge gap is only a Knowledge Chatbot
   question for which no source was found: a Not helpful rating, a Complex triage result and a failed answer are not
   knowledge gaps (D28, GBR-36).
5. The conversations are kept for the session only and cleared at logout (UC-52, GBR-36, UC-02).
6. A user rates an answer Helpful or Not helpful and can change or remove the rating (UC-53, KAN-75). There is no
   comment: Figma 12.1 (c) shows only the two buttons (FE Lead, 24/10).
7. Retention (backend, later): a rating is kept permanently with the rater's identity; the stored question is
   PII-masked. When the session ends, unrated `ANSWERED` answers are deleted; rated answers and `NO_SOURCE` answers
   (knowledge gaps) are kept (GBR-36). A Not helpful rating is feedback, not a knowledge gap. Every stored answer,
   knowledge gap and rating belongs to the Workspace of the request that created it (GBR-37).
8. AI server (backend, later): the chat LLM and the bge-m3 embeddings (1024 dimensions) run only on Ollama on the
   T-Solve AI server, the dedicated AI VPS operated by the T-Solve team, reached only from the application server
   over a private, encrypted link; no content goes to a third-party AI service and OpenRouter is not used (D3, D41,
   GBR-12). Only PII-masked text is sent to it (GBR-36). When the AI server is slow or down, the Chatbot fails
   gracefully as GBR-12 says: the question gets MSG06 (AC-001.13) and the rest of T-Solve keeps working.

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
    SHALL empty the question box if it still holds the question that was sent (text typed while waiting is kept).
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
  found. Built in KAN-75.
- Acceptance:
  - AC-003.1: THE web app SHALL show Helpful and Not helpful buttons under every answer, the MSG109 answer included.
  - AC-003.2: WHEN the user clicks Helpful or Not helpful on an unrated answer, THE web app SHALL call
    `PUT /api/v1/chatbot/answers/{answerId}/rating` with that rating, disable both buttons until the response, and
    highlight the chosen button once it is saved.
  - AC-003.3: WHEN the user clicks the other button of a rated answer, THE web app SHALL send the new rating, which
    replaces the previous one, and highlight only the new button once it is saved.
  - AC-003.4: WHEN a rated answer is shown again in the session (STORY-002), THE web app SHALL highlight its rating,
    which is kept with the conversation (AC-002.7).
  - AC-003.5: IF a rating or removal request fails, THEN THE web app SHALL show MSG06 as a toast and keep the
    previous highlight; a 401 instead signs the user out with MSG07 (fe-foundation), with no MSG06.
  - AC-003.6: WHEN the user clicks the highlighted button, THE web app SHALL call
    `DELETE /api/v1/chatbot/answers/{answerId}/rating` and remove the highlight once it succeeds.

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
  - AC-004.6: WHEN the mock is asked to save a rating, or to remove one, THE mock SHALL answer 204.

## Out of Scope
- The backend `chatbot` package and its acceptance criteria — added later by Hải Anh's backend change to this spec
  (KAN-179…183): the answer prompt, the Ollama client (GBR-12), PII masking of questions before the AI module
  (GBR-36), the search adapter (`SearchService` with scope `WorkspaceSolutions`, PR #30), the Wiki sources, the
  knowledge gap of a `NO_SOURCE` question, the availability check and the stored ratings, and the server-side
  criteria of Requirements 2, 4, 7 and 8 (Workspace scope, knowledge gap, Workspace of stored records, the AI
  server and its failure). The backend defaults agreed on 25/10 for that change are in the design ("Backend
  defaults agreed 25/10"). Until then MSW serves the contract.
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

PUT /api/v1/chatbot/answers/{answerId}/rating      # operationId rateChatbotAnswer — STORY-003 (KAN-75)
  request:  ChatbotRating                          # replaces any earlier rating of this user
  204
  401, 403, 404: ProblemDetail

DELETE /api/v1/chatbot/answers/{answerId}/rating   # operationId removeChatbotAnswerRating — STORY-003 (KAN-75)
  204                                              # also when the answer has no rating
  401, 403, 404: ProblemDetail

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
ChatbotRating:
  required: [rating]
  rating:  HELPFUL | NOT_HELPFUL
UnavailableDocuments:
  required: [wikiDocumentIds]
  wikiDocumentIds: int64[]
```

A ticket citation carries no ticket id, title, link, Project, writer, snapshot or Evidence (GBR-36).

No endpoint takes a Workspace: the server decides it from the Workspace site of the request (GBR-37), and every
id in a request (`answerId`, `wikiDocumentIds`) is looked up only inside that Workspace.

Session storage: `sessionStorage['tsolve.session.chatbot.conversations']`, owned by this feature only.

### Required from other modules
- `fe-foundation` (done): route `/chatbot` for every signed-in user, `paths.wikiDocument`, MSG01, MSG02, MSG06,
  MSG109, MSG110 in the catalog, `rules.required` / `rules.maxLength`, and removal of `tsolve.session.*` keys at
  logout and on a global 401.
- `search` (later, backend): `SearchService.search(query, WorkspaceSolutions(userId), limit)` returning hits with
  the Solution by section (Symptoms, Root cause, Resolution steps, Workaround, Scope of applicability) — to agree with
  Hải Anh on PR #30, whose `SearchHit` has one `solutionText` today.
- `wiki` (later, backend): the Wiki documents the user can view (GBR-31), as search hits or a source port.
- `platform` (later, backend; proposed in KAN-195): the Workspace of the request, decided from the Workspace site
  (TenantContext), for every chatbot request and every record it stores (GBR-37).
- `ai` (later, backend; proposed in KAN-195): the chat LLM and bge-m3 embedding clients for Ollama on the T-Solve AI
  server and the PII masking before them (GBR-12, GBR-36, D3, D41).
- The backend (later): only sources the user may see, of the user's Workspace; never an answer without a source;
  PII masked before the AI module.

## Definition of Done
- [ ] Every acceptance criterion of STORY-001, STORY-002 and STORY-004 has a test whose name starts with its AC id;
      `sdd.ts trace chatbot STORY-001 STORY-002 STORY-003 STORY-004` passes
- [ ] The frontend `verify` passes on every PR (KAN-71, KAN-73, KAN-72, and the KAN-75 Subtasks)
- [ ] The search contract (Solution by section) is raised with Hải Anh on PR #30
- [ ] Each PR is reviewed by the FE Lead
- The backend (Requirements 2, 4, 7 and 8): its criteria and Definition of Done come with Hải Anh's backend change
  (KAN-179…183); see Out of Scope.
