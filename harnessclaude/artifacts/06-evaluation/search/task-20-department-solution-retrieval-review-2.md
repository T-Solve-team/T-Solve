---
module: search
feature: department-solution-retrieval
task: 20
verdict: pass
attempt: 2
date: 2026-11-08
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: search — Task 20: Department Solution retrieval for the AI Solution Suggestion

## Summary
This review covers the feature as a whole at HEAD `f8586ac`: the original commit `1bc7808` plus the fix `a0f9151`.
It is checked against the STORY-011 text that was changed and re-approved on 08/11 (Changelog row 2026-11-08).
`sdd.ts check search` reports that the spec has not changed since its approval at 2026-11-07T19:09:11Z.

Both findings from attempt 1 are fixed:
- Issue 1 (HIGH, contract keyed by the internal BIGINT id): the spec now keys the Project by its `public_id`. The
  interface, the implementation and the repository take a `UUID projectId`, and the subquery is
  `SELECT department_id FROM project WHERE public_id = :projectId`. A null `projectId` throws
  `IllegalArgumentException` before any query runs. The tests pass the Project's `public_id`, and a random UUID for
  the unknown Project.
- Issue 2 (LOW, the legacy join arm was not covered): the AC-011.1 IT adds a ticket whose `project_id` is
  `lpad(to_hex(<internal id>), 32, '0')::uuid` and asserts that it comes back, in the right order.

The method's signature now matches the only id its intended caller has: triage's `suggestion_result.project_id`
(a UUID that references `project.public_id`). The fix touched only the 5 search retrieval files, and nothing outside
`vn.tsolve.search.retrieval` calls `nearestInProjectDepartment`. Verdict: **pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-011.1: at most `limit` Published tickets of the Department that owns the Project with `public_id = projectId`, at or above the cut-off, nearest first, with `t.ticket_id` (internal key) as the tie-breaker. Both arms of the `project` join are tested. AC-011.2: the query selects only `t.public_id`, the five sections and the similarity, and a blank Workaround becomes null. AC-011.3: unpublished tickets, other Departments, a DELETED Department, tickets below the cut-off and an unknown `public_id` (NULL subquery) all give no row. AC-011.4: a null `projectId`, a null or wrong-length vector, a NaN or infinite `minSimilarity`, a `minSimilarity` outside 0 to 1, a `limit` below 1 and non-finite vector components all throw before the repository is called. AC-011.5: the method takes no user argument, the class has only the repository field (no `EmbeddingClient`), no `search_log` row is written, and nothing is logged |
| Completeness | pass | Trace 5/5. The interface and record match the spec's Interface Contracts: `nearestInProjectDepartment(UUID projectId, float[] queryVector, double minSimilarity, int limit)` and `RetrievedTicketSolution(UUID ticketId, symptoms, rootCause, resolutionSteps, workaround, scopeOfApplicability, similarity)` |
| Correctness | pass | My feature run: unit 6/6 and IT 5/5 (Testcontainers pgvector), with `spotless:check` clean, exit 0. Full `mvn -q verify` on the fixed code (dispatcher's run): exit 0, and its reports show 1000 unit and 272 IT tests with 0 failures, 0 errors and 0 skipped. The STORY-010 tests still pass |
| Integration | pass | The `UUID projectId` (the Project's `public_id`) is what triage holds (`SuggestionResultRecord.getProjectId()` returns `UUID`), and it follows `backend/AGENTS.md` §2. No cross-module lookup is needed. Nothing else in `backend/src` calls the method yet. Triage Task 4 is the intended caller |

## Traceability

```text
  ✓ AC-011.1  backend/src/test/java/vn/tsolve/search/retrieval/repository/SolutionRetrievalRepositoryIT.java
  ✓ AC-011.2  backend/src/test/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImplTest.java
  ✓ AC-011.3  backend/src/test/java/vn/tsolve/search/retrieval/repository/SolutionRetrievalRepositoryIT.java
  ✓ AC-011.4  backend/src/test/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImplTest.java
  ✓ AC-011.5  backend/src/test/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImplTest.java
trace search (STORY-011): 5/5 acceptance criteria have a test
```

## Issues

No blocking issues.

1. **INFO — the comment names the wrong changeset.**
   `backend/src/test/java/vn/tsolve/search/retrieval/repository/SolutionRetrievalRepositoryIT.java:130` says "A
   ticket imported before changeset 008 holds the hex form of the internal project id". The changeset that writes
   the hex form is `010-ticket-import-project-uuid.sql` (lines 7-9, `lpad(to_hex(project_id), 32, '0')::uuid`).
   `008-review-ticket-storage` does not touch `project_id`. Fix: change the comment to "changeset 010", whenever the
   file is next edited. The test's behavior is correct.

Carried over from attempt 1 for the caller (outside this task): the triage plan for Task 4
(`harnessclaude/artifacts/04-plans/triage/design.md`) should call `nearestInProjectDepartment` with
`suggestion_result.project_id` (UUID) as it is. No conversion is needed.

### Assumptions made
- "Internal ticket id" in AC-011.1 means `ticket.ticket_id`, the UUID primary key since `008-review-ticket-storage`.
  The query and the IT break ties on it, the same way STORY-010 does. (Same as attempt 1.)
- The Project's own `status` is not filtered. AC-011.3 names only the Department's status and the ticket's status, so
  this is not an issue. If an archived or deleted Project should give an empty list, that is a spec change. (Same as
  attempt 1. This is an open judgment call for the user.)
- I did not rerun the full `mvn verify` myself. I accepted the dispatcher's full run on the same content as `a0f9151`
  (exit 0) for two reasons: its surefire and failsafe XML reports in `backend/target` agree with it (1000 unit + 272
  IT, 0 failures), and between that run and HEAD only harness artifacts changed (`f8586ac`). My feature run
  re-executed the affected tests and `spotless:check`.
- The dispatcher said not to change statuses. So I did not run `sdd.ts feature search department-solution-retrieval
  passing`, which the reviewer rules would otherwise call for. The main session should run it.

## Test Report
See `harnessclaude/artifacts/06-evaluation/search/task-20-department-solution-retrieval-test-report.md`.
- Traceability: `sdd.ts trace search STORY-011`: 5/5.
- Feature run (JBR 21, Testcontainers pgvector): `mvn -q verify -Dtest=SolutionRetrievalImplTest
  -Dit.test=SolutionRetrievalRepositoryIT …` exited 0. Unit 6/6, IT 5/5, `spotless:check` clean.
- Full `mvn -q verify` on the fixed code (dispatcher's run): exited 0. 1000 unit + 272 IT, 0 failures, 0 errors,
  0 skipped.
- Coverage: no JaCoCo is configured. From reading the code and tests, all branches of the new code are exercised,
  including both arms of the `project` join.
