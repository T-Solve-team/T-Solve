package vn.tsolve.review.service.impl;

import java.time.ZoneId;
import org.jobrunr.scheduling.cron.CronExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.tsolve.review.dto.ReviewExpiryDispatchRequest;
import vn.tsolve.worker.config.JobRunrRuntime;

/**
 * Registers JOB-02 (ticket-review AC-014.1). The run time comes from the environment, so a deployment moves it without
 * a code change; the expiry compares due dates with today's Vietnam date, so any run time gives the same result.
 */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class ReviewExpiryRegistration {
    public static final String ID = "review-job-02-expire-overdue";
    public static final String CRON = "0 0 * * *";
    public static final String CRON_VARIABLE = "REVIEW_EXPIRY_CRON";
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JobRunrRuntime runtime;
    private final String cron;

    public ReviewExpiryRegistration(
            JobRunrRuntime runtime, @Value("${" + CRON_VARIABLE + ":" + CRON + "}") String cron) {
        this.runtime = runtime;
        this.cron = cron(cron);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        runtime.scheduler().scheduleRecurrently(ID, cron, ZONE, new ReviewExpiryDispatchRequest());
    }

    // A blank value (`REVIEW_EXPIRY_CRON=` in .env) means the default; a wrong one stops the start.
    static String cron(String configured) {
        String value = configured == null || configured.isBlank() ? CRON : configured.strip();
        try {
            new CronExpression(value);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException(CRON_VARIABLE + " is not a valid cron expression: " + value, invalid);
        }
        return value;
    }
}
