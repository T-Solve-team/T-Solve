---
module: triage
feature: suggestion-history-contract
task: 2
verdict: fail
attempt: 1
date: 2026-11-04
reviewer: harnessclaude-reviewer
iteration: 1  # progress.json currentIteration: 1
---

# Review: triage — Task 2: Align the history contract with the backend

## Summary
`bef5072` applies the 2026-11-04 contract change correctly. The OpenAPI schemas have `SuggestionProject {projectId (uuid), key}`, drop `issueSummary`, and make `jiraIssueUrl` nullable. The generated client is regenerated with no drift. Mocks, list (`row.project.key`) and popup (`result.project.key`) follow the new shape, and `safeIssueUrl` accepts null. All checks pass, and no triage test assertion fails (the red results come from shared-setup load timeouts; see Test Report).

The verdict is **fail** for one test defect. The commit deleted the mock field that the AC-001.11 Workspace assertion relied on and left the test unchanged. That assertion now checks a string that exists nowhere, so it passes even if the handler leaked another Workspace's rows. The spec says a test that names a criterion but does not check it counts as MISSING. Task 2 in `tasks.md` also required the AC-001.11 test to be updated first. The fix is one line.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | The changed criteria behave correctly: AC-001.7 shows the issue key and Project key, AC-001.12 renders text only and requires HTTPS, and AC-002.1 shows the Project key with no summary. A null `jiraIssueUrl` renders as plain text (`TriageHistoryPage.tsx:13-14,50-56`) |
| Completeness | fail | AC-001.11 Workspace-exclusion clause: the AC-named assertion is vacuous (Issue 1), so it counts as MISSING |
| Correctness | pass | With `--maxWorkers=2`: 79/79 targeted tests pass and the full suite has 1317 passed. The only red is a shared-setup timeout outside triage. No CRITICAL code defect |
| Integration | pass | The contract matches the spec's Interface Contracts: summary fields, the nested `project`, nullable `jiraIssueUrl`, `suggestedSolutionExcerpt` and `reporterAnswer`, and on the detail `suggestedSolution` and `wikiSources {documentId,title,isAvailable}`. Ids use the session's uuid type. `generate:api` leaves no diff. No reference to `issueSummary` is left in `frontend/src` or `openapi` |

### Stage 1: spec compliance for the ACs Task 2 touches

| AC | Status | Evidence |
|----|--------|----------|
| AC-001.3 | PASS | The Project options come from the session `jiraProjectKey` (`HistoryFilters.tsx:42-45`). The Task 2 shape does not affect them. Test `TriageHistoryPage.test.tsx:66` |
| AC-001.7 | PASS | `TriageHistoryPage.tsx:57` shows `row.project.key`. Test `:120` checks `ITSUP` in the row |
| AC-001.11 | **MISSING (Workspace clause)** | The 403 clause is checked at `handlers.test.ts:70-73`. The Workspace clause at `:74-77` asserts `not.toContain('Foreign Workspace suggestion')`. `bef5072` deleted that `issueSummary` value from `mocks/data.ts`, so the assertion can no longer fail |
| AC-001.12 | PASS | Test `:173` checks that the HTML-like excerpt is shown as text (`SCRIPT_SUGGESTION`) and that the `javascript:` URL is not a link |
| AC-002.1 | PASS | `TriageResultDetailModal.tsx:36-39` (Issue, Project key; Summary removed). Test `:182` |
| Other STORY-001/002 ACs | PASS | Unchanged from Task 1; see the Task 1 review |

## Traceability
```
trace triage (STORY-001, STORY-002): 20/20 acceptance criteria have a test
```
Trace checks names only. AC-001.11's named test exists, but half of it checks nothing (Issue 1).

## Issues

CRITICAL (blocks pass):
1. `frontend/src/features/triage/mocks/handlers.test.ts:74-77`: the AC-001.11 assertion that another Workspace's results are excluded is vacuous. `expect(body).not.toContain('Foreign Workspace suggestion')` searched for the foreign row's `issueSummary`. `bef5072` removed that field from `mocks/data.ts`, so the string no longer exists anywhere, and the test passes even if `handlers.ts:96` (`row.workspaceId === MOCK_WORKSPACE`) were deleted. The behavior is still guarded indirectly: AC-001.1 (`TriageHistoryPage.test.tsx:39`) expects ITNET-2002 first, which fails if the newer foreign ITSUP-9999 leaked, and AC-002.7 covers the detail. But the criterion's own test does not check it.
   Fix: assert on fields that still exist, for example `expect(body).not.toContain('ITSUP-9999')` and `expect(body).not.toContain('Foreign private data')`. Then run the test once with the Workspace check in `handlers.ts:96` removed and confirm it fails.

Informational (does not block):
2. No test asserts that a null `jiraIssueUrl` row renders without a link. ITNET-2002 (`mocks/data.ts:72`) is rendered as the first row in AC-001.1, but no test checks its link.
   Fix: in the AC-001.7 test, add `expect(within(await issueRow('ITNET-2002')).queryByRole('link', { name: 'ITNET-2002' })).not.toBeInTheDocument()`.
3. The `openapi/tsolve-api.yaml` tag description still says "KAN-149 frontend proposal", but the operation description now names the backend triage module. This is cosmetic.
4. The Task 1 review's informational items 1–7 still apply to this code state. Item 1 is the Project filter that conflicts with guideline 03 §5.

## Assumptions made
- **Open judgment call:** I treated a vacuous AC-named assertion as MISSING, per code-review Stage 1, even though AC-001.1 and AC-002.7 guard the behavior indirectly. If the user accepts the indirect coverage, Issue 1 drops to Informational and this task would pass.
- The shared setup-hook timeouts (`src/test/setup.ts:103`) are not counted as triage failures: in the targeted run with default workers for `history-filters.test.ts`, and in the full run for `src/app/dev/mock-mode.test.tsx`. Both files pass alone. **Open judgment call:** accept this, or require the shared fix first.
- The spec is judged at the current HEAD. The three-row changelog includes the id-type row, and keeping uuid ids follows it.
- The browser check was not verified independently.

## Test Report
`harnessclaude/artifacts/06-evaluation/triage/task-2-suggestion-history-contract-test-report.md`. format:check, lint (0 errors), typecheck and build all exit 0, and `generate:api` leaves no diff. The targeted run with `--maxWorkers=2` passes 79/79. With default workers, the `history-filters.test.ts` setup hook timed out in both runs and the file passed 8/8 when run alone. The full run with `--maxWorkers=2` passed 1317 tests and skipped 2. Its one failed file is `src/app/dev/mock-mode.test.tsx`, a setup timeout, and that file passes alone. Trace is 20/20.
