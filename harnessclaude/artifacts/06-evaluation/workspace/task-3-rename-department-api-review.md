---
module: workspace
feature: rename-department-api
task: 3
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 3: rename-department-api

## Summary
The GET and PUT endpoints for `/api/v1/departments/{departmentId}` meet all 22 criteria of STORY-003. The code checks for a Workspace first, then strips the name. Next it locks the row with `FOR UPDATE`, and only after that compares the name with the current one. So a second save running at the same time sees the first save's name (AC-003.16), and a save with the same name writes nothing (AC-003.14).

A taken name is caught from the 23505 error of the `UPDATE`. The code throws a `RuntimeException` and runs no SQL after it, so the transaction Postgres has aborted is rolled back cleanly. A missing Space throws, which rolls back the Department rename. The audit details have the shape AC-003.5 requires, and the IT checks that shape with JSON paths.

An id that is not a UUID goes to a separate route, so it can never also match the UUID route. That route needs the Admin role and answers 404 MSG04. The `/api/v1/users/departments` route lives in another controller and is not affected. The generated client matches the contract.

The test reports written after the last source change show `DepartmentRenameIT` 17/17, `DepartmentControllerTest` 32/32 and `DepartmentServiceImplTest` 11/11 passing, and the trace covers 22/22. I found no CRITICAL issue. Nine informational issues follow; none of them blocks the pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-003.1–.22 pass (Stage 1 below) |
| Completeness | pass | Every criterion is implemented and has a test that names it and checks it. Nothing EXTRA: the `invalidId` route is the spec's "not a UUID" case |
| Correctness | pass | IT 17/17, unit 32 + 11 per the reports written after the last change. No CRITICAL finding. The full `mvn verify` and the frontend checks are the main session's run, which was still going |
| Integration | pass | Contract `getDepartment` and `updateDepartment` with schema `UpdateDepartmentRequest` match the spec. Orval output is consistent (`departments.ts`, `.msw.ts`, `model/updateDepartmentRequest.ts`, `index.ts`). `DepartmentService` gains `get(UUID)` and `update(UUID, UpdateDepartmentRequest)` as the spec says. `GET /api/v1/users/departments` returns the new name (AC-003.7 IT) |

### Stage 1 — spec compliance

