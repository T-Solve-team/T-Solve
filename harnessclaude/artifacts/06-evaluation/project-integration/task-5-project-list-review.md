---
module: project-integration
feature: project-list
task: 5
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: project-integration — Task 5: List the caller's Department Projects (STORY-004, STORY-007, KAN-127)

## Summary

I reviewed code commit `05aeb3ad` and harness commit `38afebf1`, diffed against `3b860534`. The change touches:
- 4 main files: `ProjectController` (+10), `ProjectLinkRepository` (+27), `ProjectService` (+4) and
  `ProjectServiceImpl` (+26 −5)
- 2 new DTOs: `ProjectListResponse` and `ProjectRowResponse`
- 3 test files: `ProjectServiceImplTest` (+76), `ProjectControllerTest` (+116) and the new `ProjectListIT` (289 lines)

I checked it against:
- spec STORY-004 (AC-004.1–AC-004.8) and STORY-007 (AC-007.1–AC-007.8) as they apply to GET
- the `ProjectList`/`ProjectRow` tables in the spec's Interface Contracts
- `listProjects`, `ProjectList`, `ProjectRow`, `ProjectManagerRef`, `DepartmentRef` and `ProjectStatus` in
  `frontend/openapi/tsolve-api.yaml`
- Task 5 in `iteration-1/tasks.md`
- edge-case rows 13, 25–27 and 30 of the master `design.md`
- D26, D32, D36 and D50–D53 in `kan-127-decisions.md`
- the DDL for `project`, `project_membership` and `uq_membership_one_manager` (changesets 002, 017 and 023)

I read the session log only for the commit ids, the listed deviations and the claimed test counts.

`GET /api/v1/projects` runs `checkScope()` (`ProjectServiceImpl.java:184-191`) and then one SQL read,
`ProjectLinkRepository.listProjects` (`:177-195`). `checkScope()` checks, in order:
1. the bound Workspace, so an unbound call is 500 MSG06 before any read
2. the session principal
3. `findCallerDepartment`: an Active account, the PM role in the database and an Active Department, or 403 MSG08

The query:
- filters `project` by the caller's internal `department_id`, with no status filter
- LEFT JOINs the single `PROJECT_MANAGER` row; `uq_membership_one_manager` guarantees at most one, so rows cannot
  multiply
- LEFT JOINs `app_user` only while `status = 'ACTIVE'`
- counts every membership row in a correlated subquery
- orders by `jira_project_key COLLATE "C"`

The handler takes no parameters, so a client cannot pass paging or scope. Nothing on the path calls the tracker,
the audit service or a transaction manager.

The three listed deviations are acceptable:
- **`COLLATE "C"`:** stable code-point order, independent of the database locale (Assumptions).
- **No `@Transactional(readOnly = true)` on `list()`:** the rationale holds, and it keeps the Workspace check ahead of
  any connection. The deviation from `backend/AGENTS.md` §3 should still be written down (INFO-2).
- **`checkScope()` pulled out of `checkScopeAndKey`:** a mechanical move. The POST unit tests and ITs stay green, and
  AC-007.8's order is unchanged.

There are no CRITICAL, HIGH or MEDIUM findings. All targeted tests pass, and all four SQL mutations are killed.
Findings:
- LOW-1: `project.status` has no CHECK constraint, and the row mapper copies it as a raw string, so a third value
  would leak past the `ProjectStatus` enum.
- INFO-2 and INFO-3 are notes, not defects.

