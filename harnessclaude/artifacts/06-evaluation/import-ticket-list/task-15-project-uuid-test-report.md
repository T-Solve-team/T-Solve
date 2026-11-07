# Test Report: import-ticket-list — Task 15: project-uuid

**Date:** 2026-11-03. The main session ran the suites once at HEAD `15d346c`. The backend code at HEAD is the same as at `da59564`, because no later commit touches the backend. Two earlier shared runs are not used: run 1 had a wrong `JAVA_HOME` and frontend hook timeouts, and run 2 failed because Testcontainers could not reach the Docker API. Both were environment faults.
**Commands:** `cd backend && mvn verify` (JDK 24; includes `spotless:check`) · `cd frontend && npm run format:check && lint && typecheck && test && build && generate:api` (no diff in `src/shared/api/generated`) · `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-001`

## Results
- Backend unit tests (Surefire): 233 total, 232 passed, 0 failed, 1 skipped (`AuthorizeControllerTest`, identity, pre-existing and unrelated).
- Backend integration tests (Failsafe): 10 total, 10 passed. `ImportConfirmIT` 6/6 runs Liquibase, including changeset 005, on PostgreSQL with Hibernate `validate`. `SignInStateRepositoryIT` 4/4.
- Spotless: pass. Compile: pass. `mvn verify`: BUILD SUCCESS, exit 0.
- Frontend: format pass; lint 0 errors (1 pre-existing warning in `features/tickets`); typecheck pass; 57 files / 1074 tests passed; build pass; `generate:api` no diff (`VERIFY-OK`, exit 0).
- Feature tests: `StubProjectAccessTest` 6/6 · `ImportControllerTest` 13/13 · `CsvImportServiceImplTest` 12/12 · `ImportConfirmServiceImplTest` 16/16 · `ImportPreviewServiceImplTest` 12/12 · `CsvTicketParserTest` 15/15 · `ImportConfirmIT` 6/6.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no JaCoCo in `backend/pom.xml`) | spec silent |

## Acceptance Criteria → Tests
`sdd.ts trace` reports `0/12 acceptance criteria have a test`. This is the known harness gap: the tool does not see JUnit `_AC_XXX_Y` names. The manual trace:

| AC | Tests | Result |
|---|---|---|
| AC-001.1 | createsPreviewWithCountsAndFirstPage_AC_001_1, firstPageHoldsFiftyRows_AC_001_1 | pass |
| AC-001.2 | rejectsFileNotEndingInCsv_AC_001_2, acceptsUpperCaseExtension_AC_001_2 | pass |
| AC-001.3 | CsvTicketParserTest ×4 _AC_001_3 | pass |
| AC-001.4 | rejectsFileOverTenMegabytes_AC_001_4, uploadTooLargeIs413WithMsg28_AC_001_4 | pass |
| AC-001.5 | listsEveryMissingRequiredColumn_AC_001_5, semicolonFileFailsTheHeaderCheck_AC_001_5 | pass |
| AC-001.6 | rejectsHeaderWithoutDataRows_AC_001_6 | pass |
| AC-001.7 | ignoresByteOrderMark_AC_001_7 | pass |
| AC-001.8 | ignoresColumnsNotInTheTemplate_AC_001_8 | pass |
| AC-001.9 | rejectsProjectTheCallerDoesNotManage_AC_001_9 | pass |
| AC-001.10 | uploadNeedsProjectManagerRole / everyImportEndpointNeedsProjectManagerRole / confirmNeedsProjectManagerRole _AC_001_10 | pass |
| AC-001.11 | replacesTheCallersPreviousPreview_AC_001_11, rejectedFileKeepsThePreviousPreview_AC_001_11 | pass |
| AC-001.12 | readsCommasQuotesAndLineBreaksInsideQuotedValues_AC_001_12 | pass |
| Edge case 31 (malformed stub UUID) | StubProjectAccessTest.ignoresMalformedEntries, anOldNumericIdGrantsNothing | pass |
| Edge case 32 (non-UUID `projectId` → 400) | ImportControllerTest.uploadRejectsAProjectIdThatIsNotAUuid | pass |
| Changeset 005 on PostgreSQL + Hibernate validate | ImportConfirmIT (6) | pass |

## Failures
None.

## Notes
- Testcontainers 1.21.x needs a docker-java API version that Docker Desktop 29 accepts. Run 2 failed for this reason alone; it is an environment fault, not a test result.
