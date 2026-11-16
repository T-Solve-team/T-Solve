---
module: wiki
feature: wiki-permissions-api
task: 11
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 11: Space permissions API

Commit reviewed: `93423f09` (KAN-163). The reviewer read the code with `git show` and ran no tests (see Test Report).

## Summary
`WikiSpaceSettingsController` adds the four endpoints with `@PreAuthorize(KNOWLEDGE_MANAGER)`. `WikiPermissionServiceImpl` checks, on every call, that the caller manages the Space. It uses `AccessSql.MANAGES_SPACE`: Active user, `space_manager` row, `KNOWLEDGE_MANAGER` role, Active Space and Department. An unknown, Hidden or Deleted-Department Space answers 404 MSG04, and a non-manager gets 403 MSG08.
- **Grant:** an empty selection answers 400 MSG01 on `grantees`. Every id is checked before anything is written: Active Department, not the own Department, Active user. Any bad id answers 404 MSG04 and stores nothing. Ids are de-duplicated and sorted. Each insert uses `ON CONFLICT DO NOTHING` against the partial unique indexes, and an audit entry is written only when a row is inserted. All of this runs in one transaction.
- **Revoke:** an unknown permission, or one outside the caller's managed Space, answers 404 MSG04. A revoke that loses a race answers 404 and writes no audit.
- **Settings:** the own Department is computed (W9), not stored. Department and user lists are sorted.
- **Candidates:** the own Department, Departments already granted, inactive users and users already granted are left out.

All SQL uses bound parameters. The OpenAPI contract and the generated client are in the same commit. The audit uses `PERMISSION_CHANGE` / `WIKI_SPACE` / Space id with details `operation`, `granteeType`, `granteeId`. AC-006.10 is covered: the IT checks `listSpaces` after each grant and revoke, against the view rule that reads live rows. No critical or major findings.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.1–AC-006.10 are built as written; W9 and W30 are followed. |
| Completeness | pass | Each AC has a test named with its id. Coverage includes a mixed batch rejected by any of 5 invalid states, concurrent grants (risk row 9), audit-failure rollback, and a manager re-check in 6 states. |
| Correctness | pass (static) | The reviewer did not run the tests. |
| Integration | pass | Reuses `AccessRepository.managesSpace` and `AccessSql.ACTIVE_SPACE`, `AuditService`, and `GlobalExceptionHandler` (AccessDeniedException → 403 MSG08). |

## Traceability
`sdd.ts trace` could not run: the shared working tree is checked out on `feat/KAN-170-wiki-view-documents`, where `SpaceSettingsPage.test.tsx` does not exist, so trace fails with ENOENT. The reviewer did not switch branches. Manual trace with `git grep` at `a20ae6c2`:
```
  ✓ AC-006.1  WikiPermissionIT, WikiSpaceSettingsControllerTest, WikiPermissionServiceImplTest
  ✓ AC-006.2  WikiPermissionIT, WikiSpaceSettingsControllerTest, WikiPermissionServiceImplTest
  ✓ AC-006.3  WikiPermissionIT (incl. concurrent), WikiSpaceSettingsControllerTest, WikiPermissionServiceImplTest
  ✓ AC-006.4  WikiPermissionIT, WikiSpaceSettingsControllerTest, WikiPermissionServiceImplTest
  ✓ AC-006.5  WikiPermissionIT, WikiSpaceSettingsControllerTest, WikiPermissionServiceImplTest
  ✓ AC-006.6  WikiPermissionIT, WikiSpaceSettingsControllerTest, WikiPermissionServiceImplTest
  ✓ AC-006.7  WikiPermissionIT, WikiSpaceSettingsControllerTest, WikiPermissionServiceImplTest
  ✓ AC-006.8  WikiPermissionIT, WikiSpaceSettingsControllerTest, WikiPermissionServiceImplTest
  ✓ AC-006.9  WikiPermissionIT, WikiPermissionServiceImplTest
  ✓ AC-006.10 WikiPermissionIT
```

## Issues
Minor only (they do not fail the task):
1. `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiPermissionServiceImpl.java:59`: minor. The id lists have no size cap. Each id costs one SELECT and one INSERT in a single transaction, so a very large body holds the transaction open for a long time. This is limited to Knowledge Managers. Fix: add `@Size(max = …)` on the lists in `GrantWikiPermissionsRequest`, if the user wants a limit.
2. `backend/src/main/java/vn/tsolve/wiki/repository/WikiPermissionRepository.java:70`: minor. Permissions of a Deleted Department are still listed in settings. They grant nothing (the view rule needs `pd.status = 'ACTIVE'`), and nothing on the screen says so. Fix: filter them out or flag them, after a spec decision.

## Assumptions made
- AC-006.7 and AC-006.8 overlap on revoke. A Knowledge Manager who revokes a permission of a Space they do not manage gets 404 MSG04 (AC-006.7 "belongs to another Space"), not 403 MSG08. A caller without the role still gets 403. Accepted. Open judgment call for the user.
- AC-006.2 "that have no permission on the Space yet" is read as applying to Departments as well, so Departments already granted are not offered. Accepted.
- Omitting `departmentIds` or `userIds` (null) answers 400 MSG01 on that field, not on `grantees`. Accepted, because the contract marks both as required.
- The reviewer ran no tests and did not run `sdd.ts trace`, as instructed and as the working tree allowed. This PASS depends on `mvn verify` (unit tests, `WikiPermissionIT`, spotless) being green in the orchestrator's run.

## Test Report
See `task-11-wiki-permissions-api-test-report.md`.
