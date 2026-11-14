package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.util.UUID;

/** What the writer email of one Reject or Request Changes decision needs (STORY-010). */
public record ReviewNotification(
        Kind kind,
        String recipient,
        String ticketKey,
        String feedback,
        UUID ticketId,
        String ticketTitle,
        String projectName,
        String reviewerName,
        String writerName,
        Instant decidedAt) {

    public enum Kind {
        REJECTED,
        CHANGES_REQUESTED
    }
}
