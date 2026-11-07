# Test Report: import-ticket-list — Task 17: duplicate-id-code

**Date:** 2026-11-03. This report uses the main session's shared run at HEAD `15d346c` (backend code unchanged since `da59564`). Runs 1 and 2 were environment faults and are not used.
**Commands:** `cd backend && mvn verify` (JDK 24; includes `spotless:check`) · frontend `format:check`, `lint`, `typecheck`, `test`, `build`, `generate:api` · `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-002`

## Results
- Backend unit tests: Total 233, Passed 232, Failed 0, Skipped 1 (pre-existing, unrelated identity test).
- Backend integration tests: 10/10 passed (`ImportConfirmIT` 6/6, `SignInStateRepositoryIT` 4/4). `mvn verify` BUILD SUCCESS, exit 0.
- Spotless: pass · Compile: pass.
- Frontend: all checks pass, 1074/1074 tests, and `generate:api` leaves no diff (`model/rowError.ts` matches the contract).
- Feature tests: `CsvRowValidatorTest` 15/15 (`everyRowSharingATicketIdIsMsgNew3_AC_002_6`, `duplicateInFileThatAlsoExistsGetsBothErrors_AC_002_6`) · `CsvImportServiceImplTest` 12/12 · `ImportPreviewServiceImplTest` 12/12 · `CsvImportPerformanceTest` 1/1. Frontend context: `catalog.test.ts` (AC-006.7, AC-006.9 for `MSG_NEW_3`) and `ImportPreviewPage.test.tsx` (duplicate row shows the `MSG_NEW_3` text) pass.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no JaCoCo) | spec silent |

## Acceptance Criteria → Tests
`sdd.ts trace`: `0/11`. This is the known gap for JUnit `_AC_XXX_Y` names. Manual trace (all pass): AC-002.1 (3 tests) · AC-002.2 (2) · AC-002.3 (3) · AC-002.4 (1) · AC-002.5 (2) · AC-002.6 (2) · AC-002.7 (2) · AC-002.8 (3) · AC-002.9 (1) · AC-002.10 (2) · AC-002.11 (1). The test names are in `task-17-duplicate-id-code-review.md`, section Traceability.

## Failures
None.
