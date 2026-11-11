# Task 2: New Department on Workspace Settings

**Module:** workspace
**Assignee:** Claude (Developer role, supervising Codex CLI 0.167.0)
**Status:** in-progress (tracking is moved by the main session)

---

## Session 2026-11-11

### Working on: create-department-screen (KAN-120, STORY-002)

**Who wrote what.** Codex wrote the code in three runs (`codex exec -s workspace-write --ephemeral`, prompts `codex-t2-step1.md` to `codex-t2-step3.md` in the scratchpad): step 1, the test file only; step 2, `useAddDepartment`, `NewDepartmentModal` and the page; step 3, the MSW POST handler. Claude reviewed each diff against every AC of STORY-002, made two small edits (below), ran every check and wrote this log. Codex was not blocked by its sandbox.

**Test-first evidence.** After step 1, Claude ran `npx vitest run src/features/workspace/pages/WorkspaceSettingsPage.test.tsx` on the placeholder page: 16 of 17 failed on assertions (`Unable to find role="table" and name "Departments"`, `Unable to find role="button" and name "New Department"`, `Unable to find role="alert"`), none on imports or syntax. AC-002.13 passed already, because the Admin route guard in `routes.ts` existed before this task; the test pins it.

**Files**
- New: `frontend/src/features/workspace/components/NewDepartmentModal.tsx`, `frontend/src/features/workspace/hooks/useDepartmentMutations.ts` (`useAddDepartment`), `frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx` (17 tests).
- Modified: `frontend/src/features/workspace/pages/WorkspaceSettingsPage.tsx`, `frontend/src/features/workspace/mocks/handlers.ts`.
- Untouched, as the task says: `routes.ts`, `src/mocks/handlers.ts`, `src/shared/`, backend.

**Decisions**
- The modal copies `RejectTicketModal`: a ref guard against a second submit, `onCancel` ignored while a save runs (AC-002.14), `applyApiErrors` for MSG43 on `name`, otherwise `showMessage(error.code, error.params)` (AC-002.11). `FormField` has `validateFirst`, so for a blank name over 50 characters only MSG01 shows (blank wins over length).
- The hook copies `useAddUser`: `handlesOwnErrors`, awaits the invalidation of `getGetUserDepartmentsQueryKey()` (the query the Add User form also reads), then MSG05 (AC-002.9).
- The page: the "New Department" button is outside the loading and error branches; `LoadingState` while `isPending`; `DataTable` "Departments" with one Name column, `error` and `onRetry` from the query, no paging and no `loading` prop.
- The MSW handler validates as the contract does (MSG01, MSG02 as received, MSG43 ignoring case against names created in the mock session) and answers 201 with the stripped name. It imports no other feature's mock data; a comment names Interim limit 4.
- Claude's edits: renamed the page state `open` to `isModalOpen` (guideline 03 and 05: booleans take `is`); in the mock, replaced Codex's `catch` that answered an unreadable body with MSG03 (the email message) with `request.json().catch(() => null)`, so it falls into MSG01.

**Deviations**
- none from the plan. The plan names the hook `useAddDepartment` (design) in `useDepartmentMutations.ts` (tasks); both kept.

### Verify (frontend/, Node 24.22.2)

| Command | Result |
|---|---|
| `npm run format:check` | exit 0 |
| `npm run lint` | exit 0 (1 existing warning in `tickets/components/TicketListFilters.tsx`, not ours) |
| `npm run typecheck` | exit 0 |
| `npm run test` | exit 0, **87 files, 1509/1509 passed** (`WorkspaceSettingsPage.test.tsx` 17/17) |
| `npm run build` | exit 0 |
| `npm run generate:api` | exit 0; `git status src/shared/api/generated` shows no change |
| `bun harnessclaude/hooks/sdd.ts trace workspace STORY-002` | exit 0, 14/14 |

Backend untouched; `mvn` not run.

Trace output:

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

Not verified: the screen in a browser (mock mode or real backend). The real backend needs the CSRF header and KAN-275 (Interim limits 2 and 3).

### Proposed commit message

```
feat(workspace): add New Department to Workspace Settings (KAN-120)

STORY-002

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

Nothing staged or committed.

### Blockers
none

### Next up
Review, then commit after the user agrees.

## Session 2026-11-11 (main session, after review attempt 1 passed)

- `useDepartmentMutations.ts`: MSG05 is shown before the Departments query is refetched, and the hook returns the invalidation promise (review issue 3, guideline 06 §3, D29).
- Review issue 5 logged as BUG-201 in `docs/bug-log.md` (shared/, FE Lead), not fixed here.
- Re-run: format:check, lint, typecheck ok; tests 1509/1509; build ok; generate:api unchanged; trace STORY-002 14/14.
- A browser check in mock mode was not possible: the built-in browser pane could not register the MSW Service Worker.

## Commit

- default@5d06062 `feat(workspace): add New Department to Workspace Settings (KAN-120)`, after review attempt 1 passed and the main session re-ran the frontend checks.
