---
module: fe-foundation
feature: route-map
task: 3
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: fe-foundation — Task 3: Route map with the new screens as placeholders

## Summary
Pass. Checked against the spec only, for `default: b902ab0..02e9fc3`. Every routed screen of the AC-005.1 table has its
path in `paths.ts` and a route with the table's title and roles: 27 routes, 26 screens, User Detail on two paths. I
compared all 15 `routes.ts` files to the table by hand and found no difference. `ALL_SIGNED_IN_ROLES` is gone, and
the every-user screens use `['EMPLOYEE']`. That is safe because `RequireRole` already passes on any of the user's roles
(`hasAnyRole`) and the session always includes `EMPLOYEE`. The nine popups of AC-005.2 are named in their feature's
`routes.ts` and have no path. The new features `triage`, `wiki` and `chatbot` are set up like `audit-log`:
`index.ts`, `routes.ts`, `mocks/handlers.ts` with `resetMockData`, and `pages/`. They are registered in
`feature-routes.ts` and in `FEATURE_MOCKS` in `src/mocks/handlers.ts`, and the ESLint feature boundaries pick them up
automatically. The placeholders show "{number} {title} — not built yet". All five checks pass (132/132 tests), and
the trace for STORY-005 is 5/5 plus AC-002.5. I found only minor test-quality points.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-005.1: the paths and titles match the table. AC-002.5: each route lists exactly the table's roles, including the two-role screens 3.1, 5.5, 6.1, 6.2 and 9.1, and `KNOWLEDGE_MANAGER` only on 11.4 and 11.7. AC-005.3: each of the seven placeholders renders behind its guard for a permitted role. AC-005.5: an unknown path shows the 404 page (`path: '*'`). Edge case 16 (roles copied wrong) is covered: the route-table test compares every route with the spec table. |
| Completeness | pass | 5/5 STORY-005 criteria and AC-002.5 have tests; none MISSING. 26 routed screens plus 9 popups make the 35 web screens of functional requirement 3. NFR 3 holds: no new dependency (`package.json` is unchanged). The Task 2 items (menu entries for Wiki, Knowledge Chatbot and Triage History, landing order, AC-002.1–AC-002.4) are planned for later and are not counted here. |
| Correctness | pass | format:check, lint, typecheck, test (132/132) and build all pass on the first run, with no flaky `findBy`. The AC-005.1 test is exact: `arrayContaining` plus an equal length, and the table has no duplicate rows. Roles are compared after sorting, so a difference in order is not reported as a failure, which is correct because the spec gives no order. |
| Integration | pass | `@/shared/routing` now exports `paths` for every screen of AC-005.1 (the interface contract). It no longer exports `ALL_SIGNED_IN_ROLES`, which nothing else in `src/` uses. `buildRoutes` is unchanged and wraps every new route in `RequireAuth` → `AppShell` → `RequireRole`. The review feature on `main` is untouched, and its tests pass (NFR 5). |

## Traceability
```
  ✓ AC-005.1  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.2  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.3  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.4  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.5  frontend/src/app/router/routes.test.tsx
trace fe-foundation (STORY-005): 5/5 acceptance criteria have a test

  ✓ AC-002.5  frontend/src/app/router/route-table.test.tsx
  (AC-002.1–AC-002.4: Task 2, guards-menus — not part of this task)
```

## Issues
None blocking. Minor (optional):
1. `frontend/src/app/router/route-table.test.tsx:113-115` (Minor): the AC-005.4 test only checks that the raw text of
   `src/mocks/handlers.ts` contains the import path. It does not check that the feature's handlers are actually in
   `FEATURE_MOCKS` or `handlers`. Today ESLint would flag an unused namespace import, but the check is still
   indirect. Suggested fix: export `FEATURE_MOCKS`, or compare the feature's `handlers` and `resetMockData` by
   reference.
2. `frontend/src/app/router/route-table.test.tsx:106` (Nit): the screen number goes into `new RegExp` unescaped, so
   the `.` in `2.1` matches any character. It is harmless today. Suggested fix: escape the number, or match the full
   string.
3. `frontend/src/shared/ui/ScreenPlaceholder/ScreenPlaceholder.tsx:19` (Nit, not in this diff): the placeholder text
   still says "SRS III.{number}", but the new screens (10.1–12.1) come from Report3 and have no SRS III section.
   Consider fixing the wording when the guidelines are updated (Task 5).

## Assumptions made
- AC-005.5 "a user opens a path that no screen has": I took this to mean a signed-in user. A signed-out user who opens
  an unknown path is sent to Login (AC-002.3) and does not see 404. This behaviour existed before this task and was
  not changed by it. Judgment call for the user: confirm that this is intended.
- AC-002.5 is judged as the declared route roles matching the table, together with the `RequireRole` guard that is
  already in place. Behaviour tests per role (403 for the other roles) belong to AC-002.1 and AC-002.2 in Task 2.
- The dispatcher said Task 2 runs after Task 3, so I did not count the missing Wiki, Knowledge Chatbot and Triage
  History menu entries, or the current landing logic, as findings. Until Task 2 lands, those three screens can be
  reached only by URL.
- `generate:api` was not run, because it writes files. The OpenAPI file and the generated client are unchanged in
  this range.

## Test Report
See `task-3-route-map-test-report.md`. Results: format:check pass · lint pass · typecheck pass · test 132/132 pass
(18 files, 38.4 s, first run with no timeout) · build pass · trace STORY-005 5/5 · coverage not measured (the project
has no coverage script).
