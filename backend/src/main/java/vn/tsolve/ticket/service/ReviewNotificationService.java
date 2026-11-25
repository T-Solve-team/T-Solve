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

    /**
     * The https site of the bound Workspace's Jira service account, without trailing slashes, for the issue link of
     * the email (AC-010.8). Empty when there is no usable service account or its site is not https.
     */
    Optional<String> jiraSiteUrl();
}
