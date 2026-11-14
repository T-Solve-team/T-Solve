---
module: workspace
feature: rename-department-screen
task: 4
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 4: rename-department-screen

## Summary
Pass. I reviewed the uncommitted working tree against `006a8b78`, frontend only:
- `features/workspace/pages/WorkspaceSettingsPage.tsx` (the Name cell is now a link)
- `pages/DepartmentDetailPage.tsx`
- `components/DepartmentNameForm.tsx` (new)
- `hooks/useDepartmentMutations.ts` (`useRenameDepartment`)
- `mocks/handlers.ts` (seeded IT and HR, plus GET and PUT)
- the two page tests

All 14 STORY-004 criteria are met, and each one has a test that names it and passes. The trace shows 14/14. The 44 workspace tests pass. Nothing in `shared/`, `app/`, `routes.ts` or `src/mocks/handlers.ts` was edited. I found no CRITICAL or blocking issue. There are six minor findings below. Four of them were raised or sharpened by the Codex second opinion, and I checked each one myself.

The feature is `in-progress` in `progress.json`, not `evaluating` (evaluate step 3). As in the Task 2 review, I did not change it.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | **AC-004.1:** the 2.2 Name cell uses `cells.link(paths.departmentDetail(id), name)`.<br>**AC-004.2 to 004.4:** 2.3 reads `useGetDepartment(departmentId)`. It shows `LoadingState` while pending. On error it shows `ErrorState` (message by code, so MSG04 for a 404) with "Try again". Edit and the back link appear only when data is loaded.<br>**AC-004.5:** Edit mounts an `AppForm` with `initialValues` set to the current name and a required "Department name" field. Save and Cancel take the place of Edit.<br>**AC-004.6, 004.7:** `rules.required` and `rules.maxLength(50)` through `FormField` (`validateFirst`).<br>**AC-004.8:** the name is trimmed. One PUT goes out, guarded by the `isSubmitting` ref plus `disabled`/`loading`. Enter while pending also hits the ref.<br>**AC-004.9:** the hook shows MSG05, then returns `invalidateQueries()`. The form closes once the refetch is done, and 2.3 shows the stored name from the refreshed GET.<br>**AC-004.10, 004.11:** `applyApiErrors` puts field errors below the field. Any other code goes through `showMessage`. The typed text is kept.<br>**AC-004.12:** Cancel calls `resetFields`. With `clearOnDestroy` and `initialValues`, the next Edit shows the current name with no stale error.<br>**AC-004.13:** Cancel returns early while the ref is set.<br>**AC-004.14:** the route has `roles: ['ADMIN']` (`routes.ts:19-24`) |
| Completeness | pass | Every criterion is implemented. The out-of-scope items (Default Project, Managers tab, Delete) are correctly absent |
| Correctness | pass | 44/44 workspace tests pass (3 files). The AC-004.9 test really proves the stored name: it types `it support`, the server answers `IT Support`, and the test asserts `IT Support`. The AC-004.13 test holds the PUT open and checks that the form and text stay |
| Integration | pass | Uses the generated `useGetDepartment` and `useUpdateDepartment` (contract `getDepartment`, `updateDepartment`, `DepartmentRef`). The `name` form field matches the server's `fieldErrors[].field`, so a server MSG01, MSG02 or MSG43 shows below the field. There is no cross-feature import (guideline 01). The MSW seed copies the users mock's two ids and names as literals (D18) |

## Traceability

```
  ✓ AC-004.1  frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx
  ✓ AC-004.2  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.3  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.4  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.5  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.6  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.7  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.8  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.9  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.10  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.11  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.12  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.13  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
  ✓ AC-004.14  frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx
trace workspace (STORY-004): 14/14 acceptance criteria have a test
```

## Issues

None blocking. Minor (non-blocking):

1. **MINOR: `invalidateQueries()` with no key goes against guideline 06 §4.** `frontend/src/features/workspace/hooks/useDepartmentMutations.ts:29`. The guideline says "Never `queryClient.invalidateQueries()` with no key — it refetches the whole app". AC-004.9 ("refresh every cached query") and decision D14 ask for exactly this call, and the spec is the contract, so this is not a defect of the code. But it departs from a team rule, so the FE Lead should sign it off (record it in `guidelines/13-decision-log.md`, or narrow the spec in Change Mode). A concrete side effect, verified: it also refetches the active `/me` session query. A failed `/me` refetch puts `SessionErrorPage` in place of the whole app (`app/router/guards.tsx:15`). *(Side effect raised by Codex.)*
2. **MINOR: a 200 followed by a failed refetch shows ErrorState, not the saved name.** `pages/DepartmentDetailPage.tsx:13` together with `useDepartmentMutations.ts:27-29`. The rename's own response is never written to the cache. If the GET refetch after the PUT fails (after its one retry), `isError` is checked before `data`, so the page swaps the form for ErrorState even though MSG05 was shown and the name was saved. "Try again" recovers. A failed window-focus refetch while the form is open also drops the typed text in the same way. This is the guideline 05 page pattern, so it is not specific to this story. Suggested fix: in `onSuccess`, call `client.setQueryData(getGetDepartmentQueryKey(departmentId), data)` before invalidating, and render ErrorState only when `isError && !data`. *(Raised by Codex as MAJOR. I rate it MINOR: it needs two failed GETs after a 200, nothing is lost on the server, and recovery is one click.)*
3. **MINOR: focus is lost on Edit, Cancel and Save.** `components/DepartmentNameForm.tsx:50` and `:37-41`. The button that has focus is removed (Edit becomes the form, and the form becomes Edit again), and focus is not moved, so it drops to `<body>` for keyboard and screen-reader users. Suggested fix: autofocus the input when the form opens (`<Input autoFocus …>`), and put focus back on Edit when the form closes. *(Raised by Codex. I confirmed there is no focus handling in the component.)*
4. **MINOR: a 401 on Save shows MSG07 twice.** `DepartmentNameForm.tsx:30-31`. This is the existing **BUG-201** (`docs/bug-log.md:216`, Open), from the same `handlesOwnErrors` plus `showMessage` pattern. It should be fixed at the shared level as the bug row says. Suggested: add this form to BUG-201's reproduction steps. Nothing to fix in this story. *(Raised by Codex.)*
5. **MINOR: test gaps (not ACs).**
   - The AC-004.8 test clicks a button that is already disabled, so it proves the one-PUT outcome but not the ref guard on its own. Enter in the input while a save is pending is not tested.
   - AC-004.11 is tested with 404, 403 and 500. A server 400 with `fieldErrors` (shown below the field) and a 401 are not tested.
   - No test covers a 200 followed by a failed GET (issue 2).

   *(Gaps listed by Codex. I checked them against the test file.)*
