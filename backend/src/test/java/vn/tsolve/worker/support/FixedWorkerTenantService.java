package vn.tsolve.worker.support;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.worker.service.WorkerTenantService;

/** Test fixture: a {@link WorkerTenantService} over fixed Active and Suspended lists. Never routes queries. */
public class FixedWorkerTenantService implements WorkerTenantService {
    private final Set<UUID> active;
    private final Set<UUID> suspended;
    private final TenantContext holder;

    /** {@code holder} is the one per-thread binding: {@code runIn} and {@code currentWorkspaceId} go through it. */
    public FixedWorkerTenantService(Set<UUID> active, Set<UUID> suspended, TenantContext holder) {
        this.active = Set.copyOf(active);
        this.suspended = Set.copyOf(suspended);
        this.holder = Objects.requireNonNull(holder, "holder");
        if (!Collections.disjoint(this.active, this.suspended)) {
            throw new IllegalArgumentException("A fixed workspace cannot be both active and suspended");
        }
    }

    @Override
    public Optional<UUID> currentWorkspaceId() {
        return holder.currentWorkspaceId();
    }

    /**
     * The bound Workspace (only when it is Active, on any thread, so a Suspended or unlisted one yields empty and
     * callers queue no job), else the single Active Workspace; otherwise it refuses, never guesses.
     */
    @Override
    public Optional<UUID> enqueueWorkspaceId() {
        Optional<UUID> bound = currentWorkspaceId();
        if (bound.isPresent()) {
            return bound.filter(active::contains);
        }
        return active.size() == 1 ? active.stream().findFirst() : Optional.empty();
    }

    @Override
    public Set<UUID> activeWorkspaceIds() {
        return active;
    }

    @Override
    public boolean isWorkspaceActive(UUID workspaceId) {
        return active.contains(Objects.requireNonNull(workspaceId, "workspaceId"));
    }

    @Override
    public void runIn(UUID workspaceId, Runnable action) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(action, "action");
        if (!active.contains(workspaceId) && !suspended.contains(workspaceId)) {
            throw new IllegalStateException("Unknown fixed workspace");
        }
        holder.runIn(workspaceId, action);
    }
}
