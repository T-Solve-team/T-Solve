# Test Report: search — Task 20: Department Solution retrieval (department-solution-retrieval, KAN-47)

**Date:** 2026-11-08
**Change under test:** `1bc7808^..1bc7808` (repo `default`, monorepo root; named `53b1e8c` in the session log before
the rebase), on branch `feat/KAN-47-triage-ai-suggestion` at HEAD `11db514` (rebased onto `origin/main` `19e7a30`;
working tree clean). Backend only, 6 files under `backend/src/{main,test}/java/vn/tsolve/search/retrieval/`.
**JDK:** IntelliJ JBR 21 (`JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"`); Testcontainers on
`pgvector/pgvector:pg16` (Docker running).

## Commands

| # | Command (from `backend/` unless noted) | Exit | Result |
|---|----------------------------------------|------|--------|
| 1 | `bun harnessclaude/hooks/sdd.ts trace search STORY-011` (project root) | 0 | 5/5 acceptance criteria have a test |
| 2 | `mvn -q verify -Dtest=SolutionRetrievalImplTest -Dit.test=SolutionRetrievalRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false` (includes `spotless:check`, bound to `verify`) | 0 | unit 6/6, IT 5/5, spotless clean |
| 3 | Full `mvn -q verify` at HEAD `11db514` (run by the dispatcher; log `C:\Users\admin\AppData\Local\Temp\claude\a8dc05aea7f586a9\c993eb01-fa79-464e-a173-1175aa94792c\scratchpad\mvn-verify-rebase.log`) | 0 (reported) | Cross-checked against the surefire/failsafe XML reports it left in `backend/target`: 90 unit suites, 1000 tests; 35 IT suites, 272 tests; 0 failures, 0 errors, 0 skipped |

Run 3 was not repeated: no code changed between it and HEAD (`11db514` only adds the session log), and run 2 re-ran the
feature's tests and the formatter on the same tree.

## Feature tests (run 2)

`SolutionRetrievalImplTest` (surefire, 6 tests, 0 failures, 2.8 s):
- `rejectsBadDepartmentArguments_AC_011_4` — pass
- `returnsTicketIdAndSections_AC_011_2` — pass
- `takesNoUserAndEmbedsNothing_AC_011_5` — pass
- `rejectsBadArguments_AC_010_5`, `returnsSectionsWithoutTicketDetails_AC_010_2`, `takesNoUserAndEmbedsNothing_AC_010_4` — pass (STORY-010 regression; the refactor moved its checks into a shared helper)

`SolutionRetrievalRepositoryIT` (failsafe, Testcontainers, 5 tests, 0 failures, 21.9 s):
- `returnsNearestOfTheProjectsDepartment_AC_011_1` — pass
- `leavesOutOtherDepartmentsUnpublishedInactiveOrFar_AC_011_3` — pass
- `ignoresTheUsersDepartments_AC_010_1`, `returnsNearestAboveCutOff_AC_010_1_BUG_153`, `leavesOutUnpublishedInactiveOrFar_AC_010_3` — pass (STORY-010 regression)

## Traceability

```text
  ✓ AC-011.1  backend/src/test/java/vn/tsolve/search/retrieval/repository/SolutionRetrievalRepositoryIT.java
  ✓ AC-011.2  backend/src/test/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImplTest.java
  ✓ AC-011.3  backend/src/test/java/vn/tsolve/search/retrieval/repository/SolutionRetrievalRepositoryIT.java
  ✓ AC-011.4  backend/src/test/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImplTest.java
  ✓ AC-011.5  backend/src/test/java/vn/tsolve/search/retrieval/service/impl/SolutionRetrievalImplTest.java
trace search (STORY-011): 5/5 acceptance criteria have a test
```

## Coverage

No coverage tool (JaCoCo) is configured in `backend/pom.xml`. By reading: both branches of the argument check, both
branches of `blankToNull`, the row mapping, and the query's filters (other Department, unpublished, inactive
Department, below the cut-off, unknown Project, limit, tie-break) are exercised. Not exercised by the new tests: a
ticket whose `project_id` holds the legacy hex form of the internal Project id (the second arm of the `project` join).
