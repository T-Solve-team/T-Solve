---
module: tracker
feature: suggestion-job-worker
task: 16
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
---

# Review: tracker — Task 16: Run the suggestion job on the worker (STORY-011 change of 2026-11-07, KAN-22)

## Summary
Verdict: PASS. All 9 criteria, AC-011.1 to AC-011.9, hold in the code, and each has a test that checks it. No finding blocks the pass; the minor fixes are listed below.

Scope: the uncommitted working tree on `feat/KAN-22-tracker-suggestion-job` (`git diff HEAD -- backend .env.example` plus the untracked files under backend/).

- **The job carries only a UUID:** `SuggestionJobRequest(jobRef, issueKey)` stays inside tracker. The worker receives only `TenantJobService.enqueue("tracker.suggestion", jobRef)` (WorkerSuggestionJobQueue.java:44, :46). The issue text is in `suggestion_result.issue_summary` / `issue_description`.
- **Retries:** `SuggestionJobServiceImpl.run` throws `SuggestionJobFailedException` on every read, search, serialize or store failure (:44-52). The handler lets the exception through (SuggestionJobHandler.java:31). Nothing in the worker path wraps it: `TenantJobRequestHandler.run` → `TenantExecutionServiceImpl.runInTenant` → `runIn`. So JobRunr sees the failure and retries the job.
- **Idempotency:** a rerun stops at the `hasSuggestion()` guard (:39). `fillSuggestion` is still the conditional update (`where suggestedSolution is null`).
- **What JobRunr stores:** the exception it stores has no cause. Its message is `Suggestion job failed for <key|jobRef>: <SimpleName>` (SuggestionJobFailedException.java:13). Tests check `hasNoCause()` and that the message holds no issue text.
- **Bean selection:** `InProcessSuggestionJobQueue` has `havingValue=false, matchIfMissing=true` and `WorkerSuggestionJobQueue` has `havingValue=true`, so exactly one queue exists for `true`, `false` and an unset property. `SuggestionJobQueueSelectionTest` checks all three cases.
  - Worker disabled: the worker beans `TenantJobService` and `WorkerTenantService` are not needed, and `SuggestionJobHandler` only needs `SuggestionJobService`, so the app starts.
  - Worker enabled: the bean graph has no cycle. It runs `TenantJobService` → `TenantJobRequestHandler(List<TenantJobHandler>)` → `SuggestionJobHandler` → `SuggestionJobService`, and the queue depends only on worker beans.
  - Handler type `tracker.suggestion` is the only `TenantJobHandler` in main code, so it is unique.
- **Module boundaries:** tracker imports only `vn.tsolve.worker.service.{TenantJobService, TenantJobHandler, WorkerTenantService}`, and the worker module does not import tracker. Injection is through constructors. The exception is in `tracker/exception` and extends `common.exception.CodedException`.
- **Changeset 017:** it adds `job_ref`, backfills it with `gen_random_uuid()`, then sets DEFAULT `gen_random_uuid()`, NOT NULL and UNIQUE `uq_suggestion_job_ref`, and adds `issue_summary TEXT` and `issue_description TEXT`.
  - The file is new and is included last in the master changelog.
  - Effect on triage: `SuggestionResultRecord` does not map the new columns, so `ddl-auto: validate` is not affected. Triage's raw-SQL seed (SuggestionResultRecordRepositoryIT.java:94) leaves out `job_ref`, and the DEFAULT covers it.
