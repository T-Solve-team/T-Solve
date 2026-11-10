---
module: identity
feature: scope-checks
task: 36
verdict: pass
attempt: 1
date: 2026-11-10
reviewer: harnessclaude-reviewer (subagent)
---

# Review: identity — Task 36: Shared scope checks (STORY-011, KAN-118)

## Summary

**PASS WITH NOTES.** This pass depends on the separate `mvn verify` run showing `ScopeAccessIT` at 14/14. See the Test Report and Assumptions.

`ScopeAccessService` / `ScopeAccessServiceImpl` / `ScopeAccessRepository` / `ScopeAccessDeniedException` meet
AC-011.1 to AC-011.11. I checked each SQL predicate by hand against the schema (changesets 002, 006, 008-search,
017, 019, 020, 023):
- Membership rows only count while the matching Role is held.
- The Account, the target Department and the Project's or Space's Department must be ACTIVE.
- A HIDDEN Space is refused.
- A REMOVED Project is allowed.
- ADMIN and EMPLOYEE appear in no predicate.
- A NULL `department_id` is safe, because it is only used inside `IN`.

The tests include the required negative cases across Departments, Projects and Workspaces. I found no critical
issues. The informational notes are below.

I reviewed uncommitted work on branch `feat/KAN-118-identity-scope-checks`: 4 new main files and 2 new test files.
The modified harness files were not evaluated as code.

## Stage 1 — Spec compliance

| AC | Result | Evidence |
|----|--------|----------|
| AC-011.1 | pass | Interface `vn.tsolve.identity.service.ScopeAccessService` (6 methods, exact signatures). `@Service` impl in `service/impl`. `require…` returns void. The IT autowires the interface, so the bean exists. |
| AC-011.2 | pass | `SCOPED_DEPARTMENTS` (`ScopeAccessRepository.java:18-27`) is `u.department_id` only when a `user_role` PROJECT_MANAGER row exists, UNION the Departments of the counted memberships. IT: `PM` allowed D1 and refused D2. `FORMER` (own Dept D3 plus a stale PM row in P3, no PM Role) is refused D3. The own Department and the Project Department are tested independently (IT:135-153). |
| AC-011.3 | pass | A MEMBER row counts only with TICKET_RESOLVER (`COUNTED_MEMBERSHIP`, `:10-16`). IT: TR allowed D1 and D2, refused D3. |
| AC-011.4 | pass | `PROJECT` (`:39-49`): a counted membership row of `p.public_id = :projectId`. Project status is not filtered. IT: REMOVED `PR` allowed, P2 and P3 refused, a stale row without the Role refused (IT:166-176, 254-263). |
| AC-011.5 | pass | `SPACE` (`:51-64`): a `space_manager` row joined to the KNOWLEDGE_MANAGER Role, OR `s.department_id IN SCOPED_DEPARTMENTS`. IT: KM allowed its Space and refused another. After the Role is removed, it is refused. The union with TR is tested (IT:199-210). PM and TR get the Spaces of their Departments only. |
| AC-011.6 | pass | No ADMIN or EMPLOYEE literal appears in any query. IT: ADMIN (own Dept D1 plus a MEMBER row in P1) and EMPLOYEE (no Role) get false from all three checks. |
| AC-011.7 | pass | `u.status = 'ACTIVE'` is in all three queries. `d.status = 'ACTIVE'` applies to the target Department, the Project's Department and the Space's Department. `s.status = 'ACTIVE'` refuses HIDDEN. IT: DEACTIVATED Account, deleted DX (Department, Project PX, Space SX, including a Space managed by KM), and HIDDEN S1/S3. |
| AC-011.8 | pass | Null arguments return false before any repository call (unit test, `verifyNoInteractions`). An unknown id gets EXISTS false with no throw (IT:265-277). |
| AC-011.9 | pass | `tenantScope.currentWorkspaceId()` runs first, also for null arguments (unit test: the same exception instance, no repository call). IT: BETA ids are refused while ACME is bound and allowed while BETA is bound. An unbound call throws `IllegalStateException`. The test router sends an unbound call to the main DB, so the exception really comes from `TenantScope`. |
| AC-011.10 | pass | `ScopeAccessDeniedException extends MessageCodeException(FORBIDDEN, "MSG08", Map.of())`. `GlobalExceptionHandler.handleMessageCode` (`common/exception/GlobalExceptionHandler.java:77`) renders it. IT: a refused Project and an unknown Project get the same exception. |
| AC-011.11 | pass | Every query is `SELECT EXISTS`. There is no logger, no audit call and no id in any exception message. The transaction is read-only. IT: the row counts of 5 tables, including `audit_log_entry`, are unchanged in both tenants. |

Out of Scope respected: `ticket` `ProjectAccess`, `review` `ReviewWorkspaceAccess`, `triage` `ManagedProjects` and
`search` `VisibleScope` are untouched. There is no `@PreAuthorize` use, no schema change and no endpoint.

## Stage 2 — Quality

### Critical
None.

### Informational
1. **`backend/src/main/java/vn/tsolve/identity/service/impl/ScopeAccessServiceImpl.java:12` (with `:24`, `:33`, `:42`)**
   - **What happens:** the class-level `@Transactional(readOnly = true)` starts the transaction, and with
     `JpaTransactionManager` that already checks out a connection, before the `tenantScope.currentWorkspaceId()`
     guard runs.
   - **Why it is fine today:** an unbound call reaches the default datasource, so the `TenantScope` exception wins
     and AC-011.9 holds.
   - **Risk:** when the platform router (KAN-196) throws at connection checkout for an unbound thread, callers would
     get that error, possibly wrapped as `CannotCreateTransactionException`, instead of the `TenantScope` exception.
     A null argument also costs a transaction.
   - **Consistency:** `UserDetailServiceImpl` uses the same pattern.
   - **Suggestion:** none needed now. Re-check AC-011.9 when the real router lands.
