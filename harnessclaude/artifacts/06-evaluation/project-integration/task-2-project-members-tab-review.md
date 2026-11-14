---
module: project-integration
feature: project-members-tab
task: 2
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: project-integration — Task 2: Project Detail Members tab

## Summary
The commit range `915903a..0af04d1` (frontend only) replaces the 3.2 placeholder with three parts: a read-only header, the Members tab, and the Add member popup. It uses Orval hooks generated from a hand-edited `tsolve-api.yaml` and adds MSW handlers and the audit-log label. I checked every Task 2 acceptance criterion against the code and found a named, passing test for each. The YAML contract matches `ProjectController` and its DTOs. Errors map as the spec requires: MSG08, MSG04 and MSG06 on the screen; MSG05 and MSG06 for the add. I found no critical or high issue. The three Low findings below are optional.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | **AC-001.5:** the header shows the key, Department, Project Manager and status (`StatusTag`). The table has the columns Full name / Email / Role / Added on, and Added on uses `formatDate`, which converts to `APP_TIME_ZONE`. **AC-001.6:** `screenError` maps 403 to MSG08, 404 to MSG04 and any other error to MSG06. An error from either query replaces the whole screen. **AC-001.7:** there is no Remove action. **AC-002.9:** the button is rendered only when `status === 'ACTIVE'`. **AC-002.10:** the popup sends the first request at once with `q=''`, sends the next one 300 ms after the last keystroke (`setTimeout`/`clearTimeout` keyed on `search`), and allows multi-select with `preserveSelectedRowKeys`. **AC-002.11:** `DataTable` shows MSG04 when the list is empty. **AC-002.12:** OK is disabled while nothing is selected or `isSubmitting` is true, and `handleSubmit` guards again. **AC-002.13:** on 201 the hook shows MSG05, calls `setQueryData` with the returned list, and the popup closes. **AC-002.14:** on any error the hook shows MSG06 (`handlesOwnErrors`, so no second global toast), the popup stays mounted, the selection is kept, and the candidates are reloaded. **AC-002.15:** Cancel and Close unmount the popup without sending anything, and Enter in the search box is suppressed so it cannot submit the form. **AC-002.16:** the label is added to `ACTION_LABELS`. |
| Completeness | pass | All 11 Task 2 criteria are implemented and have tests (see Traceability). The Settings tab and Remove are correctly left out (Interim limit 3, AC-001.7). |
| Correctness | pass | The orchestrator's full verify passed (format, lint, typecheck, 1551/1551 tests, build, `generate:api` with no diff). My targeted re-run of jira-integration and audit-log passed 49/49. |
| Integration | pass | **YAML vs backend:** paths `/api/v1/projects/{projectId}`, `/members` (GET and POST 201), `/member-candidates?q` match. The operationIds `getProject`, `listProjectMembers`, `searchProjectMemberCandidates` and `addProjectMembers` match `ProjectController`, and so do the status codes (400/403/404/409 where the controller declares them). The schemas match the records. `ProjectSummaryResponse.status` is ACTIVE/REMOVED (`ProjectStatus`). `ProjectMemberResponse.role` points to the existing `ProjectRole` enum, whose values PROJECT_MANAGER/MEMBER are the same as the nested `ProjectMemberResponse.Role`. `fullName` and `projectManagerName` are nullable, which matches the LEFT/nullable `full_name` columns. `userIds` has 1–50 uuids, which matches `@Size(min=1,max=50) List<@NotNull UUID>`. `AuditAction` gets `PROJECT_MEMBER_CHANGE`. The query keys used for `setQueryData` and `invalidateQueries` match the generated keys (prefix match on `[…/member-candidates]`). |

## Traceability
```
✓ AC-001.5  frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx
✓ AC-001.6  frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx
✓ AC-001.7  frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx
✓ AC-002.9  frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx
✓ AC-002.10 frontend/src/features/jira-integration/components/AddMemberModal.test.tsx
✓ AC-002.11 frontend/src/features/jira-integration/components/AddMemberModal.test.tsx
✓ AC-002.12 frontend/src/features/jira-integration/components/AddMemberModal.test.tsx
✓ AC-002.13 frontend/src/features/jira-integration/components/AddMemberModal.test.tsx
✓ AC-002.14 frontend/src/features/jira-integration/components/AddMemberModal.test.tsx
✓ AC-002.15 frontend/src/features/jira-integration/components/AddMemberModal.test.tsx
✓ AC-002.16 frontend/src/features/jira-integration/pages/AuditActionLabel.test.tsx
! ids in tests but not in the spec: AC-003.1, AC-003.8 (outside this range)
trace project-integration (STORY-001, STORY-002): 23/23 acceptance criteria have a test
```

## Issues
None of these blocks the verdict. All three are Low.

1. **Low:** `frontend/src/features/jira-integration/hooks/useAddMembersToProject.ts:20`. The hook-level `onSuccess` returns the `invalidateQueries` promise, and TanStack Query v5 waits for it before it runs the `mutate` callback (`AddMemberModal.tsx:37`, `onClose`). As a result, after a 201 the popup stays open with a spinning Add button until the candidate list has been fetched again. That list is about to be unmounted anyway. Fix: call `void refreshCandidates()` without returning it on success, or just invalidate. The invalidation should stay so the next popup does not show stale candidates.
2. **Low (guideline 10, "test sits next to its subject"):** `frontend/src/features/jira-integration/pages/AuditActionLabel.test.tsx` tests the audit-log screen's label (`features/audit-log/hooks/audit-filters.ts`) from inside the jira-integration feature. Fix: move it to `features/audit-log/` next to the existing Audit Log tests.
3. **Low (test strength):** `frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx`, test AC-001.5. It never checks that the header shows the Project Manager's name (`projectManagerName`). The string "Project Manager" it finds is the role cell or the label, and the name also appears in the table. Fix: check the header's Descriptions item, e.g. find the "Project Manager" label's value inside the header region and expect "Đỗ Minh Quân".

## Assumptions made
- **OpenAPI hand-edited, not pulled:** the design allows a hand edit when `pull-api.mjs` cannot run. The existing YAML is curated (flow style, `required` lists, shared `ProjectRole`), so I compared it with the controller and DTOs by meaning (paths, operationIds, status codes, fields, nullability, enums), not byte for byte against springdoc output. A real `npm run api:pull` against the running backend may produce cosmetic differences, for example an inline role enum instead of `$ref ProjectRole`. Judgment call for the user: accept the curated YAML, or require a real pull before the module is completed.
- **Tabs not kept in the URL:** guideline 08 says tabs live in search params (`?tab=`). The page has only one tab until KAN-127 adds Settings, so I did not count the missing `?tab=` as a defect. It should be added together with the Settings tab.
- **Cancel during an add:** Cancel and Close do nothing while the POST is running. I read AC-002.15 as covering a popup with no request in flight. Once a request is sent it cannot be taken back, so I accept ignoring Cancel/Close at that point.
- **"Try again" on 403/404:** `ErrorState` shows a retry button next to MSG08/MSG04. The spec only asks for the message, so I accept the extra button (the shared component's behaviour).
- **`q` maxLength in the YAML:** the YAML has `maxLength: 100` on the raw `q`, while the backend checks the trimmed length. The input caps raw input at 100, so a user cannot reach the 400 response. I accept this.

## Test Report
See `task-2-project-members-tab-test-report.md`. Orchestrator full verify: format/lint/typecheck/test (1551/1551)/build exit 0, no `generate:api` diff. Reviewer re-run of jira-integration and audit-log: 49/49 passed. Trace: 23/23.
