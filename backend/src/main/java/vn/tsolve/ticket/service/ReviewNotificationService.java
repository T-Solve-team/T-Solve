package vn.tsolve.ticket.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.ticket.dto.ReviewNotification;

public interface ReviewNotificationService {

    /**
     * The writer email for a stored decision, in the bound Workspace. Empty when the decision is gone, is an Approve,
     * or its ticket has no writer account.
     */
    Optional<ReviewNotification> findNotification(UUID decisionId);
}
