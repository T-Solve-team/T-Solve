---
module: project-integration
feature: project-member-remove-api, project-member-remove-tab
task: 3, 4
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: project-integration — Tasks 3 and 4: Remove a member (KAN-129, STORY-003, AC-001.7)

## Summary
Reviewed the uncommitted change on `feat/KAN-129-project-integration-remove-member` (`git diff` plus the untracked `frontend/src/features/jira-integration/hooks/useRemoveMemberFromProject.ts`) against the spec only. Every criterion of STORY-003 and the reworded AC-001.7 is implemented and has a named test that really checks it. No critical finding. One important finding (stale Add-member candidates after a remove) and four minor ones; none blocks.

The verdict is **pass on the evidence below**. It depends on the controller's full frontend verify, which I did not complete (see Test Report).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | See the Stage 1 table. |
| Completeness | pass | 10/10 criteria of STORY-003 and AC-001.7 have code and a test. Nothing extra: no new table, no migration, no new audit action, no Solution Form change (Out of Scope). |
| Correctness | pass | Reviewer run: `npx vitest run src/features/jira-integration` 22/22; `prettier --check` and `eslint` on the changed frontend files exit 0. Controller run: backend `mvn clean verify` green (1497 unit, 506 IT, `ProjectMembersIT` 23/23). No critical finding. |
| Integration | pass | `DELETE /api/v1/projects/{projectId}/members/{userId}` → 200 `ProjectMemberResponse[]` matches the spec's Interface Contracts in the controller, the YAML, the generated client and the MSW handler. CORS allows DELETE (`WebConfig.java:21`). Nothing references `project_membership` by foreign key, so the delete cannot cascade. |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-003.1 delete the MEMBER row, 200 with the full list in AC-001.2 order | PASS | `ProjectMemberRepository.java:133`, `ProjectMemberServiceImpl.java:93-101`, `ProjectController.java:81-90`. `ProjectMembersIT.java:388` checks 200, the order `PM, ALICE, NAMELESS_MEMBER`, the Project Manager first, and that the row is gone from the database. |
| 2 | AC-003.2 one audit entry `{"removed": [id]}`, same transaction | PASS | The audit call sits inside the `@Transactional` method after the delete (`ProjectMemberServiceImpl.java:99`); `AuditServiceImpl` refuses to record outside a transaction and uses the same tenant connection. `ProjectMembersIT.java:401` checks exactly one entry: action, target type, actor, `removed = [ALICE]`, no `added` key. The unit test checks the order delete → audit → list. |
| 3 | AC-003.3 no MEMBER row → 409, nothing deleted, no audit | PASS | The delete filters on `role_in_project = 'MEMBER'`; 0 rows → `BusinessRuleException` MSG06. `ProjectMembersIT.java:418` covers all three cases of the criterion plus an unknown id: already removed (BOB twice), the Project Manager's own row, a non-member (CAROL), a random id. It checks 409 + MSG06, the member count (the Project Manager's row is still there) and that no audit entry was added. |
| 4 | AC-003.4 Removed Project → 409, nothing deleted, no audit | PASS | Status check before the delete (`ProjectMemberServiceImpl.java:96`). `ProjectMembersIT.java:432` gives the Removed Project a real MEMBER row first, so the 409 comes from the status and not from "no row"; the unit test checks the delete is never called. |
| 5 | AC-003.5 tickets and Solutions kept | PASS | No code touches `ticket`. `ProjectMembersIT.java:442` inserts two tickets written by the member (a `SOLUTION_FORM` one in `PENDING_REVIEW`, a `CSV` one `PUBLISHED`), removes the member and compares `SELECT *` of both rows before and after. The test would fail on a cascade or an update of any column. I also checked the changelogs: no foreign key references `project_membership`. |
| 6 | AC-003.6 Remove on every MEMBER row while Active, none while Removed | PASS | `MembersTable.tsx:36-56`. `ProjectDetailPage.test.tsx:98` and `:104`. The Removed test serves a MEMBER row for the Removed Project first, so it is not passing only because the list has no member. |
| 7 | AC-003.7 MSG55 with full name (else email) and Jira key, no request yet | PASS | `MembersTable.tsx:20`, `:61-67`. `ProjectDetailPage.test.tsx:113` (name, key, zero DELETE requests recorded) and `:122` (email when the name is null). |
| 8 | AC-003.8 confirm + 200 → MSG05 and the returned list | PASS | `useRemoveMemberFromProject.ts:12-15` (`setQueryData` with the response). `ProjectDetailPage.test.tsx:130` checks MSG05, the row disappears, and exactly one DELETE to the right path. |
| 9 | AC-003.9 failure → MSG06, list unchanged | PASS | `useRemoveMemberFromProject.ts:16`, `handlesOwnErrors` so there is no second global toast. `ProjectDetailPage.test.tsx:143` (409 → MSG06, row still shown). |
| 10 | AC-003.10 Cancel → no request, nothing changed | PASS | `MembersTable.tsx:78`. `ProjectDetailPage.test.tsx:153`. |
| 11 | AC-001.7 no Remove button on the Project Manager's row | PASS | `MembersTable.tsx:44` renders the button only for `role === 'MEMBER'`. `ProjectDetailPage.test.tsx:92` checks the manager's row holds no button at all. The backend refuses that row too (row 3). |
| 12 | AC-001.3 / AC-001.4 on the new endpoint | PASS | `requireManagedProject` runs first: 404 for an unknown Project, then 403 for a caller who is not its manager, then the status check. `@PreAuthorize` requires the `PROJECT_MANAGER` role. `ProjectMembersIT.java:208` (404) and `expectForbiddenEverywhere` (`:474`: member of the Project, manager of another Project, deactivated manager, manager without the role, caller without the role → 403, member count and audit unchanged). |

