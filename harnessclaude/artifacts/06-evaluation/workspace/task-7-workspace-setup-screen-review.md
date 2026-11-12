---
module: workspace
feature: workspace-setup-screen
task: 7
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 7: Screen 2.1 Workspace Setup

## Summary
Commit `default@f802956` (frontend) replaces the 2.1 placeholder with `WorkspaceSetupForm`, adds `useSetUpWorkspace`,
stateful MSW handlers, MSG61/MSG153/MSG156 in the catalog and `report3-messages.json`, and removes the 2.1 row from
fe-foundation's placeholder route-table test (D26). Every STORY-007 criterion is implemented and proven by a test
that names it. No CRITICAL or IMPORTANT findings; four MINOR ones below. Verdict: **pass**.

### Stage 1 — Spec compliance (STORY-007)

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-007.1 Admin with `workspaceReady` false opens 2.1 with MSG60 | PASS | `WorkspaceSetupPage.tsx:9` (Alert MSG60); test `AC-007.1 opens Setup with MSG60…` renders `/users`, asserts redirect to `/workspace-setup` and MSG60 |
| 2 | AC-007.2 four required fields, limits 100/200/100, placeholder, masked token | PASS | `WorkspaceSetupForm.tsx:35-66`; tests `AC-007.2 shows four required fields…` (required, placeholder, `type=password`) and `AC-007.2 validates the %s limit with MSG02…` (3 cases, nothing sent) |
| 3 | AC-007.3 empty field → MSG01 under it, send nothing | PASS | `rules.required` on all four; test `AC-007.3 shows MSG01 for each empty or whitespace-only field without sending` asserts 4×MSG01 for empty and whitespace and 0 requests |
| 4 | AC-007.4 server field error MSG01/02/03/61/153/156 under the field, values kept | PASS | `WorkspaceSetupForm.tsx:22` `applyApiErrors`; test `AC-007.4 displays server %s under %s and keeps every value` (6 codes, accessible description, all 4 values kept, no MSG06) |
| 5 | AC-007.5 success → MSG05, refetch `/me`, open User List | PASS | `useWorkspace.ts:24-28`; test `AC-007.5 sends the form…` asserts body, MSG05, `/me` called twice, cache `workspaceReady` true, path `/users` |
| 6 | AC-007.6 409 → no error, refetch `/me`, open 2.2 | PASS | `useWorkspace.ts:17-19,29-33`, component `onError` returns early; test `AC-007.6 refetches /me and opens Settings…` asserts path, 2 `/me` calls, no MSG06/MSG05 |
| 7 | AC-007.7 Save disabled while in flight | PASS | `WorkspaceSetupForm.tsx:12-16,69-77` (ref guard + `disabled`); test `AC-007.7 disables Save while pending and sends once on a double click` (edge 17) |

EXTRA: none outside the spec. The side menu is still shown on 2.1 — removed from AC-007.1 by the recorded spec change (D24) and listed as a change request to `fe-foundation`.

### Stage 2 — Quality
No CRITICAL findings. Token handling: the token goes only to the POST body, is never stored by the mock
(`handlers.ts` keeps only the three info fields), never logged or rendered (`Input.Password`,
`autoComplete="new-password"`). Error handling: `meta.handlesOwnErrors` with field errors applied to the form and
other errors shown via the catalog; a 500 keeps the input and allows a retry (tested). Access: route roles are owned
by fe-foundation (`ADMIN`), the mock enforces 401/403.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 7 STORY-007 criteria implemented (table above) |
| Completeness | pass | No MISSING, no EXTRA; D24 removed the menu clause from the spec |
| Correctness | pass | Recorded verify on this commit: vitest 88 files / 1527 tests passed, typecheck/lint/format/build ok; no CRITICAL finding |
| Integration | pass | Uses generated `useSetupWorkspace` (`POST /api/v1/workspace/setup` → 201 `WorkspaceInfo`); backend sends field errors as `fieldErrors` (`ProblemDetails.withFieldErrors`) and the 409 as `BusinessRuleException` → `CONFLICT` with an unknown code, which `apiErrorFromResponse` maps to MSG06 — matched by `isWorkspaceAlreadySetUp` |

