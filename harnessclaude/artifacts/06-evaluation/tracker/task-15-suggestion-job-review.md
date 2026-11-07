---
module: tracker
feature: suggestion-job
task: 15
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
---

# Review: tracker — Task 15: Suggestion job for a new issue (STORY-011, KAN-22)

## Summary
Verdict: PASS. No finding blocks the pass; the minor fixes are listed below. All 8 criteria, AC-011.1 to AC-011.8, hold in the code, and each has a test that checks it.

- **Commit before queueing:** the row is committed before the job is queued. Neither `receive` nor the controller is transactional, and `saveAndFlush` commits in its own transaction.
- **The job:** it reads the row, then calls search outside any transaction, then stores the answer with a conditional JPQL update (`... where suggestedSolution is null`). The IT shows that this update writes a real JSONB array and never overwrites a stored suggestion.
- **Logging:** a search failure logs only the issue key and the exception's simple class name. No log line in the change contains summary, description or suggestion text.
- **7ae286b:** it adds only the two closing braces (`}` and `},`) to approvals.json. It also adds the BUG-122 row to docs/bug-log.md, which the project rule requires.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | See the Stage 1 table. |
| Completeness | pass | 8/8 criteria have a test. Edge cases 37-44 are covered: 37 `enqueuesNoJobForAnIssueAlreadyRecorded`, 38 IT `neverOverwritesAStoredSuggestion`, 39 AdfTextExtractorTest + `enqueuesAnEmptyDescription...`, 40 `leavesTheResultUnchanged...`, 41 `dropsAndLogsAJob...`, 42 the output-capture assertions, 43 `changesNothingWhenTheResultNoLongerExists`, 44 `...CreatedByTheChatbot...`. No EXTRA behaviour. |
| Correctness | pass | The reviewer ran the feature's 31 unit tests: all pass. The dispatcher's `mvn verify`: 549 unit + 42 IT pass. No CRITICAL finding. |
| Integration | pass | Triage's `SuggestionResultRecord` is `@Immutable` and has a different entity name, so the JPQL `update SuggestionResult` reaches only tracker's entity. Both entities map `sources_used` as `@JdbcTypeCode(JSON) String`, and the stored shape `[{type,id,title,similarity}]` is what triage's `SourcesUsed` reads. The change adds no Spring executor bean (the stand-in keeps its pool private), so Boot's `applicationTaskExecutor` is unchanged. The OpenAPI contract and the generated client are updated. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-011.1: one job with key, Project, plain-text summary and description, queued after commit; 202 without waiting | PASS | IssueCreatedServiceImpl.java:49-65. `enqueuesOneJobWithTheIssueTextAfterTheRowIsSaved_AC_011_1` checks the order (save, then enqueue), the exact request, and no other calls. `runsTheJobOnItsOwnThreadWithoutWaiting_AC_011_1` (weak, see issue 3). AdfTextExtractorTest. The controller test checks that summary, ADF and labels are parsed. |
| 2 | AC-011.2: no job for a duplicate or a project that is not ACTIVE | PASS | Early `return` at :47 (no project) and :58 (duplicate). Tests: `enqueuesNoJobForAnIssueAlreadyRecorded_AC_011_2`, `enqueuesNoJobForAnIssueOfNoActiveProject_AC_011_2`. A non-duplicate integrity error is rethrown and queues no job. |
| 3 | AC-011.3: ask search with Project, summary and description | PASS | SuggestionJobServiceImpl.java:44; `asksSearchWithTheJobsProjectSummaryAndDescription_AC_011_3` |
| 4 | AC-011.4: store sources and suggested solution, change no other field | PASS | `fillSuggestion` sets only the two columns. The unit test checks the exact JSON. IT `storesTheSourcesAsJsonAndTheSuggestedSolution_AC_011_4` reads `sources_used -> 0 ->> 'type'`, which shows the column holds a JSONB array, not a JSON string. |
| 5 | AC-011.5: no suggestion → no sources and no solution | PASS | :53-55; `storesNothingWhenSearchHasNoSuggestion_AC_011_5` and a blank-text variant. The IT checks the insert defaults (`[]` / NULL). |
| 6 | AC-011.6: search fails → row unchanged; log has only key and error type | PASS | :43-51. In `leavesTheResultUnchangedAndLogsOnlyKeyAndErrorTypeWhenSearchFails_AC_011_6`, search throws an exception whose message contains issue text; the test checks the log has neither summary nor description. "Does not answer" depends on the port's own timeout (assumption 1). |
| 7 | AC-011.7: row gone or already filled → no search call, no change | PASS | Guard at :36-40 plus the conditional update. Two unit tests (`verifyNoInteractions(search)`). ITs `neverOverwritesAStoredSuggestion_AC_011_7` and `changesNothingForAnIssueWithNoResult_AC_011_7`. |
| 8 | AC-011.8: an issue labelled `tsolve-chatbot` is handled like any other | PASS | The code never reads labels (IssueCreatedServiceImpl.java:60-65); `enqueuesTheJobOfAnIssueCreatedByTheChatbotLikeAnyOther_AC_011_8` |

