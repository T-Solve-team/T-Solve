---
module: import-ticket-list
feature: preview-read
task: 16
verdict: pass
attempt: 1
date: 2026-11-03
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 16: Read an existing preview

## Summary
Commit `6bb02d7` (repo `default`, range `6bb02d7^..6bb02d7`) adds `GET /api/v1/imports/previews/{previewId}` (`getImportPreview`). The endpoint calls `ImportPreviewService.preview(user, previewId)`, which is `summary(owned(user, previewId))` (`ImportPreviewServiceImpl.java:45-49`). `owned()` looks the preview up by id and owner first, so another user's preview or a deleted one gives 404 without revealing whether the id exists. It then re-checks `managesActive` and gives 403 MSG08. `summary()` (`:93-102`) is the same builder that `removeRows` used before (counts, MSG30 through `ImportPreviewResponse.of`, first page of 50), and `removeRows` now calls it too. The endpoint inherits the controller's class-level `@PreAuthorize` (PM role). The contract and the generated client (`useGetImportPreview`) are in place, and `generate:api` leaves no diff. All three STORY-007 criteria have passing tests. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-007.1: the Project, file name, the three counts, MSG30 when there are Error rows, and the first page of size `FIRST_PAGE_SIZE` (the same as the upload, AC-001.1). AC-007.2: 404 from `owned()` (`:85-86`). A confirmed preview is deleted on Confirm (AC-004.4), so it also gives 404. AC-007.3: 403 MSG08 from `:87-89`, raised before any row is read. |
| Completeness | pass | Service, controller, contract and generated client are all present. The read runs in `@Transactional(readOnly = true)` and makes no extra query beyond the three counts and one page, the same as remove-rows. Nothing EXTRA. |
| Correctness | pass | `ImportPreviewServiceImplTest` 12/12 (3 new) and `ImportControllerTest` 13/13 (1 new, plus the role check extended to the new path). `mvn verify` BUILD SUCCESS (233 unit tests, 10 ITs, 0 failed). `spotless:check` passes. |
| Integration | pass | `tsolve-api.yaml` lists `/api/v1/imports/previews/{previewId}` with 200 / 401 / 403 / 404 and the response type `ImportPreviewResponse`. The Import Preview screen (tasks 20–22) uses the generated hook, and the frontend suite passes 1074/1074. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-007` → `0/3 acceptance criteria have a test`. This is the known gap for JUnit `_AC_XXX_Y` names. Manual trace:

| AC | Tests | Status |
|---|---|---|
| AC-007.1 | ImportPreviewServiceImplTest.readsTheCallersPreviewWithCountsMessageAndFirstPage_AC_007_1 (:184); ImportControllerTest.readsThePreviewOfTheCaller_AC_007_1 (:110) | PASS |
| AC-007.2 | ImportPreviewServiceImplTest.rejectsReadingAnotherUsersOrAGonePreview_AC_007_2 (:208): another user and a missing id both give `NotFoundException`, and `verifyNoInteractions(rows)` holds | PASS |
| AC-007.3 | ImportPreviewServiceImplTest.rejectsReadingWhenTheCallerNoLongerManagesTheActiveProject_AC_007_3 (:215): 403, MSG08, no row read | PASS |
| Role (NFR 3) | ImportControllerTest.everyImportEndpointNeedsProjectManagerRole_AC_001_10 (now includes `GET /previews/42`) | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `ImportPreviewServiceImplTest.java:215`: AC-007.3 has two conditions ("no longer manages" or "Project no longer Active"). The test stubs `managesActive` to false, so the inactive case is covered only through the `ProjectAccess` contract and `StubProjectAccessTest.inactiveProjectIsNeitherActiveNorManaged`. Task 14 had the same gap (its issue 1). Fix (optional): add a case using a real `StubProjectAccess` with the Project listed as inactive.
2. [Low] `ImportControllerTest.java:110` checks only the 200 path at HTTP level. The 404 and 403 bodies come from the shared `GlobalExceptionHandler` mapping, which other tests cover. No change needed.

## Assumptions made
- AC-007.2 "has been confirmed": Confirm deletes the preview (AC-004.4, tested in task 9), so the "no longer exists" test also covers a confirmed preview. No separate confirmed state exists to test.
- AC-007.1 "as in AC-001.1" means the same first page (size 50, rows ordered by row number) as the upload response. The test checks page 0 with size `CsvImportServiceImpl.FIRST_PAGE_SIZE`.
- The `sdd.ts trace` result (0/3) is a tool limitation. I used the manual trace instead.

## Test Report
`task-16-preview-read-test-report.md`. `mvn verify` BUILD SUCCESS at `15d346c`: 233 unit tests (0 failed, 1 skipped, pre-existing) and 10 ITs (0 failed). Spotless passes. Frontend 1074/1074, and `generate:api` leaves no diff.
