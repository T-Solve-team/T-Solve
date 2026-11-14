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
- Commit: `default@0d5eed03`

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-12-draft-reminder-job-review.md`.
