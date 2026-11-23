package vn.tsolve.worker.service.mock;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.worker.service.WorkerTenantService;

/** Temporary platform mock: never routes queries or grants access to business data. */
public class MockWorkerTenantService implements WorkerTenantService {
    private final Set<UUID> active;
    private final Set<UUID> suspended;
    private final TenantContext holder;

    /** For tests that bind nothing else: a holder that knows every id, so only the mock's own lists decide. */
    public MockWorkerTenantService(Set<UUID> active, Set<UUID> suspended) {
        this(active, suspended, new ThreadLocalTenantContext(id -> true));
    }

    /** {@code holder} is the one per-thread binding: {@code runIn} and {@code currentWorkspaceId} go through it. */
    public MockWorkerTenantService(Set<UUID> active, Set<UUID> suspended, TenantContext holder) {
        this.active = Set.copyOf(active);
        this.suspended = Set.copyOf(suspended);
        this.holder = Objects.requireNonNull(holder, "holder");
        if (!Collections.disjoint(this.active, this.suspended)) {
            throw new IllegalArgumentException("Mock workspace cannot be both active and suspended");
        }
    }

    @Override
    public Optional<UUID> currentWorkspaceId() {
        return holder.currentWorkspaceId();
    }

    /**
     * Local/demo stacks only: the single Active Workspace, else the request's TenantContext Workspace when it is
     * Active; otherwise it refuses, never guesses.
     */
    @Override
    public Optional<UUID> enqueueWorkspaceId() {
        Optional<UUID> bound = currentWorkspaceId();
        if (bound.isPresent()) {
            // ponytail: drop the request check in platform Task 6a-2, with StubTenantContext
            return RequestContextHolder.getRequestAttributes() == null ? bound : bound.filter(active::contains);
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
            throw new IllegalStateException("Unknown mock workspace");
        }
        holder.runIn(workspaceId, action);
    }
}
