---
module: ticket-review
feature: review-decision-endpoints
task: 7
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-review — Task 7: Review decision endpoints (KAN-24)

## Summary

Pass. `ReviewDecisionController` (`backend/src/main/java/vn/tsolve/review/controller/ReviewDecisionController.java`) exposes `POST /api/v1/reviews/{approve,reject,request-changes}` with the existing operation ids. Access is checked in layers:
1. `@PreAuthorize` requires the Project Manager role (`:27`).
2. Bean validation checks the body (`:67-73`).
3. The verified Account and its managed Projects come from an optional `ReviewWorkspaceAccess`. An absent adapter, or one that returns empty (no binding, or an unverified Account), gives 403 MSG08 (`:57-65`).
4. The batch is passed to the Task 6 transaction, which re-checks scope per ticket under lock.

Error mapping follows the contract: 401 MSG07 for anonymous callers (rethrown to the entry point), 403 MSG08 for a missing role, no binding or an unmanaged Project, 400 with `fieldErrors` for blank or oversized feedback, 404 MSG04 for a missing or other-Workspace id, 409 MSG06 for stale tickets, and `{count}` on success.

The two-PostgreSQL-database API test shows that a BETA ticket id cannot be decided through the ACME Workspace and that neither database changes. Each HTTP decision commits the state and the decision row with the session Account as `project_manager_id`.

Production note: in this range no production `ReviewWorkspaceAccess` exists, so every decision endpoint answers 403 in production, fail-closed as the task requires (BUG-141, open). HEAD has since added `ProjectMembershipReviewAccess` under Task 9's addendum, which is reviewed separately.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | The three routes map to the right action and feedback field. The reviewer id is the authenticated `TSolveUser.id()`; the client cannot select it. The managed scope comes only from the trusted adapter. |
| Completeness | pass | AC-009.3 (401, 403 for role, binding and unmanaged, cross-Workspace 404 with no write) and the API portion of AC-009.4–.8 are named and tested in `ReviewDecisionApiIT`. |
| Correctness | pass | `ReviewDecisionApiIT` 23/23 at `77fa3a31` (run by the reviewer) and on HEAD. |
| Integration | pass | Matches the published OpenAPI operations `approveTickets`, `rejectTickets` and `requestTicketChanges`. CSRF is enforced (tests use `csrf()`). |

## Traceability

`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-009` (2026-11-07):

```
✓ AC-009.3  ProjectMembershipReviewAccessIT.java, ReviewDecisionApiIT.java
✓ AC-009.4  ReviewDecisionApiIT.java, TicketDecisionIT.java
✓ AC-009.5  ReviewDecisionApiIT.java, TicketDecisionIT.java
✓ AC-009.6  JiraReviewFeedbackControllerTest.java, ReviewDecisionApiIT.java, TicketDecisionIT.java
✓ AC-009.7  ReviewDecisionApiIT.java, TicketDecisionIT.java
✓ AC-009.8  ReviewDecisionApiIT.java, TicketDecisionIT.java
```

HTTP tests: `deniesRequestsWithoutAuthentication_AC_009_3`, `deniesAuthenticatedUsersWithoutProjectManagerRole_AC_009_3`, `deniesUnboundAndUnmanagedRequests_AC_009_3`, `cannotDecideAnotherWorkspacesTicket_AC_009_3_AC_009_7`, `validatesFeedbackAndUniqueIds_AC_009_7`, `commitsEachHttpDecision_AC_009_4_AC_009_5_AC_009_6_AC_009_8` (x3), and `acceptsSearchPublicIdsWithoutDuplicatingDecisions_BUG_144`.

## Security review

Required because the module handles authorization.
- No IDOR: the scope is re-checked per locked row in the same transaction.
- No caller-selected actor or Workspace.
- Cross-tenant ids resolve to "missing" in the bound database.
- No ticket data in error bodies, and user text is not logged.
- Low: a manager can tell "exists in an unmanaged Project of my Workspace" (403) from "does not exist" (404). This needs a valid random UUID and matches the contract's status table, so it is not raised as a finding.
- Low: the membership is read just before the transaction begins (`:61-63`), outside it, which leaves a negligible revoke window (time-of-check to time-of-use).

## Issues

No CRITICAL or failing findings.

Warnings (no effect on the verdict):

1. **WARNING:** `ReviewDecisionController.java:61-63`. The "adapter bean absent" branch (`Optional.empty()`) has no test for the decision controller. Only the "binding absent" case is covered (`ReviewDecisionApiIT.java:428`, a request outside `runIn`). The Jira controller has the equivalent test (`JiraReviewFeedbackControllerTest.refusesToReadWithoutTrustedWorkspaceIntegration_AC_009_6`). Suggested fix: a unit test constructing `new ReviewDecisionController(service, Optional.empty())` that expects `AccessDeniedException` and no service call.
2. **WARNING:** `ReviewDecisionApiIT.java:455-456`. Duplicate ids and the U+2003-only comment are asserted only as 400. Both reach the service's bare `ResponseStatusException`, so their body is MSG06 without `fieldErrors` (see Task 6 warning 1). The contract asks for field errors to be preserved.
3. **WARNING:** no HTTP test sends an empty `ticketIds`, 501 ids or a 1,001-character reason. The annotations `@NotEmpty @Size(max = 500)` and `@Size(max = 1000)` exist but are not exercised through the API (the service-level test covers 1,001 characters).

## Assumptions made

- In this range, Task 7 is complete with a fail-closed adapter seam and test-supplied adapters, as its task description states ("Deny access when its production adapter or binding is absent"). Production decisions only work once the HEAD membership adapter (Task 9 addendum) is accepted. The user may want this recorded as a dependency of the module's completion.
- 404 MSG04 for an id from another Workspace satisfies "expose no ticket data and perform no write" (AC-009.3).

## Test Report

See `task-7-review-decision-endpoints-test-report.md`.
