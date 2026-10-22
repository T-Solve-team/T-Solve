package vn.tsolve.ticket.csv;

import java.util.List;
import vn.tsolve.ticket.entity.ImportPreviewRow;

/** The data rows of a ticket CSV whose header passed the file-level checks. */
public record ParsedCsv(List<Row> rows) {

    /** rowNo = CSV record number of the data row, from 1; a quoted multi-line value counts as one row. */
    public record Row(int rowNo, ImportPreviewRow.Values values) {}
}
