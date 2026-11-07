---
module: ticket-review
feature: review-queue-events-hardening
task: 15
verdict: pass
attempt: 1
date: 2026-11-08
reviewer: tsolve-code-reviewer
iteration: 1
---

# Review: ticket-review — Task 15: Review Queue event stream hardening (PR #155 review fixes, KAN-154)

## Summary
Branch `feat/KAN-154-review-realtime-queue`, fixes BUG-172/173 (commit d8ac8f4a) and BUG-174..177 (commit `38a0abd9`). Read against AC-012.1 to .7 with no implementer context. The backend keeps one LISTEN connection per Workspace on its own thread, sends through a small pool with per-subscription coalescing, and re-reads the managed Projects at each heartbeat (adds a Project with one `queue-changed`, ends the stream when none is left or the Account is unverified). The frontend stays on polling in mock mode and keeps a 60 s safety refetch while connected. No critical finding. Verdict PASS.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-012.1 to .3 and .5 to .7 still hold; the fixes close the stale-Project, stuck-draining and dead-stream gaps. |
| Completeness | pass | Each bug row (BUG-172 to BUG-177) names a regression test that exists in the tree. |
| Correctness | pass | `ReviewQueueEventServiceImplTest` re-run here: 8 tests, 0 failures. ITs and frontend not run (see Test Report). |
| Integration | pass | `ReviewQueueEventService` / controller contract unchanged; the event name and URL match Task 13/14. |

## Acceptance criteria

| AC | Met | Proving test | Asserts it? |
|----|-----|--------------|-------------|
| 012.1 | yes | `ReviewQueueEventsIT` heartbeat/open tests; `sendPoolSaturatedByStuckStreamsLosesNoEventAndDoesNotBlockHeartbeats_AC_012_2` | yes |
| 012.2 | yes | `projectGrantedMidStreamReceivesEvents_AC_012_2_BUG_172`, `addedProjectGetsQueueChangedAtTheHeartbeat_AC_012_2_BUG_174`, `edge10_aBlockedStreamDoesNotDelayAnotherStreamsEvent_AC_012_2`, IT `edge11_...BUG_172` | yes |
| 012.3 | yes | coalescing through the `pending` set (one event per Project per drain); IT cases for unmanaged Projects | yes |
| 012.4 | yes, unchanged | Task 13 tests | yes |
| 012.5 | yes | `useReviewQueueEvents.test.tsx` (queue-changed invalidates both keys) | yes |
| 012.6 | yes | tests for no polling while open, 5 s polling after error, refetch on every open, BUG-175, BUG-176 | yes |
| 012.7 | yes | unmount and remount tests | yes |

BUG-173 (`drainRecoversAfterAnUnexpectedError_BUG_173`) and BUG-177 (`subscribeFailureAfterAddRemovesTheSubscription_BUG_177`, `joiningSubscriptionHasOneFiveSecondWaitBudget_BUG_177`, `joinBarrierFailureRemovesOnlyTheJoiningSubscription_BUG_177`) are not tied to an AC but protect AC-012.1/.2 behavior.

## Traceability
Per the test report, `bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-012` gives 7/7 (not re-run here).

## Issues
No CRITICAL findings. Informational (do not block):
1. The 60 s safety refetch while connected (BUG-176) goes beyond the literal AC-012.6 ("not run the 5-second polling"); it is not 5 s polling, so the AC holds, but the spec does not mention it. Consider a one-line note in AC-012.6 at the next spec change.
2. The test report quoted only the Codex frontend run (127 tests); it could not be re-run here (no `node_modules` in this worktree).
3. Mock mode (BUG-175) is detected by `import.meta.env.DEV && VITE_API_MOCKING`; a production build with mocks forced on would still open the stream. Acceptable per design.

Checked and fine: `drain` releases `draining` in a `finally` and atomically with the empty-pending check; the heartbeat read failure keeps the stream (`return true`) rather than dropping it; subscribe shares one 5 s budget for LISTEN wait and the join barrier; the effect cleanup removes all three listeners and closes the source.

## Test Report
See `task-15-review-queue-events-hardening-test-report.md`.
