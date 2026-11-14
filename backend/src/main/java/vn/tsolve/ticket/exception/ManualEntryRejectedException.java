package vn.tsolve.ticket.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** A manual entry that fails a check of UC-22 before anything is saved (AC-006.3–AC-006.5, AC-006.8). */
public class ManualEntryRejectedException extends MessageCodeException {

    private ManualEntryRejectedException(HttpStatus status, String code, Map<String, Object> params, String field) {
        super(status, code, params, field);
    }

    /** MSG01 naming every missing field; the message sits below the first one. */
    public static ManualEntryRejectedException missing(List<String> fields) {
        return new ManualEntryRejectedException(
                HttpStatus.BAD_REQUEST, "MSG01", Map.of("field_name", String.join(", ", fields)), fields.get(0));
    }

    public static ManualEntryRejectedException tooLong(String fieldName, int maxLength, String field) {
        return new ManualEntryRejectedException(
                HttpStatus.BAD_REQUEST, "MSG02", Map.of("field_name", fieldName, "max_length", maxLength), field);
    }

    /** The resolved date is not a calendar date or is after today in Vietnam (MSG39). */
    public static ManualEntryRejectedException invalidResolvedDate() {
        return new ManualEntryRejectedException(HttpStatus.BAD_REQUEST, "MSG39", Map.of(), "resolvedDate");
    }

    /** The entry part is not readable JSON. */
    public static ManualEntryRejectedException unreadable() {
        return new ManualEntryRejectedException(HttpStatus.BAD_REQUEST, "MSG06", Map.of(), null);
    }

    public static ManualEntryRejectedException ticketIdTaken(String ticketId) {
        return new ManualEntryRejectedException(
                HttpStatus.CONFLICT, "MSG31", Map.of("ticket_id", ticketId), "ticketId");
    }
}
