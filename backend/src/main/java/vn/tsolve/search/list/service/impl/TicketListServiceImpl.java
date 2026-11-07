package vn.tsolve.search.list.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.list.dto.TicketListQuery;
import vn.tsolve.search.list.dto.TicketSummaryResponse;
import vn.tsolve.search.list.exception.InvalidListQueryException;
import vn.tsolve.search.list.mapper.TicketSummaryMapper;
import vn.tsolve.search.list.repository.TicketListRepository;
import vn.tsolve.search.list.service.TicketListService;
import vn.tsolve.search.scope.service.VisibleScope;

@Service
public class TicketListServiceImpl implements TicketListService {
    private static final Set<String> SOURCES = Set.of("SOLUTION_FORM", "JIRA_IMPORT", "CSV", "MANUAL");
    private static final Set<String> STATUSES =
            Set.of("PENDING_REVIEW", "CHANGES_REQUESTED", "PUBLISHED", "REJECTED", "UNPUBLISHED", "EXPIRED");
    private static final Map<String, String> SORT_KEYS = Map.of(
            "sourceTicketId", "sourceTicketId", "title", "title", "project", "projectName", "updatedAt", "updatedAt");
    private final TicketListRepository repository;
    private final VisibleScope visibleScope;
    private final TicketSummaryMapper mapper;

    public TicketListServiceImpl(
            TicketListRepository repository, VisibleScope visibleScope, TicketSummaryMapper mapper) {
        this.repository = repository;
        this.visibleScope = visibleScope;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketSummaryResponse> list(TSolveUser user, TicketListQuery query) {
        PageRequest paging = paging(query);
        if (query.keyword() != null && query.keyword().length() > 100) {
            throw InvalidListQueryException.keywordTooLong();
        }
        UUID departmentPublicId = departmentId(query.departmentId());
        if (query.source() != null && !SOURCES.contains(query.source())
                || query.status() != null && !STATUSES.contains(query.status())) {
            throw new InvalidListQueryException();
        }
        VisibleScope.Scope scope = visibleScope.of(user);
        if (scope.isEmpty()) return Page.empty(paging);

        Long department = null;
        if (departmentPublicId != null) {
            department = repository.findDepartmentId(departmentPublicId).orElse(null);
            if (department == null || !scope.departmentIds().contains(department)) return Page.empty(paging);
        }
        var specification = TicketListSpecs.visible(scope)
                .and(TicketListSpecs.filters(query.keyword(), department, query.source(), query.status()));
        // JPA takes an int offset; a valid, very distant page still answers empty with the actual total.
        if (paging.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), paging, repository.count(specification));
        }
        return repository.findAll(specification, paging).map(mapper::toResponse);
    }

    private static UUID departmentId(String value) {
        if (value == null) return null;
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new InvalidListQueryException();
            return id;
        } catch (IllegalArgumentException invalid) {
            throw new InvalidListQueryException();
        }
    }

    private static PageRequest paging(TicketListQuery query) {
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? 20 : query.size();
        if (page < 0 || size < 1 || size > 100) throw new InvalidListQueryException();
        // Spring joins repeated sort parameters with commas.
        String[] sort = (query.sort() == null ? "updatedAt,desc" : query.sort()).split(",", -1);
        if (sort.length % 2 != 0) throw new InvalidListQueryException();
        Sort order = Sort.unsorted();
        for (int i = 0; i < sort.length; i += 2) {
            if (!("asc".equals(sort[i + 1]) || "desc".equals(sort[i + 1]))) {
                throw new InvalidListQueryException();
            }
            if ("reviewDueDate".equals(sort[i])) continue;
            String property = SORT_KEYS.get(sort[i]);
            if (property == null) throw new InvalidListQueryException();
            order = order.and(Sort.by(new Sort.Order(Sort.Direction.fromString(sort[i + 1]), property)));
        }
        return PageRequest.of(page, size, order.and(Sort.by(Sort.Order.desc("id"))));
    }
}