Paths: `C` = `controller/DepartmentController.java`, `S` = `service/impl/DepartmentServiceImpl.java`, `R` = `repository/DepartmentRepository.java`, all under `backend/src/main/java/vn/tsolve/workspace/`. Tests are under `backend/src/test/java/vn/tsolve/workspace/`.

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-003.1 read | PASS | `C:51-58`, `S` `get` → `R.findActive` with `status = 'ACTIVE'`. IT `readsActiveDepartment_AC_003_1`, plus a controller test and a service test |
| 2 | AC-003.2 unknown, Deleted or not a UUID → 404 MSG04 | PASS | The not-a-UUID route `C:73-79` uses a negative lookahead, so it is disjoint from the UUID route. The controller test covers `abc`, a UUID one character short and a UUID with a trailing `x` for GET and PUT, and checks the service is never called. The IT covers an unknown id and a Deleted one for GET and PUT, and checks that no Space and no audit entry are written |
| 3 | AC-003.3 store the stripped name, 200 | PASS | `S` `update` strips the name. The IT sends `"  X New  Name "` and checks the stored name, that the inner double space is kept, and the response. The controller test sends 50 characters |
| 4 | AC-003.4 Space renamed, status and KM kept | PASS | `R.renameSpace`, in the same transaction. The IT sets the Space `HIDDEN` and gives it a `space_manager` row; after the rename both are unchanged, the `space_id` is the same and the Space has the new name |
| 5 | AC-003.5 audit shape | PASS | `S` `update`: `{operation: UPDATE, before: {name}, after: {name}, spaceId}`. The IT checks action, target type, actor id and email, and each JSON path. The service test checks the exact map |
| 6 | AC-003.6 status, default Project and Projects kept | PASS | The `UPDATE` sets only `name`. The IT checks the `default_project_id` and the `project` row |
| 7 | AC-003.7 new name in both GETs | PASS | IT `newNameIsReadBackAndListed_AC_003_7` |
| 8 | AC-003.8 blank → MSG01 | PASS | Compact constructor in `UpdateDepartmentRequest`. The controller test covers `{}`, null, `""`, spaces, 51 spaces, `　`, ` ` and 51 × ` ` |
| 9 | AC-003.9 >50 non-blank → MSG02 | PASS | Controller test with 51 × `a` checks `max_length` 50 (see issue D) |
| 10 | AC-003.10 another Active name → 409 | PASS | `R:66-75` turns 23505 into MSG43 on `name`. The IT sends the plain name and the name padded with spaces, then checks that both names are kept and no audit entry is written |
| 11 | AC-003.11 a Deleted Department's name → 409 | PASS | The index covers every status. IT |
| 12 | AC-003.12 case variant of another name → 409 | PASS | `uq_department_name_lower`. The IT sends `PHÒNG IT` in upper case (non-ASCII) |
| 13 | AC-003.13 case-only change of its own name | PASS | No pre-check, so the row never conflicts with itself. The IT checks the Department, the Space and one UPDATE audit entry |
| 14 | AC-003.14 same name → 200, no write | PASS | `S` `update` compares after `lockActive`. The IT sends the name padded with spaces and checks there is no audit entry. The service test checks that no rename call is made |
| 15 | AC-003.15 two Departments to one name at once | PASS | The unique index makes the second request wait, then fail with 23505. The IT race checks {200, 409}, MSG43, one Department, one Space and one audit entry |
| 16 | AC-003.16 the same Department at once | PASS | `R:52-56` `FOR UPDATE` comes before the compare, and READ COMMITTED reads the row again after the wait. The IT checks the second entry's `before` equals the first entry's `after` (see issue C) |
| 17 | AC-003.17 no Space, or the audit write fails → rollback, 500 MSG06 | PASS | IT with no Space: HTTP 500 MSG06, old name kept, no audit entry. IT with a failed audit: the security context is cleared, and the Department and Space names are kept. Service and controller tests as well |
| 18 | AC-003.18 non-Admin → 403 | PASS | `@PreAuthorize` on all three routes. The controller test covers GET and a PUT with an allowed name, and checks the service is never called |
| 19 | AC-003.19 anonymous → 401 | PASS | Controller test, GET and PUT |
| 20 | AC-003.20 only the bound Workspace | PASS | The IT uses a name held only in tenant B: the rename succeeds, and nothing is written to B or to `main` |
| 21 | AC-003.21 no Workspace → 500 MSG06 | PASS | `scope.currentWorkspaceId()` runs before the repository. IT for GET and PUT; the service test checks the repository is never called |
| 22 | AC-003.22 earlier audit entries kept | PASS | IT (backed by the trigger, see issue E) |

### Stage 2 — quality (the coordinator's focus points)
- **The transaction after a 23505.** `renameDepartment` throws `BusinessRuleException`, a `RuntimeException` (it extends `CodedException`). No SQL runs after the failure, and `@Transactional` rolls back. Only `uq_department_name` and `uq_department_name_lower` can fail on `SET name`, so reading 23505 as MSG43 is correct. Other `DataIntegrityViolationException`s are thrown on and answered 500.
- **`FOR UPDATE` and the strip and compare.** `strip()` works only on the request, so it can safely run before the lock. The compare runs after the lock, as it must. A Department Deleted while the request waits fails the `status = 'ACTIVE'` re-check and gets 404.
- **Workspace isolation.** Same as create: the routed datasource, and the scope check comes first. The IT proves both databases are left as they should be.
- **The not-a-UUID route.** `(?!UUID$).+` is matched against the whole path segment, so the two routes cannot both match one id. Only GET and PUT are mapped, so POST on the collection is not affected. Paths with more segments do not match. A literal single-segment route added later still wins, because Spring prefers literal patterns. The route copies `UserDetailController.java:82-93`. See issue A for the OpenAPI side effect.
- **Audit details.** The shape is right, and the nested `Map.of` serializes to a JSON object. `spaceId` comes from `RETURNING public_id`.
- **Space rollback.** `IllegalStateException` rolls back the Department `UPDATE`. The IT proves the old name is kept.
- **403 against 400.** A non-Admin who sends an invalid body gets 400, because `@Valid` runs before method security. The spec requires 403 only for an allowed name (AC-003.18), and the design accepts this (row 37, KAN-120 D19). A non-Admin who sends a malformed id gets 403, which is correct.
- **Generated client.** The client matches the YAML. The generated `getDepartmentsMock` is not registered in `src/mocks` (only `index.msw.ts` re-exports it), so faker data does not leak into browser mock mode. Task 4 adds the hand-written mock (K121-D18).

