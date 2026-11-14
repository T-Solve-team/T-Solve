package vn.tsolve.jiraapp.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.tsolve.jiraapp.dto.DraftReminderDispatchRequest;
import vn.tsolve.jiraapp.dto.DraftReminderWorkspaceRequest;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.service.WorkerTenantService;

/**
 * Starts the day's Draft reminder in every active Workspace (jira-app AC-011.1). Whether an SMTP sender is configured
 * is the outbox's concern (AC-011.6).
 */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class DraftReminderDispatchHandler implements JobRequestHandler<DraftReminderDispatchRequest> {

    private final WorkerTenantService tenants;
    private final JobRunrRuntime runtime;
    private final Clock clock;

    public DraftReminderDispatchHandler(WorkerTenantService tenants, JobRunrRuntime runtime, Clock clock) {
        this.tenants = tenants;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public void run(DraftReminderDispatchRequest request) {
        LocalDate day = LocalDate.now(clock.withZone(DraftReminderRegistration.ZONE));
        tenants.activeWorkspaceIds()
                .forEach(workspace -> runtime.scheduler().enqueue(new DraftReminderWorkspaceRequest(workspace, day)));
    }
}
