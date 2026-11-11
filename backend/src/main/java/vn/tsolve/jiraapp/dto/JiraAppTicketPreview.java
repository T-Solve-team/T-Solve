package vn.tsolve.jiraapp.dto;

import java.util.UUID;
import vn.tsolve.ticket.dto.SolutionSections;

/** 3.4 Published Ticket Preview (jira-app AC-009.1); {@code webUrl} is the "Open in T-Solve" link. */
public record JiraAppTicketPreview(
        UUID id, String ticketId, String title, String departmentName, SolutionSections solution, String webUrl) {}
