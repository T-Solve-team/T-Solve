# Task 26: Confirm of a file without Solutions

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-07

### Working on: import-type-confirm
- `RowsNotReadyException` (`MessageCodeException`, 409 `MSG_NEW_4`, `count` = Needs Solution rows + Error rows). `GlobalExceptionHandler` already renders every `MessageCodeException`, so the handler is unchanged.
- `ImportConfirmServiceImpl.requireRowsReady` runs only for `WITHOUT_SOLUTION` previews, twice: in `confirm` before the MSG30 check (a file with only Needs Solution rows answers `MSG_NEW_4`, edge case 47), and in `importRows` under the preview lock (edge case 48). `confirm` rethrows `RowsNotReadyException` with `NotFoundException` and `NothingToImportException`, so no Failed Import Batch is recorded.
- Every row Removed: no row is not ready, so MSG30 still answers (edge case 49). `toTicket` unchanged.
- Commit: default@a1093d8
- Branch: `feat/KAN-31-ticket-import-csv-screens` (team branch rule).

### Deviations from the plan
- No change to `GlobalExceptionHandler` (see above).
- The tests were written before the code, but not run on their own first to watch them fail.

### Verification
- `mvn verify` (JBR 21, `-Dapi.version=1.44`): 635 unit tests and 43 integration tests, 0 failures; Spotless check passes.
- New tests: `ImportConfirmServiceImplTest` +6 (AC-014.1 ×4, AC-014.2, AC-016.1); `ImportConfirmIT` +2 (a Needs Solution row imports nothing and records no batch; filled rows become Published Tickets with the written Solution, APPROVE decisions and the after-commit event).
- `sdd.ts trace import-ticket-list STORY-014 STORY-016`: 3/3 acceptance criteria have a test.
- No review in `06-evaluation` yet.

### Blockers
none

### Next up
Task 27: import type on the CSV tab (`import-type-form`, frontend).
