---
module: wiki
feature: wiki-permissions-ui
task: 12
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 12: Permissions tab

Commit reviewed: `a20ae6c2` (KAN-164). The reviewer read the code with `git show` and ran no tests (see Test Report).

## Summary
`SpaceSettingsPage` now shows the Space header (name, Department, Knowledge Manager) and the Permissions tab:
- **Departments table** (Department, Granted by, Date, Remove). The first row is the computed own Department, with a lock icon and the MSG107 tooltip and no Remove button.
- **Users table** (Full name, Email, Home Department, Granted by, Date, Remove). Deactivated users show as `Typography.Text disabled`. An empty table shows the default MSG04 from `DataTable`.

**Grant access** opens `GrantAccessModal`, which has multi-selects for the AC-006.2 candidates and shows MSG04 when there are none. Save is disabled until something is selected, and double submits are guarded. Save shows MSG05 and invalidates both the settings and the candidates queries. A failed grant keeps the selection and shows the error inline.

**Remove** asks MSG106 with `grantee` and `space_name` (they match the catalog's parameters). On confirm it shows MSG05 and refreshes the tables. A failed revoke keeps the dialog open.

**AC-006.14 (flagged by the Task 10 review): now met.** A non-manager, or any 403 from the settings call, gets `<Result status="403" title="403" subTitle={msg('MSG08')} />`. This is the same markup as `app/pages/ForbiddenPage`, so it looks the same as the app's no-permission page. Features may not import `app/` (eslint boundary zone), so copying the markup is the only option that does not touch `src/app` or `src/shared`.

No `src/app` or `src/shared` edits, no banned raw antd (`Tag`/`Table`/`Modal`/`Upload`/`message`), no new `eslint-disable`, all messages from the catalog. BUG-284 (a test-only timing issue) is logged with its test. No critical or major findings.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.11–AC-006.14 are built as written. |
| Completeness | pass | Each AC has a test named with its id. The tests cover a stateful grant then revoke, cancel, double-click, failure, empty, error with retry, two non-manager roles, and a server 403 that overrides a stale Space list. One clause has no assertion (issue 1). |
| Correctness | pass (static) | The reviewer did not run the tests. |
| Integration | pass | Uses the Task 11 generated hooks through `@/shared/api`. The MSW handlers keep state and apply the view rule for granted users and Departments. |

## Traceability
`sdd.ts trace` could not run: the working tree is on another branch, and the test file is missing there (ENOENT). Manual `git grep` at `a20ae6c2`:
```
  ✓ AC-006.11  frontend/src/features/wiki/pages/SpaceSettingsPage.test.tsx
  ✓ AC-006.12  frontend/src/features/wiki/pages/SpaceSettingsPage.test.tsx
  ✓ AC-006.13  frontend/src/features/wiki/pages/SpaceSettingsPage.test.tsx
  ✓ AC-006.14  frontend/src/features/wiki/pages/SpaceSettingsPage.test.tsx
```

## Issues
Minor only (they do not fail the task):
1. `frontend/src/features/wiki/pages/SpaceSettingsPage.test.tsx:92`: minor. The fixture user has `active: false`, but no test asserts the greyed-out style, so removing `disabled={!row.active}` (`PermissionTables.tsx:75`) would still pass. Fix: assert that the row's text carries antd's disabled class or style for the inactive user.
2. `frontend/src/features/wiki/mocks/handlers.ts:415`: minor. The mock revoke answers 403 MSG08 for a permission of a Space the user does not manage, but the backend answers 404 MSG04 (AC-006.7). Fix: return `problem(404, 'MSG04')` when `managerError` fails.
3. `frontend/src/features/wiki/pages/SpaceSettingsPage.tsx:29`: minor. This copies `ForbiddenPage`'s markup, so a change to the app's 403 page will not reach this screen. Fix: move `ForbiddenPage` into `shared/ui` with the FE Lead's review, then use it here. The AC-006.14 tests (`:291`) assert only the MSG08 text, not the 403 result.
4. `frontend/src/features/wiki/components/GrantAccessModal.tsx:13`: minor. After a 404 MSG04 from a stale candidate list, the candidates are not refetched, so Save fails again with the same selection. Fix: invalidate the candidates query in `onError`.
5. `docs/bug-log.md:251`: minor. The BUG-284 row says "Task 12 (uncommitted)" in the commit column. Fix: replace it with `a20ae6c2`.

## Assumptions made
- "Show the no-permission page with MSG08" (AC-006.14) is met by rendering the same 403 `Result` as `ForbiddenPage`, with the URL unchanged. Open judgment call: the user may want a shared component instead (issue 3).
- The Space header is name + Department + Knowledge Manager; the spec gives no more detail.
- The reviewer ran no tests. This PASS depends on `npm run format:check`, `lint`, `typecheck`, `test`, `build` and a clean `generate:api` diff being green in the orchestrator's run.

## Test Report
See `task-12-wiki-permissions-ui-test-report.md`.
