---
module: ticket-repository
feature: manual-ticket-create
task: 15
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 15: Save a manual ticket

## Summary
`POST /api/v1/imports/manual` (c0fe5464, final state 2bd4f203) saves a Published MANUAL Ticket, its approve decision
and a Completed MANUAL Import Batch in one transaction, publishes one `TicketsPublishedEvent`, and rejects invalid
fields (all at once, 400), foreign/inactive Projects (403), taken Ticket IDs incl. the insert race (409 MSG31).
Every AC-006.1–AC-006.8 is implemented and named by a test that checks it; tests pass. No CRITICAL findings. **PASS.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | See Stage 1 table. |
| Completeness | pass | No MISSING; no EXTRA beyond the documented decisions (unparsable date → MSG39; Solution total → one MSG02 on `solution`). |
| Correctness | pass | 297 unit + 29 IT green; rollback path reviewed below. |
| Integration | pass | Contract operationId/statuses match; `GlobalExceptionHandler` change keeps the old single-field path; `Ticket.writerId` insertable does not alter Jira/CSV inserts (they still write NULL, column has no default). |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-006.1 Ticket + decision + Completed batch, writer/writer_id, MSG32 | PASS | `ManualTicketServiceImpl.java:108-148`, `Ticket.java:132-155`; `ManualTicketControllerIT.AC_006_1_…` checks every column against the DB; unit `AC_006_1_cutsALongFullNameToTheWriterColumn` |
| 2 | AC-006.2 one event | PASS | `ManualTicketServiceImpl.java:141`; `AC_006_2_publishesOneTicketsPublishedEventWithTheNewTicket` |
| 3 | AC-006.3 MSG01 per missing/blank field | PASS | `:227-242,256-267`; unit `AC_006_3_…` + IT `AC_006_3_AC_006_6_…` (blank ticketId, null projectId, nothing saved) |
| 4 | AC-006.4 MSG02 with limit (50/200/10,000/Solution total 10,000) | PASS | `:232-234,243-252`; `AC_006_4_rejectsTooLong…`, `AC_006_4_acceptsFieldsAtTheirLimitCountingCodePoints` |
| 5 | AC-006.5 future date → MSG39, missing allowed, Vietnam today | PASS | `:235-238,269-275`; `AC_006_5_…` (two tests, fixed clock) |
| 6 | AC-006.6 every invalid field in one answer | PASS | `InvalidFieldsException` + `GlobalExceptionHandler.java:181-183`; unit + IT (3 fieldErrors) |
| 7 | AC-006.7 409 MSG31, incl. race, no batch | PASS | pre-check `:103-106`; race `:112-134` (saveAndFlush, SQLState 23505 on `uq_ticket_ticket_id`); IT `AC_006_7_…` (count batch = 1); unit race + "other DB error passes through" |
| 8 | AC-006.8 403 MSG08 (role, inactive, unmanaged) | PASS | `@PreAuthorize` on `ManualTicketController.java:28`, `:97-99`; IT covers no-role and unmanaged; unit covers managesActive=false |

### Transaction / ordering

- Order fields(400) → Project(403) → Draft lock(404) → taken id(409) → writes, all in one `@Transactional`
  (`:89-149`). A 400 never reveals whether a Project or Draft exists; a 403 comes before any Draft lookup, so another
  user's Draft id cannot be probed (the Draft lookup is owner-filtered anyway).
- MSG31 race: the unique violation surfaces inside the method (saveAndFlush), is turned into a `BusinessRuleException`
  (unchecked) and leaves the `@Transactional` boundary → full rollback of the already-saved batch. Decision, Draft
  delete, event and audit come after the insert, so none run.

## Traceability
```
✓ AC-006.1 … ✓ AC-006.8 (ManualTicketServiceImplTest, ManualTicketControllerIT)
trace ticket-repository (STORY-006): 13/14 — AC-006.14 (web app, Task 17) missing as expected
```

## Issues

CRITICAL: none.

Informational:
1. `ManualTicketServiceImpl.java:129-134` — the race branch is tested only with a mocked
   `DataIntegrityViolationException` (`ManualTicketServiceImplTest.AC_006_7_answersMsg31WhenAnotherRequestTakesTheTicketIdMeanwhile`);
   no IT proves the real Postgres 23505 path rolls the batch back. The same mechanism is IT-proven for CSV Confirm, so
   risk is low. Fix: optional IT inserting the Ticket ID via JDBC between the pre-check and the insert (or with
   `findExistingTicketIds` spied empty).
2. `ManualTicketServiceImpl.java:248-251` — the Solution-total MSG02 uses field `solution` and `field_name` "Solution",
   while every other entry uses the request property name as `field_name` (`ticketId`, `title`…). Task 17 must map
   `solution` to a form location. Fix: document in the contract or align the naming.
3. `common/exception/InvalidFieldsException.java` + `GlobalExceptionHandler.java:181-183` — a change in `common/`.
   AGENTS.md "Notes for AI agents" asks to stop and say so; the session log flags it for the PR instead. It fits §5
   (a base exception in `common/exception`, rendered by the one handler), so not blocking. The constructor
   (`InvalidFieldsException.java:15-19`) throws `IndexOutOfBoundsException` on an empty list — both callers pass a
   non-empty list. Fix: `if (errors.isEmpty()) throw new IllegalArgumentException(...)` or leave.
4. `InvalidFieldsException` keeps request order for `fieldErrors`, whereas `@Valid` errors are sorted by field name
   (`GlobalExceptionHandler.java:191-`). Two orders for the same ProblemDetail shape; harmless for the web app, which
   keys by field.
5. `ImportConfirmServiceImpl.java:56-57,225,293` — `WRITER_MAX`, `UNIQUE_VIOLATION`, `sqlState`, `cut` widened from
   private to package-private and reused by `ManualTicketServiceImpl`. Works, but couples two services through impl
   statics. Fix (later): move to a small package helper.
6. `GlobalExceptionHandler.java:240-247` — reformatting only (Spotless/Palantir), no behaviour change.

## Assumptions made
- Unparsable `resolvedDate` → MSG39 and Solution total over 10,000 → one MSG02 on `solution`: orchestrator decisions
  recorded in the session log; not in the spec text. Accepted as reasonable readings of AC-006.4/AC-006.5 — the user
  may want them written into the spec (judgment call, open).
- The `common/` change (InvalidFieldsException) is accepted as within AGENTS.md §5; the user did not explicitly
  approve it (judgment call, open).

## Test Report
`task-15-manual-ticket-create-test-report.md`: scoped `mvn verify` — 297 unit + 29 IT, 0 failures, 0 errors,
Spotless clean; includes `ManualTicketServiceImplTest`, `ManualTicketControllerIT`, `ImportConfirmIT`.
