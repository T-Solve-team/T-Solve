---
module: jira-app
feature: draft-reminder-job
task: 12
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 12: Reminder job and email (draft-reminder-job, KAN-336)

Scope: commit `649fe327` (repo `default`), reviewed on HEAD `4bbbb60c` of `feat/KAN-336-jira-app-draft-reminder`.
The merge-base with `main` is `main`'s HEAD `a09f7d27`, so KAN-333's `ReviewNotificationSender` / `ReviewEmailRenderer`
are in the reviewed tree. The working tree has one unrelated change (`jira-app/TSolve_JiraApp/package-lock.json`).
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md` STORY-011 (AC-011.1 to AC-011.7). Edge cases:
`harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md`, "Change — 2026-11-14: Draft reminder email".
Passes run: code-review (spec compliance, then quality), plus a security pass on logs and mail, because the job
handles personal data (email addresses).

## Summary

Verdict: **PASS WITH NOTES** (frontmatter `pass`).

**The job chain:**
- `DraftReminderRegistration` sets up the schedule: its own id `jira-app-draft-reminder`, `0 8 * * *`,
  Asia/Ho_Chi_Minh, only with `tsolve.worker.enabled`.
- `DraftReminderDispatchHandler` checks for a mail sender, then queues one Workspace job per active Workspace for
  today's Vietnam date.
- `DraftReminderWorkspaceHandler` finds the writers in the tenant, then queues one email job per writer under a
  name-based UUID of workspace, account and day.
- `DraftReminderEmailHandler` reads the active Account's email and its open Drafts in the tenant, composes the
  message, and sends it outside the tenant binding and outside any transaction.

**Checked and holding:**
- **Tenant handling:** both handlers check `isWorkspaceActive`, and every DB read runs inside
  `TenantExecutionService.runInTenant`. That call also refuses a cross-Workspace switch and a suspended Workspace.
- **No outbound call in a transaction:** the SMTP send at `DraftReminderEmailHandler.java:73` runs after
  `runInTenant` returns.
- **Same-day dedupe (AC-011.5):** I checked that JobRunr 8.6.0 really ignores a second enqueue with the same id.
  `AbstractJobScheduler.saveJob` catches the storage conflict and logs "Skipped Job with id {} as it already exists".
  Succeeded jobs are kept 36 h by default, longer than the day the id covers.
- **No bean clash with KAN-333:** `ReviewNotificationSender` and `SmtpDraftReminderMailClient` are different types.
  Both inject the single auto-configured `JavaMailSender` under the same conditions (`@Profile("worker")` +
  `spring.mail.host`). Each consumer uses `ObjectProvider<OwnType>`, so neither lookup is ambiguous.

**Tests:** 11/11 green (`DraftReminderHandlersTest` 8, `DraftReminderMessageTest` 3). Every AC-011.x has a test that
names it.

No blocking issue. The notes below are about duplicated mail plumbing (weaker than KAN-333's), a single global Jira
URL in a multi-tenant product, and test depth.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All seven criteria are met; see Stage 1. |
| Completeness | pass | `sdd.ts trace jira-app STORY-011`: 7/7. |
| Correctness | pass | `DraftReminderHandlersTest` 8/8 and `DraftReminderMessageTest` 3/3, run by the reviewer. Retry: the email job throws `EmailNotSentException` (no stack trace, no cause, only the failure's class name), and JobRunr's default retry policy (10 attempts, back-off) applies to that one job. |
| Integration | pass | The job uses `worker` only through `WorkerTenantService`, `TenantExecutionService` and `JobRunrRuntime`, the same set `review`'s JOB-02 uses. It uses `ticket` through `SolutionDraftService` + `OpenDraft`, and `identity` through `AccountLookupService.findActive`. No `worker` or `ticket-review` class changed. The registration runs wherever jobs are enabled (also an enqueue-only web process), exactly like `ReviewExpiryRegistration`; only processes with `jobrunr.background-job-server.enabled` execute it. |

### Stage 1 — Spec compliance

| AC | Implementation | Test | Result |
|----|----------------|------|--------|
| AC-011.1 (08:00 Asia/Ho_Chi_Minh, own id, per active Workspace) | `DraftReminderRegistration.java:18-35`; `DraftReminderDispatchHandler.java:47-49`; inactive skip `DraftReminderWorkspaceHandler.java:57` | `DraftReminderHandlersTest` `runsDailyAtEightInVietnamUnderItsOwnRecurringId_AC_011_1` (asserts the id differs from JOB-02's), `queuesOneRunPerActiveWorkspaceForTodaysVietnamDate_AC_011_1`, `skipsAWorkspaceThatIsNoLongerActive_AC_011_1` | PASS |
| AC-011.2 (exactly one email per writer; none without an open Draft, for an inactive Account, or for a Draft with a ticket) | writers deduped `DraftReminderWorkspaceHandler.java:62-68`; `compose` `DraftReminderEmailHandler.java:94-106`; ticket exclusion in Task 11's query | `queuesOneEmailPerWriterWithADayKeyedJobId_AC_011_2_AC_011_5`, `mailsNobodyForAnInactiveAccountOrAWriterWithNoOpenDraftLeft_AC_011_2`, `SolutionFormTicketIT:186` | PASS |
| AC-011.3 (Account email; singular/plural subject text exact; one line per Draft, newest first, yyyy-MM-dd VN date, optional `/browse/` link; closing line; no Solution text) | `DraftReminderMessage.java:20-35`; recipient `DraftReminderEmailHandler.java:105` | `DraftReminderMessageTest` (3 tests, full body equality, VN date rollover 23:30Z → next day, link left out for blank URL, trailing `/` stripped); `mailsTheWritersOpenDraftsToTheirAccountEmail_AC_011_2_AC_011_3` | PASS |
| AC-011.4 (retry that writer only) | one JobRunr job per writer; `DraftReminderEmailHandler.java:77-85` rethrows | `failsOnlyThisWritersJobSoJobRunrRetriesItAndLogsNoAddress_AC_011_4_AC_011_7` | PASS |
| AC-011.5 (no second email the same day) | `DraftReminderWorkspaceHandler.jobId` `:49-52` + `enqueue(id, …)` `:70-73`; JobRunr skips existing ids (verified in 8.6.0 bytecode) | `queuesOneEmailPerWriterWithADayKeyedJobId_AC_011_2_AC_011_5` (same ids on a second run, a different id the next day) | PASS (see Issue 4) |
| AC-011.6 (no sender → send nothing, one warning per run) | `DraftReminderDispatchHandler.java:43-46` | `sendsNothingAndWarnsOnceWithoutAMailSender_AC_011_6` (no enqueue, the warning text appears exactly once) | PASS |
| AC-011.7 (logs: ids, counts, error types only) | `DraftReminderEmailHandler.java:78-84`, `:86-90`; `DraftReminderWorkspaceHandler.java:58`, `:75`; the job payloads carry ids and a date only (`DraftReminderEmailRequest`) | `failsOnlyThisWritersJobSoJobRunrRetriesItAndLogsNoAddress_AC_011_4_AC_011_7` (output has neither the address nor the issue key, even when the SMTP error message contains the address) | PASS |

## Traceability

```
  ✓ AC-011.1  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.2  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.3  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderMessageTest.java
  ✓ AC-011.4  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.5  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.6  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.7  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
