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
