# Task 1: Five roles, multi-role session, logout, mock users and test helpers

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 23:00

### Working on: roles-session (KAN-59, branch `feature/KAN-59-fe-foundation-roles`)
- `/me` contract changed as in the spec (Interface Contracts); the backend owner Anh Tran Hai approved it by
  message (the Definition of Done item "agreed before KAN-59 merges", confirmed by the FE Lead on 22/10).
  `department` is `anyOf [DepartmentRef, null]` (OpenAPI 3.1); orval generates `DepartmentRef | null`.
- Session: `toSession()` adds EMPLOYEE and drops unknown roles; `useSession()` returns `roles`, `hasRole`,
  `hasAnyRole` (the single `role` is gone). `resetSession(client, { signedOut })`: logout and a global 401 set the
  session to `null` locally (no refetch), so a failed logout cannot sign the user back in; it also removes
  `tsolve.session.*` keys. `useSignOut` never throws.
- Mock users: `src/mocks/session.ts` (8 users of the design table); ids and labels in `shared/auth/dev-user.ts`
  (shared code may not import `src/mocks`); storage key `tsolve.dev.user`; `DevUserSwitcher` replaces
  `DevRoleSwitcher`.
- Test helpers: `{ role }` → the single-role fixture user (default PM), `{ user }` → a fixture user; a union type.
- Mechanical rename so the PR is green on its own: `STAFF` → `TICKET_RESOLVER`, `DEPARTMENT_MANAGER` removed in
  4 `routes.ts`, `MENUS`, `ALL_SIGNED_IN_ROLES` (= `ROLES` until KAN-61), the guards (any-role check), the tests.
- Temporary until KAN-60 / KAN-61 (marked in the code): `mainRole(roles)` gives a multi-role user the landing
  page and menu of one role; Knowledge Manager and Employee land on My Profile with an empty menu.
- Commit: default@d9bfc7b

### Deviations from the plan
- Two test files more than the task lists: `shared/auth/session.test.ts` (AC-001.1, 001.3, 001.7) and
  `app/dev/mock-mode.test.tsx` (AC-009.6: loads the real `main.tsx` with mock mode off and on, MSW worker faked).
- Review mock data still holds HRHELP tickets (the PM now manages only ITSUP and ITNET); the Department-filter
  test uses IT. Scoping the queue to the user's Projects is `ticket-review`'s.
- Size: about 810 changed lines without generated code (≈400 of them tests), over the ~400-line guideline.

### Verify
- `npm run format:check`, `lint`, `typecheck`, `build`: exit 0. `generate:api`: no change against the working tree.
- `npm run test`: 17 files, 87 tests passed.
- `bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-001 STORY-008 STORY-009`: 16/16 acceptance criteria
  have a test.

### Blockers
none

### Next up
Evaluate roles-session; then Task 2 guards-menus (KAN-60).
