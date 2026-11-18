# Task 12: Reminder job and email

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** evaluating
**Iteration:** 2

> Written on 2026-11-14 with the work.

---

## Session 2026-11-14

### Working on: draft-reminder-job (KAN-336)
- STORY-011 (AC-011.1 to AC-011.7).
- `jiraapp`: `DraftReminderRegistration` (id `jira-app-draft-reminder`, `0 8 * * *` Asia/Ho_Chi_Minh, override
  `JIRA_APP_DRAFT_REMINDER_CRON` for local checks), `DraftReminderDispatchHandler` (no mail sender → one warning),
  `DraftReminderWorkspaceHandler` (one email job per writer, job id = name-based UUID of workspace + account + day),
  `DraftReminderEmailHandler` (active Account email + open Drafts → `DraftReminderMessage`), `DraftReminderMailClient`
  + `SmtpDraftReminderMailClient` (`@Profile("worker")`, `spring.mail.host`), the three JobRunr request records.
- Tests: `DraftReminderMessageTest` (3), `DraftReminderHandlersTest` (8) — all green.
- Local check: backend with `worker,worker-mock`, cron every minute, a Python SMTP sink on 127.0.0.1:2525. The two
  open Drafts of alice@acme.test (SAM1-73, SAM1-77) gave one email with the AC-011.3 subject, lines and links; three
  runs gave one email (AC-011.5); the log held ids and counts only (AC-011.7).
- Approved by the user before the commit.
- Commit: `default@34a1208b` (rebased onto main 15/11; first recorded as `0d5eed03`)

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-12-draft-reminder-job-review.md`.

## Session 2026-11-15

### Working on: draft-reminder-job and draft-reminder-query fixes (KAN-336)
- Rebased onto main after PR #203 (KAN-126) and PR #233 (KAN-162, shared email outbox) merged.
- Spec Change: AC-011.4 and AC-011.6 reworded (the outbox sends, retries and expires; queuing is retried per writer);
  re-approved by the user.
- `DraftReminderEmailHandler` queues an `OutboundEmail` (plain text, HTML null) with `EmailOutboxService.queue` inside
  `runInTenant`; a failed queue call fails only this writer's job (`EmailNotQueuedException`, type only); an address
  the outbox rejects is logged and dropped. `DraftReminderDispatchHandler` no longer checks for a mail sender.
  `DraftReminderMailClient` and `SmtpDraftReminderMailClient` removed.
- Review notes fixed: the handler test asserts the queued recipient, subject and body (issue key and Jira link); the
  Workspace handler logs "found N writers" instead of an attempted "queued" count; `SolutionFormTicketIT` keeps only
  its own issues of the shared tenant A; plan docs and commit ids brought up to date.
- Open: an HTML body (user, 15/11: after 16/11); the per-Workspace Jira link (review note 1) stays on `JIRA_SITE_URL`.
