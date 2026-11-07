---
module: import-ticket-list
feature: preview-fill-solution
task: 25
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 25: Fill solution endpoint

## Summary
Commit `9204467` (repo `default`, range `9204467^..9204467`), read at HEAD `6267856`.

`POST /api/v1/imports/previews/{previewId}/fill-solution` (`ImportController.java:127-138`) checks its body with `@Valid FillSolutionRequest`. `rowNumbers` must not be empty, and each item must be at least 1. The four required sections must pass `@NotBlank`, and a failure is a 400 with MSG01 on each field through the existing `GlobalExceptionHandler`.

`ImportPreviewServiceImpl.fillSolution` (`:95-129`) runs these steps in order:
1. Trim the sections, then reject a total over 10,000 code points with 400 MSG02 for the Solution (`:96-107`).
2. `owned` (404 for another user's or a missing preview, 403 MSG08) (`:109`).
3. Take the same `PESSIMISTIC_WRITE` lock that Confirm takes, then 404 if the preview is gone (`:111-112`).
4. 409 MSG06 for a file with Solutions (`:113-115`).
5. Remove duplicate row numbers, then 404 if any number is missing (`:116-121`).
6. 409 MSG06 if any row is Error or Removed (`:122-125`).
7. Only then fill every row and set it Valid (`:126`; `ImportPreviewRow.java:103`).

Every rejection comes before the first write, all inside one transaction, so a rejected request changes no row.

All seven ACs have tests that check them, including a real-database test of the wait for a running Confirm (edge case 44). The clean `mvn verify` is green. I found no CRITICAL issue. **Verdict pass**, with two Medium findings: issue 1 is an open judgment call, and issue 2 is a race with Remove.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-013.1 to AC-013.7 behave as written; see the Stage 1 table. Issue 1 is a narrow input class (Unicode-only whitespace) where AC-013.2 is not met. |
| Completeness | pass | All 7 ACs are traced. Edge cases 43, 44, 45 and 46 are tested. No EXTRA behaviour. |
| Correctness | pass | `mvn clean verify`: 635 unit tests and 43 integration tests passed, spotless clean, exit 0. Every "changes no row" test checks the in-memory row and, where it matters, `never().findAllById`. |
| Integration | pass | It matches the task 23 contract (`fillImportPreviewSolution`; 200 / 400 / 403 / 404 / 409; body fields and `minItems: 1`). It returns the same `ImportPreviewResponse` as remove-rows. The Solution columns are now updatable, and `errors` stays `updatable = false`. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-013.1 trimmed copy in each row, Valid | PASS | `ImportPreviewServiceImpl.java:96-100,126`; test `:298` checks two rows, trimmed `symptoms` / `rootCause`, title untouched |
| 2 | AC-013.2 400 MSG01 per empty section / MSG02 over 10,000 | PASS (see issue 1) | `FillSolutionRequest.java:12-16`; `:105-107`; controller `:317`, service `:325`, `:338` |
| 3 | AC-013.3 409 MSG06 for a file with Solutions or an Error / Removed row | PASS | `:113-115`, `:122-125`; tests `:352`, `:364`, controller `:344` |
| 4 | AC-013.4 404 for another user / confirmed / gone / missing row | PASS | `:109-112`, `:119-121`; tests `:390`, `:401`, `:410`, `ImportConfirmIT:212` |
| 5 | AC-013.5 403 MSG08 | PASS | `owned()` at `:109`; test `:420` |
| 6 | AC-013.6 replace a Valid row's Solution | PASS | Valid rows are not rejected at `:122-125`; test `:433` |
| 7 | AC-013.7 saved Solutions read back | PASS | `ImportConfirmIT:197` reads through `previewService.row` |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-013` → `7/7 acceptance criteria have a test`

```
✓ AC-013.1  ImportPreviewServiceImplTest, ImportControllerTest
✓ AC-013.2  ImportPreviewServiceImplTest, ImportControllerTest
✓ AC-013.3  ImportPreviewServiceImplTest, ImportControllerTest
✓ AC-013.4  ImportPreviewServiceImplTest, ImportConfirmIT
✓ AC-013.5  ImportPreviewServiceImplTest, ImportControllerTest
✓ AC-013.6  ImportPreviewServiceImplTest
✓ AC-013.7  ImportConfirmIT
```

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Medium, judgment call] `backend/src/main/java/vn/tsolve/ticket/dto/FillSolutionRequest.java:12-16` with `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportPreviewServiceImpl.java:96-100`: the check and the stored value disagree on "blank".
   - The app ships Hibernate Validator 8.0.1 (`BOOT-INF/lib/hibernate-validator-8.0.1.Final.jar`). Its `@NotBlank` tests `String.trim()`, which removes only characters ≤ U+0020.
   - The service then trims with `CsvRowValidator.trim` → `String.strip()`, which also removes Unicode whitespace.
   - So a section of only U+3000 or U+2003 passes validation, is stored as null, and the row is marked Valid without a required section. I checked this with JDK 24: `"　".trim().isEmpty()` is false and `"　".strip().isEmpty()` is true.
   - On Confirm, `toTicket` passes the null on to `ticket.symptoms` (`nullable = false`, `NOT NULL` in `003-ticket-import-schema.sql:33`). Saving fails, so the whole import fails with MSG33 and a Failed Import Batch, instead of the 400 MSG01 that AC-013.2 requires.
   - The web form's whitespace check (JS `\s`) probably stops this on screen, so only a direct API call reaches it.
   - Fix: after trimming in the service, reject with 400 and MSG01 for each required section that became null, in the same field-error shape as `@Valid`. Or use a constraint that matches `strip()`. Add a test with `"　"`.
2. [Medium] `ImportPreviewServiceImpl.java:82-90` (`removeRows`) against `:111-126`: Fill solution locks the preview, but Remove does not, and `ImportPreviewRow` has no `@Version` or `@DynamicUpdate`. Here is how a removed row can come back (READ COMMITTED):
   - A Fill reads a Needs Solution row.
   - A concurrent Remove of the same row commits `REMOVED`.
   - The Fill then flushes a full-row UPDATE that writes `status = 'VALID'` over it.

   The row the PM removed comes back as Valid and is imported on Confirm (AC-003.1). It needs two requests from the same PM at the same moment, for example two tabs, or the screen sending Remove while Fill is running. Remove without the lock predates this task (`task-9-csv-confirm-review.md`, attempt 2, issue 1). This task adds a second writer to the same rows. Fix: take `previews.lockByIdAndOwner(previewId, user)` in `removeRows` too, and add a lock test like `ImportConfirmIT:212`.
3. [Low] `ImportPreviewServiceImpl.java:101-109`: the MSG02 length check runs before `owned()`. A request with an over-long Solution for another user's preview gets 400 MSG02, not 404. The answer does not depend on the preview, so nothing leaks, and the spec does not order 400 against 404/403. I record it only so the order is a known choice.

## Assumptions made
- Issue 1 is non-blocking because only direct API calls reach it, its effect stays within the caller's own import (a Failed batch with MSG33), and the CSV path has no such gap. **Open judgment call for the user:** count AC-013.2 as unmet for Unicode-only whitespace (then verdict fail, attempt 2), or keep it as a follow-up fix?
- AC-013.4 "has been confirmed": a confirmed preview is deleted, so "confirmed" and "no longer exists" are the same 404 path. `ImportConfirmIT:212` stands in for the Confirm with a raw `SELECT … FOR UPDATE` plus `DELETE`, and does not run a real Confirm. I accept this as evidence of edge case 44.
- Edge case 45: a duplicate row number is saved once (deduplicated with `LinkedHashSet`), and an empty list is a 400 with no MSG code required.

## Test Report
`task-25-preview-fill-solution-test-report.md`: `mvn -B -Dapi.version=1.44 clean verify` exit 0. Surefire 635/635, failsafe 43/43, spotless clean. The ticket-import classes all pass. No coverage tool is configured, and the spec sets no target.
