---
module: jira-app
feature: ask-job-backend
task: 24
verdict: pass
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 24: Ask T-Solve job — contract and backend

## Summary
The change is uncommitted in the working tree on `fix/KAN-294-jira-app-ask-job`. POST `/api/v1/jira-app/questions` checks the site and project first (AC-010.11, AC-010.13), then writes a PENDING record to `jira-app:ask:{uuid}` with a 300 s TTL. Only after that does it submit the chatbot call to the bounded pool, binding the Workspace on the worker (BUG-318), and it answers 202 `{jobId}`. A full pool deletes the record and answers 503 MSG06 (AC-010.18, BUG-183). DONE and FAILED are written with `SET XX KEEPTTL`. A late answer after the 60 s `orTimeout` cannot overwrite FAILED: the future is already complete, and a queued `AsyncSupply` is skipped. GET compares cloudId, accountId and projectKey and answers 404 MSG06 when they do not match. Logs carry only the job id and the error type. I found no critical or major issues, only minor ones.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-010.4 (202 without waiting; PENDING, then DONE or FAILED), AC-010.16, AC-010.17 (TTL 300 s, KEEPTTL, XX) and AC-010.18 are implemented as the spec says |
| Completeness | pass | OpenAPI has 202 `JiraAppQuestionJob` and `getJiraAppQuestionJob` (200/401/404); the generated client and MSW match; `.env.example` sets 60s; BUG-343 is logged and two tests name it |
| Correctness | pass | Surefire: JiraAppQuestionServiceImplTest 21/0, RedisAskJobStoreTest 3/0, JiraAppQuestionControllerTest 21/0. No test sleeps or waits on the clock (queued executor, injected deadline) |
| Integration | pass (note) | The Forge client (Task 25) still expects a synchronous answer, so Task 24 must not be deployed without Task 25 |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-010` → 18/18 acceptance criteria have a test (AC-010.16, .17 and .18 are covered by JiraAppQuestionServiceImplTest, RedisAskJobStoreTest and JiraAppQuestionControllerTest). Also printed: "ids in tests but not in the spec: AC-001.77". That id existed before this change.

## Issues
1. `backend/src/main/java/vn/tsolve/jiraapp/service/impl/JiraAppQuestionServiceImpl.java:185` — minor — If Redis is down, `jobs.create` throws a `DataAccessException`. The catch-all handler turns it into 500 MSG06 and an ERROR log, but OpenAPI declares only 503 for POST. Lettuce's default 60 s command timeout can also hold the request well past AC-010.4's 2 s. Fix: catch `DataAccessException` around `create` (as `JiraAccountLinkPageServiceImpl:84` does), log the class name and throw `AiUnavailableException`. Separately, consider setting `spring.data.redis.timeout`, which applies to the whole app.
2. `JiraAppQuestionServiceImpl.java:192` — minor — The `whenComplete` callback blocks on Redis I/O, and on timeout it runs on the JDK's single `CompletableFuture` delayer thread. That thread is shared with the `orTimeout` calls in ChatbotAnswerServiceImpl, ChatbotIssueServiceImpl and SolutionFormAiImpl, so a stalled Redis delays their deadlines. Fix: `whenCompleteAsync(…, executor)` on a small dedicated executor (or the ask pool, with a fallback when it rejects).
3. `JiraAppQuestionServiceImpl.java:197` — minor — A chatbot exception reaches `whenComplete` wrapped, so the log always says `CompletionException`, not the real type (for example `AiUnavailableException`). Fix: unwrap `CompletionException` before `getSimpleName()`.
4. `docs/bug-log.md:357` — minor — BUG-343 is marked `Fixed` while the user-visible fix also needs Task 25 (the Forge polling client). Fix: keep it open (or "Fixed in backend") until Task 25 lands, and fill in the PR column then.

## Assumptions made
- I did not run Maven, because the dispatcher forbade it while another `mvn verify` was running. Test results come from `backend/target/surefire-reports`. Those reports are dated 01:50, which is after the newest source and test edits (01:42). Spotless, the full suite and ITs were not checked by me.
- I did not run `npm run generate:api`, because it would rewrite the working tree. I judged the generated client consistent by reading its diff against the YAML.
- AC-010.17 is checked with a mocked `RedisConnection` (TTL ≤ 300 s, `keepTtl`, `ifPresent`), not against a real Redis. I accepted this because AGENTS §8 discourages new Redis containers and Redis 7.4 supports KEEPTTL. This is a judgment call: the task preferred adding the check to an existing Redis IT.
- The 60 s deadline starts when the job is submitted, so time waiting in the queue counts against it. I read AC-010.4's "within 60 seconds" as counted from the start of the job.
- I treated a Redis failure on POST (500 MSG06, finding 1) as minor: the spec does not cover it, and the page shows MSG06 for any 5xx (AC-010.8).
- progress.json shows `ask-job-backend` as `in-progress`, not `evaluating`. I did not change the status, because the dispatcher asked for a read-only review.

## Test Report
See `task-24-ask-job-backend-test-report.md`.
