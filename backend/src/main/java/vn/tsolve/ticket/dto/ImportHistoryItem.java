package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import vn.tsolve.ticket.entity.ImportBatchStatus;
import vn.tsolve.ticket.entity.TicketSource;

public record ImportHistoryItem(
        long batchId,
        Instant createdAt,
        String performedBy,
        Project project,
        Department department,
        TicketSource source,
        LocalDate dateFrom,
        LocalDate dateTo,
        String fileName,
        int publishedCount,
        int removedCount,
        int errorCount,
        ImportBatchStatus status,
        String errorCode) {
    public record Project(UUID projectId, String projectKey) {}

    public record Department(UUID id, String name) {}
}
