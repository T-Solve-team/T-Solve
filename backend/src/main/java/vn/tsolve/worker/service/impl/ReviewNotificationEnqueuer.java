package vn.tsolve.worker.service.impl;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

/** Queues the writer email of committed Reject and Request Changes decisions (ticket-review STORY-010). */
@Component
public class ReviewNotificationEnqueuer {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewNotificationEnqueuer.class);

    private final ObjectProvider<TenantJobService> jobs;
    private final ObjectProvider<WorkerTenantService> tenants;

    public ReviewNotificationEnqueuer(
            ObjectProvider<TenantJobService> jobs, ObjectProvider<WorkerTenantService> tenants) {
        this.jobs = jobs;
        this.tenants = tenants;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(ReviewFeedbackDecidedEvent event) {
        TenantJobService queue = jobs.getIfAvailable();
        if (queue == null) {
            // tsolve.worker.enabled is off in this process: the decision stands and no email is sent.
            return;
        }
        AtomicInteger queued = new AtomicInteger();
        Runnable enqueue = () -> {
            for (UUID decisionId : event.decisionIds()) {
                queue.enqueue(ReviewNotificationJobHandler.TYPE, decisionId);
                queued.incrementAndGet();
            }
        };
        try {
            WorkerTenantService context = tenants.getIfAvailable();
            if (context == null || context.currentWorkspaceId().isPresent()) {
                enqueue.run();
            } else {
                var workspaceId = context.enqueueWorkspaceId();
                if (workspaceId.isEmpty()) {
                    LOG.warn(
                            "Could not queue {} of {} review notification jobs ({})",
                            event.decisionIds().size(),
                            event.decisionIds().size(),
                            "NoWorkspace");
                    return;
                }
                context.runIn(workspaceId.get(), enqueue);
            }
        } catch (RuntimeException exception) {
            // The exception text may carry mail data; the decision is already committed.
            LOG.warn(
                    "Could not queue {} of {} review notification jobs ({})",
                    event.decisionIds().size() - queued.get(),
                    event.decisionIds().size(),
                    exception.getClass().getSimpleName());
        }
    }
}
