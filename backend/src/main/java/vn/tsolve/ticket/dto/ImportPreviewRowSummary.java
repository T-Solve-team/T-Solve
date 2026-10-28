package vn.tsolve.ticket.dto;

import java.util.List;
import vn.tsolve.ticket.csv.RowError;
import vn.tsolve.ticket.entity.PreviewRowStatus;

/** One preview row in a list: values as read from the file, Solution sections cut to 200 characters. */
public record ImportPreviewRowSummary(
        int rowNo,
        String ticketId,
        String title,
        String author,
        String resolvedDate,
        String symptoms,
        String rootCause,
        String resolutionSteps,
        String workaround,
        String scope,
        PreviewRowStatus status,
        List<RowError> errors) {}
