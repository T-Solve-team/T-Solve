package vn.tsolve.ticket.event;

import java.util.List;
import java.util.UUID;

/**
 * Tickets became Unpublished (ticket.ticket_id values), e.g. to drop them from search. It is published inside the
 * request's transaction, so listen with {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: a plain
 * {@code @EventListener} would run before the commit, and a failure there would undo the change.
 */
public record TicketsUnpublishedEvent(List<UUID> ticketIds) {}
