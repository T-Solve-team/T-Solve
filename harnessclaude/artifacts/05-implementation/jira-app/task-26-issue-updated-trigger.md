# Task 26: issue-updated-trigger

**Module:** jira-app · **Assignee:** Claude (for the user) · **Status:** evaluating · **Iteration:** 2

## Session 2026-11-18

### Working on: issue-updated-trigger (KAN-371)
- `manifest.yml`: trigger `tsolve-issue-updated-trigger` (`avi:jira:updated:issue`, `filter.ignoreSelf: true`) →
  endpoint `tsolve-issue-updated-endpoint` (remote `tsolve-backend`, route `/api/v1/jira-app/events/issue-updated`),
  no Forge function, no new scope.
- `manifest.test.js`: AC-005.6 test. Jest: all suites pass; `npm run lint` clean; `forge lint` 0 errors, 1 approval
  (MAJOR_VERSION_RULE "egress modification") that is already reported on the unchanged manifest (placeholder
  `baseUrl`), so not caused by this change.
- **Not done here:** the dev-site check (drag a card to Done on the board → the assignee's panel shows MSG77 and
  "Continue draft") — the user checks the Forge UI (`forge deploy` + `forge install --upgrade`, backend tunnel).
- Commit: see `lastCommit` (feat(jira-app): send issue updates … KAN-371)

### Next up
Task 27 draft-reminder-done-only
