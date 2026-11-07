# Test Report: import-ticket-list — Task 27: import-type-form

**Date:** 2026-11-07. The reviewer ran every check at HEAD `6267856`. That HEAD holds tasks 27–29, and the code of `ff90aa7` is unchanged in it apart from the later tasks. One shared run serves tasks 27–29. Node v24.22.2, Git Bash.
**Commands** (in `frontend/`): `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` (`vitest run`) · `npm run build` · `npm run generate:api` + `git status -- src/shared/api/generated`
**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-015`

## Results
- format:check: exit 0, "All matched files use Prettier code style!"
- lint: exit 0, 0 errors, 1 warning. The warning is unrelated: `react-refresh/only-export-components` in `src/features/tickets/components/TicketListFilters.tsx`.
- typecheck (`tsc -b --noEmit`): exit 0
- vitest, run 1: exit 1. 7 of 80 files failed, 73 passed; 6 tests failed, 1150 passed, 286 skipped (1442 in total); 430.63 s. This run overlapped with lint, typecheck and build, which the reviewer started at the same time. Every failure came from the load (see Notes).
- vitest, run 2 (re-run alone, as run-tests step 5 says): exit 0. 80/80 files, 1442/1442 tests, 0 failed or skipped, 216.32 s.
- vitest, verbose run of `ImportTicketsPage.test.tsx`, `ImportPreviewPage.test.tsx` and `catalog.test.ts`: exit 0, 3/3 files, 274/274 tests.
- build: exit 0, built in 20.49 s (`ImportTicketsPage` chunk 2.55 kB).
- generate:api: exit 0, and it left no diff in `src/shared/api/generated`.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported | The spec is silent and the project has no coverage gate (guideline 10). |

## Acceptance Criteria → Tests
`sdd.ts trace import-ticket-list STORY-015`: 7/7 acceptance criteria have a test. This task's AC is in `frontend/src/features/ticket-import/pages/ImportTicketsPage.test.tsx`:

| AC | Test | Result |
|---|---|---|
| AC-015.1 | `:114` the Import type choice, with "Tickets with a Solution" checked | pass (342 ms) |
| AC-015.1 | `:124` "Tickets without a Solution": the request has `importType=WITHOUT_SOLUTION`, the file is saved as `ticket-import-template.csv`, and its content is the 5-column header | pass (816 ms) |
| AC-015.1 | `:154` the upload FormData carries `importType=WITHOUT_SOLUTION`, and the chosen file stays when the type changes | pass (1443 ms) |
| AC-015.1 (default) | `:91` AC-008.3: the default download's content matches `/^ticket_id,title,description,author,resolved_date,/`, which only the with-Solution template does | pass |

## Failures
None in the authoritative run (run 2).

Run 1 failures, all from load:
- `src/test/setup.ts:133`: the `beforeAll` hook timed out at 10 s, so `guidelines.test.ts`, `catalog.test.ts`, `msg.test.ts` and `chatbot-storage.test.ts` were skipped (286 tests). This is open bug BUG-105.
- `KnowledgeChatbotPage.test.tsx`: 3 tests timed out at 15 s, and 1 got garbled typing ("eH ocwo udnot eId ?c").
- `ReviewQueuePage.test.tsx`: one `toBeEnabled` wait failed (BUG-100 test).
- `ImportPreviewPage.test.tsx:765` (task 28's edge-case-51 test) timed out at 15 s. It takes 3.07 s alone.

## Notes
- Every failure of run 1 passed in run 2 with nothing else running, so they are timing, not code faults.
- No tests are skipped in run 2.
