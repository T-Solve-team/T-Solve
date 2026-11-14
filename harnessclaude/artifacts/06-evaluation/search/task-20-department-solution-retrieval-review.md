---
module: search
feature: department-solution-retrieval
task: 20
verdict: fail
attempt: 1
date: 2026-11-08
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: search — Task 20: Department Solution retrieval for the AI Solution Suggestion

## Summary
Commit `1bc7808` (`53b1e8c` before the rebase) adds `SolutionRetrieval.nearestInProjectDepartment(long projectId, …)`,
the record `RetrievedTicketSolution` and a native pgvector query. Read against STORY-011 as written, the code is right:
it returns at most `limit` Published tickets of the Project's Department, at or above the cut-off, nearest first with
the internal ticket id as the tie-breaker, with `public_id` and the five Solution sections only. Bad arguments throw
before any query. The method takes no user, calls no `EmbeddingClient`, writes no `search_log` row and logs nothing.
All 5 criteria have a named test, the feature's tests pass, and the full `mvn verify` at HEAD is green.

The verdict is **fail** on Integration, and the fix starts in the spec. The rebase brought in main's tracker changeset
`019-tracker-suggestion-uuid-keys`. Since then `suggestion_result.project_id`, the only Project id the single intended
caller (triage STORY-005, AC-005.4, "the row's Project") holds, is the Project's `public_id` UUID
(`SuggestionResultRecord.getProjectId()` returns `UUID`). The new contract takes the internal BIGINT `project.project_id`
(AC-011.1, "internal id `projectId`", `long projectId`). No module exposes a way to turn a Project `public_id` into its
internal id: `ProjectService` offers only `findActivePublicIdByJiraProjectKey`. Triage would have to read the `project`
table itself, which `backend/AGENTS.md` §1 forbids, or another module would need a new service. The contract also
breaks `backend/AGENTS.md` §2 (a reference is a UUID, Java type `UUID`). The spec turns out wrong for current main, so
the fix is a spec change (plan skill, Change Mode), then a small code change.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-011.1 to AC-011.5 behave as written. The query is `nearest` plus `d.department_id = (SELECT department_id FROM project WHERE project_id = :projectId)`. An unknown Project gives a NULL subquery and no row. Blank Workaround becomes null. NaN, ±Inf and wrong-length vectors throw before the repository is touched |
| Completeness | pass | Every criterion of STORY-011 has a test that names it (trace 5/5). The interface and record match the spec's Interface Contracts exactly (`long projectId`; `RetrievedTicketSolution(UUID ticketId, symptoms, rootCause, resolutionSteps, workaround, scopeOfApplicability, similarity)`) |
| Correctness | pass | Feature run: unit 6/6, IT 5/5 (Testcontainers pgvector), `spotless:check` clean. Full `mvn verify` at HEAD: 1000 unit + 272 IT, 0 failures. STORY-010 tests still pass after the refactor into `checkedVector` / `blankToNull` |
| Integration | fail | The contract's `long projectId` (internal Project key) does not match what the only caller has after main's `019-tracker-suggestion-uuid-keys`: a Project `public_id` UUID, with no cross-module mapping to the internal key (Issue 1) |

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

