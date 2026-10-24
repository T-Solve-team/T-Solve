# Task 1: Ticket List with filters, on mocks

**Module:** search
**Assignee:** Trần Hải Anh
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-24 11:30

### Working on: ticket-list (KAN-77, branch `feature/KAN-77-search-ticket-list`)
- Contract: tag `tickets`, `GET /api/v1/tickets` (`getTickets`) with `keyword`, `departmentId`, `source`, `status`
  and the shared paging; `TicketStatus`, `TicketSummary`, `TicketSummaryPage`; client regenerated.
- Mocks: `ticket-data.ts` — 26 tickets, the eight Figma 6.2 rows first (NETOPS / TCK keys renamed to the mock Projects
  ITNET / ITSUP, ids as in `src/mocks/session.ts`); the handler filters (keyword on ID, title, Solution), sorts and
  pages; the Solution text is never returned.
- Screen: `TicketListFilters` (`Input.Search` keyed by the applied keyword, so back/forward resets the draft; three
  `Select`s with the "All …" option), `TicketListTable` (8 columns, 5 sortable, "—" for an empty review due date),
  `TicketListPage` (`useListSearchParams`, URL values checked against the enums so a bad link sends no invalid value).
- Decision (builder): source and status labels come from `TICKET_SOURCE_LABELS` and `STATUS_TAGS`; no new shared code.
- Trace: `sdd.ts trace search STORY-001 STORY-002` → 15/15 acceptance criteria have a test.
- Verify (`frontend/`): `format:check` ✓, `lint` ✓, `typecheck` ✓, `build` ✓, `generate:api` no further diff ✓;
  `vitest run src/features/tickets` 24/24 ✓. Full `npm run test`: 11 review/chatbot tests time out at ~15 s, a different
  set each run; two of them fail alone on `main` with the KAN-77 changes stashed → logged as BUG-008 (not caused by
  this task).
- Commit: default@bd1f914 (plan: bb8c3b7).

### Next up
Evaluate ticket-list (harnessclaude-evaluate); the backend `search` package and 6.1 / 8.1 come in a later change.
