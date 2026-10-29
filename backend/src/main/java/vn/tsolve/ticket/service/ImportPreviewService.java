package vn.tsolve.ticket.service;

import java.util.Collection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import vn.tsolve.ticket.dto.ImportPreviewResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.entity.PreviewRowStatus;

/** Reading and trimming the caller's own Import Preview. Another user's preview is reported as not found. */
public interface ImportPreviewService {

    /** One page of rows by row number; status null = all rows. Pages hold at most 100 rows. */
    PagedModel<ImportPreviewRowSummary> rows(String user, long previewId, PreviewRowStatus status, Pageable page);

    ImportPreviewRowResponse row(String user, long previewId, int rowNo);

    /** Marks the rows Removed (Valid or Error alike); row numbers not in the preview are ignored. */
    ImportPreviewResponse removeRows(String user, long previewId, Collection<Integer> rowNumbers);
}
