# Task 20: Review email through the worker outbox

**Module:** ticket-review
**Assignee:** Phạm Tùng Dương (implementation by Codex, review by Claude)
**Status:** evaluating
**Iteration:** 1

## Session 2026-11-14

### Working on: review-email-outbox

- KAN-162, after PR 230 (KAN-333) was merged into the branch (merge eab3d9dc). User approved the merged spec, version de462e28: AC-010.1–010.6 follow the outbox, AC-010.2 and AC-010.7–010.9 keep the KAN-333 HTML email but compose it when the decision commits.
- `ReviewNotificationEnqueuer` (AFTER_COMMIT listener) now reads each decision with `ReviewNotificationService.findNotification`, builds the KAN-333 `ReviewEmail` (site-host link through `WorkspaceSiteService`, no link without a host), renders subject, text and HTML with `ReviewEmailRenderer` and calls `EmailOutboxService.queue`. The outbox is taken through `ObjectProvider`; without it the listener returns.
- Each decision is handled on its own: a failure is logged as a count and the exception class name only, and the other decisions are still queued.
- `ReviewNotificationJobHandler`, its test and the `review-notification` handler type are removed. Jobs of that type still in JobRunr storage fail as unknown handlers (worker AC-002.15).
- Tests: new `ReviewNotificationEnqueuerTest`; `ReviewNotificationEnqueueBugTest` and `ReviewNotificationIT` rewritten against the outbox, keeping the KAN-333 scenarios (BUG-212, BUG-213) and adding the retry-to-FAILED case for AC-010.4.

### Deviations from the plan

- None. The task was planned as Task 19 and renumbered to Task 20 when PR 230 took Task 19.

### Verification evidence

- `mvn -q -Dtest='vn.tsolve.worker.**.*Test' test` — 106 run, 0 failures, 0 errors (local, 2026-11-14).
- `mvn -q spotless:check` — pass.
- `sdd.ts trace ticket-review STORY-010` — 9/9; `trace worker STORY-004` — 12/12.
- Not run locally (Docker off): `ReviewNotificationIT` and the full `mvn verify`. Left to the CI of PR #233.

### Commit

- default@5cf5c10 — feat(ticket-review): queue the review email in the worker outbox (KAN-162)

### Next up

Evaluate worker `email-outbox` and ticket-review `review-email-outbox`.
