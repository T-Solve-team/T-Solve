package vn.tsolve.worker.service;

import java.util.UUID;

public interface TenantExecutionService {
    void runInTenant(UUID workspaceId, Runnable action);
}
