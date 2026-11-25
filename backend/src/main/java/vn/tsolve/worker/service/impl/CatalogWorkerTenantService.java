package vn.tsolve.worker.service.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.worker.service.WorkerTenantService;

/**
 * The worker's view of the Workspaces, read from the Registry through the catalog (STORY-004). A Registry failure
 * propagates: it is never turned into "no Workspace". The bean exists only in the worker process.
 */
@Service
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class CatalogWorkerTenantService implements WorkerTenantService {
    private final WorkspaceCatalog catalog;
    private final TenantContext holder;

    public CatalogWorkerTenantService(WorkspaceCatalog catalog, TenantContext holder) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.holder = Objects.requireNonNull(holder, "holder");
    }

    @Override
    public Optional<UUID> currentWorkspaceId() {
        return holder.currentWorkspaceId();
    }

    /** The bound Workspace when it is Active (any thread), else the single Active one; otherwise empty. */
    @Override
    public Optional<UUID> enqueueWorkspaceId() {
        Optional<UUID> bound = currentWorkspaceId();
        if (bound.isPresent()) {
            return bound.filter(this::isWorkspaceActive);
        }
        Set<UUID> active = activeWorkspaceIds();
        return active.size() == 1 ? active.stream().findFirst() : Optional.empty();
    }

    @Override
    public Set<UUID> activeWorkspaceIds() {
        return catalog.findActive().stream().map(w -> w.id()).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isWorkspaceActive(UUID workspaceId) {
        return catalog.findById(Objects.requireNonNull(workspaceId, "workspaceId"))
                .filter(w -> w.status() == WorkspaceStatus.ACTIVE)
                .isPresent();
    }

    @Override
    public void runIn(UUID workspaceId, Runnable action) {
        holder.runIn(workspaceId, action);
    }
}
