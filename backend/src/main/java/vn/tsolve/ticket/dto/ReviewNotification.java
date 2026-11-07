package vn.tsolve.ticket.dto;

/** What the writer email of one Reject or Request Changes decision needs (STORY-010). */
public record ReviewNotification(Kind kind, String recipient, String ticketKey, String feedback) {

    public enum Kind {
        REJECTED,
        CHANGES_REQUESTED
    }
}
