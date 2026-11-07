package vn.tsolve.search.query.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.query.dto.RankedPage;
import vn.tsolve.search.query.dto.TicketSearchQuery;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.query.repository.SearchLogRepository;
import vn.tsolve.search.query.service.TicketRankingService;
import vn.tsolve.search.query.service.TicketSearchService;
import vn.tsolve.search.scope.service.VisibleScope;

@Service
public class TicketSearchServiceImpl implements TicketSearchService {
    private static final Logger log = LoggerFactory.getLogger(TicketSearchServiceImpl.class);

    private final VisibleScope visibleScope;
    private final TicketRankingService ranking;
    private final SearchLogRepository logs;

    public TicketSearchServiceImpl(VisibleScope visibleScope, TicketRankingService ranking, SearchLogRepository logs) {
        this.visibleScope = visibleScope;
        this.ranking = ranking;
        this.logs = logs;
    }

    @Override
    public Page<TicketSearchResultResponse> search(TSolveUser user, TicketSearchQuery query) {
        RankedPage result = ranking.rank(query.q(), query.q(), query.filter(), visibleScope.of(user), query.page());
        if (query.page().getPageNumber() == 0) {
            try {
                logs.log(
                        user.id(),
                        query.departmentId() == null
                                ? null
                                : query.departmentId().toString(),
                        query.q(),
                        "WEB",
                        Math.toIntExact(result.page().getTotalElements()),
                        result.isFallback());
            } catch (RuntimeException failure) {
                log.warn("Search log write failed: {}", failure.getClass().getSimpleName());
            }
        }
        return result.page();
    }
}
