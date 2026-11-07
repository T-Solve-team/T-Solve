# Task 24: Upload a file without Solutions

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-07

### Working on: import-type-upload
- Changeset `009-ticket-import-type.sql`: `import_preview.import_type` (`VARCHAR(20) NOT NULL DEFAULT 'WITH_SOLUTION'`); `import_preview_row.status` widened to `VARCHAR(20)` for `NEEDS_SOLUTION`. Included in `db.changelog-master.xml`.
- `ImportType` holds each type's required and template columns. `CsvTicketParser.parse(in, type)` checks the type's required columns and keeps only its template columns, so a type 2 file's Solution columns stay unread (AC-012.4). `CsvRowValidator.validate(rows, existing, type)` checks the type's required cells.
- A row without errors in a `WITHOUT_SOLUTION` file starts as `NEEDS_SOLUTION`. `ImportPreviewRow.isValid()` is removed; upload counts rows by status.
- Controller: `importType` with default `WITH_SOLUTION` on the upload and the template. `ImportPreviewResponse` and the preview summary carry `importType` and `needsSolutionCount`.
- Commit: default@f6bb9fa
- Branch: `feat/KAN-31-ticket-import-csv-screens` (team branch rule, as in Task 23).

### Deviations from the plan
- `CsvColumns` is not modified: the type's columns live in `ImportType`.

### Verification
- `mvn verify` (JBR 21, `-Dapi.version=1.44`): 552 unit tests and 39 integration tests, 0 failures; Spotless check passes.
- Known gap until Task 26: Confirm imports `VALID` rows only, so `NEEDS_SOLUTION` rows are skipped without the 409 `MSG_NEW_4`.
- No review in `06-evaluation` yet.

### Blockers
none

### Next up
Task 25: fill solution endpoint (`preview-fill-solution`).
