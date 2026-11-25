package vn.tsolve.jiraapp.service.impl;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

/** Resolves a Jira site's {@code cloudId} to its Workspace id through {@link WorkspaceCatalog}. */
@Service
public final class CatalogJiraWorkspaceLookup implements JiraWorkspaceLookup {

    private final ObjectProvider<WorkspaceCatalog> catalog;

    public CatalogJiraWorkspaceLookup(ObjectProvider<WorkspaceCatalog> catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<UUID> workspaceOf(String cloudId) {
        if (cloudId == null) {
            return Optional.empty();
        }
        WorkspaceCatalog activeCatalog = catalog.getIfAvailable();
        if (activeCatalog == null) {
            return Optional.empty();
        }
        return activeCatalog.findByCloudId(cloudId).map(WorkspaceSummary::id);
    }
}
