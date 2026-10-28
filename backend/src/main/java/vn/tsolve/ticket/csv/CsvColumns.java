package vn.tsolve.ticket.csv;

import java.util.List;

/** The CSV template columns (spec D4), in template order. */
public final class CsvColumns {

    public static final String TICKET_ID = "ticket_id";
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String AUTHOR = "author";
    public static final String RESOLVED_DATE = "resolved_date";
    public static final String SYMPTOMS = "symptoms";
    public static final String ROOT_CAUSE = "root_cause";
    public static final String RESOLUTION_STEPS = "resolution_steps";
    public static final String WORKAROUND = "workaround";
    public static final String SCOPE = "scope";

    public static final List<String> TEMPLATE = List.of(
            TICKET_ID,
            TITLE,
            DESCRIPTION,
            AUTHOR,
            RESOLVED_DATE,
            SYMPTOMS,
            ROOT_CAUSE,
            RESOLUTION_STEPS,
            WORKAROUND,
            SCOPE);

    public static final List<String> REQUIRED =
            List.of(TICKET_ID, TITLE, SYMPTOMS, ROOT_CAUSE, RESOLUTION_STEPS, SCOPE);

    private CsvColumns() {}
}
