package vn.tsolve.ticket.dto;

import java.util.UUID;

/**
 * A status change of a Project's Jira issue (jira-app AC-005.7, AC-005.9): its new Done state and, on a move into
 * Done, the Draft to create for {@code draftOwner} ({@code null} when none is created).
 */
public record IssueStatusChange(
        UUID projectId, String issueKey, boolean done, UUID draftOwner, SolutionSections draftSections) {}