## Traceability
```
  ✓ AC-003.1  DepartmentServiceImplTest.java, DepartmentControllerTest.java, DepartmentRenameIT.java
  ✓ AC-003.2  DepartmentServiceImplTest.java, DepartmentControllerTest.java, DepartmentRenameIT.java
  ✓ AC-003.3  DepartmentServiceImplTest.java, DepartmentControllerTest.java, DepartmentRenameIT.java
  ✓ AC-003.4  DepartmentRenameIT.java
  ✓ AC-003.5  DepartmentServiceImplTest.java, DepartmentRenameIT.java
  ✓ AC-003.6  DepartmentRenameIT.java
  ✓ AC-003.7  DepartmentRenameIT.java
  ✓ AC-003.8  DepartmentControllerTest.java
  ✓ AC-003.9  DepartmentControllerTest.java
  ✓ AC-003.10  DepartmentControllerTest.java, DepartmentRenameIT.java
  ✓ AC-003.11  DepartmentRenameIT.java
  ✓ AC-003.12  DepartmentRenameIT.java
  ✓ AC-003.13  DepartmentRenameIT.java
  ✓ AC-003.14  DepartmentServiceImplTest.java, DepartmentRenameIT.java
  ✓ AC-003.15  DepartmentRenameIT.java
  ✓ AC-003.16  DepartmentRenameIT.java
  ✓ AC-003.17  DepartmentServiceImplTest.java, DepartmentControllerTest.java, DepartmentRenameIT.java
  ✓ AC-003.18  DepartmentControllerTest.java
  ✓ AC-003.19  DepartmentControllerTest.java
  ✓ AC-003.20  DepartmentRenameIT.java
  ✓ AC-003.21  DepartmentServiceImplTest.java, DepartmentRenameIT.java
  ✓ AC-003.22  DepartmentRenameIT.java
trace workspace (STORY-003): 22/22 acceptance criteria have a test
```
(All test paths are under `backend/src/test/java/vn/tsolve/workspace/`.)

## Issues

### CRITICAL (blocks pass)
None.

### Informational (does not block)

A. `DepartmentController.java:73-79`: the `invalidId` route has no `@Hidden`.
   - springdoc will publish `GET` and `PUT /api/v1/departments/{invalidDepartmentId}` with operationId `invalidId` in `/v3/api-docs`.
   - The YAML is written by hand today, so nothing breaks now. Once `npm run api:pull` (guideline 06 §2) replaces the stub, Orval would generate a fake operation and a second path template.
   - `UserDetailController` has the same gap.
   - Fix: add `@io.swagger.v3.oas.annotations.Hidden` to the method. Consider a quick change to the users route too.

B. `DepartmentRepository.java:66-75`: two renames that swap names at the same moment can deadlock.
   - Example: A renames P to Q while B renames Q to P. Each `UPDATE` waits on the other's uncommitted index entry.
   - Postgres aborts one with 40P01, which is not a `DataIntegrityViolationException`. The loser gets 500 MSG06 where 409 MSG43 would be right.
   - Everything is rolled back, so the data stays correct. The spec does not cover a swap.
   - Fix: none needed. Optionally map `PessimisticLockingFailureException` (covers deadlock) on this `UPDATE` to MSG43, or note the limit.

