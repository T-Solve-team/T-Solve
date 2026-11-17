# Task 14: Classify and rewrite before the search

**Module:** chatbot
**Assignee:** anhtranhai835
**Status:** passing
**Iteration:** 3

---

## Session 2026-11-17 14:00

### Working on: classify-question
- Coded by a Codex subagent (`gpt-6.1-sol`, reasoning high, sandbox `workspace-write`), at the user's request;
  reviewed by `harnessclaude-reviewer` (Opus, effort high): verdict pass, attempt 1
  (`06-evaluation/chatbot/task-14-classify-question-review.md`).
- New `QuestionClassifier` (greeting phrase set, normalization, fail-open `parse`) and `ClassifierPrompt`
  (system message, tagged history and question, `CLASSIFIER_SCHEMA`); `AnswerPrompt.escape` made package-private
  and reused.
- `ChatbotAnswerServiceImpl.ask`: greeting check, then the classifier call under the one deadline; classified
  outcomes saved with empty text; `QUESTION` searches with the query and answers with the question; an
  `AiUnavailableException` from the classifier fails open (user decision 17/11); a timeout is 503 with no row.
- Tests: `QuestionClassifierTest`, `ClassifierPromptTest`, new cases in `ChatbotAnswerServiceImplTest` and
  `ChatbotAnswerTimeoutIT.classifierCountsAgainstDeadline_AC_009_7`, all named in Task 14.
- BUG-321 (review finding): the normalizer also strips `\p{Cf}` and U+FE00–U+FE0F;
  `stripsEmojiJoinersAndSelectors_AC_009_1_BUG_321` red before, green after.

### Verify
- `sdd.ts trace chatbot STORY-009 STORY-001 STORY-003 STORY-004 STORY-005 STORY-007`: 51/51 criteria have a test.
- Chatbot answer unit tests (`QuestionClassifierTest`, `ClassifierPromptTest`, `ChatbotAnswerServiceImplTest`,
  `AnswerPromptTest`, `CitationsTest`, `ChatbotAnswerControllerTest`): 133/133; `spotless:check` pass.
- Full `mvn verify` (JDK 24, `-Dnet.bytebuddy.experimental=true`): BUILD SUCCESS, ITs 900/900, Spotless clean.

### Deviations
- None from the plan. Codex could not reach Docker in its sandbox; the ITs were run outside it.

### Blockers
none

### Commit

- `fd44833b` feat(chatbot): classify questions before the search (KAN-243). Review passed (attempt 1); BUG-321 fixed before the commit.
