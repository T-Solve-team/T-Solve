---
module: chatbot
date: 2026-11-06
iteration: 2
total_tasks: 5
owner: Dev
---

# Tasks: chatbot — iteration 2 (backend)

Spec: `artifacts/03-modules/chatbot/spec.md` · Design: `artifacts/04-plans/chatbot/design.md` ("Backend (change of
2026-11-06)") · the search `SolutionRetrieval` is search Task 19 (`artifacts/04-plans/search/iteration-1/tasks.md`).

Backend tasks follow `backend/AGENTS.md`: test first, test names end with the AC id (`…_AC_005_1`), no Docker in the
Codex sandbox (the controller runs the ITs and `mvn clean verify -DargLine="-Dapi.version=1.44"`). One Jira issue =
one branch, stacked: `feat/KAN-179-chatbot-answers` (from `feat/KAN-141-search-embedding`, carries this plan) →
`feat/KAN-180-chatbot-citations` → `feat/KAN-181-chatbot-follow-up`. No mock data is added or changed.

Order: search Task 19 → Task 6 → Task 7 → Task 8 → Task 9 → Task 10.

## Task 6: The answer table

**Story:** STORY-005
**Feature:** chatbot-answer-schema
**Jira:** KAN-179 · branch `feat/KAN-179-chatbot-answers`
**Description:**
- Changeset `013-chatbot-answer.sql` (author `chatbot`, id `013-chatbot-answer`) creating `chatbot_answer` as the spec
  Interface Contracts, `--rollback DROP TABLE chatbot_answer;`, appended to `db.changelog-master.xml` after 012.
