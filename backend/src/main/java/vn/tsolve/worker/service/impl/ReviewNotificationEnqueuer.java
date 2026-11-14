package vn.tsolve.worker.service.impl;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.tsolve.identity.service.WorkspaceSiteService;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.worker.client.ReviewEmail;
import vn.tsolve.worker.client.ReviewEmail.Decision;
import vn.tsolve.worker.client.ReviewEmailRenderer;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.WorkerTenantService;

/** Queues the writer email of committed Reject and Request Changes decisions (ticket-review STORY-010). */
@Component
public class ReviewNotificationEnqueuer {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewNotificationEnqueuer.class);

    private final ObjectProvider<EmailOutboxService> outboxes;
    private final ReviewNotificationService notifications;
    private final WorkspaceSiteService sites;
    private final ReviewEmailRenderer renderer = new ReviewEmailRenderer();
    private final ObjectProvider<WorkerTenantService> tenants;

    public ReviewNotificationEnqueuer(
            ObjectProvider<EmailOutboxService> outboxes,
            ObjectProvider<WorkerTenantService> tenants,
            ReviewNotificationService notifications,
            WorkspaceSiteService sites) {
        this.outboxes = outboxes;
        this.tenants = tenants;
        this.notifications = notifications;
        this.sites = sites;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(ReviewFeedbackDecidedEvent event) {
        AtomicInteger processed = new AtomicInteger();
        try {
            EmailOutboxService queue = outboxes.getIfAvailable();
            if (queue == null) {
                return;
            }
            WorkerTenantService context = tenants.getIfAvailable();
            Runnable enqueue = () -> {
                for (UUID decisionId : event.decisionIds()) {
                    try {
                        notifications
                                .findNotification(decisionId)
                                .ifPresent(notification -> queue.queue(render(notification, context)));
                    } catch (RuntimeException exception) {
                        // Exception messages and causes may contain private mail or database values.
                        LOG.warn(
                                "Could not queue {} of {} review emails ({})",
                                1,
                                event.decisionIds().size(),
                                exception.getClass().getSimpleName());
                    }
                    processed.incrementAndGet();
                }
            };
            if (context == null || context.currentWorkspaceId().isPresent()) {
                enqueue.run();
            } else {
                var workspaceId = context.enqueueWorkspaceId();
                if (workspaceId.isEmpty()) {
                    int total = event.decisionIds().size();
                    LOG.warn("Could not queue {} of {} review emails (no Workspace to bind)", total, total);
                    return;
                }
                context.runIn(workspaceId.get(), enqueue);
            }
        } catch (RuntimeException exception) {
            // The exception text may carry mail data; the decision is already committed.
            LOG.warn(
                    "Could not queue {} of {} review emails ({})",
                    event.decisionIds().size() - processed.get(),
                    event.decisionIds().size(),
                    exception.getClass().getSimpleName());
        }
    }

    private OutboundEmail render(ReviewNotification notification, WorkerTenantService context) {
        Decision decision =
                switch (notification.kind()) {
                    case REJECTED -> Decision.REJECT;
                    case CHANGES_REQUESTED -> Decision.REQUEST_CHANGES;
                };
        String host = (context == null ? Optional.<UUID>empty() : context.currentWorkspaceId())
                .flatMap(sites::siteHost)
                .filter(value -> !value.isBlank())
                .orElse(null);
        String link = host == null ? null : "https://" + host + "/tickets/" + notification.ticketId();
        var email = new ReviewEmail(
                decision,
                notification.ticketKey(),
                notification.ticketTitle(),
                notification.projectName(),
                notification.reviewerName(),
                notification.writerName(),
                notification.decidedAt(),
                notification.feedback(),
                link,
                host);
        return new OutboundEmail(
                notification.recipient(), renderer.subject(email), renderer.plain(email), renderer.html(email));
    }
}
