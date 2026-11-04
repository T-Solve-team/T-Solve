---
module: triage
feature: suggestion-history-contract
task: 2
verdict: pass
attempt: 2
date: 2026-11-04
reviewer: harnessclaude-reviewer
iteration: 1  # progress.json currentIteration: 1
---

# Review: triage — Task 2: Align the history contract with the backend

## Summary
Attempt 2 reviews `626ce59..c2a80da`: the Task 2 code is `bef5072` and the fix is `c2a80da`. The attempt-1 CRITICAL is fixed. The AC-001.11 Workspace assertion now checks values that exist only in the foreign mock row. That row would be the first item on page 0 if the Workspace filter leaked, so the test now fails on a leak (see the reasoning in Stage 1). The fix also closes attempt-1 Informational 2: the AC-001.7 test now checks that a null `jiraIssueUrl` renders the key with no link. It makes the mock sort by parsed time instead of by string, and it renames the day-boundary test from AC-001.6 to AC-001.3, which fits the criterion better. format:check, lint (0 errors), typecheck and build all exit 0. `generate:api` leaves no diff, and trace is 20/20. Every triage test assertion passes.

The verdict is **pass**. One concern stays open. Shared setup bug BUG-105 makes `history-filters.test.ts` skip its 8 tests in the targeted multi-file run (2 of 2 runs). The file passes 8/8 alone. This is recorded as a judgment call under "Assumptions made".

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.7 shows the issue key and Project key (`TriageHistoryPage.tsx:46-57`). A null or unsafe URL renders as plain text (`:13-21,50-56`). AC-001.12 renders text only. AC-002.1 shows the Project key with no summary. The mock list orders by parsed `createdAt` (`handlers.ts:104-107`) |
| Completeness | pass | All 20 STORY-001/002 criteria have a named test, and each Task 2 criterion's test checks the criterion (Stage 1). The attempt-1 MISSING clause of AC-001.11 is covered again |
| Correctness | pass | 35/35 tests pass in `handlers.test.ts`, `TriageHistoryPage.test.tsx` and `history-filters.test.ts` (that file run alone). The targeted run passes 71 tests and skips 8 because of BUG-105, a shared setup timeout. No triage assertion fails. No CRITICAL defect |
| Integration | pass | The contract is unchanged since attempt 1 and matches the spec's Interface Contracts: nested `project {projectId, key}`, no `issueSummary`, nullable `jiraIssueUrl`. `generate:api` leaves no diff |

### Stage 1: spec compliance for the ACs Task 2 touches

| AC | Status | Evidence |
|----|--------|----------|
| AC-001.3 | PASS | `TriageHistoryPage.test.tsx` "AC-001.3 offers managed Projects…" checks the Project options. `handlers.test.ts:26` checks the Project and answer filters. `handlers.test.ts:54` checks the inclusive Vietnam day boundary (renamed from AC-001.6). AC-001.6 pagination is still covered by `TriageHistoryPage.test.tsx` "AC-001.6 paginates twenty rows…" |
| AC-001.7 | PASS | `TriageHistoryPage.test.tsx:120-133`. It checks `ITSUP` in the row, the HTTPS link and View details, and that the null-URL row ITNET-2002 has no link but shows its key (lines 129-132, new in `c2a80da`) |
| AC-001.11 | PASS (fixed) | `handlers.test.ts:69-79`. The 403/MSG08 clause is at `:70-73`. The Workspace clause is at `:74-78`: `not.toContain('ITSUP-9999')` and `not.toContain('Foreign private data')`. See the leak reasoning below |
| AC-001.12 | PASS | Unchanged since attempt 1: the HTML-like excerpt is shown as text and the `javascript:` URL is not a link |
| AC-002.1 | PASS | `TriageResultDetailModal.tsx:36-39` shows the Issue and Project key with no Summary. The AC-002.1 test passes |
| Other STORY-001/002 ACs | PASS | Unchanged; see the Task 1 review |

**Why the AC-001.11 assertion now fails on a leak.** This reasoning is from `mocks/data.ts` and `handlers.ts`; no files were edited.
- The strings occur only in the foreign row. `mocks/data.ts:95-107` has `workspaceId: 'foreign'`, `issueKey: 'ITSUP-9999'`, `jiraIssueUrl` containing `ITSUP-9999`, and `suggestedSolutionExcerpt: 'Foreign private data'`. Under `frontend/src`, a grep finds both strings only in that row and in the tests. The current-Workspace ITSUP rows are 1001-1025 (`data.ts:25-27`).
- Suppose `row.workspaceId === MOCK_WORKSPACE` (`handlers.ts:96`) were removed. The row would then pass every other filter. Its Project, ITSUP, is managed by `pm`, as `handlers.test.ts:26-36` proves by getting 200 with rows for `projectId: ITSUP_ID`. The request has no Project, answer or date filter.
- Its `createdAt`, `2026-11-02T10:00:00Z`, is the newest in the data set, so the sort at `handlers.ts:104-107` puts it first on page 0 with size 20.
- `summary()` (`handlers.ts:41-48`) keeps `issueKey`, `jiraIssueUrl` and `suggestedSolutionExcerpt`. The body would therefore contain both strings, and both assertions would fail.

