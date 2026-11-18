package vn.tsolve.ticket.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import vn.tsolve.ticket.repository.ImportPreviewRepository;

/**
 * Deletes Import Previews nobody confirmed within 24 hours (spec STORY-006, design D9). The run time
 * (`IMPORT_PREVIEW_CLEANUP_CRON`, default 01:00) and its time zone (`TSOLVE_JOB_TIME_ZONE`, shared by every scheduled
 * job, default Asia/Ho_Chi_Minh) come from the environment, so a deployment moves them without a code change.
 */
@Component
public class PreviewCleanupJob implements SchedulingConfigurer {

    static final Duration MAX_AGE = Duration.ofHours(24);
    static final String CRON = "0 1 * * *";
    static final String CRON_VARIABLE = "IMPORT_PREVIEW_CLEANUP_CRON";
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String ZONE_VARIABLE = "TSOLVE_JOB_TIME_ZONE";

    private static final Logger log = LoggerFactory.getLogger(PreviewCleanupJob.class);

    private final ImportPreviewRepository previews;
    private final Clock clock;

    private final CronTrigger trigger;

    public PreviewCleanupJob(
            ImportPreviewRepository previews,
            Clock clock,
            @Value("${" + CRON_VARIABLE + ":}") String cron,
            @Value("${" + ZONE_VARIABLE + ":}") String zone) {
        this.previews = previews;
        this.clock = clock;
        this.trigger = new CronTrigger(springCron(cron), zone(zone));
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::deleteExpiredPreviews, trigger);
    }

    CronTrigger trigger() {
        return trigger;
    }

    /** Each preview is deleted in its own transaction, so one failure stops nothing. */
    public int deleteExpiredPreviews() {
        Instant cutoff = Instant.now(clock).minus(MAX_AGE);
        int deleted = 0;
        for (Long id : previews.findIdsCreatedBefore(cutoff)) {
            try {
                previews.deleteById(id);
                deleted++;
            } catch (RuntimeException e) {
                log.warn("Could not delete expired import preview {}", id, e);
            }
        }
        return deleted;
    }

    // The .env uses the 5-field cron of the other jobs (minute hour day month weekday); Spring wants seconds first.
    // A blank value means the default; a wrong one stops the start.
    static String springCron(String configured) {
        String value = configured == null || configured.isBlank() ? CRON : configured.strip();
        String spring = value.split("\s+").length == 5 ? "0 " + value : value;
        try {
            CronExpression.parse(spring);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException(CRON_VARIABLE + " is not a valid cron expression: " + value, invalid);
        }
        return spring;
    }

    static ZoneId zone(String configured) {
        if (configured == null || configured.isBlank()) {
            return ZONE;
        }
        try {
            return ZoneId.of(configured.strip());
        } catch (RuntimeException invalid) {
            throw new IllegalStateException(
                    ZONE_VARIABLE + " is not a valid time zone: " + configured.strip(), invalid);
        }
    }
}