1. **HIGH — the contract keys the Project by its internal BIGINT id, but the caller only has the Project `public_id`
   UUID (spec defect, shown by the rebase).**
   - `harnessclaude/artifacts/03-modules/search/spec.md:386` (AC-011.1, "the Project with internal id `projectId`"),
     `:392` (AC-011.3, "no Project has id `projectId`"), `:577-578` (Interface Contracts, `long projectId`).
   - `backend/src/main/java/vn/tsolve/search/retrieval/service/SolutionRetrieval.java:15-16`,
     `backend/src/main/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImpl.java:37-40`,
     `backend/src/main/java/vn/tsolve/search/retrieval/repository/SolutionRetrievalRepository.java:44,50-51`.
   - Evidence: `backend/src/main/resources/db/changelog/changes/019-tracker-suggestion-uuid-keys.sql` makes
     `suggestion_result.project_id` a UUID FK to `project(public_id)`.
     `backend/src/main/java/vn/tsolve/triage/entity/SuggestionResultRecord.java:29,53` exposes it as `UUID`.
     `vn.tsolve.project.service.ProjectService` has no public-id-to-internal-id lookup. `backend/AGENTS.md:102`:
     "a reference is `<entity>_id` (UUID); Java type `UUID`".
   - Fix: change the spec first (Changelog row, re-approval). AC-011.1 / AC-011.3 become "the Project with `public_id`
     `projectId`", and the contract becomes `nearestInProjectDepartment(UUID projectId, float[] queryVector, double
     minSimilarity, int limit)`. Then the code: `UUID projectId` in the interface, the implementation and the
     repository, with the subquery `SELECT department_id FROM project WHERE public_id = :projectId`. The tests follow:
     the IT passes the Project's `public_id` and a random UUID for the unknown Project, and the unit test's reflection
     check uses `UUID.class`. A null `projectId` should be added to the AC-011.4 argument checks.

2. **LOW — the legacy arm of the `project` join is not covered for the new query.**
   `backend/src/test/java/vn/tsolve/search/retrieval/repository/SolutionRetrievalRepositoryIT.java:181-191` always
   writes `ticket.project_id` as the Project's `public_id`. The `OR lpad(to_hex(p.project_id), 32, '0')::uuid =
   t.project_id` arm (`SolutionRetrievalRepository.java:40-41`) is never exercised. A ticket imported before
   `010-ticket-import-project-uuid` that silently dropped out of the Department's results would go unnoticed.
   Fix: in the AC-011.1 IT, add one ticket whose `project_id` is `lpad(to_hex(<internal id>), 32, '0')::uuid` and
   assert it is returned.

Note for the caller, outside this task: the triage design (`harnessclaude/artifacts/04-plans/triage/design.md:220` and
the "Job record id" decision, `new UUID(0, suggestionId)`) still assumes BIGINT `suggestion_id` / `project_id`.
`019-tracker-suggestion-uuid-keys` made both UUIDs, so triage Task 4's plan needs the same update.

### Assumptions made
- I judged the `long projectId` contract to be a spec defect against current main, so this review fails rather than
  passes on spec conformance alone. The evaluate rule "the spec turns out wrong or incomplete → verdict fail" applies.
  The spec text predates `019-tracker-suggestion-uuid-keys` and matched the schema at the time. **Open judgment call
  for the user:** keep `long` and add a `ProjectService` lookup from public id to internal id (a change in the
  `project` module), or switch the contract to the `public_id` UUID (recommended: one query line, follows
  `backend/AGENTS.md` §2, no other module touched).
- "Internal ticket id" in AC-011.1 is read as `ticket.ticket_id`, the UUID primary key since
  `008-review-ticket-storage`. The query and test tie-break on it, the same as STORY-010.
- The Project's own `status` is not filtered. AC-011.3 names only the Department's status and the ticket's status, so
  this is not raised as an issue.

## Test Report
See `harnessclaude/artifacts/06-evaluation/search/task-20-department-solution-retrieval-test-report.md`.
- Traceability: `sdd.ts trace search STORY-011`: 5/5.
- Feature run (JBR 21, Testcontainers pgvector): `mvn -q verify -Dtest=SolutionRetrievalImplTest
  -Dit.test=SolutionRetrievalRepositoryIT …` exit 0. Unit 6/6, IT 5/5, `spotless:check` clean.
- Full `mvn -q verify` at HEAD `11db514` (dispatcher's run, exit 0). Its reports show 1000 unit + 272 IT tests,
  0 failures, 0 errors, 0 skipped.
- Coverage: no JaCoCo configured. Everything is covered by reading except the legacy join arm (Issue 2).
