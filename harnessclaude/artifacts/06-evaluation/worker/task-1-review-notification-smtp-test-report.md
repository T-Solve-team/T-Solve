# Test Report: worker — Task 1: review-notification-smtp

**Date:** 2026-11-07
**Code:** merged on main as 49231eb8 (PR #131; original commit c7dd3d10). No change to the sender or its tests since then.
**Tree:** worktree kan-24-merge on `feat/KAN-159-ticket-review-history-api`; its uncommitted KAN-159 changes do not touch worker files.
**Environment:** Temurin JDK 21.0.13+9, Maven 3.9.9, Docker Desktop Engine 29.9.2 (Mailpit container for the IT).

## Commands and results

- `bun harnessclaude/hooks/sdd.ts trace worker STORY-001`: 7/7 acceptance criteria have a test.
- `mvn -B "-DargLine=-Dapi.version=1.44" -Dtest=ReviewNotificationSenderTest -Dit.test=ReviewNotificationSenderIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Dspotless.check.skip=true verify`: BUILD SUCCESS in 1 min 28 s.
  - `ReviewNotificationSenderTest`: 7 run, 0 failures, 0 errors, 0 skipped.
  - `ReviewNotificationSenderIT` (real SMTP to Mailpit): 1 run, 0 failures, 0 errors, 0 skipped.
- jshell check with jakarta.mail 2.0.3: `InternetAddress.parse(v, true)` + `validate()` rejects `writer` and `writer@`, accepts `a@b`.

## Not run in this evaluation

- The full backend suite and `spotless:check`. This run is focused on the feature. The implementation session's full `mvn verify` (535 unit, 39 integration tests) and Spotless passed before merge, and the files have not changed since.

## Acceptance Criteria → Tests

| AC | Evidence |
|----|----------|
| AC-001.1 | ReviewNotificationSenderIT: Reject mail captured in Mailpit; subject, From, single To, ticket key and Unicode reason in plain text |
| AC-001.2 | ReviewNotificationSenderIT: Request Changes mail captured in Mailpit with the review comment |
| AC-001.3 | ReviewNotificationSenderTest: empty, two addresses, group, CRLF + Bcc rejected with no send |
| AC-001.4 | ReviewNotificationSenderTest: MailSendException mapped to DeliveryException after one attempt, fixed message, no cause |
| AC-001.5 | ReviewNotificationSenderTest: auto-configured JavaMailSenderImpl has connect 5000, read 3000, write 5000 ms |
| AC-001.6 | ReviewNotificationSenderTest: captured output contains no address, content or credential |
| AC-001.7 | ReviewNotificationSenderTest: web context starts with no SMTP settings, no mail sender bean |
