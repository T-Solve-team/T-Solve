package vn.tsolve.identity.service.impl;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserListQuery;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.UserListService;
import vn.tsolve.identity.signin.port.TenantScope;

@Service
@Transactional(readOnly = true)
public class UserListServiceImpl implements UserListService {
    private final TenantScope tenantScope;
    private final UserListRepository repository;

    public UserListServiceImpl(TenantScope tenantScope, UserListRepository repository) {
        this.tenantScope = tenantScope;
        this.repository = repository;
    }

    @Override
    public Page<UserAccountResponse> list(UserListQuery query) {
        tenantScope.currentWorkspaceId();
        return repository.list(query);
    }

    @Override
    public List<DepartmentRefResponse> departments() {
        tenantScope.currentWorkspaceId();
        return repository.departments();
    }
}
