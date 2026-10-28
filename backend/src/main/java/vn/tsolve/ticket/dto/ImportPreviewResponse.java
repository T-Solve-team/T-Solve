package vn.tsolve.ticket.dto;

import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;

/** The Import Preview: counts, MSG30 when some rows are Error, and one page of rows. */
public record ImportPreviewResponse(
        long previewId,
        long projectId,
        String fileName,
        long validCount,
        long errorCount,
        long removedCount,
        MessageRef message,
        PagedModel<ImportPreviewRowSummary> rows) {

    public static ImportPreviewResponse of(
            long previewId,
            long projectId,
            String fileName,
            long validCount,
            long errorCount,
            long removedCount,
            Page<ImportPreviewRowSummary> rows) {
        MessageRef message = errorCount > 0 ? new MessageRef("MSG30", Map.of("count", errorCount)) : null;
        return new ImportPreviewResponse(
                previewId, projectId, fileName, validCount, errorCount, removedCount, message, new PagedModel<>(rows));
    }
}
