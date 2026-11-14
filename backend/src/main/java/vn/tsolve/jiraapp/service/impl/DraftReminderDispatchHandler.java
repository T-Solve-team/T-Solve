package vn.tsolve.jiraapp.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.tsolve.jiraapp.client.DraftReminderMailClient;
import vn.tsolve.jiraapp.dto.DraftReminderDispatchRequest;
import vn.tsolve.jiraapp.dto.DraftReminderWorkspaceRequest;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.service.WorkerTenantService;

/** Starts the day's Draft reminder in every active Workspace (jira-app AC-011.1). */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class DraftReminderDispatchHandler implements JobRequestHandler<DraftReminderDispatchRequest> {
    private static final Logger log = LoggerFactory.getLogger(DraftReminderDispatchHandler.class);

    private final WorkerTenantService tenants;
    private final JobRunrRuntime runtime;
    private final ObjectProvider<DraftReminderMailClient> mailers;
    private final Clock clock;

    public DraftReminderDispatchHandler(
            WorkerTenantService tenants,
            JobRunrRuntime runtime,
            ObjectProvider<DraftReminderMailClient> mailers,
            Clock clock) {
        this.tenants = tenants;
        this.runtime = runtime;
        this.mailers = mailers;
        this.clock = clock;
    }

    @Override
    public void run(DraftReminderDispatchRequest request) {
        // Checked once here, so a worker without SMTP gives one warning per run, not a failed job per writer
        // (AC-011.6).
        if (mailers.getIfAvailable() == null) {
            log.warn("Draft reminder skipped: no mail sender is configured in this process");
            return;
        }
        LocalDate day = LocalDate.now(clock.withZone(DraftReminderRegistration.ZONE));
        tenants.activeWorkspaceIds()
                .forEach(workspace -> runtime.scheduler().enqueue(new DraftReminderWorkspaceRequest(workspace, day)));
    }
}
