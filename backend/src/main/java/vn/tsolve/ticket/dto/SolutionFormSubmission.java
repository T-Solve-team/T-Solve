package vn.tsolve.ticket.dto;

import java.util.UUID;

/**
 * A Solution Form submission (jira-app STORY-004, STORY-006), after the caller checked that the writer is a member of
 * the Project: the issue key becomes the Ticket ID, the issue summary the title.
 */
public record SolutionFormSubmission(
        UUID projectId, String issueKey, String title, UUID writerId, String writerName, SolutionSections sections) {}
