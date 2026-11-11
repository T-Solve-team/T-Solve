package vn.tsolve.ticket.dto;

import java.util.UUID;

/** A Published ticket as the Jira App shows it: its public id, Ticket ID, title, Department and Solution. */
public record PublishedTicketView(
        UUID id, String ticketId, String title, UUID departmentId, String departmentName, SolutionSections solution) {}
