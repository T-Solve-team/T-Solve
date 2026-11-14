# Task 21: Review email link fallback

**Module:** ticket-review
**Assignee:** Phạm Tùng Dương (implementation by Codex, review by Claude)
**Status:** evaluating
**Iteration:** 1

## Session 2026-11-14

### Working on: review-email-link-fallback

- Fixes the findings of `artifacts/06-evaluation/ticket-review/task-20-review-email-outbox-review.md`. No acceptance criterion changes.
- BUG-223 (AC-010.8): `ReviewNotificationEnqueuer` reads the site host once per event; a lookup that throws counts as "no site address", so every decision's email is still queued without the button and the link.
- BUG-224: the test `stopsAtTheFirstFailedEnqueue_BUG_164` is renamed `continuesAfterAFailedQueueCall_BUG_164`, matching what it asserts.

### Deviations from the plan

- None.

### Verification evidence

- `mvn -q -Dtest='vn.tsolve.worker.**.*Test' test` — 115 run, 0 failures, 0 errors (local, 2026-11-14).
- `mvn -q spotless:check` — pass.
- `sdd.ts trace ticket-review STORY-010` — 9/9.
- Not run locally (Docker off): `ReviewNotificationIT` and the full `mvn verify`. Left to the CI of PR #233.

### Commit

- default@e9eec86 — fix(ticket-review): queue the review email when the site lookup fails (KAN-162)

### Next up

Evaluate `review-email-link-fallback`.

## Session 2026-11-14 (attempt 2)

- Review attempt 1 failed only on the red CI caused by worker BUG-225 (another feature); no code change here. Bug-log commit column filled for BUG-223 and BUG-224.
