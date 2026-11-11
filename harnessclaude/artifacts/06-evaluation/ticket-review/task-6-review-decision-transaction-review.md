---
module: ticket-review
feature: review-decision-transaction
task: 6
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: ticket-review — Task 6: Atomic Review decisions (KAN-24)

## Summary

Pass. `TicketDecisionServiceImpl.decide` (`backend/src/main/java/vn/tsolve/ticket/service/impl/TicketDecisionServiceImpl.java:44-79`) runs in one `@Transactional` boundary inside the Ticket module. It works in this order:
1. Validates the whole input: 1–500 unique non-null ids, and nonblank feedback of at most 1,000 characters for Reject and Request Changes.
2. Locks every requested row `PESSIMISTIC_WRITE` in a deterministic order (`TicketRepository.java:24-26`).
3. Refuses the entire batch if anything is missing (404 MSG04), outside the managed Projects (403 MSG08), imported (MSG18 for Request Changes, otherwise 409 MSG06) or no longer Pending review (409 MSG06).
4. Only then mutates every ticket and saves one `review_decision` per ticket. Each decision carries the reviewer, action, time and stripped feedback.

The writes are limited to status, the feedback columns and `updated_at`. `review_due_date`, `review_queue_entered_at`, `writer_id` and the Solution columns are mapped `updatable=false`, or not written at all, so they are preserved. Evidence is never touched.

The PostgreSQL tests prove the following:
- each action commits (manager-authored ticket included);
- invalid, missing, unmanaged, stale and imported batches leave every ticket and the history unchanged;
- a failing decision insert rolls back the ticket update, and a failing second ticket update rolls back the first ticket and all decisions;
- of two concurrent competing decisions, exactly one commits.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Approve → PUBLISHED (out of the queue, which filters `PENDING_REVIEW`), Reject → REJECTED with `rejection_reason`, Request Changes → CHANGES_REQUESTED with `change_request_comment`. The same text goes into each decision row. A manager may approve a ticket they wrote (`TicketDecisionIT.java:95`, `writer_id = reviewerId`). |
| Completeness | pass | AC-009.4, .5, .6 (the decision part), .7 and .8 each have a named service-level PostgreSQL test. The Jira MSG64 half of AC-009.6 belongs to Task 10. |
| Correctness | pass | `TicketDecisionIT` passes 8/8 at `77fa3a31`. Lock ordering (`order by t.id`) prevents deadlocks between overlapping batches. Under READ COMMITTED the waiter re-reads the committed status and fails with MSG06 (concurrency test `TicketDecisionIT.java:200`). |
| Integration | pass | Accepts a reviewer and a managed Project set from a trusted caller (Task 7 controller). Accepts the `ticket_id` or Search `public_id` of a ticket and records decisions against `ticket_id`. |

## Traceability

`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-009` (2026-11-07):

```
✓ AC-009.4  ReviewDecisionApiIT.java, TicketDecisionIT.java
✓ AC-009.5  ReviewDecisionApiIT.java, TicketDecisionIT.java
✓ AC-009.6  JiraReviewFeedbackControllerTest.java, ReviewDecisionApiIT.java, TicketDecisionIT.java
✓ AC-009.7  ReviewDecisionApiIT.java, TicketDecisionIT.java
✓ AC-009.8  ReviewDecisionApiIT.java, TicketDecisionIT.java
```

Named tests: `commitsDecisionsAndPreservesTicketContent_AC_009_4_AC_009_5_AC_009_6_AC_009_8` (x3 actions), `refusesInvalidMissingUnmanagedAndStaleBatches_AC_009_7_BUG_139`, `rollsBackTicketStateIfDecisionCannotBeSaved_AC_009_8`, `rollsBackDecisionsIfAnyTicketUpdateFails_AC_009_8`, `commitsOnlyOneOfTwoCompetingDecisions_AC_009_7_AC_009_8`.

## Issues

No CRITICAL or failing findings.

Warnings (no effect on the verdict):

1. **WARNING:** `TicketDecisionServiceImpl.java:50-56,85-87`. Whole-batch input errors (duplicate ids, empty or >500 ids, Unicode-only blank feedback that passes `@NotBlank`) throw a bare `ResponseStatusException(BAD_REQUEST)`. `GlobalExceptionHandler.createResponseEntity` gives these the generic code MSG06 with no `fieldErrors`. The interface contract asks for catalog validation Problems that preserve field errors. In practice the frontend never sends duplicates, but a 400 coded "unexpected error" is misleading. The service also imports a web-layer exception. Suggested fix: throw a `MessageCodeException(BAD_REQUEST, "MSG01"/"MSG02", field="ticketIds"|"reason"|"comment")` or a validation exception carrying the field.
2. **WARNING:** `TicketDecisionServiceImpl.java:85`. Feedback length is checked before `strip()`, so a 1,000-character reason with surrounding spaces is refused although the stored value would fit. `String.isBlank()` also does not treat U+00A0 (no-break space) as blank, so an NBSP-only reason is accepted as "nonblank". The bean annotations behave the same way, so the behavior is consistent, but either may contradict the frontend form convention. See Assumptions.
3. **WARNING:** `TicketDecisionServiceImpl.java:73-78`. Approve sets PUBLISHED but publishes no `TicketsPublishedEvent`, unlike import (`ImportConfirmServiceImpl.java:188`). Search embedding then relies on `EmbeddingCatchUpJob` (every 60 s by default), so an approved Solution reaches semantic search up to a minute late. AC-009.4 only requires "publish … and remove from the queue", which is met. Suggested fix: publish the event after commit for approved ids.
4. **WARNING:** test gap. No test sends an empty list or 501 ids to the service or the API. The bounds are enforced by `TicketDecisionServiceImpl.java:50` and `@NotEmpty @Size(max = 500)`, but nothing exercises them.

## Assumptions made

- "At most 1,000 characters" in AC-009.5/.6 is measured on the submitted value (before trimming), and "nonblank" follows Java `isBlank()` semantics. The user should confirm, or ask for trim-then-measure plus NBSP handling as in the frontend.
- A competing decision losing the race with 409 MSG06 ("no longer eligible") satisfies AC-009.7. The spec does not name a code for this, and the contract maps stale to 409.

## Test Report

See `task-6-review-decision-transaction-test-report.md`. `TicketDecisionIT` 8/8 at `77fa3a31` (run by the reviewer) and on HEAD (full verify, 0 failures).
