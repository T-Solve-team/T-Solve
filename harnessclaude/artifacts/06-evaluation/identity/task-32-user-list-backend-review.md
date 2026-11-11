---
module: identity
feature: user-list-backend
task: 32
verdict: pass
attempt: 1
date: 2026-11-10
reviewer: harnessclaude-reviewer (subagent)
---

# Review: identity — Task 32: User List backend on PostgreSQL (KAN-107)

## Summary

PR #162, range `980db994..a7c7659b`. The two endpoints meet AC-004.9 to AC-004.16:

- `GET /api/v1/users`: `UserListController.list` → `UserListQuery.of` → `UserListServiceImpl.list` → `UserListRepository.list`
- `GET /api/v1/users/departments`

The interim `identity-mock` sign-in (`MockSignedInCallerFilter`, `MockCallerReader`) and `MockUserListSeeder` meet
AC-004.17 and AC-004.18.

- **Query safety:** every filter is a bound parameter. The ORDER BY column and direction come from a fixed list, so
  no SQL injection path exists.
- **Search (`q`):** trimmed, lower-cased, and escaped for `\`, `%` and `_` (in that order) with `ESCAPE '\'`.
- **Roles:** loaded in one `IN (:ids)` query per page (no N+1). Their order comes from an `EnumSet` of `Role`.
- **Paging:** the offset is computed as a `long`, and `PageImpl` keeps the count-query total on an empty page.
- **Errors:** 401 and 403 come from `@PreAuthorize`. A bad enum or UUID fails type conversion, and an over-long `q`
  is refused in `UserListQuery.of`; none of these reaches the service.
- **Tenant check:** the service calls `TenantScope.currentWorkspaceId()` before any read.
- **Mock filter:** registered only through a `@Profile("identity-mock")` configuration. The filter class itself is not a component.

The tests check real database state and responses:

- JSON fields with explicit nulls
- exact row-id order for every sort
- a Beta row never returned or counted
- 500 MSG06 when no Workspace is bound
- a deactivated or downgraded mock caller
- the seeder run twice, compared with full-table snapshots

Later commits on main (KAN-108/109/110/295/296) only add to the feature's files: `UserListRepository.findById`, seed
rows on another email domain (`@roles-demo.test`), the `companyDomain` mock property, and new tests. None of them
changes STORY-004 behaviour. No Critical or Major finding.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Each AC checked against the code (below). Filters are combined with AND. EMPLOYEE means `NOT EXISTS user_role`. Sorts use `lower(...)` with `NULLS FIRST`/`NULLS LAST`, then `u.id ASC`. Departments are Active only, ordered by `lower(name)` then id. |
| Completeness | pass | All 10 backend ACs implemented. Changeset 019 adds `user_role` (UUID PK, cascading FK, CHECK on role, UNIQUE `(user_id, role)`) and `app_user.department_id`, with rollback. The seed has the Admin, 2 Departments and 6 demo Accounts. |
| Correctness | pass | Traceability 18/18. The tests compare exact id lists against an independent Java comparator for all 6 sorts. Full `mvn verify`: see the test report. |
| Integration | pass | Contract unchanged. `PagedModel` returns `content` plus `page{size,number,totalElements,totalPages}`. `DepartmentRef` is `{departmentId, name}`. `UserAccount` fields are always serialised (`@JsonInclude(ALWAYS)`). Paging is lenient like the MSW mock: size clamped to 1..100, page at least 0. |

### Per-criterion verification

- **AC-004.9** — `app_user` LEFT JOIN `department`. A blank `full_name` becomes `null`. Roles are EMPLOYEE plus the
  `user_role` rows, in enum order. The IT checks:
  - `roles == ["EMPLOYEE","TICKET_RESOLVER","ADMIN"]`; the rows were inserted in the opposite order, so the sorting is proven;
  - the Department id and name, and `lastSignInAt`;
  - explicit JSON nulls for both a `NULL` and a `''` name.
