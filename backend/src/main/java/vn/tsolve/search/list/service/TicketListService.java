package vn.tsolve.search.list.service;

import org.springframework.data.domain.Page;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.list.dto.TicketListQuery;
import vn.tsolve.search.list.dto.TicketSummaryResponse;

public interface TicketListService {
    Page<TicketSummaryResponse> list(TSolveUser user, TicketListQuery query);
}
