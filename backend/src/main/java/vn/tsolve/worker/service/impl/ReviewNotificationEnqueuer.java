package vn.tsolve.worker.service.impl;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
import vn.tsolve.worker.service.TenantJobService;

/** Queues the writer email of committed Reject and Request Changes decisions (ticket-review STORY-010). */
@Component
public class ReviewNotificationEnqueuer {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewNotificationEnqueuer.class);

    private final ObjectProvider<TenantJobService> jobs;

    public ReviewNotificationEnqueuer(ObjectProvider<TenantJobService> jobs) {
        this.jobs = jobs;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(ReviewFeedbackDecidedEvent event) {
        TenantJobService queue = jobs.getIfAvailable();
        if (queue == null) {
            // tsolve.worker.enabled is off in this process: the decision stands and no email is sent.
            return;
        }
        int failed = 0;
        for (UUID decisionId : event.decisionIds()) {
            try {
                queue.enqueue(ReviewNotificationJobHandler.TYPE, decisionId);
            } catch (RuntimeException ignored) {
                // The exception text may carry mail data; the decision is already committed.
                failed++;
            }
        }
        if (failed > 0) {
            LOG.warn("Could not queue {} of {} review notification jobs", failed, event.decisionIds().size());
        }
    }
}