- **Earlier findings fixed:** issues 1, 2 and 3 of task 15 are fixed. `runOnce` catches failures in the in-process stand-in. The unused `fill`/getters are gone. The 202-without-waiting test now blocks on a latch.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | See the Stage 1 table. |
| Completeness | pass | 9/9 criteria have a test. Edge cases: 45 by `SuggestionJobHandlerTest.letsAFailedJobFail...` plus the worker's native-retry IT; 46 by the three `failsForARetry..._AC_011_6` tests (`hasNoCause`, no text in message or log); 47 by `WorkerSuggestionJobQueueTest.logsWithoutThrowingWhenNoWorkspaceIsBoundOrConfigured`; 48 is not tested (issue 4). No EXTRA behaviour. |
| Correctness | pass | The reviewer ran 41 tests in 8 classes: all pass (see the test report). `mvn verify`, which includes `SuggestionResultRepositoryIT` and spotless, is run separately by the dispatcher. No CRITICAL finding. |
| Integration | pass | The code matches the worker's contract in README-worker: the record id is a UUID; the handler type is non-blank and unique; enqueue needs a bound Workspace; retries come only from a thrown exception; the handler is idempotent. Triage still reads the same table with no change. No other module changes. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-011.1: store summary + description as plain text in the row, enqueue exactly one job carrying only a reference, after commit, 202 without waiting | PASS | IssueCreatedServiceImpl.java:50-67: the row with its text is saved with `saveAndFlush` (non-transactional `receive`), then `enqueue(SuggestionJobRequest(jobRef, key))`. Tests: `storesTheIssueTextAndEnqueuesOnlyAReferenceAfterTheRowIsSaved_AC_011_1` (InOrder, exact request, no more calls), IT `storesTheIssueTextAndAJobReferenceTheJobFindsItBy_AC_011_1`, `WorkerSuggestionJobQueueTest` (only `(TYPE, JOB_REF)` sent, in the bound or dev Workspace), `usesTheWorkerWhenItIsEnabled_AC_011_1`. |
| 2 | AC-011.2: no job for a duplicate or a non-ACTIVE project | PASS | Unchanged early returns (:45-48, :59-64). Tests: `enqueuesNoJobFor..._AC_011_2`. |
| 3 | AC-011.3: ask search with the job's Project, summary, description | PASS | SuggestionJobServiceImpl.java:57-58 reads them from the row. Tests: `asksSearchWithTheProjectAndTheIssueTextStoredInTheRow_AC_011_3`, `SuggestionJobHandlerTest.runsTheSuggestionJobOfTheReferencedResult_AC_011_3`. |
| 4 | AC-011.4: store sources and solution, change no other field | PASS | `fillSuggestion` is unchanged. The new columns are `updatable=false`. Unit test checks the exact JSON; the IT checks JSONB. |
| 5 | AC-011.5: no suggestion leaves no sources and no solution | PASS | :59-62; `storesNothingWhen..._AC_011_5` (empty and blank). |
| 6 | AC-011.6: search, read or store failure → job fails for retry, row unchanged, log only key + error type | PASS | :44-52, :71-75. Tests: `failsForARetryWithOnlyKeyAndErrorTypeWhenSearchFails`, `...WhenTheResultCannotBeRead`, `...WhenTheSuggestionCannotBeStored` (all `_AC_011_6`, `hasNoCause`, message has no text, captured output has no text); `letsAFailedJobFailSoTheWorkerRetriesIt_AC_011_6`. The row stays unchanged because the only write is the single conditional UPDATE. On a read failure the issue key is unknown, so the jobRef is logged instead (issue 2). |
| 7 | AC-011.7: result gone or already filled → no search, no change | PASS | :38-42. Tests: `changesNothingWhen..._AC_011_7` (2 unit tests) and the 2 ITs. |
| 8 | AC-011.8: `tsolve-chatbot` handled like any other | PASS | The code never reads labels. Test: `...CreatedByTheChatbotLikeAnyOther_AC_011_8`. |
| 9 | AC-011.9: worker not enabled → in-process, no retry, 202 without waiting | PASS | InProcessSuggestionJobQueue.java:23 (condition), :50-67 (`runOnce` catches and logs). Tests: `returnsBeforeTheJobFinishesAndRunsItOnItsOwnThread_AC_011_9` (latch: `enqueue` returns while the job is blocked), `logsAFailedJobWithoutRetryingIt_AC_011_9` (`run` called exactly once), `dropsAndLogs..._AC_011_9`, `runsInProcessWhenTheWorkerIs{NotConfigured,Disabled}_AC_011_9`. |

### Stage 2 — Quality

CRITICAL (blocks pass): none.

