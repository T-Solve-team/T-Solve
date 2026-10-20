package vn.tsolve.ticket.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.tsolve.ticket.dto.ReviewAction;
import vn.tsolve.ticket.dto.ReviewDecisionResult;
import vn.tsolve.ticket.dto.ReviewQueueItem;
import vn.tsolve.ticket.dto.ReviewRegistration;
import vn.tsolve.ticket.dto.ReviewTicketResponse;

public interface ReviewService {
    Page<ReviewQueueItem> queue(Long projectId, Pageable pageable);

    List<Long> queueTicketIds(Long projectId);

    ReviewTicketResponse get(long ticketId);

    ReviewDecisionResult decide(List<Long> ticketIds, ReviewAction action, String comment);

    ReviewTicketResponse registerSubmission(ReviewRegistration submission);

    ReviewTicketResponse getPublished(long ticketId);
}
