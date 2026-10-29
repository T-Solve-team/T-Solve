---
module: import-ticket-list
feature: csv-confirm-db-tests
task: 13
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 13: Real-database tests for Confirm

## Summary
`ImportConfirmIT` runs the real changesets on `pgvector/pgvector:pg16` (Testcontainers, `@ServiceConnection`) and proves what mocks cannot: a database-level failure mid-import leaves no Ticket and no decision but one FAILED batch with 0 published (AC-004.8); the event arrives with no transaction open and all Tickets visible (AC-004.10); two PMs confirming the same IDs at once give one success and one MSG33. `backend/AGENTS.md` §8 documents Testcontainers/Docker; Failsafe runs `*IT` in `mvn verify`.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-004.8 and AC-004.10 proved against a real rollback/commit. |
| Completeness | pass | pom (Testcontainers, Failsafe), AGENTS.md line, IT class — all as in tasks.md. |
| Correctness | pass | 4/4 pass in 24.9 s. |
| Integration | pass | Uses the real `CsvImportService` upload to build previews. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

| AC | Test | Status |
|---|---|---|
| AC-004.8 | ImportConfirmIT.savingFailureRollsBackEveryTicketButKeepsTheFailedBatch_AC_004_8 | PASS |
| AC-004.10 | ImportConfirmIT.eventArrivesAfterTheTicketsAreCommitted_AC_004_10 | PASS |

## Issues
CRITICAL: none.

Informational:
1. [Low] `backend/src/test/java/vn/tsolve/ticket/service/impl/ImportConfirmIT.java:89` — the test listener is a plain `@EventListener`, so the IT cannot catch that a `@TransactionalEventListener(AFTER_COMMIT)` listener (as the design prescribes) would never run (task 9 issue 4).
   Fix: add a second listener with `@TransactionalEventListener(phase = AFTER_COMMIT)` and assert it is called once task 9 settles the publishing approach.
2. [Low] `ImportConfirmIT.java:158-184` — the race test uses two different PMs; the same-user double click from design edge case 21 (second request → 404, no Failed batch) and the AC-001.11 replace path are not exercised on the real database.
   Fix: add both cases when task 9 issue 2 and task 6 issue 4 are fixed.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.

## Test Report
Shared full run (all 13 tasks), see `task-13-csv-confirm-db-tests-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
