package vn.tsolve.jiraapp.service.impl;

import java.time.ZoneId;
import org.jobrunr.scheduling.cron.CronExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.tsolve.jiraapp.dto.DraftReminderDispatchRequest;
import vn.tsolve.worker.config.JobRunrRuntime;

/**
 * Registers the daily Draft reminder (jira-app AC-011.1). Its own recurring id: sharing JOB-02's would replace it. The
 * run time and its time zone come from the environment, so a deployment moves them without a code change. The
 * reminder day stays a Vietnam date (DraftReminderMessage.ZONE) whatever zone the schedule uses.
 */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class DraftReminderRegistration {
    public static final String ID = "jira-app-draft-reminder";
    public static final String CRON = "0 8 * * *";
    public static final String CRON_VARIABLE = "JIRA_APP_DRAFT_REMINDER_CRON";
    /** The default zone of the schedule; `TSOLVE_JOB_TIME_ZONE` replaces it. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static final String ZONE_VARIABLE = "TSOLVE_JOB_TIME_ZONE";

    private final JobRunrRuntime runtime;
    private final String cron;
    private final ZoneId zone;

    public DraftReminderRegistration(
            JobRunrRuntime runtime,
            @Value("${" + CRON_VARIABLE + ":}") String cron,
            @Value("${" + ZONE_VARIABLE + ":}") String zone) {
        this.runtime = runtime;
        this.cron = cron(cron);
        this.zone = zone(zone);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        runtime.scheduler().scheduleRecurrently(ID, cron, zone, new DraftReminderDispatchRequest());
    }

    // A blank value (`JIRA_APP_DRAFT_REMINDER_CRON=` in .env) means the default; a wrong one stops the start.
    static String cron(String configured) {
        String value = configured == null || configured.isBlank() ? CRON : configured.strip();
        try {
            new CronExpression(value);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException(CRON_VARIABLE + " is not a valid cron expression: " + value, invalid);
        }
        return value;
    }

    // A blank value means the default; a wrong one stops the start.
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
