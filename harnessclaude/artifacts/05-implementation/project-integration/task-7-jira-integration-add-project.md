# Task 7: Test and add or reconnect a Project in the popup (KAN-127)

**Module:** project-integration
**Assignee:** Đặng Tấn Bình (Developer: Claude, D49, no Codex)
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-13

### Working on: jira-integration-add-project (STORY-006)

Branch `feat/KAN-127-project-integration-add-project`, from HEAD `76361628`. Screen 3.1 now has an "Add project" button
(Project Manager content only) that opens the Add project popup. The popup tests a key, then shows the settings and
saves a new or reconnected Project. The table refreshes after a Save.

- `AddProjectModal`: kit `FormModal` titled "Add project" (which renders `ModalTitle`), `Form.useForm`, `FormField`, one
  `submitting` ref for both requests and every dismissal, as in `RejectTicketModal`. The primary action is
  "Test connection" until a test succeeds, then "Save" (D40: no separate re-test button). The tested result is the
  test mutation's `data`; the settings fields mount only while it is set, so a key-only Test never checks hidden
  settings. A successful test writes `doneStatusIds` and `reviewRange` from the response into the form
  (`setFieldsValue`). Any key edit calls `reset()` on both mutations, which hides the settings and Save and clears an
  inline MSG48 (AC-006.8). Save sends `jiraProjectKey` from the tested response (the canonical key), the selected ids
  and the number (AC-006.12). Success closes the popup (AC-006.13). Idle Cancel, the close icon, Escape and the mask
  go through `onCancel`; they reset both mutations and close, and `FormModal`'s `destroyOnHidden` + `clearOnDestroy`
  empties the form for the next opening (AC-006.23).
- Validation: key `rules.required('Project key')` (blank wins via `validateFirst`) plus a feature-local UTF-16 rule
  (`value.length > 20` → MSG02 `{field_name: 'Project key', max_length: 20}`), because antd's `max` counts code points.
  `Input count={{ show: true, max: 20 }}`, no native `maxLength`. Done statuses: antd `Select mode="multiple"`
  (`virtual={false}`, so every option has `role="option"`), value = id, label = name, `name (id)` when two statuses
  share a name (master design, builder check); rule `{ ...rules.required('Done statuses'), type: 'array' }`, the
  pattern `UserRolesForm` uses. Review range: `InputNumber` with no `precision`/`min`/`max` (no rounding or clamping);
  rules `{ ...rules.required('Review range'), type: 'number' }`, `rules.wholeNumberMin(1)` and a feature-local
  `> 2147483647` → MSG44 rule.
- Errors: field errors through `applyApiErrors` (MSG49 and others under their field, AC-006.16, AC-006.18); MSG48
  without a field is an inline error `Alert` derived from the mutations' `error` (AC-006.17); any other error goes to
  `showMessage(code, params)` (the code is already MSG06 for a network failure or an unknown code, `shared/api/errors.ts`)
  (AC-006.19). A 401 is left to the global handler, so it is shown once (MSG07 dialog). Values and tested settings
  stay on failure (AC-006.20).
- Busy (AC-006.21, AC-006.22): `isSubmitting = test.isPending || save.isPending` disables the primary action and every
  field; the ref drops a second submit and every dismissal. antd 6's `Modal` itself also ignores `onCancel` while
  `confirmLoading` is set (see "Mutation checks").
- `useProjectMutations.ts`: `useProjectConnectionTest()` wraps generated `useTestProjectConnection` (MSG47
  `{tracker_name: 'Jira'}` on success); `useAddProject()` wraps `useCreateProject` (MSG05, then
  `invalidateQueries({ queryKey: getListProjectsQueryKey() })`). Both set `meta: { handlesOwnErrors: true }`.
- `DepartmentProjects`: an "Add project" primary button (right-aligned `Flex`) between the Department field and the
  table or MSG04, and the popup. The button is absent for an Admin-only session, which never mounts this component.
- `mocks/handlers.ts`: unchanged. Task 6's stateful handlers covered every popup case (OPS Active, OLD Removed, SUP
  foreign → MSG49, MISSING/`1BAD` → MSG48, 201/200, `resetMockData`); per-test overrides use `server.use`.

**Who wrote what.** Claude wrote all code and tests (Codex is out of quota, D49).

**Red step.** Both test files were written first. Run: `Tests 37 failed (37)`, each on
`findByRole('button', { name: 'Add project' })` (no button yet). After the code: 35 of 37 passed; the two failures
asserted `toBeVisible()` on the Done statuses combobox, which antd renders as a zero-opacity search input. They now
assert `toBeInTheDocument()`. Then 37 of 37.

