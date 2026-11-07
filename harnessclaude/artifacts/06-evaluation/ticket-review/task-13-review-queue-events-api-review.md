---
module: ticket-review
feature: review-queue-events-api
task: 13
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: tsolve-code-reviewer
iteration: 1
---

# Review: ticket-review — Task 13: Review Queue event stream (KAN-154)

## Summary
Range `e41f5330..36ee3e44` on `feat/KAN-154-review-realtime-queue`, backend code commit `b539777f`. The SSE endpoint, the tenant NOTIFY trigger and the per-Workspace listener meet AC-012.1 to AC-012.4. No critical finding. Verdict PASS; the informational findings do not block.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-012.1 to .4 met (table below). |
| Completeness | pass | Each AC has an asserting test in `ReviewQueueEventsIT`. No EXTRA behavior beyond the spec; the 25 s heartbeat, 30 min emitter timeout and subscribe-time membership snapshot are written in design.md. |
| Correctness | pass | `ReviewQueueEventsIT` 14/14 re-run; all 24 `*IT` classes ran in the same `mvn verify` re-run with 0 failures. |
| Integration | pass | Contract matches spec Interface Contracts (path, event name, `{"projectId"}` payload, 401/403 Problem). OpenAPI entry added before code; `generate:api` no diff (orchestrator). |

## Acceptance criteria

| AC | Met | Proving test | Asserts it? |
|----|-----|--------------|-------------|
| 012.1 | yes | `streamOpensAsEventStreamAndSendsHeartbeats_AC_012_1` | yes: content type text/event-stream, async started, a second `:hb` arrives (heartbeat PT1S in test; production default PT25S is below 30 s) |
| 012.2 | yes | `ticketEnteringPendingReview…`, `ticketUpdatedToPendingReview…`, `decisionMovingTicketOutOfPendingReview…` (real request-changes POST), `edge1_ticketMovedToAnotherProject…` | yes: event for the Project within 5 s; the move case gets both Projects |
| 012.3 | yes | `edge2` (unmanaged Project), `edge3` (other Workspace), `edge4` (rollback), `edge5` (3 tickets, one event per Project), `edge6` (payload is only `projectId`) | yes: each uses a later sentinel event so absence is proven, not assumed |
| 012.4 | yes | `deniesAnonymousCallers` (401), `deniesCallersWithoutProjectManager`, `deniesManagerWithoutManagedProjects`, `deniesUnverifiedWorkspaceAccount` (403) | yes |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-012` → 7/7 acceptance criteria have a test.

## Issues
No CRITICAL findings. Informational (do not block):
1. `backend/src/main/java/vn/tsolve/review/service/impl/ReviewQueueEventServiceImpl.java:73` — `new Listener(...)` does a blocking DB connect and LISTEN inside the global `synchronized (lock)`; a slow tenant DB stalls subscribe and unsubscribe for every Workspace. Fix: build the listener outside the lock, publish it after.
2. `ReviewQueueEventServiceImpl.java:128,317` — `emitter.send` runs on the shared per-Workspace listener thread and on the single heartbeat thread; one stalled client delays events and heartbeats for the others. Fix: hand off per emitter or add a send timeout.
3. `ReviewQueueEventsIT.java` — no test for several open streams, last-subscriber close (listener thread and connection released), reconnect resync, or the join barrier (exercised only implicitly, because streams of earlier tests stay open). Streams are never completed in tests, so listeners live until the context closes. Not flaky (waits poll up to 5 s on a state), but those lifecycle paths are unproven.
4. `backend/src/main/resources/db/changelog/changes/017-review-queue-notify.sql` — the KAN-24 branch adds `017-project-membership.sql` after 016 in the same master changelog and edits the same `ReviewStorageIT` area. File names and changeset ids differ, so Liquibase is fine, but the second branch to merge gets a textual conflict; renumber to 018 if KAN-24 merges first.
5. `017-review-queue-notify.sql:19` — the exit branch fires for any leaving ticket, not only Solution Form ones; a DELETE of a pending ticket sends nothing. Harmless (Project id only, refetch is idempotent).
6. One tenant pool connection per Workspace with an open stream is held for the whole stream (as designed); no pool-size check or metric.

Checked and fine: `@PreAuthorize` plus 403 for empty managed set or unverified Workspace; logs carry only the exception class; listener closed on last unsubscribe and `@PreDestroy`; completion, timeout and error callbacks all call the same idempotent `unsubscribe`; heartbeat scheduled after LISTEN is attached; workspace bound with `TenantScope.runIn` on both the request-thread connect and the listener-thread reconnect; trigger covers enter, exit and project move while pending; `pom.xml` postgresql `runtime` to compile scope is needed for `PGConnection` and changes no version; the `ReviewStorageIT` regex edit only keeps its old-schema replay working. Not covered by any test: async re-dispatch after the 30 min timeout and proxy buffering (MockMvc cannot show them).

## Test Report
See `task-13-review-queue-events-api-test-report.md`.
