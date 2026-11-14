---
module: ticket-repository
feature: manual-entry-drafts
task: 18
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Test Report: ticket-repository — Task 18: Drafts of a manual entry

Shared run: `harnessclaude/artifacts/06-evaluation/ticket-repository/task-14-manual-entry-contract-test-report.md`.
Backend `mvn -B -Dapi.version=1.44 clean verify`: BUILD SUCCESS, 1725 unit and 555 integration tests, 0 failures,
spotless passes.

## Task 18 tests (all pass)

| Class | Tests | Notes |
|-------|-------|-------|
| `ManualDraftServiceTest` | 14 | AC-007.1 (incl. edge 25 date kept as text), AC-007.2 (MSG01, 403, MSG34, MSG72), AC-007.4 (no storage keys returned), AC-007.5 (update, add, delete removed; null keeps all, empty list removes all; at most five), AC-007.6 (kept files moved, Draft deleted; rejected or failed save leaves the Draft), AC-007.8 (another user's, missing, Jira → 404; edge 23 deleted by another tab), AC-007.9 (a kept id gone under the lock → 404, new files deleted) |
| `ImportPreviewServiceImplTest.aManualDraftIs404OnEveryPreviewEndpoint_AC_007_8` | 1 | preview, rows, row, remove-rows, fill-solution (edge 24) |
| `ImportConfirmServiceImplTest.aManualDraftIs404OnConfirm_AC_007_8` | 1 | edge 24 |
| `ImportDraftServiceImplTest` (changed for the lock and MANUAL delete) | all | pass |
| `ManualEntryControllerTest` | 2 of 9 | AC-007.1 200 + MSG41; AC-007.4 reopened Draft, and a missing one is 404 |
| `ManualEntryIT` | 6 of 12 | AC-007.3 listed with the other Drafts as MANUAL with 1 row; edge 27 daily cleanup keeps a Manual Draft older than a day; AC-007.6 Draft deleted in the save transaction, and a failed save leaves it; AC-007.7 delete removes the files after commit; AC-007.9 / edge 28 a save waits on the lock held by another connection, then gets MSG04, its new file deleted and no batch |

## Not covered
- The two-requests count race of review Issue 1 (null `keepEvidenceIds`): no test.
- `POST /imports/previews/{id}/draft` with a Manual Draft id: no explicit test (review Issue 3).
