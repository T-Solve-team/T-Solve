---
module: chatbot
feature: classify-question
task: 14
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude-reviewer
iteration: 3
---

# Review: chatbot — Task 14: Classify and rewrite before the search (KAN-243, STORY-009)

## Summary
I reviewed the uncommitted backend changes on `feat/KAN-243-chatbot-classify-questions` (base `e274b932`). Task 13
(`807452ff`: outcome enum, changeset 043, contract) was read for context only. The change adds `QuestionClassifier`
(greeting set, normalization, fail-open JSON parse) and `ClassifierPrompt` (system message, tagged turns and question,
`CLASSIFIER_SCHEMA`). It wires both into `ChatbotAnswerServiceImpl.ask` and makes `AnswerPrompt.escape`
package-private. I checked AC-009.1 to AC-009.7, AC-005.1 and AC-007.1 against the code and the tests. I ran
`QuestionClassifierTest` (44/44), `ClassifierPromptTest` (1/1) and `ChatbotAnswerTimeoutIT` (2/2, real Postgres
via Testcontainers) myself, and they pass. The query is never returned, stored or logged. The answer prompt gets
the question, not the query. Every fail-open path is in the code and in a test. A deadline that passes during the
classifier gives 503 and stores no row. I found no CRITICAL issue. One normalization gap (emoji sent with U+FE0F or
ZWJ) is a judgment call that I list below. Verdict: PASS.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `ask` (`ChatbotAnswerServiceImpl.java:112-174`) sets the deadline first, then runs the greeting rule (`:115`, saves a `GREETING` row with no AI call), then one classifier call (`:119-128`) through `callAi` under the same `deadline`, then `remaining(deadline)` (`:129`), then `parse`. A non-`QUESTION` category saves that outcome with `""` and no citations (`:131-134`). A `QUESTION` embeds `query` when it has one, else the old retrieval text (`:136-139`). `AnswerPrompt.messages(question, …)` (`:166`) still gets the trimmed question. An `AiUnavailableException` from `llm.chat` is caught inside the supplier and becomes `null`, so it fails open (`:121-126`). A timeout leaves `orTimeout`, becomes `CompletionException`, then `AiUnavailableException("MSG06")` with no cause (`:195-205`), and nothing is saved. |
| Completeness | pass | AC-009.1 to AC-009.7, AC-005.1 and AC-007.1 each have tests that assert the behaviour, not only a matching name (see Stage 1). The design's edge cases are covered: Q1 (`hi, how do I reset my VPN?` goes to the classifier), Q2 (prose fails open), Q5 (IT with a stalled classifier), Q6 (NFD, NBSP, em space, emoji, punctuation), Q8 (saved row and log). Every test the task lists exists. |
| Correctness | pass | I ran the tests listed above, and all pass. The caller reports the chatbot answer package at 130/130 and `spotless:check` clean. I did not run the full `mvn verify` again, as agreed. |
| Integration | pass | `LlmClient.chat(messages, Map)` is used as the `ai` module documents it. `OllamaLlmClient` sends the schema as `format` and throws `AiUnavailableException` for a blank or non-object reply, which the service fails open. `OllamaHttp` logs no body. The only other reader of `chatbot_answer` is `DashboardRepository`, and it filters `outcome = 'NO_SOURCE'` (`DashboardRepository.java:273`), so the new rows are not knowledge gaps (AC-009.4). The constructor now takes `ObjectMapper`. Spring Boot provides it, and both `ApplicationContextRunner` setups in the IT register one. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-009.1 greeting phrase gives `GREETING`, '' text, no citations, no embedding and no chat call; trimmed, lower-cased, punctuation and emoji removed | PASS (see Issue 1) | The phrase set (`QuestionClassifier.java:13-30`) is exactly the 17 phrases of Interface Contracts. Normalization is NFC, `toLowerCase(Locale.ROOT)`, `[\p{P}\p{So}\p{Sk}]` replaced by a space, Unicode white space collapsed, then trimmed (`:40-44`). Diacritics stay, so `xin chao` is not a greeting. `QuestionClassifierTest.matchesGreetingPhrases_AC_009_1` checks all 17 phrases, and checks that each phrase followed by a question is not a greeting. `normalizesPunctuationEmojiAndCase_AC_009_1` checks `HELLO!!! 👋🏽`, `Cảm ơn!! 🙏`, a tab, `thank-you`, NFD `cảm ơn` and NBSP/em space. `ChatbotAnswerServiceImplTest.greetingCallsNoAi_AC_009_1` checks `verifyNoInteractions(llm, embeddings, solutions, wiki)` and the empty response. |
| 2 | AC-009.2 one `LlmClient.chat` before any embedding, with the schema, system message, tagged turns and question | PASS | `ClassifierPromptTest.tagsHistoryAndQuestionAsData_AC_009_2` checks the role order, the escaped `<turn-question>`, `<turn-answer>` and `<question>` content (`&`, `<`, `>`, `"`), the key phrases of the system message, and that `CLASSIFIER_SCHEMA` equals the contract schema. `classifierCalledOnceBeforeEmbeddingWithSchema_AC_009_2` uses `inOrder` with `eq(CLASSIFIER_SCHEMA)`: classifier, then embed, then both searches, then the answer chat, then `verifyNoMoreInteractions`. It also checks that the call runs off the request thread. `parsesCategoryAndQuery_AC_009_2` checks lower-case and padded categories, and a trimmed 1,200+ character query (no cap, as decided). |
| 3 | AC-009.3 `OFF_TOPIC` / `INJECTION` / classifier `GREETING` answer that outcome, no embed or search | PASS | `refusesOffTopicAndInjectionWithoutSearch_AC_009_3` (both values, one LLM call, `verifyNoInteractions(embeddings, solutions, wiki)`). `classifierGreetingAnswersGreeting_AC_009_3` (`" greeting "` from the model). |
| 4 | AC-009.4 one row with that outcome, the trimmed question, '' answer_text; id returned | PASS | `storesClassifiedRowWithEmptyText_AC_009_4` (three categories). It checks the user id, `"message"` trimmed from `"  message  "`, the outcome string, the empty text, the returned id, the flush, the commit with no rollback, and that the save runs on the request thread. The dashboard counts `NO_SOURCE` only. |
| 5 | AC-009.5 the trimmed query is the retrieval text; never returned, stored or logged; the answer prompt gets the question | PASS | `searchesWithQueryAnswersWithQuestion_AC_009_5` checks that `embed` gets `"VPN Windows setup"` (trimmed). It also checks that the answer prompt's last message is `<question>What about Windows?</question>`, that no prompt message contains the query, and that the saved question and text are not the query. `neverLogsQuery_AC_009_5` checks the captured output, `response.toString()` and the saved row. The code has no logger in `vn.tsolve.chatbot`. |
| 6 | AC-009.6 fail open: `AiUnavailableException`, not JSON, unknown category, `QUESTION` with a blank query | PASS | `parse` (`QuestionClassifier.java:46-71`) returns `QUESTION` with `""` for null or blank input, invalid JSON, trailing tokens, non-object JSON, a missing or non-text category, an unknown category (`valueOf` throws `IllegalArgumentException`) and a missing, null, non-text or blank query. These cases are tested in `failsOpenOnBadJson_AC_009_6` and `failsOpenOnUnknownCategoryOrBlankQuery_AC_009_6`. `fallsBackToTodaysRetrievalText_AC_009_6` checks that `embed("VPN question")` runs for prose, `{`, `OTHER` and a blank query. `classifierAiUnavailableFailsOpen_AC_009_6` checks the AC-007.1 text and that the answer is still `ANSWERED`. |
| 7 | AC-009.7 deadline during the classifier gives 503 MSG06, no row; one deadline | PASS | One `deadline` local is passed to every `callAi`, `remaining` and `save`. `ChatbotAnswerTimeoutIT.classifierCountsAgainstDeadline_AC_009_7` stalls the classifier past the real 3 s `CHATBOT_ASK_TIMEOUT`. It checks `AiUnavailableException("MSG06")` with no cause, no embedding or search, zero rows, and zero rows after the late classifier reply. I ran it: pass. The unit test `classifierTimeoutIs503StoresNothing_AC_009_7` is weaker (Issue 3). |
| 8 | AC-005.1 embed the retrieval text once; both sources | PASS | `embedsQueryOnce_AC_005_1` checks one `embed("rewritten query")` and `verifyNoMoreInteractions(embeddings)`, and both `nearest` calls with the same vector. `embedsOnceAndAsksBothSources_AC_005_1` still covers the old path. |
| 9 | AC-007.1 no usable query and history: last question + `\n` + new question | PASS | `followUpFallbackPrependsLastQuestion_AC_007_1` (prose and `QUESTION` with `""`, two turns, trimmed). `prependsLastQuestionForRetrieval_AC_007_1` covers the default stub. |

