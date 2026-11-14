package vn.tsolve.ticket.exception;

import java.util.Map;
import vn.tsolve.common.exception.BusinessRuleException;

/**
 * A ticket with this Ticket ID already exists, so a Solution Form submission creates no second one (MSG31, 409,
 * jira-app AC-004.3).
 */
public class TicketAlreadyExistsException extends BusinessRuleException {
    public TicketAlreadyExistsException(String ticketId) {
        super("MSG31", Map.of("ticket_id", ticketId));
    }
}
