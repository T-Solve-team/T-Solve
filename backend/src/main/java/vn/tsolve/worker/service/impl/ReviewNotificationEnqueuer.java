package vn.tsolve.worker.service.impl;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.service.WorkspaceSiteService;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.worker.client.ReviewEmail;
import vn.tsolve.worker.client.ReviewEmail.Decision;
import vn.tsolve.worker.client.ReviewEmailRenderer;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.EmailOutboxUnavailableException;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;
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
    private final ObjectProvider<TenantContext> requestTenants;
    private final String siteOriginTemplate;

    public ReviewNotificationEnqueuer(
            ObjectProvider<EmailOutboxService> outboxes,
            ObjectProvider<WorkerTenantService> tenants,
            ReviewNotificationService notifications,
            WorkspaceSiteService sites) {
        this(outboxes, tenants, notifications, sites, "");
    }

    public ReviewNotificationEnqueuer(
            ObjectProvider<EmailOutboxService> outboxes,
            ObjectProvider<WorkerTenantService> tenants,
            ReviewNotificationService notifications,
            WorkspaceSiteService sites,
            String siteOriginTemplate) {
        this(outboxes, tenants, notifications, sites, siteOriginTemplate, null);
    }

    /** {@code requestTenants} is the holder that names the Workspace in the web process, where no WorkerTenantService exists. */
    @Autowired
    public ReviewNotificationEnqueuer(
            ObjectProvider<EmailOutboxService> outboxes,
            ObjectProvider<WorkerTenantService> tenants,
            ReviewNotificationService notifications,
            WorkspaceSiteService sites,
            @Value("${TSOLVE_SITE_ORIGIN_TEMPLATE:}") String siteOriginTemplate,
            ObjectProvider<TenantContext> requestTenants) {
        this.requestTenants = requestTenants;
        this.outboxes = outboxes;
        this.tenants = tenants;
        this.notifications = notifications;
        this.sites = sites;
        this.siteOriginTemplate = siteOriginTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(ReviewFeedbackDecidedEvent event) {
        AtomicInteger processed = new AtomicInteger();
        try {
            EmailOutboxService queue = outboxes.getIfAvailable();
            if (queue == null) {
                // Silent before BUG-339: the web process had no outbox and nobody could tell why no mail went out.
                int total = event.decisionIds().size();
                LOG.warn("Could not queue {} of {} review emails (the email outbox is not available)", total, total);
                return;
            }
            WorkerTenantService context = tenants.getIfAvailable();
            Runnable enqueue = () -> {
                String host = siteHost(context);
                for (UUID decisionId : event.decisionIds()) {
                    try {
                        notifications
                                .findNotification(decisionId)
                                .ifPresent(notification -> queue.queue(render(notification, host)));
                    } catch (EmailOutboxUnavailableException outage) {
                        // The Jobs database is down: every further queue call would wait for a connection on the
                        // request thread (BUG-164), so the outer handler logs the remainder once.
                        throw outage;
                    } catch (InvalidOutboundEmailException invalid) {
                        // Only reached with a Workspace bound (checked below), so it is the email itself (BUG-341).
                        LOG.warn(
                                "Could not queue {} of {} review emails (the email was rejected: recipient, subject"
                                        + " or body)",
                                1,
                                event.decisionIds().size());
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
            TenantContext web = requestTenants == null ? null : requestTenants.getIfAvailable();
            if (context == null && web != null && web.currentWorkspaceId().isEmpty()) {
                // The web outbox would refuse every email for the same reason; say it once (BUG-341).
                int total = event.decisionIds().size();
                LOG.warn("Could not queue {} of {} review emails (no Workspace to bind)", total, total);
                return;
            }
            if (context == null) {
                enqueue.run();
                return;
            }
            var workspaceId = context.enqueueWorkspaceId();
            if (workspaceId.isEmpty()) {
                int total = event.decisionIds().size();
                LOG.warn("Could not queue {} of {} review emails (no Workspace to bind)", total, total);
                return;
            }
            if (context.currentWorkspaceId().isPresent()) {
                enqueue.run();
            } else {
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

    private String siteHost(WorkerTenantService context) {
        try {
            return currentWorkspace(context)
                    .flatMap(sites::siteHost)
                    .filter(value -> !value.isBlank())
                    .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Optional<UUID> currentWorkspace(WorkerTenantService context) {
        if (context != null) {
            return context.currentWorkspaceId();
        }
        TenantContext request = requestTenants == null ? null : requestTenants.getIfAvailable();
        return request == null ? Optional.empty() : request.currentWorkspaceId();
    }

    private OutboundEmail render(ReviewNotification notification, String host) {
        Decision decision =
                switch (notification.kind()) {
                    case REJECTED -> Decision.REJECT;
                    case CHANGES_REQUESTED -> Decision.REQUEST_CHANGES;
                };
        // The site service exposes the host only; tenant site hosts start with their Workspace slug.
        String link = null;
        if (host != null) {
            String origin = siteOriginTemplate == null || siteOriginTemplate.isBlank()
                    ? "https://" + host
                    : siteOriginTemplate.replace("{slug}", host.split("[.:]", 2)[0]);
            link = origin + "/tickets/" + notification.ticketId();
        }
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
