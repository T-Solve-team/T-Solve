---
module: platform
feature: one-tenant-holder
task: KAN-384 Task 6a-1 (one Workspace holder)
verdict: fail
attempt: 1
date: 2026-11-23
reviewer: tsolve-code-reviewer
iteration: 1
---

# Review: platform — KAN-384: one Workspace holder (Task 6a-1)

## Conclusion

Attempt 1 concluded "fix first": 1 critical (12 unit tests failing in the context runners of
`DiscoveryCheckTest`, `IdentityClockTest` and `WorkerConfigurationTest`), 1 should-fix (the mock's request branch
could queue a job for a Suspended or unlisted bound Workspace), 6 notes.

## Fixes

- Both findings fixed in commits 68125e49 (test contexts given the Workspace beans), 99e124f1 (BUG-324 guard tests
  for Suspended and unlisted request Workspaces) and 8956277a (no job queued for a bound Workspace that is not active).
- The developers re-verified with targeted tests (`mvn spotless:check test` on the affected classes, green).
- No second review by decision of Hiếu. The full run is CI on the PR.

## Notes carried to Task 6a-2

- Drop the 2-arg `MockWorkerTenantService` constructor (accept-all holder) and the dead request branch.
- `.env.example`: with `identity-mock` on, the mock id must be in `TSOLVE_STUB_WORKSPACES`.
- Before the staging smoke check, confirm that `TSOLVE_MOCK_ACTIVE_WORKSPACES`, `TSOLVE_TRACKER_DEV_WORKSPACE_ID` and
  `TSOLVE_STUB_JIRA_SITES` name the `TSOLVE_STUB_WORKSPACES` id.
- Task 6: unbound pools that do DB work (`InProcessSuggestionJobQueue`, `TicketsPublishedListener`,
  `SolutionFormAiImpl` rewrite pool).
- Trace: add `backend/src/test/java/vn/tsolve/identity/**` to the platform trace paths (harness change).
- `WorkerTenantService.java` class comment still says "the mock supplies context propagation".
CI on PR #320: green on 8063b119 (it-other failure BUG-323 IT setup fixed in 8063b119).
