# Test Report: identity — Task 13: project-handover

**Date:** 2026-10-30
**Base:** KAN-110 `6548ed0`, on `feat/KAN-111-identity-project-handover` by explicit user instruction.
**Scope:** STORY-008 frontend/MSW; production backend remains outside this delivery.

## Results

| Check | Result |
|-------|--------|
| Spec / trace | OK; STORY-008 9/9 criteria have tests |
| Formatting / lint / TypeScript | Pass |
| Production build | Pass |
| API regeneration | Pass; no diff in generated client |
| Full default Windows test run | 854 passed / 4 failed of 858; four 15s timeouts in unchanged ReviewQueue and chatbot rating401 tests |
| Full recheck with `--maxWorkers=2` | Pass, exit 0; all test files included, no assertion/timeout/config changes |
| Browser | Mock handover and reactivation verified in Edge, 1366 × 768 |
| Diff whitespace | Pass |

Commands used the installed Node executables for the package scripts because npm was not on PATH: Prettier `--check .`, ESLint `.`, TypeScript `-b --noEmit`, Vite `build`, Orval `--config orval.config.ts`, Vitest `run`, then Vitest `run --maxWorkers=2`. No coverage target or coverage script is specified for this slice; no coverage percentage is claimed.

## Acceptance Criteria → Evidence

| AC | Evidence |
|----|----------|
| AC-008.1 | UserStatusPage existing Project handover flow, linked to AC-006.5; browser popup before deactivation |
| AC-008.2 | UserRolesPage old-Department transfer; existing role-removal contract coverage in roles.test.ts |
| AC-008.3 | status.test.ts checks same-Department, Active, other Workspace-scoped PM recipients; invalid choices rejected atomically |
| AC-008.4 | status.test.ts preserves inactive history; new reactivation scenario verifies all unrelated Projects remain equal |
| AC-008.5 | UserStatusPage no-recipient case shows MSG59 and sends no mutation |
| AC-008.6 | UserStatusPage failure retains choices and permits retry; existing cancel tests preserve confirmed state |
| AC-008.7 | Status transfer preserves tracker key, recipient membership and references; status retry test verifies list/detail invalidation; existing hook also invalidates session |
| AC-008.8 | New BUG-038 component regression: pending cached refresh disabled, failed refresh disabled, successful retry keeps choices and submits; existing pending-submit protections retained and observed in browser |
| AC-008.9 | New explicit deactivate/transfer/reactivate contract test; browser reactivation no longer requires the transferred Projects |

## Failures and limitations

- The default run timed out in `KnowledgeChatbotPage.rating-401.test.tsx` (AC-003.5) and three ReviewQueuePage scenarios (approve, reject, request changes). Existing BUG-025 records this workstation-load pattern. The complete two-worker rerun passed; the default parallel timing problem is not claimed fixed.
- Red/green BUG-038: before the fix, the confirm button remains enabled during recipient refetch with cached successful data; after adding `isFetching` guards, the same regression passes. The initial draft test's ambiguous recipient selector was corrected before measuring this red/green result.
- jsdom warns about pseudo-element `getComputedStyle`; no warning suppression added.
- No production authorization, SMTP, audit persistence, real backend or live Jira mutation was tested.
