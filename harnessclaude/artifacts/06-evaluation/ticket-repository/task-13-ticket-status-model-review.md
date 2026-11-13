---
module: ticket-repository
feature: ticket-status-model
task: 13
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 13: Ticket status model (KAN-132, STORY-005)

## Summary
This review covers the uncommitted working tree of `feat/KAN-132-ticket-repository-ticket-status-model` (backend only).

- `TicketStatus.canMoveTo` holds the AC-005.1 table in an `EnumMap`. It has exactly 9 allowed pairs, and Rejected maps to an empty set.
- `Ticket.moveTo` throws `BusinessRuleException("MSG06")`, which `GlobalExceptionHandler` maps to 409. Otherwise it sets `status` and `updatedAt`. `applyReview` was split into `moveTo` plus `recordReviewFeedback`.
- `TicketTransitions.move` checks the whole batch before it changes any ticket. It then publishes one `TicketsPublishedEvent` or one `TicketsUnpublishedEvent` with every id.
- `TicketDecisionServiceImpl.decide` now goes through `move`. As a result, review Approve publishes `TicketsPublishedEvent`, and `TicketsPublishedListener` (AFTER_COMMIT, async) embeds approved tickets.

Regressions checked in `decide`:
- The MSG06 order is unchanged. `validateBatch` still runs first, in the order MSG04 → 403 → source → MSG06.
- The feedback fields are the same as before.
- `ReviewFeedbackDecidedEvent` is still published for non-Approve actions only.
- The event is published inside the transaction. If `decisions.saveAll` or the audit fails, the transaction rolls back and no listener runs.

Status writes that bypass the state machine:
- The only remaining ones are the two bulk SQL updates the design keeps: `ReviewExpiryRepository.expireOverdue` and `TicketRepository.sendBack`.
- `SearchTicket` is `@Immutable @Subselect`, so it cannot write a status.
- `Ticket` has no status setter, and `Ticket.imported` is creation, not a transition.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-005.1 table exact; AC-005.2 `Ticket.imported` → PUBLISHED; AC-005.3 check-all-then-move, MSG06/409; AC-005.4/005.5 one event per batch, none for other targets |
| Completeness | pass | 5/5 ACs traced; unpublish/re-publish endpoints out of scope per spec |
| Correctness | pass | 86 targeted unit tests green; `mvn spotless:check` clean; ITs not run here (Docker), verified separately by dispatcher |
| Integration | pass | Event id = `ticket.ticket_id`, same as import Finish and as `ticket_embedding.ticket_id`; three IT slices updated to import `TicketTransitions` |

## Traceability
```
  ✓ AC-005.1  backend/src/test/java/vn/tsolve/ticket/entity/TicketStatusTest.java
  ✓ AC-005.2  backend/src/test/java/vn/tsolve/ticket/entity/TicketStatusTest.java
  ✓ AC-005.3  backend/src/test/java/vn/tsolve/ticket/service/TicketStatusTransitionsTest.java, backend/src/test/java/vn/tsolve/ticket/entity/TicketStatusTest.java
  ✓ AC-005.4  backend/src/test/java/vn/tsolve/ticket/service/TicketStatusTransitionsTest.java
  ✓ AC-005.5  backend/src/test/java/vn/tsolve/ticket/service/TicketStatusTransitionsTest.java
trace ticket-repository (STORY-005): 5/5 acceptance criteria have a test
```

## Issues
No critical issues. All of the following are informational:

1. `backend/src/main/java/vn/tsolve/ticket/service/impl/TicketDecisionServiceImpl.java:86`: no test checks that `decide(APPROVED)` publishes `TicketsPublishedEvent`.
   - This was the bug that motivated STORY-005. AC-005.4 is tested only on `TicketTransitions`, so reverting `decide` to a direct status change would break no test.
   - Fix: in `TicketDecisionIT`, add `@RecordApplicationEvents` and assert one `TicketsPublishedEvent` with both ids after Approve, and none after Reject.
2. The `@Import` lists use fully qualified `vn.tsolve.ticket.service.TicketTransitions.class` instead of an import statement, in three places:
   - `backend/src/test/java/vn/tsolve/review/TicketDecisionIT.java:43`
   - `backend/src/test/java/vn/tsolve/analytics/AuditRecordingIT.java:62`
   - `backend/src/test/java/vn/tsolve/worker/service/impl/ReviewNotificationIT.java:56`

   Fix: import the class.
3. `backend/src/test/java/vn/tsolve/review/TicketDecisionIT.java:109,181,224` and `ReviewNotificationIT.java:204`: text-block calls were reformatted, which is not part of this feature.
   - `spotless:check` accepts the new form, but it adds noise to the diff.
   - Fix: revert these hunks if the team formatter accepts the old form too.
4. `backend/src/main/java/vn/tsolve/ticket/service/TicketTransitions.java:37`: the `List<UUID>` return value is not used by the only production caller.
   - Fix: return `void`. Alternatively keep it, if the UC-25 endpoints will use it soon.
5. `backend/src/test/java/vn/tsolve/ticket/entity/TicketStatusTest.java:48`: `rejectedIsFinal_AC_005_1` repeats what the 36-pair parameterized test already checks.
   - Fix: delete it (optional).

## Assumptions made
- In `decide`, the explicit Pending-review pre-check makes `TicketTransitions`' own MSG06 check unreachable. I accept this as the design intends ("it also guards the source rule").
- I treat the missing decide-level event test (issue 1) as informational, not as a fail, because AC-005.4 has a test that names it. The user may want it raised to a blocking issue.
- I did not run the integration tests here. `TicketDecisionIT`, `AuditRecordingIT`, `ReviewNotificationIT` and `ImportConfirmIT` need Docker, and the dispatcher verifies them separately. This PASS assumes they are green.

## Test Report
See `task-13-ticket-status-model-test-report.md`. TicketStatusTest 40/40, TicketStatusTransitionsTest 4/4,
TicketsPublishedListenerTest 1/1, ImportConfirmServiceImplTest 22/22, JiraFinishTest 19/19; spotless:check clean.
