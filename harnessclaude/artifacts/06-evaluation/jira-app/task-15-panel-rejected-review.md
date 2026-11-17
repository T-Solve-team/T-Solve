---
module: jira-app
feature: panel-rejected
task: 15
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 15: Issue Panel — Rejected ticket (panel-rejected, KAN-337)

Scope: commit `6e6d0f8a` (repo `default`), reviewed on HEAD `72dcf49d` of `feat/KAN-337-jira-app-reject-reason` and re-checked on `main` `8cfeec0e`, which holds it squashed as `bfb1dc5f` (PR #238). The reviewed code is the same on `main` except comments renumbering BUG-215 to BUG-231; tests were re-run there.
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md` AC-008.9 and its changelog row of 2026-11-15.

## Summary
When `ticketStatus` is `REJECTED` the panel now calls the review-feedback endpoint (as it did for Changes requested)
and renders the `REJECTED` code through a new `APP_TEXTS` table in `messages.js`:
`Rejected by {manager_name}: "{reason}".` No button opens the form. Meets the spec.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `IssuePanel.jsx:37-38` fetches feedback for `CHANGES_REQUESTED` or `REJECTED`; the warning SectionMessage shows `message(feedback.code, feedback.params)`. |
| Completeness | pass | Text, both placeholders and "no button": the server sends `mayOpenForm=false` whenever a ticket exists (`JiraAppSolutionServiceImpl.panel`, line 136) and `mayResubmit` only for `CHANGES_REQUESTED` (line 133), so neither form button can show. |
| Correctness | pass | Tests pass; `messages.test.js` checks the exact text, `IssuePanel.test.jsx` checks the text, the endpoint call and that no button renders. |
| Integration | pass | Uses the unchanged `getReviewFeedback` client and the code/params from ticket-review AC-009.13. `APP_TEXTS` is looked up after `MESSAGES`, so SRS codes keep priority and an unknown code still shows MSG06. |

## Traceability
```
✓ AC-008.9  jira-app/TSolve_JiraApp/src/frontend/messages.test.js, jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx
trace jira-app (STORY-008): 6/6 acceptance criteria have a test
```

## Issues
None blocking.

Observation for the user (spec scope, not a code defect): AC-008.9 covers only the writer. Any other viewer of a
Rejected ticket — another member, the writer of a `JIRA_IMPORT` ticket, or anyone when the reviewer has no name —
gets no feedback and sees an empty panel, the symptom this change fixed for the writer. Same behaviour as Changes
requested today. If that should change, it is a spec change (e.g. a plain "Solution rejected." label).

## Test Report
See `task-15-panel-rejected-test-report.md`.
