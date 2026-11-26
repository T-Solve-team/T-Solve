# Test report: chatbot — Task 27 answer-sources and Task 28 rag-eval-runner

Date: 2026-11-26. Runner: harnessclaude-reviewer. JDK: IntelliJ JBR. Command prefix:
`mvn -B -Dapi.version=1.44`. Working tree: `112f6f28` plus the uncommitted Task 28 changes.

| Run | Result |
|-----|--------|
| `test -Dtest=GoldFileTest,RagEvalReportTest,RagEvalRunnerTest,ChatbotAnswerServiceImplTest,SolutionRetrievalImplTest,CitationsTest,AnswerPromptTest,EmbeddingCatchUpJobTest` | **112 run, 0 failures, 0 errors** — BUILD SUCCESS |
| `test` (full surefire suite) | 3278 run, 0 failures, **186 errors**, all environmental (see below) |
| `spotless:check` | BUILD SUCCESS |
| `bun harnessclaude/hooks/sdd.ts trace chatbot STORY-014 STORY-015` | 13/13 criteria have a test |
| `bun harnessclaude/hooks/sdd.ts trace search STORY-010` | 5/5 |

**The 186 errors.** All of them are in 15 full-context classes (identity session/sign-in/jiralink tests and
`SecurityConfigCsrfOrderTest`). I grepped each failing `surefire-reports/TEST-*.xml`, and every one has the root cause
`Connect to localhost:3900 ... Connection refused`: `ObjectFileStorage.checkBucket` runs on `ApplicationReadyEvent`,
and object storage is not running locally. This failure comes after context refresh, so the normal-profile bean graph
(the new `ChatbotRetrieval` and the conditional `EmbeddingCatchUpJob`) refreshed without error. No failure is related
to this change.

**Not run.** Failsafe ITs (`ChatbotSchemaIT`, `SolutionRetrievalRepositoryIT`, `ChatbotConversationStoreIT`,
`ChatbotAnswerTimeoutIT`) were not run by the reviewer. The dispatcher reports they pass locally. No test starts the
application with the `rag-eval` profile (see the Task 28 review, Issue 4).

**Probe.** Jackson 2.17.2 `ObjectMapper.readTree("﻿{...}")` throws `JsonParseException`. This confirms Task 28
Issue 5 (a UTF-8 BOM in the gold file). The probe ran from the session scratchpad, not the repo.
