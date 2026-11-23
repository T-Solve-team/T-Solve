package vn.tsolve.worker.service.impl;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jobrunr.scheduling.JobRequestScheduler;
import vn.tsolve.worker.dto.TenantJobRequest;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

public class TenantJobServiceImpl implements TenantJobService {
    private final WorkerTenantService tenants;
    private final JobRequestScheduler scheduler;
    private final TenantJobRequestHandler handler;

    public TenantJobServiceImpl(
            WorkerTenantService tenants, JobRequestScheduler scheduler, TenantJobRequestHandler handler) {
        this.tenants = tenants;
        this.scheduler = scheduler;
        this.handler = handler;
    }

    @Override
    public UUID enqueue(String handlerType, UUID recordId) {
        UUID workspaceId = tenants.currentWorkspaceId()
                .orElseThrow(() -> new IllegalStateException("Enqueue requires a bound workspace"));
        handler.requireHandler(handlerType, recordId);
        return scheduler
                .enqueue(new TenantJobRequest(workspaceId, handlerType, recordId))
                .asUUID();
    }

    @Override
    public UUID enqueueFromRequest(String handlerType, UUID recordId) {
        if (tenants.currentWorkspaceId().isPresent()) {
            if (tenants.enqueueWorkspaceId().isEmpty()) {
                throw new IllegalStateException("Enqueue requires an active workspace");
            }
            return enqueue(handlerType, recordId);
        }
        UUID workspaceId = tenants.enqueueWorkspaceId()
                .orElseThrow(() -> new IllegalStateException("Enqueue requires a bound workspace"));
        AtomicReference<UUID> jobId = new AtomicReference<>();
        tenants.runIn(workspaceId, () -> jobId.set(enqueue(handlerType, recordId)));
        return jobId.get();
    }
}
