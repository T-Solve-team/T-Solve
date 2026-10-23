package vn.tsolve.review.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.tsolve.review.dto.ReviewAction;
import vn.tsolve.review.dto.ReviewDecisionResult;
import vn.tsolve.review.dto.ReviewQueueItem;
import vn.tsolve.review.dto.ReviewRegistration;
import vn.tsolve.review.dto.ReviewTicketResponse;

public interface ReviewService {
    Page<ReviewQueueItem> queue(Long projectId, Pageable pageable);

    List<Long> queueTicketIds(Long projectId);

    ReviewTicketResponse get(long ticketId);

    ReviewDecisionResult decide(List<Long> ticketIds, ReviewAction action, String comment);

    ReviewTicketResponse registerSubmission(ReviewRegistration submission);

    ReviewTicketResponse getPublished(long ticketId);
}
