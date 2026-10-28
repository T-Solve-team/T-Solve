# Task 12: Retire the old snapshot import

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 21:30

### Working on: retire-snapshot-import
- Caller check before deleting: grep for `ResolvedTicket`, `ImportTicketRequest`, `TicketService`, `TicketController`, `/api/tickets` and `resolved_ticket_snapshot` across `backend/src`, `frontend/src`, `frontend/openapi` and `jira-app` (Java, TS/TSX, YAML, JSON). Nothing outside the five files themselves.
- Deleted with `git rm`: `ticket/ImportTicketRequest.java`, `ResolvedTicketRepository.java`, `ResolvedTicketSnapshot.java`, `TicketController.java`, `TicketService.java`. This removes `GET /api/tickets`, `GET /api/tickets/{id}` and `POST /api/tickets/import`, the idempotent upsert that conflicted with MSG31 (design D2).
- Kept the `resolved_ticket_snapshot` table (no changeset): `solution_source` in the `knowledge` package still has an FK to it. Hibernate `validate` only checks mapped entities, so the unmapped table is harmless. Dropping it is a later cleanup together with the `knowledge` rework.
- Housekeeping: the Task 11 status change to `evaluating` crashed inside Bun again (Bun 1.3.14 panic) and was re-run here; it goes in this task's harness commit.
- Verify: `mvn verify -DargLine="-Dnet.bytebuddy.experimental=true"` → exit 0; 87 tests, 0 failures. No tests were removed, because the old classes had none.

- Commit: default@4bbbc4a (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Task 13 csv-confirm-db-tests.
