---
module: import-ticket-list
feature: import-type-confirm
task: 26
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 26: Confirm of a file without Solutions

## Summary
Commit `a1093d8` (repo `default`, range `a1093d8^..a1093d8`), read at HEAD `6267856`.

- **The check:** `RowsNotReadyException` is a `MessageCodeException` with 409, `MSG_NEW_4` and `count`. It goes through the existing `handleMessageCode`, so no new handler was needed. `requireRowsReady` (`ImportConfirmServiceImpl.java:129-139`) does nothing for a file with Solutions. For a file without Solutions it throws when Needs Solution + Error > 0.
- **First call:** in `confirm` (`:94`), before the MSG30 check, so a file with only Needs Solution rows answers `MSG_NEW_4` (edge case 47). This is before any transaction, so no Import Batch is recorded.
- **Second call:** inside `importRows` under the preview lock (`:174`), for edge case 48. The exception is rethrown, not turned into a Failed batch (`:111`).
- **Unchanged:** a type 2 preview whose rows are all Removed still reaches the old MSG30 path (edge case 49). `toTicket` is unchanged, so a filled row becomes a Ticket with its saved sections.

Every AC has a named test. The DB test proves the full AC-014.2 path: Published, written Solution, APPROVE decisions, event after commit, preview deleted. The clean `mvn verify` is green. I found no CRITICAL issue. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-014.1, AC-014.2 and AC-016.1 behave as written; see the Stage 1 table. |
| Completeness | pass | All 3 ACs are traced. Edge cases 47, 48 and 49 are tested. No EXTRA behaviour. |
| Correctness | pass | `mvn clean verify`: 635 unit tests and 43 integration tests passed, spotless clean, exit 0. |
| Integration | pass | It matches the contract: 409 `MSG_NEW_4` on `confirmImport` (task 23) and the spec's Rejections line ("rows not ready on a file without Solutions → 409 (`MSG_NEW_4`)"). It uses the existing `MessageCodeException` → ProblemDetail path, with `code` and `params.count`. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-014.1 409 `MSG_NEW_4` with the count, nothing imported, no batch | PASS | `:94`, `:129-139`, `:174`; unit `:379`, `:396`, `:410`, `:428`; DB `ImportConfirmIT:241` |
| 2 | AC-014.2 all Valid / Removed → imported as AC-004.1–004.4, 004.10 | PASS | unchanged `importRows`; unit `:439`; DB `ImportConfirmIT:255` |
| 3 | AC-016.1 file with Solutions still skips Error rows | PASS | early return for `WITH_SOLUTION`; unit `:451` |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-014 STORY-016` → `3/3 acceptance criteria have a test`

```
✓ AC-014.1  ImportConfirmServiceImplTest, ImportConfirmIT
✓ AC-014.2  ImportConfirmServiceImplTest, ImportConfirmIT
✓ AC-016.1  CsvImportServiceImplTest, ImportConfirmServiceImplTest, CsvTicketParserTest
```

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Medium] `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportConfirmServiceImpl.java:174`: the second check under the lock protects only against a Remove or Fill that commits before Confirm takes the lock. Fill takes the same lock, so it is fully serialised (task 25). Remove still takes no lock (`ImportPreviewServiceImpl.java:82-90`). A Remove that commits after `importRows` has read the Valid rows is still lost, and the removed row is imported. This predates this task (`task-9-csv-confirm-review.md`, attempt 2, issue 1). Edge case 48's unit test (`ImportConfirmServiceImplTest:410`) is mock-based and covers only the "changed before the lock" order. Fix: the same as task 25 issue 2, so that `removeRows` takes `lockByIdAndOwner`.
2. [Low] `ImportConfirmServiceImpl.java:94` vs `:101-106`: `MSG_NEW_4` (with its count) is answered before MSG40 (Project not Active) and 403 MSG08. A PM who no longer manages the Project, or whose Project is inactive, is told to write Solutions first, and only gets 403 / MSG40 after that. The spec orders only MSG40 before 403 (AC-004.11). The existing MSG30 check already sits in the same place, and the count is only about the caller's own preview, so nothing leaks. Fix if wanted: move both row checks after the Active / manages checks.
3. [Info] `ImportConfirmIT:255` checks tickets, decisions, the event and the deleted preview. It does not check the Completed `import_batch` row of a type 2 Confirm (AC-004.3 through AC-014.2). The code is shared with type 1, which `ImportConfirmIT:309` covers. Fix if wanted: one `select status, published_count from import_batch` assert.

## Assumptions made
- AC-014.1 "the number of those rows" = Needs Solution + Error rows, without Removed rows. This matches the `MSG_NEW_4` text ("row(s) still need a Solution or have errors").
- Edge case 49 (type 2, every row Removed → MSG30): the spec's AC-014.1 does not block it, so AC-004.5's 409 MSG30 applies. I accept the design's reading.

## Test Report
`task-26-import-type-confirm-test-report.md`: `mvn -B -Dapi.version=1.44 clean verify` exit 0. Surefire 635/635, failsafe 43/43, spotless clean. No coverage tool is configured, and the spec sets no target.