- `ChatbotAnswer` entity and `ChatbotAnswerRepository`.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/013-chatbot-answer.sql`,
  `…/chatbot/answer/entity/ChatbotAnswer.java`, `…/chatbot/answer/repository/ChatbotAnswerRepository.java`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Test: `…/test/chatbot/ChatbotSchemaIT.java` (`createsAnswerTableWithTenantConvention_AC_005_11`,
  `refusesUnknownOutcome_AC_005_11`, `rollsBackTheAnswerTable_AC_005_11`)
**Covers:** AC-005.11
**Dependencies:** none
**Estimated size:** small

---

## Task 7: Ask a question and get an answer

**Story:** STORY-005
**Feature:** ask-answer-api
**Jira:** KAN-179 · branch `feat/KAN-179-chatbot-answers`
**Description:**
- `WikiSources` port and `NoWikiSources` (no passage; every id unavailable) — the port is needed by the pipeline.
- `ChatbotAnswerController` `POST /api/v1/chatbot/answers` (`@Tag("chatbot")`, `askChatbot`, `authenticated()`),
  `ChatbotQuestionRequest` (`question` `@NotBlank` MSG01, `@Size(max = 1000)` MSG02; `history` accepted as the
  contract says, its own checks are Task 10), `ChatbotAnswerResponse`, `ChatbotCitation` sealed with the two records
  and `CitedSolution` (citations are filled by Task 8; until then an `ANSWERED` answer carries every kept source).
- `ChatbotAnswerServiceImpl`: embed once, `SolutionRetrieval.nearest` + `WikiSources.nearest` with
  `CHATBOT_MIN_SIMILARITY` (0.7), merge and keep `CHATBOT_SOURCE_LIMIT` (5), number them; none → `NO_SOURCE`
  without a chat call; `AnswerPrompt` → `LlmClient.chat(messages, null)`; no `[n]` of a kept source in the text →
  `NO_SOURCE`; save one row; the AI part under `CompletableFuture.orTimeout(CHATBOT_ASK_TIMEOUT)` (60 s) →
  `AiUnavailableException` (503 MSG06), nothing saved.
- `.env.example`: `CHATBOT_MIN_SIMILARITY=0.7`, `CHATBOT_SOURCE_LIMIT=5`, `CHATBOT_ASK_TIMEOUT=60s`.
**Files:**
- Create: `…/chatbot/answer/controller/ChatbotAnswerController.java`, `…/chatbot/answer/dto/*.java`,
  `…/chatbot/answer/service/ChatbotAnswerService.java`, `…/chatbot/answer/service/impl/ChatbotAnswerServiceImpl.java`,
  `…/chatbot/answer/service/impl/ChatbotSource.java`, `…/chatbot/answer/service/impl/AnswerPrompt.java`,
  `…/chatbot/source/service/WikiSources.java`, `…/chatbot/source/service/impl/NoWikiSources.java`,
  `backend/src/main/java/vn/tsolve/chatbot/package-info.java`
- Modify: `.env.example`
- Test: `…/test/chatbot/answer/controller/ChatbotAnswerControllerTest.java` (`rejectsBlankQuestion_AC_005_8`,
  `rejectsLongQuestion_AC_005_8`, `answers401WithoutSession_AC_005_9`, `letsAnySignedInUserAsk_AC_005_9`,
  `ignoresWorkspaceParameter_AC_005_10`), `…/test/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java`
  (`embedsOnceAndAsksBothSources_AC_005_1`, `keepsTopSourcesNumbered_AC_005_2`, `answersNoSourceWithoutChat_AC_005_4`,
  `answersNoSourceWhenNoMarker_AC_005_5`, `storesOneRowAndReturnsItsId_AC_005_6`, `answers503AndStoresNothing_AC_005_7`,
  `timesOutTheWholeAsk_AC_005_7`, `logsNoContent_AC_005_10`), `…/test/chatbot/answer/service/impl/AnswerPromptTest.java`
  (`tagsSourcesAndQuestionAsData_AC_005_3`)
**Covers:** AC-005.1 – AC-005.10 (edge cases B2–B4, B10)
**Dependencies:** search Task 19, Task 6
**Estimated size:** medium

---

## Task 8: Citations

**Story:** STORY-006
**Feature:** answer-citations
**Jira:** KAN-180 · branch `feat/KAN-180-chatbot-citations`
**Description:**
- `Citations`: the kept sources whose `[n]` (single integer) appears in the text, in number order; markers of an
  unknown number removed from the text before it is saved and returned; ticket source → `TicketSolutionCitation`
  (workaround null when blank), Wiki → `WikiDocumentCitation`.
**Files:**
- Create: `…/chatbot/answer/service/impl/Citations.java`
- Modify: `…/chatbot/answer/service/impl/ChatbotAnswerServiceImpl.java`
- Test: `…/test/chatbot/answer/service/impl/CitationsTest.java` (`listsOnlyCitedSourcesInOrder_AC_006_1`,
  `removesUnknownMarkers_AC_006_2`, `mapsTicketSourceToFiveSections_AC_006_3`, `mapsWikiSourceToDocument_AC_006_4`),
  `…/test/chatbot/answer/controller/ChatbotAnswerControllerTest.java` (`writesCitationTypeDiscriminator_AC_006_1`)
**Covers:** AC-006.1 – AC-006.4 (edge cases B1, B5, B6)
**Dependencies:** Task 7
**Estimated size:** small

---

## Task 9: Unavailable cited documents

**Story:** STORY-006
**Feature:** cited-documents
**Jira:** KAN-180 · branch `feat/KAN-180-chatbot-citations`
**Description:**
- `UnavailableDocumentsController` `GET /api/v1/chatbot/unavailable-documents` (`listUnavailableCitedDocuments`,
  `authenticated()`): 1..50 UUID `wikiDocumentIds` (set), else 400 MSG06; answers `WikiSources.unavailable`.
**Files:**
- Create: `…/chatbot/source/controller/UnavailableDocumentsController.java`
- Test: `…/test/chatbot/source/controller/UnavailableDocumentsControllerTest.java` (`listsWhatThePortReturns_AC_006_5`,
  `rejectsMissingTooManyOrBadIds_AC_006_6`, `answers401WithoutSession_AC_006_6`),
  `…/test/chatbot/source/service/impl/NoWikiSourcesTest.java` (`failsClosed_AC_006_7`)
**Covers:** AC-006.5 – AC-006.7 (edge case B11)
**Dependencies:** Task 7
**Estimated size:** small

---

## Task 10: Follow-up questions and the real contract

**Story:** STORY-007
**Feature:** follow-up-context
**Jira:** KAN-181 · branch `feat/KAN-181-chatbot-follow-up`
**Description:**
- `history` checks: missing, > 10 turns, a question > 1,000 or an answer > 10,000 characters → 400 MSG06 on
  `history`, no AI, nothing stored.
- Retrieval text: last turn's trimmed question, a line break, the trimmed new question (`ponytail:` comment on the
  missing LLM rewrite).
- `AnswerPrompt`: the turns oldest first as tagged user / assistant messages, never numbered or cited; only the new
  question is stored.
- Contract: compare the backend with `askChatbot`, `listUnavailableCitedDocuments` and their schemas in
  `frontend/openapi/tsolve-api.yaml`; change the yaml and regenerate only if they differ; `npm run typecheck`.
**Files:**
- Modify: `…/chatbot/answer/dto/ChatbotQuestionRequest.java`, `…/chatbot/answer/service/impl/ChatbotAnswerServiceImpl.java`,
  `…/chatbot/answer/service/impl/AnswerPrompt.java`
- Modify (only on drift): `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
- Test: `…/test/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java` (`prependsLastQuestionForRetrieval_AC_007_1`,
  `storesOnlyTheNewQuestion_AC_007_4`), `…/test/chatbot/answer/service/impl/AnswerPromptTest.java`
  (`sendsHistoryAsContextNeverAsSource_AC_007_2`), `…/test/chatbot/answer/controller/ChatbotAnswerControllerTest.java`
  (`rejectsBadHistory_AC_007_3`)
**Covers:** AC-007.1 – AC-007.4 (edge cases B8, B9)
**Dependencies:** Tasks 8, 9
**Estimated size:** small
