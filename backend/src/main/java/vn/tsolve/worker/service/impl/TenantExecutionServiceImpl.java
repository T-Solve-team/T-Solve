package vn.tsolve.worker.service.impl;

import java.util.Objects;
import java.util.UUID;
import vn.tsolve.worker.service.TenantExecutionService;
import vn.tsolve.worker.service.WorkerTenantService;

public class TenantExecutionServiceImpl implements TenantExecutionService {
    private final WorkerTenantService tenants;

    public TenantExecutionServiceImpl(WorkerTenantService tenants) {
        this.tenants = tenants;
    }

    @Override
    public void runInTenant(UUID workspaceId, Runnable action) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(action, "action");
        tenants.currentWorkspaceId().ifPresent(current -> {
            if (!current.equals(workspaceId)) {
                throw new IllegalStateException("A job cannot switch its bound workspace");
            }
        });
        if (!tenants.isWorkspaceActive(workspaceId)) {
            throw new IllegalStateException("Job workspace is unavailable or suspended");
        }
        tenants.runIn(workspaceId, action);
    }
}
