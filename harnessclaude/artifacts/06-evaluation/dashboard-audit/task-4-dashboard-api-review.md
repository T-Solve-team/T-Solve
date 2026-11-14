---
module: dashboard-audit
feature: dashboard-api
task: 4
verdict: pass
attempt: 1
date: 2026-11-09
reviewer: harnessclaude-reviewer (independent subagent)
iteration: 1
---

# Review: dashboard-audit — Task 4: dashboard-api

## Summary

Range `72556bd..7fe616d`. The contract `getKnowledgeDashboard` / `KnowledgeDashboard` matches the spec's interface
contract field for field (period, options, five groups, nullable rates, at most 5/10 list items, 400/401/403/404).
The backend serves `GET /api/v1/dashboard` behind `@PreAuthorize(PM, ADMIN)`, parses the period (30-day VN default,
MSG39 for one date, malformed, reversed, future, more than 366 days), parses ids (MSG06, strict canonical UUID),
resolves unknown ids to 404 MSG04, scopes a PM to `app_user.department_id` read from the database (403 MSG08 for
another Department, another Department's Project, or no Department), lets the Admin pick Workspace / Department /
Project, and returns the filter options per AC-004.13 (D6 covered). Figures are a zero skeleton with every day listed,
as Task 4 intends. The service is `@Transactional(readOnly = true)`, nothing is logged, no Workspace parameter or
header is read. No blocking finding.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-004.1–004.7, 004.13–004.15 behave as written (see per-AC table). Figures for 004.8–004.12 are zero by design of this task. |
| Completeness | pass | Every AC of this task has a test naming its id (trace 10/15; the 5 missing belong to Tasks 5–6). Edge cases D1, D2, D4, D6 tested. |
| Correctness | pass | Controller reports unit tests and DashboardIT passed; reviewer could not re-run `mvn` (dispatcher constraint). Palantir dry-run and unused-import check pass; frontend typecheck passes. |
| Integration | pass | Uses be-foundation `TSolveUser`, `Roles`, `GlobalExceptionHandler` (AccessDenied → 403 MSG08, anonymous → 401 MSG07), `NotFoundException`, `MessageCodeException`. Generated client and MSW are orval output. |

### Per acceptance criterion

| AC | Verdict | Evidence |
|----|---------|----------|
| AC-004.1 | pass | `KnowledgeDashboardResponse` = contract; controller test asserts every field and null rates; Admin and PM. |
| AC-004.2 | pass | `DashboardPeriod.parse` today in `Asia/Ho_Chi_Minh`, `today-29..today`; returned in `period`; test at 18:00Z (next VN day). |
| AC-004.3 | pass | one date, regex + STRICT `uuuu-MM-dd`, `from > to`, `to > today`, `days+1 > 366` → MSG39; 366 accepted, 367 rejected. |
| AC-004.4 | pass | PM scope = `Set.of(own department pk)` from `app_user`; own `departmentId` and own Project accepted. |
| AC-004.5 | pass | other Department, other Department's Project, no Department → `AccessDeniedException` → 403 MSG08; Admin exempt (Admin+PM test). |
| AC-004.6 | pass | Admin: `(null,null)`, `({D},null)`, `(null,P)`, `({D},P)`. |
| AC-004.7 | pass | non-canonical / non-UUID → 400 MSG06 before any lookup; unknown → 404 MSG04 before the access check. |
| AC-004.13 | pass | Admin: `d.status='ACTIVE'`, Projects `p.status='ACTIVE' and d.status='ACTIVE'`; PM: own Department and all its Projects. IT. |
| AC-004.14 | pass | `@PreAuthorize`; employee 403 MSG08 and anonymous 401 MSG07 with no service call. |
| AC-004.15 | pass | read-only transaction, no logger, only four `@RequestParam`, no `@RequestHeader`; IT row counts unchanged. |

## Traceability

```
✓ AC-004.1  DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.2  DashboardServiceImplTest
✓ AC-004.3  DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.4  DashboardServiceImplTest
✓ AC-004.5  DashboardControllerTest, DashboardServiceImplTest
✓ AC-004.6  DashboardServiceImplTest
✓ AC-004.7  DashboardControllerTest, DashboardServiceImplTest, DashboardIT
✗ AC-004.8 .. AC-004.12  no test — later features dashboard-outcomes (Task 5), dashboard-reuse-gaps (Task 6)
✓ AC-004.13 DashboardIT
✓ AC-004.14 DashboardControllerTest
✓ AC-004.15 DashboardIT
trace dashboard-audit (STORY-004): 10/15 acceptance criteria have a test
```

## Issues

### Blocking

None.

### Non-blocking

1. `backend/src/main/java/vn/tsolve/analytics/service/impl/DashboardScope.java:28` — an Admin may send a
   `departmentId` and a `projectId` of another Department; the scope keeps both, so Tasks 5–6 will AND them and
   return zeros silently. The contract says `projectId` is "inside the chosen scope" but the spec gives no error for
   it. Suggested: decide with the user (keep silent zeros, or 403/404); if an error, add it to the spec first. Make
   sure Tasks 5–6 apply both filters, not only `projectId`.
2. `backend/src/main/java/vn/tsolve/analytics/repository/DashboardRepository.java:13` — `NamedParameterJdbcTemplate`
   reading `app_user`, `department` and `project` (identity / project tables) instead of a `JpaRepository` or the
   owning module's service. AGENTS.md §1/§3 prefer Spring Data and service-only cross-module use. The design records
   this choice ("cross-module read model, aggregates only") and identity's repositories already use JdbcTemplate, so
   it is accepted; noted for the team rule's owner.
3. `backend/src/main/java/vn/tsolve/analytics/controller/DashboardController.java:27` — `@Operation` has no
   `summary`; AGENTS.md §6 asks for `@Operation(operationId, summary)`. Same omission as `AuditLogController`.
   Suggested: add `summary = "Knowledge Dashboard figures for a period and scope"`.
4. `backend/src/main/java/vn/tsolve/analytics/service/impl/DashboardServiceImpl.java:29` — `ObjectProvider<Clock>`
   with a `systemUTC` fallback; the only `Clock` bean lives in `ticket/config/TicketClockConfig`. Works (the period
   re-zones to VN), but the analytics module silently depends on another module's bean. Acceptable; a shared Clock
   in `common/config` would remove the fallback and its test.
5. `backend/src/main/java/vn/tsolve/analytics/dto/KnowledgeDashboardResponse.java:50` — `TicketFeedback.helpfulRate`
   is `Double` (nullable) while the contract's `DashboardRatedTicket.helpfulRate` is required non-null. Unused in
   this task; Task 6 must never put `null` there (or use `double`).
6. `backend/src/test/java/vn/tsolve/analytics/DashboardControllerTest.java:94` — fully qualified
   `org.springframework.security.access.AccessDeniedException` inline; import it.
7. `backend/src/test/java/vn/tsolve/analytics/DashboardIT.java:140` — the AC-004.15 "no Workspace parameter or
   header" test is a reflection check of the controller signature. It passes, but a request test sending
   `?workspaceId=…` and an `X-Workspace-Id` header and asserting the same scope would test behavior. AC-004.15's "no
   question or query text logged" has nothing to log yet; Task 6 (which reads query text) should add the log check.

## Assumptions made

- **Tests not re-run by the reviewer.** The dispatcher forbade `mvn` (another process edits backend files; the
  controller runs the Docker tests). Pass relies on the controller's report that DashboardControllerTest,
  DashboardServiceImplTest and DashboardIT passed. The reviewer ran Palantir 2.39.0 directly (dry-run, clean),
  an unused-import check (clean) and `npm run typecheck` (clean). `npm run generate:api` no-diff was not run
  (it writes into the worktree).
- **PM options include inactive items.** AC-004.13 says "the user's Department and its Projects" for a PM with no
  "active" qualifier, so a PM sees their Department and its Projects even when deactivated. Taken literally.
- **Admin options exclude active Projects of a deactivated Department.** AC-004.13 says "every active Department and
  Project"; the implementation and design read this as active Projects of active Departments. Accepted.
- **Unknown id before access check.** A PM sending an unknown `departmentId` gets 404 MSG04, not 403 MSG08; a PM
  with no Department sending an unknown id also gets 404. AC-004.5 lists "another `departmentId`", which an unknown
  id is not. UUIDs are random, so the 404/403 difference reveals nothing useful.
- **"Accept no Workspace parameter or header"** read as "reads none and ignores any sent", not "rejects them".
- Admin + mismatched Department/Project (non-blocking 1) is an open judgment call for the user.
- Accepted per dispatcher: extra file `analytics/exception/InvalidDashboardFilterException.java`; zero-figure
  skeleton filled by Tasks 5–6.

## Test Report

See `task-4-dashboard-api-test-report.md`. Palantir dry-run PASS, unused imports PASS, frontend typecheck PASS,
`git diff --check` PASS, `sdd.ts check` PASS, trace 10/15 (expected). `mvn verify` and `generate:api` not re-run by
the reviewer (dispatcher constraint); controller reports the backend tests passed.
