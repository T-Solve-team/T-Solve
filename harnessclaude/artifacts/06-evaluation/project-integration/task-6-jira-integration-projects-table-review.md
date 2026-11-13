---
module: project-integration
feature: jira-integration-projects-table
task: 6
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: project-integration — Task 6: Show the Department and its Projects on Jira Integration (STORY-005, KAN-127)

## Summary

I reviewed code commit `22068e4d` and harness commit `76361628`, diffed against `88bb8329`. The code commit touches
only `frontend/src/features/jira-integration/`:
- `pages/JiraIntegrationPage.tsx` (+7 −1): a `hasRole('PROJECT_MANAGER')` gate
- 3 new files: `components/DepartmentProjects.tsx` (24 lines), `components/ProjectsTable.tsx` (35) and
  `hooks/useProjects.ts` (6)
- `mocks/handlers.ts` (+274, was an empty stub): stateful GET, connection test and Save
- 2 new test files: `pages/JiraIntegrationPage.test.tsx` (9 tests) and `mocks/handlers.test.ts` (9 tests)

Nothing changed in `src/app/`, `src/shared/`, routes, registration files, the contract or the generated client.

I checked it against:
- spec STORY-005 (AC-005.1–AC-005.10), and STORY-001–003 for the mock answers
- Task 6 in `iteration-1/tasks.md`
- the master `design.md` "Web app" and MSW paragraphs, and edge-case rows 2, 15, 19, 21, 30, 36 and 37
- `iteration-1/design.md`
- D26, D29–D33, D36, D44, D53 and D54 in `kan-127-decisions.md`
- `frontend/AGENTS.md` and guidelines 05, 06, 07, 08 and 10
- the `review` reference feature (`mocks/handlers.ts` `actor()`, `ReviewQueuePage`)
- the backend contract: `listProjects`, `testProjectConnection`, `createProject`, `ProjectList`, `ProjectRow`,
  `Project`, `ProjectConnectionTest` and `CreateProjectRequest` in `frontend/openapi/tsolve-api.yaml`
- the backend's real behaviour: `ProjectController`, `ProjectServiceImpl`, `ProjectConnectionTestResponse` and
  `common/exception/GlobalExceptionHandler` + `ProblemDetails`

I read the session log only for the commit ids, the listed deviations and the claimed test counts.

How the screen works:
- **Gate.** `JiraIntegrationPage` renders the existing `ScreenPlaceholder` unless the session has
  `PROJECT_MANAGER`. Only with that role does it mount `DepartmentProjects`, so an Admin-only session never creates
  the query. The route table already allows only `ADMIN` and `PROJECT_MANAGER` (`route-table.test.tsx:22`), so an
  Employee gets the guard's MSG08 page.
- **States.** `DepartmentProjects` follows guideline 05 C9 literally:
  - `isPending` → `LoadingState`
  - `isError` → `ErrorState` with `onRetry={() => void refetch()}`. It renders `msg(error.code, error.params)`
    with `role="alert"`.
  - data → a read-only `Input` labelled "Department", then `EmptyState` (MSG04) when `projects` is empty, else
    `ProjectsTable`
- **No duplicate message.** `onQueryError` is not wired in `src/app`, so a failed list shows no toast next to the
  `ErrorState`.
- **Table.** `ProjectsTable` is the kit `DataTable` with exactly five columns, in the spec's order:
  - "Project ID": the public id
  - "Jira project key"
  - "Project Manager": `fullName`, else `msg('MSG54')`
  - "Members": `memberCount`
  - "Status": `cells.tag`

  The table is unpaged, because `DataTable` sets `pagination={false}` when no `pagination` prop is given. It is
  unsorted (no `sortable` column), and `projectId` is the row key.
- **Status labels.** `cells.tag(status)` renders `<StatusTag>`, which reads `STATUS_TAGS`: `ACTIVE` → "Active"
  (green), `REMOVED` → "Removed" (default). Both keys were already in the shared map. No raw value reaches the cell
  (mutation M1 is killed).

The three listed deviations are acceptable:
- **No AC-006 ids on the fixture tests.** Correct, and consistent with D48. `sdd.ts trace … STORY-006` must not
  count mock-contract tests as popup coverage. Task 7's popup tests own AC-006.15–AC-006.17.
- **`EmptyState` instead of `DataTable`'s empty view.** Required by AC-005.8 ("MSG04 in place of the table"):
  `DataTable` would show MSG04 inside an empty table with headers. Mutation M3 confirms that the test tells the two
  apart.
