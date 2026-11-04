---
module: triage
feature: suggestion-history
task: 1
verdict: pass
attempt: 1
date: 2026-11-04
reviewer: harnessclaude-reviewer
iteration: 1  # progress.json currentIteration: 1
---

# Review: triage — Task 1: Suggestion History list and detail

## Summary
Task 1 (`a294fbf`, plus the BUG-104 fix `6de048c`) delivers 10.1 Suggestion History and the read-only 10.2 popup on MSW, judged at the shared code state `bef5072`. All 20 STORY-001/STORY-002 criteria have an AC-named test, and each test was read and checks its criterion. Every triage test that ran passed. Format, lint, typecheck, build and deterministic `generate:api` pass. The only red results are shared test-setup hook timeouts under load, outside triage (see Test Report). Rendering is plain text only. Jira links must be HTTPS. Detail access goes through the issue key and returns 404 for an issue the user cannot see. There is no app or shared source edit except the allowed route-table test row and the generated client. One test-strength regression in the AC-001.11 test came from Task 2 (`bef5072`), so it is recorded in the Task 2 review. At `a294fbf` that assertion worked: the foreign row's `issueSummary` "Foreign Workspace suggestion" was in the mock data.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1: all 20 ACs PASS (table below) |
| Completeness | pass | No MISSING. EXTRA: "Clear date range" button (`HistoryFilters.tsx:89`). It is a recovery action for AC-001.5 and harmless |
| Correctness | pass | 79/79 targeted triage + route-table tests pass with `--maxWorkers=2`. Full suite 1317 passed. The only red is a shared-setup timeout in `src/app/dev/mock-mode.test.tsx` (passes alone). No CRITICAL finding |
| Integration | pass | Uses the session `projects[].roleInProject`/`jiraProjectKey`, `useListSearchParams`, shared UI kit (`DataTable`, `FormModal`, `LoadingState`, `EmptyState`, `ErrorState`), MSG catalog (`MSG04/06/08/39/110`), `paths.wikiDocument`, generated `useGetSuggestionResults`/`useGetSuggestionResult`. Mocks register through the existing `triage/mocks/handlers.ts`. `src/app` change is only the route-table test row (BUG-104) |

### Stage 1 — spec compliance (frontend/src/features/triage)

| AC | Status | Evidence |
|----|--------|----------|
| AC-001.1 | PASS | `pages/TriageHistoryPage.tsx:41-91` columns. Test `TriageHistoryPage.test.tsx:29` (headers + newest row ITNET-2002), `:42` (BUG-104) |
| AC-001.2 | PASS | `TriageHistoryPage.tsx:28-39`. Test `:48` asserts exactly `page=0,size=20,sort=createdAt,desc` |
| AC-001.3 | PASS | `components/HistoryFilters.tsx:15-16,40-63,70`. Tests `TriageHistoryPage.test.tsx:66`, `hooks/history-filters.test.ts:19` |
| AC-001.4 | PASS | `HistoryFilters.tsx:18-28` clears `page`. Test `:79` (combine, page reset, Back) |
| AC-001.5 | PASS | `hooks/history-filters.ts:8-12`, query disabled `TriageHistoryPage.tsx:38`, MSG39 `HistoryFilters.tsx:84-91`. Tests `:94` (0 requests), `history-filters.test.ts:8`, `mocks/handlers.test.ts:41` |
| AC-001.6 | PASS | DataTable `Total N items`. Test `:107` (25 → 20 + 5, filters kept) |
| AC-001.7 | PASS | `TriageHistoryPage.tsx:13-21,46-62`. Test `:120` |
| AC-001.8 | PASS | `TriageHistoryPage.tsx:65-90`. Test `:131` (counts, dash, No answer). See Informational 2 |
| AC-001.9 | PASS | `TriageHistoryPage.tsx:96-115`. Test `:140` (loading, MSG06 → retry same URL → MSG04) |
| AC-001.10 | PASS | Route guard `routes.ts:10`. Handler `mocks/handlers.ts:19-27`. Tests `TriageHistoryPage.test.tsx:160`, `handlers.test.ts:62` |
| AC-001.11 | PASS at Task 1 | `handlers.ts:92,96`. Test `handlers.test.ts:69`. 403 part checked. Workspace part weakened by Task 2, see Task 2 review |
| AC-001.12 | PASS | React text only (no `dangerouslySetInnerHTML` in the feature), HTTPS check `TriageHistoryPage.tsx:17`. Test `:173` |
| AC-002.1 | PASS | `components/TriageResultDetailModal.tsx:35-77`. Test `:182`. See Informational 3 |
| AC-002.2 | PASS | `TriageResultDetailModal.tsx:50,71-75`. Test `:195` |
| AC-002.3 | PASS | `TriageResultDetailModal.tsx:53-67`. Tests `:205`, `handlers.test.ts:91` |
| AC-002.4 | PASS | `TriageResultDetailModal.tsx:61-65`. Test `:217` |
| AC-002.5 | PASS | `TriageResultDetailModal.tsx:15,28-33`. Test `:229` |
| AC-002.6 | PASS | `key={selectedIssue}` `TriageHistoryPage.tsx:118` and a per-key query. Test `:249` |
| AC-002.7 | PASS | `handlers.ts:123-129`. Test `handlers.test.ts:80` (unmanaged and foreign Workspace both 404/MSG04) |
| AC-002.8 | PASS | Popup has only the FormModal Close/Cancel. Test `:277` |

