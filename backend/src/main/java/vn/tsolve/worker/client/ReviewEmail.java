package vn.tsolve.worker.client;

import java.time.Instant;

/** Shared facts for the HTML and plain-text alternatives of a review email. */
public record ReviewEmail(
        Decision decision,
        String ticketKey,
        String ticketTitle,
        String projectName,
        String reviewerName,
        String writerName,
        Instant decidedAt,
        String feedback,
        String ticketUrl,
        String workspaceName) {
    public enum Decision {
        REJECT,
        REQUEST_CHANGES
    }
}