## Traceability
```
  ✓ AC-001.1  frontend/src/features/triage/pages/TriageHistoryPage.test.tsx
  ✓ AC-001.2  …TriageHistoryPage.test.tsx, …mocks/handlers.test.ts, …hooks/history-filters.test.ts
  ✓ AC-001.3  …TriageHistoryPage.test.tsx, …mocks/handlers.test.ts, …hooks/history-filters.test.ts
  ✓ AC-001.4  …TriageHistoryPage.test.tsx
  ✓ AC-001.5  …TriageHistoryPage.test.tsx, …mocks/handlers.test.ts, …hooks/history-filters.test.ts
  ✓ AC-001.6  …TriageHistoryPage.test.tsx
  ✓ AC-001.7  …TriageHistoryPage.test.tsx
  ✓ AC-001.8  …TriageHistoryPage.test.tsx
  ✓ AC-001.9  …TriageHistoryPage.test.tsx
  ✓ AC-001.10 …TriageHistoryPage.test.tsx, …mocks/handlers.test.ts
  ✓ AC-001.11 …mocks/handlers.test.ts
  ✓ AC-001.12 …TriageHistoryPage.test.tsx
  ✓ AC-002.1 … AC-002.6, AC-002.8  …TriageHistoryPage.test.tsx (AC-002.3 also …mocks/handlers.test.ts)
  ✓ AC-002.7  …mocks/handlers.test.ts
trace triage (STORY-001, STORY-002): 20/20 acceptance criteria have a test
```

## Issues

CRITICAL (blocks pass):
No issues found. The attempt-1 CRITICAL (`handlers.test.ts:74-77`, the vacuous Workspace assertion) is fixed in `c2a80da`.

Informational (does not block):
1. `frontend/src/features/triage/mocks/handlers.test.ts:23`: the AC-001.2 newest-first check compares `createdAt` as strings, but `c2a80da` changed the mock to sort by `Date.parse`. The check is right for the current first two rows, but it would misjudge mixed ISO formats (`…:00Z` vs `…:00.500Z`).
   Fix: `expect(Date.parse(newest.createdAt)).toBeGreaterThanOrEqual(Date.parse(next.createdAt))`.
2. `frontend/openapi/tsolve-api.yaml:32`: the tag description still says "KAN-149 frontend proposal". This is cosmetic; it was attempt-1 Informational 3.
3. BUG-105 (`frontend/src/test/setup.ts:103`, with `vite.config.ts:57` raising `testTimeout` but not `hookTimeout`): the shared `beforeAll` times out under load, so `history-filters.test.ts` is skipped in the targeted run. The bug is logged as Open in `docs/bug-log.md` and owned by the FE Lead. It is outside Task 2's files.
4. The Task 1 review's informational items still apply. Item 1 is the Project filter that conflicts with guideline 03 §5.

## Assumptions made
- **Open judgment call:** I do not count the BUG-105 skip as a failing triage test. The run reports `Error: Hook timed out in 10000ms.` at `src/test/setup.ts:103`, not a failed assertion. The file passes 8/8 alone, and the cause is shared setup outside triage. If the user wants every targeted file green in one run, this verdict becomes fail until BUG-105 is fixed.
- The Workspace-leak check is a code-reading argument, as the dispatcher instructed. The test was not run with `handlers.ts:96` removed, because evaluation edits no files.
- The test rename from AC-001.6 to AC-001.3 is correct: the inclusive calendar-day range belongs to AC-001.3, and AC-001.6 is pagination.
- The spec is judged at the current HEAD. The browser check at 1366 × 768 was not verified independently.

## Previous attempt
Attempt 1 (2026-11-04, commit `66c58c6`): **fail**. One CRITICAL: the AC-001.11 Workspace assertion at `handlers.test.ts:74-77` checked `'Foreign Workspace suggestion'`, an `issueSummary` value that `bef5072` deleted, so the test could not fail. Informational: no test for a null `jiraIssueUrl`, the stale OpenAPI tag text, and the Task 1 items. Attempt 2 confirms the CRITICAL and the null-URL item are fixed. The full attempt-1 text is in git: `git show 66c58c6:harnessclaude/artifacts/06-evaluation/triage/task-2-suggestion-history-contract-review.md`.

## Test Report
`harnessclaude/artifacts/06-evaluation/triage/task-2-suggestion-history-contract-test-report.md`. The code state tested is HEAD `66c58c6`, whose code is the same as `c2a80da`. format:check, lint (0 errors, 1 warning outside triage), typecheck and build exit 0. `generate:api` exits 0 and `git status --porcelain src/shared/api/generated` is empty. Two targeted runs with `--maxWorkers=2` each gave `Tests 71 passed | 8 skipped (79)`, with only `history-filters.test.ts` skipped (BUG-105). That file passes 8/8 alone. A verbose run of `handlers.test.ts` and `pages/` passed 27/27. Trace is 20/20. No full-suite run was made, per the dispatcher.
