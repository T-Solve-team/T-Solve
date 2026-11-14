# Task 8: Issue Panel

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** evaluating
**Iteration:** 2

> Written on 2026-11-14 from the commits and their messages, after the PR #203 review found the log missing. It records what the commits hold; reasoning not in a commit message is not reconstructed.

---

## Session 2026-11-12

### Working on: issue-panel-ui (KAN-126)
- STORY-001, STORY-008 (and STORY-009, retired on 14/11).
- `IssuePanel.jsx`: MSG62 only when not linked, MSG65, MSG64 from review feedback, MSG77 with Continue draft,
  Write Solution while the issue is Done and may open; the Published-ticket search and 3.4 preview (removed by
  Task 9). Replaced the hello-world panel and its resolver.
- Tests: `IssuePanel.test.jsx`.
- Commit: `default@3c4aaa7c`

## Session 2026-11-14

### Working on: BUG-213 (found in the dev-site test)
- Write Solution re-reads the status and hides itself once the issue left Done; the panel reloads on
  `JIRA_ISSUE_CHANGED`. Checked live with SAM1-99.
- Commit: `default@a5a6d4ca`

### Blockers
none

### Next up
Independent review: `06-evaluation/jira-app/task-8-issue-panel-ui-review.md`.
