package vn.tsolve.ticket.dto;

import java.util.UUID;
import vn.tsolve.ticket.entity.TicketStatus;

/** The Solution Form ticket just created: its UUID, its Ticket ID (the issue key) and its status. */
public record SubmittedSolutionTicket(UUID id, String ticketId, TicketStatus status) {}
