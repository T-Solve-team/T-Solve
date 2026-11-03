---
module: import-ticket-list
feature: project-uuid
task: 15
verdict: pass
attempt: 1
date: 2026-11-03
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 15: UUID Project ids

## Summary
Commit `c4f3900` (repo `default`, range `c4f3900^..c4f3900`) puts D10 in place:
- `projectId` is now a `UUID` in `ProjectAccess`, `StubProjectAccess`, the three entities (`ImportBatch`, `ImportPreview`, `Ticket`), `ImportPreviewResponse`, `CsvImportService(Impl)` and the controller's `@RequestParam UUID projectId`.
- The stub reads `user:<uuid>,<uuid>;…` and drops a malformed id, so it still fails closed.
- The new changeset `005-ticket-import-project-uuid.sql` changes the three `project_id` columns with `lpad(to_hex(project_id), 32, '0')::uuid`. Applied changeset 003 is not touched.
- The contract (`format: uuid` in the upload body and in `ImportPreviewResponse`) and the generated client match, and `npm run generate:api` leaves no diff.

Every STORY-001 criterion has a passing test. `mvn verify` passes at HEAD, including `ImportConfirmIT` 6/6, which runs changeset 005 on PostgreSQL with Hibernate `validate`. **Verdict: pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | D10 covers the API, `ProjectAccess`, the stub setting and the columns. `ImportController.java:78` binds `UUID projectId`, so a value that is not a UUID gets 400 before the service runs (`ImportControllerTest.uploadRejectsAProjectIdThatIsNotAUuid`, edge case 32). `StubProjectAccess.java:47-56` ignores a malformed id (`ignoresMalformedEntries`, `anOldNumericIdGrantsNothing`, edge case 31). Some callers were not edited (`ImportConfirmServiceImpl:97,100,171,195`, `ImportBatchRecorder:29`, `ImportPreviewServiceImpl:87,96`). They pass `preview.getProjectId()` straight through, so they compile against `UUID` without change. |
| Completeness | pass | Every item in the task's file list is changed, including `.env.example`, the master changelog, `tsolve-api.yaml` and the generated client. Nothing EXTRA. No other local or remote branch has a changeset numbered 005–009, so 005 does not collide. |
| Correctness | pass | `mvn verify` BUILD SUCCESS: 233 unit tests (1 skipped, pre-existing and unrelated), 10 ITs including `ImportConfirmIT` 6/6, and `spotless:check` passes. |
| Integration | pass | `ImportPreviewResponse.projectId` is serialised as a UUID string (`ImportControllerTest.readsThePreviewOfTheCaller_AC_007_1` asserts `PROJECT.toString()`). This matches the session's `CurrentUser.projects[].projectId` (UUID) that the web app sends. The frontend suite passes 1074/1074 on the regenerated client. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-001` returns `0/12 acceptance criteria have a test`, with every AC marked ✗. This is the known harness gap: the tool does not see JUnit names of the form `_AC_XXX_Y` (see the task-14 review). The manual trace below comes from a grep of `backend/src/test/java/vn/tsolve/ticket`:

| AC | Tests | Status |
|---|---|---|
| AC-001.1 | CsvImportServiceImplTest.createsPreviewWithCountsAndFirstPage_AC_001_1, firstPageHoldsFiftyRows_AC_001_1 | PASS |
| AC-001.2 | CsvImportServiceImplTest.rejectsFileNotEndingInCsv_AC_001_2, acceptsUpperCaseExtension_AC_001_2 | PASS |
| AC-001.3 | CsvTicketParserTest.rejectsNonUtf8File / rejectsUnclosedQuote / rejectsEmptyFile / rejectsDuplicateHeaderName _AC_001_3 | PASS |
| AC-001.4 | CsvImportServiceImplTest.rejectsFileOverTenMegabytes_AC_001_4, ImportControllerTest.uploadTooLargeIs413WithMsg28_AC_001_4 | PASS |
| AC-001.5 | CsvTicketParserTest.listsEveryMissingRequiredColumn_AC_001_5, semicolonFileFailsTheHeaderCheck_AC_001_5 | PASS |
| AC-001.6 | CsvTicketParserTest.rejectsHeaderWithoutDataRows_AC_001_6 | PASS |
| AC-001.7 | CsvTicketParserTest.ignoresByteOrderMark_AC_001_7 | PASS |
| AC-001.8 | CsvTicketParserTest.ignoresColumnsNotInTheTemplate_AC_001_8 | PASS |
| AC-001.9 | CsvImportServiceImplTest.rejectsProjectTheCallerDoesNotManage_AC_001_9 (UUID `…0002`) | PASS |
| AC-001.10 | ImportControllerTest.uploadNeedsProjectManagerRole / everyImportEndpointNeedsProjectManagerRole / confirmNeedsProjectManagerRole _AC_001_10 | PASS |
| AC-001.11 | CsvImportServiceImplTest.replacesTheCallersPreviousPreview_AC_001_11, rejectedFileKeepsThePreviousPreview_AC_001_11 | PASS |
| AC-001.12 | CsvTicketParserTest.readsCommasQuotesAndLineBreaksInsideQuotedValues_AC_001_12 | PASS |
| D10 / edge case 31 | StubProjectAccessTest.ignoresMalformedEntries, anOldNumericIdGrantsNothing | PASS |
| D10 / edge case 32 | ImportControllerTest.uploadRejectsAProjectIdThatIsNotAUuid | PASS |
| D10 changeset 005 on PostgreSQL | ImportConfirmIT (6) | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `backend/src/main/resources/application.yml:59`: the comment still reads `"user:projectId,projectId;user:projectId"` and does not say the ids are UUIDs. `.env.example:29-30` was updated. Fix: add "(UUIDs)" to the comment.
2. [Low] `StubProjectAccess.java:51`: `UUID.fromString` accepts short forms. `1-1-1-1-1` parses to `00000001-0001-0001-0001-000000000001` instead of being dropped as malformed. The stub only reads local configuration, and a bare `1` still grants nothing (tested), so the risk is small. Fix (optional): accept only the 36-character canonical form.
3. [Low] `ImportControllerTest.uploadRejectsAProjectIdThatIsNotAUuid` checks only the 400 status and that the service was not called. It does not check that the body carries a `code`. The be-foundation handler tests cover that body, so this fix is optional.

## Assumptions made
- D10 is a decision, not an acceptance criterion. I judged it against the spec's Decisions table and the Changelog row of 2026-11-01. I traced its tests to the design's edge cases 31 and 32; those tests have no AC id because no AC exists for them.
- The `sdd.ts trace` result (0/12) comes from a tool limitation. I used the manual `_AC_XXX_Y` trace instead.

## Test Report
See `task-15-project-uuid-test-report.md`. `mvn verify` BUILD SUCCESS at `15d346c`: 233 unit tests (0 failed, 1 skipped) and 10 ITs (0 failed), with `spotless:check` passing. Frontend: format, lint, typecheck, 1074/1074 tests and the build pass, and `generate:api` leaves no diff.
