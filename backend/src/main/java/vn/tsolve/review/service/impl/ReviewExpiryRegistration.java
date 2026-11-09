package vn.tsolve.review.service.impl;

import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.tsolve.review.dto.ReviewExpiryDispatchRequest;
import vn.tsolve.worker.config.JobRunrRuntime;

@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class ReviewExpiryRegistration {
    public static final String ID = "review-job-02-expire-overdue";
    public static final String CRON = "0 0 * * *";
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JobRunrRuntime runtime;

    public ReviewExpiryRegistration(JobRunrRuntime runtime) {
        this.runtime = runtime;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        runtime.scheduler().scheduleRecurrently(ID, CRON, ZONE, new ReviewExpiryDispatchRequest());
    }
}
