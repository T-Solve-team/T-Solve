---
module: workspace
feature: create-department-screen
task: 2
verdict: pass
attempt: 1
date: 2026-11-11
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 2: create-department-screen

## Summary
Pass. Change reviewed: the uncommitted working tree against `b09e8505`, frontend only. That is `features/workspace/pages/WorkspaceSettingsPage.tsx`, `components/NewDepartmentModal.tsx`, `hooks/useDepartmentMutations.ts`, `mocks/handlers.ts` and `pages/WorkspaceSettingsPage.test.tsx`. All 14 STORY-002 criteria are met and each has a named test that passes. Every verify check passes: format, lint, typecheck, 1509/1509 tests and build, and `generate:api` leaves no diff. The trace shows 14/14. Nothing in `shared/` or `app/` was edited. The page and popup follow the reference implementation (`RejectTicketModal`, `AuditLogPage`) and use the kit's `FormModal`, `FormField`, `DataTable`, `LoadingState`, the `rules` builders and the MSG catalog. I found no CRITICAL or blocking issue. There are six minor findings below. Two of them come from decision D22, which was logged on purpose.

The feature is `in-progress` in `progress.json`, not `evaluating` (evaluate step 3). As instructed, I did not change it.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.1 to 002.14 hold. Button, Name-only table labelled "Departments" from `useGetUserDepartments`, `LoadingState` while pending, `ErrorState` with "Try again" and the button kept, MSG04 when empty. The popup "New Department" has a required "Department name" field and Save and Cancel. `validateFirst` puts MSG01 ahead of MSG02, so a blank name wins. The name is trimmed before sending. A ref guard plus `confirmLoading` sends one POST. On 201: refetch, MSG05, close. A field error goes below the field through `applyApiErrors`, and any other code becomes a message by code. `destroyOnHidden` and `clearOnDestroy` reopen the popup empty. The route guard sets roles `['ADMIN']`. Cancel and Close are ignored during a save (antd `confirmLoading` plus the component guard) |
| Completeness | pass | Every criterion is implemented. Out-of-scope items (other columns, Show deleted, company information) are correctly absent |
| Correctness | pass | 18/18 workspace tests and 1509/1509 overall. A mutation probe on AC-002.14 confirmed the test proves what the user sees (test report) |
| Integration | pass | Uses the generated `useCreateDepartment` and `getGetUserDepartmentsQueryKey`, the same query as `CreateUserForm` and `UserRolesForm`. So the Add User form gets the new Department (spec Requirement 6, AC-002.9). The request and response match `CreateDepartmentRequest` and `DepartmentRef`. Server field errors (MSG01, MSG02, MSG43 on `name`) land under the field because the form field is named `name` |

## Traceability

```
  ✓ AC-002.1  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.2  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.3  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.4  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.5  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.6  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.7  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.8  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.9  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.10  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.11  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.12  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.13  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-002.14  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
trace workspace (STORY-002): 14/14 acceptance criteria have a test
```

Design Edge Cases & Risks for STORY-002 (rows 22 to 34): rows 22 to 31 and 33 are covered by the tests above. Row 32 (many Departments) and row 34 (browser mock mode) have no test task, and the code matches what they expect.

## Issues

None blocking. Minor (non-blocking):

