---
module: ticket-review
feature: review-queue-entry-time
task: 8
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-review — Task 8: Record Review Queue entry time (KAN-24)

## Summary

Pass. `009-review-queue-entry-time.sql` adds nullable `ticket.review_queue_entered_at TIMESTAMPTZ`, as the interface contract requires. It does not backfill: historical rows stay null.

A `BEFORE INSERT OR UPDATE OF status` row trigger (`:8-21`) sets the column to `statement_timestamp()` only in two cases:
- a `SOLUTION_FORM` ticket is inserted as `PENDING_REVIEW`;
- its status changes from something else to `PENDING_REVIEW` (`OLD.status IS DISTINCT FROM NEW.status`).

Edits, a same-value status write (Hibernate writes all columns) and decisions leave the time alone. The JPA mapping is `insertable=false, updatable=false` (`Ticket.java:89-90`), so the application cannot overwrite it. `review_due_date` is untouched by the trigger and read-only in the entity, so the existing due-date rules stay with their owners.

Tests prove the following:
- a new entry records a time;
- title edits and a same-status update preserve it;
- a Request Changes decision preserves it;
- a return to Pending review records a later time and leaves the due date unchanged;
- a pending row that existed before the upgrade keeps null.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | First save and re-entry record the time. While the ticket stays Pending review the time is preserved. Unknown historical times stay null, so AC-009.12 can return `enteredQueueAt: null`. |
| Completeness | pass | AC-009.11 has named PostgreSQL tests for the lifecycle and the upgrade. |
| Correctness | pass | `TicketDecisionIT.recordsNewQueueEntriesButPreservesTheTimeDuringEdits_AC_009_11` and `ReviewStorageIT…AC_009_11…` pass at `77fa3a31` and on HEAD. |
| Integration | pass | `015` disables user triggers while it copies the legacy key, so the upgrade does not stamp rows with a migration time. The queue read (Task 9) exposes the column. |

## Traceability

`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-009` (2026-11-07):

```
✓ AC-009.11  backend/src/test/java/vn/tsolve/review/TicketDecisionIT.java
```

Also tagged: `ReviewStorageIT.upgradesExistingTicketsWithoutInventingQueueEntryTime_AC_009_9_AC_009_11_AC_004_13_BUG_140` (pre-upgrade Pending review row stays null).

## Issues

No CRITICAL or failing findings.

Notes (no effect on the verdict):

1. **NOTE:** `009-review-queue-entry-time.sql:19-21`. The trigger fires only when `status` is in the SET list. A direct SQL `UPDATE ticket SET review_queue_entered_at = …` (or `= NULL`) bypasses it and can change the time of a ticket that stays Pending review. No application path does this (the column is read-only in JPA), so AC-009.11 holds for the system. A stricter guard would also reject changes to the column itself when the status is unchanged.
2. **NOTE:** the lifecycle test drives the re-entry with raw SQL `update ticket set status = 'PENDING_REVIEW'` (`TicketDecisionIT.java:80,86`), because no resubmit or send-back service exists yet. The trigger makes the behavior independent of the caller, so this is acceptable, but a resubmit service added later should get its own test.

## Assumptions made

- "SHALL keep the existing review due date rules (GBR-08, GBR-10)" means the entry-time change must not alter `review_due_date`. Setting or recalculating it (for example GBR-10's recalculation on send-back, KAN-156) belongs to the flows that own it, and none exists in the production backend yet. The test asserts the due date is unchanged on re-entry, consistent with this reading. The user may correct.

## Test Report

See `task-8-review-queue-entry-time-test-report.md`.