Findings already accepted under D50 or D52 are not raised again.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 8 STORY-004 criteria and the 8 STORY-007 criteria hold for GET (Stage 1 table). |
| Completeness | pass | Every criterion has a test that names it (trace 16/16). Every Task 5 test bullet (rows 13, 25–27, 30) has a matching test. Nothing EXTRA: `checkScope()` is a refactor of existing code with no behaviour change. |
| Correctness | pass | `ProjectServiceImplTest` 127/127, `ProjectControllerTest` 77/77, `ProjectListIT` 10/10, and the other Project ITs (61/61 in total) pass. Spotless passes. Mutations of the active-manager filter, the count, the collation and the role filter are each killed by `ProjectListIT`. |
| Integration | pass | The rendered body matches `ProjectList` exactly: the IT compares the whole JSON tree, and the controller test runs a strict `content().json(…, true)`. The contract was unchanged by this commit, and the generated client already types `ProjectRow.status` as `ProjectStatus`. `DepartmentRefResponse` is reused from identity. Jackson's default inclusion writes `"projectManager": null`, so the required, nullable field is present. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-004.1 200 `ProjectList`, own Department `{departmentId, name}`, all Active and Removed Projects | PASS (LOW-1) | `ProjectServiceImpl.java:297-302` builds `DepartmentRefResponse(department.departmentPublicId(), department.name())`, a public UUID. The IT compares the whole body: four own Projects (two Active, two Removed), with the foreign `PLSOTHER` absent. No status filter exists, so any third value would also be listed (LOW-1). |
| 2 | AC-004.2 key ascending, no paging | PASS | `ORDER BY p.jira_project_key COLLATE "C"` (`ProjectLinkRepository.java:185`). Rows are inserted out of order and come back as `PLS9, PLSA, PLSB, PLS_A`. Controller test `ignoresClientPagingAndScopeParameters…` sends `page`/`size`/`sort` and checks that the body has no `page`. Mutation `no-collate` is killed (2 tests). |
| 3 | AC-004.3 row = `projectId`, `jiraProjectKey`, `projectManager`, `memberCount`, `status` | PASS | `ProjectRowResponse.java:6-10` has exactly these 5 fields, plus nested `ProjectManagerRef(userId, fullName)`. `projectId` is `p.public_id`. Strict JSON equality in both the IT and the controller test. |
| 4 | AC-004.4 Active PM account → `{userId, fullName}` | PASS | `LEFT JOIN app_user u ON u.id = m.user_id AND u.status = 'ACTIVE'`. IT: `PLSB` shows `PLS Moved`, an Active account in another Department with no PM role. This is the D26 display rule, distinct from D27 (row 30). `userId` is `app_user.id` (D32). |
| 5 | AC-004.5 no PM row or PM not Active → null | PASS | IT: `PLS9` and `PLS_A` have no PM row → null. `PLSA`'s PM `PLS Gone` is DEACTIVATED → null. A clean `no-active-filter` mutation (join line only) is killed. Mutation `no-role-filter` turns 4 rows into 7 and is killed by 3 tests. |
| 6 | AC-004.6 count all membership rows, including the PM | PASS | The subquery counts `project_membership` by `project_id` with no join to `app_user`. IT counts 1/3/2/0, including a DEACTIVATED former PM and a DEACTIVATED MEMBER. Mutation `count-active-only` is killed. |
| 7 | AC-004.7 no Projects → 200 with Department and `[]` | PASS | IT `returnsTheDepartmentWithAnEmptyProjectList_AC_004_7` (caller in `PLS Empty`). Controller and unit tests too. |
| 8 | AC-004.8 no Jira call, no persisted write | PASS | The service has no tracker, audit or transaction call on this path. The unit test asserts `verifyNoInteractions(tracker, audit, transactionManager, projectRepository)` and only two repository calls. The IT asserts `tracker.calls()` is empty and that row-level JSON snapshots of the tenant and main databases (department, app_user, user_role, project, project_membership, audit_log_entry) are unchanged. |
| 9 | AC-007.1 `@PreAuthorize` with `Roles.PROJECT_MANAGER` | PASS | `ProjectController.java:49-55`. A reflection test asserts the exact value `hasRole('PROJECT_MANAGER')`. |
| 10 | AC-007.2 anonymous → 401 MSG07 | PASS | `refusesAnonymousList_AC_007_2`: no field problem, and the service is never called. |
| 11 | AC-007.3 wrong role → 403 MSG08 | PASS | Employee and Admin-only callers: no field problem, service never called. GET has no body, so D36's "valid body" case does not arise. |
| 12 | AC-007.4 stale account, role, missing or inactive Department → 403 MSG08 | PASS | IT, parameterised over 4 changes made in the database while the session still says PM. Each gives 403 MSG08 with `{}`, no `fieldErrors`, no `projects`, no tracker call and an unchanged snapshot. Unit tests cover the missing principal and the missing Department. |
| 13 | AC-007.5 unbound Workspace → 500 MSG06 | PASS | `scope.currentWorkspaceId()` is the first statement of `checkScope()`. IT: 500 MSG06 `{}`, main database unchanged. Unit test: the exception propagates with zero repository interactions. |
| 14 | AC-007.6 bound database only | PASS | IT `listsOnlyTheBoundWorkspaceDatabase_AC_007_6`: the same key `PLSB` in BETA, with a different id and count. ACME still lists its own 4. |
| 15 | AC-007.7 scope from `TenantScope` and `app_user.department_id` only | PASS | `list()` has no parameters (reflection test). IT and controller test send forged `departmentId`/`workspaceId` and get the own Department. |
| 16 | AC-007.8 Workspace and eligibility before any other read | PASS | Unit `InOrder`: `currentWorkspaceId` → `findCallerDepartment` → `listProjects`. GET has no key or link check. |

