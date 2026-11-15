package vn.tsolve.jiraapp.dto;

import java.util.UUID;
import vn.tsolve.ticket.entity.TicketStatus;

/** The Solution Form ticket just created (jira-app AC-004.1). */
public record SubmittedTicketResponse(UUID id, String ticketId, TicketStatus status) {}
