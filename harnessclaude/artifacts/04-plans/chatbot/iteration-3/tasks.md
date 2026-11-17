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

## Task 16: Store web conversations on the backend

**Story:** STORY-013, STORY-007, STORY-010
**Feature:** conversation-store
**Jira:** KAN-183 · branch `feat/KAN-183-chatbot-persist-sessions` (from `main` once KAN-243 is merged; it needs the five outcomes)
**Description:**
- Changeset `047-chat-session.sql` (author `chatbot`): `chat_session` and `chat_message` as spec Interface Contracts,
  indexes, `--rollback` drops both (AC-013.11).
- Contract (`frontend/openapi/tsolve-api.yaml`): `askChatbot` takes the new `ChatbotAsk`; `ChatbotAnswer` gains optional
  `chatSessionId`; 404 on `askChatbot`; `draftChatbotIssue` loses its body and `ChatbotIssueDraftRequest`; the Jira App
  path keeps `ChatbotQuestion`. `npm run generate:api`.
- `ChatbotAnswerController.ask` takes `ChatbotAskRequest(question, chatSessionId)`; the service loads the own session
  or 404 MSG04 (AC-013.4), reads the last 10 messages as history (AC-013.2), and after the answer saves
  `chatbot_answer`, `chat_message` and `last_message_at` in one short transaction (AC-013.1, AC-013.3).
  `askForProject` keeps `ChatbotQuestionRequest` and touches no session (AC-013.12). No `jiraapp` file changes.
- `ChatbotIssueServiceImpl.draft`: the turns before the answer's message in its conversation (none when the answer has
  no message), no request body (AC-010.3, AC-010.8).
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/047-chat-session.sql`,
  `backend/src/main/java/vn/tsolve/chatbot/conversation/entity/ChatSession.java`, `…/entity/ChatMessage.java`,
  `…/repository/ChatSessionRepository.java`, `…/repository/ChatMessageRepository.java`,
  `backend/src/main/java/vn/tsolve/chatbot/answer/dto/ChatbotAskRequest.java`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`, `frontend/openapi/tsolve-api.yaml`,
  `frontend/src/shared/api/generated/**` (regenerated), `…/chatbot/answer/controller/ChatbotAnswerController.java`,
  `…/answer/dto/ChatbotAnswerResponse.java`, `…/answer/service/ChatbotAnswerService.java`,
  `…/answer/service/impl/ChatbotAnswerServiceImpl.java`, `…/issue/controller/ChatbotIssueController.java`,
  `…/issue/service/impl/ChatbotIssueServiceImpl.java`
- Test: `ChatSessionSchemaIT` (`AC_013_11`), `ChatbotConversationStoreIT` (`newConversation_AC_013_1`,
  `historyFromConversation_AC_013_2`, `storesMessageWithAnswer_AC_013_3`, `unknownSession_AC_013_4`,
  `jiraPathNoSession_AC_013_12`), updated `ChatbotAnswerServiceImplTest` (`AC_007_1`–`AC_007_3`),
  `ChatbotIssueServiceImplTest` (`AC_010_3`, `AC_010_8`)
**Covers:** AC-013.1 – AC-013.4, AC-013.11, AC-013.12, AC-007.1 – AC-007.3, AC-010.3, AC-010.8
**Dependencies:** Task 14 (KAN-243 merged)
**Estimated size:** large

---

## Task 17: List, open and delete conversations; retention job

**Story:** STORY-013
**Feature:** conversation-api
**Jira:** KAN-183 · same branch
**Description:**
- `ChatbotConversationController`: `listChatbotConversations`, `getChatbotConversation`, `deleteChatbotConversation`,
  `authenticated()`; path id as `String`, not a UUID → 404 MSG04 (AC-013.5 – AC-013.8, AC-013.10). Contract paths and
  schemas added; `generate:api`.
- Delete (service, one transaction): collect the session's `chatbot_answer_id`s, delete the session (messages cascade),
  then `DELETE FROM chatbot_answer WHERE id IN (:ids) AND rating IS NULL AND outcome IN ('ANSWERED','GREETING','OFF_TOPIC')`
  (AC-013.7).
- `ConversationRetentionJob` `@Scheduled(cron = "${CHATBOT_CONVERSATION_CLEANUP_CRON:0 0 2 * * *}", zone = "Asia/Ho_Chi_Minh")`:
  each session with `last_message_at` before now − `CHATBOT_CONVERSATION_RETENTION` (default 30d) deleted as above, one
  transaction per session, a failure logged by id only (AC-013.9). `ponytail:` comment: runs in the process's database
  only until `worker` runs recurring jobs per Workspace. `.env.example` gains both settings.
**Files:**
- Create: `…/chatbot/conversation/controller/ChatbotConversationController.java`, `…/conversation/dto/*`,
  `…/conversation/service/ChatbotConversationService.java`, `…/conversation/service/impl/ChatbotConversationServiceImpl.java`,
  `…/conversation/service/impl/ConversationRetentionJob.java`
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`, `backend/.env.example`,
  `…/conversation/repository/ChatSessionRepository.java`
- Test: `ChatbotConversationIT` (`list_AC_013_5`, `open_AC_013_6`, `delete_AC_013_7`, `unknown_AC_013_8`,
  `noSession_AC_013_10`), `ConversationRetentionJobIT` (`AC_013_9`)
**Covers:** AC-013.5 – AC-013.10
**Dependencies:** Task 16
**Estimated size:** medium

---

## Task 18: Saved conversations in the web app

**Story:** STORY-002, STORY-001, STORY-003, STORY-004
**Feature:** conversation-ui
**Jira:** KAN-183 · same branch
**Description:**
- `useConversations` becomes TanStack Query: `useListChatbotConversations` for the list, `useGetChatbotConversation`
  for the open one; the ask sends `{ question, chatSessionId }` and, on success, invalidates the list and sets the
  open id from the answer (AC-001.6, AC-002.2 – AC-002.5, AC-002.7). Delete `chatbot-storage.ts` (+ test) and
  `toHistory`; `useRateAnswer` updates the open conversation's query data (AC-003.4).
- `ConversationList`: a Delete icon button (`aria-label="Delete"`) per row, MSG103 confirm, MSG06 on failure, all
  disabled while an answer is pending (AC-002.13 – AC-002.15); list loading = `Spin`, error = MSG06 + Retry
  (AC-002.8).
- Mocks: conversations in memory per AC-004.8 (lost on a page reload in mock mode only).
**Files:**
- Modify: `frontend/src/features/chatbot/hooks/useConversations.ts`, `…/hooks/useAskQuestion.ts`, `…/hooks/useRateAnswer.ts`,
  `…/model.ts`, `…/components/ConversationList.tsx`, `…/pages/KnowledgeChatbotPage.tsx`, `…/mocks/data.ts`,
  `…/mocks/handlers.ts`
- Delete: `…/hooks/chatbot-storage.ts`, `…/hooks/chatbot-storage.test.ts`
- Test: `…/pages/KnowledgeChatbotPage.test.tsx` (`AC-001.6`, `AC-002.x`, `AC-003.4`), `…/mocks/handlers.test.ts` (`AC-004.8`)
**Covers:** AC-001.6, AC-002.1 – AC-002.15, AC-003.4, AC-004.8
**Dependencies:** Task 17 (generated client)
**Estimated size:** large
