# Task 15: manual-ticket-create

**Module:** ticket-repository · **Assignee:** harnessclaude-developer (Claude) · **Status:** evaluating

## Session 2026-11-14
### Working on: manual-ticket-create
- ManualTicketController / ManualTicketService(Impl): POST /api/v1/imports/manual. Order: fields (400, all at once) → Project (403) → Draft lock (404) → taken id (409 MSG31) → save.
- New common/exception/InvalidFieldsException (400, fieldErrors in request order); GlobalExceptionHandler puts its list into fieldErrors. A change in common/ (AGENTS.md asks to flag it): flagged in the PR.
- Ticket.manual(...) sets writer = fullName (cut 100) and writer_id = caller id; writer_id insertable.
- MSG31 as BusinessRuleException with a fieldErrors entry on ticketId; unique-violation race via saveAndFlush → MSG31, all rolled back.
- Decisions: unparsable resolvedDate → MSG39; Solution total > 10,000 → one MSG02 on field "solution"; MSG01/MSG02 field_name = request property name.
- Shared TicketTestDatabase container for ManualTicketControllerIT and ImportConfirmIT (AGENTS.md §8).
- Verify: mvn verify 1539 unit + 488 IT, 0 failures (developer). Orchestrator re-ran Manual*/ImportDraft*/ImportConfirm* unit + ITs: all pass.
- Commit: default@c0fe5464