- **Mock simplifications.** One Department for every PM, no alias mocking, and a different order of field errors.
  None of them can be reached through the screen or the Task 7 popup as specified, because local validation runs
  first. They are recorded as INFO-2 and INFO-3 so that Task 7 does not build on them unknowingly.

There are no CRITICAL, HIGH or MEDIUM findings. All 18 feature tests pass, typecheck, lint and format are clean, and
5 of 6 mutations are killed. Findings:
- LOW-1: the surviving mutation. The AC-005.2 test cannot tell "the returned order" from a client-side sort by key.
- INFO-2 to INFO-5 are notes on the mocks and the existing kit.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 10 STORY-005 criteria hold (Stage 1 table). |
| Completeness | pass | Every criterion has a test that names it (trace 10/10). Every Task 6 test bullet has a matching test: rows 2, 15 and 21 through the page; rows 19, 36 and 37 as mock-contract fixtures; row 30 through the page with an override. Nothing EXTRA: the Test and Save handlers are required by the task for Task 7. |
| Correctness | pass | `npx vitest run src/features/jira-integration` 18/18, `typecheck` exit 0, `lint` 0 errors (1 old warning in `tickets`), `format:check` clean. Mutations M1–M5 killed, M6 survived (LOW-1). |
| Integration | pass | The mocks are typed with the generated `ProjectList`, `ProjectConnectionTest` and `Project`. They answer the same statuses, codes, params and field names as the backend: 201 new / 200 reconnect; MSG49 on `jiraProjectKey` with `{tracker_name, department_name}` and identical field params, as `GlobalExceptionHandler.coded()` builds it; MSG48 `{tracker_name: "Jira"}` with no `fieldErrors`; MSG44 `{}` on `reviewRange`; MSG01 `{field_name}`; `projectId` omitted for a new key (`@JsonInclude(NON_NULL)`). Remaining differences: INFO-2 and INFO-3. Production code imports only from `@/shared/api`, `@/shared/ui`, `@/shared/messages` and `@/shared/auth`. The test imports the generated `projects.msw` handler, which guideline 10 §5 allows. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-005.1 load `GET /api/v1/projects`, show the Department name in a read-only field labelled "Department" | PASS | `DepartmentProjects.tsx:17-19`: `FormField label="Department" htmlFor="department"` + `Input id="department" … readOnly`. The test checks `getByLabelText('Department')` has the value `IT Operations` and the `readonly` attribute, with exactly 1 list request. |
| 2 | AC-005.2 columns "Project ID", "Jira project key", "Project Manager", "Members", "Status", in the returned order | PASS (LOW-1) | `ProjectsTable.tsx:6-21`. The test compares all `columnheader` texts with the exact array (`JiraIntegrationPage.test.tsx:13,56`) and the row order `OLD, OPS` (`:58`). No paging, no `sortable`. The order test cannot fail on a client-side key sort (M6, LOW-1). |
| 3 | AC-005.3 `projectManager.fullName` in the PM column | PASS | `ProjectsTable.tsx:17`. Every cell of both rows is checked (`:59-66`): `Đỗ Minh Quân`, counts `2`/`3`, public ids. |
| 4 | AC-005.4 `projectManager: null` → MSG54 | PASS | `p.projectManager?.fullName ?? msg('MSG54')`, from the catalog. The test overrides the list with the typed `getListProjectsMockHandler` and checks the cell equals `msg('MSG54')` (`:70-94`). Killed by M1 too (the whole row is compared). |
| 5 | AC-005.5 `ACTIVE`/`REMOVED` → "Active"/"Removed" | PASS | `cells.tag` → `StatusTag` → `STATUS_TAGS.ACTIVE.label`/`REMOVED.label`. The test expects the label text, not the API value. M1 (`cells.text(p.status)`, raw `ACTIVE`/`REMOVED`) fails 2 tests. |
| 6 | AC-005.6 loading state in place of the table | PASS | `DepartmentProjects.tsx:9`. The test holds the first GET, waits until the page's own request has started (so the route's Suspense fallback cannot satisfy it), and then asserts "Loading ..." and no "Project ID" header. After release, the row appears and the loading view is gone. |
| 7 | AC-005.7 returned message by code, error state with retry | PASS | `DepartmentProjects.tsx:10-12`. Test 1: 409 MSG48 with `{tracker_name}` → the alert text equals `msg('MSG48', {tracker_name: 'Jira'})`, with no table; "Try again" → table, 2 requests. Test 2: `HttpResponse.error()` → MSG06. |
| 8 | AC-005.8 empty list → MSG04 in place of the table | PASS | `DepartmentProjects.tsx:21`. The test expects `msg('MSG04')`, the Department still shown, and no "Project ID" header. M3 (render `DataTable` with zero rows) fails it. |
| 9 | AC-005.9 Admin without PM → placeholder, no Projects request, no Add project button | PASS | `JiraIntegrationPage.tsx:8-10`: the query is not mounted. The Admin-only test checks the placeholder text, no Department, no "Add project" and 0 requests. The Admin+PM test overrides `/me` with roles `[EMPLOYEE, ADMIN, PROJECT_MANAGER]` and sees the table. M2 (`… \|\| hasRole('ADMIN')`) fails it. The developer's own mutation, which dropped the gate, failed the Admin-only test. |
| 10 | AC-005.10 neither Admin nor PM → guard MSG08, no Projects request | PASS | Route roles `['ADMIN', 'PROJECT_MANAGER']`. The test (Employee) checks `msg('MSG08')`, no Department and 0 requests. |

