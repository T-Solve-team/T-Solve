# Test Report: workspace — Task 2: create-department-screen

## Attempt 1
**Date:** 2026-11-11
**State tested:** branch `feat/KAN-120-workspace-create-department`, base `b09e8505`, change uncommitted (working tree and untracked files under `frontend/src/features/workspace/`; `docs/diagrams/`, `RESEARCH-003`, `schema-ddl-alignment.md` and `harnessclaude/` logs excluded)
**Iteration:** 1 · **Attempt:** 1 · **Reviewer:** harnessclaude-reviewer (independent run)

## Commands

- Frontend, from `frontend/`, Node v24.22.2 (`PATH` prefixed with `/c/Users/admin/AppData/Local/nvm/v24.22.2`): `npm run format:check`, `lint`, `typecheck`, `test`, `build`, then `generate:api` and `git status --short -- src/shared/api/generated openapi`
- Focused re-run: `npx vitest run src/features/workspace --reporter=verbose`
- Repo root: `bun harnessclaude/hooks/sdd.ts trace workspace STORY-002`
- Backend not run (frontend-only task, by the dispatcher's scope)

## Results

| Check | Result |
|-------|--------|
| `format:check` | pass: "All matched files use Prettier code style!" |
| `lint` | pass: 0 errors, 1 warning in `src/features/tickets/components/TicketListFilters.tsx:23` (`react-refresh/only-export-components`). That file is not part of this change |
| `typecheck` | pass (`tsc -b --noEmit`) |
| `test` | pass: 87 files, 1509 tests, 0 failed (201.5 s) |
| `build` | pass (built in 13.4 s) |
| `generate:api` | exit 0. `src/shared/api/generated` and `openapi/` unchanged afterwards |
| `sdd.ts trace workspace STORY-002` | 14/14 acceptance criteria have a test |

### Workspace tests (verbose, 18/18 pass)

| Test | Time |
|------|------|
| AC-002.1 shows New Department and the Active Departments in a Name-only table | 725 ms |
| AC-002.2 shows Loading ... in place of the table and keeps New Department | 163 ms |
| AC-002.3 shows MSG06 with retry instead of the table and keeps New Department | 335 ms |
| AC-002.4 shows MSG04 inside the table when there are no Active Departments | 169 ms |
| AC-002.5 opens New Department with an empty required name, Save and Cancel | 396 ms |
| AC-002.6 shows MSG01 below empty or whitespace-only names without a POST | 857 ms |
| AC-002.7 shows MSG02 below a 51-character name and keeps every character without a POST | 656 ms |
| AC-002.8 sends exactly the trimmed name in the POST body | 736 ms |
| AC-002.8 sends only one POST after repeated Save clicks while the request is pending | 874 ms |
| AC-002.9 closes on 201, shows MSG05 and refreshes the shared Departments query and table | 957 ms |
| AC-002.10 shows MSG43 below the name on 409 and keeps the popup and typed name | 702 ms |
| AC-002.11 shows 403 MSG08 and keeps the popup and typed name | 659 ms |
| AC-002.11 shows 500 MSG06 and keeps the popup and typed name | 682 ms |
| AC-002.12 closes with Cancel without a POST and reopens with an empty name | 904 ms |
| AC-002.12 closes with Close without a POST and reopens with an empty name | 955 ms |
| AC-002.13 shows MSG08 and no New Department button for an Employee | 109 ms |
| AC-002.14 ignores Cancel and Close while saving and closes after success | 981 ms |
| AC-001.16 (task 1) labels the DEPARTMENT_CHANGE action "Department change" on the Audit Log screen | 1272 ms |

## Mutation probe (AC-002.14)

Run in a throw-away copy of `frontend/` in the session scratchpad (`node_modules` linked by a junction, removed afterwards; the repo was not touched). In the copy, `NewDepartmentModal.tsx:48` was changed from `if (!submitting.current) onClose();` to `onClose();`.

| Probe | Result |
|-------|--------|
| The AC-002.14 test as written, guard removed | passes |
| Same, with the test changed to expect the popup to close after Cancel mid-save | fails: the popup never starts closing |
| Same, with Close instead of Cancel | fails the same way |

Antd 6 `Modal.handleCancel` (`node_modules/antd/es/modal/Modal.js:113-114`) returns early while `confirmLoading` is set, and `FormModal` sets `confirmLoading={isSubmitting}`. So the popup ignores Cancel and Close mid-save even without the component's own guard. The AC-002.14 test checks behaviour the user sees, and that behaviour holds.

## Coverage

No coverage run (not set up for this check). Every one of AC-002.1 to AC-002.14 is proven by at least one named test. Gaps that are not ACs are listed in the review (issue 6).

Defects found by tests: none.
