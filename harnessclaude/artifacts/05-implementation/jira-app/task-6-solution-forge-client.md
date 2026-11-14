# Task 6: Forge client, manifest and background script

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** evaluating
**Iteration:** 2

> Written on 2026-11-14 from the commits and their messages, after the PR #203 review found the log missing. It records what the commits hold; reasoning not in a commit message is not reconstructed.

---

## Session 2026-11-12

### Working on: solution-forge-client (KAN-126, branch `feat/KAN-126-jira-app-solution-form-v2`)
- STORY-001, STORY-004.
- `tsolveClient.js`: the Solution Form, draft, rewrite, panel and review-feedback calls through the one client
  module; submit retries network errors and 5xx 3 times (1 s, 2 s, 4 s), then MSG69; a 4xx is never retried.
- `watchDone.js`: the viewer's own change into the Done status category (`isOwnChange`, `movedIntoDone`), the
  issue read with `requestJira` (status, summary, description as text).
- Tests: `solutionClient.test.js`, `watchDone.test.js`.
- Commit: `default@215d7051`

## Session 2026-11-13

### Working on: review fix of Task 1 (BUG-215, logged on the branch as BUG-204)
- Only a 409 MSG31 after a retry counts as submitted; a 409 MSG40 is given back.
- Commit: `default@ccfee5d6`

## Session 2026-11-14

### Working on: BUG-212 (found in the dev-site test)
- `JIRA_ISSUE_CHANGED` never reached the background script, and Jira sometimes reloads the script after a
  transition, so the form never opened by itself. `readIssue` can also read `statuscategorychangedate` and the
  changelog; `recentlyMovedIntoDone` (60 s, the viewer's own change) opens the form on load; the visible issue view
  re-reads the status every 4 s.
- Checked live on bdang798 (5.7.0): SAM1-8 and SAM1-9 open the form on In Progress/To Do → Done.
- Commit: `default@a5a6d4ca`

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-6-solution-forge-client-review.md`.
