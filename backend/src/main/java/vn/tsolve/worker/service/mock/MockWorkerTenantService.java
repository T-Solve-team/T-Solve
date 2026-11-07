package vn.tsolve.worker.service.mock;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import vn.tsolve.worker.service.WorkerTenantService;

/** Temporary platform mock: never routes queries or grants access to business data. */
public class MockWorkerTenantService implements WorkerTenantService {
    private final ThreadLocal<UUID> workspace = new ThreadLocal<>();
    private final Set<UUID> active;
    private final Set<UUID> suspended;

    public MockWorkerTenantService(Set<UUID> active, Set<UUID> suspended) {
        this.active = Set.copyOf(active);
        this.suspended = Set.copyOf(suspended);
        if (!Collections.disjoint(this.active, this.suspended)) {
            throw new IllegalArgumentException("Mock workspace cannot be both active and suspended");
        }
    }

    @Override
    public Optional<UUID> currentWorkspaceId() {
        return Optional.ofNullable(workspace.get());
    }

    @Override
    public boolean isWorkspaceActive(UUID workspaceId) {
        return active.contains(Objects.requireNonNull(workspaceId, "workspaceId"));
    }

    @Override
    public void runIn(UUID workspaceId, Runnable action) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(action, "action");
        UUID previous = workspace.get();
        if (previous != null && !previous.equals(workspaceId)) {
            throw new IllegalStateException("Mock context cannot switch workspace");
        }
        if (!active.contains(workspaceId) && !suspended.contains(workspaceId)) {
            throw new IllegalStateException("Unknown mock workspace");
        }
        workspace.set(workspaceId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                workspace.remove();
            } else {
                workspace.set(previous);
            }
        }
    }
}
