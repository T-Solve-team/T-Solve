# Task 20: Department Solution retrieval for the AI Solution Suggestion — KAN-47

**Module:** search
**Assignee:** Trần Hải Anh
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-07 21:20

### Working on: department-solution-retrieval
- `SolutionRetrieval.nearestInProjectDepartment(projectId, queryVector, minSimilarity, limit)` returns
  `RetrievedTicketSolution` (ticket `public_id` + five sections + similarity). Argument checks shared with `nearest`
  in `SolutionRetrievalImpl.checkedVector` (AC-011.4); blank Workaround → null.
- `SolutionRetrievalRepository.nearestInProjectDepartment`: the `nearest` native query plus
  `d.department_id = (SELECT department_id FROM project WHERE project_id = :projectId)`; unknown Project → empty.
  `TicketSolutionRow extends SolutionRow` adds `getTicketId()`.
- Test first: `SolutionRetrievalImplTest` (AC-011.2, 011.4, 011.5; the AC-010.4 check of "one method" moved to
  "two methods" in AC-011.5), `SolutionRetrievalRepositoryIT` (AC-011.1, 011.3).
- Files: `search/retrieval/dto/RetrievedTicketSolution.java` (new), `service/SolutionRetrieval.java`,
  `service/impl/SolutionRetrievalImpl.java`, `repository/SolutionRetrievalRepository.java`, and the two tests.
- Commit: default@53b1e8c on `feat/KAN-47-triage-ai-suggestion`.
- No deviation from the plan.

### Verification
- `bun harnessclaude/hooks/sdd.ts trace search STORY-011`: 5/5 acceptance criteria have a test.
- `mvn verify` (JAVA_HOME = IntelliJ JBR 21.0.6): 887 unit + 212 IT, 0 failures; Spotless check passed.
- Environment notes: the machine's `JAVA_HOME` points to a missing `jdk-21` (JDK 24 breaks Mockito); Testcontainers
  needed `~/.docker-java.properties` `api.version=1.44` for Docker 29.

### Blockers
none

### Next up
tracker Task 14 `issue-content-event`.
