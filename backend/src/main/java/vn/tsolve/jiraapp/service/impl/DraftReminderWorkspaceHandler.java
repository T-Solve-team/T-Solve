package vn.tsolve.jiraapp.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.tsolve.jiraapp.dto.DraftReminderEmailRequest;
import vn.tsolve.jiraapp.dto.DraftReminderWorkspaceRequest;
import vn.tsolve.ticket.dto.OpenDraft;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.service.TenantExecutionService;
import vn.tsolve.worker.service.WorkerTenantService;

/** Queues one Draft reminder email per writer of a Workspace for the day (jira-app AC-011.2, AC-011.5). */
@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class DraftReminderWorkspaceHandler implements JobRequestHandler<DraftReminderWorkspaceRequest> {
    private static final Logger log = LoggerFactory.getLogger(DraftReminderWorkspaceHandler.class);

    private final WorkerTenantService tenants;
    private final TenantExecutionService execution;
    private final SolutionDraftService drafts;
    private final JobRunrRuntime runtime;

    public DraftReminderWorkspaceHandler(
            WorkerTenantService tenants,
            TenantExecutionService execution,
            SolutionDraftService drafts,
            JobRunrRuntime runtime) {
        this.tenants = tenants;
        this.execution = execution;
        this.drafts = drafts;
        this.runtime = runtime;
    }

    /**
     * The email job's id for a writer and a day. JobRunr does not queue a job whose id it already has, so a second run
     * of the same day mails nobody twice (AC-011.5).
     */
    static UUID jobId(UUID workspaceId, UUID accountId, LocalDate day) {
        String key = "jira-app-draft-reminder:" + workspaceId + ':' + accountId + ':' + day;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void run(DraftReminderWorkspaceRequest request) {
        UUID workspace = request.workspaceId();
        if (!tenants.isWorkspaceActive(workspace)) {
            log.info("Draft reminder skipped inactive Workspace {}", workspace);
            return;
        }
        List<UUID> writers = new ArrayList<>();
        execution.runInTenant(workspace, () -> {
            Set<UUID> seen = new LinkedHashSet<>();
            for (OpenDraft draft : drafts.findOpenDrafts()) {
                seen.add(draft.creatorId());
            }
            writers.addAll(seen);
        });
        for (UUID writer : writers) {
            runtime.scheduler()
                    .enqueue(
                            jobId(workspace, writer, request.day()),
                            new DraftReminderEmailRequest(workspace, writer, request.day()));
        }
        log.info("Draft reminder queued {} emails in Workspace {}", writers.size(), workspace);
    }
}
