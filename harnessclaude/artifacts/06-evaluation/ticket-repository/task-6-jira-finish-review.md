---
module: ticket-repository
feature: jira-finish
task: 6
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 6: Finish — Confirm of a Jira preview

## Summary
Commit `b23bf842`. `ImportConfirmServiceImpl` has a Jira branch. It requires no Needs Solution row and at least one
Valid row (MSG_NEW_4), checks MSG40 before 403, locks the preview, and skips keys that became Tickets (MSG38 with
count and keys, beside MSG32). If every key is taken, it records a Completed batch with 0. It retries once after a
unique violation, cuts the title to 200 code points, records approve decisions and the event, and deletes the
preview (and so the Draft). AC-003.1 to AC-003.10 are met. PostgreSQL ITs prove the concurrency cases (Edge 6, 7)
and Edge 11. The issues found are informational.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-003.1: Ticket with Jira key, source JIRA_IMPORT, Published, snapshot fields, Solution, approve decision per Ticket, one Completed batch with published / skipped (`error_count`) / removed, preview deleted, MSG32 (`ImportConfirmServiceImpl.java:199-245`). AC-003.2: taken keys are filtered under the lock, and the response carries `notice` MSG38 `{count, issue_keys}` with MSG32 (`:211-221, :124-134`). AC-003.3: everything taken → Completed batch with 0 and the preview deleted. AC-003.4 and AC-003.5: MSG40 first, then 403, before any batch (`:103-108`). AC-003.6: MSG_NEW_4 with the Needs Solution count, before and under the lock (`:161-171, :206`). AC-003.7: failure → rollback, then a Failed batch in its own transaction with error "`<Type>`: MSG33" (no issue content), preview kept. AC-003.8: `FOR UPDATE` lock, second Finish → 404 (`:201-202`). AC-003.9: event published inside the transaction for AFTER_COMMIT listeners (`:240-243`). AC-003.10: Draft = preview, deleted (`:239`). |
| Completeness | pass | All ten of the task's criteria have code and tests. |
| Correctness | pass | `JiraFinishTest` 17/17, `JiraFinishIT` 4/4. The CSV regression suites `ImportConfirmServiceImplTest` (16) and `ImportConfirmIT` (6) are green. |
| Integration | pass | `ImportResultResponse.notice` matches the contract. `TicketsPublishedEvent` is unchanged for `search`. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-003` → `10/10 acceptance criteria have a test`.
- ✓ AC-003.1, AC-003.3, AC-003.5, AC-003.6, AC-003.7, AC-003.9 `JiraFinishTest`
- ✓ AC-003.2, AC-003.4, AC-003.8, AC-003.10 `JiraFinishTest`, `JiraFinishIT`

## Issues
No blocking issues.

Informational:
1. `ImportConfirmServiceImpl.java:141-151, :116-118`. The Jira retry, and the "TicketIdTaken" label on the Failed
   batch, fire on **any** `DataIntegrityViolationException`, not only a violation of `uq_ticket_ticket_id`.
   Examples: a NOT NULL violation (see Task 5 Issue 1), or a value too long for `ticket.writer VARCHAR(100)` (a
   Jira display name over 100 characters). Each of those runs a pointless second transaction and records a
   misleading error description.
   Fix: retry and label "TicketIdTaken" only when the SQLState is `23505` (`sqlState()` already exists at
   `:190-197`). Label everything else by type.
2. `ImportConfirmServiceImpl.java:96-108`. The MSG_NEW_4 row check runs before MSG40 and 403. An inactive Project's
   preview that still has Needs Solution rows therefore gets 409 MSG_NEW_4, not 409 MSG40. The spec orders only
   MSG40 before 403 (AC-003.5), so this is acceptable. Consider checking MSG40 first for a clearer message.
3. AC-003.3 says "return MSG38". The response holds `message` MSG32 `{created: 0}` **and** `notice` MSG38. The web
   app (Task 9) must show MSG38 alone in that case.

Assumptions made:
- AC-003.9's "after the transaction commits" is met by publishing inside the transaction to
  `@TransactionalEventListener(AFTER_COMMIT)` listeners, as `TicketsPublishedEvent` documents. This is the existing
  `import-ticket-list` pattern.

## Test Report
See `task-6-jira-finish-test-report.md`. 632 unit + 48 IT, 0 failures.
