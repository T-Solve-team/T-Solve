package vn.tsolve.worker.service.impl;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.worker.client.SmtpEmailSender;
import vn.tsolve.worker.dto.OutboundEmail;
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
    private final ObjectProvider<SmtpEmailSender> senders;

    public ReviewNotificationJobHandler(
            ReviewNotificationService notifications, ObjectProvider<SmtpEmailSender> senders) {
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
        SmtpEmailSender sender = senders.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("Review email sender is not configured in this process");
        }
        boolean rejected = notification.kind() == ReviewNotification.Kind.REJECTED;
        sender.send(new OutboundEmail(
                notification.recipient(),
                rejected ? "Ticket rejected" : "Changes requested for your ticket",
                "Ticket %s\n\n%s\n%s"
                        .formatted(
                                notification.ticketKey(),
                                rejected ? "Rejection reason" : "Review comment",
                                notification.feedback()),
                null));
    }
}
