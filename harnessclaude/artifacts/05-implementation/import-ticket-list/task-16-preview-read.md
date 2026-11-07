# Task 16: Read an existing preview

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-01

### Working on: preview-read
- D11: `ImportPreviewService.preview(user, previewId)` = `owned()` (404 for another owner or a gone preview, 403 MSG08 when the caller no longer manages the Active Project) + `summary(preview)`. `removeRows` now returns the same `summary(preview)`, so both build one response.
- `GET /api/v1/imports/previews/{previewId}` (`getImportPreview`) in the contract first, then the controller; client regenerated.
- Tests: `ImportPreviewServiceImplTest` AC_007_1 (counts, MSG30, first page of 50), AC_007_2 (other owner / missing → 404, no row query), AC_007_3 (403 MSG08, no row query); `ImportControllerTest` AC_007_1 (the path) and the role check on the new path.

### Verify
- `mvn verify` (same environment flags as Task 15): 233 unit tests + 10 IT, 0 failures, Spotless OK.
- Frontend: `format:check`, `typecheck`, `lint`, `test` (1022), `build` pass; `generate:api` is stable.
- Trace by grep (sdd trace skips `*Test.java`, FEEDBACK.md 2026-11-01): `AC_007_1` ×2, `AC_007_2`, `AC_007_3`.

### Commit
- default@6bb02d7

### Next up
Task 17 duplicate-id-code

## Session 2026-11-04

### Working on: preview-read — Low findings of review attempt 1
- Review issue 1: `rejectsReadingWhenTheProjectIsNoLongerActive_AC_007_3` uses the real `StubProjectAccess` with the Project listed as inactive, the other half of AC-007.3.
- Verify: `mvn verify` BUILD SUCCESS (235 unit tests, 1 skipped as before; 10 ITs; spotless) with JDK 24 and `-Dapi.version=1.44` for Docker Desktop 29; frontend `format:check`, `lint` (0 errors, the old `TicketListFilters.tsx` warning), `typecheck`, `test` (58 files, 1083 passed), `build` pass; `generate:api` leaves no diff.
- Commit: default@84245c1

### Next up
Module completion
