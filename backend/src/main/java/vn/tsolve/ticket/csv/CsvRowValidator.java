package vn.tsolve.ticket.csv;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.tsolve.ticket.entity.ImportPreviewRow;

/**
 * Checks each CSV row against the import rules (UC-21 step 5, spec STORY-002). Every failed rule of a row is
 * reported, not only the first.
 */
@Component
public class CsvRowValidator {

    static final int SOLUTION_MAX_LENGTH = 10_000;

    private static final Map<String, Integer> MAX_LENGTH = Map.of(
            CsvColumns.TICKET_ID, 50,
            CsvColumns.TITLE, 200,
            CsvColumns.DESCRIPTION, 10_000,
            CsvColumns.AUTHOR, 100);
    private static final List<String> LIMITED =
            List.of(CsvColumns.TICKET_ID, CsvColumns.TITLE, CsvColumns.DESCRIPTION, CsvColumns.AUTHOR);

    /** Its zone decides what "today" is for resolved_date (AC-002.7: Vietnam time). */
    private final Clock clock;

    public CsvRowValidator(Clock clock) {
        this.clock = clock;
    }

    public record Result(ParsedCsv.Row row, List<RowError> errors) {}

    /** The distinct, trimmed, non-blank Ticket IDs of the rows — what to look up as existing tickets. */
    public static Set<String> ticketIdsOf(List<ParsedCsv.Row> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (ParsedCsv.Row row : rows) {
            String id = trim(row.values().ticketId());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public List<Result> validate(List<ParsedCsv.Row> rows, Set<String> existingTicketIds) {
        Map<String, Integer> idCounts = new HashMap<>();
        for (ParsedCsv.Row row : rows) {
            String id = trim(row.values().ticketId());
            if (id != null) {
                idCounts.merge(id, 1, Integer::sum);
            }
        }
        LocalDate today = LocalDate.now(clock);
        List<Result> results = new ArrayList<>(rows.size());
        for (ParsedCsv.Row row : rows) {
            results.add(new Result(row, check(row.values(), existingTicketIds, idCounts, today)));
        }
        return results;
    }

    private static List<RowError> check(
            ImportPreviewRow.Values v, Set<String> existing, Map<String, Integer> idCounts, LocalDate today) {
        Map<String, String> cells = cells(v);
        List<RowError> errors = new ArrayList<>();

        for (String column : CsvColumns.REQUIRED) {
            if (cells.get(column) == null) {
                errors.add(new RowError("MSG01", column, Map.of("field_name", column)));
            }
        }
        for (String column : LIMITED) {
            String value = cells.get(column);
            int max = MAX_LENGTH.get(column);
            if (value != null && length(value) > max) {
                errors.add(new RowError("MSG02", column, Map.of("field_name", column, "max_length", max)));
            }
        }
        int solutionLength = 0;
        for (String section : List.of(
                CsvColumns.SYMPTOMS,
                CsvColumns.ROOT_CAUSE,
                CsvColumns.RESOLUTION_STEPS,
                CsvColumns.WORKAROUND,
                CsvColumns.SCOPE)) {
            String value = cells.get(section);
            solutionLength += value == null ? 0 : length(value);
        }
        if (solutionLength > SOLUTION_MAX_LENGTH) {
            errors.add(
                    new RowError("MSG02", null, Map.of("field_name", "Solution", "max_length", SOLUTION_MAX_LENGTH)));
        }

        String id = cells.get(CsvColumns.TICKET_ID);
        if (id != null && existing.contains(id)) {
            errors.add(new RowError("MSG31", CsvColumns.TICKET_ID, Map.of("ticket_id", id)));
        }
        if (id != null && idCounts.getOrDefault(id, 0) > 1) {
            errors.add(new RowError("MSG78", CsvColumns.TICKET_ID, Map.of("ticket_id", id)));
        }

        String date = cells.get(CsvColumns.RESOLVED_DATE);
        if (date != null && !isPastOrToday(date, today)) {
            errors.add(new RowError("MSG39", CsvColumns.RESOLVED_DATE, Map.of()));
        }
        return errors;
    }

    private static boolean isPastOrToday(String date, LocalDate today) {
        try {
            return !LocalDate.parse(date).isAfter(today);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Trimmed values by column; blank becomes null. */
    private static Map<String, String> cells(ImportPreviewRow.Values v) {
        Map<String, String> cells = new HashMap<>();
        cells.put(CsvColumns.TICKET_ID, trim(v.ticketId()));
        cells.put(CsvColumns.TITLE, trim(v.title()));
        cells.put(CsvColumns.DESCRIPTION, trim(v.description()));
        cells.put(CsvColumns.AUTHOR, trim(v.author()));
        cells.put(CsvColumns.RESOLVED_DATE, trim(v.resolvedDate()));
        cells.put(CsvColumns.SYMPTOMS, trim(v.symptoms()));
        cells.put(CsvColumns.ROOT_CAUSE, trim(v.rootCause()));
        cells.put(CsvColumns.RESOLUTION_STEPS, trim(v.resolutionSteps()));
        cells.put(CsvColumns.WORKAROUND, trim(v.workaround()));
        cells.put(CsvColumns.SCOPE, trim(v.scope()));
        return cells;
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Characters as the user counts them (code points), so emoji and Vietnamese letters count once. */
    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
