package vn.tsolve.ticket.service;

import java.util.Collection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import vn.tsolve.ticket.dto.FillSolutionRequest;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.entity.PreviewRowStatus;

/** Reading and trimming the caller's own Import Preview. Another user's preview is reported as not found. */
public interface ImportPreviewService {

    /** Counts, MSG30 and the first page of rows, as the upload returned them (AC-007.1). */
    ImportPreviewResponse preview(String user, long previewId);

    /** One page of rows by row number; status null = all rows. Pages hold at most 100 rows. */
    PagedModel<ImportPreviewRowSummary> rows(String user, long previewId, PreviewRowStatus status, Pageable page);

    ImportPreviewRowResponse row(String user, long previewId, int rowNo);

    /** Marks the rows Removed (Valid or Error alike); row numbers not in the preview are ignored. */
    ImportPreviewResponse removeRows(String user, long previewId, Collection<Integer> rowNumbers);

    /**
     * Stores one Solution, trimmed, in each given row of a preview of a file without Solutions; each row becomes
     * Valid. All rows or none (STORY-013).
     */
    ImportPreviewResponse fillSolution(String user, long previewId, FillSolutionRequest solution);
}
