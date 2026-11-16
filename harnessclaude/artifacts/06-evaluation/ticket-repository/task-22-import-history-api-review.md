---
module: ticket-repository
feature: import-history-api
task: 22
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 22: Import History API (KAN-134, STORY-007)

## Summary
This review covers the code at HEAD `7554caca`, worktree `KAN-134-pr257`. The feature was merged in PR #235 (`e98a9006`), and no later commit touches its files.

**Endpoint.** `GET /api/v1/imports/history` (`ImportHistoryController.java:33-50`):
- The guard is `@PreAuthorize(ImportController.PROJECT_MANAGER)`, i.e. `hasRole('PROJECT_MANAGER')`, which answers 403 MSG08.
- The answer is a `PagedModel<ImportHistoryItem>`.

**Service** (`ImportHistoryServiceImpl.java:38-64`):
- A date from after the date to → `InvalidPullRangeException.invalid()`, which answers 400 MSG39.
- Paging defaults to page 0, size 20. Page < 0 or a size outside 1–100 → 400.
- The dates become `[from 00:00, to+1 00:00)` in the zone of the injected `Clock`. That zone is `Asia/Ho_Chi_Minh` (`TicketClockConfig.java:14`), and it is the only `Clock` bean.

**Query** (`ImportBatchRepository.java:15-46`), one native page query plus a count query:
- **Scope.** `EXISTS project_membership … role_in_project = 'PROJECT_MANAGER'` for the caller. This is the real table, as D22 asks. The membership row also keeps the tenancy boundary.
- **Legacy Project ids.** A batch's project is matched by `public_id` or by the legacy hex form.
- **Performer name.** `coalesce(u.full_name, b.performed_by)` with `u.id::text = b.performed_by`, so a non-UUID value cannot fail the cast.
- **Order.** `created_at desc, id desc`, so ties page stably.

**Error code** (AC-007.4). The service returns the first `MSG\w+` match of the stored error, or null when there is none. The stored error is never returned.

**Writers of `performed_by`.** Every writer stores `Principal.getName()`, which is the `TSolveUser.id` UUID. So AC-007.3's name lookup works for every new batch.

**Contract.** In `frontend/openapi/tsolve-api.yaml` (path at line 2396, schemas at 4611-4673):
- operationId `getImportHistory`, tag `import`, the query parameters, and the 400 and 403 responses are all there.
- `ImportHistoryPage` and `ImportHistoryItem` match the spec's Interface Contracts field by field.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-007.1: scope, newest first, paging. AC-007.2: every field. AC-007.3: name, or the stored value. AC-007.4: MSG code only. AC-007.5: each filter, Vietnam calendar days with both ends included, unmanaged `projectId` → empty page. AC-007.6: 400 MSG39. AC-007.7: 403 MSG08. |
| Completeness | pass | AC-007.1–AC-007.7 each have a test. The design's edge cases 17–20 (raw performer, a Project no longer managed, a legacy hex id, 23:30 on `dateTo`) are in `ImportHistoryControllerIT`. |
| Correctness | pass | `ImportHistoryServiceImplTest` passes 15/15, and `spotless:check` is clean. `ImportHistoryControllerIT` was not run here (Testcontainers). |
| Integration | pass | The generated client (`useGetImportHistory`, the models) is used by Task 23. The `Principal`-based `performed_by` matches `app_user.id`. |

## Traceability
```
  ✓ AC-007.1  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportHistoryServiceImplTest.java, backend/src/test/java/vn/tsolve/ticket/controller/ImportHistoryControllerIT.java
  ✓ AC-007.2  backend/src/test/java/vn/tsolve/ticket/controller/ImportHistoryControllerIT.java
  ✓ AC-007.3  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportHistoryServiceImplTest.java, backend/src/test/java/vn/tsolve/ticket/controller/ImportHistoryControllerIT.java
  ✓ AC-007.4  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportHistoryServiceImplTest.java, backend/src/test/java/vn/tsolve/ticket/controller/ImportHistoryControllerIT.java
  ✓ AC-007.5  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportHistoryServiceImplTest.java, backend/src/test/java/vn/tsolve/ticket/controller/ImportHistoryControllerIT.java
  ✓ AC-007.6  backend/src/test/java/vn/tsolve/ticket/service/impl/ImportHistoryServiceImplTest.java, backend/src/test/java/vn/tsolve/ticket/controller/ImportHistoryControllerIT.java
  ✓ AC-007.7  backend/src/test/java/vn/tsolve/ticket/controller/ImportHistoryControllerIT.java
trace ticket-repository (STORY-007): 10/10 acceptance criteria have a test
```

## Issues
No critical or blocking issues. All of the following are informational:

1. `backend/src/main/java/vn/tsolve/ticket/repository/ImportBatchRepository.java:17`: the join on `p.public_id = b.project_id OR lpad(to_hex(p.project_id),32,'0')::uuid = b.project_id` cannot use an index on either side.
   - Postgres will scan `project` for each batch, or hash-join after computing the expression for every row.
   - This is fine at the current size (few Projects).
   - Fix, if the history grows: backfill the legacy hex ids to `public_id` once, then drop the `OR` branch.
2. `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportHistoryServiceImpl.java:46-48`: a bad `page` or `size` answers 400 with `ProblemDetails.UNEXPECTED_ERROR` and a field name.
   - Spec AC-007.1 gives the range but no message for a value outside it, so this is not a deviation.
   - Fix (optional): align it with whatever `GET /api/v1/tickets` answers for the same input.
3. `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportHistoryServiceImpl.java:27`: `MSG\w+` would also take trailing letters or `_` (for example `MSG50_X`).
   - The stored formats are `"<Exception> MSG50"` (`JiraImportServiceImpl.java:148`) and `"<reason>: MSG33"` (`ImportConfirmServiceImpl.java:224`), so this never happens today.
   - Fix (optional): `MSG\d+`.

## Assumptions made
- I did not run `ImportHistoryControllerIT` here, because it needs Testcontainers and the dispatcher runs the full verify. This PASS assumes it is green. Its assertions were read and match AC-007.1–007.7.
- A Project whose status is REMOVED still shows its batches when the PM is still a member with the PM role. The spec is silent on Project status, and the IT seeds such a Project (`SECOND`) as listed. I accept this.
- I did not check that `npm run generate:api` leaves no diff, because the frontend has no `node_modules` in this worktree. The dispatcher's full verify covers it.

## Test Report
See `task-22-import-history-api-test-report.md`.
