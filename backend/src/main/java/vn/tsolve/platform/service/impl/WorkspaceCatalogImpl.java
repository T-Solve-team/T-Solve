package vn.tsolve.platform.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.registry.RegistryRepository;
import vn.tsolve.platform.service.WorkspaceCatalog;

/** {@link WorkspaceCatalog} over the Registry database; registered only inside {@code RegistryDataSourceConfig}. */
public class WorkspaceCatalogImpl implements WorkspaceCatalog {

    private final RegistryRepository registry;
    private final TenantContext tenantContext;

    public WorkspaceCatalogImpl(RegistryRepository registry, TenantContext tenantContext) {
        this.registry = registry;
        this.tenantContext = tenantContext;
    }

    @Override
    public Optional<WorkspaceSummary> findById(UUID workspaceId) {
        return registry.findById(workspaceId);
    }

    @Override
    public Optional<WorkspaceSummary> findBySiteName(String siteName) {
        return registry.findBySiteName(siteName);
    }

    @Override
    public Optional<WorkspaceSummary> findByCloudId(String cloudId) {
        return registry.findByCloudId(cloudId);
    }

    @Override
    public List<WorkspaceSummary> findActive() {
        return registry.findByStatus(WorkspaceStatus.ACTIVE);
    }

    @Override
    public WorkspaceSummary linkJiraSite(String siteUrl) {
        throw new UnsupportedOperationException("linkJiraSite is not implemented yet");
    }

    @Override
    public void claimEmail(String email) {
        UUID workspaceId = tenantContext.requireWorkspaceId();
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        registry.insertEmailIfAbsent(normalized, workspaceId);
        UUID owner = registry.emailOwner(normalized).orElseThrow();
        if (!workspaceId.equals(owner)) {
            throw new BusinessRuleException("MSG155", Map.of(), "email");
        }
    }
}
