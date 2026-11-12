package vn.tsolve.workspace.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.lookup.WorkspaceStatusLookup;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.repository.WorkspaceInfoRepository;

@Service
public class WorkspaceStatusLookupImpl implements WorkspaceStatusLookup {
    private final TenantScope scope;
    private final WorkspaceInfoRepository repository;

    public WorkspaceStatusLookupImpl(TenantScope scope, WorkspaceInfoRepository repository) {
        this.scope = scope;
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReady() {
        return repository.existsById(scope.currentWorkspaceId());
    }
}