### Stage 2 — Quality

CRITICAL (blocks pass): none.

Informational (does not block): issues 1-6 below.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace tracker STORY-011`: 8/8 acceptance criteria have a test.

## Issues
1. backend/src/main/java/vn/tsolve/tracker/service/impl/SuggestionJobServiceImpl.java:36, :58-59, :69: minor.
   - **Problem:** `SuggestionJobService.run` says it never throws, but several failures escape it: `findByJiraIssueKey` or `fillSuggestion` failing (DB down, lock timeout, PostgreSQL rejecting `\u0000` in a source title for jsonb), and the serialization `IllegalStateException`.
   - **Why it matters:** nothing catches the exception on the stand-in pool (`executor.execute`, InProcessSuggestionJobQueue.java:48). The JVM's default handler then prints the message and stack trace to stderr, outside SLF4J. A PostgreSQL JSON error can quote the bad JSON, which holds source titles.
   - **Scenario:** the DB connection drops while the job stores its result. Stderr gets a raw stack trace, and no "job failed: <key> <type>" line is logged.
   - **Fix:** catch `RuntimeException` around the store step, or inside the runnable in `enqueue`. Log only the issue key and `e.getClass().getSimpleName()`, and add a test.
2. backend/src/main/java/vn/tsolve/tracker/entity/SuggestionResult.java:54-58, :76-82: minor.
   - **Problem:** main code never uses `fill(...)`, `getSourcesUsed()` or `getSuggestedSolution()`. Only `SuggestionJobServiceImplTest` calls `fill`, to build a filled row. AGENTS §9 forbids unused code.
   - **Also:** the Javadoc on `fill` says the job writes the suggestion with a conditional update and that `fill` keeps the entity in step. That is wrong: the job never calls `fill`.
   - **Fix:** remove the unused methods and build the filled entity in the test another way. If they are kept for STORY-010, correct the comment.
3. backend/src/test/java/vn/tsolve/tracker/service/impl/InProcessSuggestionJobQueueTest.java:29-38: minor.
   - **Problem:** `runsTheJobOnItsOwnThreadWithoutWaiting_AC_011_1` would also pass if `enqueue` ran the job synchronously. So it does not prove "respond 202 without waiting for the job".
   - **Fix:** make the mocked `run` block on a latch, check that `enqueue` returns while the job is still blocked, then release the latch.
4. backend/src/main/java/vn/tsolve/tracker/service/impl/SuggestionJobServiceImpl.java:44 and InProcessSuggestionJobQueue.java:25-26: minor.
   - **Problem:** the job has no timeout of its own. A `SuggestionSearch` that hangs ties up both pool threads, then fills the 100-job queue. After that every new job is dropped (each drop is logged).
   - **Mitigation:** the port's Javadoc makes the implementation responsible for the timeout, the stub cannot hang, and KAN-47 builds on the ai clients, which have read timeouts.
   - **Fix:** nothing needed now. KAN-47 must honour the port's contract: throw when search does not answer in time.
5. frontend/openapi/tsolve-api.yaml (IssueCreatedPayload.fields.description): minor.
   - **Problem:** the description text says "Absent or null", but the file is OpenAPI 3.1 and the schema is `type: object`, which does not allow `null`. Also, the backend accepts a plain-string description (AdfTextExtractor.java:24), which the contract does not describe.
   - **Fix:** use `type: [object, "null"]`.
6. backend/src/main/java/vn/tsolve/tracker/repository/SuggestionResultRepository.java:19: info.
   - **Observation:** `@Transactional` is on the repository method, not on the service as AGENTS §3 describes.
   - **Why it is acceptable:** the service must not be transactional around the outbound search call (AGENTS §4), and a `@Modifying` query needs a transaction.

## Test Report
See task-15-suggestion-job-test-report.md.

## Assumptions made
1. **AC-011.6 "does not answer":** the timeout belongs to the `SuggestionSearch` implementation (KAN-47), not to the tracker job; the tracker treats a timeout like any thrown exception. If the user wants the tracker to enforce its own limit, that is a fix (issue 4).
2. **AC-011.1 "after the row is committed":** accepted on three pieces of evidence: a unit test that checks the call order, a non-transactional `receive`, and a non-transactional controller. No IT covers it, so a later `@Transactional` on `receive` would break it without any test failing.
3. **AGENTS §4 PII masking:** not applicable. The team runs its own Ollama and dropped PII masking (module-map: "No PII masking anywhere", KAN-138 cancelled); the AGENTS §4 line is out of date. Raw summary and description to `search` is intended (user, 2026-11-07).
4. **Real Forge payload:** Task 15 asks to confirm with `forge tunnel` that the real `avi:jira:created:issue` payload has `summary` and an ADF `description`. The code cannot show whether that was done. If those fields are absent, the job sends empty text, which still meets AC-011.1 as far as the code can.
5. **Issue 1 does not block:** it is not a failure of AC-011.6, which covers search failures, and those are handled.
