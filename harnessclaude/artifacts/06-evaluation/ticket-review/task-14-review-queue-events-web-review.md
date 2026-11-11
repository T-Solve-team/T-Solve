---
module: ticket-review
feature: review-queue-events-web
task: 14
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: tsolve-code-reviewer
iteration: 1
---

# Review: ticket-review — Task 14: Review Queue live refresh in the web app (KAN-154)

## Summary
Range `e41f5330..36ee3e44`, frontend code commit `50d2a3f0`. `useReviewQueueEvents` opens a credentialed EventSource while the page is mounted, invalidates the queue and ticket-ids queries on `queue-changed` and on every open, and closes on unmount; `useReviewQueue` drops the 5 s polling while connected. No critical finding. Verdict PASS.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-012.5 to .7 met (table below). |
| Completeness | pass | Each AC has an asserting test; no EXTRA behavior. |
| Correctness | pass | `npx vitest run src/features/review` 13 files, 125 tests passed on the clean re-run (see Test Report for one load-induced flake). |
| Integration | pass | URL and event name match the contract; generated client regenerated without diff; selection and stale-data rules untouched (invalidate only). |

## Acceptance criteria

| AC | Met | Proving test | Asserts it? |
|----|-----|--------------|-------------|
| 012.5 | yes | `useReviewQueueEvents.test.tsx` (queue-changed invalidates both keys; unrelated event ignored), `ReviewQueueLive.test.tsx` (selection kept after refetch, `dataUpdatedAt` advances) | yes |
| 012.6 | yes | `does not poll while the stream is open` (`refetchInterval` false after open), `polls every 5 s again after the stream fails`, `refetches the queue once on every (re)open` | yes; the option value is checked, not a timer, which is a fair proxy |
| 012.7 | yes | `closes the stream when the screen unmounts`, remount test, page-level test | yes: `source.closed` true after unmount and listeners removed |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-012` → 7/7 acceptance criteria have a test.

## Issues
No CRITICAL findings. Informational (do not block):
1. `frontend/src/features/review/hooks/useReviewQueueEvents.ts:19-22` — `onOpen` invalidates both the queue and the ticket-ids queries, while AC-012.6 says refetch the queue once; the extra ticket-ids refetch is harmless and consistent with AC-012.5, but the 012.6 test checks only the queue key count.
2. `useReviewQueueEvents.ts:25` — a 401/403 on the stream makes the browser close the EventSource for good (no retry), so the page keeps polling; correct, but no test covers a permanently closed stream (only a retrying `error`).
3. Mock mode (MSW) has no stream, so the hook opens an EventSource to a missing URL and falls back to polling; matches design, noted for demo builds.

Checked and fine: effect cleanup removes all three listeners and closes the source; `typeof EventSource` guard; `live` default false keeps existing callers on polling; no ticket data is read from the event; effect deps stable (`queryClient` only), so no reconnect loop; the existing 1 lint warning is in `TicketListFilters.tsx`, not in this diff.

## Test Report
See `task-14-review-queue-events-web-test-report.md`.