## Traceability
```
  ✓ AC-007.1  frontend/src/features/workspace/pages/WorkspaceSetupPage.test.tsx
  ✓ AC-007.2  frontend/src/features/workspace/pages/WorkspaceSetupPage.test.tsx
  ✓ AC-007.3  frontend/src/features/workspace/pages/WorkspaceSetupPage.test.tsx, frontend/src/features/workspace/mocks/handlers.test.ts
  ✓ AC-007.4  frontend/src/features/workspace/pages/WorkspaceSetupPage.test.tsx, frontend/src/features/workspace/mocks/handlers.test.ts
  ✓ AC-007.5  frontend/src/features/workspace/pages/WorkspaceSetupPage.test.tsx, frontend/src/features/workspace/mocks/handlers.test.ts
  ✓ AC-007.6  frontend/src/features/workspace/pages/WorkspaceSetupPage.test.tsx, frontend/src/features/workspace/mocks/handlers.test.ts
  ✓ AC-007.7  frontend/src/features/workspace/pages/WorkspaceSetupPage.test.tsx
trace workspace (STORY-007): 7/7 acceptance criteria have a test
```
Every AC is proven by the page test; the `mocks/handlers.test.ts` entries only check the mock contract (see issue 3).

## Issues
No CRITICAL or IMPORTANT issues. MINOR (do not block):

1. `frontend/src/features/workspace/pages/WorkspaceSetupPage.test.tsx:146-147` — the MSG153 case answers with status
   409, but AC-003.9 and the backend (`WorkspaceServiceImpl.java:211-212`, `FieldMessageCodeException(BAD_REQUEST, …)`)
   answer 400. The test still proves display under the field, and incidentally that a 409 with field errors is not taken
   for "already set up", but it does not exercise the real status. Fix: use 400 for MSG153 (keep a separate 409-with-field-error
   case if that guard matters).
2. `frontend/src/shared/messages/catalog.test.ts` (new `it.each` and the 143-messages test) — named `AC-007.4`, a
   workspace id, inside fe-foundation's catalog test file, where fe-foundation's own AC-005.4 means "triage, wiki and
   chatbot are registered"; a fe-foundation trace will count these as its AC-005.4. The renamed
   `AC-008.2 gives a toast the error level…` checks toast levels, which fe-foundation AC-006.2 (type mapping) does not
   state. Fix: name the catalog tests by fe-foundation `AC-006.1` (exact Report3 text); leave the toast-level test unnamed
   or tie it to the design note it checks. Needs the FE Lead review anyway (frontend/AGENTS.md rule 10).
3. `frontend/src/features/workspace/mocks/handlers.test.ts:14,31,40,49` — mock-contract tests carry AC-007.3..6 ids, so
   the trace lists them as proof of screen behaviour they do not show. Fix: drop the AC prefix or describe them as mock
   contracts.
4. `frontend/src/features/workspace/hooks/useWorkspace.ts:17-19` — the 409 is recognised by status + normalised MSG06 +
   no field errors (D25). Any future non-field 409 on Setup would also silently redirect to 2.2. Acceptable today
   (only conflict on the endpoint); the root fix is to keep the raw technical code on `ApiError` (shared/api, FE Lead).

## Assumptions made
- Per the dispatcher's brief (low-RAM machine) I did not run vitest/build; I relied on the orchestrator's recorded
  verify for this commit (session log `task-7-workspace-setup-screen.md`) and ran `sdd.ts trace` myself. Judgment call
  for the user: is the recorded run acceptable in place of a reviewer-run suite?
- The side menu still showing on 2.1 is accepted because the spec (changelog 2026-11-12, D24) removed it from AC-007.1.
- Issue 1 (MSG153 at 409 in the test fixture) is treated as MINOR, not a missing test, since the display behaviour is proven.

## Test Report
See `task-7-workspace-setup-screen-test-report.md`. Recorded on commit f802956: format:check ok, lint 0 errors
(1 pre-existing warning in tickets), typecheck ok, `generate:api` no diff, vitest 88 files / 1527 tests passed, build ok.
Trace STORY-007 7/7 (run by the reviewer).
