package vn.tsolve.jiraapp.dto;

import java.util.UUID;

/** One Published ticket in the Issue Panel's search results (jira-app AC-008.4). */
public record JiraAppTicketResult(UUID id, String ticketId, String title, String departmentName) {}
