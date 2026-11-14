# Task 4: Rename on 2.3 Department Detail

**Module:** workspace
**Assignee:** Claude (Developer role, supervising Codex CLI 0.167.0)
**Status:** in-progress (tracking is moved by the main session)

---

## Session 2026-11-12

### Working on: rename-department-screen (KAN-121, STORY-004)

**Who wrote what.**
- Claude Sonnet (stopped mid-task, decision D22) wrote the first version of all seven files: the 2.2 link, `DepartmentDetailPage`, `DepartmentNameForm`, `useRenameDepartment`, the seeded MSW list with GET and PUT, the AC-004.1 test and `DepartmentDetailPage.test.tsx`. It left no log and ran no checks.
- Codex wrote one run (`codex exec -s workspace-write --ephemeral`, prompt `kan121-t4-step1.txt` in the scratchpad): it reviewed Sonnet's work against STORY-004, the design and guidelines 05, 06, 09 and 10. It kept the page, hook, link and mocks. It changed `DepartmentNameForm.tsx` (form `initialValues` and `clearOnDestroy` in place of `setFieldsValue` before the form mounts, Save disabled while pending, `submitting` renamed `isSubmitting`, a `handleCancel` function) and strengthened `DepartmentDetailPage.test.tsx`. The strengthened tests: AC-004.2 for both seeded ids, AC-004.3 waits for the held GET, field descriptions for MSG01 and MSG02, AC-004.8 with a held PUT and a third click on the disabled Save, AC-004.9 checking that the form closes only after the refetch and that every cached query is invalidated, a second AC-004.12 case (no stale MSG43 after Cancel), AC-004.13 with a held PUT that is released, and a "Department mock handlers" block for the MSW rules.
- Claude (this session) reviewed both versions against every AC, made no code edits, produced the red evidence, ran every check and wrote this log.

**Test-first evidence.** The tests came after Sonnet's code, so Claude proved them in two ways and restored the production files byte for byte each time (`sha256sum -c` OK on all five files).
1. Pre-task production code. Claude put the HEAD versions of `WorkspaceSettingsPage.tsx`, `DepartmentDetailPage.tsx` (the placeholder), `useDepartmentMutations.ts` and `mocks/handlers.ts` in place and ran `npx vitest run src/features/workspace/pages`: 25 of 44 failed, every one on an assertion (15 `Unable to find role="button" and name "Edit"`, `Unable to find role="link" and name "IT"`, MSG04 and MSG06 texts not found, 5 `promise resolved instead of rejecting` in the mock block). None failed on imports. AC-004.14 passed already, because the Admin route guard in `routes.ts` existed before this task; the test pins it (same as AC-002.13 in Task 2).
2. Targeted mutations, one at a time, each restored before the next:

| Mutation | Failing test |
|---|---|
| no `trim()` in the PUT body | AC-004.8 |
| no `isSubmitting` guard in Cancel | AC-004.13 |
| no `resetFields` and no `clearOnDestroy` | both AC-004.12 cases |
| `invalidateQueries()` removed from `useRenameDepartment` | AC-004.9 |
| `showMessage('MSG05')` removed from `useRenameDepartment` | AC-004.9 (`Unable to find ... Changes saved successfully.`) |
| `applyApiErrors` skipped (toast only) | AC-004.10 |

**Files**
- New: `frontend/src/features/workspace/components/DepartmentNameForm.tsx`, `frontend/src/features/workspace/pages/DepartmentDetailPage.test.tsx` (25 tests).
- Modified: `frontend/src/features/workspace/pages/WorkspaceSettingsPage.tsx`, `frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx` (+ AC-004.1), `frontend/src/features/workspace/pages/DepartmentDetailPage.tsx`, `frontend/src/features/workspace/hooks/useDepartmentMutations.ts`, `frontend/src/features/workspace/mocks/handlers.ts`.
- Untouched, as the task says: `routes.ts`, `src/mocks/handlers.ts`, `src/shared/`, backend. All files are inside `module.md` `paths`.

