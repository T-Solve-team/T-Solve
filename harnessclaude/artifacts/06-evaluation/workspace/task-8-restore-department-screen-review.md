---
module: workspace
feature: restore-department-screen
task: 8
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 8: Show deleted and Restore on 2.2 (restore-department-screen, KAN-123)

Scope: KAN-122 commits `cf849847` (API), `d2b78999` (screen), `beee1623` (IT fix) and KAN-123 commits `778c7767` (API), `9a59a55a` (screen), reviewed on `feat/KAN-123-workspace-department-restore` `0603ae45`, after merging `origin/main` `8cfeec0e` into KAN-122 (`aab8b75e`) and KAN-122 into KAN-123.
Contract: `harnessclaude/artifacts/03-modules/workspace/spec.md` STORY-008 and the reworded AC-002.1, AC-002.4, AC-002.9 and the KAN-122/KAN-123 interface contracts (2026-11-14).

## Summary
2.2 has a "Show deleted" switch (off by default) that switches `useListDepartments` to `includeDeleted=true`; Deleted rows show the plain name, a "Deleted" tag and "Restore"; the MSG51 popup sends one restore, ignores Cancel while running, closes on settle; on 200 MSG52 and `invalidateQueries()`. AC-002.1, AC-002.4 and AC-002.9 reworded to the new list endpoint are met. Meets the spec.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `WorkspaceSettingsPage`, `RestoreDepartmentDialog`, `useRestoreDepartmentAction`. |
| Completeness | pass | AC-008.1 to AC-008.9 and the reworded AC-002.1, AC-002.4, AC-002.9. |
| Correctness | pass | `DepartmentRestore.test.tsx` and `WorkspaceSettingsPage.test.tsx` pass in CI. |
| Integration | pass | Generated `useListDepartments` / `useRestoreDepartment`; MSW answers both. |

## Traceability
```
trace workspace (STORY-008): all acceptance criteria have a test
```
(STORY-005 17/17, STORY-006 8/8, STORY-007 12/12, STORY-008 9/9, STORY-002 14/14.)

## Issues
None.

Process note: there is no implementation session log for Tasks 5 to 8 in `artifacts/05-implementation/workspace/` (only Tasks 1 to 4); the review relied on the commits and progress.json.

## Test Report
See `task-8-restore-department-screen-test-report.md`.
