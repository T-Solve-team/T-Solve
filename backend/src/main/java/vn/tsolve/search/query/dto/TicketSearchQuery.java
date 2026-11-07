package vn.tsolve.search.query.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.tsolve.search.query.exception.InvalidSearchQueryException;

public record TicketSearchQuery(
        String q, UUID departmentId, String source, LocalDate resolvedFrom, LocalDate resolvedTo, Pageable page) {
    private static final Set<String> SOURCES = Set.of("SOLUTION_FORM", "JIRA_IMPORT", "CSV", "MANUAL");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    public static TicketSearchQuery parse(
            String q,
            String departmentId,
            String source,
            String resolvedFrom,
            String resolvedTo,
            String page,
            String size) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) throw InvalidSearchQueryException.requiredQuery();
        if (query.codePointCount(0, query.length()) > 200) throw InvalidSearchQueryException.queryTooLong();
        UUID department = departmentId(departmentId);
        if (source != null && !SOURCES.contains(source)) throw new InvalidSearchQueryException();
        LocalDate from = date(resolvedFrom);
        LocalDate to = date(resolvedTo);
        if (from != null && to != null && from.isAfter(to)) throw InvalidSearchQueryException.reversedDates();
        int number = integer(page, 0);
        int count = integer(size, 20);
        if (number < 0 || count < 1 || count > 100) throw new InvalidSearchQueryException();
        long offset = (long) number * count;
        if (offset > Integer.MAX_VALUE) throw new InvalidSearchQueryException();
        return new TicketSearchQuery(query, department, source, from, to, PageRequest.of(number, count));
    }

    public RankFilter filter() {
        return new RankFilter(departmentId, source, resolvedFrom, resolvedTo);
    }

    private static UUID departmentId(String value) {
        if (value == null) return null;
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new InvalidSearchQueryException();
            return id;
        } catch (IllegalArgumentException invalid) {
            throw new InvalidSearchQueryException();
        }
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new InvalidSearchQueryException();
        try {
            LocalDate date = LocalDate.parse(value, DATE);
            if (date.getYear() < 1) throw new InvalidSearchQueryException();
            return date;
        } catch (DateTimeParseException invalid) {
            throw new InvalidSearchQueryException();
        }
    }

    private static int integer(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidSearchQueryException();
        }
    }
}
