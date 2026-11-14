# Task 13: Resubmit — contract, ticket storage and endpoints

**Module:** jira-app
**Assignee:** BinhBinh13
**Status:** evaluating
**Iteration:** 2

> Written on 2026-11-15 with the work (PR #237, KAN-130).

---

## Session 2026-11-14

### Working on: resubmit-backend (STORY-012)
- Contract first: `resubmit` (default false) on `SolutionFormRequest` and `SubmitSolutionRequest`, `mayResubmit` on
  `IssuePanel`, origin `TICKET`, 409 MSG06 on `submitSolution`; generated client regenerated.
- `ticket`: `findResubmittableSections` and one guarded native UPDATE `resubmitSolutionFormTicket` (five sections,
  `PENDING_REVIEW`, due date reset as for a new submission, ACTIVE Project); a guard miss throws
  `TicketNotResubmittableException` (409 MSG06); the writer's Draft is deleted in the same transaction.
- `jiraapp`: `openForm`, `submit` and `panel` follow the design; a removed member cannot resubmit (AC-012.3).
- Commit: `default@d3850a2d` (before the squash of #203; re-applied on main by the merge `ef1fc5e0`)

## Session 2026-11-15: PR #237 review and merge with main

- Merge: #203 was squash-merged into main (`5aad8e6a`), so the five KAN-130 commits were re-applied on main and
  recorded as a merge commit; 2 files of the first commit and `tsolveClient.js` conflicted (progress.json,
  events.jsonl, the BUG-233 comment), resolved to main's side plus KAN-130's own changes.
- Review fixes: the UPDATE now clears `change_request_comment` (as `Ticket.recordReviewFeedback` does); the comment on
  `resubmitSolutionFormTicket` and a "Deliberate exception" line in design.md say why it does not use
  `TicketTransitions` (one atomic UPDATE); `SolutionFormTicketIT` asserts the cleared comment; module.md `paths`
  now covers `ticket/**/SolutionForm*` so `sdd.ts trace jira-app` finds AC-012.7 (46/46).
- Verify (backend): `mvn -q spotless:check` exit 0; `mvn -q test -Dtest='*JiraApp*,*SolutionForm*,*Ticket*'` 304 run,
  0 failures, 16 errors, all in ITs that need Docker (`ExpiredTicketsApiIT`, `ManualTicketControllerIT`,
  `SolutionFormTicketIT`: "Could not find a valid Docker environment"); the ITs run in CI on the PR.

### Blockers
Docker is not available locally, so the IT assertion for the cleared comment was written but not run here.

### Next up
Independent review: `06-evaluation/jira-app/task-13-resubmit-backend-review.md`.