Task 6 fixture bullets (mock contract, `handlers.test.ts`):

| Bullet | Status | Evidence |
|--------|--------|----------|
| Typed responses, GET own Department sorted | PASS | `HttpResponse.json<ProjectList>` etc. `OWN` filter. `SUP` (Customer Support) is never listed. The sort is code-unit order, equal to `COLLATE "C"` for `[A-Z0-9_]` keys. The sort runs on a `filter()` copy, so the store order is untouched. |
| POST Test prefill | PASS | New key: canonical `NEW_ONE`, Done defaults `10002, 10003`, range 3, no `projectId`, and a non-Done status present. OLD: kept id `10003` (stale `99999` dropped), stored range 7, `reconnect: true` + id (row 37). |
| Save 201 updates GET; OLD Save 200 keeps id | PASS | 201, deduplicated ids in first-seen order (AC-002.15), new row first by key with 1 member. OLD: 200, same id, member count 2, now Active (row 19). |
| SUP MSG49, MISSING MSG48 | PASS | Both operations. MSG49 carries the owning Department in the field params (row 36). MSG48 has no field, for `MISSING` (Jira not found) and `1BAD` (format, AC-001.4). |
| `resetMockData` restores ids, settings and counts | PASS | `seedProjects()` returns fresh row objects and arrays. `Object.assign` and `memberCount +=` only touch those, never the shared `OWN`/`QUAN`/`JIRA_STATUSES` constants. The id counter is reset too. M4 (keep the counter) and M5 (drop new rows but keep the mutated OLD) each fail the reset test. The reset is registered through `FEATURE_MOCKS` and runs after every test (`src/test/setup.ts:145`). `setDevUser` is stored in `localStorage`, which `setup.ts:155` clears before each test. |

### Stage 2 — Quality and security pass

Checked and found sound:
- **Frontend rules.**
  - No raw antd `Table`/`Tag`/`Modal`: only kit components, plus antd `Input`, which is allowed.
  - Lint's `no-restricted-imports` passes.
  - No message text typed by hand: MSG04, MSG54 and the error text all go through the catalog.
  - The other strings are the SRS's field and column labels.
  - No `any`, `!` or `@ts-ignore`, and typecheck is clean under strict.
  - No `useEffect`, no copy of query data into state.
  - The page has the SRS comment and a default export.
  - Every file is far below the C2 size limits, and each test file is far below the T7 limit (~30 tests).
- **Access.** The gate uses `useSession().hasRole`, as guideline 08 §4 prescribes. The backend re-checks the role
  (AC-007.1). The UI gate only decides whether the query is mounted.
- **Background refetch.** A failed refetch that comes after data replaces the table with `ErrorState`. That is the
  reference feature's behaviour too (`ReviewQueuePage.tsx:123`), so there is no finding.
- **Mock role check.** `projectManager()` calls `getMe()` through the real client, copying the reference's
  `actor()`. It passes through 401 MSG07 and the dev-site codes (MSG119/MSG120) unchanged.
- **Mock body parsing.** `readBody` refuses non-object JSON as 400 MSG06, as AC-001.20/AC-002.16 require.
- **Security.** The mock code ships only in mock mode (guideline 10 §5 import rule, enforced by lint). No secret
  sits in any changed file: the session log reports gitleaks clean, and I saw no token, email or credential in the
  diff.

## Traceability

Run from the main repo root (HEAD `76361628`). The Task 7 developer's uncommitted work in the same folder does not
add STORY-005 ids.

```
  ✓ AC-005.1  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx, frontend/src/features/jira-integration/mocks/handlers.test.ts
  ✓ AC-005.2  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx
  ✓ AC-005.3  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx
  ✓ AC-005.4  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx
  ✓ AC-005.5  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx
  ✓ AC-005.6  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx
  ✓ AC-005.7  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx
  ✓ AC-005.8  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx
  ✓ AC-005.9  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx
  ✓ AC-005.10  frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx
trace project-integration (STORY-005): 10/10 acceptance criteria have a test
```

