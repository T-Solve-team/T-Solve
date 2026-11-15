# Test Report: ticket-repository — Task 28: ticket-publication-buttons

**Date:** 2026-11-15
**Commit range:** `default`: da60828..1315800 (checked at HEAD 18faf52)
**Commands (in `frontend/`):** `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` · `npm run build` · `npm run generate:api` + `git status --porcelain -- src/shared/api/generated` · re-runs with `npx vitest run …`
Traceability: `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-009`

## Results
- `format:check` → pass · `lint` → 0 errors, 1 warning (`TicketListFilters.tsx:23`, not in this range) ·
  `typecheck` → exit 0 · `build` → exit 0 · `generate:api` → no diff in `src/shared/api/generated`
- **Full suite** `npm run test`: Test Files 11 failed | 102 passed (113) · Tests 5 failed | 1475 passed | 323 skipped
  (1803) · Duration 707 s. All failures are timeouts; none is in a file of this range.
- **Re-run 1** (`npx vitest run` on the failed files + `src/features/tickets` + `src/features/review`):
  Test Files 6 failed | 24 passed (30) · Tests 2 failed | 508 passed | 88 skipped. Again only timeouts.
- **Re-run 2** (`npx vitest run --no-file-parallelism` on the files still failing + `TicketPublicationActions.test.tsx`):
  Test Files 7 passed (7) · Tests 115 passed (115).
- **This task's test file alone:** `TicketPublicationActions.test.tsx` → Tests 9 passed (9). It also passed inside
  both parallel runs.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (`npm run test` has no coverage flag) | spec gives none |

## Acceptance Criteria → Tests
```
  ✓ AC-009.8   frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
  ✓ AC-009.9   frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
  ✓ AC-009.10  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
trace ticket-repository (STORY-009): 10/10 acceptance criteria have a test
```

## Failures (full suite; each passed on a re-run)
| File | Test | Error |
|------|------|-------|
| `src/test/guidelines.test.ts`, `src/test/setup-teardown.test.ts`, `src/app/router/host.test.ts`, `src/shared/lib/date.test.ts`, `src/shared/messages/catalog.test.ts`, `src/shared/messages/msg.test.ts`, `src/features/chatbot/hooks/chatbot-storage.test.ts`, `src/features/triage/hooks/history-filters.test.ts` | whole file | `Error: Hook timed out in 10000ms.` |
| `src/features/review/pages/ReviewQueuePage.test.tsx` | AC-002.2 / AC-002.5 … (BUG-100) | `expect(element).toBeEnabled()` |
| `src/features/ticket-import/components/ManualEntryForm.test.tsx` | AC-006.18 AC-006.19 …; AC-006.11 …; AC-006.12 … | `Test timed out in 15000ms.` |
| `src/features/ticket-import/pages/ImportPreviewPage.test.tsx` | AC-015.5 … (edge case 50) | `Test timed out in 15000ms.` |

- Cause: machine load. Other sessions were running 4 java and 34 node processes, and pure unit files such as
  `date.test.ts` timed out in their setup hook. Every failed file passed in re-run 1 or re-run 2. None of the files
  is in da60828..1315800 and none imports `TicketPublicationActions`.
- Suggested fix: none for this task. Run the suite on an idle machine or in CI to confirm.

## Notes
- Skipped: 323 in the full run. These are the suite's existing skips; this task adds no skipped test.
- Slow: the new tests use a 5 s `findBy` timeout (`TIMEOUT`); each finishes well within it when run alone.
