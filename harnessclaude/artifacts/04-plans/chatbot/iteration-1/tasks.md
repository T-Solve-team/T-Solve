---
module: chatbot
date: 2026-10-23
total_tasks: 5
owner: Dev
---

# Tasks: chatbot — iteration 1

Spec: `artifacts/03-modules/chatbot/spec.md` · Design: `artifacts/04-plans/chatbot/design.md`

One task = one Jira Subtask of KAN-48 = one branch = one PR, merged in this order. Each task is one vertical slice
a user can try: it carries every layer it needs (contract, mocks, hooks, components, tests). Tasks 1-3 build
KAN-48 (UC-51, UC-52, frontend with mocks); Task 4 builds KAN-75 (UC-53 ratings, frontend with mocks, added 24/10).
The backend is not in this iteration.
Every test's `it` name starts with the AC id it proves (guideline 10 T6). Each task ends with the frontend `verify`
(root `CLAUDE.md`).

## Task 1: Ask a question and show the answer

**Story:** STORY-001, STORY-004
**Feature:** ask-answer
**Jira:** KAN-71 · branch `feature/KAN-71-chatbot-ask-answer`
**Description:** The first slice, with the contract and the mocks it needs (guideline 06: an endpoint and its first
screen in one PR).
- Contract: the `chatbot` tag and `POST /api/v1/chatbot/answers` with its schemas (spec Interface Contracts);
  regenerate the client. The availability path is Task 2's (its first user), the rating path STORY-003's.
- Mocks: the answers of the mock data of the design and the ask handler, typed with the generated types. The
  answers never change, so `resetMockData()` gets its first data (and AC-004.5) in Task 2.
- Screen: replace the placeholder with the 12.1 layout (list card on the left, empty for now; thread card with the
  question box). `QuestionBox`: `TextArea` with count 1,000, `rules.required` / `rules.maxLength` with the label
  "Question", Enter sends, Shift+Enter new line. `useAskQuestion`: generated mutation, `handlesOwnErrors`, field
  errors under the box, otherwise MSG06; the box is cleared only on success. `ConversationThread`: turns of one
  conversation kept in memory, the pending question with a loading answer, Send disabled while pending.
  `ChatAnswer`: answer text as plain text, or MSG109. `model.ts` with `toHistory`.
- fe-foundation change request (agreed with the FE Lead, 23/10), done once for every placeholder: reword AC-005.3 to
  "WHILE a screen of 2.1, 10.1, 11.1, 11.2, 11.4, 11.7 or 12.1 is not built by its module, THE web app SHALL show a
  placeholder …" (Changelog row), the FE Lead approves fe-foundation again, fe-foundation is reopened
  (`completed → planned`) for one task, and the 12.1 row leaves `app/router/route-table.test.tsx`. Later modules
  then remove their row with a quick change.
The PR is about 850 changed lines without generated code (~550 code, ~300 tests); it is one behaviour, so it is not
split further, and its description says so.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Create: `frontend/src/features/chatbot/mocks/data.ts` · Modify: `frontend/src/features/chatbot/mocks/handlers.ts`
- Create: `frontend/src/features/chatbot/model.ts`, `hooks/useAskQuestion.ts`, `hooks/useConversations.ts`
- Create: `frontend/src/features/chatbot/components/QuestionBox.tsx`, `ConversationThread.tsx`, `ChatAnswer.tsx`,
  `*.module.css`
- Modify: `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.tsx`
- Test: `frontend/src/features/chatbot/mocks/handlers.test.ts`,
  `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx`
- Modify (change request): `frontend/src/app/router/route-table.test.tsx`,
  `harnessclaude/artifacts/03-modules/fe-foundation/spec.md`
**Covers:** AC-001.1–AC-001.8, AC-001.12–AC-001.14, AC-004.1–AC-004.3; edge cases 1–8, 11, 21; risk "ticket details
leak" (no ticket fields in the contract)
**Dependencies:** the plan PR (KAN-69) merged
**Estimated size:** medium

---

## Task 2: Citations of an answer