1. **MINOR: the counter and the length rule count characters differently.** `frontend/src/features/workspace/components/NewDepartmentModal.tsx:54,56`. `rules.maxLength` uses async-validator, which counts a surrogate pair as one character (`@rc-component/async-validator/lib/rule/range.js:37`). The `count` counter counts `value.length` in UTF-16 units (`@rc-component/input/es/hooks/useCount.js:30`), which is what the server counts (Java `char`). For 26 emoji the counter shows 52/50, but Save passes the browser check. The server then answers 400 MSG02, shown under the field as "name must not exceed 50 characters." Nothing wrong is stored. *This is decision D22, logged on purpose* ("the sides differ only for … emoji, and a server refusal still shows below the field"). Suggested fix, only if the FE Lead wants the two to agree: give `count` a `strategy` that counts the same way as the rule, or use a validator on `value.length`. A change in `shared/forms` needs the FE Lead.
2. **MINOR: the browser and the server use different whitespace sets.** `NewDepartmentModal.tsx:26` (`trim()`) and `:54` (`whitespace: true`, JS `\s`), and `mocks/handlers.ts:20,23`. These differ from the server's `strip()` and `Character.isWhitespace` (D22, D27) for rare characters such as NBSP. *Decision D22, logged on purpose.* The server still decides, and its error lands under the field. No change needed for this story.
3. **MINOR: MSG05 and the close wait for the list refetch.** `frontend/src/features/workspace/hooks/useDepartmentMutations.ts:10-13`. The hook-level `onSuccess` awaits `invalidateQueries` before `showMessage('MSG05')`, and the popup closes only after both (TanStack runs the `mutate`-level `onSuccess` after the hook's promise resolves). If `GET /users/departments` stalls after a 201, the Department is already created but Save keeps spinning and Cancel and Close are ignored (AC-002.14). Returning the invalidation promise is the guideline 06 §3 pattern, so the wait itself is by the book. But that example shows the message first. Suggested fix: call `showMessage('MSG05')` before `return client.invalidateQueries(...)`, as in guideline 06 §3.
4. **MINOR: the browser mock accepts duplicates of the Departments it already lists.** `frontend/src/features/workspace/mocks/handlers.ts:6,24`. The 409 MSG43 check looks only at Departments created in this session. So "IT", "hr" and other names from the users mock's static list return 201 in browser mock mode, which differs from the contract (409 MSG43). Interim limit 4 covers the table not refreshing, not this. Suggested fix: also refuse the names `GET /api/v1/users/departments` serves. Take them from the users mock through its feature `index.ts`, or from a seeded copy in this feature's mock, and reset them in `resetMockData`.
5. **MINOR: a 401 shows MSG07 twice.** `NewDepartmentModal.tsx:33`. With `handlesOwnErrors`, a 401 still goes to the global `onUnauthorized`, which opens the MSG07 dialog (`shared/api/query-client.ts:44-46`). The popup's `onError` then also shows an MSG07 toast. This comes from `RejectTicketModal`, the reference implementation, so it affects every popup built this way. Suggested fix, at the shared level and by the FE Lead: `showMessage` or `applyApiErrors` should skip `status === 401`. Not for this story.
6. **MINOR: test gaps (not ACs).**
   - No test runs this feature's default POST mock handler (`mocks/handlers.ts:15-33`), because every POST test overrides it. Guideline 10 asks mocks to mirror the contract. A small `mocks/handlers.test.ts` with MSG01, MSG02 and MSG43 cases would cover it.
   - The browser rule that a blank name wins over length (AC-002.7 says "not only whitespace", e.g. 51 spaces → MSG01 only) is untested in the browser. The code is right (`FormField` has `validateFirst`).
   - AC-002.11 is tested with 403 and 500 only. A 400 with `fieldErrors` from the server (it goes below the field) is not tested.

Informational, no action: the component's own Cancel guard (`NewDepartmentModal.tsx:47-49`) is not needed, because antd ignores Cancel while `confirmLoading` is set (test report, mutation probe). It matches `RejectTicketModal` and does no harm.

### Security pass
No secrets or tokens. No `dangerouslySetInnerHTML` or raw HTML. Names render through `cells.text` and antd text nodes. No new dependency. No Workspace, tenant or site value is sent (the request body is `{ name }` only). The route requires `ADMIN` (`routes.ts:14-18`), and AC-002.13 shows the no-permission page with no button. The backend re-checks with `@PreAuthorize` (task 1). No finding.

### Codex second opinion (read-only), verified
- Accepted: #4 (mock duplicates), recorded as issue 4.
- Accepted with lower severity: #1 (emoji length) and #2 (whitespace set). Codex rated them MAJOR. They are MINOR issues 1 and 2, because D22 deliberately accepts the difference and the server refusal shows under the field. #3 (popup locked by a stalled refetch) was also rated MAJOR and is MINOR issue 3: it is the guideline 06 §3 pattern, it needs a stalled GET after a 201, and no data is lost.
- Rejected: #5 ("the AC-002.14 test does not prove Close is ignored; a probe showed Close accepted"). My mutation probe disproves it. With the component guard removed, Cancel and Close mid-save still do not start the close animation, and a test expecting the close fails for both buttons. The cause is antd 6 `Modal.handleCancel`, which returns early while `confirmLoading` is set (`antd/es/modal/Modal.js:113-114`). The popup ignores both buttons, which is what AC-002.14 asks for, and the test checks that.
- Codex's coverage notes on AC-002.11 (400 and 401 untested) are in issue 6. Its "blank-over-length untested" note is also in issue 6.

### Assumptions made
- **AC-002.7 "characters" in the browser:** I took D22 ("the existing behaviour on each side") as the spec's answer for the web app. So browser counting by code point is a logged choice, not a breach of AC-002.7 (issue 1). If the user wants the browser to count Java `char`s exactly, issue 1 becomes a defect.
- **AC-002.9 order:** the spec does not fix the order of close, MSG05 and refresh, so waiting for the refetch counts as compliant (issue 3).
- **AC-002.11 "any other error":** for 401, the MSG07 dialog plus a toast meets "show the message of the returned code". The duplicate is a quality issue, not a breach (issue 5).
- **Feature status:** the feature is `in-progress`, not `evaluating`. I reviewed it as the dispatcher asked and left the status change to the main session.

## Test Report
See `task-2-create-department-screen-test-report.md`. From `frontend/` with Node v24.22.2:

| Check | Result |
|-------|--------|
| `format:check` | pass |
| `lint` | pass (1 warning, in `src/features/tickets/components/TicketListFilters.tsx:23`, outside this change) |
| `typecheck` | pass |
| `test` | 87 files, 1509 passed, 0 failed |
| `build` | pass |
| `generate:api` | no diff in `src/shared/api/generated` |
| `sdd.ts trace workspace STORY-002` | 14/14 |

No coverage run. Backend not run (frontend-only task).
