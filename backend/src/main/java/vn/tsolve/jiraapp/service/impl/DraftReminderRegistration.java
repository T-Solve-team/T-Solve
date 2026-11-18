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
 * run time comes from the environment, so a deployment moves it without a code change; the zone is the system's.
 */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class DraftReminderRegistration {
    public static final String ID = "jira-app-draft-reminder";
    public static final String CRON = "0 8 * * *";
    public static final String CRON_VARIABLE = "JIRA_APP_DRAFT_REMINDER_CRON";
    public static final ZoneId ZONE = DraftReminderMessage.ZONE;

    private final JobRunrRuntime runtime;
    private final String cron;

    public DraftReminderRegistration(
            JobRunrRuntime runtime, @Value("${" + CRON_VARIABLE + ":" + CRON + "}") String cron) {
        this.runtime = runtime;
        this.cron = cron(cron);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        runtime.scheduler().scheduleRecurrently(ID, cron, ZONE, new DraftReminderDispatchRequest());
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
}
