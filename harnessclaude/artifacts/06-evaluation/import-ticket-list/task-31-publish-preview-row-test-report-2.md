---
module: import-ticket-list
feature: publish-preview-row
task: 31
attempt: 2
date: 2026-11-19
reviewer: harnessclaude-reviewer
---

# Test report: import-ticket-list — Task 31: Publish one preview row (attempt 2)

Code: `58e57127..80cece71` plus the fix `2f785147..4e2b56cd`. Run on branch `feat/KAN-373-import-publish-row` at
`f23b344d` (no code change after `720ef9d2`). Working tree clean apart from the untracked `docs/diagrams/`.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-017`: 10/10 criteria have a test.
AC-017.1 to AC-017.7 are now also named in `ImportConfirmIT` (AC-017.1/.2/.3/.7) and `ImportControllerTest`
(AC-017.1/.6). The stray `AC-007.4` comes from older tests.

## Backend (JBR from IntelliJ 2025.1.1.1, Docker running)

| Command | Result |
|---------|--------|
| `mvn -B test -Dtest='vn.tsolve.ticket.**.*Test'` | 417 run, 0 failures, 0 errors. BUILD SUCCESS |
| — of which `ImportConfirmServiceImplTest` | 35/35 (2 new: `uniqueViolationOnSaveIsATakenTicketId_AC_017_5`, `savingFailureAnswersMsg33WithoutAFailedBatch_AC_017_5`) |
| — of which `ImportControllerTest` | 24/24 (2 new: `publishRowReturnsTheImportResult_AC_017_1`, `publishRowNeedsProjectManagerRole_AC_017_6`) |
| `mvn -B spotless:check` | BUILD SUCCESS |
| `mvn -B -Dapi.version=1.44 -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='Import*IT,JiraFinishIT' verify` | 43 run, 0 failures, 0 errors. BUILD SUCCESS |
| — `ImportConfirmIT` | 14/14 (2 new: `publishedRowLeavesThePreviewAndTheRestStillConfirms_AC_017_1_AC_017_2_AC_017_3`, `publishWaitsForARunningConfirmThenFindsThePreviewGone_AC_017_7`) |
| — `ImportHistoryControllerIT` 13/13, `ImportDraftRepositoryIT` 6/6, `ImportHistoryDateRangeIT` 6/6, `JiraFinishIT` 4/4 | pass |

No time-outs and no re-runs were needed. `EmailOutboxIT` was not in the run.

## Contract
`npm run generate:api` (frontend) leaves no diff in `src/shared/api/generated` — see the task 32 test report.
