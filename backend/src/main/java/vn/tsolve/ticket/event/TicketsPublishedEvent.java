package vn.tsolve.ticket.event;

import java.util.List;

/** Tickets became Published (ticket.id values); sent after the import committed, e.g. for search indexing. */
public record TicketsPublishedEvent(List<Long> ticketIds) {}
