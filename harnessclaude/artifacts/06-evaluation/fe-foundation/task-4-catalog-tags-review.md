---
module: fe-foundation
feature: catalog-tags
task: 4
verdict: pass
attempt: 1
date: 2026-10-23
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: fe-foundation — Task 4: Report3 message catalog and triage status tags

## Summary
Pass. I checked `default: a5ee1a7..f67ead7` against the spec only. First I checked the reference data. I parsed
`$TSOLVE_DOCS/Report3/System_Messages.md` with my own script, and the result is the same as
`report3-messages.json`: 103 codes, with the same type, context and text for each one. The `_context` copy holds
the same text. The catalog then holds exactly the 91 web codes plus `MSG_NEW_2` (92 entries). It leaves out the 8 Jira App codes
(MSG64, 74, 76, 77, 80–83) and MSG20, MSG70, MSG58 and MSG79. Each text and type matches the JSON. I checked every
code the app uses (`msg(`, `showMessage(`, `code=`, `emptyCode=`, mock `problem(...)`, the OpenAPI and generated
comments) against the meaning of its old SRS v2 code. The remap was done in one pass, with no chaining:
16→13, 18→14, 19→15, 20→16, 21→17, 90→19, 29→21, 47→49, 53→64. No SRS v2 code is left in `src/` or `openapi/`.
Toast levels follow the design rule: `error` for MSG06, MSG33 and MSG69 (the action failed), `success` for the
others. No toast is a plain notice, so no code has `info`. `generate:api` leaves no diff. The `banner` type is gone.
The SIMPLE and COMPLEX tags are added next to the six GBR-07 statuses. The BUG-005 fix follows the bug row's
steps. From reading the code, its test would fail without the fix (I did not run it on the old code). All checks pass (352/352 tests). The trace is 7/7 for STORY-006 and 3/3
for STORY-007. I found only minor points.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.1/.2/.7: the catalog keys are exactly the Report3 codes minus the excluded ones, plus `MSG_NEW_2`. Each text equals the JSON, and each type follows the AC-006.2 mapping. The test is generated from the JSON, so if Report3 changes one character, the test fails (edge case 14). AC-006.3: the `MSG_NEW_2` text and type are exact. AC-006.4: `msg()` puts `TRACKER_NAME` ('Jira') first in the values and then lets the caller's params override it. AC-006.5/.6: placeholders are replaced, and a missing value keeps `{name}`. AC-007.1/.2: `SIMPLE` → "Simple" and `COMPLEX` → "Complex" sit next to the six ticket statuses. AC-007.3: `StatusTagStatus` is a closed key union, and an `expectTypeOf` check rejects an unknown value. `tsc -b` checks it, because `tsconfig.app.json` includes `src` and the test files. |
| Completeness | pass | 10/10 criteria have a named test, and none is MISSING. Every file of the Task 4 list is touched. Edge case 15 (unknown backend code → MSG06) still holds: `isMsgCode` uses `Object.hasOwn`, and `http.test.ts` tests MSG999. Edge case 19: the review screens use the Report3 codes, and their tests pass. |
| Correctness | pass | format:check, lint, typecheck, test (352/352, 21 files) and build all pass on the first run, with no `findBy` timeout. `report3-messages.json` matches the source exactly. I parsed it on my own and found 0 differences. `report3-messages.json` is not in the production bundle, because only the test imports it with `?raw`. |
| Integration | pass | `MsgCode` is still `keyof typeof MESSAGES`, so the type checker would reject any removed code in a caller. `show.ts` reads `level` the same way as before. `ConfirmDialog`, `EmptyState` and `DataTable` are unchanged apart from comments. The OpenAPI descriptions were changed in `tsolve-api.yaml`, and the generated client matches what `generate:api` produces (no diff). BUG-005: `RequireWorkspace` lower-cases the path before it compares it, and the new test opens `/Profile` as an Admin while the Workspace is not set up and gets My Profile. Before the fix, this path redirected to `/workspace-setup`. The bug row has Status `Fixed`, Jira `KAN-62` and Test (the exact test name). "Fixed in" is left empty until the PR has a number, as the rules at the top of `docs/bug-log.md` allow. |

