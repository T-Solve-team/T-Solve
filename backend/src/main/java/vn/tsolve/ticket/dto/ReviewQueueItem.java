package vn.tsolve.ticket.dto;

import java.time.Instant;

public record ReviewQueueItem(
        Long ticketId,
        String sourceTicketId,
        String title,
        ProjectRef project,
        ReviewSource source,
        String authorName,
        Instant enteredQueueAt,
        Instant reviewDueDate) {
    public record ProjectRef(long projectId, String name) {}
}