### Stage 2 — Quality and security pass

Checked and found sound:
- **Row multiplication.** The manager join could duplicate a Project only if a Project had two `PROJECT_MANAGER`
  rows, which `uq_membership_one_manager` (changeset 023) forbids. The `app_user` join is on the primary key. The
  count is a scalar subquery, so it cannot be inflated by the joins either.
- **Query plan.** I ran `EXPLAIN (ANALYZE, BUFFERS)` on a throwaway `pgvector/pgvector:pg16` with the same tables
  and indexes: 50 Departments, 5,000 users, 2,000 Projects and 60,000 memberships, 30 per Project. For one Department
  (40 Projects) the plan is:
  - Bitmap Index Scan on `idx_project_department`
  - Index Scan on `uq_membership_one_manager` for the manager
  - Index Scan on `app_user_pkey`, with the status as a filter
  - for the correlated count, one Bitmap Index Scan on `idx_membership_project` per Project (loops = 40)
  - an in-memory quicksort of 40 rows on `COLLATE "C"`

  Execution took 1.3 ms with 1,544 shared buffer hits and no sequential scan. The cost grows with the Department's
  Projects × members, and D26 expects a few Projects per Department. No paging is needed, and no finding.
- **Transactions.** Two autocommit reads on separate pooled connections. Under READ COMMITTED a read-only
  transaction would give each statement its own snapshot anyway, so it would add no consistency. The session log's
  reasoning is correct (INFO-2).
- **Status values.**
  - Only three writers set `project.status`: `insertProject` (`'ACTIVE'`), `reconnectProject` (`'ACTIVE'`) and
    `MockUserListSeeder` (`ACTIVE`/`REMOVED`). The JPA entity maps an enum. No code path writes a third value today.
  - The column itself is an unconstrained `VARCHAR(20)` (changeset 002, no CHECK), and the mapper copies it verbatim
    (LOW-1).
- **SQL safety.** A named parameter, a `long` taken from the server-side Department row, and nothing from the request.
- **Data exposure.** The row exposes only the D26/D32 fields. `fullName` is the PM's display name, which the same PM
  population already sees in the UI. No credential, internal BIGINT id or email.
- **Logging.** No new log statement.

## Traceability

Run from the main repo root (HEAD `88bb8329`; the Task 6 developer's uncommitted frontend work does not touch these
ids).

