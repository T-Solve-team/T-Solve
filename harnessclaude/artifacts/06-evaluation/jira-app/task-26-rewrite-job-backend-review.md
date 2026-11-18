---
module: jira-app
feature: rewrite-job-backend
task: 26
verdict: pass
attempt: 1
date: 2026-11-18
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 26: Rewrite with AI job — contract and backend

## Summary
I reviewed the uncommitted change on `fix/KAN-294-jira-app-ask-job` (backend, frontend contract and generated client, `.env.example`). I did not look at `jira-app/`, which belongs to Task 27.

POST `/solution-rewrites` runs `JiraIssueAccess.asUser` synchronously first. It then writes a PENDING record to `jira-app:rewrite:{uuid}` (owner is cloudId + accountId, no projectKey, TTL 300 s) and submits `SolutionFormAi.startRewrite` to `SolutionFormAiImpl`'s bounded pool with `JIRA_APP_REWRITE_TIMEOUT` (60 s). It answers 202 `{jobId}`. A Redis error or a full pool deletes the record and answers 503 MSG68. GET compares cloudId and accountId and answers 404 MSG68 for any other job. A Redis error on GET answers 503 MSG68.

DONE and FAILED are written with `SET XX KEEPTTL`. Completion runs on the pool, or inline if the pool rejects it. Logs carry only the job id and the unwrapped error type. The AI draft is unchanged: it is synchronous and still uses `JIRA_APP_AI_TIMEOUT`. Ask T-Solve only moved to the generic store API and its tests pass. None of Task 24's findings come back. I found no critical or major issues.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-007.5 to AC-007.8 are implemented as written. A timeout covers queue time, and a queued supplier that has timed out is skipped. A late AI answer cannot overwrite FAILED. Stub modes AVAILABLE and UNAVAILABLE also run as jobs |
| Completeness | pass | OpenAPI has 202 `SolutionRewriteJob` and `getSolutionRewriteJob` (200/401/404/503). The generated client, models and MSW (status 202) match the YAML. `.env.example` sets `JIRA_APP_REWRITE_TIMEOUT=60s`. No web consumer of `rewriteSolution` |
| Correctness | pass | Surefire from the running `mvn verify`: SolutionRewriteJobTest 11/0, SolutionFormAiImplTest 11/0, RedisAskJobStoreTest 4/0, JiraAppSolutionServiceImplTest 19/0, JiraAppSolutionControllerTest 16/0, JiraAppQuestionServiceImplTest 29/0, JiraAppQuestionControllerTest 21/0. No new test sleeps: they use a queued executor and an injected deadline |
| Integration | pass (note) | The Forge client (Task 27) still expects 200 with sections, so do not deploy Task 26 without Task 27 |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-007 STORY-012`: 15/15 acceptance criteria have a test. AC-007.5 to AC-007.8 are covered by SolutionRewriteJobTest, SolutionFormAiImplTest, RedisAskJobStoreTest and JiraAppSolutionControllerTest. AC-012.7 is covered by `returnsBeforeAiThenPendingAndDone_AC_007_5_AC_012_7`. The trace also printed "ids in tests but not in the spec: AC-001.77", which existed before this change.

## Issues
1. `backend/src/main/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImpl.java:153` — minor — The completion is queued FIFO on the same 4+4 AI pool, behind AI calls that can each hold a thread for up to `AI_CHAT_TIMEOUT` 60 s (`OllamaHttp.java:52`). When the pool is saturated, a finished rewrite's DONE write can wait long enough for the form to reach its 75 s limit and show MSG68. The queued completion also takes a queue slot, so a draft can get "Busy". Ask T-Solve has the same pattern (accepted in Task 24). Fix: run the short Redis write on a small dedicated completion executor and keep the inline fallback.
2. `SolutionFormAiImpl.java:243` — minor (judgment call) — Rewrite jobs can starve the synchronous draft. Four running rewrites plus four queued ones fill the pool, so every AI draft in that window is rejected ("Busy") or times out at 18 s and falls back to MSG67 (AC-002.2). Nothing is blocked, and threads were held just as long before this change because `orTimeout` never interrupts. The difference now is that queued rewrites keep their slot for up to 60 s instead of 18 s. Fix, if wanted: a separate rewrite pool, or one thread reserved for drafts.
3. `backend/src/main/resources/application.yml:18` — minor (carried over from the Task 24 note) — `spring.data.redis.timeout` is not set. A Redis that hangs, rather than refusing connections, holds POST for Lettuce's 60 s default. That breaks AC-007.5 (2 s) and AC-007.8 ("503 at once"). Fix: set a short command timeout (for example 2s). It applies to the whole app, so agree it with the team.

## Assumptions made
- I did not run Maven, because the dispatcher said not to while `mvn verify` was running. The results above come from that run's surefire reports, written 03:39–03:40, which is after the last source and test edits (03:32). I did not check Spotless, ITs or the final `verify` result.
- I did not run `npm run generate:api`, because it rewrites the tree. I read the generated diff against the YAML instead. `prettier --check` on the changed contract and generated files passes, and so does `npm run typecheck`.
- The owner check leaves out projectKey, following the design. AC-007.6 names only "Jira account or site". A test confirms that a FIT from another project can read the job.
- Starvation of the draft (issue 2) is minor because AC-002.2's fallback covers it. This is a judgment call for the user.
- `docs/bug-log.md` has no row for the 18 s rewrite cut that this change fixes. Ask T-Solve had BUG-343 for the same cut. That file is outside the diff I reviewed. The main session should decide whether CLAUDE.md's "log every bug" requires a row.
- I did not change the feature status (dispatcher instruction).

## Test Report
See `task-26-rewrite-job-backend-test-report.md`.
