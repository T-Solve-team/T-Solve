---
module: project-integration
feature: jira-integration-add-project
task: 7
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: project-integration — Task 7: Test and add or reconnect a Project in the Add project popup (STORY-006, KAN-127)

## Summary

I reviewed code commit `d5df3971` and harness commit `7465d274`, diffed against `76361628`. The code commit touches
only `frontend/src/features/jira-integration/`:
- 2 new files: `components/AddProjectModal.tsx` (156 lines) and `hooks/useProjectMutations.ts` (27)
- `components/DepartmentProjects.tsx` (+13): an "Add project" primary button and the popup
- 2 new test files: `components/AddProjectModal.test.tsx` (20 tests) and `components/AddProjectModalErrors.test.tsx`
  (17 tests)
- test-only D55 items: `pages/JiraIntegrationPage.test.tsx` (+1 test, one name changed) and `mocks/handlers.test.ts`
  (2 assertions)

`mocks/handlers.ts`, `src/app/`, `src/shared/`, routes, registration files, the contract and the generated client are
unchanged.

I checked it against:
- spec STORY-006 (AC-006.1–AC-006.24), the `createProject`/`testProjectConnection` status tables and the MSG texts
  in the spec's interface section
- Task 7 in `iteration-1/tasks.md`
- the master `design.md` "Web app" paragraphs and edge-case rows 3, 4, 6–8, 12, 14, 18–21, 24, 36 and 37;
  `iteration-1/design.md`
- D29–D31, D37, D40, D42, D53 and D55 in `kan-127-decisions.md`
- `frontend/AGENTS.md` and guidelines 05, 06, 07, 09 and 10
- the reference `review/components/RejectTicketModal.tsx`
- the shared code the popup relies on: `FormModal`, `AppForm`/`FormField` (`validateFirst`), `applyApiErrors`,
  `rules`, `showMessage`, `createQueryClient` + `app/providers/global-errors.ts`, `shared/api/errors.ts`
- the installed libraries, where the behaviour depends on them: TanStack Query core 5.104.1
  (`MutationObserver.reset/#notify`), antd 6.6.5 (`Modal.handleCancel`), `@rc-component/input` (`useCount`) and
  `@rc-component/form` (`Field`)
- the backend's real field names (`CreateProjectRequest`, `ProjectServiceImpl`: `doneStatusIds`, `reviewRange`)
- the newest SRS (`_context/Report/Sua_31-10`, UC-14 normal flow and screen 3.1 field table) for the labels

I read the session log only for the commit ids, the listed choices and the claimed results.

How the popup works:
- **One component, one ref.** `AddProjectModal` uses `Form.useForm`, `FormField` and the kit `FormModal`
  (`ModalTitle`, `destroyOnHidden`, `clearOnDestroy`). Both mutations share one `submitting` ref, which is set
  synchronously before `mutate` and released in the per-call `onSettled`.
- **Two stages, one primary action.** `okText` is "Test connection" until `test.data` exists, then "Save" (D40 as
  revised: no re-test button). The settings fields mount only while `test.data` is set, so a key-only Test never
  validates hidden settings. A successful Test writes the prefill with `setFieldsValue`.
- **Discarding the result.** Every key edit (`Input onChange`) calls `reset()` on both mutations, which drops
  `test.data` (hiding settings and Save) and any inline MSG48. `close()` does the same before `onClose`.
- **Save body.** Save sends `tested.jiraProjectKey`, the canonical key from the Test answer, plus the selected ids
  and the `InputNumber` value.
- **Errors.** `showError` calls `applyApiErrors` first, so field errors go under their fields (MSG49 under Project
  key, MSG01/MSG44 under the settings). MSG48 without a field is an `Alert` derived from the two mutations' `error`.
  A 401 is left to the global handler. Every other error goes to `showMessage(code, params)`; `ApiError.code` is
  already MSG06 for a network failure or an unknown code.

The `onSettled` release is safe on the success path. `close()` calls `reset()` inside `onSuccess`, but
`MutationObserver.#notify` still calls the stored `onSettled` after `onSuccess` returns, because `reset()` does not
clear `#mutateOptions`. A mid-flight `reset()` would leave the ref stuck, because the detached observer gets no
settle event. Both callers are closed off while busy, though: the key input is disabled and `close()` is guarded by
the ref.

