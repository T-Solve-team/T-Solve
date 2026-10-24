---
module: chatbot
date: 2026-10-23
total_tasks: 3
owner: Dev
---

# Tasks: chatbot — iteration 1

Spec: `artifacts/03-modules/chatbot/spec.md` · Design: `artifacts/04-plans/chatbot/design.md`

One task = one Jira Subtask of KAN-48 = one branch = one PR, merged in this order. Each task is one vertical slice
a user can try: it carries every layer it needs (contract, mocks, hooks, components, tests). All three together build
KAN-48 (UC-51, UC-52, frontend with mocks). STORY-003 (UC-53 ratings) and the backend are not in this iteration.
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
