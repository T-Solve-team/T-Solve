---
module: workspace
feature: workspace-settings-screen
task: 8
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 8: Screen 2.2 Workspace Settings, company information

## Summary
Commit `default@8e62372` (frontend) builds the Company information section of 2.2: `WorkspaceInfoForm`,
`useUpdateWorkspaceInfo` and the page with loading / shared error state with retry / form, plus a TEMPORARY Departments
placeholder (KAN-120, out of scope). Every STORY-006 criterion is implemented and proven by a named test. No CRITICAL
or IMPORTANT findings. Verdict: **pass**.

### Stage 1 — Spec compliance (STORY-006)

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-006.1 load `GET /api/v1/workspace`; company name and email editable, Jira site read-only text | PASS | `WorkspaceSettingsPage.tsx:9-13`, `WorkspaceInfoForm.tsx:47-66`; test `AC-006.1 loads the Workspace…` (values, enabled, site as text not an input, 1 GET) |
| 2 | AC-006.2 token field empty with the keep hint; stored token never shown | PASS | `WorkspaceInfoForm.tsx:23-26` (only two fields in `initialValues`), `:67-73` (`extra` hint, `Input.Password`); test `AC-006.2 starts the masked token field empty…` feeds a response carrying a token and asserts it is neither a value nor text |
| 3 | AC-006.3 save sends `PUT` (token only when typed), shows MSG05 | PASS | `WorkspaceInfoForm.tsx:11-16` `toRequest`, `useWorkspace.ts` `useUpdateWorkspaceInfo` (MSG05, refresh query); tests `AC-006.3 sends the edited values and a typed token…` and `AC-006.3 omits serviceAccountToken… (edge 19)` |
| 4 | AC-006.4 server field error under the field, values kept | PASS | `WorkspaceInfoForm.tsx:33-35`; test `AC-006.4 displays server %s under %s and keeps the values entered` (MSG01/02/03/156) |
| 5 | AC-006.5 Cancel restores loaded values, sends nothing | PASS | `WorkspaceInfoForm.tsx:76` `form.resetFields()`; test `AC-006.5 Cancel restores the loaded values and sends nothing` (also clears the typed token) |
| 6 | AC-006.6 load failure → shared error state with retry | PASS | `WorkspaceSettingsPage.tsx:11` `ErrorState onRetry`; test `AC-006.6 shows the shared error state when loading fails and retries` |

EXTRA: the token field is cleared after a successful Save (reasonable, keeps AC-006.2 true after a save). Service
account email is required — matches spec Out of Scope ("the service account is required (screen 2.2)").

### Stage 2 — Quality
No CRITICAL findings. Token: never placed in the form from the response, omitted from the PUT when blank or
whitespace, cleared after success, masked input. Errors: `handlesOwnErrors`, field errors to the form, other errors via
the catalog. Cancel after a save resets to the refreshed data (rc-field-form re-applies `initialValues` to the store
on each render, so `resetFields` uses the latest loaded values). Save is disabled while pending through the form's
`disabled` (not required by STORY-006).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 6 STORY-006 criteria implemented (table above) |
| Completeness | pass | No MISSING; Departments left as a placeholder, out of scope |
| Correctness | pass | Recorded verify on this commit: vitest 89 files / 1537 tests passed, typecheck/lint/format/build ok; session log notes the 10 new tests failed against the placeholder first |
| Integration | pass | Generated `useGetWorkspace` / `useUpdateWorkspace` and `getGetWorkspaceQueryKey` match `GET`/`PUT /api/v1/workspace` → `WorkspaceInfo`; `WorkspaceUpdateRequest` has no `jiraSiteUrl` and the form never sends it (AC-003.7) |

## Traceability
```
  ✓ AC-006.1  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-006.2  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-006.3  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-006.4  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-006.5  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-006.6  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
trace workspace (STORY-006): 6/6 acceptance criteria have a test
```

## Issues
No CRITICAL or IMPORTANT issues. MINOR (do not block):

1. `frontend/src/features/workspace/pages/WorkspaceSettingsPage.tsx:15-18` — the TEMPORARY Departments card renders
   `ScreenPlaceholder number="2.2" name="Workspace Settings"`, which reads "2.2 Workspace Settings — not built yet" on a
   screen that is now partly built. Fix: when KAN-120 lands this goes; until then a neutral placeholder text
   (Departments, KAN-120) would be less confusing.

## Assumptions made
- Per the dispatcher's brief (low-RAM machine) I did not run vitest/build; I relied on the orchestrator's recorded
  verify for this commit (session log `task-8-workspace-settings-screen.md`) and ran `sdd.ts trace` myself. Judgment call
  for the user: is the recorded run acceptable in place of a reviewer-run suite?
- Clearing the token field after a successful Save is accepted as consistent with AC-006.2 (not a scope addition).
- A double-click guard on 2.2 Save is not required: no STORY-006 criterion asks for it (only AC-005.7 on 2.1), and the
  form is disabled while pending.

## Test Report
See `task-8-workspace-settings-screen-test-report.md`. Recorded on commit 8e62372: format:check ok, lint 0 errors
(1 pre-existing warning in tickets), typecheck ok, `generate:api` no diff, vitest 89 files / 1537 tests passed, build ok.
Trace STORY-006 6/6 (run by the reviewer).
