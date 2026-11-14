---
module: workspace
feature: delete-department-screen
task: 6
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 6: Delete on 2.3 Department Detail (delete-department-screen, KAN-122)

Scope: KAN-122 commits `cf849847` (API), `d2b78999` (screen), `beee1623` (IT fix) and KAN-123 commits `778c7767` (API), `9a59a55a` (screen), reviewed on `feat/KAN-123-workspace-department-restore` `0603ae45`, after merging `origin/main` `8cfeec0e` into KAN-122 (`aab8b75e`) and KAN-122 into KAN-123.
Contract: `harnessclaude/artifacts/03-modules/workspace/spec.md` STORY-006 and the KAN-122/KAN-123 interface contracts (2026-11-14).

## Summary
2.3 shows a danger "Delete" beside "Edit" (hidden while renaming); the MSG45 popup carries the name and `projectCount`; Delete sends one request, Cancel is ignored while it runs; on 204 MSG46, `invalidateQueries()` and navigation to 2.2; on error the popup closes and the app's handler shows the code. Meets the spec.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `DeleteDepartmentButton`, `useRemoveDepartment`. |
| Completeness | pass | AC-006.1 to AC-006.8 each covered. |
| Correctness | pass | `DepartmentDeletion.test.tsx` and `DepartmentDetailPage.test.tsx` pass in CI. |
| Integration | pass | Uses the generated `useDeleteDepartment`; MSW marks the entry Deleted. |

## Traceability
```
trace workspace (STORY-006): all acceptance criteria have a test
```
(STORY-005 17/17, STORY-006 8/8, STORY-007 12/12, STORY-008 9/9, STORY-002 14/14.)

## Issues
None.

Checked and dismissed: `useRemoveDepartment` (`hooks/useDepartmentMutations.ts`) awaits `client.invalidateQueries()` before the
component navigates, so the still-mounted 2.3 query of the deleted Department refetches once and gets 404 MSG04. A
temporary test (not kept) watched the page from the 204 until 2.2 opened: no MSG04 and no Retry appeared, because
`DepartmentDetailPage` shows ErrorState only when there is no data and TanStack keeps the earlier data on a failed
refetch. The cost is one extra GET and one extra round trip before 2.2 opens; no user-visible defect, so no bug is
logged.

Process note: there is no implementation session log for Tasks 5 to 8 in `artifacts/05-implementation/workspace/` (only Tasks 1 to 4); the review relied on the commits and progress.json.

## Test Report
See `task-6-delete-department-screen-test-report.md`.
