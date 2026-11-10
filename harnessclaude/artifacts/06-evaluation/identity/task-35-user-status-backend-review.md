---
module: identity
feature: user-status-backend
task: 35
verdict: pass
attempt: 1
date: 2026-11-10
reviewer: harnessclaude-reviewer (subagent)
---

# Review: identity — Task 35: Account status backend (KAN-109)

## Summary

`PUT /api/v1/users/{userId}/status` (`UserDetailController.updateStatus` → `UserStatusUpdateServiceImpl.update`)
meets AC-006.9 to AC-006.19 and follows the MSW mock (`status-handlers.ts`, `handover.ts`) check for check. Checks
run in the order of AC-006.16: tenant bound → advisory lock → target lock/404 → body and handover-list shape (MSG06,
including the ACTIVE-with-handovers case) → MSG56 → MSG112 → recipients locked, handover options re-read → MSG59 →
MSG118 → MSG01. Writes, status change and audit share one `@Transactional` method; the no-op path returns before any
write or audit. The `HandoverChoices` extraction is a verbatim move of the KAN-110 helpers; KAN-110 tests still pass.
Tests assert real database state (row snapshots before/after, audit row contents, two real concurrency races), not
just status codes. Only minor test-naming / test-strength remarks; no blocking issue.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Each AC checked against code (below). Order matches the mock: shape checks of both lists before the ACTIVE-with-handovers MSG06, MSG56 only for an Active Admin target, MSG112 after MSG56, MSG59 (first Project, `department_name`) before MSG118 before MSG01. |
| Completeness | pass | All 11 backend ACs implemented; seed of AC-006.19 present and idempotent (`ON CONFLICT DO NOTHING` for user and role). |
| Correctness | pass | 43 + 25 + 46 unit tests rerun green; full `mvn verify` reports 1086 + 391, 0 failures/errors. |
| Integration | pass | Contract `updateUserStatus` unchanged (request `status` + optional handover lists, 200 `UserDetail`, 400/401/403/404/409); `generate:api` no diff. Shares `UserRoleUpdateRepository` locks/transfers and `UserDetailRepository.handoverOptions` with KAN-110, so eligibility is the AC-007.9 one by construction. |

### Per-criterion verification

- **AC-006.9** — `transferProject`/`transferSpace` then `updateStatus` only; no `replaceAssignments`/`removeMembers`. IT `deactivatesWithHandoversKeepsAssignmentsAndAudits…` compares the full `app_user` row (only `status` differs), all `user_role` rows, the MEMBER row and the non-Active Project's PM row before/after.
- **AC-006.10** — activation skips all deactivation guards and handovers; IT compares the full four-table snapshot after deactivate→activate (only `status` changes, `managedSpaces` empty).
- **AC-006.11** — 401 MSG07 / 403 MSG08 / non-UUID 404 MSG04 (new `/{invalid}/status` route) in `UserDetailControllerTest`; unknown id → 404 in unit test; BETA account id → 404 in IT.
- **AC-006.12** — 16 malformed bodies (non-object, missing/null/lower-case/unknown status, null/object/non-object-item/non-string handover lists, ACTIVE with either list) → MSG06, asserted with a last-Active-Admin target so MSG06 is proven to come before MSG56. JSON `null` lists are `NullNode` → MSG06, same as the mock.
- **AC-006.13** — MSG56 and MSG112 order tested in unit and IT. Locking: `serializeUpdates()` takes the same `pg_advisory_xact_lock(110, 7)` as the role update (transaction-scoped, per Workspace database), then `lockActiveAdmins` (FOR UPDATE). Two real races in `UserStatusIT`: mutual deactivation, and deactivation vs. Admin-role removal; both assert exactly one 200 and one 409 MSG56 and one remaining Active Admin. Without the shared lock both would succeed, so the tests do discriminate.
- **AC-006.14** — affected Projects = Active Projects where the Account is `PROJECT_MANAGER`; recipients = other Active PMs of the Project's Department; Spaces = all managed, recipients = other Active KMs (shared `handoverOptions`). Matching requires equal size, distinct ids, each option matched with an eligible recipient (so extra/unrelated/duplicate/foreign entries fail). Recipients are locked before re-reading options. Unit test covers 9 mismatch variants; IT covers missing, duplicate, ineligible (other Department) and non-Active Project.
- **AC-006.15** — `retainMember = target holds TICKET_RESOLVER`: row turned into `MEMBER` (IT, resolver) or deleted (IT, non-resolver); recipient added or upgraded `MEMBER`→`PROJECT_MANAGER` (IT); non-Active Project PM row kept (IT); one-PM index `uq_membership_one_manager` mapped to 500 MSG06 with rollback (unit).
- **AC-006.16** — order as above; trigger-forced audit failure in IT rolls back every write (snapshot unchanged). No-op: same status and no handover due → `getDetail` only, no write, no audit (unit, both statuses; IT, both statuses). A same-status deactivation that still has handovers due applies them and audits (unit), consistent with "no handover is due".
- **AC-006.17** — one `ROLE_CHANGE` event, target `USER`/Account, details `statusBefore`, `statusAfter`, `projectHandovers`, `spaceHandovers`; IT reads the stored JSON; no event on failures or no-op.
- **AC-006.18** — `scope.currentWorkspaceId()` first (unit: no repository touched when unbound; IT: 500 MSG06); BETA recipient, BETA Project, BETA Space → MSG01; BETA account → 404; tenant B rows unchanged.
- **AC-006.19** — `knowledge-replacement@roles-demo.test`, Active, KNOWLEDGE_MANAGER, no Department, no Space; seeder re-run leaves the row unchanged; absent from tenant B.

