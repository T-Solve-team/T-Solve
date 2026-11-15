---
module: fe-foundation
feature: session-project-status
task: 13
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: fe-foundation — Task 13: Project status in the `/me` contract and the session

## Summary
Commit `ef9b3fc2` (range `default: 6e69f821..87018e28`) adds `ProjectStatus` (`enum [ACTIVE, REMOVED]`) and the
required `ProjectMembership.projectStatus` to `frontend/openapi/tsolve-api.yaml`, regenerates the client (the
regeneration leaves no diff), and gives every fixture Project `projectStatus: 'ACTIVE'`. The session passes the
field through unchanged. AC-001.9 has two tests and they pass. Every frontend gate passes: format, lint, typecheck,
build, generate:api with no diff, and the full suite (1641/1641, no BUG-213 timeouts this run).

I also checked the BUG-216 fix (`2ce9216b`) against ticket-repository AC-006.12. It meets the criterion and has a
test named with the bug id, and the bug-log row was updated in the same commit. But the filter is in a hook shared
with the Drafts list, so it also hides the Drafts of REMOVED Projects (finding 1). That is a ticket-repository issue,
outside this feature's scope, and is left to the user as an open judgment call.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `toSession` keeps `projectStatus` as `/me` gives it, ACTIVE and REMOVED alike (`session.test.ts:90`). All fixture users' Projects carry a status (`mocks/session.ts`, `features/users/mocks/state.ts:197`; test `session.test.ts:109`) |
| Completeness | pass | The contract matches the spec's Interface Contracts: `ProjectMembership.projectStatus: ProjectStatus` is required, and `ProjectStatus` is `enum [ACTIVE, REMOVED]` (`tsolve-api.yaml:2166-2169`, `:2187`, `:2201-2202`). Generated `model/projectStatus.ts` and `projectMembership.ts` match it |
| Correctness | pass | format:check, lint, typecheck and build pass. `generate:api` leaves no diff in `src/shared/api/generated`. Full `npm run test`: 99 files and 1641 tests, all passed |
| Integration | pass | It matches the backend `ProjectMembership` (be-foundation Task 5; `CurrentUserSchemaTest` checks the same required set and `$ref`). Consumer: `useManagedProjectOptions` (BUG-216). See finding 1 for its side effect on the Drafts list |

## Traceability
```
  ✓ AC-001.1  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.2  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.3  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.4  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.5  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.6  frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.7  frontend/src/shared/auth/session.test.ts
  ✓ AC-001.8  frontend/src/shared/auth/session.test.ts, frontend/src/shared/auth/sign-in.test.ts, frontend/src/shared/auth/useSession.test.tsx
  ✓ AC-001.9  frontend/src/shared/auth/session.test.ts, frontend/src/shared/auth/sign-in.test.ts
trace fe-foundation (STORY-001): 9/9 acceptance criteria have a test
```
The `sign-in.test.ts` hit for AC-001.9 is a false match: line 221 names the backend (identity) criteria "AC-001.8 and
AC-001.9" in a describe title. The real AC-001.9 tests are at `session.test.ts:90` and `:109`.

BUG-216 (ticket-repository AC-006.12): `ManualEntryForm.test.tsx:157` "AC-006.12 offers no managed Project that is
not Active (BUG-216)" passes.

## Issues
None blocking this feature. Findings, all from the BUG-216 quick change (ticket-repository) unless noted:

1. **MAJOR (ticket-repository, BUG-216 side effect): Drafts of a REMOVED Project disappear from the Drafts list.**
   `frontend/src/features/ticket-import/hooks/useManagedProjects.ts:4-9` now keeps only ACTIVE Projects. `DraftsList.tsx:30`
   uses the same hook, and `DraftsList.tsx:34-37` hides any Draft whose Project has no option (the BUG-195
   rule). The result: a Draft of a Project the caller still manages but that is now REMOVED is no longer listed, so
   it cannot be reopened or deleted from the UI. ticket-repository AC-004.2 says the list shows "the caller's own Drafts".
   BUG-195's decision (2026-11-08) covers only Projects the user "no longer manages", and AC-002.9 refuses only an
   unmanaged Project. No test covers this case. The `docs/bug-log.md` BUG-216 note says the filter applies to
   "Manual entry and Jira pull", but it also changes the CSV tab (`CsvImportForm.tsx:37`, which is consistent
   with import-ticket-list AC-001.9) and the Drafts list.
   Suggested fix: give `DraftsList` its own label lookup over the managed Projects of any status, for example a
   second hook or a parameter. Or, if hiding these Drafts is what the user wants, record it in the spec (AC-004.2) or the
   bug log and add a test. Left as a judgment call (see Assumptions).
2. **INFO:** `features/users/mocks/state.ts:197` hard-codes `ProjectStatus.ACTIVE` for the dev mock Projects. The
   mock project store has no status, so this is right for now. If the mock store gains a status, map it here.
3. **INFO (traceability):** a false `sign-in.test.ts` match for AC-001.9 (see above). Harmless. Naming the module in
   that describe title, for example "identity AC-001.9", would remove it.

## Assumptions made
- I judged this feature (Task 13, AC-001.9) only on the contract, the generated client, the session and the
  fixtures. Finding 1 belongs to the BUG-216 quick change in ticket-repository, so it does not fail this feature.
  **Open judgment call for the user:** should a Draft of a REMOVED Project that the PM still manages stay
  in the Drafts list (so it can be reopened or deleted), or is hiding it acceptable? If it should stay, BUG-216 needs a
  follow-up fix. If hiding is acceptable, AC-004.2 or the bug log should say so.
- Filtering the CSV tab to ACTIVE Projects is taken as correct, since import-ticket-list AC-001.9 rejects a non-Active Project anyway.

## Test Report
See `task-13-session-project-status-test-report.md`. All frontend gates pass. Full suite: 99 files and 1641 tests,
all passed in 474.95 s, with no timeouts, so no solo reruns were needed. The client regeneration leaves no diff.