2. **`backend/src/main/java/vn/tsolve/identity/repository/ScopeAccessRepository.java` (whole class)**
   - **What happens:** it uses native SQL through `NamedParameterJdbcTemplate`. `backend/AGENTS.md` §3 says native
     SQL is only for pgvector or full-text.
   - **Why it is acceptable:** the identity module has the same precedent (`UserDetailRepository`), and the approved
     design names this approach. This is a convention deviation, not a defect.
3. **`ScopeAccessRepository.java:18-20`**
   - **What happens:** `SCOPED_DEPARTMENTS` can contain NULL (a Project Manager with no `department_id`). That is
     only correct because the set is used with `IN`. A later change to `NOT IN` would silently return no rows.
   - **Suggestion:** add a one-line comment. The only NULL case tested is a positive one (IT:137-140). No test has a
     NULL Department and no Projects together.
4. **Test gaps in `backend/src/test/java/vn/tsolve/identity/ScopeAccessIT.java`. Coverage is still adequate; none
   of these blocks the pass.**
   - The REMOVED Project `PR` (IT:97) is in D1, which `PM` already reaches through its own Department and P1. So no
     test shows that a Department reached *only* through a REMOVED Project is allowed by `canAccessDepartment`
     (spec Terms: "a Project counts whether it is ACTIVE or REMOVED").
   - No test binds BETA and asks about ACME ids (the reverse direction of AC-011.9).
   - The MSG08 403 rendering is not checked end-to-end through MockMvc. It relies on the shared handler.
5. **`ScopeAccessIT.java:340-344`:** the AC-011.11 check counts 5 tables, not every table. "Logs no id" is shown by
   code inspection only. Acceptable for a read-only `SELECT EXISTS` repository.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every predicate matches AC-011.2 to AC-011.7. The SQL was checked against the schema: `project_membership.project_id` → `project.public_id`, `space.department_id` → `department.department_id`, `space_manager.space_id` → `space.space_id`, `user_role (user_id, role)` unique. |
| Completeness | pass | All 11 ACs are implemented. Trace 11/11. |
| Correctness | pass (conditional) | Unit test `ScopeAccessServiceImplTest` 6/6 green. The latest `ScopeAccessIT` report on disk (21:34) shows 14 errors from **Docker being unavailable** ("Previous attempts to find a Docker environment failed"), not from an assertion. The final IT result depends on the separate `mvn verify` run. |
| Integration | pass | Matches the Interface Contracts `Services` entry. The exception follows the `ticket` `ProjectAccessDeniedException` shape. Callers use only the `service/` interface and `exception/`. |

## Traceability

```
✓ AC-011.1  ScopeAccessServiceImplTest.java
✓ AC-011.2  ScopeAccessIT.java
✓ AC-011.3  ScopeAccessIT.java
✓ AC-011.4  ScopeAccessIT.java
✓ AC-011.5  ScopeAccessIT.java
✓ AC-011.6  ScopeAccessIT.java
✓ AC-011.7  ScopeAccessIT.java
✓ AC-011.8  ScopeAccessServiceImplTest.java, ScopeAccessIT.java
✓ AC-011.9  ScopeAccessServiceImplTest.java, ScopeAccessIT.java
✓ AC-011.10 ScopeAccessServiceImplTest.java, ScopeAccessIT.java
✓ AC-011.11 ScopeAccessIT.java
trace identity (STORY-011): 11/11 acceptance criteria have a test
```

## Issues

No blocking issues. The informational items are listed under Stage 2.

## Assumptions made

- **Verdict depends on the IT run.** The verdict is `pass`, provided the separately running `mvn verify` reports
  `ScopeAccessIT` 14/14 and Spotless clean. I was told not to run `mvn verify`, so I did not run the IT myself. If
  that run shows any ScopeAccessIT failure, treat this review as **fail**.
- **Spec interpretation of AC-011.5.** "A Department for which `canAccessDepartment` is true" is read as the same
  Department set, including the ACTIVE Account and ACTIVE Department conditions. The implementation applies the
  same conditions.
- **Spec interpretation of REMOVED Projects.** A REMOVED Project also brings its Department into scope for
  `canAccessDepartment` and `canAccessSpace`, following spec Terms "a Project counts whether it is ACTIVE or
  REMOVED". The implementation does this, but no test isolates it (Informational 4).
- **Review file location.** The dispatcher named `05-reviews/identity/scope-checks-review.md`. The existing identity
  reviews live in `06-evaluation/identity/task-{N}-{slug}-review.md`, so this file follows that convention.
- **Feature status.** This review does not change the feature status. progress.json shows `scope-checks` as
  `in-progress`, not `evaluating`. The main session moves it after `mvn verify`.

## Test Report

See `task-36-scope-checks-test-report.md`.

## Condition met (main session, 2026-11-10)

The full `mvn verify` after the seed fix ran `ScopeAccessIT` 14/14 and `ScopeAccessServiceImplTest` 6/6; the new
files pass the Palantir check outside the worktree (BUG-130). The verdict stands: **pass**. The only other failures
are the flaky `ReviewQueueEventsIT` timing tests (BUG-197), outside this change. See `task-36-scope-checks-test-report.md`.
