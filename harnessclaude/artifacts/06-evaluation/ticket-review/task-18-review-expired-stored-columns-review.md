---
module: ticket-review
feature: review-expired-stored-columns
task: 18
verdict: pass
attempt: 1
date: 2026-11-10
reviewer: harnessclaude-reviewer
---
# Review: ticket-review - Task 18: review-expired-stored-columns

## Summary
The Expired list now reads ticket.expired_at and status_before_expiry (read-only entity columns, selected in the ExpiredRow JPQL). It falls back per column: due date +1 at 00:00 Asia/Ho_Chi_Minh, else updated_at, for expiredAt; PENDING_REVIEW for previousStatus. The expiredAt sort uses the reported value (ExpiredItem::expiredAt). The fallback is never persisted. The OpenAPI text and the generated client comment match. No CRITICAL findings.

## Criteria
| Criterion | Result | Evidence |
|---|---|---|
| Functionality | PASS | TicketReviewReadServiceImpl.java:165-172; sort at line 209 |
| Completeness | PASS | AC-013.3 and AC-013.4 covered; per-column fallback tested independently |
| Correctness | PASS | ExpiredTicketsApiIT 26/26 (fresh run); developer full verify 1036 unit + 380 IT |
| Integration | PASS | frontend/openapi/tsolve-api.yaml and generated review.ts wording identical |

## Traceability
trace ticket-review (STORY-013): 9/9 acceptance criteria have a test (AC-013.3 and AC-013.4 in ExpiredTicketsApiIT).

## Issues
CRITICAL: none.
Informational: none. The stored-vs-derived sort test is real: EXP-8 has stored expired_at 2026-11-08 (derived would be 11-03), so it sorts last ascending and first descending. The test asserts the fallback columns stay NULL in the DB.

## Test Report
See task-18-review-expired-stored-columns-test-report.md.
