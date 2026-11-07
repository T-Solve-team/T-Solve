# Task 25: Fill solution endpoint

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-07

### Working on: preview-fill-solution
- `POST /api/v1/imports/previews/{previewId}/fill-solution` (`fillImportPreviewSolution`), PM role from the class-level `@PreAuthorize`.
- `FillSolutionRequest` with `@Valid`: `rowNumbers` `@NotEmpty` of `@Min(1)`; `@NotBlank` on symptoms, rootCause, resolutionSteps, scope, so each empty section is a MSG01 field error (AC-013.2).
- `ImportPreviewService.fillSolution`, one transaction, in this order: total length of the trimmed sections (code points, as the upload counts) over `CsvRowValidator.SOLUTION_MAX_LENGTH` → 400 MSG02 for "Solution"; `owned` (404, 403 MSG08); `lockByIdAndOwner` (waits for a running Confirm, then 404); not `WITHOUT_SOLUTION` → 409 MSG06; a row number not found → 404; an Error or Removed row → 409 MSG06; then `row.fillSolution` on every row. Duplicate numbers count once.
- `ImportPreviewRow`: the five Solution columns are updatable; `fillSolution(...)` stores the sections and sets Valid, replacing an earlier Solution (AC-013.6).
- Commit: default@9204467
- Branch: `feat/KAN-31-ticket-import-csv-screens` (team branch rule).

### Deviations from the plan
- The 10,000-character total is checked in the service (`SolutionTooLongException`, 400 MSG02), not on the request: `GlobalExceptionHandler` maps a class-level constraint to MSG06, not MSG02. It runs before any row is read, so the request still changes no row.
- `CsvRowValidator.SOLUTION_MAX_LENGTH` is now `public`, so upload and fill solution share one limit. `CsvRowValidator` was not in the task's file list.
- The first `sdd.ts feature … evaluating` run crashed in Bun before writing; the second run recorded one event.

### Verification
- `mvn verify` (JBR 21, `-Dapi.version=1.44`): 629 unit tests and 41 integration tests, 0 failures; Spotless check passes.
- New tests: `ImportPreviewServiceImplTest` +10, `ImportControllerTest` +5, `ImportConfirmIT` +2 (Solution read back from the database, AC-013.7; fill waits on the preview lock, then 404, AC-013.4 / edge case 44).
- `sdd.ts trace import-ticket-list STORY-013`: 7/7 acceptance criteria have a test.
- No review in `06-evaluation` yet.

### Blockers
none

### Next up
Task 26: Confirm of a file without Solutions (`import-type-confirm`).
