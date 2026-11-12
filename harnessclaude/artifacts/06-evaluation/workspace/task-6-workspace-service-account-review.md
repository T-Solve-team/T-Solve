---
module: workspace
feature: workspace-service-account
task: 6
verdict: fail
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 6: Service account for tracker, remove the stand-in

## Summary
Commit `default@2969d0a` (STORY-004). The class itself is correct for a web request: it reads the bound Workspace's
row on every call, decrypts with the Workspace id as AAD, returns empty for no row / incomplete row / decrypt failure,
and logs only the Workspace id. The stand-in, its test, its three settings and its env variables are gone (repo-wide
grep finds no reference), and the AC-004.2 test really component-scans `vn.tsolve` for `JiraServiceAccount` beans.

It fails because the only consumer of `JiraServiceAccount` runs in a worker job, where this implementation cannot
find the Workspace and throws. Removing the stand-in therefore turns the tracker's MSG80 suggestion comment (tracker
STORY-012) from "posted" into "job failed" — a regression no test catches because every consumer test mocks
`JiraServiceAccount`. The spec is incomplete on the same point (it names only the request's `TenantScope`).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | Inside a web request AC-004.1/004.3 hold. In the worker (the real caller) `current()` throws instead of returning the account or empty (Issue 1). |
| Completeness | pass | 3/3 ACs have tests that check them; no EXTRA behaviour. |
| Correctness | fail | Recorded verify: `mvn -B verify` BUILD SUCCESS (1588 unit + 481 IT), not re-run by the reviewer (dispatcher's low-RAM limit). CRITICAL Issue 1 is untested runtime behaviour. |
| Integration | fail | `tracker` consumer `SuggestionCommentServiceImpl` is reached only through the worker's `TenantExecutionService`, which binds `WorkerTenantService`, not identity's `TenantScope`. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-004.1 | PASS (request context only) | `WorkspaceJiraServiceAccount.java:33-53`; tests: account, no row, incomplete row, re-read per call |
| 2 | AC-004.2 | PASS | stand-in, its test and `tsolve.tracker.jira.site-url/service-account-*` removed; test "AC-004.2 … exactly one JiraServiceAccount bean" scans `vn.tsolve` |
| 3 | AC-004.3 | PASS | `:46-52`; tests tampered / other-workspace AAD / wrong key, ERROR log without token, email, ciphertext or throwable |

## Traceability
```
$ bun harnessclaude/hooks/sdd.ts trace workspace STORY-004
  ✓ AC-004.1  WorkspaceJiraServiceAccountTest.java
  ✓ AC-004.2  WorkspaceJiraServiceAccountTest.java
  ✓ AC-004.3  WorkspaceJiraServiceAccountTest.java
trace workspace (STORY-004): 3/3 acceptance criteria have a test
```

## Issues

CRITICAL (blocks pass):
1. `backend/src/main/java/vn/tsolve/workspace/service/impl/WorkspaceJiraServiceAccount.java:34` — the account is
   unreachable from the worker, its only caller.
   - `scope.currentWorkspaceId()` is identity's `LocalTenantScope`, a ThreadLocal bound only by
     `identity/session/WebSessionFilter` (web requests); unbound it throws
     `IllegalStateException("no Workspace is bound to this thread")` (`identity/signin/port/LocalTenantScope.java:17-22`).
   - The sole consumer path is a worker job: `TenantJobRequestHandler` → `TenantExecutionServiceImpl.runInTenant`
     (`worker/service/impl/TenantExecutionServiceImpl.java:27`, binds `WorkerTenantService` — a different
     ThreadLocal in `MockWorkerTenantService`) → `SuggestionJobServiceImpl.run` (`tracker/…/SuggestionJobServiceImpl.java:64`)
     → `SuggestionCommentServiceImpl.post` (`tracker/…/SuggestionCommentServiceImpl.java:36`) → `current()`.
   - Result: the call at `:34` (outside the `try`) throws, `SuggestionJobServiceImpl.run` rethrows it as a job
     failure, and the MSG80 comment is never posted. Before this commit the stand-in answered from configuration and
     the comment was posted. Other worker-run code in the repo (`review/…/ReviewExpiryServiceImpl.java:35`,
     `worker/…/ReviewNotificationEnqueuer.java:45`) reads the Workspace from `WorkerTenantService`, confirming the
     two bindings are separate today.
   - No test catches it: `SuggestionCommentServiceImplTest` mocks `JiraServiceAccount`, and
     `WorkspaceJiraServiceAccountTest` mocks `TenantScope` as always bound.
   Fix (pick one, then add a test that runs `current()` inside `TenantExecutionService.runInTenant` with no web
   binding):
   a. Preferred, smallest blast radius for every future worker-side caller: have `TenantExecutionServiceImpl.runInTenant`
      also bind identity's `TenantScope` (`scope.runIn(workspaceId, …)`) — worker module code, so a change request
      to `worker` unless the user adds the file to this module.
   b. Inside `workspace`: resolve the Workspace from `TenantScope` when bound, else from
      `ObjectProvider<WorkerTenantService>.getIfAvailable().currentWorkspaceId()`, else return empty.
   Spec: STORY-004 / "Required from other modules" names only `TenantScope.currentWorkspaceId()` "(the bound Workspace
   of the request)"; AC-004.1 must say which binding a worker job uses (spec change first, plan skill Change Mode).

MINOR:
2. `WorkspaceJiraServiceAccount.java:49-51` — even if the unbound case were moved inside the `try`, it would be
   logged as "could not be decrypted"; keep the "no Workspace bound" case separate from the decrypt failure.

## Assumptions made
- **Open judgment call (for the user):** whether the worker binding is this module's job or a platform/worker
  change request. I rated it CRITICAL because the spec's user story is "As `tracker` and the Chatbot, I want the
  current Workspace's service account" and the delivered bean cannot serve `tracker`'s only call site, a regression
  of tracker STORY-012 introduced by this commit's removal of the stand-in. If the user accepts "suggestion comments
  stop until platform unifies the bindings", this can be downgraded with a spec note — but then AC-004.2's removal
  should wait for that.
- Severity assumes the worker profile is a supported way to run the stack today (`worker-mock`; there is no other
  `WorkerTenantService` implementation in the repo).
- Tests were not re-run by the reviewer (dispatcher's low-RAM limit); the orchestrator's recorded verify is used.

## Test Report
See `task-6-workspace-service-account-test-report.md`. Recorded: `mvn -B verify` BUILD SUCCESS — 1588 unit + 481 IT
(WorkspaceJiraServiceAccountTest 10). Trace 3/3 (run by the reviewer).
