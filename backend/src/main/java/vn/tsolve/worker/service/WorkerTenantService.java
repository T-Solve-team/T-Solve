package vn.tsolve.worker.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Platform integration port; the mock supplies context propagation, not database routing. */
public interface WorkerTenantService {
    Optional<UUID> currentWorkspaceId();

    /**
     * Workspace a caller may enqueue for (an after-commit listener of an HTTP request): the bound one, or on an
     * unbound thread the one to bind with {@link #runIn}. Callers queue no job when it is empty, which includes a
     * bound Workspace that is not Active.
     */
    Optional<UUID> enqueueWorkspaceId();

    boolean isWorkspaceActive(UUID workspaceId);

    /** Returns the Workspaces Active at the moment of the call; binds nothing on the calling thread. */
    Set<UUID> activeWorkspaceIds();

    /** Must reject cross-workspace nesting and restore the prior binding, including on failure. */
    void runIn(UUID workspaceId, Runnable action);
}
