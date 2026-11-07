# Task 6: Atomic Review decisions

**Module:** ticket-review
**Assignee:** Phạm Tùng Dương
**Status:** evaluating
**Iteration:** 1

## Session 2026-11-07 06:20

### Working on: review-decision-transaction

- Added an internal Ticket decision service and Review decision persistence. The service locks tickets in UUID order, validates the entire batch and writes ticket state and history in one transaction.
- The service accepts a trusted Workspace Account and managed Project IDs. It uses the existing internal numeric Project keys until the production Project integration is available.
- PostgreSQL tests cover all three decisions, preservation of Solution, Evidence and due date, invalid and out-of-scope batches, concurrent decisions, and rollback when either the decision insert or a ticket update fails.
- The initial integration test failed before the service was implemented. After implementation and restarting the local Docker engine, `mvn -B -Dapi.version=1.44 verify` passed 528 unit tests and 46 integration tests with zero failures, errors or skips. All seven TicketDecisionIT cases passed. `mvn -B spotless:check` passed.
- Trace links AC-009.4–009.8 to the PostgreSQL cases. These tests cover the transaction portion. HTTP authentication, routing, web integration and the Jira visibility clause of AC-009.6 are outside this task.
- Code commit: `default@32918fb`.

### Blockers

Production integration requires trusted Workspace routing and Project membership.

### Next up

Evaluate the transaction, then connect it to the Review API under the production authorization boundary.
