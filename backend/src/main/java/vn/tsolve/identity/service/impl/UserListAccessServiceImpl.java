package vn.tsolve.identity.service.impl;

import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.exception.UserListException;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.UserListAccessService;
import vn.tsolve.identity.session.WorkspaceSessionBinding;

@Service
@ConditionalOnProperty(name = "tsolve.identity.user-list.enabled", havingValue = "true")
public class UserListAccessServiceImpl implements UserListAccessService {
    private final TenantContext context;
    private final UserListRepository repository;

    public UserListAccessServiceImpl(TenantContext context, UserListRepository repository) {
        this.context = context;
        this.repository = repository;
    }

    @Override
    public void validateSession(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof TSolveUser)
                || !(authentication.getDetails() instanceof WorkspaceSessionBinding binding)
                || context.currentWorkspaceId()
                        .filter(binding.workspaceId()::equals)
                        .isEmpty()) {
            throw expired();
        }
    }

    @Override
    public Set<Role> requireAdmin(Authentication authentication) {
        validateSession(authentication);
        var principal = (TSolveUser) authentication.getPrincipal();
        var account = repository
                .accountAccess(principal.id())
                .filter(found -> "ACTIVE".equals(found.status()))
                .orElseThrow(UserListAccessServiceImpl::expired);
        if (!account.roles().contains(Role.ADMIN)) {
            throw new UserListException(HttpStatus.FORBIDDEN, "MSG08");
        }
        return account.roles();
    }

    private static UserListException expired() {
        return new UserListException(HttpStatus.UNAUTHORIZED, "MSG07");
    }
}
