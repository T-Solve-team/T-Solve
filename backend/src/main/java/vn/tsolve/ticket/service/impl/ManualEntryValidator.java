package vn.tsolve.ticket.service.impl;

import static vn.tsolve.ticket.csv.CsvRowValidator.trim;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.tsolve.ticket.csv.CsvRowValidator;
import vn.tsolve.ticket.dto.ManualEntryRequest;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.exception.ManualEntryRejectedException;

/**
 * The field checks of a manual entry (UC-22). The first message kind found is the answer, as in CSV import: MSG01,
 * then MSG02, then MSG39; the web app shows every field error from its own checks (AC-006.12).
 */
@Component
public class ManualEntryValidator {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int TICKET_ID_MAX = 50;
    private static final int TITLE_MAX = 200;
    private static final int DESCRIPTION_MAX = 10_000;

    /** A checked entry: every value trimmed, a blank optional value null. */
    public record Entry(
            UUID projectId,
            String ticketId,
            String title,
            String description,
            LocalDate resolvedDate,
            Ticket.Solution solution) {}

    private final Clock clock;

    public ManualEntryValidator(Clock clock) {
        this.clock = clock;
    }

    public Entry validate(ManualEntryRequest request) {
        String ticketId = trim(request.ticketId());
        String title = trim(request.title());
        String description = trim(request.description());
        Ticket.Solution solution = new Ticket.Solution(
                trim(request.symptoms()),
                trim(request.rootCause()),
                trim(request.resolutionSteps()),
                trim(request.workaround()),
                trim(request.scope()));

        List<String> missing = new ArrayList<>();
        require(missing, "projectId", request.projectId());
        require(missing, "ticketId", ticketId);
        require(missing, "title", title);
        require(missing, "description", description);
        require(missing, "symptoms", solution.symptoms());
        require(missing, "rootCause", solution.rootCause());
        require(missing, "resolutionSteps", solution.resolutionSteps());
        require(missing, "scope", solution.scope());
        if (!missing.isEmpty()) {
            throw ManualEntryRejectedException.missing(missing);
        }

        limit("ticketId", ticketId, TICKET_ID_MAX);
        limit("title", title, TITLE_MAX);
        limit("description", description, DESCRIPTION_MAX);
        int solutionLength = length(solution.symptoms())
                + length(solution.rootCause())
                + length(solution.resolutionSteps())
                + length(solution.workaround())
                + length(solution.scope());
        if (solutionLength > CsvRowValidator.SOLUTION_MAX_LENGTH) {
            throw ManualEntryRejectedException.tooLong("Solution", CsvRowValidator.SOLUTION_MAX_LENGTH, null);
        }

        return new Entry(
                request.projectId(), ticketId, title, description, resolvedDate(request.resolvedDate()), solution);
    }

    private static void require(List<String> missing, String field, Object value) {
        if (value == null) {
            missing.add(field);
        }
    }

    private static void limit(String field, String value, int max) {
        if (length(value) > max) {
            throw ManualEntryRejectedException.tooLong(field, max, field);
        }
    }

    /** Characters as the user counts them (code points), so emoji and Vietnamese letters count once. */
    private static int length(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    /** No date is fine; a date must be a calendar day that is not after today in Vietnam (AC-006.5). */
    private LocalDate resolvedDate(String text) {
        String trimmed = trim(text);
        if (trimmed == null) {
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw ManualEntryRejectedException.invalidResolvedDate();
        }
        if (date.isAfter(LocalDate.now(clock.withZone(VIETNAM)))) {
            throw ManualEntryRejectedException.invalidResolvedDate();
        }
        return date;
    }
}
