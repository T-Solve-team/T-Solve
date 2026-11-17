# Test Report: workspace — Task 12: default-project-api

Date: 2026-11-17 · Commit range `db594997..030ed7b7` (code `5a363ceb`) · Branch `feat/KAN-121-workspace-default-project`

## Command
From `backend/`, Git Bash, JAVA_HOME = IntelliJ jbr (Java 21):

```
mvn -B -Dapi.version=1.44 \
  -Dtest='JiraClientTest,TrackerProjectServiceImplTest,DepartmentControllerTest,DepartmentServiceImplTest' \
  -Dit.test='DepartmentRenameIT' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify
```

## Results

| Suite | Tests | Failures | Errors | Skipped |
|-------|-------|----------|--------|---------|
| JiraClientTest | 78 | 0 | 0 | 0 |
| TrackerProjectServiceImplTest | 37 | 0 | 0 | 0 |
| DepartmentControllerTest | 40 | 0 | 0 | 0 |
| DepartmentServiceImplTest | 23 | 0 | 0 | 0 |
| **Surefire total** | **178** | 0 | 0 | 0 |
| DepartmentRenameIT (PostgreSQL, Testcontainers) | 28 | 0 | 0 | 0 |
| spotless:check | clean | | | |

BUILD SUCCESS, exit 0. The `ERROR ... GlobalExceptionHandler: Unhandled exception` lines in the log come from tests that inject failures on purpose (audit failure, IllegalState), not from failing tests.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace workspace STORY-012` → 17/17 acceptance criteria have a test.

## Not run
- Full `mvn verify` (the whole backend suite): only the targeted classes, as dispatched.
- Coverage: not measured.
- The manual staging Jira calls of the Definition of Done: not verifiable here (no session log).