## Traceability
```
  ✓ AC-006.1  frontend/src/shared/messages/catalog.test.ts
  ✓ AC-006.2  frontend/src/shared/messages/catalog.test.ts
  ✓ AC-006.3  frontend/src/shared/messages/catalog.test.ts
  ✓ AC-006.4  frontend/src/shared/messages/msg.test.ts
  ✓ AC-006.5  frontend/src/shared/messages/msg.test.ts
  ✓ AC-006.6  frontend/src/shared/messages/msg.test.ts
  ✓ AC-006.7  frontend/src/shared/messages/catalog.test.ts
trace fe-foundation (STORY-006): 7/7 acceptance criteria have a test
  ✓ AC-007.1  frontend/src/shared/ui/StatusTag/StatusTag.test.tsx
  ✓ AC-007.2  frontend/src/shared/ui/StatusTag/StatusTag.test.tsx
  ✓ AC-007.3  frontend/src/shared/ui/StatusTag/StatusTag.test.tsx
trace fe-foundation (STORY-007): 3/3 acceptance criteria have a test
```
BUG-005 (STORY-003): `AC-003.2 treats a path in other letter case as the same screen (BUG-005)` in
`frontend/src/app/router/guards.test.tsx`. STORY-003 trace is 4/4.

## Issues
None blocking. Minor (optional):
1. `frontend/src/shared/messages/catalog.ts:27,88,186` and `catalog.test.ts` (Minor): no test checks the toast
   level rule of the design (failed → `error`, succeeded → `success`). If a level were wrong, all tests would still
   pass. Today all 21 levels are right. Suggested fix: a test that lists the three `error` codes and expects
   `success` for every other toast code.
2. `frontend/src/shared/messages/msg.ts:20` (Nit): `name in values` also matches inherited names. So a
   `{constructor}` or `{toString}` placeholder would be filled with a function. No Report3 text has such a
   placeholder today. Suggested fix: `Object.hasOwn(values, name)`, like `isMsgCode` already uses.
3. `frontend/src/features/review/hooks/useReviewDecisions.ts:53` (Nit): `showMessage('MSG17', { count })` passes
   `count`, but MSG17 has no `{count}`. It is harmless (the code did the same with the old MSG21). Suggested fix:
   drop the parameter.
4. `frontend/src/shared/ui/StatusTag/StatusTag.test.tsx:6` (Nit): the test name says "the 17 statuses of the Figma
   kit", but Simple and Complex are not in the kit yet (their colours are placeholders). Suggested fix: say "17
   statuses (the Figma kit plus the triage classes)".
5. `frontend/scripts/report3-messages.mjs:9` against design.md key decision "Catalog test source" (Nit): the
   script reads `$TSOLVE_DOCS/Report3/System_Messages.md`, but the design names
   `$TSOLVE_DOCS/_context/Report3/System_Messages.md.md`. Today both files have the same content (only the line
   endings and a source comment differ). Suggested fix: change the design text to the path the script uses.

## Assumptions made
- `frontend/AGENTS.md` and `frontend/guidelines/*.md` still name SRS v2 codes, for example
  `showMessage('MSG18', …)` in AGENTS.md rule 5 (MSG18 is now a tooltip) and MSG16 as the Review Queue empty text.
  I did not count this as a finding, because tasks.md puts the guidelines in Task 5. Until Task 5 lands, the docs
  point to the wrong codes. Judgment call for the user: confirm that this is fine for the time between the tasks.
- The commit holds the BUG-005 fix together with the feature. The project rule says "never several features or
  unrelated changes in one commit". The dispatcher stated that the fix is in this commit, so I did not count it.
  Judgment call for the user: accept it, or ask for the fix in its own commit.
- `MSG64` (a Jira App banner) is named only in comments: `RequestChangesModal.tsx` and the `RequestChangesRequest`
  description in the OpenAPI. I took AC-006.7 ("every code the app uses") to mean codes passed to the message
  API, not code comments. MSG64 is a Report3 code with the right meaning (the comment shown to the author in Jira).
- I did not run `npm run messages:report3`, because it writes the JSON. Instead I parsed the Report3 source with my
  own script and compared the result to the JSON (0 differences). I ran `generate:api`, and it left no diff, so
  nothing was restored.

## Test Report
See `task-4-catalog-tags-test-report.md`. Results: format:check pass · lint pass · typecheck pass · test 352/352
pass (21 files, 36.2 s, first run, no timeout) · build pass · `generate:api` no diff · trace STORY-006 7/7,
STORY-007 3/3 · coverage not measured (the project has no coverage script).
