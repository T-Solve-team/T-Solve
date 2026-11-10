---
module: identity
feature: add-user-backend
task: 33
verdict: pass
attempt: 1
date: 2026-11-10
reviewer: harnessclaude-reviewer (subagent)
---

# Review: identity — Task 33: Add user backend on PostgreSQL (KAN-108)

## Summary

PR #164, range `e2c496cc..595c6e74`. `POST /api/v1/users` (`UserCreationController.create` →
`UserCreationServiceImpl.create`) and `GET /api/v1/users/creation-options` meet AC-005.9 to AC-005.17. The checks
run in the same order as the MSW mock (`frontend/src/features/users/mocks/handlers.ts:51-118`):

1. Workspace bound
2. body is an object (else MSG03)
3. email required (MSG01)
4. raw length over 100 (MSG02)
5. format and company domain (MSG03)
6. duplicate email (409 MSG11 / MSG111)
7. roles (MSG01 Roles)
8. Department (MSG01 Department)
9. Spaces (MSG01 Spaces)
10. Space already managed (409 MSG117)
11. registry claim (409 MSG155)
12. inserts and audit
13. read the new Account back

All writes and the audit share one `@Transactional` method. The database backs up the earlier checks in two ways:
- The unique index on `app_user.email` catches a duplicate that slips past the check. `ON CONFLICT DO NOTHING`
  then answers MSG11.
- The `space_manager` primary key does the same for a Space, answering MSG117.

The Space rows are also locked `FOR UPDATE` in `space_id` order, so two creations for the same Space run one after
the other. The tests check real database state, not just status codes:
- the `app_user` columns, `user_role` count and `space_manager` rows
- the stored audit JSON
- no rows in tenant B
- after a real two-thread race, one manager and one Account

The later commits on main leave the feature's production files unchanged. KAN-109/110/296 changed only
`MockUserListSeeder`, `db.changelog-master.xml` and `MockSignedInCallerIT`. Changeset 026 adds a nullable
`keycloak_sub` column, which the insert does not need to set. No Critical or Major finding.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Each AC checked against the code (below). The order of checks and the message codes, fields and params match the mock and the spec. |
| Completeness | pass | All 9 backend ACs implemented. Changeset 020 adds `space` and `space_manager`. Also present: the `WorkspaceEmailRegistry` port with an accept-all interim, the `identity-mock` domain fallback, and seeded Spaces with one Knowledge Manager assignment. |
| Correctness | pass | No defect against a criterion found by reading the code. Full `mvn verify`: see the test report. |
| Integration | pass | Matches `tsolve-api.yaml` (`CreateUserRequest`, `UserCreationOptions`, `SpaceRef`, `UserAccount`). The response is read back through Task 32's `UserListRepository.findById`. KAN-110/109 reuse `space` and `space_manager` unchanged. |

### Per-criterion verification

- **AC-005.9**
  - The email is stripped and lower-cased. The Account is stored with `full_name NULL`, `status 'ACTIVE'`, no
    sign-in time, `user_role` rows without EMPLOYEE, the Department, and one `space_manager` row per Space.
  - The answer is 201 with the Account read back.
  - The IT sends a padded, upper-case email and duplicate roles and Spaces. It checks the columns, 3 `user_role`
    rows, 1 `space_manager` row, and zero rows in tenant B.
- **AC-005.10**
  - Missing, null, non-string or blank email gives MSG01 (`field_name: Email`). A raw length over 100 gives MSG02
    (`max_length: 100`). A bad format or another domain gives MSG03 on `email`.
  - Unit tests check each case and that nothing is inserted, claimed or audited. The controller test shows that 10
    malformed bodies give MSG03 without calling the service.
- **AC-005.11**
  - A Deactivated Account with that email gives MSG111; otherwise MSG11. Both are 409 on `email` and checked before roles.
  - A race at insert gives MSG11 through `ON CONFLICT (email) DO NOTHING`.
  - The IT covers both statuses. Unit tests cover the order and the conflict at insert.
- **AC-005.12**
  - These give MSG01 Roles: `roles` that is not an array, or an item that is not a string or not a Role.
  - These give MSG01 Department: a malformed id, a Department that is unknown or not Active, or a Project Manager with no Department.
  - Unit tests check the order (roles, then Department, then Spaces). The IT covers a Project Manager with no
    Department and a Deactivated Department, and checks no Account is created in either case.
- **AC-005.13**
  - These give MSG01 Spaces: `spaceIds` that is not an array, a bad UUID, an unknown or non-Active Space, a
    Knowledge Manager with no Space, or Spaces without the Knowledge Manager role.
  - A Space that already has a manager gives 409 MSG117 with `space_name`.
  - The IT covers a HIDDEN Space and an occupied Space. A real two-thread race gives exactly one 201 and one 409
    MSG117, with one `space_manager` row and one `app_user` row.
- **AC-005.14** — Lists the Active Spaces, ordered by `lower(name)` then `space_id`; Spaces that already have a
  manager are still listed. The IT checks the case-insensitive order and that a HIDDEN Space is left out.
  `MockSignedInCallerIT` checks the seeded Spaces.
