---
module: chatbot
feature: chatbot-answer-schema, ask-answer-api (and search solution-retrieval)
task: 6, 7 (and search Task 19)
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: chatbot — Tasks 6 and 7 (answer table, ask a question) and search Task 19 (Solution retrieval) (KAN-179, chatbot STORY-005, search STORY-010)

## Summary
I reviewed the uncommitted change against `a0c486d` on `feat/KAN-179-chatbot-answers` (worktree
`T-Solve-kan179`). It adds search's `SolutionRetrieval` (STORY-010), the `013-chatbot-answer.sql` changeset with its
entity and repository (AC-005.11), and `POST /api/v1/chatbot/answers` with the `WikiSources` port and the fail-closed
`NoWikiSources` (AC-005.1 to AC-005.10). It also changes `SearchChangesetRollbackIT` so that its rollback boundary is
changeset `008-search-public-ids` and not the count of `search` changesets. I checked each criterion against the
code and its test. I checked the JSON shape against `frontend/openapi/tsolve-api.yaml`, the module boundary against
`backend/AGENTS.md`, and the thread model against the design. There are no CRITICAL findings. Tasks 8 to 10 are
deliberately not done (see Assumption 2). Verdict: PASS.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Retrieval (`SolutionRetrievalRepository.java:13-23`) is a native query with bound parameters only (`:vector` through `PgVector.literal` and `CAST(... AS vector)`, `:minSimilarity`, `:limit`). It filters `t.status = 'PUBLISHED' AND d.status = 'ACTIVE'` and the similarity cut-off, and orders by distance, then `t.id`. It has no user or Department filter. `SolutionRetrievalImpl.java:22-29` checks the arguments before any query, and `:35` maps a blank Workaround to null. The ask pipeline (`ChatbotAnswerServiceImpl.java:100-136`) trims the question and embeds it once on the executor. Both sources and the save run on the request thread. The service merges the sources by similarity, a Solution first on a tie (stable sort), keeps `CHATBOT_SOURCE_LIMIT` and numbers them 1..n. With no source it saves `NO_SOURCE` and makes no chat call (`:125-127`). With no `[n]` marker of a kept source it saves `NO_SOURCE` with `""` (`:131-133`). It uses one deadline. `callAi` (`:154-164`) applies `orTimeout(remaining)` and turns any failure into an `AiUnavailableException` with no cause. The `remaining()` checks after each step make a late ask fail before `save`, so a 503 stores nothing. The prompt (`AnswerPrompt.java`) has the system rules of AC-005.3. It escapes `& < > "` in every source text and in the question, so an injected `</question>` stays data. |
| Completeness | pass | AC-005.1 to AC-005.11 and AC-010.1 to AC-010.5 each have a named test (see Traceability). The parts that depend on Tasks 8 to 10 are deliberately absent (Assumption 2). |
| Correctness | pass | The controller ran 29 unit tests, and they pass. The ITs (`ChatbotSchemaIT`, `SolutionRetrievalRepositoryIT`, `SearchChangesetRollbackIT`) run in the controller's separate `mvn verify`. I did not run them (Assumption 1). |
| Integration | pass | The JSON matches the yaml. The path, `operationId` `askChatbot` and tag `chatbot` match. `ChatbotCitation` (`@JsonTypeInfo(property = "type")`, `WIKI_DOCUMENT` / `TICKET_SOLUTION`) gives `{number, type, wikiDocumentId, title}` and `{number, type, solution}`. `CitedSolution` has the 5 sections, and `workaround` is serialized as null; there is no global `non_null` inclusion. The controller test checks `$.citations[*].type` and that `workaround` is null. The module boundary holds: chatbot imports from search only `search.retrieval.service.SolutionRetrieval` and `search.retrieval.dto.RetrievedSolution`. The `@Value` defaults follow the existing `ai` pattern, and `.env.example` lists the three settings. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-010.1 nearest, cut-off, order, tie-breaker | PASS | `SolutionRetrievalRepositoryIT.returnsNearestAboveCutOff_AC_010_1` (limit 2 and 5, ties in id order, similarity 1 at the cut-off 1) |
| 2 | AC-010.2 sections only, blank workaround null | PASS | `SolutionRetrievalImplTest.returnsSectionsWithoutTicketDetails_AC_010_2` (it also checks the record components) |
| 3 | AC-010.3 unpublished / inactive / far left out | PASS | `leavesOutUnpublishedInactiveOrFar_AC_010_3` (an `UNPUBLISHED` ticket, a `DELETED` department, an orthogonal vector) |
| 4 | AC-010.4 no user, no embedding, no `search_log` | PASS | `takesNoUserAndEmbedsNothing_AC_010_4` (reflection on the method and the fields); the IT asserts that `search_log` is empty |
| 5 | AC-010.5 bad arguments, no query | PASS | `rejectsBadArguments_AC_010_5` (vector null/0/1023/1025 and NaN; min -0.1/1.1/NaN/Inf; limit 0); `verifyNoInteractions(repository)` |
| 6 | AC-005.1 embed once, both sources, threshold | PASS | `embedsOnceAndAsksBothSources_AC_005_1` (the trimmed text, 0.7, 5; checks which thread each call runs on) |
| 7 | AC-005.2 top N, numbered | PASS | `keepsTopSourcesNumbered_AC_005_2` (a tie between a Solution and a Wiki passage keeps the Solution first; the 4th source is dropped) |
| 8 | AC-005.3 tags as data | PASS (turns: Task 10) | `AnswerPromptTest.tagsSourcesAndQuestionAsData_AC_005_3` (escaped injection) |
| 9 | AC-005.4 no source, no chat | PASS | `answersNoSourceWithoutChat_AC_005_4` (`verifyNoInteractions(llm)`, row `NO_SOURCE` with `""`) |
| 10 | AC-005.5 no kept marker → NO_SOURCE | PASS | `answersNoSourceWhenNoMarker_AC_005_5` (`[9]`, `[01] [ 1 ] [0] [1, 2]`, `[100]`, none) |
| 11 | AC-005.6 one row, id returned | PASS | `storesOneRowAndReturnsItsId_AC_005_6` (user, trimmed question, outcome, text; saved on the request thread) |
| 12 | AC-005.7 503, nothing stored, no retry, deadline | PASS | `answers503AndStoresNothing_AC_005_7` (embed or chat fails, `times(1)`), `timesOutTheWholeAsk_AC_005_7` (an embed or a chat that stalls), controller 503 MSG06 |
| 13 | AC-005.8 MSG01 / MSG02, no AI | PASS | controller `rejectsBlankQuestion_AC_005_8` (missing, null, blank), `rejectsLongQuestion_AC_005_8`; `verifyNoInteractions(answers)` |
| 14 | AC-005.9 401 MSG07; any role | PASS | `answers401WithoutSession_AC_005_9`, `letsAnySignedInUserAsk_AC_005_9` (every `Role`) |
| 15 | AC-005.10 no Workspace input; no content in logs | PASS | `ignoresWorkspaceParameter_AC_005_10` (query, header and body field); `logsNoContent_AC_005_10`. By reading: the chatbot and the retrieval code have no logger, `OllamaHttp` logs only op/model/duration/error class, `AiUnavailableException` carries no cause, and the IAE messages are generic |
| 16 | AC-005.11 table and rollback | PASS | `013-chatbot-answer.sql` matches the contract; `ChatbotSchemaIT` (columns, NOT NULL, `timestamptz now()`, `gen_random_uuid()`, FK, `ck_chatbot_answer_outcome`, rollback of 013 only) |

