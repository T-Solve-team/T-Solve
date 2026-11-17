package vn.tsolve.worker.service.mock;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.web.context.request.RequestContextHolder;
import vn.tsolve.worker.service.WorkerTenantService;

/** Temporary platform mock: never routes queries or grants access to business data. */
public class MockWorkerTenantService implements WorkerTenantService {
    private final ThreadLocal<UUID> workspace = new ThreadLocal<>();
    private final Set<UUID> active;
    private final Set<UUID> suspended;

    private final Supplier<Optional<UUID>> requestWorkspace;

    public MockWorkerTenantService(Set<UUID> active, Set<UUID> suspended) {
        this(active, suspended, Optional::empty);
    }

    /** {@code requestWorkspace} is the platform TenantContext's Workspace (BUG-324). */
    public MockWorkerTenantService(Set<UUID> active, Set<UUID> suspended, Supplier<Optional<UUID>> requestWorkspace) {
        this.active = Set.copyOf(active);
        this.suspended = Set.copyOf(suspended);
        this.requestWorkspace = Objects.requireNonNull(requestWorkspace, "requestWorkspace");
        if (!Collections.disjoint(this.active, this.suspended)) {
            throw new IllegalArgumentException("Mock workspace cannot be both active and suspended");
        }
    }

    @Override
    public Optional<UUID> currentWorkspaceId() {
        return Optional.ofNullable(workspace.get());
    }

    /**
     * Local/demo stacks only: the single Active Workspace, else the request's TenantContext Workspace when it is
     * Active; otherwise it refuses, never guesses.
     */
    @Override
    public Optional<UUID> enqueueWorkspaceId() {
        return currentWorkspaceId()
                .or(() -> active.size() == 1 ? active.stream().findFirst() : Optional.empty())
                // ponytail: StubTenantContext answers on every thread, so only a request thread may use it; drop the
                // request check when ThreadLocalTenantContext (platform Task 6) binds per request
                .or(() -> RequestContextHolder.getRequestAttributes() == null
                        ? Optional.empty()
                        : requestWorkspace.get().filter(active::contains));
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
