---
module: import-ticket-list
feature: preview-cleanup
task: 10
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 10: Preview cleanup job

## Summary
`PreviewCleanupJob` runs daily at 01:00 Vietnam time (`@EnableScheduling` in `TicketSchedulingConfig`), deletes every preview created before now − 24 h one at a time (each `deleteById` in its own repository transaction; rows go by `ON DELETE CASCADE`), logs and continues when one deletion fails.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.1 `PreviewCleanupJob.java:29-42` + `ImportPreviewRepository.java:21-22`; AC-006.2 strict `<` cutoff; AC-006.3 `:34-39`. |
| Completeness | pass | Edge case 26 (injected `Clock`). Every preview is treated as non-Draft, as the spec's Out of Scope says. |
| Correctness | pass | 3/3 tests pass. |
| Integration | pass | Uses the shared `Clock` bean from `TicketClockConfig`. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

| AC | Test | Status |
|---|---|---|
| AC-006.1 | deletesEveryPreviewOlderThanTwentyFourHours_AC_006_1 | PASS |
| AC-006.2 | keepsPreviewsYoungerThanTwentyFourHours_AC_006_2 | PASS |
| AC-006.3 | keepsGoingWhenOneDeletionFails_AC_006_3 | PASS |

## Issues
No issues found.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.

## Test Report
Shared full run (all 13 tasks), see `task-10-preview-cleanup-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