The four choices in the log are acceptable:
- **No toast on 401.** Correct, and required by guideline 06 §6, which allows one message per error. A 401 still
  reaches `onUnauthorized` (MSG07 dialog) under `handlesOwnErrors`. Without the skip, `showMessage('MSG07')` would
  add an error toast, because MSG07 has no `level`. A scratch probe confirmed one dialog and no toast as committed,
  and a second MSG07 as a toast with the skip removed. No committed test pins this (LOW-1).
- **156 lines.** Six over guideline 05 C2's soft "~150". Splitting the two settings fields out would add a file for
  no reader benefit. Accepted (INFO-6).
- **`handlers.ts` unchanged.** Correct. Every popup case is reachable through the Task 6 stateful mocks or per-test
  `server.use` overrides.
- **Cancel blocked by antd and by its own check.** Safe, because the feature does not depend on antd alone. antd
  6.6.5 `Modal.handleCancel` returns early while `confirmLoading`, but that is undocumented behaviour of one
  version. The popup's own ref check is the guarantee. If antd drops the early return, the ref still ignores every
  dismissal. If someone removes the ref check, antd still covers it today (the developer's mutation). Removing both
  fails both AC-006.22 tests.

There are no CRITICAL, HIGH or MEDIUM findings:
- All 56 feature tests pass; typecheck, lint and format are clean; trace is 24/24.
- 4 of 6 mutations are killed. The other 2 survive for different reasons, both explained below: the submit-guard
  mutation tests a defence-in-depth line that no browser-level test can reach (INFO-3), and the 401 mutation shows
  a real test gap (LOW-1).
- LOW-2 is a traceability mislabel in the D55 test that came in with this commit.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 24 STORY-006 criteria hold (Stage 1 table). |
| Completeness | pass | Every criterion has a test that names it (trace 24/24). Every Task 7 test bullet has a matching test, including rows 3, 8, 12, 14, 18–21, 24, 36 and 37. Rows 4, 6 and 7 are covered on the browser side (MSG01 blank-wins, empty selection, the range extremes); their server halves belong to Tasks 2 and 3. Nothing EXTRA: no credential field, no mapping editor, no shared-kit change. |
| Correctness | pass | `npx vitest run src/features/jira-integration` 56/56, `typecheck` exit 0, `lint` 0 errors (1 old warning in `tickets`), `format:check` clean. Mutations M1, M3, M5 and M6 killed; M2 survived (INFO-3); M4 survived (LOW-1). |
| Integration | pass | Uses the generated `useTestProjectConnection`, `useCreateProject`, `getListProjectsQueryKey` and the `TrackerStatus`/`ProjectConnectionTest` types through `@/shared/api`. Form field names equal the API property names the backend emits in `fieldErrors` (`jiraProjectKey`, `doneStatusIds`, `reviewRange`), so `applyApiErrors` puts each error on a mounted field. The invalidation key `['/api/v1/projects']` is exactly the key of `useListProjects` (`useProjects.ts`), and no other query starts with it. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-006.1 FormModal "Add project", empty required Project key, Test connection and Cancel; settings and Save hidden | PASS | `AddProjectModal.tsx:106-125`; `DepartmentProjects.tsx:24-30`. Test `:136`: dialog by accessible name "Add project", empty value, `ant-form-item-required` on the label, Test connection and Cancel present, no Save, no combobox, no spinbutton. The labels match the SRS UC-14 flow ("Add project", "Test connection", "Save"); "Cancel" comes from `FormModal`. The popup title is D30's choice (no SRS source names it). |
| 2 | AC-006.2 empty/whitespace key → MSG01 `{field_name: "Project key"}`, no request | PASS | `rules.required` (`whitespace: true`) with `FormField`'s `validateFirst`, so blank wins over the length rule. Test `:152`: empty, 3 spaces and 21 spaces; MSG01 shown, MSG02 absent, the value is kept, 0 requests. |
| 3 | AC-006.3 >20 UTF-16 code units → MSG02 `{field_name, max_length: 20}`, input preserved, no request | PASS | `keyLength` uses `value.length` (`:17-22`); no native `maxLength`. The `count` counter uses `value.length` too (`@rc-component/input` `useCount` default), so the counter and the rule agree. Test `:169`: 19 letters + an emoji (21 units, 20 code points) → MSG02, value kept, 0 requests; 18 + emoji (20 units) is sent. The developer's mutation (`rules.maxLength`, which counts code points) fails it. |
| 4 | AC-006.4 one connection-test POST with the original key, however often clicked | PASS | `{ data: { jiraProjectKey } }` from the form, untrimmed. Test `:187` sends `' new1 '` and sees exactly that body. Test `:407` holds the request and clicks Test connection again: still 1 request. Also: a scratch probe firing three synchronous `fireEvent.click`s sent one request (INFO-3). |
| 5 | AC-006.5 200 → MSG47 `{tracker_name: "Jira"}` toast | PASS | `useProjectMutations.ts:10`. Test `:187` checks the catalog text, once. |
| 6 | AC-006.6 Done statuses multi-select with every status by name and id, preselected from `doneStatusIds` | PASS | `statusOptions` (value = id, label = name, `name (id)` for shared names); `virtual={false}`. Tests `:197` (new: To Do/In Progress off, Done/Closed on), `:212` (OLD reconnect prefill), `:225` (two "Done" → `Done (20001)`/`Done (20002)`), `:248` (empty statuses → Save asks for MSG01). |
| 7 | AC-006.7 Review range prefilled from `reviewRange` and Save revealed | PASS | `setFieldsValue` in `onSuccess` (`:87-91`). Tests: `:197` (3), `:212` (7), `:268` (legacy 0 shown, MSG44 on Save, then 5 saves). |
| 8 | AC-006.8 key edit after a test discards the result and hides settings and Save | PASS | `Input onChange={discardResults}` (`:124`). Tests `:295` (type one character → no settings/Save, Test connection back; the next Test sends `new12`) and Errors `:153` (a failing second key shows no settings of the first). M3 (drop the reset) fails both. |
| 9 | AC-006.9 no Done status → MSG01 `{field_name: "Done statuses"}`, no request | PASS | `{ ...rules.required('Done statuses'), type: 'array' }`. Test `:312` deselects both and sees MSG01 with 0 creates; `:248` covers the empty-statuses case. |
| 10 | AC-006.10 empty range → MSG01 `{field_name: "Review range"}`, no request | PASS | `{ ...rules.required('Review range'), type: 'number' }`. Test `:326`. |
| 11 | AC-006.11 not a whole number 1..2147483647 → MSG44 `{}`, no request | PASS | `rules.wholeNumberMin(1)` + feature-local `rangeMax`; `InputNumber` without `precision`/`min`/`max`, so no rounding or clamping. Test `:338`: 0, -1, 2.5 and 2147483648 each → MSG44, the typed value kept, 0 creates; 2147483647 saves as a number. The developer's mutation (no `rangeMax`) fails it. |
| 12 | AC-006.12 one Save with the tested canonical key, selected ids, numeric range, however often clicked | PASS | `:100` sends `tested.jiraProjectKey`. Test `:358` tests `' new1 '`, adds the non-Done "To Do", holds Save and clicks twice: one body `{jiraProjectKey: 'NEW1', doneStatusIds: ['10002','10003','10000'], reviewRange: 3}`. M1 (typed key) fails 3 tests. |
| 13 | AC-006.13 201/200 → close | PASS | `onSuccess: close`. Tests `:374` (201) and `:388` (OLD 200) wait for the leave animation and the dialog's removal. |
| 14 | AC-006.14 201/200 → MSG05 toast | PASS | `useProjectMutations.ts:22`; both tests check exactly one MSG05. |
| 15 | AC-006.15 invalidate the generated `listProjects` key | PASS | `invalidateQueries({ queryKey: getListProjectsQueryKey() })`. Both tests see a second `GET /projects` and the refreshed row: `NEW1`, PM, `1`, Active; OLD keeps `MOCK_PROJECT_IDS.OLD` and member count 2, now Active (row 19). The developer's mutation (no invalidation) fails both. |
| 16 | AC-006.16 MSG49 on `jiraProjectKey` from either request → below Project key with the server's params | PASS | `applyApiErrors`. Errors `:102` (Test, stateful SUP mock, `department_name: 'Customer Support'`) and `:113` (Save override), each shown once. |
| 17 | AC-006.17 MSG48 from either request → inline with the server's params | PASS | `inlineError` → `<Alert type="error" title={msg('MSG48', params)}>`, which is `role="alert"`, and `showError` skips the toast. Errors `:131` (Test, `missing`) and `:142` (Save), each once. The developer's mutation (MSG48 also to `showMessage`) fails both. |
| 18 | AC-006.18 another field error → its message by code and params below the field | PASS | Errors `:164` (Test MSG02 with the server's `field_name: 'jiraProjectKey'`) and `:180` (Save MSG01 on `doneStatusIds` + MSG44 on `reviewRange`, both shown). |
| 19 | AC-006.19 another non-field error → shared error handling by code, MSG06 fallback | PASS | `showMessage(error.code, error.params)` under `handlesOwnErrors`, so the global toast is off and nothing doubles. `it.each` × 2 operations × 5: 403 MSG08, 503 MSG06, 500 MSG06, network → MSG06, unknown `MSG999` → MSG06, each once. Mutation `retry: 0` means one request. 401 is the global dialog's (LOW-1). |
| 20 | AC-006.20 failure keeps the popup open with values and any revealed settings | PASS | Nothing resets on error. `expectSettingsKept` checks the same dialog, the key, both chips, range `9` (edited after the test) and Save, after every Save failure; the Test failures keep the key. A probe confirmed a second Test after a failure is sent (the ref is released). |
| 21 | AC-006.21 while running: no field edits, no further Test/Save | PASS | `isBusy` disables Input, Select, InputNumber and the OK button (`FormModal` `disabled` + `confirmLoading`); the ref drops a second submit. Tests `:407` (held Test: key disabled, second click → still 1) and `:429` (held Save: key, range and statuses disabled, second click → 1). |
| 22 | AC-006.22 while running: ignore Cancel, close icon, Escape, mask | PASS | `onCancel` → `if (!submitting.current) close()`. Tests `:407` and `:429` try Cancel, Close and Escape, and `:429` the mask too; the dialog never gets `ant-zoom-leave-active`. After release, Test re-enables and Save closes. |
| 23 | AC-006.23 idle dismissal closes without a request and clears values, messages and tested result | PASS | `close()` resets both mutations; `destroyOnHidden` + `clearOnDestroy` empty the form. Tests `:453` (Cancel after an MSG01 and the close icon after a successful test → reopens empty, no MSG01, no settings, Test connection back, 1 test / 0 creates) and `:476` (Escape and mask, 0 requests). |
| 24 | AC-006.24 no token, service-account email, credential or mapping editor | PASS | The component has one text field. Test `:136` checks one textbox and no `/token\|service account\|e-?mail\|credential\|mapping/i` text. |

### Stage 2 — Quality and security pass

Checked and found sound:
- **Double submit.** Two lines of defence stand before the ref:
  - antd's form drops a submit whose validation was overtaken by a newer one: `@rc-component/form` resolves
    `onFinish` only for `lastValidatePromise`.
  - After the first render, the OK button is disabled.

  The ref is a third, synchronous line, as in `RejectTicketModal`. I could not make two requests with three
  synchronous clicks even with the ref check removed (INFO-3).
- **Stale settings from another key.** `tested` is the Test mutation's own result, not copied state. Any key edit
  resets it; a later failed Test leaves it `undefined`; `close()` resets it before the next opening. Hidden settings
  values left in the form store are overwritten by the next successful Test's `setFieldsValue`. Before that they are
  never submitted, because `onFinish` gets only mounted fields and `handleSubmit` reads `tested`.
- **One message per error.**
  - Field errors: inline only (`applyApiErrors` returns true → no toast).
  - MSG48: inline only.
  - 401: the global dialog only.
  - MSG119/MSG120: the site page only (`createQueryClient` routes them first, and `showMessage` drops them).
  - Everything else: one toast, because `handlesOwnErrors` turns the global toast off.
  - MSG47 and MSG05 come from the hook-level `onSuccess`, which runs once per mutation.
- **Accessibility.** Every field is found by its accessible name (`textbox` "Project key", `combobox` "Done
  statuses", `spinbutton` "Review range"). The MSG48 `Alert` is `role="alert"`. Field errors are rendered by antd
  `Form.Item`, which links the help text to the control (kit behaviour, same as every other form).
- **Frontend rules.**
  - No raw antd `Modal` (kit `FormModal`); lint's `no-restricted-imports` passes.
  - No native `maxLength`, no `precision`.
  - All message text comes from `msg()`/`showMessage`; the other strings are the SRS's labels.
  - No `any`, `!` or `@ts-ignore`; the one narrowing `return` (`:97`) is documented and unreachable after the
    rules.
  - No `useEffect`, no `useState` for server data.
  - The tests follow guideline 10: `renderApp` + MSW, typed generated handlers, by role and name. The one DOM
    query (`.ant-modal-wrap` for the mask) has no accessible alternative and is commented in the log.
  - File sizes: 20 and 17 tests, both under T7's ~30. On this machine the larger file ran 78 s alone, and the
    `ImportPreviewPage.test.tsx` the guideline names as ~65 s ran 176.6 s. Scaled by that ratio (about 2.7×), the
    larger file is about 29 s in CI, inside T7's ~40 s.
- **Security.** The popup sends only the key, ids and range; no credential reaches the browser or the request
  (AC-006.24). The session log reports gitleaks clean on the 7 files; I saw no secret in the diff.

## Traceability

Run from the main repo root (HEAD `7465d274`). The Developer's uncommitted backend test edits in that folder add no
STORY-006 ids.

```
  ✓ AC-006.1  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.2  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.3  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.4  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.5  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.6  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.7  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.8  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx, frontend/src/features/jira-integration/components/AddProjectModalErrors.test.tsx
  ✓ AC-006.9  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.10  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.11  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.12  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.13  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.14  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.15  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.16  frontend/src/features/jira-integration/components/AddProjectModalErrors.test.tsx
  ✓ AC-006.17  frontend/src/features/jira-integration/components/AddProjectModalErrors.test.tsx
  ✓ AC-006.18  frontend/src/features/jira-integration/components/AddProjectModalErrors.test.tsx
  ✓ AC-006.19  frontend/src/features/jira-integration/components/AddProjectModalErrors.test.tsx
  ✓ AC-006.20  frontend/src/features/jira-integration/components/AddProjectModalErrors.test.tsx
  ✓ AC-006.21  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.22  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.23  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
  ✓ AC-006.24  frontend/src/features/jira-integration/components/AddProjectModal.test.tsx
trace project-integration (STORY-006): 24/24 acceptance criteria have a test
```

`trace project-integration STORY-005` → `10/10`, but see LOW-2: the AC-005.2 and AC-005.3 ids are swapped between
two tests.

## Issues

No CRITICAL, HIGH or MEDIUM findings. Non-blocking:

1. **LOW-1 — no test pins "one message for a 401", so a second MSG07 could come back unnoticed**
   - Location: `frontend/src/features/jira-integration/components/AddProjectModal.tsx:69`
     (`|| error.status === 401`); missing in `AddProjectModalErrors.test.tsx` (`NON_FIELD`, `:82-92`).
   - Mutation M4 drops the 401 skip and all 37 popup tests pass. A scratch probe showed the effect: with the skip,
     a 401 on Test gives one MSG07 dialog and no toast; without it, a second MSG07 appears as an error toast
     (`role="alert"`) under the dialog. That breaks guideline 06 §6 ("never show two messages for one error").
   - The behaviour today is correct. This is a test gap on a choice the session log calls out.
   - Fix: one test in `AddProjectModalErrors.test.tsx`. Answer the next Test with `problem(401, 'MSG07')`, then wait
     for the MSG07 dialog. Use `findAllByText`, because antd prints a confirm dialog's title twice. Then assert that
     no `alert` contains `msg('MSG07')` (the toast is the only `role="alert"` with that text). If it fits the
     file's pattern, add the same for Save.
2. **LOW-2 — the D55 order test is labelled AC-005.3 (PM name) instead of AC-005.2 (returned order), so trace maps
   AC-005.3 to a test that cannot fail for it**
   - Location: `frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx:70` (named
     `AC-005.3 keeps the returned row order…`; every row has `projectManager: null`), and `:46` (the first test, which
     checks `Đỗ Minh Quân` at `:59`/`:63`, no longer names AC-005.3).
   - Spec: AC-005.2 is "… columns … using the returned order"; AC-005.3 is "show its `fullName` in the Project
     Manager column". The Task 6 review's LOW-1 was about AC-005.2. The D55 row in `kan-127-decisions.md:61` wrote
     "AC-005.3", and the developer followed it.
   - Effect: the test file still covers both behaviours, but by the wrong ids. `sdd.ts trace` reports AC-005.3 as
     covered by a test whose rows all have no PM, and the test that can actually fail on a client sort does not name
     AC-005.2.
   - Fix (names only):
     - rename `:70` to `AC-005.2 keeps the returned row order instead of sorting by key (D55)`
     - put `AC-005.3` back in the name at `:46`
     - correct "AC-005.3" to "AC-005.2" in the D55 row
3. **INFO-3 — the `submitting` check in `handleSubmit` cannot be killed by a browser-level test**
   - Location: `AddProjectModal.tsx:74`. Mutation M2 deletes `if (submitting.current) return;` and all 37 tests pass.
     A probe with three synchronous `fireEvent.click`s on Test connection, and two on Save, still sent one request
     each without the check.
   - Reason: antd's form already ignores an overtaken submit (`lastValidatePromise`), and after the first render the
     button is disabled. The ref is the design's required third line (design "Web app", same as
     `RejectTicketModal`) and costs one line. No fix; do not delete it because it "has no test".
4. **INFO-4 — the master `design.md` still has the pre-revision D40 text**
   - Location: `harnessclaude/artifacts/04-plans/project-integration/design.md:240` ("… Save after a successful test,
     with a body re-test button when tested").
   - `kan-127-decisions.md:46` (D40 "revised in review": no re-test button) and the same `design.md:98` agree with
     the code. Fix: copy the revised D40 wording into the design table, so a later reader does not add the button.
5. **INFO-5 — `progress.json` `lastCommit` for project-integration still points at Task 3**
   - Location: `harnessclaude/progress.json:170-171` (`default: 15b491d9…`, "create a Project with its manager"),
     although `d5df3971` is the module's latest code commit. The session log carries the right id. Fix: record
     `d5df3971` through the usual tracking command when the module finishes.
6. **INFO-6 — two accepted small deviations**
   - `AddProjectModal.tsx` is 156 lines against guideline 05 C2's "~150"; accepted (see Summary).
   - After a server field error (e.g. MSG49 under Project key), typing a new key leaves the message visible until the
     field loses focus. `@rc-component/form` clears errors only on an external `setFieldsValue`; the app validates on
     blur. AC-006.8 asks only for the tested result to go. Every other form in the app (the Reject popup included)
     behaves the same, so this is a kit-level UX point, not this feature's.

## Assumptions made

- **"Ignore Cancel" (AC-006.22).** I read "ignore" as "clicking it does nothing", not "Cancel is disabled". The design
  ("disable all fields/buttons") is stricter, but the spec wins and the tests prove the click is ignored.
- **"however often the button is clicked while it runs" (AC-006.4/AC-006.12).** I accept a held-request test with
  repeated clicks as proof, plus my probe for synchronous clicks; a test with the ref alone cannot be built (INFO-3).
- **Popup title.** "Add project" is the spec's and D30's choice; the SRS names the button "Add project" but not the
  popup. Accepted.
- **Add project hidden during loading and on a list error.** `DepartmentProjects` shows the button only with the
  list data (Task 6's states replace the whole content). The spec only says the button is on screen 3.1; I assumed
  hiding it while the Department is unknown is acceptable.
- **CI timing.** I judged T7 by scaling this machine's time with the guideline's reference file (about 2.7× slower
  here), not by a CI run.
- **Open judgment call (minor): LOW-1 and LOW-2 now or in the D50 hardening pass?** Both are test-only (one new
  test, three names). I recommend the D50 hardening pass before module finishing, as D50–D52 did for other test-only
  findings. The verdict does not depend on them, and the feature can be marked `passing`.
- **Open judgment call (minor): INFO-4.** Correct the stale D40 row in `design.md` with the hardening pass. It is a
  planning artifact, and editing it may need the design to be re-approved if the harness treats it as part of the
  approved plan.

## Test Report

Isolated worktree at `7465d274`
(`git worktree add …/scratchpad/wt-review-t7 7465d274`). `frontend/node_modules` was a junction to the main tree's
folder (`cmd /c mklink /J`), so nothing was installed. All commands ran from `<worktree>/frontend` with Node
v24.22.2. The main tree, where a Developer is editing backend tests, was not written to. At the end:
- the junction was removed with `cmd /c rmdir`
- the main `node_modules` was intact (261 entries)
- the worktree was removed with `git worktree remove --force`

- `npx vitest run src/features/jira-integration`
  ```
  Test Files  4 passed (4)
       Tests  56 passed (56)
    Duration  73.53s
  ```
  The `Not implemented: Window's getComputedStyle() … pseudo-elements` lines are jsdom noise. Per file (JSON
  reporter, parallel run): `AddProjectModal.test.tsx` 20 tests 68.1 s, `AddProjectModalErrors.test.tsx` 17 tests 52.2
  s, `JiraIntegrationPage.test.tsx` 10 tests 8.7 s, `handlers.test.ts` 9 tests 0.6 s. Run alone, `AddProjectModal`
  took 78.0 s, and the guideline's reference `ImportPreviewPage.test.tsx` (53 tests, "~65 s") took 176.6 s.
- `npm run typecheck` → `tsc -b --noEmit`, exit 0.
- `npm run lint` → 0 errors, 1 warning: the old
  `src/features/tickets/components/TicketListFilters.tsx` `react-refresh/only-export-components`.
- `npm run format:check` → `All matched files use Prettier code style!`
- Mutations: each one was applied with `sed` in the worktree, then the named test files were run. `git checkout` of
  the feature folder restored it, and `git status` was clean after each (only the scratch probe file was listed,
  and it was deleted before the final run).

  | Mutation | File | Tests | Result |
  |----------|------|-------|--------|
  | M1 Save sends the typed key: `{ jiraProjectKey, doneStatusIds, reviewRange }` | `AddProjectModal.tsx:100` | 3 failed / 20: `AC-006.12 …`, `AC-006.11 …`, `AC-006.7 AC-006.11 …` | killed |
  | M2 no submit guard: delete `if (submitting.current) return;` | `AddProjectModal.tsx:74` | 37 passed / 37 | survived (INFO-3) |
  | M3 no key-edit reset: delete `onChange={discardResults}` | `AddProjectModal.tsx:124` | 2 failed / 37: `AC-006.8 hides the settings …`, `AC-006.8 AC-006.20 shows no settings of an earlier key …` | killed |
  | M4 toast on 401: drop `\|\| error.status === 401` | `AddProjectModal.tsx:69` | 37 passed / 37; the scratch probe fails (MSG07 toast next to the dialog) | **survived** (LOW-1) |
  | M5 ref never released: `onSettled` no longer sets `submitting.current = false` | `AddProjectModal.tsx:78` | 12 failed / 37 (every second request: AC-006.7/8/11 and all Save-error tests) | killed |
  | M6 (probe, original code) 401 on Test → one MSG07 dialog, no MSG07 toast | — | probe passed on the original, failed under M4 | confirms LOW-1 |

  The developer's own mutations (session log) also killed: `rules.maxLength` instead of the UTF-16 rule
  (AC-006.3), no `rangeMax` (AC-006.11), MSG48 also toasted (AC-006.17 ×2), no list invalidation (AC-006.15 ×2),
  client sort (the D55 order test). Dropping the `onCancel` ref check alone survives, because antd 6.6.5 also
  returns early; dropping it together with `isSubmitting` fails both AC-006.22 tests.
- Scratch probe (in the worktree only, never committed, deleted before cleanup):
  - three synchronous clicks on Test → 1 request
  - two on Save → 1 request
  - both with and without the M2 guard
  - a Test after a 503 → sent (ref released)
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-006`: 24/24 (above), exit 0;
  `… STORY-005`: 10/10 (labels: LOW-2).
- Full suite, build and `generate:api`: I did not rerun them. The session log and the main session report
  `Test Files 90 passed (90)`, `Tests 1555 passed (1555)`, build exit 0, and no `generate:api` diff at `d5df3971`.
  This commit changes no shared file, contract or generated file, so the feature run above covers what changed.
