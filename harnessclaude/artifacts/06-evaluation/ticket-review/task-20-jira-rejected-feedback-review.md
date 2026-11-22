---
module: ticket-review
feature: jira-rejected-feedback
task: 20
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-review — Task 20: Jira feedback for a Rejected ticket (jira-rejected-feedback, KAN-337)

Scope: commit `15e148df` (repo `default`), reviewed on HEAD `72dcf49d` of `feat/KAN-337-jira-app-reject-reason` and re-checked on `main` `8cfeec0e`, which holds it squashed as `bfb1dc5f` (PR #238). The reviewed code is the same on `main` except comments renumbering BUG-215 to BUG-231; tests were re-run there.
Contract: `harnessclaude/artifacts/03-modules/ticket-review/spec.md` AC-009.13 and its changelog row of 2026-11-15.

## Summary
`GET /api/v1/jira-app/issues/{issueKey}/review-feedback` now falls back, when there is no current change request, to
the latest Reject decision of the verified writer's Rejected Solution Form ticket, answered as code `REJECTED` with
`manager_name` and `reason`. A missing or blank reviewer name gives no feedback, the same as MSG64 (BUG-135).
MSG64 is unchanged. Meets the spec.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `TicketRepository.findJiraRejection` filters by Project, writer, issue key, `source = SOLUTION_FORM`, `status = REJECTED` and `decision = 'REJECTED'`, latest first (`decidedAt desc, id desc`); the controller maps it through the same `message(...)` helper as MSG64, so the blank-name rule is shared. |
| Completeness | pass | Every clause of AC-009.13 is covered: code, both params, latest decision, writer only, Solution Form only, no feedback for a nameless reviewer. OpenAPI summary, description and `JiraReviewFeedback` schema updated; `npm run generate:api` leaves no diff. |
| Correctness | pass | Unit and integration tests pass (see Test Report). The IT drives a real Reject through the web API and reads it back as the Jira writer, another Jira account (no feedback) and after switching the source to `JIRA_IMPORT` (no feedback). |
| Integration | pass | Same endpoint, same auth chain (`ReviewJiraAccess.inWriterIssue`); the response shape (`feedback.code`, `feedback.params`) is unchanged, so the Jira App reads it with its existing client. A Reject reason is nonblank by AC-009.5, so `reason` is never empty. |

## Traceability
```
✓ AC-009.13  backend/src/test/java/vn/tsolve/review/JiraReviewFeedbackControllerTest.java, backend/src/test/java/vn/tsolve/review/ReviewDecisionApiIT.java
```

## Issues
None blocking.

Observation (no change asked): the IT does not cover a ticket with two Reject decisions, so "latest" is checked only
by the query's `order by`. A ticket reaches Rejected once (final state, AC-009.5), so two Reject rows are not
expected in practice.

## Test Report
See `task-20-jira-rejected-feedback-test-report.md`.
