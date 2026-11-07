package vn.tsolve.worker.service.impl;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.worker.client.ReviewNotificationSender;
import vn.tsolve.worker.client.ReviewNotificationSender.Decision;
import vn.tsolve.worker.service.TenantJobHandler;

/**
 * Sends one writer email per review decision id. Registered wherever jobs are enabled, because the API process
 * checks the handler type when it enqueues; only the worker profile has the SMTP sender.
 */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class ReviewNotificationJobHandler implements TenantJobHandler {

    public static final String TYPE = "review-notification";

    private final ReviewNotificationService notifications;
    private final ObjectProvider<ReviewNotificationSender> senders;

    public ReviewNotificationJobHandler(
            ReviewNotificationService notifications, ObjectProvider<ReviewNotificationSender> senders) {
        this.notifications = notifications;
        this.senders = senders;
    }

    @Override
    public String handlerType() {
        return TYPE;
    }

    @Override
    public void execute(UUID decisionId) {
        notifications.findNotification(decisionId).ifPresent(this::send);
    }

    private void send(ReviewNotification notification) {
        ReviewNotificationSender sender = senders.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("Review email sender is not configured in this process");
        }
        Decision decision =
                switch (notification.kind()) {
                    case REJECTED -> Decision.REJECT;
                    case CHANGES_REQUESTED -> Decision.REQUEST_CHANGES;
                };
        // A DeliveryException fails the job, so JobRunr retries it; the decision is already committed.
        sender.send(decision, notification.recipient(), notification.ticketKey(), notification.feedback());
    }
}
