package vn.tsolve.tracker.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.service.SuggestionJobQueue;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

/**
 * Hands the suggestion job to the worker's JobRunr (KAN-102) while the worker is enabled (AC-011.1). The worker
 * queues for the bound Workspace only, and only when it may enqueue; with nothing bound the job is not queued. Never
 * throws: an unqueued job leaves the issue without a suggestion and is logged.
 */
@Service
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class WorkerSuggestionJobQueue implements SuggestionJobQueue {

    private static final Logger log = LoggerFactory.getLogger(WorkerSuggestionJobQueue.class);

    private final TenantJobService jobs;
    private final WorkerTenantService tenants;

    public WorkerSuggestionJobQueue(TenantJobService jobs, WorkerTenantService tenants) {
        this.jobs = jobs;
        this.tenants = tenants;
    }

    @Override
    public void enqueue(SuggestionJobRequest request) {
        try {
            if (tenants.currentWorkspaceId().isPresent()
                    && tenants.enqueueWorkspaceId().isPresent()) {
                jobs.enqueue(SuggestionJobHandler.TYPE, request.suggestionId());
            } else {
                log.warn("Suggestion job not queued, no Workspace to queue for: {}", request.issueKey());
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Suggestion job not queued for {}: {}",
                    request.issueKey(),
                    e.getClass().getSimpleName());
        }
    }
}