### KAN-110 unchanged

`HandoverChoices` (`Choice`, `choices`, `matchesProjects`, `matchesSpaces`, `distinct`, `uuid`) is byte-for-byte the
removed private code of `UserRoleUpdateServiceImpl`; the role service keeps its own `malformed()` and call order
(`serializeUpdates` → `lockTarget` → … unchanged). `UserRoleUpdateServiceImplTest` (46) and `UserRoleUpdateIT` (13)
pass.

## Traceability

```
✓ AC-006.9   UserStatusUpdateServiceImplTest, UserStatusIT
✓ AC-006.10  UserStatusUpdateServiceImplTest, UserStatusIT
✓ AC-006.11  UserStatusUpdateServiceImplTest, UserDetailControllerTest
✓ AC-006.12  UserStatusUpdateServiceImplTest, UserDetailControllerTest, UserStatusIT
✓ AC-006.13  UserStatusUpdateServiceImplTest, UserStatusIT
✓ AC-006.14  UserStatusUpdateServiceImplTest, UserStatusIT
✓ AC-006.15  UserStatusUpdateServiceImplTest, UserStatusIT
✓ AC-006.16  UserStatusUpdateServiceImplTest, UserDetailControllerTest, UserStatusIT
✓ AC-006.17  UserStatusUpdateServiceImplTest, UserStatusIT
✓ AC-006.18  UserStatusUpdateServiceImplTest, UserStatusIT
✓ AC-006.19  MockSignedInCallerIT
trace identity (STORY-006): 19/19 acceptance criteria have a test
```

## Issues

No blocking issue. Minor, optional:

1. **Minor (test naming)** — `backend/src/test/java/vn/tsolve/identity/controller/UserDetailControllerTest.java:137`
   `passesAbsentStatusBodyToService_AC_006_12_AC_006_16` only asserts that an absent body reaches the service as
   `null` (the mocked service throws MSG04); it proves neither MSG06 (AC-006.12) nor the order (AC-006.16). The
   behaviour itself is covered by `UserStatusUpdateServiceImplTest.missingBody_AC_006_12` and
   `targetBeforeMalformedBody_AC_006_11_AC_006_16`. Suggested fix: rename to e.g.
   `passesAbsentStatusBodyToService` (or cite only what it shows).
2. **Minor (test strength)** — `backend/src/test/java/vn/tsolve/identity/UserStatusIT.java:228`
   `rejectsNoProjectRecipientBeforeSpaceAndMatching_AC_006_14_AC_006_16` removes all of RECIPIENT's roles, so the
   Space may also lack a recipient depending on the tenant's other Knowledge Managers; the test proves MSG59 is
   returned but not that it wins over an otherwise-valid MSG118 scenario explicitly. The order is proven in the unit
   test `projectUnavailableBeforeSpaceAndMatching…` (both unavailable → MSG59), so this is informational only.
3. **Info** — `backend/src/main/java/vn/tsolve/identity/service/impl/UserStatusUpdateServiceImpl.java:121`: a
   repeated `DEACTIVATED` for an already Deactivated Account that still manages a Space/Active Project (possible via a
   KAN-110 role update on a Deactivated Account) is not a no-op: it requires the handovers and records a
   `ROLE_CHANGE` with `statusBefore == statusAfter`. This matches AC-006.14/.16/.17 and the mock; noted so the user
   knows it is intended.

## Assumptions made

- Assumed 500 MSG06 for a database failure after the first write (one-PM index, audit failure) satisfies AC-006.16's
  "roll back the whole change", as decided in Task 34 (design "Write" bullet); the spec states no status code for it.
- Assumed handover ids are matched as exact strings against the lower-case UUIDs the API returns (an upper-case UUID
  is refused with MSG01), as the MSW mock does; the spec does not address case.
- Spotless was not re-run by the reviewer (BUG-130); relied on the dispatcher's formatter run on the 11 changed files.
- ITs were not re-run by the reviewer; relied on the `mvn verify` reports on this commit in `backend/target/failsafe-reports`.

## Test Report

See `task-35-user-status-backend-test-report.md`: full suite 1086 unit + 391 IT, 0 failures/errors; reviewer rerun
of 114 unit tests green; trace 19/19.
