# Task 6: Show Department and Projects on Jira Integration (KAN-127)

**Module:** project-integration
**Assignee:** Đặng Tấn Bình (Developer: Claude, D49, no Codex)
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-13

### Working on: jira-integration-projects-table (STORY-005)

Branch `feat/KAN-127-project-integration-add-project`, from HEAD `88bb8329`. Screen 3.1 now shows a Project Manager
the read-only Department and the Projects table from `GET /api/v1/projects`. An Admin-only session keeps the
placeholder and never mounts the query.

- `JiraIntegrationPage`: `useSession().hasRole('PROJECT_MANAGER')` decides. Without that role the page renders the
  existing `ScreenPlaceholder` and does not mount `DepartmentProjects`, so no Projects request is sent (AC-005.9).
  An Admin who is also a Project Manager gets the PM content. Other roles never reach the page: the existing route
  guard shows MSG08 (AC-005.10). No change to routes, `app/`, `shared/` or the registration files.
- `DepartmentProjects`: calls `useProjects()`. Pending → `LoadingState`; error → `ErrorState` with
  `onRetry={() => void refetch()}`, which shows the error's code and params (AC-005.6, AC-005.7). Data → a read-only
  antd `Input` labelled "Department" (`FormField` in `AppForm`, `htmlFor`/`id` so the label names it), then
  `EmptyState` (MSG04) when `projects` is empty (AC-005.8), else `ProjectsTable`. Task 7 adds the Add project button
  here.
- `ProjectsTable`: kit `DataTable` with five columns, in this order: "Project ID" (public id), "Jira project key",
  "Project Manager" (`fullName`, else `msg('MSG54')`), "Members" (`memberCount`), "Status" (`cells.tag` →
  `StatusTag`: Active / Removed). Row key `projectId`; no paging, no sorting: the rows keep the server's order
  (AC-005.2–AC-005.5, D26).
- `useProjects`: a thin wrapper around the generated `useListProjects` (the task names the hook file). Query errors
  are left to the screen, as the global handler does for every query.
- `mocks/handlers.ts`: stateful handlers for all three operations, typed with the generated `ProjectList`,
  `ProjectConnectionTest` and `Project`. Seed: Department IT Operations; `OPS` Active and `OLD` Removed (both
  managed by the "pm" fixture user Đỗ Minh Quân, so the default mock user reconnects OLD); `SUP` Active in Customer
  Support (only in the conflict store, never listed). The mock checks requests in the backend's way: PM role (401/403
  MSG08); unreadable body → 400 MSG06; key blank → MSG01, more than 20 UTF-16 units → MSG02 (field
  `jiraProjectKey`); strip + upper-case; format `[A-Z][A-Z0-9_]*` → 409 MSG48; an Active key anywhere, a Removed key
  of another Department, or a Removed key with another Active manager → 409 MSG49 on `jiraProjectKey` with
  `tracker_name`/`department_name`; `MISSING` → 409 MSG48 (Jira "not found"). The test returns four statuses (two
  Done-category, two not), `reconnect` with `projectId` for OLD, prefilled ids (stored ids still in Jira, else the
  Done defaults) and range (stored, else 3). Save checks ids/range (MSG01 / MSG44 / MSG06 like AC-002.4–AC-002.7 and
  AC-002.16), deduplicates ids in first-seen order, refuses ids Jira does not have, then answers 201 with a new
  Project (caller as PM, 1 member) or 200 for OLD (same id and member count). `resetMockData()` rebuilds the store
  from a seed function that returns fresh objects and resets the id counter.

**Who wrote what.** Claude wrote all code and tests (Codex is out of quota, D49).

**Red step.** Only the `MOCK_PROJECT_IDS` constant was added first, so the tests could import it. Run: 16 of 18
failed on assertions. The handler tests got the generated faker data: a random Department name, no 401/403, 200
instead of 201, no rejections. The page tests found no `OLD` row, no loading view and no alert. The two tests that
pin behaviour that already existed passed before the change: the Admin-only placeholder (AC-005.9) and the
Employee MSG08 page (AC-005.10). A mutation check then removed the `hasRole` gate, and the AC-005.9 placeholder test
failed (1 of 9 page tests). The gate was then restored. A first green run caught one real gap: `DataTable` shows MSG04
inside an empty table with headers, while AC-005.8 asks for MSG04 in place of the table, so `DepartmentProjects`
renders `EmptyState` itself.

### Decisions
- **No Add project button in this task.** The task says no mutation UI. AC-005.9 still asserts that the button is
  absent for an Admin-only session, so that test keeps its meaning once Task 7 adds the button.
- **Fixture tests carry no AC-006 ids.** The task ties the Test/Save fixtures to AC-006.15–AC-006.17. If those ids
  were in the names, `sdd.ts trace project-integration STORY-006` would count popup criteria as covered by
  mock-contract tests. That is the false coverage D48 corrected for the backend. They are named `Add project fixture:
  …`; the popup tests in Task 7 own the AC-006 ids. The two list contract tests keep AC-005.1, which the page test
  covers too.
- **Department field.** A read-only `Input` in a `FormField`, labelled "Department", not `Descriptions`: the spec
  says "read-only field". The test checks the value and the `readonly` attribute.
- **Error test code.** AC-005.7 is tested with a 409 MSG48 that has params, to prove params pass through, plus a
  network failure → MSG06. A real list does not answer MSG48; the test comment says so.
