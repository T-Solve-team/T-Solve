---
module: import-ticket-list
feature: preview-rows-access
task: 14
verdict: pass
attempt: 1
date: 2026-10-23
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 14: Re-check Project access on preview rows

## Summary
Commit `58e0a06` (repo `default`, range `d15e991..58e0a06`) adds a `ProjectAccess.managesActive(user, preview.projectId)` check to `ImportPreviewServiceImpl.owned()`. The check runs after the owner lookup, so a missing or foreign preview still gives 404 (AC-003.2), and it throws `ProjectAccessDeniedException` (403 MSG08) before any row is read or changed. Every row endpoint goes through `owned()`: `rows` (:49), `row` (:56) and `removeRows` (:66). The stub's `managesActive` already includes "Project is Active" (`StubProjectAccess.java:37`), which covers both conditions of AC-003.4. All four STORY-003 ACs have a test that checks them, and the suite passes (99/99). **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-003.4: `ImportPreviewServiceImpl.java:84-92` gives 403 MSG08 for a caller who no longer manages the Project or whose Project is no longer Active. It runs before `previewRows` is touched, so nothing changes. AC-003.1–003.3 are unchanged and still pass. |
| Completeness | pass | All of STORY-003 is traced to tests (below). Nothing EXTRA: `upload` keeps its own check, and Confirm has its own (task 9). |
| Correctness | pass | `ImportPreviewServiceImplTest`: 9/9. Full `mvn verify`: 99/99. `spotless:check` passes. No CRITICAL finding. |
| Integration | pass | `ProjectAccessDeniedException` is a `MessageCodeException(FORBIDDEN, "MSG08")`, rendered by `GlobalExceptionHandler`. `frontend/openapi/tsolve-api.yaml` already lists `403` for `/rows`, `/rows/{rowNo}` and `/remove-rows`. The new constructor dependency (`ProjectAccess`) is satisfied by the existing `StubProjectAccess` bean, and the Spring context starts in `ImportControllerTest` and `ImportConfirmIT`. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-003 STORY-004` → `trace import-ticket-list (STORY-003, STORY-004): 0/16 acceptance criteria have a test` (every AC ✗). This is the known harness gap: the tool does not see JUnit methods named `_AC_XXX_Y` (FEEDBACK.md). For the manual trace I grepped `backend/src/test` and read each test:

| AC | Test (`ImportPreviewServiceImplTest`) | Status |
|---|---|---|
| AC-003.1 | removesValidAndErrorRows_AC_003_1 (:110) | PASS |
| AC-003.2 | rejectsAnotherUsersPreview_AC_003_2 (:130), rejectsAPreviewThatNoLongerExists_AC_003_2 (:140), rejectsARowNumberNotInThePreview_AC_003_2 (:147) | PASS |
| AC-003.3 | returnsOneRowInFull_AC_003_3 (:154) | PASS |
| AC-003.4 (new) | callerWhoNoLongerManagesTheProjectCannotReadOrChangeRows_AC_003_4 (:167). It calls `rows`, `row` and `removeRows`; each gives 403 with code MSG08, and `verifyNoInteractions(rows)` proves no row was read or changed. | PASS |

STORY-004 is traced in `task-9-csv-confirm-review.md` (attempt 2).

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `ImportPreviewServiceImplTest.java:167`: the test stubs `managesActive` to false, so it covers "Active but no longer managed" and "no longer Active" only through the `ProjectAccess` contract (`ProjectAccess.java:9`, "managed and Active"), which `StubProjectAccessTest` checks. If the real workspace-module implementation splits these differently, AC-003.4's inactive case has no test of its own. Fix (optional): add a case using a real `StubProjectAccess` with an inactive project.
2. [Low] `ImportPreviewServiceImpl.java:84-87`: `removeRows` loads the preview without the lock that Confirm takes, so a remove-rows racing a Confirm can still have its removed row imported. Details and fix are in `task-9-csv-confirm-review.md`, attempt 2, issue 1.

## Assumptions made
- AC-003.4 lists "listing rows, opening a row and removing rows". I read this as the three service methods `rows`, `row` and `removeRows`, which are every preview-row endpoint. `upload` and Confirm have their own criteria (AC-001.9, AC-004.6, AC-004.11).
- AC-003.4 treats "Project no longer Active" as 403 MSG08 on the row endpoints, while Confirm gives 409 MSG40 for the same state (AC-004.6). The two differ, but the spec says so explicitly (user decisions of 2026-10-22), so I do not count this as a spec defect.
- The `sdd.ts trace` result (0/16) is a tool limitation. I used the manual `_AC_XXX_Y` trace instead.

## Test Report
`task-14-preview-rows-access-test-report.md`: `mvn verify` exit 0, with 99 tests (93 unit and 6 `ImportConfirmIT`), 0 failed or skipped. `spotless:check` passes. Coverage not measured. Security: `security-review.md`, section "Attempt 2 (2026-10-23)". No new findings above Low.
