# Test Report: ticket-repository — Task 26: ticket-publication-contract

**Date:** 2026-11-15
**Commit range:** `default`: 566fb3f..ce4dad2 (checked at HEAD 18faf52)
**Commands (in `frontend/`):** `npm run generate:api` then `git status --porcelain -- src/shared/api/generated` · `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run build` · `npm run test`
Traceability: `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-009`

## Results
- `npm run generate:api` → exit 0; `git status --porcelain -- src/shared/api/generated` → empty (no diff): the
  generated `review.ts` / `review.msw.ts` match the YAML, so nothing under `generated/` was edited by hand.
- `npm run format:check` → "All matched files use Prettier code style!" (exit 0)
- `npm run lint` → 0 errors, 1 warning (`src/features/tickets/components/TicketListFilters.tsx:23`,
  `react-refresh/only-export-components`; that file is not in this range)
- `npm run typecheck` (`tsc -b --noEmit`) → exit 0
- `npm run build` → exit 0
- `npm run test` (full suite): Test Files 11 failed | 102 passed (113); Tests 5 failed | 1475 passed | 323 skipped
  (1803). Every failure was a timeout (hook 10 s / test 15 s) under machine load; none is in a file of this range.
  The failed files passed when re-run (see the Task 28 test report).
- Backend `mvn -o verify` (shared run): the generated client is frontend-only; the backend side of the contract is
  Task 27.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| — | Not applicable: contract and generated code only | spec gives none |

## Acceptance Criteria → Tests
This task has no criterion of its own. It is the contract of AC-009.1–AC-009.7 (and the client used by
AC-009.8–AC-009.10).
```
trace ticket-repository (STORY-009): 10/10 acceptance criteria have a test
```

## Failures
None in this task's files. The full-suite timeouts are listed in
`task-28-ticket-publication-buttons-test-report.md`.

## Notes
- The machine was loaded by other sessions (4 java, 34 node processes) during the frontend run.
