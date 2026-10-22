---
module: import-ticket-list
feature: retire-snapshot-import
task: 12
verdict: pass
attempt: 1
date: 2026-10-22
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: import-ticket-list — Task 12: Retire the old snapshot import

## Summary
The five old classes (`ImportTicketRequest`, `ResolvedTicketRepository`, `ResolvedTicketSnapshot`, `TicketController` with `/api/tickets/**`, `TicketService`) are deleted; nothing references them in `backend/src` or `frontend/src`; the `resolved_ticket_snapshot` table is kept because `solution_source` points at it (design D2).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | The upsert path that contradicted MSG31 is gone (spec D2). |
| Completeness | pass | All five files deleted; no changeset drops the table. |
| Correctness | pass | Build and the full suite pass; `ImportConfirmIT` boots the whole context with `ddl-auto: validate`. |
| Integration | pass | No caller left (grep); `knowledge` still compiles and starts. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list` → `trace import-ticket-list: 0/41 acceptance criteria have a test` (all ✗). Known harness gap: it scans only `*.test.*/*.spec.*/*_test.*` files and the `AC-001.1` spelling; JUnit methods here cite `_AC_001_1` (backend/AGENTS.md §2). Manual trace (grep of `backend/src/test` for `_AC_XXX_Y`, then reading each test): 41/41 ACs have at least one test that checks the criterion. This task's rows:

No AC owned (supports STORY-004 / decision D2).

## Issues
No issues found.

## Assumptions made
- The `sdd.ts trace` result (0/41) is a tool limitation, not missing tests; the manual `_AC_XXX_Y` trace was used instead (dispatcher instruction).
- Coverage was not measured (no JaCoCo in `backend/pom.xml`) and the spec sets no threshold — open question for the user whether a coverage figure is required.

## Test Report
Shared full run (all 13 tasks), see `task-12-retire-snapshot-import-test-report.md`: `mvn verify` 91/91 passed (87 unit + 4 `ImportConfirmIT` on PostgreSQL 16), 0 failed/skipped; `spotless:check` pass; frontend `typecheck` and `lint` pass. Coverage not measured (no coverage plugin). Security: `security-review.md`.
