---
module: import-ticket-list
feature: preview-rows
task: 7
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 7: Preview rows: paging, detail, remove

## Summary
`ImportPreviewServiceImpl` filters every read and change by owner (404 for another user's, a confirmed or a missing preview, and for an unknown row number), pages rows ordered by row number with size capped at 100 and an optional result filter, returns one row in full, and marks Valid or Error rows Removed (unknown numbers ignored) with counts and MSG30 recomputed.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.8 `ImportPreviewServiceImpl.java:82-88` + `ImportPreviewMapper.java:39-45`; AC-002.11 `:84-86`; AC-003.1 `:56-74` + `ImportPreviewRow.java:96-98`; AC-003.2 `:76-80`, `:50-53`; AC-003.3 `:48-54`. |
| Completeness | pass | Edge cases 18 and 19 covered. |
| Correctness | pass | 8/8 tests pass. |
| Integration | pass | Returns `PagedModel` matching the contract's `ImportPreviewRowPage`. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

| AC | Test | Status |
|---|---|---|
| AC-002.8 | listsRowsPageByPageWithSnippets / capsThePageSizeAtOneHundred `_AC_002_8` | PASS |
| AC-002.11 | filtersRowsByResult_AC_002_11 | PASS |
| AC-003.1 | removesValidAndErrorRows_AC_003_1 | PASS |
| AC-003.2 | rejectsAnotherUsersPreview / rejectsAPreviewThatNoLongerExists / rejectsARowNumberNotInThePreview `_AC_003_2` | PASS |
| AC-003.3 | returnsOneRowInFull_AC_003_3 | PASS |

## Issues
CRITICAL: none.

Informational:
1. [Medium] `backend/src/main/java/vn/tsolve/ticket/service/impl/ImportPreviewServiceImpl.java:76-80` — the row endpoints check only the owner, not that the caller still manages the preview's Project (spec NFR 3: "the backend re-checks this on every call"). Impact is low here (the PM reads or trims their own upload), so it does not block this task; the same gap at Confirm does (task 9). Spec gap: no AC states the re-check.
   Fix: after the AC is added, call `projects.managesActive(user, preview.getProjectId())` in `owned(...)` and throw `ProjectAccessDeniedException` (403 MSG08).
2. [Low] `backend/src/main/java/vn/tsolve/ticket/dto/RemoveRowsRequest.java:8` — no upper bound on `rowNumbers`.
   Fix: `@Size(max = …)`.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.
- NFR 3 for the read/remove endpoints is treated as non-blocking (Medium) because the data is the caller's own upload; open judgment call for the user.

## Test Report
Shared full run (all 13 tasks), see `task-7-preview-rows-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
