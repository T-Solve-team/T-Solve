package vn.tsolve.jiraapp.service.impl;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.tsolve.jiraapp.dto.DraftReminderDispatchRequest;
import vn.tsolve.worker.config.JobRunrRuntime;

/**
 * Registers the daily Draft reminder (jira-app AC-011.1). Its own recurring id: sharing JOB-02's would replace it.
 */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class DraftReminderRegistration {
    public static final String ID = "jira-app-draft-reminder";
    public static final String CRON = "0 8 * * *";
    public static final ZoneId ZONE = DraftReminderMessage.ZONE;

    private final JobRunrRuntime runtime;
    private final String cron;

    // The schedule can be moved for a local check (e.g. every minute); the default is the spec's 08:00.
    public DraftReminderRegistration(
            JobRunrRuntime runtime, @Value("${JIRA_APP_DRAFT_REMINDER_CRON:" + CRON + "}") String cron) {
        this.runtime = runtime;
        this.cron = cron;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        runtime.scheduler().scheduleRecurrently(ID, cron, ZONE, new DraftReminderDispatchRequest());
    }
}