### Frontend rules (frontend/AGENTS.md)
- No `src/app`/`src/shared` source edits. The only edits there are `src/app/router/route-table.test.tsx` (10.1 row removed, BUG-104) and the generated client, which matches `openapi/tsolve-api.yaml` (no diff after regeneration).
- Messages come only from `msg()`. No message text is typed by hand.
- Raw antd `Table`/`Modal`/`Tag`/`message` are not used, and there is no `style={{}}`. Lint (import boundaries) passes.
- No new npm dependency.

### Bug log
- BUG-093 matches: the row says Fixed in `a294fbf`. The regex `handlers.ts:15` now has the full 8-4-4-4-12 UUID shape. The test `handlers.test.ts:26` `AC-001.3 filters Project and answer together (BUG-093)` filters by a managed UUID, so it would get 400 with the old regex. It is a real regression test.
- BUG-104 matches: the row says Fixed in `6de048c`, and the row was removed from `route-table.test.tsx`. See Informational 4 on its named test.

## Traceability
```
trace triage (STORY-001, STORY-002): 20/20 acceptance criteria have a test
```
Full per-AC output: `task-1-suggestion-history-test-report.md`.

## Issues

No CRITICAL issues.

Informational (does not block):
1. `frontend/src/features/triage/components/HistoryFilters.tsx:33-47`: the Project filter conflicts with `frontend/guidelines/03-naming.md:162` ("never add a Project filter to any screen … except My Profile → My Solutions", decision FE-45). The approved spec AC-001.3 requires the filter, so the code follows the contract.
   Fix: ask the FE Lead to record a triage exception in `guidelines/13-decision-log.md` (like FE-45), or change the spec.
2. `TriageHistoryPage.test.tsx:131-138` (AC-001.8): the test checks only the list label "No answer". The list labels "Solved" and "Not solved" (`TriageHistoryPage.tsx:84-88`) are not checked.
   Fix: also check one YES row and one NO row.
3. `TriageHistoryPage.test.tsx:182-193` (AC-002.1): the popup date (`TriageResultDetailModal.tsx:40-47`) is not checked.
   Fix: check the formatted Asia/Ho_Chi_Minh date of ITSUP-1001.
4. BUG-104's named test `TriageHistoryPage.test.tsx:42-46` passes even with the fix reverted, because the bug was the stale row in `route-table.test.tsx`. That file's AC-005.3 cases are the real regression check. Acceptable for a test-only bug.
   Fix (optional): add a note in the bug-log Test column.
5. `mocks/handlers.test.ts:54`: the test is named `AC-001.6` but checks the AC-001.3 inclusive Vietnam-day boundary (design row 5).
   Fix: rename it to AC-001.3.
6. `mocks/handlers.ts:105`: the mock orders by ISO string (`localeCompare`), not by time. Timestamps in the same second, with and without milliseconds, sort wrong. Today's data has none. Mock only.
   Fix: compare `Date.parse` values.
7. `TriageResultDetailModal.tsx:62-64`: an unavailable Wiki source still shows its title next to MSG110. AC-002.4 only forbids a usable link, and the contract returns `title` for every source, so this is allowed. See the assumption below.
8. `a294fbf` on its own broke `src/app/router/route-table.test.tsx` (BUG-104), which means Task 1 was committed without a green full suite. `6de048c` fixed it within Task 1's scope.

## Assumptions made
- Judged against the current spec (2026-11-04 changelog), at code state `bef5072`. Criteria that the 04/11 change reworded (AC-001.7, AC-001.12, AC-002.1) are attributed to Task 2. Task 1 is judged on the rest of its deliverable.
- The `history-filters.test.ts` (targeted run, default workers) and `mock-mode.test.tsx` (full run) failures are setup-hook timeouts in shared `src/test/setup.ts:103` under load. Both pass alone and the triage file passes with 2 workers, so they are not counted as triage test failures. **Open judgment call for the user:** accept this, or require the shared setup timeout fixed (and logged in `docs/bug-log.md`) first.
- Showing the title of an unavailable Wiki source is acceptable (Informational 7). **Open judgment call:** the user or BA may want only MSG110.
- The Project filter conflict with guideline 03 §5 does not block, because the approved spec requires the filter (Informational 1). **Open judgment call** for the FE Lead.
- Not verified here: the DoD browser check at 1366 × 768. No browser tool was available in this evaluation.

## Test Report
`harnessclaude/artifacts/06-evaluation/triage/task-1-suggestion-history-test-report.md`. Results: format:check, lint (0 errors), typecheck and build all exit 0. `generate:api` leaves no diff in `src/shared/api/generated`. Targeted `npx vitest run --maxWorkers=2 src/features/triage src/app/router/route-table.test.tsx`: `Tests 79 passed (79)`. With default workers, `history-filters.test.ts` hits the setup-hook timeout (8 skipped) in 2 of 2 runs and passes 8/8 alone. Full `npx vitest run --maxWorkers=2`: `Tests 1317 passed | 2 skipped (1319)`, with one failed suite, `src/app/dev/mock-mode.test.tsx` (setup-hook timeout; 2/2 alone). Trace 20/20. No coverage script exists.
