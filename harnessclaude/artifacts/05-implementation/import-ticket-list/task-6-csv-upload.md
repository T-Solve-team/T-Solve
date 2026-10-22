# Task 6: Upload creates the preview

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 18:30

### Working on: csv-upload
- `CsvImportService.upload(user, projectId, file)`, implemented in `CsvImportServiceImpl` (`@Transactional`). Check order follows design B6:
  1. File name ends in `.csv`, case-insensitive; a missing name → MSG27.
  2. Size ≤ 10 MB (`MAX_IMPORT_SIZE`), otherwise `UploadTooLargeException` (413 MSG28).
  3. `ProjectAccess.managesActive`, otherwise `ProjectAccessDeniedException` (403 MSG08).
  4. Parse, then one `findExistingTicketIds(ticketIdsOf(rows))` query, then validate.
  5. `deleteByOwner(user)` (AC-001.11), save the `ImportPreview` (createdAt from the injected `Clock`), `saveAll` the rows.
  6. The response has the counts, MSG30 `{count}` when any row is Error, and the first page of 50 rows as `PagedModel`.
- A rejected file never deletes the PM's previous preview, because every check runs before `deleteByOwner`.
- New files:
  - DTOs: `MessageRef`, `ImportPreviewRowSummary`, `ImportPreviewResponse` (factory `of(...)` sets MSG30).
  - MapStruct `ImportPreviewMapper.toSummary`, with `snippet` cutting each Solution section to 200 code points (design B8, AC-002.8).
  - Exceptions: `UploadTooLargeException`, `ProjectAccessDeniedException`.
- Tests (TDD: red on compile, then green): `CsvImportServiceImplTest`, 11 tests, covering AC-001.1 (preview + counts + first page of 50), AC-001.2 (.xlsx, .csv.txt, no extension, empty name; upper-case .CSV accepted), AC-001.4, AC-001.9, AC-001.11 (delete before save; a rejected file keeps the old preview), AC-002.5 (one lookup of distinct ids), AC-002.8 (200-character snippet with emoji) and AC-002.9 (MSG30 count).
  - One test first compared `count` as an Integer while the service sends a Long. Both are the same JSON number, so the test was corrected.
- Verify: `mvn spotless:apply`, then `mvn verify` → exit 0; 51 tests, 0 failures.

- Commit: default@e1c0b4c (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Task 7 preview-rows.
