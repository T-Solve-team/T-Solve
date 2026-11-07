# Test Report: import-ticket-list — Task 20: import-preview-screen

**Date:** 2026-11-03. The run was made by the main session at HEAD `15d346c` (the code is the same as `1b6bef5`), once for tasks 20–22. The reviewer did not run the suites (dispatcher instruction).
**Commands** (in `frontend/`): `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` (`vitest run`) · `npm run build` · `npm run generate:api` + diff check of `src/shared/api/generated`. Output: `scratchpad/frontend-verify.txt` (second run; the first run was invalid because of the environment and is not used).
**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-009 STORY-010`

## Results
- format:check: pass
- lint: 0 errors, 1 warning (`features/tickets/components/TicketListFilters.tsx:23`, react-refresh; not in this task)
- typecheck: pass
- vitest: 57/57 files, 1074/1074 tests passed, 0 failed or skipped, 287 s
- build: pass (only the existing warning about the antd chunk being over 1000 kB)
- generate:api: no diff (`VERIFY-OK`), exit 0

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported by the run | spec silent; no coverage gate (guideline 10) |

## Acceptance Criteria → Tests
`sdd.ts trace`: 16/16 criteria of STORY-009 and STORY-010 have a test. The ACs of this task, all in `frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx`:

| AC | Tests | Result |
|---|---|---|
| AC-009.1 | `:50` Summary, `:64` no MSG30 | pass |
| AC-009.2 | `:74` columns and content, `:107` unknown code → MSG06 (edge case 38), `:121` paging | pass |
| AC-009.11 | `:140` five addresses (edge case 33) | pass |
| AC-009.12 | `:150` 404 → MSG04, `:156` 403 → MSG08 | pass |
| AC-009.13 | `:163` Summary loading, `:174` rows loading | pass |

## Failures
None.

## Notes
- The default vitest reporter in a non-TTY run lists only failed files. That the test file passed comes from the 57/57 total.
