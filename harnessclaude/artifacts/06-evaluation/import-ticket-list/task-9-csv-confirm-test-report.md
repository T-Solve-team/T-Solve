# Test Report: import-ticket-list — Task 9: Confirm the import — Attempt 2

**Date:** 2026-10-23 (run by the reviewer at `58e0a06`; one shared run backs the task 9 attempt-2 and task 14 reports)
**Commands** (JDK 24 via `JAVA_HOME`, Byte Buddy experimental flag; these are environment quirks): `cd backend && mvn -q verify -DargLine="-Dnet.bytebuddy.experimental=true"` (Surefire, plus Failsafe `*IT` with Testcontainers 1.21.4 on PostgreSQL 16) · `mvn -q spotless:check` · `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-003 STORY-004`. Typecheck: the Java compile inside `verify` passed. No frontend file changed in `d15e991..58e0a06`, so the frontend checks were not re-run.

## Results
- Total: 99 · Passed: 99 · Failed: 0 · Skipped: 0 · `mvn verify` exit 0, about 61 s wall clock
- Compile: pass · Spotless: pass (exit 0)

| Test class | Tests | Fail/Err/Skip | Time |
|---|---|---|---|
| GlobalExceptionHandlerTest | 6 | 0/0/0 | 0.2 s |
| ImportControllerTest | 11 | 0/0/0 | 11.4 s (Spring context) |
| CsvImportPerformanceTest | 1 | 0/0/0 | 0.7 s |
| CsvRowValidatorTest | 15 | 0/0/0 | 0.1 s |
| CsvTicketParserTest | 15 | 0/0/0 | 0.1 s |
| CsvImportServiceImplTest | 12 | 0/0/0 | 0.8 s |
| ImportConfirmServiceImplTest | 16 (+5 since attempt 1) | 0/0/0 | 0.9 s |
| ImportPreviewServiceImplTest | 9 (+1, task 14) | 0/0/0 | 0.1 s |
| PreviewCleanupJobTest | 3 | 0/0/0 | 0.1 s |
| StubProjectAccessTest | 5 | 0/0/0 | 0.0 s |
| ImportConfirmIT (Failsafe) | 6 (+2) | 0/0/0 | 29.6 s incl. container start |

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no JaCoCo in `backend/pom.xml`) | spec silent (open question for the user) |

## Acceptance Criteria → Tests
`sdd.ts trace import-ticket-list STORY-003 STORY-004` output (trimmed; every line also lists the module's paths):
```
  ✗ AC-003.1 … AC-003.4  no test under this module's paths (…)
  ✗ AC-004.1 … AC-004.12 no test under this module's paths (…)
trace import-ticket-list (STORY-003, STORY-004): 0/16 acceptance criteria have a test
```
This is the known harness gap: the tool does not see JUnit `_AC_XXX_Y` method names. The manual trace (grep of `backend/src/test`) found every STORY-004 criterion covered:

| AC | Tests | Result |
|---|---|---|
| AC-004.1 | savesEachValidRowAsAPublishedTicket_AC_004_1 | pass |
| AC-004.2 | recordsAnApproveDecisionByTheProjectManagerPerTicket_AC_004_2 | pass |
| AC-004.3 | recordsOneCompletedImportBatchAndLinksTheTickets_AC_004_3 | pass |
| AC-004.4 | returnsMsg32AndDeletesThePreview_AC_004_4, confirmReturnsTheImportResult_AC_004_4 | pass |
| AC-004.5 | rejectsPreviewWithoutValidRows_AC_004_5 | pass |
| AC-004.6 | rejectsWhenTheProjectIsNoLongerActive_AC_004_6 | pass |
| AC-004.7 | failsWholeConfirmWhenATicketIdWasTakenMeanwhile_AC_004_7, uniqueConstraintOnTicketIdIsAlsoATakenId_AC_004_7, confirmFailureIsMsg33_AC_004_7 | pass |
| AC-004.8 | savingFailureImportsNothingAndRecordsAFailedBatch_AC_004_8, failureToRecordTheFailedBatchStillAnswersMsg33_AC_004_8, IT savingFailureRollsBackEveryTicketButKeepsTheFailedBatch_AC_004_8 | pass |
| AC-004.9 | rejectsAnotherUsersPreview_AC_004_9 | pass |
| AC-004.10 | publishesTheNewTicketIdsInsideTheImportTransaction_AC_004_10, IT eventArrivesAfterTheTicketsAreCommitted_AC_004_10, IT aListenerFailingAfterCommitDoesNotUndoTheImport_AC_004_10 | pass |
| AC-004.11 | rejectsCallerWhoNoLongerManagesTheProject_AC_004_11, inactiveProjectIsCheckedBeforeManagement_AC_004_11 | pass |
| AC-004.12 | secondConfirmOfTheSamePreviewIsNotFoundWithoutAFailedBatch_AC_004_12, validRowsAreReadAgainUnderTheLock_AC_004_12, IT secondConfirmOfTheSamePreviewIsNotFound_AC_004_12 | pass |

## Failures
None.

## Notes
- No flaky or skipped tests. The slow ones are the Spring/Testcontainers start-up in `ImportControllerTest` and `ImportConfirmIT`, which is expected.
- The expected WARN/ERROR lines come from tests that force failures. They now print only the exception type and SQLState (e.g. `Import of preview 1 failed: DataIntegrityViolationException (SQLState 23505)`), plus the stack of the IT's deliberately failing AFTER_COMMIT listener (`IllegalStateException: search down`). A grep of the verify log for `insert into ticket`, `Login fails`, `Error 500` and `Bad token` finds nothing, so attempt-1 finding 5 is resolved.

---

# Attempt 1 (2026-10-22)

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
