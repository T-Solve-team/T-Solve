# Test Report: import-ticket-list — Task 9: Confirm the import

**Date:** 2026-10-22
**Commands** (JDK 24 via `JAVA_HOME`; Byte Buddy experimental flag for JDK 24 — environment quirks, not code issues):
`cd frontend && npm run typecheck` · `npm run lint` · `cd backend && mvn -q verify -DargLine="-Dnet.bytebuddy.experimental=true"` (Surefire + Failsafe/Testcontainers, `pgvector/pgvector:pg16`) · `mvn -q spotless:check` · `bun harnessclaude/hooks/sdd.ts trace import-ticket-list`

**One shared full run backs all 13 test reports of this module** (run once by the reviewer at `d15e991` on 2026-10-22; results in `backend/target/surefire-reports` and `backend/target/failsafe-reports`).

## Results
- Total: 91 · Passed: 91 · Failed: 0 · Skipped: 0 · `mvn verify` exit 0 (unit tests ≈ 8.2 s, `ImportConfirmIT` 24.9 s incl. container start)
- Typecheck: pass (`tsc -b --noEmit`) · Lint: pass (ESLint, no issues) · Spotless: pass

| Test class | Tests | Fail/Err/Skip |
|---|---|---|
| GlobalExceptionHandlerTest | 6 | 0/0/0 |
| ImportControllerTest | 11 | 0/0/0 |
| CsvImportPerformanceTest | 1 | 0/0/0 |
| CsvRowValidatorTest | 15 | 0/0/0 |
| CsvTicketParserTest | 15 | 0/0/0 |
| CsvImportServiceImplTest | 12 | 0/0/0 |
| ImportConfirmServiceImplTest | 11 | 0/0/0 |
| ImportPreviewServiceImplTest | 8 | 0/0/0 |
| PreviewCleanupJobTest | 3 | 0/0/0 |
| StubProjectAccessTest | 5 | 0/0/0 |
| ImportConfirmIT (Failsafe) | 4 | 0/0/0 |

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured — no coverage plugin (JaCoCo) in `backend/pom.xml` | spec silent (open question for the user) |

## Acceptance Criteria → Tests
`sdd.ts trace import-ticket-list` output: `trace import-ticket-list: 0/41 acceptance criteria have a test` (every AC listed as ✗ "no test under this module's paths"). This is the known harness gap: the tool scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling, while JUnit methods cite `_AC_001_1` (backend/AGENTS.md §2). The manual trace (grep of `backend/src/test` for `_AC_XXX_Y`) found a test for **41/41** ACs; the rows for this task follow.

| AC | Tests (ImportConfirmServiceImplTest unless noted) | Result |
|---|---|---|
| AC-004.1 | savesEachValidRowAsAPublishedTicket_AC_004_1 | pass |
| AC-004.2 | recordsAnApproveDecisionByTheProjectManagerPerTicket_AC_004_2 | pass |
| AC-004.3 | recordsOneCompletedImportBatchAndLinksTheTickets_AC_004_3 | pass |
| AC-004.4 | returnsMsg32AndDeletesThePreview_AC_004_4; ImportControllerTest.confirmReturnsTheImportResult_AC_004_4 | pass |
| AC-004.5 | rejectsPreviewWithoutValidRows_AC_004_5 | pass |
| AC-004.6 | rejectsWhenTheProjectIsNoLongerActive_AC_004_6 | pass |
| AC-004.7 | failsWholeConfirmWhenATicketIdWasTakenMeanwhile_AC_004_7; ImportControllerTest.confirmFailureIsMsg33_AC_004_7 | pass |
| AC-004.8 | savingFailureImportsNothingAndRecordsAFailedBatch_AC_004_8 (+ ImportConfirmIT, task 13) | pass |
| AC-004.9 | rejectsAnotherUsersPreview_AC_004_9 | pass |
| AC-004.10 | publishesTheNewTicketIdsAfterCommit, aFailingListenerDoesNotUndoTheImport (`_AC_004_10`) (+ ImportConfirmIT) | pass |
| NFR 3 at Confirm (caller still manages the Project) | **no AC, no test, not implemented** — see the review | missing |

## Failures
None.

## Notes
- No flaky, skipped or slow (>5 s) unit tests. `ImportConfirmIT` takes ~25 s because it starts PostgreSQL in Docker.
- Expected ERROR log lines during `ImportConfirmIT` (forced trigger failure, unique-constraint race) are the tests working, but they print ticket row values — see `security-review.md` finding 2.
