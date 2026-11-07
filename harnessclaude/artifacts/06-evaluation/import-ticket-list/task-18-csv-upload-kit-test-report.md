# Test Report: import-ticket-list — Task 18: csv-upload-kit

**Date:** 2026-11-03. This is the shared verify run by the main session at HEAD `15d346c` (branch `feat/KAN-31-ticket-import-csv-screens`). It was the second run: the first one was invalid (setup-hook timeouts under load) and is ignored. The reviewer read the output and did not re-run the suites.
**Commands** (in `frontend/`): `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` (`vitest run`) · `npm run build` · `npm run generate:api`, then a diff check of `src/shared/api/generated` · `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-008 STORY-011`

## Results
- format:check: pass ("All matched files use Prettier code style!")
- lint: 0 errors, 1 warning, which is unrelated to this task (`src/features/tickets/components/TicketListFilters.tsx:23`, react-refresh)
- typecheck: pass
- vitest: 57/57 files, **1074/1074 tests passed**, 0 failed, 0 skipped (287 s)
- build: pass (only the existing chunk-size warning for antd)
- generate:api: no diff (`VERIFY-OK`); overall `frontend exit=0`
- Feature file: `src/shared/ui/CsvUpload/CsvUpload.test.tsx` passed, 6.4 s (vitest results cache: `failed: false`)

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured | spec silent |

## Acceptance Criteria → Tests
`sdd.ts trace` reports AC-011.1–011.3 as ✗ "ambiguous": fe-foundation also has AC-011.x, and its paths cover `shared/**`. Manual trace:

| AC | Tests (`CsvUpload.test.tsx`) | Result |
|---|---|---|
| AC-011.1 | click and remove (:35); drag and replace (:53) | pass |
| AC-011.2 | MSG27 for `data.csv.txt`, `tickets.xlsx` and `csv` (:67); MSG27 for a dropped `photo.png` (:80); `REPORT.CSV` and exactly 10 MB kept (:91); 10 MB + 1 byte gives MSG28 and is not submitted (:101) | pass |
| AC-011.3 | no `fetch` or XHR on choose or submit; `onFinish` gets `{ file }` (:113) | pass |

## Failures
None.

## Notes
- AC-011.1's remove test waits up to 3 s for antd's leave animation in jsdom. That makes it slow, but it is not flaky in this run.
