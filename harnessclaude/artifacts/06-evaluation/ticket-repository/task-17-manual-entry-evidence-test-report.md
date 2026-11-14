---
module: ticket-repository
feature: manual-entry-evidence
task: 17
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Test Report: ticket-repository — Task 17: Evidence on a manual entry

Shared run: `harnessclaude/artifacts/06-evaluation/ticket-repository/task-14-manual-entry-contract-test-report.md`.
Backend `mvn -B -Dapi.version=1.44 clean verify`: BUILD SUCCESS, 1725 unit and 555 integration tests, 0 failures,
spotless passes.

## Task 17 tests (all pass)

| Class | Tests | Notes |
|-------|-------|-------|
| `ManualEntryEvidenceTest` | 28 | AC-006.2 store and record each file, and no files means no Evidence; AC-006.6 every D18 type in any letter case (parameterized), other types (incl. `x.pdf.exe`, no extension), name never used as the key (`../../a.png`), 10 MiB accepted and 10 MiB + 1 is MSG34; AC-006.7 five accepted, six is MSG72; AC-006.3/AC-006.9/AC-006.8 store no file; AC-006.8 race and AC-006.11 failure delete the stored files; edge 19 storage down at file 3; a failed delete still answers MSG33; no storage configured is MSG33 |
| `ManualEntryControllerTest` | 2 of 9 | `files` parts reach the service in order; a rejected file is 400 MSG34 on the `files` field |
| `ManualEntryIT` | 2 of 12 | AC-006.2 Evidence rows with storage keys; AC-006.7/edge 26 five files of 10 MB accepted (at the service, through `ManualEntryFileStorage`, a test double of `FileStorage`) |

## Not covered
- The servlet multipart limit (11 MB per file, 56 MB per request) and the 413 answer: no test (review Issues 1 and 3).
- The real `ObjectFileStorage` with this feature: the ITs use the `ManualEntryFileStorage` double. `ObjectFileStorage`
  has its own tests from KAN-96.
