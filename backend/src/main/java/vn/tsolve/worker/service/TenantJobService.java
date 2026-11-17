package vn.tsolve.worker.service;

import java.util.UUID;

public interface TenantJobService {
    UUID enqueue(String handlerType, UUID recordId);

    /**
     * {@link #enqueue} for an after-commit listener of an HTTP request: an unbound thread is first bound with
     * {@link WorkerTenantService#runIn} to {@link WorkerTenantService#enqueueWorkspaceId}; refused when that is empty.
     */
    UUID enqueueFromRequest(String handlerType, UUID recordId);
}
