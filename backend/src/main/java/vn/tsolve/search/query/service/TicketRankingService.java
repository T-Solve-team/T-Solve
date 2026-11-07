package vn.tsolve.search.query.service;

import org.springframework.data.domain.Pageable;
import vn.tsolve.search.query.dto.RankFilter;
import vn.tsolve.search.query.dto.RankedPage;
import vn.tsolve.search.scope.service.VisibleScope.Scope;

public interface TicketRankingService {
    RankedPage rank(String text, String keywordText, RankFilter filter, Scope scope, Pageable page);
}
