---
module: ticket-repository
feature: manual-entry-save
task: 16
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Test Report: ticket-repository — Task 16: Save a manual entry (without Evidence)

Shared run: `harnessclaude/artifacts/06-evaluation/ticket-repository/task-14-manual-entry-contract-test-report.md`.
Backend `mvn -B -Dapi.version=1.44 clean verify`: BUILD SUCCESS, 1725 unit and 555 integration tests, 0 failures,
spotless passes.

## Task 16 tests (all pass)

| Class | Tests | Notes |
|-------|-------|-------|
| `ManualEntryValidatorTest` | 22 | AC-006.1 trim; AC-006.3 each required field and every blank field in one MSG01; AC-006.4 limits per field, Solution total, code points (edge 20); AC-006.5 future and invalid dates, today in Vietnam (edge 21); MSG01 wins over MSG02/MSG39 (edge 22) |
| `ManualEntryServiceImplTest` | 11 | AC-006.1, AC-006.3 (incl. non-JSON entry → MSG06), AC-006.8 pre-check and constraint catch, AC-006.9, AC-006.10, AC-006.11 (Failed batch; another constraint is MSG33, not MSG31; a recording failure still answers MSG33) |
| `ManualEntryControllerTest` (`@WebMvcTest`) | 9 | 201 + MSG32, 403 without the PM role, 400 field mapping, 409 MSG31, missing `entry` part is 400 |
| `ManualEntryIT` (PostgreSQL) | 4 of 12 | AC-006.1 rows in one transaction; AC-006.10 event after commit (`ids=1 visible=1`); AC-006.8 race through the unique index; AC-006.11 rollback with a Failed batch |
| `JiraImportServiceImplTest` (recorder rename) | all | pass |

No test failed. The frontend timeout of the shared run belongs to Task 19.