### Mutation checks
Each run changed one thing, ran the named tests, then restored the file (`cmp` confirmed):
- Save sends the typed key instead of the tested canonical key → AC-006.12 fails.
- Key length by antd `rules.maxLength` instead of the UTF-16 rule → AC-006.3 fails (19 letters + one emoji pass antd).
- No `> 2147483647` rule → AC-006.11 fails.
- No reset on key edit → both AC-006.8 tests fail.
- MSG48 also sent to `showMessage` → both AC-006.17 tests fail (shown twice).
- No list invalidation → both AC-006.15 tests fail.
- No `submitting` check in `onCancel` → AC-006.22 tests still pass: antd 6 `Modal.handleCancel` returns early while
  `confirmLoading`. Removing that check and `isSubmitting` together → both AC-006.22 tests fail. The ref stays: it also
  covers the moment between the click and the re-render that sets `isPending`.
- Client-side sort in `ProjectsTable` → the new AC-005.3 test fails (D55 item 1).

### Decisions
- **No 401 toast.** A 401 already gets the global MSG07 dialog; the popup skips `showMessage` for it so one error shows
  one message (guideline 06 §6). MSG119/MSG120 are dropped by `showMessage` itself.
- **Inline MSG48 from mutation state, no extra `useState`.** It shows while the last Test or Save failed with MSG48
  and no field error; a new request, a key edit or a dismissal clears it.
- **`virtual={false}` on the Select.** Few options; every option gets `role="option"` and `aria-selected`, which the
  tests and screen readers use. The same prop is used in `CsvImportForm`, `JiraPullForm` and `UserRolesForm`.
- **Mask click** is checked through `.ant-modal-wrap` (no accessible role); the only DOM query in the tests.
- **Popup tests use `renderApp`**, so AC-006.15 is proven by the second `GET /projects` and the refreshed row, not by
  a spy on `invalidateQueries`.
- `AddProjectModal.tsx` is 156 lines after Prettier, a little over the ~150 of guideline 05 C2; splitting the two
  settings fields into another file would add a file for no reader benefit.

### Task 6 review (D55)
Two test-only items from the Task 6 review, sent by the coordinator during this task:
1. `pages/JiraIntegrationPage.test.tsx`: new test `AC-005.3 keeps the returned row order instead of sorting by key
   (D55)` overrides the list with `ZED` then `ABC` and asserts that order. AC-005.3 was removed from the first test's
   name, which cannot fail for it (the mock already sorts). A client-side sort makes the new test fail (mutation check).
2. `mocks/handlers.test.ts`: the signed-out assertion now expects `code: 'MSG07'`; the Save MSG48 assertion now expects
   `fieldErrors: []`.

### Tests added (name → AC)
`components/AddProjectModal.test.tsx` (20 tests, `renderApp`, MSW):
- `AC-006.1 AC-006.24 opens "Add project" with a required empty Project key, Test connection and Cancel only` (row 3)
- `AC-006.2 shows MSG01 for an empty, whitespace-only or 21-space key and sends no request` (row 4)
- `AC-006.3 shows MSG02 for 21 UTF-16 code units, keeps the pasted value and sends no request; 20 are sent` (row 8)
- `AC-006.4 AC-006.5 sends one connection test with the original key and shows MSG47 once`
- `AC-006.6 AC-006.7 reveals every status preselected from doneStatusIds, the prefilled Review range and Save`
- `AC-006.6 AC-006.7 prefills the reconnect settings of a Removed Project` (row 37)
- `AC-006.6 labels statuses that share a name as name (id)`
- `AC-006.6 AC-006.9 asks for a Done status when Jira returns no statuses (row 14)`
- `AC-006.7 AC-006.11 requires a legacy invalid prefilled range to be corrected before Save (row 37)`
- `AC-006.8 hides the settings and Save when the key is edited after a test, until the next test (row 18)`
- `AC-006.9 shows MSG01 below Done statuses when none is selected and sends no Save` (row 6)
- `AC-006.10 shows MSG01 below Review range when it is empty and sends no Save` (row 7)
- `AC-006.11 shows MSG44 for 0, -1, 2.5 and 2147483648 without rounding, then saves 2147483647` (rows 7/8)
- `AC-006.12 saves once with the tested canonical key, the selected ids including a non-Done status and a numeric
  range` (row 12)
