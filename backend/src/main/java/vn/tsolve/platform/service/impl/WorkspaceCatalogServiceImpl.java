package vn.tsolve.platform.service.impl;

import java.util.Locale;
import java.util.Optional;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.repository.SqlWorkspaceRepository;
import vn.tsolve.platform.service.WorkspaceCatalogService;

public final class WorkspaceCatalogServiceImpl implements WorkspaceCatalogService {
    private final SqlWorkspaceRepository repository;

    public WorkspaceCatalogServiceImpl(SqlWorkspaceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<WorkspaceSummary> findById(long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<WorkspaceSummary> findBySiteName(String siteName) {
        return repository.findBySiteName(siteName.toLowerCase(Locale.ROOT));
    }
}
