---
module: jira-app
feature: draft-reminder-job
task: 12
attempt: 1
iteration: 2
date: 2026-11-14
reviewer: harnessclaude-reviewer (evaluate, run-tests)
---

# Test Report: jira-app — Task 12 "Reminder job and email" (KAN-336)

Run on branch `feat/KAN-336-jira-app-draft-reminder` at head `4bbbb60c` (repo `default`, monorepo). The feature
commit is `649fe327`. The review is `task-12-draft-reminder-job-review.md`.

## Results

| Check | Command (directory) | Result |
|-------|---------------------|--------|
| DraftReminderHandlersTest | `mvn -B -q test -Dtest='DraftReminder*Test,SolutionDraftServiceImplTest' "-DargLine=-Djdk.net.unixdomain.tmpdir=C:/tmp/jdk-uds"` (`backend/`) | PASS. 8/8 (AC-011.1 ×3, AC-011.2, AC-011.2+3, AC-011.2+5, AC-011.4+7, AC-011.6) |
| DraftReminderMessageTest | same | PASS. 3/3 (AC-011.3 ×3) |
| SolutionDraftServiceImplTest | same | PASS. 3/3 |

The captured output of the run shows only ids and counts, for example "Draft reminder email to account … in Workspace
… failed with java.lang.IllegalStateException". The test address `ngo.bao@acme.example` and the key `SAM1-16` do not
appear, although the stubbed SMTP failure message contains the address.

## Library behaviour checked

JobRunr 8.6.0, `org.jobrunr.scheduling.AbstractJobScheduler.saveJob` (read with `javap`). A save that conflicts with an
existing job id is caught and logged as "Skipped Job with id {} as it already exists", so a second
`enqueue(sameId, …)` does not queue a second email (AC-011.5).

## Not run

- **Full `mvn verify`, `mvn spotless:check`, JaCoCo, ITs**: not run, by the dispatcher's rule. A full verify is
  already running separately.
- **Live SMTP and worker-profile check**: not run by the reviewer. The session log records one with a local SMTP sink
  and a cron of every minute.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-011`: 7/7 acceptance criteria have a test.