```
  ✓ AC-004.1  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectListIT.java
  ✓ AC-004.2  ProjectControllerTest.java, ProjectListIT.java
  ✓ AC-004.3  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectListIT.java
  ✓ AC-004.4  ProjectControllerTest.java, ProjectListIT.java
  ✓ AC-004.5  ProjectControllerTest.java, ProjectListIT.java
  ✓ AC-004.6  ProjectListIT.java
  ✓ AC-004.7  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectListIT.java
  ✓ AC-004.8  ProjectServiceImplTest.java, ProjectListIT.java
  ✓ AC-007.1  ProjectControllerTest.java
  ✓ AC-007.2  ProjectControllerTest.java
  ✓ AC-007.3  ProjectControllerTest.java
  ✓ AC-007.4  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectConnectionIT.java, ProjectCreationIT.java, ProjectListIT.java
  ✓ AC-007.5  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectConnectionIT.java, ProjectCreationIT.java, ProjectListIT.java
  ✓ AC-007.6  ProjectConnectionIT.java, ProjectCreationIT.java, ProjectListIT.java, ProjectReconnectIT.java
  ✓ AC-007.7  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectListIT.java
  ✓ AC-007.8  ProjectServiceImplTest.java, ProjectCreationIT.java
trace project-integration (STORY-004, STORY-007): 16/16 acceptance criteria have a test
```

## Issues

No CRITICAL, HIGH or MEDIUM findings. Non-blocking:

