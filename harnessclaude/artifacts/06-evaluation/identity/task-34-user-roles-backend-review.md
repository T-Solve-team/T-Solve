---
module: identity
feature: user-roles-backend
task: 34
verdict: pass
attempt: 1
date: 2026-11-08
reviewer: harnessclaude-reviewer (Claude Sonnet 5.5, independent read-only)
---

# Review: identity — Task 34: user-roles-backend (KAN-110)

## Summary
PASS WITH NOTES. Range `f70247a7..37289e7d`. All AC-007.8 to .23 have code and tests; the changed tests pass here. No blocking defect found. Advisory lock then `FOR UPDATE` (target, spaces, admins, recipients) gives one lock order per Workspace database, and the partial unique index backs "one PM per Project". Notes: the check order deviates from the literal AC-007.12/.20 text (it follows the mock), plus the minor points below.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Each AC checked against `UserRoleUpdateServiceImpl`, `UserRoleUpdateRepository`, `UserDetailRepository`; mock parity checked for MSG56/113/59/118/78 and handover effects (state.ts, handover.ts). |
| Completeness | pass | AC-007.8 to .23 covered; seed (AC-007.23) idempotent via ON CONFLICT / NOT EXISTS. |
| Correctness | pass | 46 unit + 18 controller + 13 UserRoleUpdateIT + 3 UserDetailIT + 6 MockSignedInCallerIT + 4 ReviewStorageIT + 3 ProjectMembershipReviewAccessIT, 0 failures. |
| Integration | pass | Controller regex route keeps static `/users/...` routes; invalid ids give 404 MSG04. Edits to other teams' ITs keep their intent (Issue 3). |

## Traceability
`sdd.ts trace identity STORY-007`: AC-007.8 to AC-007.23 all marked covered (tests in UserDetailIT, UserRoleUpdateIT, UserRoleUpdateServiceImplTest, UserDetailControllerTest, MockSignedInCallerIT). Concurrency ACs have real tests: `concurrentMutualAdminRemovalsLeaveExactlyOneActiveAdmin_AC_007_15`, `concurrentClaimsForOneSpaceYieldOneConflict_AC_007_14`; rollback: `rollsBackEveryWriteWhenAuditInsertFails_AC_007_20_AC_007_21`.

## Issues (none blocking)
1. Medium, spec/code mismatch on check order. `UserRoleUpdateServiceImpl.java` (the `confirmTicketResolverRemoval` and `choices(...)` shape checks sit after the MSG56/MSG113 block). AC-007.12 (MSG06) comes before AC-007.13 to .15 in AC-007.20, but a body with a non-boolean `confirmTicketResolverRemoval` or malformed handover list AND a bad role, a taken Space or a last-admin removal answers MSG01/MSG117/MSG56, not MSG06. This mirrors the MSW mock (roles-handler.ts) and is pinned by `lastAdminWinsOverSelfAndMalformedConfirmation_AC_007_15` and `rejectsSelfAdminRemovalBeforeMalformedHandover_AC_007_15`. Fix: either move both shape checks to just after the "body is an object" check, or amend AC-007.12/.20 to say they come after AC-007.15 (and the tests follow). Judgment call for the user.
2. Low, `UserRoleUpdateServiceImpl.java` MSG78 `count` and audit `removedProjectIds`: `removed` is the union of MEMBER Projects and handed-over Projects, and is filled also for a target without TICKET_RESOLVER (then `removeMembers` is skipped). Matches the spec text and the mock; noted for the frontend count.
3. Low, other teams' tests: `ProjectMembershipReviewAccessIT` now gives the deactivated manager the PM row of `OTHER` instead of `MANAGED` (required by the new unique index; the "deactivated PM is denied" intent of AC-009.3 and the MANAGER assertions of AC-009.1/2 are unchanged). `ReviewStorageIT` strips the new 023 include in its old-changelog scenario; intent unchanged. Both pass.
4. Low, locking scope: `pg_advisory_xact_lock(110,7)` serialises all role updates of a Workspace database; fine for Admin-only traffic. Other writers of `project_membership` / `space_manager` are not behind it, but the unique index and the `space_manager` unique violation (mapped to MSG117) protect them.
5. Info, a `PUT .../handover-options` with a non-UUID id answers 404 instead of 405; harmless.

SQL: all queries use bound parameters (`:id`, `IN (:ids)`); no string-built values. Tenant: all access goes through the Workspace-bound `JdbcTemplate`, and `scope.currentWorkspaceId()` is called first in read and write paths (AC-007.22). No uploads or user URLs; endpoints are Admin-only through `@PreAuthorize`.

## Test Report
See `task-34-user-roles-backend-test-report.md`.

## Resolution (2026-11-08)

Finding 1: the user chose to fix the spec. AC-007.12 and AC-007.20 now place the confirmation/handover shape check (400 MSG06) after AC-007.15 and before AC-007.16, as in the MSW mock and the code; spec re-approved (1969e1d6). No code change. Findings 2 to 5: no action.
