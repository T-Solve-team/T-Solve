# Test report: ticket-repository — Task 17 (manual-entry-tab)

**Date:** 2026-11-14 · **Run by:** harnessclaude reviewer (independent subagent) · **Branch:** feat/KAN-133-ticket-repository-manual-ticket @ 8e5716d9 (feature commit 1f040bed)

## Commands (in `frontend/`)

```
npx vitest run src/features/ticket-import --maxWorkers=2
npm run typecheck
npm run lint
bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-006   # repo root
```

## Results

| Check | Result |
|---|---|
| Vitest `src/features/ticket-import` | 6 files, **116 passed**, 0 failed (216 s) |
| `npm run typecheck` (`tsc -b --noEmit`) | clean |
| `npm run lint` | 0 errors, 1 warning (`react-refresh/only-export-components` in `src/features/tickets/components/TicketListFilters.tsx`, which predates this commit and is outside this feature) |
| Trace STORY-006 | 14/14 acceptance criteria have a test |

The run includes `ManualEntryForm.test.tsx` (8 tests: AC-006.9 ×2, AC-006.11 ×2, AC-006.14 ×4),
`ImportTicketsPage.test.tsx` (with the reworded AC-008.1), `DraftsList` and `SolutionFormModal` tests, and the other
ticket-import tests (regression).
The "Not implemented: getComputedStyle with pseudo-elements" lines in the output are jsdom noise, not failures.

## Not run

- The whole frontend suite, `format:check`, `build` and the `generate:api` diff check. The machine is low on
  memory, so the dispatcher scoped the run. The session log reports them green (89 files, 1546 tests).
- The backend was not re-run. It is unchanged by this commit and was covered by the Tasks 14–16 test report.

---

## Attempt 2 — 2026-11-14 · @ 1dca69d7 (fix commit d6975188)

Commands (in `frontend/`, scoped by the dispatcher because of low memory):

```
npx vitest run src/features/ticket-import --maxWorkers=2
npm run typecheck
npx eslint src/features/ticket-import
bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-006   # repo root
```

| Check | Result |
|---|---|
| Vitest `src/features/ticket-import` | 6 files, **123 passed**, 0 failed (375 s; exit 0) |
| `npm run typecheck` (`tsc -b --noEmit`) | clean (exit 0) |
| `npx eslint src/features/ticket-import` | 0 errors, 0 warnings (exit 0) |
| Trace STORY-006 | 14/14 acceptance criteria have a test. The trace also flags `AC-008.1` as an id in tests that is not in this spec, because it belongs to import-ticket-list. |

There are 7 new tests in `ManualEntryForm.test.tsx`:
- AC-006.11: reopen after Save as draft;
- AC-006.12: reopen after Save gives MSG04;
- AC-006.14: a failed background refetch keeps the input;
- AC-006.11: a load failure gives the error screen;
- AC-008.1: an unknown tab falls back to CSV;
- AC-006.14: an unknown field error goes to the Alert (2 cases).

The MSG32 test now also asserts the 201 status.

Not run: the whole frontend suite, `format:check`, `build` and the `generate:api` diff check. The session log reports
them green for d6975188. The backend was not run, since this fix does not change it.
