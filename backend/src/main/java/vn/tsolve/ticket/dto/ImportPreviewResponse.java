package vn.tsolve.ticket.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportType;
import vn.tsolve.ticket.entity.TicketSource;

/**
 * The Import Preview: counts, MSG30 when some rows are Error, MSG35 when more Jira issues match the range, and one
 * page of rows. fileName is null for a Jira preview; resolvedFrom / resolvedTo only for one.
 */
public record ImportPreviewResponse(
        long previewId,
        UUID projectId,
        String fileName,
        ImportType importType,
        TicketSource source,
        LocalDate resolvedFrom,
        LocalDate resolvedTo,
        Instant draftSavedAt,
        long validCount,
        long errorCount,
        long removedCount,
        long needsSolutionCount,
        MessageRef message,
        PagedModel<ImportPreviewRowSummary> rows) {

    public static ImportPreviewResponse of(
            ImportPreview preview,
            long validCount,
            long errorCount,
            long removedCount,
            long needsSolutionCount,
            Page<ImportPreviewRowSummary> rows) {
        MessageRef message = null;
        if (errorCount > 0) {
            message = new MessageRef("MSG30", Map.of("count", errorCount));
        } else if (preview.isMoreAvailable()) {
            message = new MessageRef("MSG35", Map.of());
        }
        return new ImportPreviewResponse(
                preview.getId(),
                preview.getProjectId(),
                preview.getFileName(),
                preview.getImportType(),
                preview.getSource(),
                preview.getResolvedFrom(),
                preview.getResolvedTo(),
                preview.getDraftSavedAt(),
                validCount,
                errorCount,
                removedCount,
                needsSolutionCount,
                message,
                new PagedModel<>(rows));
    }
}
