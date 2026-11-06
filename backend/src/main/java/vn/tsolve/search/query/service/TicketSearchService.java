package vn.tsolve.search.query.service;

import org.springframework.data.domain.Page;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.query.dto.TicketSearchQuery;
import vn.tsolve.search.query.dto.TicketSearchResultResponse;

public interface TicketSearchService {
    Page<TicketSearchResultResponse> search(TSolveUser user, TicketSearchQuery query);
}
