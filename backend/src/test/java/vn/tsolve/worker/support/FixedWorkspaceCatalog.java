package vn.tsolve.worker.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

/** Test fixture: a {@link WorkspaceCatalog} over a fixed id-to-status map. Immutable, so safe across threads. */
public class FixedWorkspaceCatalog implements WorkspaceCatalog {
    private final Map<UUID, WorkspaceStatus> workspaces;

    public FixedWorkspaceCatalog(Map<UUID, WorkspaceStatus> workspaces) {
        this.workspaces = Map.copyOf(workspaces);
    }

    @Override
    public Optional<WorkspaceSummary> findById(UUID workspaceId) {
        return Optional.ofNullable(workspaces.get(workspaceId)).map(status -> summary(workspaceId, status));
    }

    @Override
    public Optional<WorkspaceSummary> findBySiteName(String siteName) {
        return Optional.empty();
    }

    @Override
    public Optional<WorkspaceSummary> findByCloudId(String cloudId) {
        return Optional.empty();
    }

    @Override
    public List<WorkspaceSummary> findActive() {
        return workspaces.entrySet().stream()
                .filter(entry -> entry.getValue() == WorkspaceStatus.ACTIVE)
                .map(entry -> summary(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public WorkspaceSummary linkJiraSite(String siteUrl) {
        throw new UnsupportedOperationException("not used by the worker");
    }

    @Override
    public void claimEmail(String email) {
        throw new UnsupportedOperationException("not used by the worker");
    }

    private static WorkspaceSummary summary(UUID id, WorkspaceStatus status) {
        return new WorkspaceSummary(
                id, "ws-" + id, status, "example.test", Optional.empty(), Optional.empty(), Optional.empty());
    }
}
