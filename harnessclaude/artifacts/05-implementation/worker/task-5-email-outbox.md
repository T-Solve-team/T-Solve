# Task 5: Shared email outbox and dispatcher

**Module:** worker
**Assignee:** Phạm Tùng Dương (implementation by Codex, review by Claude)
**Status:** passing
**Iteration:** 1

## Session 2026-11-14

### Working on: email-outbox

- KAN-162 change: user approved worker spec version 55f040b8 (STORY-004, reworded AC-001.1/001.2, NFR-5) and ticket-review a721bde8. Branch `feat/KAN-162-worker-email-outbox` from origin/main (team rule: one branch per Jira issue, not `feat/worker`).
- Codex built the task in three slices from briefs: (a) `OutboundEmail`, `EmailAddressRules`, `SmtpEmailSender` (rename of `ReviewNotificationSender`; text or multipart); (b) Jobs changelog `db/jobs-changelog`, `EmailOutboxRepository` (JdbcClient on the Jobs pool), `EmailOutboxService.queue`; (c) `EmailOutboxDispatchHandler`, `EmailOutboxRegistration` (recurring job `worker-email-outbox`).
- Design correction during implementation: Codex stopped on a contradiction in "Dispatch run" (step 3 failed every previously attempted row after 60 minutes; step 4 let a scheduled retry run after the hour). Fixed in design.md with a `claimed_at` column: a due row with `claimed_at` set is a lease-expired, failed attempt; a row with it null is claimed whatever its age. No spec change.
- The jobs `SpringLiquibase` is registered through a `BeanFactoryPostProcessor`, because a plain `@Bean` would make Spring Boot's Liquibase auto-configuration back off from the tenant migration.
- Review findings fixed test-first and logged: BUG-212 (an unexpected send exception aborted the batch and burned other rows' attempts), BUG-213 (no safe diagnostic when a whole run fails), BUG-214 (backoff list length not validated). An unrequested change that made the worker-profile flags environment-driven was reverted.

### Deviations from the plan

- `ReviewNotificationJobHandler` and its tests (ticket-review paths) were adapted so they compile against `SmtpEmailSender`; the review email behaves as before until ticket-review Task 19.
- PR 230 (KAN-333, HTML review email) changes the same sender, handler and criteria, and already uses BUG-212/213 and worker Task 4 / ticket-review Task 19. After it merges: rebase, renumber this task and the bug ids, take its AC-001.1/001.2 wording, and re-approve ticket-review AC-010.2.

### Verification evidence

- `mvn -q -Dtest='vn.tsolve.worker.**.*Test' test` — 80 run, 0 failures, 0 errors (local, 2026-11-14).
- `mvn -q spotless:check` — pass.
- `sdd.ts trace worker STORY-001` — 7/7; `trace worker STORY-004` — 12/12; `trace ticket-review STORY-010` — 6/6.
- Not run locally (Docker off): `EmailOutboxIT`, `SmtpEmailSenderIT`, `ReviewNotificationIT`, `JobRunrTenantIT`, full `mvn verify`. Left to the PR's CI.

### Commit

- default@89691ef — feat(worker): add the shared email outbox and dispatcher (KAN-162)

### Next up

ticket-review Task 20 (`review-email-outbox`). PR 230 was merged into this branch on 2026-11-14 (merge eab3d9dc): this task became Task 5, the bug ids BUG-215–217.