### Stage 2 — Quality

CRITICAL: none.

Informational (does not block):
1. Low/Medium. [`ChatbotAnswerServiceImpl.java:154-164`] `orTimeout` does not cancel the work. As the design
   accepts, a stalled chat keeps a thread of `applicationTaskExecutor` and an `OllamaHttp` permit until
   `AI_CHAT_TIMEOUT`. A task that was still queued when the deadline passed still runs later. The executor is shared
   with `TicketsPublishedListener` (Boot default: 8 core threads, an unbounded queue). Under load, late asks call
   Ollama after their 503 has been sent. Fix, optional: skip the call in the supplier when `System.nanoTime() >=
   deadline`.
2. Low. [`ChatbotQuestionRequest.java:10`] `@Size(max = 1000)` measures the untrimmed question, in UTF-16 units.
   A question of 1,000 characters with spaces around it gets MSG02. The spec says "longer than 1,000 characters"
   (Assumption 3).
3. Low. [`ChatbotAnswerServiceImplTest.java:240`] The test expects the ask to end in under 900 ms with a budget of
   600 ms. The 300 ms margin may be flaky on a slow CI runner.
4. Low. [`ChatbotSchemaIT.java:68-94`] The `information_schema.columns` queries have no `table_schema` filter. The
   test does not assert `outcome VARCHAR(20)`, and it checks the FK by its behaviour, not by its name. I checked the
   SQL by reading it.
