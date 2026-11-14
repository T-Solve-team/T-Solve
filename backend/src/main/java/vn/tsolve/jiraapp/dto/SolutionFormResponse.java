package vn.tsolve.jiraapp.dto;

import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.dto.SolutionSections;

/**
 * Whether the Solution Form may open and, if so, what it opens with: the user's Draft, the AI draft, or the issue
 * text with MSG67 (jira-app AC-001.2, AC-002.1, AC-002.2, AC-005.2). Origin and sections are null when it may not.
 */
public record SolutionFormResponse(boolean mayOpen, Origin origin, SolutionSections sections, MessageRef message) {

    public enum Origin {
        DRAFT,
        AI_DRAFT,
        ISSUE
    }

    public static SolutionFormResponse closed() {
        return new SolutionFormResponse(false, null, null, null);
    }
}