**Decisions**
- The form takes the current name from `initialValues` and is destroyed with `clearOnDestroy`, so every Edit starts from the stored name (AC-004.12) and no `setFieldsValue` runs on an unmounted form.
- Save is `loading` and `disabled` while the PUT runs, on top of the `isSubmitting` ref (AC-004.8). Cancel returns early while `isSubmitting.current` (AC-004.13).
- `useRenameDepartment` shows MSG05 and returns `client.invalidateQueries()`, so the per-call `onSuccess` (close the form) runs after the refetch; the stored name shows as soon as the form closes (AC-004.9, K121-D14).
- The seeded IT and HR names do not collide with the existing 2.2 tests: they create "IT Support" or override the POST.

**Deviations**
- none from the plan. The extra "Department mock handlers" tests are not in tasks.md; they pin the MSW rules the design gives (K121-D18) and carry no AC id.

**Bugs** none found; nothing added to `docs/bug-log.md`.

### Verify (frontend/, Node 24.22.2, run one after another)

| Command | Result |
|---|---|
| `npm run format:check` | exit 0 |
| `npm run lint` | exit 0 (1 existing warning in `tickets/components/TicketListFilters.tsx`, not ours) |
| `npm run typecheck` | exit 0 |
| `npm run test` | exit 0, **89 files, 1544/1544 passed**, no "Hook timed out" |
| `npm run build` | exit 0 |
| `npm run generate:api` | exit 0; `git status src/shared/api/generated` shows no change |
| `bun harnessclaude/hooks/sdd.ts trace workspace STORY-004` | exit 0, 14/14 |

Backend untouched; `mvn` not run.

Trace output:

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

Not verified: the screen in a browser (mock mode or real backend). The real backend needs the CSRF header and KAN-275 (Interim limits 2 and 3).

### Proposed commit message

```
feat(workspace): rename a Department on Department Detail (KAN-121)

STORY-004

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

Nothing staged or committed.

### Blockers
none

### Next up
Review, then commit after the user agrees.

## Review fixes (2026-11-12, after review attempt 1 passed)

Decision D24: fix review issues 2 and 3 only (`06-evaluation/workspace/task-4-rename-department-screen-review.md`); issues 1, 4, 5 and 6 are left as D24 says.

**Who wrote what.** Codex, one run (prompt `kan121-t4-step2.txt` in the scratchpad), tests first. Claude reviewed the diff, made no edits, re-ran the red check and every check.

**Changes**
- Issue 2: `useRenameDepartment` writes the PUT's 200 body into `getGetDepartmentQueryKey(departmentId)` before MSG05 and the invalidation. `DepartmentDetailPage` shows ErrorState only when `isError && !data`, so a failed refetch after a save keeps the saved name.
- Issue 3: the "Department name" input has `autoFocus`. `DepartmentNameForm` keeps a ref on Edit and gives it focus again after Cancel or a successful Save. On first load focus is not moved.
- Tests added to `DepartmentDetailPage.test.tsx`: `AC-004.5 Edit focuses the name field without moving focus on first load`, `AC-004.9 keeps the saved name when the refetch after a save fails`, `AC-004.12 Cancel returns focus to Edit`, and an Edit-focus assertion after Save in the existing AC-004.9 test.

**Red evidence.** Claude put the pre-fix versions of the three production files back and ran the test file: 4 failed, 24 passed. The failures were the three focus tests (`toHaveFocus`, focus on `<body>`) and the failed-refetch test (saved name not found). Then Claude restored the files byte for byte (`sha256sum -c` OK).

**Verify** (frontend/, Node 24.22.2, run one after another)

| Command | Result |
|---|---|
| `npm run format:check` | exit 0 |
| `npm run lint` | exit 0 (1 existing warning in `tickets/components/TicketListFilters.tsx`) |
| `npm run typecheck` | exit 0 |
| `npm run test` | exit 0, **89 files, 1547/1547 passed** |
| `npm run build` | exit 0 |
| `npm run generate:api` | exit 0; no change in `src/shared/api/generated` |
| `bun harnessclaude/hooks/sdd.ts trace workspace STORY-004` | exit 0, 14/14 |

Nothing staged or committed; no status changed.
