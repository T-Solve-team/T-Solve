package vn.tsolve.worker.service;

import java.util.Optional;
import java.util.UUID;

/** Platform integration port; the mock supplies context propagation, not database routing. */
public interface WorkerTenantService {
    Optional<UUID> currentWorkspaceId();

    boolean isWorkspaceActive(UUID workspaceId);

    /** Must reject cross-workspace nesting and restore the prior binding, including on failure. */
    void runIn(UUID workspaceId, Runnable action);
}
