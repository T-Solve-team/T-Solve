package vn.tsolve.tracker.service.impl;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.service.SuggestionJobQueue;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

/**
 * Hands the suggestion job to the worker's JobRunr (KAN-102) while the worker is enabled (AC-011.1). The worker
 * needs the Workspace bound when it enqueues; until platform binds the request's Workspace, a configured dev
 * Workspace is bound instead (dev and mock only — remove it then). Never throws: an unqueued job leaves the issue
 * without a suggestion and is logged.
 */
@Service
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class WorkerSuggestionJobQueue implements SuggestionJobQueue {

    private static final Logger log = LoggerFactory.getLogger(WorkerSuggestionJobQueue.class);

    private final TenantJobService jobs;
    private final WorkerTenantService tenants;
    private final UUID devWorkspaceId;

    public WorkerSuggestionJobQueue(
            TenantJobService jobs,
            WorkerTenantService tenants,
            @Value("${tsolve.tracker.dev-workspace-id:}") String devWorkspaceId) {
        this.jobs = jobs;
        this.tenants = tenants;
        this.devWorkspaceId =
                devWorkspaceId == null || devWorkspaceId.isBlank() ? null : UUID.fromString(devWorkspaceId.strip());
    }

    @Override
    public void enqueue(SuggestionJobRequest request) {
        try {
            if (tenants.currentWorkspaceId().isPresent()) {
                jobs.enqueue(SuggestionJobHandler.TYPE, request.suggestionId());
            } else if (devWorkspaceId != null) {
                tenants.runIn(devWorkspaceId, () -> jobs.enqueue(SuggestionJobHandler.TYPE, request.suggestionId()));
            } else {
                log.warn("Suggestion job not queued, no Workspace bound: {}", request.issueKey());
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Suggestion job not queued for {}: {}",
                    request.issueKey(),
                    e.getClass().getSimpleName());
        }
    }
}
