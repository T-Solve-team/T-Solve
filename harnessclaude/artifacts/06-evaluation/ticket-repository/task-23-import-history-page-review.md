---
module: ticket-repository
feature: import-history-page
task: 23
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 23: Import History screen (KAN-134, STORY-007)

## Summary
This review covers the code at HEAD `7554caca`, worktree `KAN-134-pr257`. The feature was merged in PR #235 (`e98a9006`), and no later commit touches its files.

`frontend/src/features/ticket-import/pages/ImportHistoryPage.tsx` replaces the placeholder.

**Table and paging:**
- A `DataTable` fed by the generated `useGetImportHistory`, with server paging through `useListSearchParams`. The URL page is 1-based and the API page 0-based.
- Ten columns (lines 16-69): Date, Project Manager, Project, Department, Source, "Date range / file", Published, "Removed / errors", Status, Error.
- "Date range / file" shows `dateFrom – dateTo` for JIRA_IMPORT, `fileName` for CSV, and `—` otherwise.

**Filters:**
- Project: `useManagedProjectOptions`, which gives the session's PROJECT_MANAGER Projects.
- Source: the three `ImportSource` values.
- Date range: `RangePicker`, with ISO days sent as `dateFrom` / `dateTo`.
- Changing a filter resets the page.

**Empty and error states:**
- An empty page shows `DataTable`'s default `EmptyState` MSG04 (`shared/ui/DataTable/DataTable.tsx:50,92`).
- A failed load shows `ErrorState` with retry.

**Mocks.** The MSW handler (`features/ticket-import/mocks/handlers.ts:157-205`) mirrors the backend rules: PM only, managed scope, the filters, Vietnam calendar days, MSG39 and paging.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-007.9: one row per batch with the AC-007.2 columns, Jira range and CSV file name in one column, and the three filters. AC-007.10: MSG04 instead of rows. |
| Completeness | pass | AC-007.9 and AC-007.10 have tests whose names cite them (8 tests in `ImportHistoryPage.test.tsx`) |
| Correctness | not run here | The worktree has no `frontend/node_modules`, so I could not run vitest. The tests were read and match the code. The dispatcher's full verify runs them. |
| Integration | pass | Uses the generated client and models of Task 22. The Project filter values are the session `projectId`s (public ids), which is what the backend filters on (`p.public_id = :projectId`). |

## Traceability
```
  ✓ AC-007.9  frontend/src/features/ticket-import/pages/ImportHistoryPage.test.tsx
  ✓ AC-007.10  frontend/src/features/ticket-import/pages/ImportHistoryPage.test.tsx
trace ticket-repository (STORY-007): 10/10 acceptance criteria have a test
```

## Issues
No critical or blocking issues. All of the following are informational:

1. `frontend/src/features/ticket-import/pages/ImportHistoryPage.tsx:75`: the `source` URL parameter is accepted from all `TicketSource` values, `SOLUTION_FORM` included. The Select only offers the three `ImportSource` values.
   - With `?source=SOLUTION_FORM` the Select shows a raw value that is not among its options, and the API returns an empty page.
   - Fix (optional): look the value up in `ImportSource` instead.
2. `frontend/src/features/ticket-import/pages/ImportHistoryPage.tsx:62-66`: Removed and errors share one column, "Removed / errors".
   - AC-007.2 lists them as two fields, and AC-007.9 says "the columns of AC-007.2".
   - D23 and the design name this combined column, so I accept it.

## Assumptions made
- I did not run the frontend tests: there is no `node_modules` in the worktree, and the brief limits me to `npx vitest run <file>` only when it exists. This PASS assumes `ImportHistoryPage.test.tsx`, `typecheck`, `lint` and `format:check` are green in the dispatcher's full verify. If they fail, the verdict becomes fail.
- I accept the combined "Removed / errors" column (issue 2) on the strength of D23.

## Test Report
See `task-23-import-history-page-test-report.md`.
