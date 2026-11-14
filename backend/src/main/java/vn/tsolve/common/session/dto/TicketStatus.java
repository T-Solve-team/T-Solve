package vn.tsolve.common.session.dto;

/**
 * The Ticket status of the API ({@code TicketStatus} in the YAML). {@code common} imports no module, so this is a copy
 * of {@code vn.tsolve.ticket.entity.TicketStatus}; the names are kept equal by {@code TicketStatusParityTest}.
 */
public enum TicketStatus {
    PENDING_REVIEW,
    CHANGES_REQUESTED,
    PUBLISHED,
    REJECTED,
    UNPUBLISHED,
    EXPIRED
}
