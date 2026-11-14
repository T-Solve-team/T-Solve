# Task 6: Email outbox review fixes

**Module:** worker
**Assignee:** Phạm Tùng Dương (implementation by Codex, review by Claude)
**Status:** passing
**Iteration:** 1

## Session 2026-11-14

### Working on: email-outbox-review-fixes

- Fixes the Medium findings of `artifacts/06-evaluation/worker/task-5-email-outbox-review.md`. No acceptance criterion changes; each fix is test-first and logged.
- BUG-218 (AC-004.4): the claim reads further pages of due rows in the same transaction (at most 10 reads of `batch-size`) until `batch-size` rows are claimed for sending; held and FAILED rows do not count. Workspace eligibility is checked once per Workspace per run. Order stays oldest first.
- BUG-219: `EmailOutboxDispatchHandler.run` has `@Job(retries = 0)`; the next tick is the retry.
- BUG-220 (AC-004.6, AC-004.7): a failure while writing one row's outcome is logged by exception class name and the remaining rows are still handled; the row stays leased and falls under the lease rule.
- BUG-221, BUG-222: `EmailOutboxIT` gains the recurring job through real JobRunr and the AC-004.9 hold path on PostgreSQL.

### Deviations from the plan

- None.

### Verification evidence

- `mvn -q -Dtest='vn.tsolve.worker.**.*Test' test` — 115 run, 0 failures, 0 errors (local, 2026-11-14).
- `mvn -q spotless:check` — pass.
- `sdd.ts trace worker STORY-004` — 12/12.
- Not run locally (Docker off): the three new `EmailOutboxIT` cases and the full `mvn verify`. Left to the CI of PR #233.

### Commit

- default@c5bfbae — fix(worker): keep held outbox rows from blocking the batch (KAN-162)

### Next up

Evaluate `email-outbox-review-fixes`.

## Session 2026-11-14 (attempt 2)

- Review attempt 1 failed: `EmailOutboxIT.recurringJobDispatchesThroughJobRunr_AC_004_4_BUG_221` timed out on CI because the test set no `spring.mail.host`, so `withBean` skipped the conditional `SmtpEmailSender` bean (BUG-225, test only). Fixed in default@e6570be; bug-log commit column filled for BUG-218 to BUG-225.