AC-009.8 (changeset) is Task 13 and is not under review here. Its test, `ChatbotSchemaIT`, is in the trace.

### Stage 2 — Quality and security

CRITICAL: none. Security High/Critical: none.

- Prompt injection: the question and every history turn are escaped with `AnswerPrompt.escape` (`&`, `<`, `>`,
  `"`) before they go inside tags, so a `</question>` in user text cannot close the tag. The system message says
  tag content is data. The classifier's output (`query`) goes only to the embedding. It never reaches the answer
  prompt, the response, the row or a log. In the worst case an injected classifier gives a wrong category (Q3,
  accepted in RESEARCH-006).
- Error content: `callAi` throws `AiUnavailableException` without a cause. The `parse` catch drops the reply. No
  logging anywhere in the module.
- Thread and tenant: the classifier and the embedding run on `applicationTaskExecutor`. Every repository call
  stays on the request thread (tested for the classified save).
- Team rules: no Lombok, constructor injection, no wildcard imports, no `System.out`. The helpers are
  package-private `final` classes in `service/impl/`, like `AnswerPrompt` and `Citations`, so they are not services
  and need no interface. Constant and method names follow AGENTS.md §2. The `ponytail:` comment on the retrieval
  text was removed, as the task asks.

Informational (does not block):
1. Medium. [`QuestionClassifier.java:31`] Only `\p{P}`, `\p{So}` and `\p{Sk}` are removed. The emoji variation
   selector U+FE0F (category Mn), the ZWJ U+200D and the zero-width space U+200B (Cf) stay. I ran the same regex on
   sample input: `thanks ❤️` → `thanks ️`, `ok thanks ☺️` → `ok thanks ️`, `hi 👨‍💻` → `hi ‍`. None
   of these is a greeting, so they cost one classifier call, which most likely answers `GREETING` anyway. The spec
   (AC-009.1) says "emoji removed". The design's "Decided in planning" pins exactly these three classes, and the code
   follows the design. Suggested fix: also remove `\x{FE0E}\x{FE0F}\x{200B}-\x{200D}\x{20E3}` (or `\p{Cf}` plus the
   variation selectors) in the backend and in the mock (`frontend/src/features/chatbot/mocks/data.ts:29`), and add
   `thanks ❤️` to both tests. Judgment call 1.