trace jira-app (STORY-011): 7/7 acceptance criteria have a test
```

## Issues

### Blocking

None.

### Non-blocking

1. **[Multi-tenancy, Medium — judgment call] The Jira link uses one process-wide `JIRA_SITE_URL` for every Workspace.**
   - `DraftReminderEmailHandler.java:46`, `:105` build every Workspace's links from the single env value.
   - The codebase already has a per-Workspace port for this: `vn.tsolve.triage.service.JiraSite`, "the Workspace's
     Jira site URL (`workspace.jira_site_url`)". Today it is the stub `NoJiraSite`, which returns empty.
   - With two Workspaces on different Jira sites, the second Workspace's writers get links into the first company's
     Jira. Only the issue keys are exposed, but the links are wrong and they name a foreign site.
   - The spec says "when the Jira site URL is configured", and the design accepted the env var ("one dev site today").
     So this does not fail the task.
   - Fix (needs a decision): read the URL from the Workspace (the `JiraSite` port, or an `identity` lookup) inside
     `runInTenant`, and fall back to no link.
2. **[Duplication / hardening, Low] `SmtpDraftReminderMailClient` duplicates KAN-333's sender without its safeguards.**
   - `SmtpDraftReminderMailClient.java:30-34` sends a bare `SimpleMailMessage`. KAN-333's
     `vn.tsolve.worker.client.ReviewNotificationSender` validates a single mailbox and rejects CR/LF in the recipient
     and `from`, sets the display name "T-Solve" and UTF-8, and maps every transport exception to a message-free
     `DeliveryException`.
   - The reminder relies on its handler to scrub exceptions (`DraftReminderEmailHandler.java:77-84`, which does work).
     It has no recipient validation, and its From header shows a bare address where review emails show "T-Solve".
   - Because a malformed Account email throws on every attempt, it burns all of JobRunr's retries.
   - Not reusing the class was a user decision ("do not change Dương's worker code"), so only the safeguards are the
     issue.
   - Fix: apply the same single-mailbox check and display name in `SmtpDraftReminderMailClient`, or extract a shared
     plain-text helper when `worker`'s owner agrees.
3. **[Test depth, Low] The handler test does not check the body that is actually sent.**
   - `DraftReminderHandlersTest.java:165` matches the body with `anyString()`, so wiring the wrong Draft list or the
     wrong site URL into `DraftReminderMessage.of` in the handler would still pass.
   - `DraftReminderMessageTest` covers the composer itself.
   - Fix: assert that the body contains `SAM1-16` and `https://acme.atlassian.net/browse/SAM1-16`.