### Focus points asked by the dispatcher

- **Transaction and audit:** correct (rows 2–4). No audit on any refused path.
- **404 before 403, manager check:** correct (row 12).
- **Concurrency E13:** two removes of the same member are one `DELETE … AND role_in_project = 'MEMBER'` each. Under READ COMMITTED the second waits on the row lock, re-checks, finds no row, deletes 0 → 409 and no audit. The test is sequential, not concurrent; the single statement makes that enough.
- **Concurrency E14:** identity's `UserRoleUpdateRepository.removeMembers` deletes the same rows with its own single statement. Whichever commits first wins; the other deletes 0 rows. This side takes no `app_user` lock, so there is no lock-order deadlock with identity's `FOR UPDATE`.
- **Project Manager's row:** cannot be removed by this endpoint (SQL filter), tested.
- **OpenAPI vs controller:** same path, method, tag, `operationId`, two required uuid path parameters, 200 array of `ProjectMemberResponse`, 403/404/409. Compared by meaning, not byte for byte with springdoc output (same basis as the Task 2 review).
- **Known deviation 1 — hook file name `useRemoveMemberFromProject.ts`:** accepted. It is not a deviation: guideline 03 says a file exporting one hook has the hook's name, `tasks.md` names this file, it mirrors `useAddMembersToProject.ts`, and the generated hook already owns the name `useRemoveProjectMember`.
- **Known deviation 2 — bare `$ref: Problem` responses and an added 401:** accepted. Every operation of the `projects` tag in the YAML, and the file as a whole (58 `'401'` entries), is written this way. The backend really answers 401 to an unauthenticated call. The controller declares only 403/404/409, which is what `backend/AGENTS.md` §6 asks for.
- **Over-engineering:** none. One repository method, one service method, one endpoint, one hook, the existing `ConfirmDialog`. The table follows `DraftsList` (empty-title actions column, per-row `aria-label`, `toRemove` state).
- **Accessibility:** each Remove button has its own accessible name (`Remove <name or email>`); the dialog is the kit's antd modal (focus trap, Escape cancels).

## Traceability
```
✓ AC-003.1  backend/.../ProjectMemberServiceImplTest.java, backend/.../ProjectServiceImplTest.java, backend/.../ProjectMembersIT.java
✓ AC-003.2  backend/.../ProjectMemberServiceImplTest.java, backend/.../ProjectMembersIT.java
✓ AC-003.3  backend/.../ProjectMemberServiceImplTest.java, backend/.../ProjectMembersIT.java
✓ AC-003.4  backend/.../ProjectMemberServiceImplTest.java, backend/.../ProjectMembersIT.java
✓ AC-003.5  backend/.../ProjectMembersIT.java
✓ AC-003.6  frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx
✓ AC-003.7  frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx
✓ AC-003.8  frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx, backend/.../ProjectServiceImplTest.java
✓ AC-003.9  frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx
✓ AC-003.10 frontend/src/features/jira-integration/pages/ProjectDetailPage.test.tsx
trace project-integration (STORY-003): 10/10 acceptance criteria have a test
trace project-integration: 33/33 acceptance criteria have a test
```
I read every listed test. `ProjectServiceImplTest.java` is a false match (finding 4); the other files prove the two criteria without it.

## Issues

CRITICAL (blocks pass): none.

Informational (does not block):

1. **Important** — `frontend/src/features/jira-integration/hooks/useRemoveMemberFromProject.ts:12-15`. A remove does not invalidate the member-candidates query. The query client keeps data fresh for 30 s (`src/shared/api/query-client.ts:66`) and the Add member popup reads `useSearchProjectMemberCandidates`. So: open Add member, close it, remove a member, open Add member again within 30 s of the first load → the cached list is shown without a refetch and the person just removed is missing, although the backend would now return them (AC-002.1 / AC-002.10). Typing a search text loads fresh data, which hides the problem. The natural case is a wrong remove followed by an immediate re-add. `useAddMembersToProject.ts` already refreshes the candidates on success; the remove hook does not. I found this by reading the code; I did not reproduce it in a test.
   Fix: in `onSuccess`, also call `void client.invalidateQueries({ queryKey: getSearchProjectMemberCandidatesQueryKey(projectId) })`, with a test (remove, open the popup, expect the removed person in the list). Log it in `docs/bug-log.md` (project rule); I did not, because this review may write only this file.

