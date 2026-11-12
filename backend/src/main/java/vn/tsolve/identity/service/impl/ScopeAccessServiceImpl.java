package vn.tsolve.identity.service.impl;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.identity.exception.ScopeAccessDeniedException;
import vn.tsolve.identity.repository.ScopeAccessRepository;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.identity.signin.port.TenantScope;

@Service
@Transactional(readOnly = true)
public class ScopeAccessServiceImpl implements ScopeAccessService {
    private final TenantScope tenantScope;
    private final ScopeAccessRepository repository;

    public ScopeAccessServiceImpl(TenantScope tenantScope, ScopeAccessRepository repository) {
        this.tenantScope = tenantScope;
        this.repository = repository;
    }

    @Override
    public boolean canAccessDepartment(UUID accountId, UUID departmentId) {
        tenantScope.currentWorkspaceId();
        if (accountId == null || departmentId == null) {
            return false;
        }
        return repository.department(accountId, departmentId);
    }

    @Override
    public boolean canAccessProject(UUID accountId, UUID projectId) {
        tenantScope.currentWorkspaceId();
        if (accountId == null || projectId == null) {
            return false;
        }
        return repository.project(accountId, projectId);
    }

    @Override
    public boolean canAccessSpace(UUID accountId, UUID spaceId) {
        tenantScope.currentWorkspaceId();
        if (accountId == null || spaceId == null) {
            return false;
        }
        return repository.space(accountId, spaceId);
    }

    @Override
    public void requireDepartment(UUID accountId, UUID departmentId) {
        if (!canAccessDepartment(accountId, departmentId)) {
            throw new ScopeAccessDeniedException();
        }
    }

    @Override
    public void requireProject(UUID accountId, UUID projectId) {
        if (!canAccessProject(accountId, projectId)) {
            throw new ScopeAccessDeniedException();
        }
    }

    @Override
    public void requireSpace(UUID accountId, UUID spaceId) {
        if (!canAccessSpace(accountId, spaceId)) {
            throw new ScopeAccessDeniedException();
        }
    }
}