6. **INFO: the one-PUT guard lives in the component.** `DepartmentNameForm.tsx:18`. Suppose the user starts a Save, follows "Back to Workspace Settings", reopens the Department and saves again before the first PUT returns. The new form instance starts with a fresh ref, so two PUTs run. AC-004.8 is about clicks on one form, the backend locks the row and the last write wins (D7, AC-003.16), and nothing is corrupted. No action. *(Raised by Codex.)*

Already accepted, not findings:
- The real backend rejects the PUT without a CSRF header (Interim limit 3).
- A rename does not show in the 2.2 table in browser mock mode (Interim limit 6).
- Whitespace and length are counted differently on each side (D22, as in the Task 2 review, issues 1 and 2).

### Stage 2 checklist
- **Double submit:** guarded (ref plus disabled) and reset in `onSettled`.
- **Cancel during save:** ignored, and the test holds the PUT open to prove it.
- **Form reset on reopen:** correct after Cancel, after a save, and after a failed save followed by Cancel (the second AC-004.12 test).
- **Invalidate scope:** see issue 1.
- **Error mapping:** MSG43 shows below the field, other codes by code, and a 404 on load shows MSG04.
- **Loading and error states:** correct.
- **Accessibility:** labels, required, error descriptions and link names are fine. Focus is not (issue 3).
- **Guideline 01:** clean.
- **Mocks:** GET and PUT follow the contract. They return 404 MSG04, 400 MSG01 and MSG02, and 409 MSG43 ignoring case and excluding the Department itself. `resetMockData` rebuilds fresh seed objects. Seeding IT and HR also fixes Task 2's issue 4, and no existing 2.2 test collides with the seed (the suite is green).
- **Route:** registered with ADMIN. A malformed id gets a 404 MSG04 from the GET.

### Security pass
- No raw HTML. Names render as React text nodes.
- No new dependency.
- No Workspace or tenant value is sent: the body is `{ name }` and the id comes from the path.
- The route requires ADMIN, and the backend checks again (Task 3).
- No finding.

### Codex second opinion (read-only), verified
Codex ran with `codex exec -s read-only --ephemeral`, from the prompt `kan121-t4-review-codex.txt` in the scratchpad. The working tree is unchanged.
- **Accepted:**
  - issue 1's `/me` side effect (checked in `guards.tsx:15` and `useSession.ts`)
  - issue 3 (focus)
  - issue 4 (BUG-201, confirmed in the bug log)
  - issue 5 (test gaps)
  - issue 6 (guard across a remount)
- **Accepted with lower severity:** issue 2. Codex said MAJOR; it is MINOR here, for the reasons given in issue 2.
- **Agreed:** Codex's conclusion that the spec and D14 outrank guideline 06 for the unkeyed invalidation. It is still recorded as issue 1, for the FE Lead.
- **Not a finding:** Codex's CSRF item (MAJOR). It is Interim limit 3, already accepted in the spec.
- **No issue on either side:** questions 2, 3, 6, 8, 9 and 10. This matches my own reading.

### Assumptions made
- **AC-004.9 against guideline 06 §4:** I took the approved spec (and D14) to win over the guideline's "never invalidate with no key". So it is a pass, with issue 1 asking the FE Lead to record the exception. If the user wants the guideline to win, the fix is a spec change in Change Mode (narrow AC-004.9 to the Department keys), not a code workaround. This judgment call is open.
- **AC-004.9 "show the stored name":** I read it as the normal path. The case where the refetch fails after a 200 (issue 2) counts as a quality gap, not a breach.
- **AC-004.13 "ignore Cancel":** I read it as the Cancel button only. "Back to Workspace Settings" and browser navigation stay usable during a save (issue 6).
- **Verify scope:** at the dispatcher's request I ran only the workspace tests and the trace. The full `format:check`, `lint`, `typecheck`, full `test`, `build` and `generate:api` diff were not run by the reviewer. They still have to be green before the commit (root `CLAUDE.md`).
- **Feature status:** the feature is `in-progress`, not `evaluating`. I left the status change (`passing`) to the main session.

## Test Report
See `task-4-rename-department-screen-test-report.md`.
