package vn.tsolve.jiraapp.dto;

import vn.tsolve.ticket.entity.TicketStatus;

/**
 * What 3.3 Issue Panel shows for the issue and the viewer (jira-app STORY-001, STORY-008). {@code linked} false =
 * MSG62 only; {@code ticketStatus} null = the issue has no ticket.
 */
public record IssuePanelResponse(
        boolean linked, TicketStatus ticketStatus, boolean hasDraft, boolean mayOpenForm, boolean canSearch) {

    public static IssuePanelResponse notLinked() {
        return new IssuePanelResponse(false, null, false, false, false);
    }
}