- **AC-004.10** — `q` matches `lower(coalesce(full_name,''))` or `lower(email)`; an empty `q` adds no clause.
  `role=EMPLOYEE` uses `NOT EXISTS`, other roles use `EXISTS`, and status and `departmentId` are equality checks.
  The IT covers each role, the combined filters, the literal characters `%`, `_` and `\`, and case-insensitive
  padded searches.
- **AC-004.11** — The first `field,asc|desc` with a known field wins; otherwise the sort is `fullName,asc`. A
  missing name sorts as an empty string. `lastSignInAt` puts nulls first when ascending and last when descending.
  Ties break on `u.id ASC`. A parameterised IT checks the exact order for all 6 field and direction pairs, including
  a case tie broken by id. The fallback is tested in the unit test and the IT.
- **AC-004.12** — Defaults are page 0 and size 20; size is clamped to 1..100. In the IT, `size=2&page=1` returns 2
  rows with totals 6 and 3 pages, `page=99` returns no rows with the same totals, and `size=500` is reported as 100.
- **AC-004.13** — More than 100 characters after trimming gives 400 MSG02 (`field_name: Search`, `max_length: 100`);
  exactly 100 is accepted. A bad role, status or departmentId gives 400. The controller test asserts
  `verifyNoInteractions(service)`.
- **AC-004.14** — Only Active Departments, ordered by `lower(name)` then `department_id`. The IT uses two names that
  differ only in case and leaves out a Deactivated Department.
- **AC-004.15** — `@PreAuthorize("hasRole(ADMIN)")` on both endpoints: 401 MSG07 for anonymous and 403 MSG08 for a
  non-Admin, both with no service call.
- **AC-004.16** — `currentWorkspaceId()` is called first; with no Workspace bound, no repository is called. The IT
  shows a Beta row is never returned or counted, and an unbound request gets 500 MSG06.
- **AC-004.17** — The filter runs only for `/api/` requests with no authentication yet. It runs inside
  `TenantScope.runIn(configured Workspace)`, re-reads the Active Account and its roles on each request, and clears
  the context afterwards. A missing or Deactivated Account stays anonymous. Unit tests and ITs cover the Deactivated
  caller (401), the ADMIN role removed (403), and the profile off (401).
- **AC-004.18** — The seeder runs inside `runIn` with `ON CONFLICT … DO NOTHING` for Departments, Accounts and
  roles. The data is mixed: roles, Departments, one DEACTIVATED and one nameless Account. The IT finds 7 `@acme.test`
  accounts and none in tenant B. Two more runs leave `app_user`, `user_role` and `department` identical.

## Traceability

```
✓ AC-004.9   UserListIT
✓ AC-004.10  UserListQueryTest, UserListIT
✓ AC-004.11  UserListQueryTest, UserListIT
✓ AC-004.12  UserListQueryTest, UserListIT
✓ AC-004.13  UserListQueryTest, UserListControllerTest
✓ AC-004.14  UserListIT
✓ AC-004.15  UserListControllerTest
✓ AC-004.16  UserListServiceImplTest, UserListIT
✓ AC-004.17  MockSignedInCallerFilterTest, UserListIT, MockSignedInCallerIT
✓ AC-004.18  MockSignedInCallerIT
trace identity (STORY-004): 18/18 acceptance criteria have a test (AC-004.1–004.8 are frontend)
```

## Issues

No blocking issue. Optional:

1. **Minor (test gap, AC-004.16)** — `backend/src/test/java/vn/tsolve/identity/UserListIT.java:244`: tenant isolation
   is proven for Accounts only. No test seeds a Department in tenant B and asserts that `/users/departments` leaves
   it out. The code path is the same routing datasource. Suggested fix: add a Beta Department and assert it is absent.
2. **Minor (test gap, AC-004.18)** — `backend/src/test/java/vn/tsolve/identity/mock/MockSignedInCallerIT.java:88,99`:
   no test asserts that the two seeded Departments exist and are Active, or that the seeded roles and statuses are
   mixed. Suggested fix: assert those rows.
3. **Minor (spec gap, AC-004.18)** — `backend/src/main/java/vn/tsolve/identity/mock/MockUserListSeeder.java:30`:
   `ON CONFLICT (name) DO NOTHING` keeps an existing `DEACTIVATED` Department with a seeded name as it is, so the
   seed does not "ensure two Active Departments" in that case. This fits "adding only the rows that are missing", so
   it is a spec gap, not a defect.
4. **Minor (spec gap, AC-004.11)** — `backend/src/main/java/vn/tsolve/identity/dto/UserListQuery.java:38`: `sort=email`
   (no direction) and `sort=email,ASC` fall back to `fullName,asc`. This matches the spec and contract text, but a
   generic Spring client could expect the Spring meaning.
5. **Info** — `UserListQuery.java:59` with `UserListRepository.java:38`: the pattern is lower-cased in Java, while the
   columns are lower-cased by PostgreSQL `lower()`. The two agree under the default `en_US.utf8` ctype but would
   differ for non-ASCII text under a `C` ctype. `LIKE lower(:pattern)` would be more robust.
6. **Info** — `MockSignedInCallerFilter.java:30,64`, three notes on the interim mock (KAN-275):
   - under `identity-mock` it would take priority over a real session sign-in;
   - it skips async re-dispatches;
   - nothing stops the profile from being enabled in production.
7. **Info** — `UserListIT.java:193`: the wildcard searches expect exactly one row across tenant A, so a future
   fixture with `_` or `%` in a name or email would break them.

## Assumptions made

- **Lenient paging:** a size below 1, a negative page or a page or size that is not a number is corrected, not
  refused with 400. This follows the design and the MSW mock; the spec states only the default and the cap.
- **Repeated `sort`:** the first valid value wins. The spec does not cover this; the mock reads only the first value.
- **Seeded Departments:** "ensure … adding only the rows that are missing" is read as "insert when missing" (Issue 3).
- **Mock priority:** the mock caller overriding a real session is accepted for the interim profile (Issue 6).
- **Tie-breaker:** `id ASC` in both sort directions.
- **Test run:** the reviewer did not run Maven. The dispatcher's `mvn verify` result is in the test report.

## Test Report

See `task-32-user-list-backend-test-report.md`: full `mvn verify` on `main` `29a42b7e` (BUILD SUCCESS) — 1086 unit + 395 IT, 0 failures/errors/skips; the feature's classes all green; formatter clean on the PR's Java files (BUG-130 workaround).
