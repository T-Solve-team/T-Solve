package vn.tsolve.search.suggestion.service;

import java.util.List;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;
import vn.tsolve.search.suggestion.dto.TicketSuggestionRequest;

public interface TicketSuggestionService {
    List<TicketSearchResultResponse> suggest(TSolveUser user, TicketSuggestionRequest request);
}
