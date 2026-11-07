package vn.tsolve.analytics.service.impl;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.analytics.dto.AuditEntryResponse;
import vn.tsolve.analytics.exception.InvalidAuditFilterException;
import vn.tsolve.analytics.mapper.AuditEntryMapper;
import vn.tsolve.analytics.repository.AuditLogEntryRepository;
import vn.tsolve.analytics.service.AuditLogService;
import vn.tsolve.common.audit.AuditAction;

@Service
@Transactional(readOnly = true)
public class AuditLogServiceImpl implements AuditLogService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private final AuditLogEntryRepository repository;
    private final AuditEntryMapper mapper;
    private final Clock clock;

    public AuditLogServiceImpl(
            AuditLogEntryRepository repository, AuditEntryMapper mapper, ObjectProvider<Clock> clocks) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clocks.getIfAvailable(Clock::systemUTC);
    }

    @Override
    public Page<AuditEntryResponse> findEntries(
            String actor, String action, String from, String to, int page, int size, List<String> sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidAuditFilterException("MSG06");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isEmpty()) {
            if (sort.size() != 1) {
                throw new InvalidAuditFilterException("MSG06");
            }
            direction = switch (sort.getFirst()) {
                case "createdAt", "createdAt,asc" -> Sort.Direction.ASC;
                case "createdAt,desc" -> Sort.Direction.DESC;
                default -> throw new InvalidAuditFilterException("MSG06");};
        }
        AuditAction selectedAction;
        try {
            selectedAction = action == null ? null : AuditAction.valueOf(action);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidAuditFilterException("MSG06");
        }
        LocalDate first = null;
        LocalDate last = null;
        if (from != null || to != null) {
            if (from == null
                    || to == null
                    || !from.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                    || !to.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new InvalidAuditFilterException("MSG39");
            }
            try {
                first = LocalDate.parse(from, DATE);
                last = LocalDate.parse(to, DATE);
            } catch (DateTimeException invalid) {
                throw new InvalidAuditFilterException("MSG39");
            }
            if (first.isAfter(last) || last.isAfter(LocalDate.now(clock.withZone(ZONE)))) {
                throw new InvalidAuditFilterException("MSG39");
            }
        }
        var lower = first == null ? null : first.atStartOfDay(ZONE).toInstant();
        var upper = last == null ? null : last.plusDays(1).atStartOfDay(ZONE).toInstant();
        String text = actor == null ? "" : actor.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        var pageable =
                PageRequest.of(page, size, Sort.by(direction, "createdAt").and(Sort.by("id")));
        return repository
                .findAll(
                        (root, query, cb) -> {
                            List<Predicate> predicates = new ArrayList<>();
                            if (!text.isEmpty()) {
                                predicates.add(cb.like(cb.lower(root.get("actorEmail")), pattern, '\\'));
                            }
                            if (selectedAction != null) {
                                predicates.add(cb.equal(root.get("action"), selectedAction));
                            }
                            if (lower != null) {
                                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), lower));
                                predicates.add(cb.lessThan(root.get("createdAt"), upper));
                            }
                            return cb.and(predicates.toArray(Predicate[]::new));
                        },
                        pageable)
                .map(mapper::toResponse);
    }
}