**Story:** STORY-001, STORY-002, STORY-004
**Feature:** citations
**Jira:** KAN-73 · branch `feature/KAN-73-chatbot-citations`
**Description:** `CitationList` under each answer in number order: a Wiki citation as a link to
`paths.wikiDocument(id)` showing "[n]", the title and "· Wiki document"; a ticket citation as "[n] Solution from a
ticket" with Show / Hide and `TicketSolution` (five sections, Workaround only when not null). With its first user,
the contract and mock of `GET /api/v1/chatbot/unavailable-documents` (the unavailable ids of the mock data).
`useUnavailableDocuments`: the generated query for the Wiki ids of the shown conversation; MSG110 in place of an
unavailable link; links kept when the query fails.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/features/chatbot/mocks/data.ts`, `mocks/handlers.ts`, `mocks/handlers.test.ts`
- Create: `frontend/src/features/chatbot/components/CitationList.tsx`, `TicketSolution.tsx`, `*.module.css`
- Create: `frontend/src/features/chatbot/hooks/useUnavailableDocuments.ts`
- Modify: `frontend/src/features/chatbot/components/ChatAnswer.tsx`, `ConversationThread.tsx`
- Test: `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx`
**Covers:** AC-001.9–AC-001.11, AC-002.9, AC-002.10, AC-004.4, AC-004.5, and the citation halves of AC-001.7 (the
list in number order) and AC-001.8 (HTML in a Wiki title or Solution section shown as text), and the reworded
AC-001.7 (the box is emptied only if it still holds the sent question; review of PR #51, finding 1); edge cases 9, 10, 19, 20
**Dependencies:** Task 1
**Estimated size:** medium

---

## Task 3: Session conversations

**Story:** STORY-002
**Feature:** session-conversations
**Jira:** KAN-72 · branch `feature/KAN-72-chatbot-session-history`
**Description:** `chatbot-storage.ts`: read and write `{ version: 1, userId, conversations }` under
`tsolve.session.chatbot.conversations` in `try/catch`, dropping another user's or broken data. `useConversations`:
the list, the open conversation, the empty "New conversation" (never stored), a new conversation on its first answer
titled with its first question, follow-ups with the last 10 turns. `ConversationList`: New conversation button and
the list newest first by start time, open one highlighted, disabled while an answer is pending.
**Files:**
- Create: `frontend/src/features/chatbot/hooks/chatbot-storage.ts`, `components/ConversationList.tsx`, `*.module.css`
- Modify: `frontend/src/features/chatbot/hooks/useConversations.ts`, `pages/KnowledgeChatbotPage.tsx`
- Test: `frontend/src/features/chatbot/hooks/chatbot-storage.test.ts`,
  `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx`
**Covers:** AC-002.1–AC-002.8, AC-002.11; edge cases 12–18
**Dependencies:** Task 2
**Estimated size:** medium

---

## Task 4: Rate an answer

**Story:** STORY-003, STORY-004
**Feature:** ratings
**Jira:** KAN-76 (Subtask of KAN-75) · branch `feature/KAN-76-chatbot-ratings`
**Description:** One slice, frontend only, with the contract and mocks it needs (KAN-75, user 24/10).
- Contract: `PUT` (`rateChatbotAnswer`, body `ChatbotRating { rating }`) and `DELETE` (`removeChatbotAnswerRating`)
  on `/api/v1/chatbot/answers/{answerId}/rating`, 204 / 401 / 403 / 404 (spec Interface Contracts); regenerate.
- Mocks: two stateless handlers answering 204 (AC-004.6).
- `model.ts`: `ChatTurn.rating?`. `chatbot-storage.ts`: accept a turn with no `rating` or a valid one; drop the data
  for any other value (edge case 26). `useConversations`: `setRating(conversationId, answerId, rating | undefined)`
  that saves like any other change.
- `useRateAnswer`: the two generated mutations with `handlesOwnErrors`; on success `setRating`; on any error but a
  401 MSG06 (design Key Decisions "Rating errors").
- `RatingButtons`: under every answer in `ChatAnswer` (the MSG109 answer included, not the pending one), antd
  `Button`s with `aria-pressed`, the rated one `primary`; click unrated -> `PUT`; click the other -> `PUT`; click the
  highlighted -> `DELETE`; both disabled while a request runs. No comment box.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Create: `frontend/src/features/chatbot/hooks/useRateAnswer.ts`, `components/RatingButtons.tsx`
- Modify: `frontend/src/features/chatbot/model.ts`, `mocks/handlers.ts`, `hooks/chatbot-storage.ts`,
  `hooks/useConversations.ts`, `components/ChatAnswer.tsx` or `ConversationThread.tsx` (wherever the turn and the
  conversation id are known), `pages/KnowledgeChatbotPage.tsx` if it wires `setRating`
- Test: `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx`, `mocks/handlers.test.ts`,
  `hooks/chatbot-storage.test.ts`
**Covers:** AC-003.1–AC-003.6, AC-004.6; edge cases 22–28
**Dependencies:** Task 3 (merged)
**Estimated size:** small (~350 changed lines without generated code)

---

## Task 5: Ids are UUID strings

**Story:** STORY-002
**Feature:** uuid-ids
**Jira:** new Subtask (Hiếu creates it) · ships in the PR of fe-foundation Task 10 · change request KAN-196 Q2
(SPEC-P1-7)
**Description:** In the contract, `WikiDocumentCitation.wikiDocumentId`, the `wikiDocumentIds` query parameter of
`listUnavailableCitedDocuments` and `UnavailableDocuments.wikiDocumentIds` become `{ type: string, format: uuid }`;
regenerate the client (with fe-foundation Task 10, which makes `session.user.id` a `string`). `model.ts`:
`StoredConversations` is `{ version: 2; userId: string; … }`. `chatbot-storage.ts`: write version 2; drop version 1,
a numeric `userId` and a citation whose `wikiDocumentId` is not a string. `useConversations` and `useRateAnswer`:
no `?? 0` number fallback for the user id. `useUnavailableDocuments`, `ConversationThread`, `CitationList`,
`ChatAnswer`: `string` ids and `ReadonlySet<string>`. Mock data and tests use UUID literals.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Modify: `frontend/src/features/chatbot/model.ts`, `hooks/chatbot-storage.ts`, `hooks/useConversations.ts`,
  `hooks/useRateAnswer.ts`, `hooks/useUnavailableDocuments.ts`, `components/ConversationThread.tsx`,
  `components/CitationList.tsx`, `components/ChatAnswer.tsx`, `mocks/data.ts`, `mocks/handlers.ts`
- Test: `frontend/src/features/chatbot/hooks/chatbot-storage.test.ts`, `mocks/handlers.test.ts`,
  `pages/KnowledgeChatbotPage.test.tsx`
**Covers:** AC-002.12
**Dependencies:** fe-foundation Task 10 (same PR)
**Estimated size:** small
