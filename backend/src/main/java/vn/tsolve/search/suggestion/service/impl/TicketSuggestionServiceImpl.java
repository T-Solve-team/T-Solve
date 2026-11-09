package vn.tsolve.search.suggestion.service.impl;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.query.dto.RankFilter;
import vn.tsolve.search.query.dto.RankedPage;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.query.repository.SearchLogRepository;
import vn.tsolve.search.query.service.TicketRankingService;
import vn.tsolve.search.scope.service.VisibleScope;
import vn.tsolve.search.suggestion.dto.TicketSuggestionRequest;
import vn.tsolve.search.suggestion.service.TicketSuggestionService;

@Service
public class TicketSuggestionServiceImpl implements TicketSuggestionService {
    private static final Logger log = LoggerFactory.getLogger(TicketSuggestionServiceImpl.class);

    private final VisibleScope visibleScope;
    private final TicketRankingService ranking;
    private final SearchLogRepository logs;

    public TicketSuggestionServiceImpl(
            VisibleScope visibleScope, TicketRankingService ranking, SearchLogRepository logs) {
        this.visibleScope = visibleScope;
        this.ranking = ranking;
        this.logs = logs;
    }

    @Override
    public List<TicketSearchResultResponse> suggest(TSolveUser user, TicketSuggestionRequest request) {
        String summary = request.summary().trim();
        String description =
                request.description() == null ? "" : request.description().trim();
        String text = description.isBlank() ? summary : summary + "\n\n" + description;
        RankedPage result = ranking.rank(
                text, summary, new RankFilter(null, null, null, null), visibleScope.of(user), PageRequest.of(0, 5));
        List<TicketSearchResultResponse> items = result.page().getContent();
        try {
            logs.log(user.id(), null, summary, "ISSUE_PANEL", items.size(), result.isFallback());
        } catch (RuntimeException failure) {
            log.warn("Search log write failed: {}", failure.getClass().getSimpleName());
        }
        return items;
    }
}
