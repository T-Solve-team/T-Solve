# Test Report: import-ticket-list — Task 21: import-preview-actions

**Date:** 2026-11-03. The run was made by the main session at HEAD `15d346c`, shared by tasks 20–22 (second run; the first was invalid because of the environment). The reviewer did not run the suites (dispatcher instruction).
**Commands** (in `frontend/`): `npm run format:check` · `lint` · `typecheck` · `test` (`vitest run`) · `build` · `generate:api` + diff check.
**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-009 STORY-010`

## Results
- format:check: pass · lint: 0 errors (1 unrelated warning in `features/tickets`) · typecheck: pass
- vitest: 57/57 files, 1074/1074 tests passed, 0 failed or skipped
- build: pass · generate:api: no diff · exit 0

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported | spec silent; no coverage gate (guideline 10) |

## Acceptance Criteria → Tests
`sdd.ts trace`: 16/16. The ACs of this task, in `frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx`:

| AC | Tests | Result |
|---|---|---|
| AC-009.3 | `:133`, `:191`, `:199` | pass |
| AC-009.5 | `:219` (edge case 35) | pass |
| AC-009.6 | `:229` (edge case 36) | pass |
| AC-009.7 | `:241` (4 cases: 409 MSG30, 409 MSG40, 409 MSG33, 500 MSG33) | pass |
| AC-009.8 | `:265` (edge case 34) | pass |
| AC-009.9 | `:289` | pass |
| AC-009.10 | `:309` | pass |

## Failures
None.

## Notes
- The AC-009.3 test at `:216` and the AC-010.2 test accept antd states that never finish in jsdom (the fade-out of the loading icon, `ant-zoom-leave`). Both test files say so in comments, so this is not flakiness.