2. **Minor** — `frontend/src/features/jira-integration/components/MembersTable.tsx:78`. Cancel still works while the remove request is running. The dialog closes, the request goes on, and if the user confirms a second member before the first request settles, the first request's `onSettled` (`:75`) closes the second dialog. No wrong data, only a confusing moment that needs sub-second timing. The Add member popup ignores Cancel while its request runs.
   Fix: `onCancel={() => !remove.isPending && setToRemove(null)}`.

3. **Minor** — `backend/src/main/java/vn/tsolve/project/service/impl/ProjectMemberServiceImpl.java:96`. The Removed check reads the Project's status and then deletes, with no lock on the Project row. Nothing in the backend changes a Project's status today (no writer found), so the race cannot happen yet. The add has the same shape (`:77`).
   Fix: none now. When KAN-127 adds "remove a Project", it must lock the Project row there and here (`SELECT … FOR SHARE` in `findProject` for the two writes), or AC-002.8 and AC-003.4 can be bypassed by a request that overlaps the removal.

4. **Minor (not from this change)** — `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java:26`, `:36`. Two tests of another module's criteria (tracker, KAN-22) are named `…_AC_003_1` and `…_AC_003_8` and live in the `project` package, so `sdd.ts trace project-integration` now lists them as evidence for this story's AC-003.1 and AC-003.8. The real tests exist, so the count is right today, but the trace could not show a gap if they were deleted.
   Fix: outside this change — the owner of that test settles which spec those ids belong to, or the trace learns to scope ids per module.

5. **Minor (spec text)** — `harnessclaude/artifacts/03-modules/project-integration/spec.md`. The Requirements section was not updated with STORY-003: Functional 3 and 5 and Non-Functional 1 speak only of the add, and Interim limit 2 says "a browser POST … gets 403" although the same holds for the new DELETE (the web app still sends no CSRF header). The acceptance criteria themselves are complete.
   Fix: add the remove to those lines in the next spec edit (this locks the code again until re-approval, so batch it with another spec change).

## Assumptions made
- **200 with the member list on a DELETE:** `backend/AGENTS.md` §5 lists 204 for a delete. The spec (AC-003.1, Interface Contracts) says 200 with the list, and the spec is the contract, so I did not count this as a defect.
- **Combined review file:** the dispatcher asked for one file for Tasks 3 and 4 with this name, instead of one `task-{N}-{slug}-review.md` and test report per task. The frontmatter names both feature keys.
- **AC-003.9 "keep the member list unchanged":** after a 409 because someone else already removed the member, the stale row stays until the list is fetched again. I read the criterion as asking for exactly that and did not flag it.
- **E13 tested sequentially:** I accept a sequential second remove as the test for the concurrent case, because the behaviour comes from one SQL statement.
- **OpenAPI compared by meaning:** as in the Task 2 review; a real `npm run api:pull` may differ cosmetically.
- **Story goal vs scope:** the story's goal ("stop writing Solutions") is reached only when `jira-app` (KAN-126) checks `project_membership`. The spec puts that out of scope by a user decision of 14/11; this change only removes the row.
- **Feature statuses not changed:** this was a read-only review; the controller sets `project-member-remove-api` and `project-member-remove-tab` with `sdd.ts feature`.

## Test Report
Run by the reviewer:
- `npx vitest run src/features/jira-integration` (in `frontend/`): 3 files, 22/22 passed.
- `npx prettier --check` on `openapi/tsolve-api.yaml`, `src/features/jira-integration`, `src/shared/api/generated/projects`: clean.
- `npx eslint src/features/jira-integration`: exit 0.
- `bun harnessclaude/hooks/sdd.ts trace project-integration STORY-003`: 10/10; whole module 33/33.

Reported by the controller, not re-run: backend `mvn clean verify` green (1497 unit, 506 IT, `ProjectMembersIT` 23/23).

Not verified by the reviewer:
- The full frontend suite. I started it; the run ended with the session before it printed a result, and the controller then took the run over. No result from my run is used here.
- `npm run typecheck`, `npm run build`, and "`npm run generate:api` leaves no diff". The last one rewrites `src/shared/api/generated`, which a read-only review must not do. The generated diff I read has the same shape as the existing `addProjectMembers` code and matches the YAML.

The pass verdict holds only if the controller's frontend verify (format, lint, typecheck, test, build, no `generate:api` diff) is green.
