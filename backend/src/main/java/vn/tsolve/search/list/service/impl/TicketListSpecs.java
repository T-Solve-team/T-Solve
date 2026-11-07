package vn.tsolve.search.list.service.impl;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import vn.tsolve.search.scope.entity.SearchTicket;
import vn.tsolve.search.scope.service.VisibleScope.Scope;

final class TicketListSpecs {
    private TicketListSpecs() {}

    static Specification<SearchTicket> visible(Scope scope) {
        return (ticket, query, builder) -> {
            List<Predicate> visible = new ArrayList<>();
            if (!scope.departmentIds().isEmpty()) {
                visible.add(builder.and(
                        builder.equal(ticket.get("status"), "PUBLISHED"),
                        ticket.get("departmentId").in(scope.departmentIds())));
            }
            List<Predicate> related = new ArrayList<>();
            if (!scope.managedProjectIds().isEmpty()) {
                related.add(ticket.<UUID>get("projectId").in(scope.managedProjectIds()));
            }
            scope.writer().ifPresent(writer -> related.add(builder.equal(ticket.get("writer"), writer)));
            if (!related.isEmpty()) {
                visible.add(builder.and(
                        builder.notEqual(ticket.get("status"), "PUBLISHED"),
                        builder.or(related.toArray(Predicate[]::new))));
            }
            return builder.and(
                    builder.equal(ticket.get("departmentStatus"), "ACTIVE"),
                    builder.or(visible.toArray(Predicate[]::new)));
        };
    }

    static Specification<SearchTicket> filters(String keyword, Long department, String source, String status) {
        return (ticket, query, builder) -> {
            List<Predicate> filters = new ArrayList<>();
            if (department != null) filters.add(builder.equal(ticket.get("departmentId"), department));
            if (source != null) filters.add(builder.equal(ticket.get("source"), source));
            if (status != null) filters.add(builder.equal(ticket.get("status"), status));
            if (keyword != null && !keyword.isBlank()) {
                String escaped = keyword.strip()
                        .toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                List<Predicate> matches = new ArrayList<>();
                for (String field : List.of(
                        "sourceTicketId", "title", "symptoms", "rootCause", "resolutionSteps", "workaround", "scope")) {
                    matches.add(builder.like(builder.lower(ticket.get(field)), "%" + escaped + "%", '\\'));
                }
                filters.add(builder.or(matches.toArray(Predicate[]::new)));
            }
            return builder.and(filters.toArray(Predicate[]::new));
        };
    }
}
