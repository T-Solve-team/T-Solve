package vn.tsolve.review.dto;

import java.time.Instant;

public record PendingReviewNotification(
        Long id, Long decisionId, String recipientId, ReviewAction action, String comment, Instant createdAt) {}
