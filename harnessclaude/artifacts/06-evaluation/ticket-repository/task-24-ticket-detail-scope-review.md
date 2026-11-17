---
module: ticket-repository
feature: ticket-detail-scope
task: 24
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 24: Ticket Detail visibility and fields (KAN-135, STORY-008)

## Summary
This review covers the code at HEAD `7554caca`. The feature was merged in PR #236 (merge commit `09f3bccf`). One later commit, `f3f782f3` (STORY-010), also touched the reviewed files. Only the backend and the OpenAPI contract are in scope.

How `TicketReviewReadServiceImpl.detail` (`backend/src/main/java/vn/tsolve/ticket/service/impl/TicketReviewReadServiceImpl.java:225-258`) checks access, in this order:
1. Ticket by id or public id, else 404 MSG04.
2. `TicketRepository.findPlacement`, filtered on `departmentActive`, else `AccessDeniedException`.
3. PUBLISHED → `ScopeAccessService.canAccessDepartment`.
4. Not PUBLISHED → `writerId == caller`, or PM role together with `managed` of the ticket's Project.
5. Otherwise `AccessDeniedException`.

`GlobalExceptionHandler.handleAccessDenied` (`common/exception/GlobalExceptionHandler.java:90-96`) turns that exception into 403 `NO_PERMISSION` (MSG08) with no body fields.

Other changes:
- The controller guard is `hasAnyRole(TICKET_RESOLVER, PROJECT_MANAGER)` (`review/controller/ReviewReadController.java:112`).
- `findPlacement` joins `project` to `department` with no Project status condition. It matches the public id or the legacy hex id.

Security checks done:
- A Published ticket's writer outside the Department gets 403.
- `lastReviewComment` is returned only to the writer or the managing PM.
- `canDecide` is unchanged: PM role, managed, SOLUTION_FORM and PENDING_REVIEW.
- A soft-deleted Department (status `DELETED`) and a missing Project row both give 403 to everyone. This is checked before any role rule.
- Workspaces are separate databases, so a foreign id gives 404, the same answer as an unknown id. The IT checks this with tenant B.
- `canAccessDepartment` also requires an ACTIVE `app_user` and an ACTIVE Department.

AC-008.7:
- `Ticket` maps `source_link` (`entity/Ticket.java:55`, `updatable = false`).
- `ImportConfirmServiceImpl.toTicket` passes `row.getSourceUrl()` only for a Jira preview (`service/impl/ImportConfirmServiceImpl.java:321`).
- The `Ticket.imported` overload without a link (`Ticket.java:107-118`) passes null for CSV and manual entry.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-008.1–AC-008.7 are implemented as specified, in the design's order (404 → placement → status rule). See Issue 1 for one narrower case in AC-008.2. |
| Completeness | pass | 7 of the task's ACs have a test (AC-008.1–AC-008.7). The OpenAPI `ReviewTicketDetail` has optional, nullable `department`, `resolvedDate` and `sourceLink`, and the operation has 403/404 responses. The generated client is regenerated. |
| Correctness | pass | Local runs: `TicketDetailVisibilityTest` 18/18 and `ImportConfirmServiceImplTest` 23/23. I did not run `TicketDetailIT` locally (Testcontainers; the dispatcher forbade it). The CI run on PR #236 passed every job, including `it-other`, `it-keycloak-a` and `it-keycloak-b`. |
| Integration | pass | `ReviewDecisionApiIT` and `ReviewExpiryIT` were updated for the 404 → 403 change, as the design's Risks section says. `CrossModuleIsolationIT` is unchanged and green in CI. |

