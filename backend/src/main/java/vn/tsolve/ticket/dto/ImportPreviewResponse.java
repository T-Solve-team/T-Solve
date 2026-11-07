package vn.tsolve.ticket.dto;

import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;
import vn.tsolve.ticket.entity.ImportType;

/** The Import Preview: counts, MSG30 when some rows are Error, and one page of rows. */
public record ImportPreviewResponse(
        long previewId,
        UUID projectId,
        String fileName,
        ImportType importType,
        long validCount,
        long errorCount,
        long removedCount,
        long needsSolutionCount,
        MessageRef message,
        PagedModel<ImportPreviewRowSummary> rows) {

    public static ImportPreviewResponse of(
            long previewId,
            UUID projectId,
            String fileName,
            ImportType importType,
            long validCount,
            long errorCount,
            long removedCount,
            long needsSolutionCount,
            Page<ImportPreviewRowSummary> rows) {
        MessageRef message = errorCount > 0 ? new MessageRef("MSG30", Map.of("count", errorCount)) : null;
        return new ImportPreviewResponse(
                previewId,
                projectId,
                fileName,
                importType,
                validCount,
                errorCount,
                removedCount,
                needsSolutionCount,
                message,
                new PagedModel<>(rows));
    }
}
