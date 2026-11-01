# Task 16: Spaces I can view on my profile — KAN-237

**Module:** identity
**Story:** STORY-003
**Branch:** feat/KAN-237-identity-profile-viewable-spaces
**Status:** passing
**Iteration:** 1

## Session 2026-11-01

- Implemented the "Spaces I can view" section on /profile against MSW mocks: `useMyViewableSpaces`, `SpacesSection` (region "Spaces", both lists, spinner after 150 ms, MSG06 without retry, hidden when both lists are empty), the `/me/spaces` handler and fixtures in `mocks/data.ts`.
- Commits on the branch: 742521e (no-spaces/no-projects messages), ce7ddbb (QA tests, API contract, client), f5d7bbe (empty spaces and projects cards), c2aff2d (the feature), b710cf5 (test-env abort fix), ed77815 (review fixes).
- Review verdict: pass on attempt 1. Should-fix items were applied in ed77815: MSG06 no longer shows next to a stale list after a failed background refetch, and the Spaces card has its SectionCard shadow again.
- BUG-080 (test-env flake): MSW 3.0.3 / @mswjs/interceptors 0.45.7 let an aborted mocked fetch leak its socket, so a later `/me/spaces` request could pass through to localhost:3000 and fail with ECONNREFUSED. Fixed in `frontend/src/test/setup.ts` with a regression test in `frontend/src/test/setup.test.ts`. The my-profile folder passed 12 of 12 runs after the fix.
- Scope note: the Projects empty state (MSG143, AC-003.16) is a follow-up Quick change. The backend for `/me/spaces` is Task 19. The AC-003.4/.5/.10 tests are mock contract tests, not backend tests.
- Pre-existing: 6 failures in `features/users` (UserDetailPage AC-005.7; UserRolesPage AC-009.2/.6; UserStatusPage AC-006.5, AC-006.8, AC-009.1). They also fail without this change and may share the BUG-080 cause; not checked further.

## Verification

- `npm run format:check`, `lint` (0 errors, existing TicketListFilters warning), `typecheck`, `build` passed. `npm run generate:api` leaves no diff.
- Full frontend test run: 1049 passed, 6 failed, all six in `features/users` (see above).
- Trace identity: 62/122 acceptance criteria have a test at the BUG-080 fix point.

## Hand-off

1. Done: Task 16 "Spaces I can view" on /profile (UI on MSW mocks), review fixes (stale list on refetch error, card shadow), BUG-080 test-env flake fixed.
2. Open: Projects empty state MSG143 / AC-003.16 as a follow-up Quick change; backend is Task 19; 6 `features/users` failures pre-existing (owner noted).
3. Next: Task 17 + 18 (KAN-238).
4. Branch: feat/KAN-237-identity-profile-viewable-spaces.
5. PR: not yet opened; squash-merge planned (ce7ddbb is red without c2aff2d).