- **Loading test.** It waits until the held list request has started before it checks "Loading ...". Without that,
  the route's own loading view could satisfy the check.
- **Mock simplifications.** The mock places every PM caller in IT Operations (the session fixtures say "IT"; the
  design names the seed IT Operations). The order of the Save checks is close to the backend's but not exact when
  several fields are wrong at once (the mock checks settings before the key format). Canonical aliases are not
  mocked: mock Jira returns the normalised key.

### Tests added (name → AC)
`pages/JiraIntegrationPage.test.tsx` (renderApp, MSW):
- `AC-005.1 AC-005.2 AC-005.3 AC-005.5 loads the Projects and shows the read-only Department and the table in the
  returned order`: one GET; Department value and `readonly`; exact headings in order; rows OLD then OPS; every cell
  of both rows (public id, key, fullName, count, Active/Removed); SUP absent.
- `AC-005.4 shows MSG54 when a Project has no Project Manager` (row 30 through the UI).
- `AC-005.6 shows the loading state in place of the table while the first request runs` (row 15).
- `AC-005.7 shows the returned message with its params and retries the request`: Try again → table, 2 requests
  (row 21).
- `AC-005.7 shows MSG06 when the request cannot reach the server` (row 21).
- `AC-005.8 shows MSG04 in place of the table when the Department has no Projects` (row 15).
- `AC-005.9 keeps the placeholder for an Admin without the Project Manager role, with no request and no Add project`
  (row 2).
- `AC-005.9 shows the Projects to a user who is both Admin and Project Manager` (`/me` overridden; row 2).
- `AC-005.10 shows the route guard MSG08 page to an Employee without a Projects request`.

`mocks/handlers.test.ts` (generated client functions against the mocks):
- `AC-005.1 lists only the own Department Projects, sorted by key, with typed rows`
- `AC-005.1 refuses a signed-out caller and a caller without the Project Manager role`
- `Add project fixture:` new padded lowercase key test (canonical key, Done defaults, range 3, no `projectId`, a
  non-Done status); new Save 201 then GET shows it with 1 member and deduplicated ids; OLD test prefill (kept id
  `10003`, range 7) then Save 200 keeping id and member count (row 19/37); MSG49 for SUP and OPS on both operations
  (row 36); MSG48 without a field for MISSING and `1BAD` on both; field errors MSG01/MSG02/MSG01/MSG44 including 0,
  −1, 2.5 and max+1; `resetMockData` restores rows, OLD settings, counts and the next new id.

### Files
- Modify: `frontend/src/features/jira-integration/pages/JiraIntegrationPage.tsx` (14 lines),
  `mocks/handlers.ts` (276 lines, was an empty stub)
- Create: `frontend/src/features/jira-integration/components/DepartmentProjects.tsx` (24),
  `components/ProjectsTable.tsx` (35), `hooks/useProjects.ts` (6)
- Test (create): `frontend/src/features/jira-integration/pages/JiraIntegrationPage.test.tsx` (9 tests),
  `mocks/handlers.test.ts` (9 tests)
- Unchanged: `src/app/`, `src/shared/` (catalog included), routes, `index.ts`, registration files, the contract and
  the generated client. No new dependency.

### Verify
Frontend, from `frontend/` (Node 24.22.2):
- `npm run format`, then `npm run format:check` → `All matched files use Prettier code style!`
- `npm run lint` → `✖ 1 problem (0 errors, 1 warning)`: the warning in `src/features/tickets/components/TicketListFilters.tsx`
  was there before (react-refresh/only-export-components). A first run found one error in the new handler test
  (`expect.any` is `any`); it now uses the literal id.
- `npm run typecheck` → `tsc -b --noEmit`, exit 0.
- `npm run test` → `Test Files 88 passed (88)`, `Tests 1517 passed (1517)`. The fixture tests were renamed after this
  run, with no change to what they test; `npx vitest run src/features/jira-integration` after the rename →
  `Test Files 2 passed (2)`, `Tests 18 passed (18)`, and `format:check` still clean.
- `npm run build` → `✓ built in 16.88s`, exit 0.
- `npm run generate:api` → no diff in `src/shared/api/generated` (and none in `openapi/`).

Harness, from the repo root:
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-005` →
  `trace project-integration (STORY-005): 10/10 acceptance criteria have a test` (exit 0).

Secrets: `gitleaks detect --no-git --redact --no-banner --source <file>` on each of the 7 changed or new files →
`no leaks found` for every file.

### Deviations
- AC-006 ids are left out of the fixture test names (see Decisions). The task's Tests line ties the fixtures to
  AC-006.15–AC-006.17, but only for the record; Task 7's popup tests cover those criteria.

### Bugs
None found.

### Blockers
none

### Next up
Review and commit after the user agrees; then Task 7 (Test and add or reconnect a Project in the popup).

### Commit
default@22068e4d — `feat(project-integration): show the Department and its Projects on Jira Integration (KAN-127)`, committed after the user agreed (13/11).

### Full checks (main session)
- format:check, lint (1 pre-existing warning in `TicketListFilters.tsx`), typecheck, build: exit 0. `npm run test` → `Test Files 88 passed (88)`, `Tests 1517 passed (1517)`. `generate:api` → no diff.