2. Low. [`ChatbotAnswerServiceImplTest.java:96-104`] `greetingCallsNoAi_AC_009_1` checks the response and that no
   AI is called. It does not capture the saved row of the rule path (outcome `GREETING`, trimmed question, `""`). The
   code is plain (`ChatbotAnswerServiceImpl.java:116`), and AC-009.4's test covers the same `save` for a classifier
   `GREETING`. Adding the captor would close the gap.
3. Low. [`ChatbotAnswerServiceImplTest.java:202-213`] `classifierTimeoutIs503StoresNothing_AC_009_7` throws a
   `TimeoutException` from the fake `chat`. That tests "a non-`AiUnavailableException` failure is 503", not the
   deadline. The real deadline is tested in the IT (`classifierCountsAgainstDeadline_AC_009_7`), so AC-009.7 is
   covered. The unit test name overstates what it shows.
4. Low. [`ChatbotAnswerServiceImpl.java:201`] A `RejectedExecutionException` (executor saturated) on the classifier
   call is 503, not fail open. AC-009.6 lists only an `AiUnavailableException` from the classifier, so this is
   within the spec. The embedding would be rejected the same way.
5. Nit. [`ChatbotAnswerServiceImplTest.java:250-264`] `neverLogsQuery_AC_009_5` captures output with the AI clients
   mocked, so `OllamaHttp`'s per-call log line is not exercised. `OllamaHttp` documents that it logs no body, and
   `logsNoContent_AC_005_10` has the same scope.