## Issues

No CRITICAL, HIGH or MEDIUM findings. Non-blocking:

1. **LOW-1 — the AC-005.2 order test cannot tell "the returned order" from a client-side sort by key**
   - Location: `frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx:58`, which tests
     `ProjectsTable.tsx:31` (`rows={rows}`).
   - The default mock already returns the rows sorted by key (`OLD, OPS`). Mutation M6 sorts the rows in the
     table (`[...rows].sort(by jiraProjectKey)`) and all 9 page tests still pass.
   - Today's code does not sort, so this is a gap in the test, not a defect. But AC-005.2 and D26 forbid client
     sorting. A later `localeCompare` sort would differ from the backend's `COLLATE "C"` order for keys with `_`
     (Task 5 INFO-3), and nothing would catch it.
   - Fix (a few lines in the existing test, or a new one): override the list with rows that are *not* in key order,
     e.g. `ZED` then `ABC`, via `getListProjectsMockHandler`, and assert that the rows on screen are `ZED, ABC`.
2. **INFO-2 — the mock Save checks fields in a different order than the backend, and returns one field error
   instead of one per field**
   - Location: `frontend/src/features/jira-integration/mocks/handlers.ts:237-239` (`checkSettings` before
     `checkKey`), `:130-137` and `:110-117`.
   - Backend order (D44): `@Valid` first, which yields one error per failing field: key MSG01/MSG02, ids MSG01,
     range MSG01. Then the role (D36: a non-PM with an invalid body gets 400). Then the key format (MSG48). Then MSG44
     and blank ids. Then MSG49, then Jira.
   - The mock checks the role first. It then runs every settings check (including MSG44) before the key checks, and
     answers only the first error.
   - Examples:
     - `{jiraProjectKey: '', doneStatusIds: []}`: the backend answers two field errors, the mock one
       (`doneStatusIds`).
     - `{jiraProjectKey: '1BAD', reviewRange: 0}`: the backend answers 409 MSG48, the mock 400 MSG44.
   - The UI cannot reach either case: Task 7's local validation blocks blank keys, overlength keys, empty ids and
     out-of-range values before any request. The developer recorded this.
   - Fix: none now. If a Task 7 test needs several server field errors at once, override the handler in that test
     with `problem(400, 'MSG01', { fieldErrors: [...] })` rather than relying on the stateful mock.
3. **INFO-3 — the mock's universe is not the session fixtures' universe**
   - Location: `handlers.ts:28-41` (`OWN` = "IT Operations", id `…03d1`) against `src/mocks/session.ts`
     (`pm.department` = "IT", id `…00d1`, managed projects `ITSUP`/`ITNET`; `pm-km` is in "HR").
   - In mock mode, a PM's `/me` Department and Projects differ from what screen 3.1 shows. Every PM user, including
     `pm-km` of HR, sees IT Operations with OPS/OLD. A Project saved in the popup never appears in `/me.projects`.
   - The design (`design.md` MSW paragraph) names the seed "IT Operations" with OPS/OLD/SUP and forbids importing
     another feature's mock internals, so this follows the approved design.
   - A related gap with no reachable effect: the reconnect branch adds 1 to `memberCount` when the listed PM is
     `null` (`:258`). The backend gives +1 only when there is no PM row. A deactivated PM row is replaced, so the
     count stays the same. No seed has a null PM.
   - Fix: none required (judgment call below).
4. **INFO-4 — a status outside `STATUS_TAGS` would crash the page instead of showing a cell**
   - Location: `frontend/src/shared/ui/StatusTag/StatusTag.tsx:9` (`const { label, color } = STATUS_TAGS[status]`),
     reached from `ProjectsTable.tsx:20`.
   - This is the frontend side of Task 5 LOW-1. D54 already decided that the backend fails loudly (500 MSG06) on an
     unknown `project.status`, and the generated type allows only `ACTIVE | REMOVED`. No action in this feature;
     `shared/ui` is outside its scope.
5. **INFO-5 — two small assertion gaps in the mock-contract tests**
   - `handlers.test.ts:39` checks only `status: 401` for the signed-out caller, not `code: 'MSG07'`.
   - `:127-129` does not assert the absence of `fieldErrors` on the Save MSG48. The connection-test half does
     (`fieldErrors: []`).
   - The implementation is correct in both cases. Fix: add `code: 'MSG07'` and `fieldErrors: []` to the two
     `toMatchObject` calls, if the file is touched in Task 7.