5. Low. [`ChatbotAnswerServiceImplTest.java:247-260`] `logsNoContent_AC_005_10` runs with mocked AI clients, so no
   real logger runs on that path. My evidence for AC-005.10 is mainly the code I read (row 15).
6. `SearchChangesetRollbackIT.java:51-69`: the change is sound. On a fresh database, `getChangeSets()` order is the
   execution order. `rollback(size - index(008))` undoes 008 to 013 and no earlier changeset. The test asserts that
   `chatbot_answer` is gone after the rollback and back after the new update. The id `008-search-public-ids` is hard
   coded, but `isNotNegative()` fails loudly if it is renamed.
7. Performance: `ticket_embedding` has no vector index, and the secondary `ORDER BY t.id` would stop an HNSW scan in
   any case. Each query scans the whole table. This is fine at the size of one Workspace.

Security: the endpoint requires a session (`/api/**` authenticated, `@PreAuthorize("isAuthenticated()")`) and CSRF.
The prompt-injection tags are escaped. No SQL is built from strings. No content goes into logs or exception
messages. A ticket citation carries only the five sections.

## Assumptions made
1. **Tests not run by the reviewer (open judgment call).** The dispatcher told me not to run Docker or mvn. My
   evidence for Correctness is the controller's run of 29 unit tests and its separate `mvn verify` for the ITs. This
   pass depends on that `mvn verify` being green.
2. **Interim behaviour until Tasks 8 to 10 (open judgment call).** In `ChatbotAnswerServiceImpl.java:135`, an
   `ANSWERED` answer returns every kept source as a citation and keeps unknown markers such as `[7]` in the text
   (AC-006.1 and AC-006.2 are Task 8). `history` is accepted but not checked, not used for retrieval and not sent to
   the model (`:103`; AC-005.3 turns, AC-007.1 to AC-007.3 and "`history` required" are Task 10). I judged Task 7
   against its plan. `KAN-179` must not reach `main` or a user before the stacked `KAN-180` and `KAN-181`.
3. **Trimming and length.** I accepted that `@Size` measures the raw question (Issue 2). If the spec means the
   trimmed length, this is a one-line change.
4. **Project status.** Retrieval ignores `project.status`. The spec defines a Workspace Solution by its ticket and
   Department status only, and I judged against the spec.

## Traceability
```
  ✓ AC-005.1 … AC-005.6  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java (AC-005.3: AnswerPromptTest.java)
  ✓ AC-005.7  ChatbotAnswerServiceImplTest.java, ChatbotAnswerControllerTest.java
  ✓ AC-005.8, AC-005.9  ChatbotAnswerControllerTest.java
  ✓ AC-005.10  ChatbotAnswerServiceImplTest.java, ChatbotAnswerControllerTest.java
  ✓ AC-005.11  backend/src/test/java/vn/tsolve/chatbot/ChatbotSchemaIT.java
trace chatbot (STORY-005): 11/11 acceptance criteria have a test
  ✓ AC-010.1, AC-010.3  backend/src/test/java/vn/tsolve/search/retrieval/repository/SolutionRetrievalRepositoryIT.java
  ✓ AC-010.2, AC-010.4, AC-010.5  backend/src/test/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImplTest.java
trace search (STORY-010): 5/5 acceptance criteria have a test
```
I read every test listed. Each one checks the behaviour of its criterion, and none only mentions the id.

## Issues
None blocking. Informational items 1 to 7 are under Stage 2.

## Test Report
- Unit tests: 29/29 pass (controller run). The reviewer did not run them (Assumption 1).
- ITs (`ChatbotSchemaIT`, `SolutionRetrievalRepositoryIT`, `SearchChangesetRollbackIT`): in the controller's
  separate `mvn verify`. The reviewer did not see the result.
- `bun harnessclaude/hooks/sdd.ts trace chatbot STORY-005`: 11/11. `trace search STORY-010`: 5/5 (reviewer).
- Scope: `git status --porcelain -uall` lists only the in-scope files, plus `progress.json` and `events.jsonl`. The
  harness artifacts listed as modified have no content diff (`git diff HEAD --stat`).
- Feature statuses were not changed: the dispatcher asked me to write only the review.