## Assumptions made
1. **Emoji with a variation selector or ZWJ (open judgment call).** I treat Issue 1 as non-blocking. The design
   pinned the character classes, the result is fail-safe (one extra classifier call, the same outcome in practice),
   and the AC's main promise, no AI call for the listed phrases with common emoji and punctuation, holds. If the
   user reads "emoji removed" strictly, this becomes a fail. The fix is about two lines in each normalizer.
2. A `RejectedExecutionException` counts as an AI-side failure (503), not as a classifier answer that is not
   usable (Issue 4).
3. I did not update the feature status in `progress.json`. The dispatcher limited me to writing review files, so
   the main session runs `sdd.ts feature chatbot classify-question passing`. I wrote no separate test-report file.
   The results are in the Test Report section below.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace chatbot STORY-009 STORY-001 STORY-003 STORY-004 STORY-005 STORY-007`:
51/51 acceptance criteria have a test. The rows for this task:

```
✓ AC-005.1  ChatbotAnswerServiceImplTest.java
✓ AC-007.1  ChatbotAnswerServiceImplTest.java
✓ AC-009.1  ChatbotAnswerServiceImplTest.java, QuestionClassifierTest.java
✓ AC-009.2  ChatbotAnswerServiceImplTest.java, ClassifierPromptTest.java, QuestionClassifierTest.java
✓ AC-009.3  ChatbotAnswerServiceImplTest.java
✓ AC-009.4  ChatbotAnswerServiceImplTest.java
✓ AC-009.5  ChatbotAnswerServiceImplTest.java
✓ AC-009.6  ChatbotAnswerServiceImplTest.java, QuestionClassifierTest.java
✓ AC-009.7  ChatbotAnswerServiceImplTest.java, ChatbotAnswerTimeoutIT.java
✓ AC-009.8  ChatbotSchemaIT.java (Task 13)
```

## Issues
No blocking issues. The informational items 1 to 5 are listed in Stage 2. Item 1 is the open judgment call.

## Test Report
- I ran (JDK 24, `-DargLine=-Dnet.bytebuddy.experimental=true`, spotless and jacoco skipped): `QuestionClassifierTest`
  44/44, `ClassifierPromptTest` 1/1, `ChatbotAnswerTimeoutIT` 2/2 (Testcontainers, changesets up to 043). All pass.
- Caller's evidence, not re-run: the chatbot answer package unit tests 130/130, `mvn spotless:check` clean.
- Probe: the greeting regex on emoji samples (Issue 1), a throw-away script in the scratchpad.

## Resolution (2026-11-17, before commit)
- The emoji normalization finding is fixed as BUG-314 (`docs/bug-log.md`): both normalizers also strip `\p{Cf}`
  (ZWJ, zero-width space) and U+FE00–U+FE0F. New tests `QuestionClassifierTest.stripsEmojiJoinersAndSelectors_AC_009_1_BUG_314`
  and `handlers.test.ts` "AC-004.7 strips emoji joiners and selectors … (BUG-314)" failed before the fix and pass
  after it. The keycap mark U+20E3 is not stripped (a "1️⃣" greeting goes to the classifier).
