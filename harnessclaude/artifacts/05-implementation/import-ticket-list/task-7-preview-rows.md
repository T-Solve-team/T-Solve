# Task 7: Preview rows: paging, detail, remove

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 19:00

### Working on: preview-rows
- `ImportPreviewService`, implemented in `ImportPreviewServiceImpl`:
  - `rows(user, previewId, status?, pageable)`: a `PagedModel<ImportPreviewRowSummary>` ordered by row number; the page size is capped at 100 (`MAX_PAGE_SIZE`); a status filter returns only that result (AC-002.11).
  - `row(user, previewId, rowNo)`: an `ImportPreviewRowResponse` with every section in full (AC-003.3); an unknown row number → `NotFoundException` (404 MSG04).
  - `removeRows(user, previewId, rowNumbers)`: one `findAllById` call; `remove()` applies to Valid and Error rows alike, and unknown row numbers are ignored. It returns the updated `ImportPreviewResponse`, with counts from three `count…` queries, MSG30 recomputed, and the first page of 50 (AC-003.1, design B10).
  - Every method first loads the preview with `findByIdAndOwner`. Another user's preview, or a deleted (confirmed) one, → 404, before any row is read (AC-003.2).
- New `ImportPreviewRowResponse` DTO and `ImportPreviewMapper.toResponse` (full text, no snippets).
- Tests (TDD: red on compile, then green): `ImportPreviewServiceImplTest`, 8 tests, covering AC-002.8 (paging, snippet, cap at 100), AC-002.11, AC-003.1, AC-003.2 (another user, missing preview, unknown row; no row access) and AC-003.3.
- Verify: `mvn spotless:apply`, then `mvn verify` → exit 0; 59 tests, 0 failures.

- Commit: default@3d37a00 (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Task 8 import-endpoints.
