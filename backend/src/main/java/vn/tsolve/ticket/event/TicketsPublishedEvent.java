package vn.tsolve.ticket.event;

import java.util.List;
import java.util.UUID;

/**
 * Tickets became Published (ticket.ticket_id values), e.g. for search indexing. It is published inside the import
 * transaction, so listen with {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: a plain {@code @EventListener}
 * would run before the commit, and a failure there would undo the import.
 */
public record TicketsPublishedEvent(List<UUID> ticketIds) {}
