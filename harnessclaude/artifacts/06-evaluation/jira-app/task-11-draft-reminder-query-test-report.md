---
module: jira-app
feature: draft-reminder-query
task: 11
attempt: 1
iteration: 2
date: 2026-11-14
reviewer: harnessclaude-reviewer (evaluate, run-tests)
---

# Test Report: jira-app — Task 11 "Open Drafts for the reminder" (KAN-336)

Run on branch `feat/KAN-336-jira-app-draft-reminder` at head `4bbbb60c` (repo `default`, monorepo). The feature
commit is `d498e63d`. The review is `task-11-draft-reminder-query-review.md`.

## Results

| Check | Command (directory) | Result |
|-------|---------------------|--------|
| SolutionDraftServiceImplTest | `mvn -B -q test -Dtest='DraftReminder*Test,SolutionDraftServiceImplTest' "-DargLine=-Djdk.net.unixdomain.tmpdir=C:/tmp/jdk-uds"` (`backend/`) | PASS. 3/3, no regression |
| SolutionFormTicketIT (Testcontainers) | not run by the reviewer; existing failsafe report | PASS. 4/4, including `listsOpenDraftsNewestFirstAndLeavesOutIssuesWithATicket_AC_011_2_AC_011_3` (report written 16:04, after the commit) |

## Not run

- **Full `mvn verify`, `mvn spotless:check`, JaCoCo**: not run, by the dispatcher's rule. A full verify is already
  running separately.
- **`SolutionFormTicketIT` by the reviewer**: see above. It is judged on the failsafe report written after the commit.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace jira-app STORY-011`: 7/7. This task's IT
(`backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java:186`) is outside the module's trace paths.
AC-011.2 and AC-011.3 are also cited by the Task 12 tests.