Informational (does not block): issues 1-6 below.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace tracker STORY-011`: 9/9 acceptance criteria have a test.

```
✓ AC-011.1  IssueCreatedServiceImplTest, SuggestionJobQueueSelectionTest, WorkerSuggestionJobQueueTest, JiraAppEventControllerTest, AdfTextExtractorTest, SuggestionResultRepositoryIT
✓ AC-011.2  IssueCreatedServiceImplTest
✓ AC-011.3  SuggestionJobHandlerTest, SuggestionJobServiceImplTest
✓ AC-011.4  StubSuggestionSearchTest, SuggestionJobServiceImplTest, SuggestionResultRepositoryIT
✓ AC-011.5  StubSuggestionSearchTest, SuggestionJobServiceImplTest, SuggestionResultRepositoryIT
✓ AC-011.6  StubSuggestionSearchTest, SuggestionJobHandlerTest, SuggestionJobServiceImplTest
✓ AC-011.7  SuggestionJobServiceImplTest, SuggestionResultRepositoryIT
✓ AC-011.8  IssueCreatedServiceImplTest
✓ AC-011.9  InProcessSuggestionJobQueueTest, SuggestionJobQueueSelectionTest
```

## Issues
1. backend/src/main/java/vn/tsolve/tracker/service/impl/WorkerSuggestionJobQueue.java:44, :46: minor.
   - **Problem:** the worker enqueue runs on the event request thread and writes to the Jobs DB through the worker's private Hikari pool. JobRunrRuntime.java:23-30 sets no `connectionTimeout`, so Hikari's default of 30 s applies.
   - **Scenario:** the Jobs DB is unreachable. `issue-created` blocks for up to about 30 s before the `catch` logs the failure. Forge's 5 s limit expires, and Non-Functional 1 is missed during the outage. Forge then redelivers the event, the redelivery is a duplicate, and so no job is ever queued.
   - **Outcome:** the issue gets no suggestion and the failure is logged, which design edge case 47 accepts.
   - **Fix (worker owner):** set a short `connectionTimeout` on the Jobs pool, for example 2 s. Alternatively, tracker could hand the enqueue off the request thread.
2. backend/src/main/java/vn/tsolve/tracker/service/impl/SuggestionJobServiceImpl.java:45: minor.
   - **Problem:** when the row cannot be read, `failed(String.valueOf(jobRef), e)` puts the jobRef UUID where the issue key normally goes. The log and the stored exception say "Suggestion job failed for <uuid>: <Type>".
   - **Why it does not block:** this does not break Non-Functional 4 (ids are allowed), and the key cannot be known before the read.
   - **Fix:** optional. Label the value, for example "job <uuid>", so readers do not take it for an issue key.
3. InProcessSuggestionJobQueue.java:23 / WorkerSuggestionJobQueue.java:21: minor.
   - **Problem:** the two conditions are `havingValue="false"` (with `matchIfMissing`) and `havingValue="true"`. A value that is neither, for example `TSOLVE_WORKER_ENABLED=yes` or `1`, matches neither queue.
   - **Scenario:** with such a value, startup fails with an unsatisfied `SuggestionJobQueue` for `IssueCreatedServiceImpl`. Before this change the app started with the worker off.
   - **Why it does not block:** this fails fast at startup and is easy to diagnose.
   - **Fix:** optional. Document that only true and false are allowed, or give the in-process queue a "not true" condition, for example `@ConditionalOnExpression("!'${tsolve.worker.enabled:false}'.equalsIgnoreCase('true')")`.
4. backend/src/main/java/vn/tsolve/tracker/service/impl/SuggestionJobServiceImpl.java:86-88 (edge case 48): minor.
   - **Problem:** no test covers a row with NULL `issue_summary` / `issue_description`, which rows created before changeset 017 have. `SuggestionJobServiceImplTest` uses only rows that have text, and `SuggestionResultRepositoryIT` runs on an empty table, so the backfill `UPDATE` is not tested either.
   - **Fix:** add a unit test showing that a row with null text calls `search.suggest(projectId, "", "")`.
5. backend/src/test/java/vn/tsolve/tracker/service/impl/SuggestionJobQueueSelectionTest.java:16-20: info.
   - **Problem:** the selection test wires only the two queue classes with mocks. No test starts the whole application with `tsolve.worker.enabled=true`, so a wiring problem such as a cycle or a missing bean would only show at runtime.
   - **What the reviewer checked:** the bean graph by hand, finding no cycle (see Summary).
   - **Fix:** optional. Add a context test with `worker,worker-mock` once a Jobs DB container is cheap enough in the IT suite.
6. harnessclaude/artifacts/04-plans/tracker/design.md:307-308: info (planner).
   - **Problem:** the risk line "with JobRunr the issue text is stored as a job argument in the Jobs database" is out of date. After this change the job holds only the UUID.
   - **Fix:** correct the design line. This is not a code change.

## Test Report
See task-16-suggestion-job-worker-test-report.md.

## Assumptions made
1. **AC-011.6 "log only the issue key":** on a read failure the key is unknown, so logging the jobRef, which is an id, is accepted (issue 2).
2. **Non-Functional 1 during a Jobs DB outage:** the 1 s p95 target is read as normal operation. A slow enqueue during an outage is minor and not a failed criterion (issue 1). If the user wants the event endpoint protected from a slow Jobs DB as well, issue 1 becomes a required fix, owned by the worker module or tracker.
3. **Dev Workspace fallback:** `TSOLVE_TRACKER_DEV_WORKSPACE_ID` has no profile guard (WorkerSuggestionJobQueue.java:45-46). It is accepted as "dev and mock only by configuration", as the spec's Out of Scope and the design say. If someone sets it in production with a real platform adapter, jobs for unbound requests would go into that Workspace, where `findByJobRef` finds nothing and the job is skipped.
4. **Feature status:** progress.json lists `suggestion-job-worker` as `in-progress`, not `evaluating`. The reviewer reviewed the working tree anyway, as the dispatcher asked, and changed no status. The main session should move the feature status (`sdd.ts feature tracker suggestion-job-worker …`).
5. **ITs and spotless:** the reviewer did not run `SuggestionResultRepositoryIT` (including the new `storesTheIssueTextAndAJobReferenceTheJobFindsItBy_AC_011_1`), Hibernate `validate` against changeset 017, or spotless. The dispatcher's separate `mvn verify` covers them. The PASS assumes that run is green; a failure there turns this verdict into FAIL.
