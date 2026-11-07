package vn.tsolve.ticket.entity;

import java.util.List;
import vn.tsolve.ticket.csv.CsvColumns;

/** The kind of uploaded file: its rows carry a Solution, or the Project Manager writes it on the preview (D15). */
public enum ImportType {
    WITH_SOLUTION(CsvColumns.REQUIRED, CsvColumns.TEMPLATE),
    WITHOUT_SOLUTION(
            List.of(CsvColumns.TICKET_ID, CsvColumns.TITLE),
            List.of(
                    CsvColumns.TICKET_ID,
                    CsvColumns.TITLE,
                    CsvColumns.DESCRIPTION,
                    CsvColumns.AUTHOR,
                    CsvColumns.RESOLVED_DATE));

    private final List<String> requiredColumns;
    private final List<String> templateColumns;

    ImportType(List<String> requiredColumns, List<String> templateColumns) {
        this.requiredColumns = requiredColumns;
        this.templateColumns = templateColumns;
    }

    public List<String> requiredColumns() {
        return requiredColumns;
    }

    public List<String> templateColumns() {
        return templateColumns;
    }
}
