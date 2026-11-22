---
module: chatbot
date: 2026-11-17
iteration: 3
---

# Tasks: chatbot — iteration 3

Spec: `artifacts/03-modules/chatbot/spec.md` · Design: `artifacts/04-plans/chatbot/design.md`, section "Question classification (change of 2026-11-17…)"

All three tasks are Jira KAN-243, so one branch `feat/KAN-243-chatbot-classify-questions` from an up-to-date
`main`, one commit per task. Paths: `…/` is `backend/src/main/java/vn/tsolve/chatbot/` (tests:
`backend/src/test/java/vn/tsolve/chatbot/`).

---

## Task 13: New outcomes in the schema and the contract

**Story:** STORY-009
**Feature:** classify-outcome-schema
**Jira:** KAN-243 · branch `feat/KAN-243-chatbot-classify-questions`
**Description:**
- Changeset `046-chatbot-answer-outcomes.sql` (next free number): drop `ck_chatbot_answer_outcome`, re-add it with
  `ANSWERED`, `NO_SOURCE`, `GREETING`, `OFF_TOPIC`, `INJECTION`; `--rollback` restores the two-value check.
- `ChatbotAnswerResponse.Outcome` gains the three values; `frontend/openapi/tsolve-api.yaml` `ChatbotAnswer.outcome`
  enum too; `npm run generate:api` in `frontend/`.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/046-chatbot-answer-outcomes.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `…/answer/dto/ChatbotAnswerResponse.java`,
  `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (generated)
- Test: `…/ChatbotSchemaIT.java` (`acceptsClassifiedOutcomes_AC_009_8`, `rejectsUnknownOutcome_AC_009_8`)
**Covers:** AC-009.8 (edge case Q9)
**Dependencies:** —
**Estimated size:** small

---

## Task 14: Classify and rewrite before the search

**Story:** STORY-009, STORY-005, STORY-007
**Feature:** classify-question
**Jira:** KAN-243 · branch `feat/KAN-243-chatbot-classify-questions`
**Description:**
- `QuestionClassifier`: greeting set and normalization (AC-009.1), `parse` of the classifier JSON with fail-open
  (AC-009.6, case-insensitive category).
- `ClassifierPrompt`: system message, tagged history and question, `CLASSIFIER_SCHEMA` (AC-009.2).
- `ChatbotAnswerServiceImpl.ask`: greeting check, then the classifier call under the existing deadline, then save a
  classified row or continue with `query` as retrieval text (AC-009.3–AC-009.5, AC-009.7, AC-005.1, AC-007.1).
  Replace the `ponytail:` comment on the retrieval text.
- Update the existing tests whose fake `LlmClient` now gets two calls for a question.
**Files:**
- Create: `…/answer/service/impl/QuestionClassifier.java`, `…/answer/service/impl/ClassifierPrompt.java`
- Modify: `…/answer/service/impl/ChatbotAnswerServiceImpl.java`
- Test: `…/answer/service/impl/QuestionClassifierTest.java` (`matchesGreetingPhrases_AC_009_1`,
  `normalizesPunctuationEmojiAndCase_AC_009_1`, `parsesCategoryAndQuery_AC_009_2`, `failsOpenOnBadJson_AC_009_6`,
  `failsOpenOnUnknownCategoryOrBlankQuery_AC_009_6`), `…/answer/service/impl/ClassifierPromptTest.java`
  (`tagsHistoryAndQuestionAsData_AC_009_2`), `…/answer/service/impl/ChatbotAnswerServiceImplTest.java`
  (`greetingCallsNoAi_AC_009_1`, `refusesOffTopicAndInjectionWithoutSearch_AC_009_3`,
  `storesClassifiedRowWithEmptyText_AC_009_4`, `searchesWithQueryAnswersWithQuestion_AC_009_5`,
  `fallsBackToTodaysRetrievalText_AC_009_6`, `classifierAiUnavailableFailsOpen_AC_009_6`, `classifierTimeoutIs503StoresNothing_AC_009_7`,
  `classifierCalledOnceBeforeEmbeddingWithSchema_AC_009_2`, `classifierGreetingAnswersGreeting_AC_009_3`,
  `neverLogsQuery_AC_009_5`,
  `embedsQueryOnce_AC_005_1`, `followUpFallbackPrependsLastQuestion_AC_007_1`),
  `…/ChatbotAnswerTimeoutIT.java` (`classifierCountsAgainstDeadline_AC_009_7`)
**Covers:** AC-009.1 – AC-009.7, AC-005.1, AC-007.1 (edge cases Q1, Q2, Q5, Q6, Q8)
**Dependencies:** Task 13
**Estimated size:** medium

---

## Task 15: Show the classified answers in the web app

**Story:** STORY-009, STORY-001, STORY-003, STORY-004
**Feature:** classify-question-ui
**Jira:** KAN-243 · branch `feat/KAN-243-chatbot-classify-questions`
**Description:**
- fe-foundation AC-006.7 (reworded 17/11) allows them: MSG_NEW_5, MSG_NEW_6, MSG_NEW_7 in `frontend/src/shared/messages/catalog.ts` with the
  spec texts.
- `ChatAnswer.tsx`: outcome → text or message (AC-001.15); rating buttons only for `ANSWERED` / `NO_SOURCE`
  (AC-003.1); `chatbot-storage.ts` accepts the five outcomes (Q7).
- Mock `handlers.ts` / `data.ts`: the AC-004.7 answers before the keyword answers.
- Frontend `verify` passes; `generate:api` leaves no diff.
**Files:**
- Modify: `frontend/src/shared/messages/catalog.ts`, `frontend/src/shared/messages/catalog.test.ts` (AC-006.7 list), `frontend/src/features/chatbot/components/ChatAnswer.tsx`,
  `frontend/src/features/chatbot/mocks/data.ts`,
  `frontend/src/features/chatbot/hooks/chatbot-storage.ts`, `frontend/src/features/chatbot/mocks/handlers.ts`
- Test: `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx` (`AC-001.15 …`, `AC-003.1 …`),
  `frontend/src/features/chatbot/hooks/chatbot-storage.test.ts` (keeps a stored classified turn),
  `frontend/src/features/chatbot/mocks/handlers.test.ts` (`AC-004.7 …`)
**Covers:** AC-001.15, AC-003.1, AC-004.7 (edge case Q7)
**Dependencies:** Task 13 (generated enum)
**Estimated size:** small

---

## Task 16: One combined answer from the sources

**Story:** STORY-005
**Feature:** combined-answer-prompt
**Jira:** KAN-380 · branch `feature/KAN-380-chatbot-combined-answer`
**Description:**
- `AnswerPrompt.SYSTEM`: ask for one answer that merges what the sources say (the steps to take), never a walk
  through the sources one by one; the citation rules stay as they are (AC-005.3, AC-006.8).
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/chatbot/answer/service/impl/AnswerPrompt.java`
- Test: `backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/AnswerPromptTest.java`
  (`asksForOneCombinedAnswer_AC_005_3`)
