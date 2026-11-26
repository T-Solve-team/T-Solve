# Test report: chatbot — Task 28 rag-eval-runner

This is the same run as `task-27-answer-sources-test-report.md` (one Maven run covered both tasks). Date:
2026-11-26. Command prefix: `mvn -B -Dapi.version=1.44`.

| Run | Result |
|-----|--------|
| `GoldFileTest`, `RagEvalReportTest`, `RagEvalRunnerTest`, `ChatbotAnswerServiceImplTest` (AC-005.2 ranking now goes through `ChatbotRetrieval`), `EmbeddingCatchUpJobTest`, plus three more | 112 run, 0 failures, 0 errors |
| Full surefire `test` | 3278 run, 0 failures, 186 errors. Every error is caused by `localhost:3900` (object storage not running locally); none comes from this change |
| `spotless:check` | pass |
| `sdd.ts trace chatbot STORY-015` | 8/8 |

Not covered by any test (see review Issues 1 and 4): a context started with `@ActiveProfiles("rag-eval")`, so there
is no evidence that "no web server starts" or "no scheduled task is registered" in a real context.
