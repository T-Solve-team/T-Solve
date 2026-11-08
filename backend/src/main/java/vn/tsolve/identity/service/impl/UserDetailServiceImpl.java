package vn.tsolve.identity.service.impl;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserDetailResponse;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse;
import vn.tsolve.identity.repository.UserDetailRepository;
import vn.tsolve.identity.service.UserDetailService;
import vn.tsolve.identity.signin.port.TenantScope;

@Service
@Transactional(readOnly = true)
public class UserDetailServiceImpl implements UserDetailService {
    private final TenantScope tenantScope;
    private final UserDetailRepository repository;

    public UserDetailServiceImpl(TenantScope tenantScope, UserDetailRepository repository) {
        this.tenantScope = tenantScope;
        this.repository = repository;
    }

    @Override
    public UserDetailResponse getDetail(UUID userId, TSolveUser caller) {
        tenantScope.currentWorkspaceId();
        UserAccountResponse account = requireAccount(userId);
        return UserDetailResponse.from(
                account,
                userId.equals(caller.id()),
                repository.ticketResolverProjectIds(userId),
                repository.projects(userId),
                repository.managedSpaces(userId));
    }

    @Override
    public UserHandoverOptionsResponse getHandoverOptions(UUID userId, TSolveUser caller) {
        tenantScope.currentWorkspaceId();
        requireAccount(userId);
        return repository.handoverOptions(userId);
    }

    private UserAccountResponse requireAccount(UUID userId) {
        return repository.account(userId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
    }
}
