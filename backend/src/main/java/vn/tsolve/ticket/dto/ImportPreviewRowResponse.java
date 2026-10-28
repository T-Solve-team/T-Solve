package vn.tsolve.ticket.dto;

import java.util.List;
import vn.tsolve.ticket.csv.RowError;
import vn.tsolve.ticket.entity.PreviewRowStatus;

/** One preview row in full, read-only (Import Ticket Preview popup). */
public record ImportPreviewRowResponse(
        int rowNo,
        String ticketId,
        String title,
        String description,
        String author,
        String resolvedDate,
        String symptoms,
        String rootCause,
        String resolutionSteps,
        String workaround,
        String scope,
        PreviewRowStatus status,
        List<RowError> errors) {}
