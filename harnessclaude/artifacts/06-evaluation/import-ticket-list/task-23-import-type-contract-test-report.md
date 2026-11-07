# Test Report: import-ticket-list — Task 23: import-type-contract

**Date:** 2026-11-07. The reviewer made the run at HEAD `6267856`. The feature commit is `0526620`; it changes the contract, the generated client, the feature mocks and `StatusTag`.
**Commands** (in `frontend/`, `PATH="/c/Users/admin/AppData/Local/nvm/v24.22.2:$PATH"`, Node v24.22.2):
- `npm run format:check`, `lint`, `typecheck`, `test` (`vitest run`), `build`
- `generate:api`, then `git status --porcelain src/shared/api/generated` and `git diff --stat src/shared/api/generated`

Backend, for the contract's consumers: `mvn -B -Dapi.version=1.44 clean verify` (shared with tasks 24–26).
**Traceability:** `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-012 STORY-013 STORY-014`

## Results
| Check | Result |
|---|---|
| `format:check` | pass, exit 0 |
| `lint` | 0 errors, 1 warning, exit 0. The warning is `react-refresh/only-export-components` in `src/features/tickets/components/TicketListFilters.tsx:23`, which this task did not touch. |
| `typecheck` | pass, exit 0 |
| `test` | 80/80 files, 1442/1442 tests passed, 0 failed, 0 skipped; 312 s; exit 0 |
| `build` | pass (`built in 1.55s`; only the usual chunk-size warning), exit 0 |
| `generate:api` | exit 0. Afterwards `git status --porcelain src/shared/api/generated` and `git diff --stat` are both empty, so there is no diff and nothing to restore. |
| backend `mvn clean verify` | exit 0: surefire 635/635, failsafe 43/43, spotless clean |

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not reported | The spec is silent; the frontend has no coverage gate (guideline 10) |

## Acceptance Criteria → Tests
`sdd.ts trace` result: 16/16 acceptance criteria have a test, exit 0. Task 23 is the contract only. It carries no behaviour, so the ACs it describes are proven by the backend tests of tasks 24–26:

| AC | Test files (from trace) |
|---|---|
| AC-012.1 | `ImportControllerTest` |
| AC-012.2 | `CsvImportServiceImplTest`, `ImportControllerTest` |
| AC-012.3 / AC-012.4 | `CsvTicketParserTest` |
| AC-012.5 | `CsvImportServiceImplTest`, `CsvRowValidatorTest`, `ImportConfirmIT` |
| AC-012.6 | `CsvRowValidatorTest` |
| AC-012.7 | `CsvImportServiceImplTest`, `ImportPreviewServiceImplTest` |
| AC-013.1 – AC-013.3, AC-013.5 | `ImportPreviewServiceImplTest`, `ImportControllerTest` |
| AC-013.4 | `ImportPreviewServiceImplTest`, `ImportConfirmIT` |
| AC-013.6 | `ImportPreviewServiceImplTest` |
| AC-013.7 | `ImportConfirmIT` |
| AC-014.1 / AC-014.2 | `ImportConfirmServiceImplTest`, `ImportConfirmIT` |

The frontend test this task changed is `src/shared/ui/StatusTag/StatusTag.test.tsx`: 18 statuses, now including Needs Solution. It passes in the run above.

## Failures
None.

## Notes
- The run was made at HEAD, which also holds tasks 24–29. It shows that the contract and the generated client are in step now. It does not re-run the tree as it was at `0526620`.
- The vitest run was slow (312 s for 1442 tests). The default reporter did not print per-test times, so I cannot name any single slow test.
