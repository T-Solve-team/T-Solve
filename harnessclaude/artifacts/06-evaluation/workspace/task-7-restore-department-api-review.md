---
module: workspace
feature: restore-department-api
task: 7
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 7: Restore Department API (restore-department-api, KAN-123)

Scope: KAN-122 commits `cf849847` (API), `d2b78999` (screen), `beee1623` (IT fix) and KAN-123 commits `778c7767` (API), `9a59a55a` (screen), reviewed on `feat/KAN-123-workspace-department-restore` `0603ae45`, after merging `origin/main` `8cfeec0e` into KAN-122 (`aab8b75e`) and KAN-122 into KAN-123.
Contract: `harnessclaude/artifacts/03-modules/workspace/spec.md` STORY-007 and the KAN-122/KAN-123 interface contracts (2026-11-14).

## Summary
`POST /api/v1/departments/{id}/restore` locks the Deleted row `FOR UPDATE`, sets it `ACTIVE`, sets the Space `ACTIVE` (0 rows throws → rollback), leaves Projects and the default Project as they are, writes one `RESTORE` audit entry and answers 200 `DepartmentRef`. Meets the spec.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `DepartmentServiceImpl.restore`, `DepartmentRepository.markActive`. |
| Completeness | pass | AC-007.1 to AC-007.12: non-UUID restore path has its own 404 route, Active/unknown → 404, concurrency via `FOR UPDATE`, 401/403/500 as for delete, earlier audit rows untouched (append-only). |
| Correctness | pass | `DepartmentRestoreIT` and unit tests pass in CI. |
| Integration | pass | `restoreDepartment` in the contract matches; the name stays reserved while Deleted, so a restore cannot collide with another Department's name. |

## Traceability
```
trace workspace (STORY-007): all acceptance criteria have a test
```
(STORY-005 17/17, STORY-006 8/8, STORY-007 12/12, STORY-008 9/9, STORY-002 14/14.)

## Issues
None.

Process note: there is no implementation session log for Tasks 5 to 8 in `artifacts/05-implementation/workspace/` (only Tasks 1 to 4); the review relied on the commits and progress.json.

## Test Report
See `task-7-restore-department-api-test-report.md`.