- **AC-005.15**
  - The company domain is the Workspace's `googleDomain()`. A blank value is ignored and the `identity-mock`
    fallback (`tsolve.identity.mock.company-domain`) is used.
  - With no domain at all, the request fails with 500 MSG06 before the duplicate check and the claim, so nothing is created.
  - Covered by unit, controller and IT tests.
- **AC-005.16**
  - The registry claim comes after every check and before the first insert. A refused claim gives 409 MSG155 on `email`.
  - The claim is released on any thrown failure, and also on a rollback found by `afterCompletion`. An
    `AtomicBoolean` makes sure it is released only once.
  - Unit tests cover the refusal, an insert failure, both conflicts, a single release, and commit against rollback.
    The IT shows a refusal leaves no row.
- **AC-005.17**
  - Both endpoints need the Admin role: anonymous gets 401 MSG07 and a non-Admin gets 403 MSG08.
  - Each creation records one `ROLE_CHANGE` event on target `USER`, with details `roles` (EMPLOYEE included), `departmentId` and `spaceIds`.
  - The controller test covers 401 and 403 for both endpoints. The IT reads the stored audit JSON. The unit test
    checks the exact details and that no audit is written on a failure.

## Traceability

```
✓ AC-005.9   UserCreationServiceImplTest, UserCreationIT
✓ AC-005.10  UserCreationServiceImplTest, UserCreationControllerTest
✓ AC-005.11  UserCreationServiceImplTest, UserCreationIT
✓ AC-005.12  UserCreationServiceImplTest, UserCreationIT
✓ AC-005.13  UserCreationServiceImplTest, UserCreationControllerTest, UserCreationIT
✓ AC-005.14  UserCreationIT, MockSignedInCallerIT
✓ AC-005.15  UserCreationServiceImplTest, UserCreationControllerTest, UserCreationIT
✓ AC-005.16  UserCreationServiceImplTest, UserCreationIT
✓ AC-005.17  UserCreationServiceImplTest, UserCreationControllerTest, UserCreationIT
trace identity (STORY-005): 17/17 acceptance criteria have a test (AC-005.1–005.8 are frontend)
```

## Issues

No blocking issue. Optional:

1. **Minor (design, interim registry)** — `backend/src/main/java/vn/tsolve/identity/service/impl/UserCreationServiceImpl.java:170-171, 185-187`
   - **What happens:** when `insertAccount` loses the email race (MSG11), the service releases the claim for an
     email that now belongs to the winning Account.
   - **Why it is fine today:** this matches AC-005.16's wording, and the accept-all interim registry ignores the release.
   - **Risk later:** once platform's real `workspace_email` registry replaces the interim, this release could
     delete the winner's claim.
   - **Suggested fix, before the real registry lands:** state in the port that a claim already held is refused, or
     skip the release on the MSG11 race. `UserCreationServiceImplTest.releasesClaimOnEmailConflictAtInsert_AC_005_11_AC_005_16`
     pins the current behaviour.
2. **Minor (test strength)** — `backend/src/test/java/vn/tsolve/identity/UserCreationIT.java`
   - **Gap:** no IT forces a failure after the first insert (for example a trigger-forced audit failure, as in
     `UserStatusIT`) and then checks that `app_user` and `user_role` were rolled back.
   - **Current cover:** AC-005.9's "one transaction" rests on `@Transactional` and the unit tests.
3. **Minor (test strength)** — `UserCreationIT.java:250-262`: `rejectsExistingAccount_AC_005_11` checks the 409 but
   not that no `user_role` or audit row was added.
4. **Minor (test naming)** — `UserCreationIT.java:242`: `sameEmailInBetaDoesNotBlockAcme_AC_005_16` proves the
   duplicate check is per tenant (AC-005.11), not the registry behaviour of AC-005.16. Suggested fix: rename it or
   cite AC-005.11.
5. **Info** — A tenant-B `departmentId` or `spaceId` is not tested. Isolation follows from the routed database. The
   unit test also does not cover an unbound Workspace on `create`.
6. **Info** — `backend/src/main/java/vn/tsolve/identity/repository/UserCreationRepository.java:34-42`: the Department
   is read without a lock, unlike the Spaces. The spec does not ask for one.
7. **Info, small differences from the mock:**
   - Upper-case UUIDs are accepted.
   - With several occupied Spaces, MSG117 names the first by `space_id`, not by catalog order.
   - `toLowerCase(Locale.ROOT)` on a non-ASCII local part could differ from PostgreSQL `lower()`.

## Assumptions made

- **Non-object body:** a missing, malformed or non-object body is answered 400 MSG03 with no field, as the MSW mock
  does. AC-005.10 does not mention this case.
- **Department for other Roles:** a Department sent for a Role other than Project Manager is stored, as AC-005.9 and
  the mock do. The OpenAPI `CurrentUser` description says null for anyone who is not a Project Manager.
- **Release after an email race:** releasing the claim is accepted under the accept-all interim registry. The user
  needs to decide this before the real registry lands (Issue 1).
- **Test run:** the reviewer did not run Maven. The dispatcher's `mvn verify` result is in the test report.

## Test Report

See `task-33-add-user-backend-test-report.md`: full `mvn verify` on `main` `29a42b7e` (BUILD SUCCESS) — 1086 unit + 395 IT, 0 failures/errors/skips; the feature's classes all green; formatter clean on the PR's Java files (BUG-130 workaround).
