package vn.tsolve.identity.dto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import vn.tsolve.common.exception.InvalidFieldsException;
import vn.tsolve.common.exception.ProblemDetails;
import vn.tsolve.common.session.dto.TicketStatus;

/** The query of {@code GET /api/v1/me/solutions}; a missing page is 0 and a missing size 20. Dates are UTC+7 days. */
public record MySolutionsQuery(
        Integer page, Integer size, TicketStatus status, UUID projectId, LocalDate from, LocalDate to) {

    public MySolutionsQuery {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    /**
     * Parses the raw query values and reports every wrong one at once: one MSG06 entry per field, sorted by field
     * name; {@code to} before {@code from} is an error on {@code to} (AC-003.9). An empty value counts as missing.
     */
    public static MySolutionsQuery parse(
            String page, String size, String status, String projectId, String from, String to) {
        List<ProblemDetails.FieldError> errors = new ArrayList<>();
        Integer pageNumber = read("page", page, Integer::valueOf, errors);
        Integer pageSize = read("size", size, Integer::valueOf, errors);
        TicketStatus ticketStatus = read("status", status, TicketStatus::valueOf, errors);
        UUID project = read("projectId", projectId, MySolutionsQuery::uuid, errors);
        LocalDate fromDay = read("from", from, LocalDate::parse, errors);
        LocalDate toDay = read("to", to, LocalDate::parse, errors);
        if (pageNumber != null && pageNumber < 0) {
            errors.add(wrong("page"));
        }
        if (pageSize != null && (pageSize < 1 || pageSize > 100)) {
            errors.add(wrong("size"));
        }
        if (fromDay != null && toDay != null && toDay.isBefore(fromDay)) {
            errors.add(wrong("to"));
        }
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparing(ProblemDetails.FieldError::field));
            throw new InvalidFieldsException(errors);
        }
        return new MySolutionsQuery(pageNumber, pageSize, ticketStatus, project, fromDay, toDay);
    }

    private static <T> T read(
            String field, String value, Function<String, T> parser, List<ProblemDetails.FieldError> errors) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException | DateTimeParseException wrongValue) {
            errors.add(wrong(field));
            return null;
        }
    }

    // UUID.fromString also takes short forms such as "1-1-1-1-1"; only the canonical 36-character form is a valid id.
    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("not a canonical UUID");
        }
        return id;
    }

    private static ProblemDetails.FieldError wrong(String field) {
        return new ProblemDetails.FieldError(field, ProblemDetails.UNEXPECTED_ERROR, Map.of());
    }
}
