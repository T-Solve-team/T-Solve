package vn.tsolve.review.service;

import java.util.Set;
import java.util.UUID;
import vn.tsolve.review.dto.AiReviewResult;

public interface AiReviewService {

    /**
     * Advisory findings on a Pending review Solution Form ticket of a managed Project (STORY-015). Refuses with the
     * decisions' 403/404/409 before any AI call; 503 MSG_NEW_AI_REVIEW_1 when the AI gives no usable answer.
     */
    AiReviewResult review(Set<UUID> managedProjectIds, UUID ticketId);
}
