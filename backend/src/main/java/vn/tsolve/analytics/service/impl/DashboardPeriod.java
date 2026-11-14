package vn.tsolve.analytics.service.impl;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import vn.tsolve.analytics.exception.InvalidDashboardFilterException;

public record DashboardPeriod(LocalDate from, LocalDate to) {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    public static DashboardPeriod parse(String from, String to, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        if (from == null && to == null) {
            return new DashboardPeriod(today.minusDays(29), today);
        }
        if (from == null
                || to == null
                || !from.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || !to.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new InvalidDashboardFilterException("MSG39");
        }
        LocalDate first;
        LocalDate last;
        try {
            first = LocalDate.parse(from, DATE);
            last = LocalDate.parse(to, DATE);
        } catch (DateTimeException invalid) {
            throw new InvalidDashboardFilterException("MSG39");
        }
        if (first.isAfter(last) || last.isAfter(today) || ChronoUnit.DAYS.between(first, last) + 1 > 366) {
            throw new InvalidDashboardFilterException("MSG39");
        }
        return new DashboardPeriod(first, last);
    }

    public Instant start() {
        return from.atStartOfDay(ZONE).toInstant();
    }

    public Instant end() {
        return to.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    public List<LocalDate> days() {
        return from.datesUntil(to.plusDays(1)).toList();
    }
}
