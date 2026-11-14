package vn.tsolve.ticket.service;

import org.springframework.data.domain.Page;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ImportHistoryItem;
import vn.tsolve.ticket.dto.ImportHistoryQuery;

public interface ImportHistoryService {
    Page<ImportHistoryItem> list(TSolveUser user, ImportHistoryQuery query);
}
