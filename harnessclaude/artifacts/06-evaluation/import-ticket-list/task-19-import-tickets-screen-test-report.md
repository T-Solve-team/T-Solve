# Test Report: import-ticket-list — Task 19: import-tickets-screen

**Date:** 2026-11-03. This is the shared verify run by the main session at HEAD `15d346c` (branch `feat/KAN-31-ticket-import-csv-screens`). It was the second run: the first one was invalid (setup-hook timeouts under load) and is ignored. The reviewer read the output and did not re-run the suites.
**Commands** (in `frontend/`): `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` (`vitest run`) · `npm run build` · `npm run generate:api`, then a diff check of `src/shared/api/generated` · `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-008 STORY-011`

## Results
- format:check: pass
- lint: 0 errors, 1 warning, which is unrelated to this task (`src/features/tickets/components/TicketListFilters.tsx:23`)
- typecheck: pass
- vitest: 57/57 files, **1074/1074 tests passed**, 0 failed, 0 skipped (287 s)
- build: pass (`ImportTicketsPage` chunk 2.18 kB; the existing antd chunk-size warning)
- generate:api: no diff (`VERIFY-OK`); overall `frontend exit=0`
- Feature file: `src/features/ticket-import/pages/ImportTicketsPage.test.tsx` passed, 22.3 s (vitest results cache: `failed: false`)

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured | spec silent |

## Acceptance Criteria → Tests
`sdd.ts trace`: AC-008.1–008.8 ✓, all in `ImportTicketsPage.test.tsx`.

| AC | Tests | Result |
|---|---|---|
| AC-008.1 | tabs with CSV selected, Jira and Manual entry disabled (:60) | pass |
| AC-008.2 | managed Projects only, `KEY · Department` (:70) | pass |
| AC-008.3 | template saved as `ticket-import-template.csv` (:88) | pass |
| AC-008.4 | MSG01 at both fields, no request (:111); no managed Project (:122, edge case 39) | pass |
| AC-008.5 | MSG27 at the field, file not kept (:134); MSG28 through `CsvUpload.test.tsx:101` | pass |
| AC-008.6 | opens `/import/preview?previewId=<n>` (:143) | pass |
| AC-008.7 | MSG29 with columns, Project and file kept (:154); MSG04 and MSG08 shown as an alert (:176 ×2); MSG27 and MSG28 shown at the field (:191 ×2) | pass |
| AC-008.8 | loading, one request for a double click (:208, edge case 34) | pass |

## Failures
None.

## Notes
- The tests run on the shared MSW handlers (`mocks/handlers.ts`), the same ones dev mock mode uses. `resetMockData()` runs after each test.
