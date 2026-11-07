# Test Report: import-ticket-list — Task 16: preview-read

**Date:** 2026-11-03. This report uses the main session's shared run at HEAD `15d346c` (backend code unchanged since `da59564`). Runs 1 and 2 were environment faults and are not used.
**Commands:** `cd backend && mvn verify` (JDK 24; includes `spotless:check`) · frontend `format:check`, `lint`, `typecheck`, `test`, `build`, `generate:api` · `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-007`

## Results
- Backend unit tests: Total 233, Passed 232, Failed 0, Skipped 1 (pre-existing, unrelated identity test).
- Backend integration tests: 10/10 passed (`ImportConfirmIT` 6/6, `SignInStateRepositoryIT` 4/4). `mvn verify` BUILD SUCCESS, exit 0.
- Spotless: pass · Compile: pass.
- Frontend: all checks pass, 1074/1074 tests, and `generate:api` leaves no diff, so `getImportPreview` in `src/shared/api/generated/import/import.ts` matches the contract.
- Feature tests: `ImportPreviewServiceImplTest` 12/12 (new: `readsTheCallersPreviewWithCountsMessageAndFirstPage_AC_007_1`, `rejectsReadingAnotherUsersOrAGonePreview_AC_007_2`, `rejectsReadingWhenTheCallerNoLongerManagesTheActiveProject_AC_007_3`) · `ImportControllerTest` 13/13 (new: `readsThePreviewOfTheCaller_AC_007_1`; `everyImportEndpointNeedsProjectManagerRole_AC_001_10` now covers the new path).

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no JaCoCo) | spec silent |

## Acceptance Criteria → Tests
`sdd.ts trace`: `0/3`. This is the known gap for JUnit `_AC_XXX_Y` names. Manual trace:

| AC | Tests | Result |
|---|---|---|
| AC-007.1 | readsTheCallersPreviewWithCountsMessageAndFirstPage_AC_007_1, readsThePreviewOfTheCaller_AC_007_1 | pass |
| AC-007.2 | rejectsReadingAnotherUsersOrAGonePreview_AC_007_2 | pass |
| AC-007.3 | rejectsReadingWhenTheCallerNoLongerManagesTheActiveProject_AC_007_3 | pass |

Design edge cases 29 (another PM's preview id → 404) and 30 (Project lost → 403 MSG08) are covered by the AC-007.2 and AC-007.3 tests.

## Failures
None.
