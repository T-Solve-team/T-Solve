package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TicketReviewView {

    private TicketReviewView() {}

    public record Project(UUID projectId, String name) {}

    public record ProjectAccess(Project project, UUID departmentId, boolean managed) {}

    public record QueueRow(
            UUID ticketId,
            String sourceTicketId,
            long projectId,
            String title,
            String authorName,
            Instant enteredQueueAt,
            LocalDate reviewDueDate) {}

    public record QueueItem(
            UUID ticketId,
            String sourceTicketId,
            String title,
            Project project,
            String source,
            String authorName,
            Instant enteredQueueAt,
            OffsetDateTime reviewDueDate) {}

    public record Solution(
            String symptoms, String rootCause, String resolutionSteps, String workaround, String scope) {}

    public record Evidence(UUID id, String filename, long sizeBytes, String url) {}

    public record Detail(
            UUID ticketId,
            String sourceTicketId,
            String title,
            String description,
            Project project,
            String source,
            String status,
            UUID authorId,
            String authorName,
            OffsetDateTime reviewDueDate,
            Solution solution,
            List<Evidence> evidence,
            String lastReviewComment) {}
}