**Covers:** AC-005.3
**Dependencies:** none
**Estimated size:** small

---

## Task 17: Show the cited Solutions open

**Story:** STORY-001
**Feature:** open-solution-citations
**Jira:** KAN-380 · branch `feature/KAN-380-chatbot-combined-answer`
**Description:**
- `CitationList.tsx`: a ticket Solution citation shows "[n] Solution from a ticket" and its `TicketSolution` under
  it, with no Show/Hide button (AC-001.10, AC-001.11). Wiki citations unchanged.
- Frontend `verify` passes; `generate:api` leaves no diff (no contract change).
**Files:**
- Modify: `frontend/src/features/chatbot/components/CitationList.tsx`
- Test: `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx` (`AC-001.10 …`, `AC-001.11 …`)
**Covers:** AC-001.10, AC-001.11
**Dependencies:** none
**Estimated size:** small

---

## Task 18: An AI title for a new conversation

**Story:** STORY-009, STORY-002
**Feature:** conversation-title-api
**Jira:** KAN-380 · branch `feature/KAN-380-chatbot-combined-answer`
**Description:**
- Contract first: `ChatbotAnswer.title` (optional string) in `frontend/openapi/tsolve-api.yaml`; `generate:api`.
- `ClassifierPrompt`: schema gains `title`; the system message asks for it when there are no earlier turns.
- `QuestionClassifier.Classification` carries the title; `ChatbotAnswerServiceImpl` returns it for a first
  `QUESTION` only, white space collapsed, at most 60 characters (`ChatbotAnswerResponse.title`, omitted when null).
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `backend/…/chatbot/answer/dto/ChatbotAnswerResponse.java`,
  `…/service/impl/ClassifierPrompt.java`, `…/service/impl/QuestionClassifier.java`,
  `…/service/impl/ChatbotAnswerServiceImpl.java`
- Test: `ClassifierPromptTest` (`asksForTitleOnFirstQuestion_AC_009_9`), `QuestionClassifierTest`
  (`readsTitle_AC_009_9`), `ChatbotAnswerServiceImplTest` (`returnsTitleOnFirstQuestionOnly_AC_009_9`)
**Covers:** AC-009.9
**Dependencies:** none
**Estimated size:** small

---

## Task 19: Show the AI title on one line

**Story:** STORY-002
**Feature:** conversation-title-ui
**Jira:** KAN-380 · branch `feature/KAN-380-chatbot-combined-answer`
**Description:**
- `Conversation` keeps `title` (from the first answer, else the first question), stored in session storage; an
  older stored conversation without one falls back to its first question.
- `ConversationList`: one line, CSS ellipsis, antd `Tooltip` with the whole title (hover and focus).
- Mock answers carry a `title` on a first question.
**Files:**
- Modify: `frontend/src/features/chatbot/model.ts`, `…/hooks/useConversations.ts`, `…/hooks/chatbot-storage.ts`,
  `…/components/ConversationList.tsx`, `…/components/Conversation.module.css`, `…/mocks/handlers.ts`
- Test: `KnowledgeChatbotPage.test.tsx` (`AC-002.2 …`, `AC-002.13 …`), `chatbot-storage.test.ts`
**Covers:** AC-002.2, AC-002.13
**Dependencies:** Task 18 (generated type)
**Estimated size:** small
