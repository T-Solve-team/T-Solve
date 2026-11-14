---
module: workspace
feature: delete-department-api
task: 5
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 5: Delete Department API (delete-department-api, KAN-122)

Scope: KAN-122 commits `cf849847` (API), `d2b78999` (screen), `beee1623` (IT fix) and KAN-123 commits `778c7767` (API), `9a59a55a` (screen), reviewed on `feat/KAN-123-workspace-department-restore` `0603ae45`, after merging `origin/main` `8cfeec0e` into KAN-122 (`aab8b75e`) and KAN-122 into KAN-123.
Contract: `harnessclaude/artifacts/03-modules/workspace/spec.md` STORY-005 and the KAN-122/KAN-123 interface contracts (2026-11-14).

## Summary
`DELETE /api/v1/departments/{id}` locks the Active row `FOR UPDATE`, marks it `DELETED` and clears the default Project, turns its `ACTIVE` Projects to `REMOVED` (`RETURNING public_id`), hides the Space (0 rows throws → rollback, 500 MSG06) and writes one `DEPARTMENT_CHANGE` audit entry with `removedProjectIds`, all in one transaction. `GET /departments` lists Active (or also Deleted) sorted by `lower(name)`; `GET /departments/{id}` adds `projectCount`. Meets the spec.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `DepartmentServiceImpl.delete`; `DepartmentRepository.lockInStatus/markDeleted/removeActiveProjects/setSpaceStatus`. Nothing is erased (AC-005.7). |
| Completeness | pass | AC-005.1 to AC-005.17: invalid ids (non-UUID route also takes DELETE), concurrent deletes (second waits on `FOR UPDATE`, then 404), 401/403 via `@PreAuthorize` on every endpoint, no Workspace → 500 before any write. Readers elsewhere already filter `status = 'ACTIVE'` (identity `UserCreationRepository`, `ScopeAccessRepository`), so AC-005.8 holds. |
| Correctness | pass | `DepartmentDeletionIT` on real PostgreSQL and the unit tests pass in CI. |
| Integration | pass | Contract matches the spec (`DepartmentDetail`, `DepartmentSummary`, `deleteDepartment` 204); no changeset, as decided (K122-D8). |

## Traceability
```
trace workspace (STORY-005): all acceptance criteria have a test
```
(STORY-005 17/17, STORY-006 8/8, STORY-007 12/12, STORY-008 9/9, STORY-002 14/14.)

## Issues
None.

Process note: there is no implementation session log for Tasks 5 to 8 in `artifacts/05-implementation/workspace/` (only Tasks 1 to 4); the review relied on the commits and progress.json.

## Test Report
See `task-5-delete-department-api-test-report.md`.
