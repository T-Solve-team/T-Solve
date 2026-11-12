# Test Report: workspace — Task 6 workspace-service-account (attempt 2)

**Commits:** default@2969d0a (original), default@df3d89e (fix) · **Date:** 2026-11-13 · **Reviewer:** harnessclaude-reviewer · **Iteration:** 1

The suites were run by the orchestrator, not by the reviewer. The dispatcher's brief forbids `mvn` on this low-RAM
machine. Source: `artifacts/05-implementation/workspace/task-6-workspace-service-account.md`, section
"Session 2026-11-13".

## Backend (recorded)
- Command: `mvn -B verify` (tests + `spotless:check`) in `backend/`, on the tree with all four fixes
  (52e87dc, b18a62f, 92f0118, df3d89e)
- Result: BUILD SUCCESS: 1594 unit + 481 integration tests
- New tests in `WorkspaceJiraServiceAccountTest`:
  - "AC-004.1 a worker job reads the Workspace bound by WorkerTenantService without touching TenantScope"
  - "AC-004.1 falls back to TenantScope when the worker binds no Workspace"
  - "AC-004.1 falls back to TenantScope when no WorkerTenantService bean exists"

## Frontend
No frontend change in this feature.

## Traceability (run by the reviewer)
```
$ bun harnessclaude/hooks/sdd.ts trace workspace STORY-004
trace workspace (STORY-004): 3/3 acceptance criteria have a test
```

## Reviewer checks
- Attempt 1 grep (no `ConfiguredJiraServiceAccount`, `JIRA_SERVICE_ACCOUNT` or `service-account-token` outside
  `harnessclaude/`) still applies: the fix touches only `WorkspaceJiraServiceAccount` and its test.
- The only `WorkerTenantService` implementation is `MockWorkerTenantService` (profile `worker-mock`). Its
  `currentWorkspaceId()` returns only the ThreadLocal binding, with no default, so a web request falls through to
  `TenantScope`.

## Attempt 1
The 2026-11-12 run used `mvn -B verify`: 1588 unit + 481 IT, BUILD SUCCESS. Its gap (no test for `current()` under a
worker binding; that path threw) is closed by the new tests. A worker-chain test end to end is still absent (review
MINOR 1).

## Coverage
Not measured in the recorded run.