## Assumptions made

- **Department field.** I read "read-only field labelled 'Department'" (AC-005.1) as an input with a label and
  `readonly`, not a `Descriptions` item. The developer's choice of a read-only antd `Input` in `FormField`
  satisfies it.
- **AC-005.7 "returned message by code".** I accept the test's use of 409 MSG48 to prove that params pass through,
  although the real list never answers MSG48. The real list's 403 MSG08 and 500 MSG06 go through the same
  `ErrorState` path, and the network test covers MSG06.
- **Admin+PM.** I took AC-005.9's "an Admin without the Project Manager role" to mean that an Admin who also holds
  the role sees the PM content (design row 2). The test pins this.
- **Mock contract fidelity.** I judged the mocks against what the Task 7 UI can send after local validation, not
  against every malformed body. INFO-2 lists what differs beyond that.
- **Open judgment call (minor): LOW-1.** Fix the order test now as a Task 6 fix, or let Task 7's developer add it
  while continuing `JiraIntegrationPage.test.tsx`? I recommend the latter: the same file is being extended now, so it
  avoids a conflicting edit with the Task 7 developer.
- **Open judgment call (minor): INFO-3.** Keep the jira-integration mock universe separate from the session fixtures,
  as designed, or align the seed with the `pm` fixture: Department "IT" (`…00d1`), with `ITSUP`/`ITNET` among the
  listed Projects? Aligning would make mock-mode demos consistent across screens, but it means reading
  `src/mocks/session.ts` ids, which the design avoided. I recommend leaving it as designed.

## Test Report

Isolated worktree at `76361628`
(`git worktree add …/scratchpad/wt-review-t6 76361628`). `frontend/node_modules` was a junction to the main tree's
folder (`cmd /c mklink /J`), so nothing was installed. All commands ran from `<worktree>/frontend` with Node
v24.22.2. The main tree, where Task 7 is being built, was not touched. At the end, the junction was removed with
`cmd /c rmdir`, the main `node_modules` was intact (261 entries), and the worktree was removed with
`git worktree remove --force`.

- `npx vitest run src/features/jira-integration`
  ```
  Test Files  2 passed (2)
       Tests  18 passed (18)
    Duration  94.70s
  ```
  The `Not implemented: Window's getComputedStyle() … pseudo-elements` lines are jsdom noise.
- `npm run typecheck` → `tsc -b --noEmit`, exit 0.
- `npm run lint` → `✖ 1 problem (0 errors, 1 warning)`, exit 0. The warning is the old
  `src/features/tickets/components/TicketListFilters.tsx:23` `react-refresh/only-export-components`.
- `npm run format:check` → `All matched files use Prettier code style!`
- Mutations: each one was applied with `sed` in the worktree, then the named test file was run. `git checkout` of
  the feature folder restored it, and `git status` was clean after each.

  | Mutation | File | Tests | Result |
  |----------|------|-------|--------|
  | M1 raw status: `cells.tag(p.status)` → `cells.text(p.status)` | `ProjectsTable.tsx` | 2 failed / 9: `AC-005.1 AC-005.2 AC-005.3 AC-005.5 …`, `AC-005.4 …` | killed |
  | M2 Admin+PM gets the placeholder: `if (!hasRole('PROJECT_MANAGER') \|\| hasRole('ADMIN'))` | `JiraIntegrationPage.tsx` | 1 failed / 9: `AC-005.9 shows the Projects to a user who is both Admin and Project Manager` | killed |
  | M3 empty list through `DataTable` (headers shown with MSG04 inside) | `DepartmentProjects.tsx` | 1 failed / 9: `AC-005.8 …` | killed |
  | M4 `resetMockData` keeps the new-id counter | `mocks/handlers.ts` | 1 failed / 9: `resetMockData restores the seeded ids, settings and counts` | killed |
  | M5 `resetMockData` drops new rows but keeps the mutated OLD objects | `mocks/handlers.ts` | 1 failed / 9: the same reset test | killed |
  | M6 client-side sort by key: `rows={[...rows].sort(…)}` | `ProjectsTable.tsx` | 9 passed / 9 | **survived** (LOW-1) |

  The developer's own mutation, which dropped the `hasRole` gate, failed the AC-005.9 Admin-only test (session log).
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-005`: 10/10 (above), exit 0.
- Full suite, build and `generate:api`: I did not rerun them. The session log and the main session report
  `Test Files 88 passed (88)`, `Tests 1517 passed (1517)`, build exit 0, and no `generate:api` diff at `22068e4d`.
  This commit changes no shared file, contract or generated file, so the feature run above covers what changed.
