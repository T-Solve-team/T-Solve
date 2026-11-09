package vn.tsolve.worker.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Platform integration port; the mock supplies context propagation, not database routing. */
public interface WorkerTenantService {
    Optional<UUID> currentWorkspaceId();

    /**
     * Workspace a caller on an unbound thread (an after-commit listener of an HTTP request) may bind with
     * {@link #runIn} before enqueueing; empty means none can be chosen.
     */
    default Optional<UUID> enqueueWorkspaceId() {
        return Optional.empty();
    }

    boolean isWorkspaceActive(UUID workspaceId);

    /** Returns the Workspaces Active at the moment of the call; binds nothing on the calling thread. */
    Set<UUID> activeWorkspaceIds();

    /** Must reject cross-workspace nesting and restore the prior binding, including on failure. */
    void runIn(UUID workspaceId, Runnable action);
}
