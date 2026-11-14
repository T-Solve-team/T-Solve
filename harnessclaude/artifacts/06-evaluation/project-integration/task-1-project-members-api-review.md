---
module: project-integration
feature: project-members-api
task: 1
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer (independent subagent)
iteration: 1
---

# Review: project-integration — Task 1: project-members-api

## Summary
Commit range `9736652..782e23f` (backend only; backend is unchanged at HEAD `f9e60c9`). The four endpoints, the manager check, the all-or-nothing add with its audit entry, the candidate search and migration 029 match AC-001.1–AC-001.4 and AC-002.1–AC-002.8. Each criterion has passing unit and/or IT tests, and the tests are named with the AC id as a suffix, which is the `backend/AGENTS.md` convention. There are no blocking findings. One Medium concurrency gap (issue 1) means the design's "FOR SHARE" decision does not fully hold. It does not affect ticket visibility and should be fixed as a follow-up or in a quick change.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.1 header via JDBC join (PM name from the unique PM row, `uq_membership_one_manager`). AC-001.2 order `role <> 'PROJECT_MANAGER', lower(full_name) NULLS LAST, email`. AC-001.3: `@PreAuthorize hasRole(PROJECT_MANAGER)` plus `isManager` (Active + PM role + PM membership row); `AccessDeniedException` maps to 403 MSG08 in `GlobalExceptionHandler`. AC-001.4: the 404 lookup runs before the 403 manager check (D4). AC-002.1–2.3: escapes `\ % _` with `ESCAPE '\'`, trims `q`, `LIMIT 20`, `>100` gives 400 MSG02; this is the same pattern as `UserListRepository`. AC-002.4–2.8: Bean Validation and the service duplicate check give 400; REMOVED or ineligible ids give 409 MSG06 before any insert; then batch insert (`date_added` DB default `now()`), audit, member list, all in one `@Transactional`. `AuditServiceImpl.record` refuses to run without a transaction, so the audit cannot escape the transaction. |
| Completeness | pass | All 12 task criteria are implemented. E1–E9 and E11 each have a test. E10 needs no special case, because departments are soft-deleted (status) so the inner join still finds them. |
| Correctness | pass | ProjectMembersIT 17/17, ProjectMemberServiceImplTest 10/10, full `mvn clean verify` exit 0 (orchestrator evidence). Re-ran the unit test: exit 0. See issues 1–3. |
| Integration | pass | Uses `TenantScope.currentWorkspaceId()`, `TSolveUser.current()` and `AuditService` as specified. Tenant isolation: queries go through the tenant-routed `NamedParameterJdbcTemplate`, and the IT shows a Tenant B resolver never appears as a candidate and gets 409 on POST. Migration 029 follows the 027 shape, with rollback to the 027 list. `ScopeAccessRepository.COUNTED_MEMBERSHIP` already counts MEMBER rows only with the TR role, so new members get Department visibility with no new code (FR 4). |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace project-integration STORY-001 STORY-002` reports 23/23 acceptance criteria with a test. Task 1 criteria:

- AC-001.1, AC-001.2: ProjectMembersIT
- AC-001.3, AC-001.4: ProjectMemberServiceImplTest, ProjectMembersIT
- AC-002.1: ProjectMembersIT
- AC-002.2 to AC-002.8: ProjectMemberServiceImplTest, ProjectMembersIT

The command exits 1 only because of the warning "ids in tests but not in the spec: AC-003.1, AC-003.8". Those ids come from other modules' files (`ai`, `ticket`, `tracker`, `triage`, frontend router tests), not from this task.

## Issues

1. **Medium (non-blocking): the FOR SHARE eligibility lock does not stop the role-removal race when identity locks first.** `backend/src/main/java/vn/tsolve/project/repository/ProjectMemberRepository.java:110-116`
   - What happens: identity's role update locks the target `app_user` row `FOR UPDATE` (`UserRoleUpdateRepository.java:32`), deletes its `user_role` rows and updates `app_user.department_id`. If our `SELECT … AND ELIGIBLE … FOR SHARE OF u` starts during that transaction, it waits for the lock. Under READ COMMITTED, Postgres then re-checks only the locked `app_user` row. The `EXISTS (user_role … 'TICKET_RESOLVER')` subquery still sees the statement's original snapshot, so the account counts as eligible. The MEMBER row is then inserted after identity's `removeMembers` has already run.
   - Impact: a MEMBER row without the TR role. It gives no visibility now (`COUNTED_MEMBERSHIP` requires TR), but it comes back to life if the TR role is granted again. The other order (we lock first) is safe.
   - Fix: split the method into two statements. First `SELECT id FROM app_user WHERE id IN (:ids) ORDER BY id FOR SHARE`, then a separate `SELECT count(*) … WHERE u.id IN (:ids) AND ELIGIBLE`; the new statement takes a fresh snapshot after the lock is granted. `ORDER BY id` also matches identity's ordered locking (`UserRoleUpdateRepository.java:97`), which avoids a deadlock. Prove it with a two-connection IT if feasible.
2. **Low: 400 can come before 403 or 404 on POST.** `ProjectController.java:73-77` (`@Valid` body) and `ProjectMemberServiceImpl.java:72-75`
   - Bean Validation runs before the service, so a non-manager (or a PM posting to an unknown Project) with an invalid body gets 400, not 403 or 404. No Project data leaks.
   - This is a strict-reading deviation from AC-001.3 ("403 to every endpoint"). See Assumptions.
   - Fix if wanted: drop `@Valid` and validate in the service after `requireManagedProject`, or accept the order and note it in the spec.
3. **Low: E5 (concurrent duplicate insert → 409) is tested only with a mocked `DuplicateKeyException`.** `ProjectMemberServiceImplTest.java:163`
   - The service maps a `DuplicateKeyException` from `jdbc.batchUpdate` to 409. Nothing shows that pgjdbc's `BatchUpdateException` (SQLState 23505) is really translated to `DuplicateKeyException`.
   - Expected to hold with Spring 6's translator, but it is unproven.
   - Fix: optionally an IT that inserts the row inside the add (or a second connection) and asserts 409.

## Assumptions made
- **Issue 1 does not fail the task.** It is graded Medium and non-blocking: it is a race outside the AC text, and the stale row grants no visibility. The user should decide between fixing it now (quick change) and a follow-up.
- **400-before-403/404 for a malformed body (issue 2) meets AC-001.3/AC-001.4.** I read "return no Project data" as the intent of AC-001.3. The user should confirm or ask for the order to change.
- **The `_AC_00x_y` suffix meets the DoD wording "name starts with its AC id".** `backend/AGENTS.md` sets the suffix form and the trace tool accepts it.
- **Lowercasing `q` in Java (`Locale.ROOT`) against Postgres `lower()` is acceptable.** It is the existing `UserListRepository` pattern. Accented Vietnamese names match only if the DB collation lowercases non-ASCII; this was not verified.

## Test Report
See `task-1-project-members-api-test-report.md`. `mvn clean verify` exit 0 on this backend code: 1473 unit tests and 495 ITs, 0 failures, spotless included. ProjectMembersIT 17/17 and ProjectMemberServiceImplTest 10/10 (report XMLs read). Targeted re-run of `ProjectMemberServiceImplTest`: exit 0.
