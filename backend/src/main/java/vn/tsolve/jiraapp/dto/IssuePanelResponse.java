package vn.tsolve.jiraapp.dto;

import vn.tsolve.ticket.entity.TicketStatus;

/**
 * What 3.3 Issue Panel shows for the issue and the viewer (jira-app STORY-001, STORY-008). {@code linked} false =
 * MSG62 only; {@code ticketStatus} null = the issue has no ticket; {@code mayResubmit} shows "Edit and resubmit"
 * (STORY-012).
 */
public record IssuePanelResponse(
        boolean linked, TicketStatus ticketStatus, boolean hasDraft, boolean mayOpenForm, boolean mayResubmit) {

    /** A panel without "Edit and resubmit" (AC-012.1). */
    public IssuePanelResponse(boolean linked, TicketStatus ticketStatus, boolean hasDraft, boolean mayOpenForm) {
        this(linked, ticketStatus, hasDraft, mayOpenForm, false);
    }

    public static IssuePanelResponse notLinked() {
        return new IssuePanelResponse(false, null, false, false, false);
    }
}