4. **[Test depth, Low] AC-011.5 is proven only up to "same job id".**
   - The test shows that a second run reuses the ids (`DraftReminderHandlersTest.java:139-147`). That JobRunr then
     skips the duplicate is a library property: I checked it by reading 8.6.0's `AbstractJobScheduler.saveJob`, and no
     test exercises it.
   - Acceptable for a unit test. A JobRunr-backed IT would prove it end to end, but would add to CI time.
   - The session log records a manual check (three runs gave one email).
5. **[Logging, Low] The "queued" count is an attempted count.**
   - `DraftReminderWorkspaceHandler.java:75` logs `writers.size()`. That count includes inactive Accounts (filtered
     later by the email job) and ids JobRunr skips on a same-day rerun.
   - The log holds ids and counts only, so AC-011.7 holds. The number may just mislead operators.
6. **[Config, Info] Two processes can register different schedules.**
   - The registration runs in every process with jobs enabled, including the enqueue-only web process
     (`DraftReminderRegistration.java:27`, `:33`).
   - If `JIRA_APP_DRAFT_REMINDER_CRON` differs between the web process and the worker, the process that starts last
     wins.
   - Same pattern as JOB-02. Keep the variable unset outside local checks.
7. **[Docs, Info] Plan drift.**
   - The design says the Workspace handler checks the mailer, but the code checks it in the dispatch handler (better:
     once per run).
   - The design and task name `DraftReminderMailer` and package `jiraapp/reminder/*`, but the code uses
     `jiraapp/client/DraftReminderMailClient` + `service/impl/*`, which follows `backend/AGENTS.md`.
   - The session log and `progress.json` `lastCommit` record `0d5eed03`, a pre-rebase SHA that no branch contains.
     The feature commit is `649fe327`.

## Assumptions made

- **The global `JIRA_SITE_URL` is accepted** for now (Issue 1), because the spec says "the Jira site URL" and the
  design chose the env var. **Judgment call for the user:** keep it until `workspace.jira_site_url` exists, or switch
  now to the per-Workspace `JiraSite` port (no links until the platform fills it)?
- **A writer is reminded even after leaving the Draft's Project.** The spec has no membership check, and the design
  decided "a Draft stays the writer's". **Judgment call for the user.**
- **"Log one warning per run" is read as per dispatch run, in the process that runs it.** An email job that later
  runs on a worker instance without SMTP fails and is retried, with a scrubbed error. That is not counted as a second
  warning.
- **The plain-text line format** ("- KEY, last saved yyyy-MM-dd: URL") is accepted. The spec fixes the contents and the
  closing line, not the separators.
- **Not run by the reviewer:** the full `mvn verify`, Spotless and ITs (by the dispatcher's rule, already running
  separately) and a live SMTP check. The session log records a local run with an SMTP sink: one email per writer over
  three runs, and logs with ids and counts only.

## Test Report

See task-12-draft-reminder-job-test-report.md
