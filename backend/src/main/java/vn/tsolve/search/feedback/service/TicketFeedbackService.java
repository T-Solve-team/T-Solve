package vn.tsolve.search.feedback.service;

import java.util.UUID;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.feedback.dto.TicketFeedbackRequest;

public interface TicketFeedbackService {
    void give(TSolveUser user, UUID ticketId, TicketFeedbackRequest request);
}