## Traceability
```
  ✓ AC-008.1  frontend/src/features/ticket-import/components/ManualEntryForm.test.tsx, backend/src/test/java/vn/tsolve/ticket/service/impl/TicketDetailVisibilityTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketDetailIT.java
  ✓ AC-008.2  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketDetailVisibilityTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketDetailIT.java
  ✓ AC-008.3  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketDetailVisibilityTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketDetailIT.java
  ✓ AC-008.4  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketDetailVisibilityTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketDetailIT.java
  ✓ AC-008.5  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketDetailVisibilityTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketDetailIT.java
  ✓ AC-008.6  backend/src/test/java/vn/tsolve/ticket/controller/TicketDetailIT.java
  ✓ AC-008.7  backend/src/test/java/vn/tsolve/ticket/controller/TicketDetailIT.java
  ✓ AC-008.8  frontend/src/features/tickets/pages/TicketDetailPage.test.tsx
  ✓ AC-008.9  frontend/src/features/tickets/pages/TicketDetailPage.test.tsx
  ✓ AC-008.10  frontend/src/features/tickets/pages/TicketDetailPage.test.tsx
trace ticket-repository (STORY-008): 10/10 acceptance criteria have a test
```
The `ManualEntryForm.test.tsx` hit for AC-008.1 is a false match. That test is `import-ticket-list`'s own AC-008.1 ("an unknown tab value falls back to CSV"). AC-008.1 of this module is still covered by the two backend tests.

The design's edge cases are covered by these tests:
- Edge case 21: `deniesAnotherDepartment_AC_008_1_AC_008_4`
- Edge case 22: `keepsManagerDecisions…AnotherProject_AC_008_2_AC_008_6` and `pendingOtherProjectManager`
- Edge case 23: `readsPublishedAcrossProjectsAndWithAnInactiveProject_AC_008_1`
- Edge case 24: `deniesInactiveDeletedAndMissingPlacementEvenToTheWriter_AC_008_3`
- Edge case 26: `hidesMissingAndForeignWorkspaceTickets_AC_008_5`

## Issues
No critical or blocking issues.

1. **Medium. A PM of an INACTIVE Project cannot open that Project's non-Published tickets.**
   - Where: `TicketReviewReadServiceImpl.java:230-232`.
   - Cause: `managed` comes from the `projects` map. `ProjectMembershipRepository.activeProjects` (`review/repository/ProjectMembershipRepository.java:37`, `WHERE p.status = 'ACTIVE'`) builds that map, so it lists only ACTIVE Projects. For a Pending, Expired or Unpublished ticket of an inactive Project, `project` is null and the PM gets 403.
   - What the spec says: AC-008.2 and D28 grant access to "a Project Manager who is `PROJECT_MANAGER` of the ticket's Project in `project_membership`", with no Project status condition. The design also says that "the Department decides" for placement.
   - This fails closed, so it is not a leak. No test covers it.
   - Fix: check the membership directly, for example with an `exists` on `project_membership` for `ticket.projectId` (public or legacy id) and the caller. Otherwise, record in the spec that an inactive Project's PM loses access. This is a judgment call for the user.
2. **Low. The trace false match described above.** AC ids are shared between `ticket-repository` and `import-ticket-list` (`ManualEntryForm.test.tsx:343`). This is informational only, because the design already moved this module's frontend tests to `TicketDetailPage.test.tsx` for the same reason.
3. **Info. A writer whose `TICKET_RESOLVER` role was removed gets 403 for their own Pending ticket.**
   - Cause: the controller guard rejects any caller without the TR or PM role (`ReviewReadController.java:112`).
   - This matches AC-008.4 ("neither … role → 403") and GBR-02, so it is not an issue. It is recorded only so the user knows.

## Assumptions made
- I treat Issue 1 as non-blocking. The deviation is stricter than the spec (it denies, never exposes), and an inactive Project with tickets still waiting for the PM is an edge case. The user may decide it is a spec deviation that must be fixed (verdict fail) or a spec gap to document.
- I did not run `TicketDetailIT`, `ReviewDecisionApiIT` and `ReviewExpiryIT` locally, because they need Docker and the dispatcher forbade them. This PASS relies on the green CI run of PR #236 and on the full verify the dispatcher is running on this tree. Commit `f3f782f3` (STORY-010) later touched `TicketReviewReadServiceImpl`, `TicketRepository` and `TicketDetailIT`. The full verify on HEAD is what confirms them now.

## Test Report
Local, from `backend/`, targeted unit tests:
- `TicketDetailVisibilityTest`: 18 run, 0 failures.
- `ImportConfirmServiceImplTest`: 23 run, 0 failures.

CI on PR #236 (`gh pr checks 236`): `backend-tests` (unit, it-other, it-keycloak-a, it-keycloak-b), `frontend-checks` and `frontend-tests` 1–4 all passed.
