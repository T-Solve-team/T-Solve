# Test Report: ticket-review — Task 9: review-production-api (attempt 1)

Date: 2026-11-07 · Reviewer: harnessclaude-reviewer · HEAD: `feat/KAN-24-review-project-membership` (b5e93cde + harness commit 4f66fa0a)

Ranges: `7e2e4c6c..77fa3a31` (PR #137, read side), `e41f5330..b5e93cde` (project membership adapter).

## Backend

| Run | Command | Result |
|---|---|---|
| Full verify (dispatcher evidence, log checked by reviewer) | `mvn -Dapi.version=1.44 verify` | 807 unit + 165 integration, 0 failures, 0 errors; `spotless:check` OK; BUILD SUCCESS (`verify2.log`) |
| Targeted ITs (run by reviewer, PostgreSQL via Docker) | `mvn -q -Dapi.version=1.44 -Dit.test=ProjectMembershipReviewAccessIT,ReviewDecisionApiIT,ReviewHistoryApiIT,ReviewStorageIT,TicketDecisionIT verify` | exit 0. 0 failures in all five suites |

Targeted suites:

| Suite | Tests | Failures |
|---|---|---|
| ProjectMembershipReviewAccessIT | 3 | 0 |
| ReviewDecisionApiIT | 23 | 0 |
| ReviewHistoryApiIT | 18 | 0 |
| ReviewStorageIT (changelog upgrade, 017 excluded from the pre-UUID master) | 4 | 0 |
| TicketDecisionIT | 8 | 0 |

## Frontend (run by reviewer in `frontend/`, Node from `D:\node.js`)

| Check | Result |
|---|---|
| `npm run typecheck` | pass (exit 0) |
| `npm run lint` | 0 errors, 1 warning in an unrelated file (`src/features/tickets/components/TicketListFilters.tsx:23`, react-refresh) |
| `npm run test -- --maxWorkers=2 src/features/review` | 11 files, 116 tests passed |

Not re-run by the reviewer: full frontend suite, `format:check`, `build` and `generate:api` diff check. No frontend files changed in the adapter range; the PR #137 frontend changes were merged through CI.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-009` gives 11/12. Every Task 9 criterion (AC-009.1, 009.2, 009.10, 009.12) has a test. AC-009.9 is marked "ambiguous" because import-ticket-list uses the same id. That criterion belongs to Task 8, not Task 9.

## Coverage

No coverage tool was run. The new adapter's code paths (active, inactive, unknown and foreign Account; managed, MEMBER-only and INACTIVE Projects; both key forms) are tested directly in `ProjectMembershipReviewAccessIT`.
