---
module: workspace
feature: workspace-service-account
task: 6
verdict: pass
attempt: 2
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 6: Service account for tracker, remove the stand-in

## Summary
Commits `default@2969d0a` (original) and `default@df3d89e` (fix), covering STORY-004. AC-004.1 now says that the bound
Workspace is the one of the running worker job (`WorkerTenantService`), or else that of the web request
(`TenantScope`) (spec Changelog 2026-11-12, D28, re-approved by the user).

The fix injects `ObjectProvider<WorkerTenantService>` and resolves
`worker.currentWorkspaceId()` → else `scope.currentWorkspaceId()` (`WorkspaceJiraServiceAccount.java:44-47`). This
closes the attempt-1 CRITICAL:
- The suggestion job runs under `TenantExecutionServiceImpl.runInTenant`, which binds only
  `MockWorkerTenantService`'s ThreadLocal. `current()` now reads that Workspace instead of hitting the unbound
  `LocalTenantScope` and throwing.
- In a web request the worker binding is empty, because `MockWorkerTenantService.currentWorkspaceId()` returns only
  the ThreadLocal, with no default. `TenantScope` then answers, as before.
- When no `WorkerTenantService` bean exists (worker profile off), `getIfAvailable()` returns null and `TenantScope` is
  used.
- `WorkerTenantService` is the `worker` module's `service/` port, so the dependency follows `backend/AGENTS.md` §1.

There are three new AC-004.1 tests:
- a worker binding is used and `TenantScope` is never touched (`verifyNoInteractions(scope)`);
- a worker bean with no binding falls back to the scope;
- with no worker bean, the scope is used.

I re-checked the whole feature against the current spec. AC-004.2 and AC-004.3 are unchanged and still met. Verdict:
**pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `current()` `:41-66`: resolve the Workspace (worker, else scope) → no row or incomplete row → empty; decrypt with the Workspace id as AAD; on decrypt failure, an ERROR log with the id only, then empty. |
| Completeness | pass | 3/3 ACs have tests that check them; no EXTRA behaviour. |
| Correctness | pass | Orchestrator's verify after the four fixes: `mvn -B verify` — 1594 unit + 481 IT, BUILD SUCCESS. Not re-run by the reviewer (brief: low-RAM machine). No CRITICAL finding. |
| Integration | pass | The only consumer path (`TenantJobRequestHandler` → `TenantExecutionServiceImpl.runInTenant` → `SuggestionJobServiceImpl` → `SuggestionCommentServiceImpl.post` → `current()`) now resolves the job's Workspace. Web callers are unchanged. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-004.1 | PASS | `:44-61`. Tests: account returned, no row, incomplete row, re-read on every call; plus the new worker binding (`WorkspaceJiraServiceAccountTest.java:112`), the fallback with an unbound worker (`:129`), and the fallback with no worker bean (`:144`) |
| 2 | AC-004.2 | PASS | The stand-in, its test and the `tsolve.tracker.jira.site-url/service-account-*` settings are removed. The test "AC-004.2 … exactly one JiraServiceAccount bean" scans `vn.tsolve` |
| 3 | AC-004.3 | PASS | `:59-65`. Parameterised tests (tampered, other-workspace AAD, wrong key): an ERROR log with no token, email, ciphertext or throwable, and the result is empty |

## Traceability
```
$ bun harnessclaude/hooks/sdd.ts trace workspace STORY-004
  ✓ AC-004.1  backend/src/test/java/vn/tsolve/workspace/service/impl/WorkspaceJiraServiceAccountTest.java
  ✓ AC-004.2  backend/src/test/java/vn/tsolve/workspace/service/impl/WorkspaceJiraServiceAccountTest.java
  ✓ AC-004.3  backend/src/test/java/vn/tsolve/workspace/service/impl/WorkspaceJiraServiceAccountTest.java
trace workspace (STORY-004): 3/3 acceptance criteria have a test
```

## Issues
CRITICAL: none. IMPORTANT: none.

MINOR (do not block):
1. (new) The worker test mocks `WorkerTenantService.currentWorkspaceId()`. It does not run `current()` inside the real
   `MockWorkerTenantService.runIn` / `TenantExecutionService.runInTenant`. The unit test proves the order of
   resolution, and the mock's contract is a plain ThreadLocal. An end-to-end test through `runInTenant` would also
   guard against a future change in the worker binding. Fix (optional): add one test that wires
   `MockWorkerTenantService` and calls `current()` from inside `runIn`.
2. (new) Spec `harnessclaude/artifacts/03-modules/workspace/spec.md`, "Required from other modules", still lists only
   `identity: TenantScope.currentWorkspaceId()`. It should also list `worker: WorkerTenantService.currentWorkspaceId()`,
   which AC-004.1 now names. Fix: one line in the spec (a documentation fix; the behaviour is in AC-004.1).
3. (carried) `WorkspaceJiraServiceAccount.java:44-47`: when neither binding is present, `LocalTenantScope` still throws
   `IllegalStateException("no Workspace is bound to this thread")`. The spec does not cover this case. It is a
   programming error (a call from an unbound thread), not a decrypt failure, so it is kept apart from the AC-004.3 log
   as attempt 1 asked.

## Attempt 1
Verdict fail (`2969d0a`).
- CRITICAL: `current()` read only identity's `TenantScope`, which a worker job never binds. The tracker suggestion
  job, the only consumer, threw, so the MSG80 comment stopped being posted after the stand-in was removed. The spec
  named only the request binding. **Resolved**: AC-004.1 was reworded (D28, re-approved) and the code was fixed in
  `df3d89e` (worker binding first, then `TenantScope`), with three AC-004.1 tests.
- MINOR (keep "no Workspace bound" separate from the decrypt failure). **Holds**: the resolution sits outside the
  `try`, so an unbound thread is never logged as a decrypt failure (MINOR 3).

## Assumptions made
- The attempt-1 open judgment call (should this module or `worker` fix the binding?) is settled by D28 and the spec
  change, which the user re-approved. No open call remains.
- The suggestion-job path is judged by reading the code (`MockWorkerTenantService` is the only `WorkerTenantService`
  in the repo). No end-to-end worker test was run (see MINOR 1).
- Tests were not re-run by the reviewer. The dispatcher's brief forbids `mvn` on this low-RAM machine. The
  orchestrator's recorded verify (session log, "Session 2026-11-13") is used.

## Test Report
See `task-6-workspace-service-account-test-report.md`. Recorded: `mvn -B verify` BUILD SUCCESS (1594 unit + 481 IT).
Trace 3/3 (run by the reviewer).