1. **LOW-1 — a third `project.status` value would leak into the list past the `ProjectStatus` enum**
   - Location: `backend/src/main/java/vn/tsolve/project/repository/ProjectLinkRepository.java:193`
     (`rs.getString("status")`) and `backend/src/main/java/vn/tsolve/project/dto/ProjectRowResponse.java:7`
     (`String status`).
   - The column is `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` with no CHECK (changeset 002). The query has no status
     filter.
   - A third value would go out unchanged. Possible sources: a future "remove Project" or "disable" path, a manual fix
     in the database, or another module's fixture. That would break the contract's `ProjectStatus` enum
     `[ACTIVE, REMOVED]` and the generated frontend type. It also contradicts AC-004.1 read literally ("all its
     Active and Removed Projects").
   - The JPA entity would throw on the same row, so the two read paths would disagree. No current code path writes
     such a value, so this is defensive, not a live defect. `ProjectResponse.status` (Task 3) has the same `String`
     pattern.
   - Fix (one line): map through the enum, `ProjectStatus.valueOf(rs.getString("status")).name()`, or type the DTO
     field as `ProjectStatus`. A bad row then fails loudly as 500 MSG06 instead of reaching the browser. The
     alternative is `AND p.status IN ('ACTIVE', 'REMOVED')`, which hides it silently. A CHECK constraint would be a
     schema change outside this task. Fits the D50 hardening pass.
2. **INFO-2 — no `@Transactional(readOnly = true)` on `list()`; the deviation from `backend/AGENTS.md` §3 is recorded
   only in a code comment and the session log**
   - Location: `ProjectServiceImpl.java:295-302`.
   - Judged acceptable:
     - Under READ COMMITTED a read-only transaction would not make the two reads consistent.
     - Opening one would take a connection (in the IT, on the main database) before `checkScope()` runs the Workspace
       check, which AC-007.5/AC-007.8 want first.
     - It matches `testConnection`.
   - The one thing a read-only transaction would add is a database-enforced "no write" for AC-004.8. The snapshot IT
     already proves that.
   - Fix: none in code. Record the exception to §3 as a decision row (or a one-line note in `design.md`), so the next
     reviewer does not re-raise it.
3. **INFO-3 — `COLLATE "C"` puts `_` after the letters**
   - Location: `ProjectLinkRepository.java:185`.
   - In code-point order `PLS_A` sorts after `PLSB`. Under a linguistic collation (`en_US.UTF-8`) it sorts before
     it. The IT pins the code-point order.
   - This matches Java's `String.compareTo` and does not depend on the database locale. The web app shows the
     returned order as is (AC-005.2), so the browser cannot disagree.
   - Fix: none (Assumptions).

## Assumptions made

- **"Ascending" in AC-004.2 means code-point order.** The spec does not name a collation. I accept `COLLATE "C"`
  because it is deterministic across database locales and matches Java. If the team wants dictionary order, where
  `_` is ignored at the first level, it is a one-word change and an update to the IT expectation. **Open judgment
  call (minor).**
- **LOW-1: fail loudly or filter.** AC-004.1 can be read as "list only Active and Removed" (filter) or as "the only
  statuses are Active and Removed" (map through the enum and fail on anything else). I recommend the enum mapping.
  **Open judgment call: which fix, and whether now or in the D50 hardening pass.**
- **INFO-2 is accepted, not a finding against the team rule.** `backend/AGENTS.md` §3 asks for `readOnly = true` on
  reads. I judged the deviation justified for the reasons above. If the team wants §3 applied literally, the change is
  one annotation, and the Workspace check would then run after the transaction opens. **Open judgment call: log a
  decision row, or add the annotation.**
- **Manager display (AC-004.4).** I read "names an Active account" as the account status alone, per D26 and edge
  row 30, so an Active manager who has moved Department or lost the PM role is still shown. The IT pins this.
- The accepted D50 and D52 items (T3 LOW-1 create race, INFO-2 Department at Save time, INFO-3 READ COMMITTED; T4
  LOW-1 to LOW-3) are not raised again.

## Test Report

Isolated worktree at `38afebf1` (`git worktree add …/scratchpad/wt-review-t5 38afebf1`). I did not use the main
checkout for Maven, because the Task 6 developer is working there. Every run was from `backend/` in Git Bash, with
JAVA_HOME set to the IntelliJ JBR and Docker 29.1.3.

- `mvn -B -Dapi.version=1.44 -Dtest='Project*Test,*Project*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='Project*IT' verify`
  ```
  Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.controller.ProjectConnectionSecrecyTest
  Tests run: 77, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.controller.ProjectControllerTest
  Tests run: 127, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.service.impl.ProjectServiceImplTest
  Tests run: 246, Failures: 0, Errors: 0, Skipped: 0
  Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectConnectionIT
  Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectCreationIT
  Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectDoneStatusesMigrationIT
  Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectListIT
  Tests run: 19, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectReconnectIT
  Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.review.ProjectMembershipReviewAccessIT
  Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
  spotless:3.2.0:check (default) … BUILD SUCCESS
  ```
  The `ERROR … Unhandled exception` log lines in the run are the expected 500 paths of existing tests.
- Mutations: each one was applied to `ProjectLinkRepository.java` in the worktree. Then
  `-Dtest=none -Dit.test=ProjectListIT -Dspotless.check.skip=true verify` was run, and
  `git checkout -- backend/src/main` restored the file (`git status` clean afterwards).

  | Mutation | `ProjectListIT` | Result |
  |----------|-----------------|--------|
  | `no-active-filter`: `LEFT JOIN app_user u ON u.id = m.user_id` (join line only) | 1 failure: `listsTheOwnDepartmentProjects…_AC_004_1_…_AC_004_6` (body: deactivated `PLS Gone` shown) | killed |
  | `no-active-filter` (first, broader run that also dropped the `u.status` check in `findCallerDepartment`) | 2 failures: the body test, plus `refusesAnIneligibleCaller…_AC_007_4` "expected 403 but was 200" | killed (also shows that AC-007.4 depends on that check) |
  | `count-active-only`: the count joins `app_user` with `status = 'ACTIVE'` | 1 failure: the body test (counts 1/3 became 0/2) | killed |
  | `no-collate`: drop `COLLATE "C"` | 2 failures: the body test, plus `listsOnlyTheBoundWorkspaceDatabase_AC_007_6` (`projects[2]` is a different id) | killed |
  | `no-role-filter`: drop `AND m.role_in_project = 'PROJECT_MANAGER'` (the same sed also hit `findLink`, which this IT does not call) | 3 failures: the body test, `…_AC_007_6` and `…_AC_007_7` ("expected 4 but was 7" rows) | killed |
- Query plan: a throwaway `pgvector/pgvector:pg16` container (removed afterwards), with numbers in Stage 2.
  Execution took 1.3 ms for 40 Projects × 30 members, index-only access paths, and no sequential scan.
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-004 STORY-007`: 16/16 (above), exit 0.
- Full `mvn verify`: I did not rerun it. The session log reports unit 1641 and IT 491 passing at `05aeb3ad`.
- Frontend: this commit changes no frontend file and no contract. `frontend/src/shared/api/generated/model/projectRow.ts`
  already types `status: ProjectStatus`.