C. `DepartmentRenameIT.java:465-480`: the AC-003.16 race shows that `FOR UPDATE` is needed only when the two requests actually overlap.
   - Without the lock, both requests read `Start`, and the check of `before` against `after` fails. That needs both to read before either commits. The start latch makes this likely but not certain.
   - The test was not run red.
   - Fix (optional): make the test deterministic. One way: in another thread, hold the row with `SELECT … FOR UPDATE` in a `TransactionTemplate` on `tenantA()`, start both PUTs, then release.

D. `DepartmentControllerTest.java:227`: no AC-003.9 case covers a name that is longer than 50 characters as received but non-blank, with whitespace around it.
   - Example: `" " + "a".repeat(49) + " "` must get MSG02. This is the same gap as Task 1 informational B.
   - Fix: add that case.

E. `DepartmentRenameIT.java:564-591`: the AC-003.22 test can fail only if the code rewrote an audit row. The `audit_log_entry_no_update_delete` trigger already blocks that.
   - The CREATE entry is inserted after the first rename, so "earlier" really means only the CREATE entry and the first UPDATE entry.
   - Acceptable, since the guarantee is structural.

F. `UpdateDepartmentRequest.java:7` carries over Task 1 informational A: a name made only of control characters gets MSG01, because `@NotBlank` uses `trim()`.

G. `DepartmentRenameIT.java`: `org.springframework.test.web.servlet.result.MockMvcResultMatchers` is written out in full 30 times.
   - Fix: static-import `status` and `jsonPath`, as `DepartmentControllerTest` does.

H. Process: `progress.json` has `rename-department-api` as `in-progress`, not `evaluating`. I did not change it (out of my scope). The main session moves it: `evaluating`, then `passing`.

I. Process: `DepartmentRenameIT` was not run red first. I could not run a mutation (see the test report). By reading each test, every IT case fails on the defect it names, except C (it depends on timing) and E (backed by the trigger).

## Assumptions made
- **Judgment call:** I accepted the evidence of the Surefire and Failsafe reports written after the last source change (IT 17/17 at 01:51, unit at 01:56; sources last changed 01:26) instead of a run of my own. The main session's `mvn verify` was using `backend/target/`. If the user wants an independent rerun, run `DepartmentRenameIT`, `DepartmentControllerTest` and `DepartmentServiceImplTest` once that build ends.
- **Judgment call:** a swap of two names at the same moment (B) gets 500 instead of 409. I treated this as informational, because the spec is silent and nothing is written.
- **Judgment call:** the AC-003.16 test depends on timing (C). I did not fail the task for it, because the code is right by inspection and the test is very likely to catch a missing lock.
- A non-Admin with an invalid body gets 400 (row 37, KAN-120 D19). This is consistent with AC-003.18's wording "with an allowed name".
- Uniqueness is judged on a UTF-8-locale PostgreSQL, as in Task 1. I did not measure coverage (no tool, no threshold).
- Spotless, the full backend suite and the frontend checks (including no diff after `generate:api`) are the main session's run. This pass depends on those being green.

## Test Report
`harnessclaude/artifacts/06-evaluation/workspace/task-3-rename-department-api-test-report.md`

| Check | Result |
|-------|--------|
| `DepartmentRenameIT` (Failsafe report 01:51) | 17/17 |
| `DepartmentControllerTest` (Surefire 01:56) | 32/32 |
| `DepartmentServiceImplTest` (Surefire 01:56) | 11/11 |
| Failsafe summary (01:51) | 459 completed, 0 failures, 0 errors |
| `sdd.ts trace workspace STORY-003` | 22/22 |
| Spotless, full unit suite, frontend checks | Main session (running) |
