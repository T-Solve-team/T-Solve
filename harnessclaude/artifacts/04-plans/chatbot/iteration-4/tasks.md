---
module: chatbot
date: 2026-11-25
iteration: 4
---

# Tasks: chatbot — iteration 4

Spec: `artifacts/03-modules/chatbot/spec.md` · Design: `artifacts/04-plans/chatbot/design.md`, section "RAG measurement (change of 2026-11-25…)"

Both tasks are one Jira issue KAN-463, one branch `feat/KAN-463-chatbot-rag-eval`
from an up-to-date `main`, one commit per task. Paths: `…/` is `backend/src/main/java/vn/tsolve/chatbot/` (tests:
`backend/src/test/java/vn/tsolve/chatbot/`).

---

## Task 27: Store the sources of each answer

**Story:** STORY-014
**Feature:** answer-sources
**Description:**
- search: `RetrievedSolution.ticketId` and the `nearest` query select `t.public_id` (search AC-010.2, reworded 25/11).
- `ChatbotSource` kind and id; `AnswerSource`; `ChatbotAnswer.sources` (JSON); fill it in `answer` as the design says.
- Changeset `056-chatbot-answer-sources.sql` and its line in `db.changelog-master.xml`.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/056-chatbot-answer-sources.sql`,
  `…/answer/entity/AnswerSource.java`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`,
  `backend/src/main/java/vn/tsolve/search/retrieval/dto/RetrievedSolution.java`,
  `backend/src/main/java/vn/tsolve/search/retrieval/repository/SolutionRetrievalRepository.java`,
  `backend/src/main/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImpl.java`,
  `…/answer/entity/ChatbotAnswer.java`, `…/answer/service/impl/ChatbotSource.java`,
  `…/answer/service/impl/ChatbotAnswerServiceImpl.java`
- Test: `backend/src/test/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImplTest.java`
  (`returnsTicketIdForServerSideOnly_AC_010_2`), `…/answer/service/impl/ChatbotAnswerServiceImplTest.java`
  (`storesKeptSourcesWithCitedFlag_AC_014_1`, `storesKeptSourcesForUncitedNoSource_AC_014_1`,
  `storesJiraAppSources_AC_014_1`, `storesEmptySourcesWithoutSearch_AC_014_2`, `responseHasNoSources_AC_014_3`,
  `failedAskStoresNoSources_AC_014_5`), `…/ChatbotSchemaIT.java` (`sourcesDefaultsToEmptyArray_AC_014_4`,
  `rollbackDropsSources_AC_014_4`)
**Covers:** AC-014.1 – AC-014.5, search AC-010.2
**Dependencies:** —
**Estimated size:** small

---

## Task 28: Offline retrieval evaluation

**Story:** STORY-015
**Feature:** rag-eval-runner
**Description:**
- Move the source merge into `ChatbotRetrieval.rank` and call it from `ask`, `askForProject` and the runner.
- `GoldFile`, `RagEvalReport` (pure), `RagEvalRunner` (`@Profile("rag-eval")`), `application-rag-eval.yml`.
- `backend/rag-eval/gold.example.jsonl`.
**Files:**
- Create: `…/answer/service/ChatbotRetrieval.java`, `…/eval/GoldFile.java`, `…/eval/RagEvalReport.java`,
  `…/eval/RagEvalRunner.java`, `backend/src/main/resources/application-rag-eval.yml`,
  `backend/rag-eval/gold.example.jsonl`
- Modify: `…/answer/service/impl/ChatbotAnswerServiceImpl.java`, `…/answer/service/impl/ChatbotSource.java`
- Modify (off switches for the `rag-eval` run, AC-015.7; see decisions.md): `identity/dev/DevAccountSeeder.java`,
  `identity/mock/MockUserListSeeder.java`, `platform/registry/RegistryDataSourceConfig.java`,
  `search/embedding/service/impl/EmbeddingCatchUpJob.java`, `ticket/service/impl/PreviewCleanupJob.java`
- Test: `…/eval/GoldFileTest.java` (`parsesLinesAndGroups_AC_015_2`, `rejectsInvalidLineByNumberOnly_AC_015_5`,
  `rejectsEmptyFile_AC_015_5`, `exampleFileIsValid_AC_015_8`), `…/eval/RagEvalReportTest.java`
  (`reportsRankSimilarityAndTop_AC_015_3`, `scoresRecallWithinKeptSources_AC_015_4`,
  `scoresMrrAndNoSourceRate_AC_015_4`, `scoresEachGroupValue_AC_015_4`), `…/eval/RagEvalRunnerTest.java`
  (`retrievesAsFirstWebQuestion_AC_015_1`, `failsBeforeAiForUnknownUser_AC_015_5`,
  `embeddingFailureWritesNoReport_AC_015_6`, `callsNoChatModelAndStoresNothing_AC_015_7`); the existing AC-005.2
  tests of `ChatbotAnswerServiceImplTest` still pass through `ChatbotRetrieval`
**Covers:** AC-015.1 – AC-015.8
**Dependencies:** Task 27 (ticket ids)
**Estimated size:** medium

---

## Task 29: Cited Solutions with null Symptoms or Scope (KAN-474)

**Story:** STORY-001
**Feature:** nullable-cited-sections
**Description:** AC-001.11, `CitedSolution` (2026-11-27): symptoms and scopeOfApplicability may be null; the LLM source
text and the web citation leave a null section out.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/chatbot/answer/service/impl/ChatbotSource.java`, `frontend/openapi/tsolve-api.yaml`, `frontend/src/features/chatbot/components/TicketSolution.tsx` (if needed)
- Test: `ChatbotSourceTest`, `TicketSolution` test
**Dependencies:** search Task 27
**Estimated size:** small
