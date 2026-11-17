package vn.tsolve.worker.service.impl;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.jobrunr.scheduling.JobRequestScheduler;
import vn.tsolve.worker.dto.TenantJobRequest;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

public class TenantJobServiceImpl implements TenantJobService {
    private final WorkerTenantService tenants;
    private final JobRequestScheduler scheduler;
    private final TenantJobRequestHandler handler;
    private final Supplier<Optional<UUID>> requestWorkspace;

    public TenantJobServiceImpl(
            WorkerTenantService tenants, JobRequestScheduler scheduler, TenantJobRequestHandler handler) {
        this(tenants, scheduler, handler, Optional::empty);
    }

    /**
     * {@code requestWorkspace} is the Workspace the platform bound to the current request (its TenantContext). An
     * after-commit listener of that request has no worker binding but is still bound to that Workspace (BUG-324).
     */
    public TenantJobServiceImpl(
            WorkerTenantService tenants,
            JobRequestScheduler scheduler,
            TenantJobRequestHandler handler,
            Supplier<Optional<UUID>> requestWorkspace) {
        this.tenants = tenants;
        this.scheduler = scheduler;
        this.handler = handler;
        this.requestWorkspace = requestWorkspace;
    }

    @Override
    public UUID enqueue(String handlerType, UUID recordId) {
        UUID workspaceId = tenants.currentWorkspaceId()
                .or(requestWorkspace)
                .orElseThrow(() -> new IllegalStateException("Enqueue requires a bound workspace"));
        handler.requireHandler(handlerType, recordId);
        return scheduler
                .enqueue(new TenantJobRequest(workspaceId, handlerType, recordId))
                .asUUID();
    }
}
