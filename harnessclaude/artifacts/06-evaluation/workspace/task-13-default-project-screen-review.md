---
module: workspace
feature: default-project-screen
task: 13
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude-reviewer (independent subagent)
iteration: 1
---

# Review: workspace — Task 13: Default Project on 2.3 Department Detail

## Summary
Reviewed `030ed7b7` (plus the form and mock parts of `5a363ceb`) against STORY-013. `DepartmentNameForm` shows the "Default Project" item ("None" when null). In edit mode it has an optional antd `Select` ("None" = `''`, then `projects` in the order received, `disabled` while the save runs) with the AC-013.4 help text as `extra`. It sends `values.defaultProjectId || null`. The edit form mounts with `initialValues.defaultProjectId = department.defaultProject?.projectId ?? ''`, so a rename that leaves the select alone sends the stored default and does not clear it. MSG157 is in the catalog with the exact text. Field errors on `defaultProjectId` land under the select through `applyApiErrors`. All workspace and shared/messages tests, typecheck and lint pass, and `generate:api` leaves no diff. No CRITICAL findings.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-013.1–013.10 checked against `DepartmentNameForm.tsx` and the catalog |
| Completeness | pass | 10/10 ACs traced; no MISSING; no EXTRA |
| Correctness | pass | workspace 88/88, shared/messages 221/221, typecheck, lint, format clean |
| Integration | pass | uses the generated `DepartmentDetail`/`UpdateDepartmentRequest`; the MSW PUT answers MSG04 on `defaultProjectId` for an id outside the Department, as the backend does |

### Stage 1 — spec compliance

| AC | Status | Evidence |
|----|--------|----------|
| AC-013.1 | PASS | `DepartmentNameForm.tsx` Descriptions item `defaultProject?.jiraProjectKey ?? 'None'`; tests "AC-013.1 shows the key…", "…None on HR" |
| AC-013.2 | PASS | `FormField defaultProjectId` after the name field, options None + projects; test checks DOM order, not required, holds SUP, options [None, OPS, SUP] |
| AC-013.3 | PASS | HR test → options [None] |
| AC-013.4 | PASS | `extra` text matches the spec exactly; test `HELP` |
| AC-013.5 | PASS | body `{name: trim, defaultProjectId: value || null}`; parameterised test null→OPS sends id, SUP→None sends null |
| AC-013.6 | PASS | closes, MSG05, shows SUP, next Edit holds SUP |
| AC-013.7 | PASS | MSG156/MSG157/MSG04 under the select (`toHaveAccessibleDescription`), name and choice kept, form open |
| AC-013.8 | PASS | Cancel after choosing OPS shows None, next Edit holds None, no PUT |
| AC-013.9 | PASS | select disabled while the PUT is held |
| AC-013.10 | PASS | `catalog.test.ts` "AC-013.10 …" checks entry and `msg('MSG157', {project_name:'SUP'})`; AC-006.7 allow-list updated |

## Traceability
```
trace workspace (STORY-013): 10/10 acceptance criteria have a test
```

## Issues
CRITICAL: none.

Informational (do not block):
1. `frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx`: no test covers "a rename of a Department that has a stored default, with the select untouched, sends that default's `projectId`". This is the no-accidental-clear path that D29 depends on (absent or null clears on the server). The code is right (the `initialValues` in `DepartmentNameForm.tsx`), and AC-013.2 proves that the select holds the value. But the PUT body is asserted only when the user picks a value (AC-013.5) or when there is no default (AC-004.8, `defaultProjectId: null`). Fix: add a case to the AC-013.5 `it.each`, such as `[SUP, <untouched>, SUP.projectId]`, or an AC-003.6/AC-004.8 variant with `itDetail('IT', SUP)`.
2. Running the dispatched file set in parallel (`vitest run src/features/workspace src/shared/messages`) on a cold cache timed out in `src/test/setup.ts:133` `beforeAll` (10 s hookTimeout) for `catalog.test.ts` and `msg.test.ts`. Rerun on its own, the same set passes (221/221). This is environmental load and does not come from this change, but CI on a slow runner could flake the same way.

## Assumptions made
- I read AC-013.7's "with its `params` filled in" for MSG04 (`params {}`) as "the plain message". The test sends `params` undefined.

## Test Report
See `task-13-default-project-screen-test-report.md`.