- `AC-006.13 AC-006.14 AC-006.15 closes, shows MSG05 once and refreshes the table after a new Project (201)` (row 19)
- `AC-006.13 AC-006.14 AC-006.15 reconnects OLD (200) and keeps its Project ID and member count (row 19)`
- `AC-006.4 AC-006.21 AC-006.22 locks the popup while the connection test runs (rows 12, 20)`
- `AC-006.12 AC-006.21 AC-006.22 locks the popup while Save runs, including a mask click (rows 12, 20)`
- `AC-006.23 closes the idle popup by Cancel and the close icon without a request and reopens empty (row 18)`
- `AC-006.23 closes the idle popup by Escape and a mask click without a request (row 20)`

`components/AddProjectModalErrors.test.tsx` (17 tests):
- `AC-006.16 AC-006.20 shows MSG49 of the connection test below Project key with the server params (row 36)`
- `AC-006.16 AC-006.20 shows MSG49 of Save below Project key and keeps the tested settings`
- `AC-006.17 AC-006.20 shows MSG48 of the connection test inline in the popup`
- `AC-006.17 AC-006.20 shows MSG48 of Save inline and keeps the tested settings`
- `AC-006.8 AC-006.20 shows no settings of an earlier key when the next key fails (row 12)`
- `AC-006.18 shows another field error of the connection test below Project key by code and params`
- `AC-006.18 AC-006.20 shows Save field errors below Done statuses and Review range`
- `AC-006.19 AC-006.20 shows the connection test error $name once by its code and keeps the key (rows 21, 24)` × 5:
  403 MSG08, 503 MSG06, 500 MSG06, a network failure → MSG06, an unknown code → MSG06
- `AC-006.19 AC-006.20 shows the Save error $name once by its code and keeps the settings (rows 21, 24)` × the same 5

`pages/JiraIntegrationPage.test.tsx`: `AC-005.3 keeps the returned row order instead of sorting by key (D55)`.

### Files
- Create: `frontend/src/features/jira-integration/components/AddProjectModal.tsx` (156 lines),
  `hooks/useProjectMutations.ts` (27)
- Modify: `frontend/src/features/jira-integration/components/DepartmentProjects.tsx` (33)
- Test (create): `components/AddProjectModal.test.tsx` (20 tests), `components/AddProjectModalErrors.test.tsx` (17)
- Test (modify): `pages/JiraIntegrationPage.test.tsx` (+1 test, one name), `mocks/handlers.test.ts` (2 assertions)
- Unchanged: `mocks/handlers.ts`, `src/app/`, `src/shared/`, routes, `index.ts`, registration files, the contract and the
  generated client. No new dependency.

### Verify
Frontend, from `frontend/` (Node 24.22.2):
- `npm run format`, then `npm run format:check` → `All matched files use Prettier code style!`
- `npm run lint` → `✖ 1 problem (0 errors, 1 warning)`: the existing warning in
  `src/features/tickets/components/TicketListFilters.tsx`.
- `npm run typecheck` → `tsc -b --noEmit`, exit 0.
- `npx vitest run src/features/jira-integration` → `Test Files 4 passed (4)`, `Tests 56 passed (56)`.
- `npm run test` → `Test Files 90 passed (90)`, `Tests 1555 passed (1555)`.
- `npm run build` → `✓ built in 759ms`, exit 0.
- `npm run generate:api` → no diff in `src/shared/api/generated` (and none in `openapi/`).

Harness, from the repo root:
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-006` →
  `trace project-integration (STORY-006): 24/24 acceptance criteria have a test` (exit 0)
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-005` → `10/10 acceptance criteria have a test` (exit 0)
- `bun harnessclaude/hooks/sdd.ts check project-integration` → `spec project-integration: 7 stories, 103 acceptance
  criteria — OK` (exit 0)
- `bun harnessclaude/hooks/sdd.ts check tracker` → `spec tracker: 8 stories, 56 acceptance criteria — OK` (exit 0)

Secrets: `gitleaks detect --no-git --redact --no-banner --source <file>` on each of the 7 changed or new frontend files
→ `no leaks found` for every file.

### Deviations
- `mocks/handlers.ts` is listed as Modify in the task; no fixture gap turned up, so it is unchanged.
- The D55 test-only items (Task 6 review) are in this change, as the coordinator asked.

### Bugs
None found.

### Blockers
none

### Next up
Review and commit after the user agrees. This is the module's last feature; then the D50 hardening pass and module
finishing.

default@d5df3971 — `feat(project-integration): test and add or reconnect a Project in the Add project popup (KAN-127)`, committed after the user agreed (13/11).

### Full checks (main session)
- format:check, lint (1 pre-existing warning in `TicketListFilters.tsx`), typecheck, build: exit 0. `npm run test` → `Test Files 90 passed (90)`, `Tests 1555 passed (1555)`. `generate:api` → no diff.
