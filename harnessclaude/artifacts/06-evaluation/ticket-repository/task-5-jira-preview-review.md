---
module: ticket-repository
feature: jira-preview
task: 5
verdict: pass
attempt: 2
date: 2026-11-07
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 5: Jira preview through the CSV preview endpoints

## Summary
Code under review: commit `791d4ddd` plus the uncommitted fix in the working tree (`git diff` against HEAD `b95b8877`):
`backend/src/main/java/vn/tsolve/ticket/dto/FillSolutionRequest.java`, `JiraPreviewControllerTest.java`, and the row
BUG-165 in `docs/bug-log.md`.

**Attempt 1 (fail):** CRITICAL on AC-002.4. A Solution section made only of Unicode spaces (U+3000, U+2003) passed
`@NotBlank`, which trims with `String.trim()` and so removes only characters up to U+0020. The service then stripped
the section to null and marked the row Valid, so Finish failed later with MSG33.

**Attempt 2 (pass):** the blocking finding is resolved. The record's compact constructor now runs
`CsvRowValidator.trim` (`String.strip()`, then empty becomes null) on all five sections. Jackson builds records
through the canonical constructor, so `@Valid` sees the stripped values, and a Unicode-blank section is null and
fails `@NotBlank` with MSG01 (`GlobalExceptionHandler.java:238-239`). The new test
`fillSolutionRejectsSectionsOfUnicodeSpacesOnly_AC_002_4_BUG_165` sends `"　"` and `"   "`. It expects 400
MSG01 naming `symptoms` and `scope`, and no call to the service. Without the fix the request would reach the mocked
service, so the test would fail. The other criteria were verified in attempt 1, and their code has not changed.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.4 now holds for ASCII and Unicode blanks (`FillSolutionRequest.java:19-26`). AC-002.1–AC-002.3, AC-002.5, AC-002.7–AC-002.9: unchanged since attempt 1, and they pass. |
| Completeness | pass | Every backend criterion of the task has a test. AC-002.6, AC-002.10 and AC-002.11 are web-app criteria for Task 9. |
| Correctness | pass | Targeted run: 38/38 (`JiraPreviewControllerTest` 9, `JiraPreviewServiceTest` 16, `ImportPreviewServiceImplTest` 13). The main session ran the full verify: 633 unit + 48 IT, 0 failures. |
| Integration | pass | Impact on the other callers is checked below. Nothing changes for them except the intended rejection. |

### Impact of the strip on the other callers of `FillSolutionRequest`
- **The only production caller** is `ImportController.fillImportPreviewSolution` (`ImportController.java:130-133`) →
  `ImportPreviewServiceImpl.fillSolution`. No other DTO, mapper or test builds the record, apart from
  `JiraPreviewServiceTest` and `JiraPreviewControllerTest`.
- **Length check (MSG02, `ImportPreviewServiceImpl.java:96-107`):** the service already ran `CsvRowValidator.trim`
  on each section before counting code points. `strip()` gives the same result when run twice, so the counted
  length and the stored text are unchanged. `rejectsOverTenThousandIncludingWorkaroundWithoutChanges_AC_002_5` and
  the 10,000 boundary test (with surrounding spaces) still pass.
- **Workaround** (optional): `"  "` was already stored as null. Now it is null one step earlier. No change.
- **Stored values:** trimmed text, as before (AC-002.3 / AC-013.1 "surrounding spaces trimmed").
- **CSV fill solution (`import-ticket-list` STORY-013):** on this branch the service still answers 409 MSG06 for a
  non-Jira preview (`:113-115`), so no CSV path exists here to change. On `origin/main`, PR #148 (`5e3dc11c`,
  BUG-151) already put the **same** compact constructor in this record for AC-013.2. The two versions differ only in
  the comment. So the fix gives CSV and Jira the same behaviour after the merge, which is what AC-013.2 asks for.
- **Error shape:** a stripped-to-null section gives `NotBlank` on null. The handler maps that to MSG01 with
  `field_name` equal to the field, the same as an ASCII blank (the existing AC-002.4 test asserts it).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-002` → `8/11 acceptance criteria have a test`
```
  ✓ AC-002.1  JiraPreviewServiceTest
  ✓ AC-002.2  JiraPreviewServiceTest
  ✓ AC-002.3  JiraPreviewServiceTest, JiraPreviewControllerTest
  ✓ AC-002.4  JiraPreviewControllerTest
  ✓ AC-002.5  JiraPreviewServiceTest, JiraPreviewControllerTest
  ✗ AC-002.6  no test under this module's paths
  ✓ AC-002.7  JiraPreviewServiceTest
  ✓ AC-002.8  JiraPreviewServiceTest, JiraPreviewControllerTest
  ✓ AC-002.9  JiraPreviewServiceTest, JiraPreviewControllerTest
  ✗ AC-002.10 no test under this module's paths
  ✗ AC-002.11 no test under this module's paths
```
AC-002.6, AC-002.10 and AC-002.11 are "THE web app SHALL" criteria for Task 9 (`jira-import-preview-page`, still
in progress), so they are not counted against this backend task, as in attempt 1.

## Issues
None blocking.

Minor (does not block):
1. `docs/bug-log.md:140` (BUG-165) duplicates **BUG-151** on `origin/main` (PR #148, `5e3dc11c`). That row has the
   same root cause and the same fix in the same file. The Notes column of BUG-165 says "the CSV fill solution on the
   KAN-31 branch has the same bug", but that is out of date: it was fixed there first. When main is merged into this
   branch, `FillSolutionRequest.java:19` will conflict on the comment line. `docs/bug-log.md` may also conflict at the
   end of the table. Fix: in BUG-165's Notes, say it is the same defect as BUG-151 (fixed on main by PR #148). On
   merge, keep main's comment.

Informational, carried over from attempt 1 and not changed:
2. `ImportPreviewServiceImpl.java:96-109`: the MSG02 check (and the request validation) runs before `owned()`, so an
   invalid body on another user's preview gets 400, not 404. Nothing leaks, because the 400 depends only on the body.
3. `ImportPreviewServiceImpl.java:82-91, :95-129`: fill solution and remove rows write straight into a reopened Draft
   (see the Task 7 review, judgment call 1).

## Assumptions made
- "Blank after trimming" in AC-002.4 means blank after the service's own trim (`String.strip()`, Unicode whitespace),
  as in attempt 1.
- The three web-app criteria (AC-002.6, AC-002.10, AC-002.11) belong to Task 9 and are not counted here.
- I accept the main session's full `mvn verify` result (633 unit + 48 IT, BUILD SUCCESS) and its Spotless check on a
  git clone. I re-ran only the targeted classes, as the dispatcher asked.

## Test Report
See `task-5-jira-preview-test-report.md`. Targeted run: 38 tests, 0 failures, BUILD SUCCESS (23 s).
