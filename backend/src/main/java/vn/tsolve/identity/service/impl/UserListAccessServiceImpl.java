package vn.tsolve.identity.service.impl;

import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.exception.UserListException;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.UserListAccessService;
import vn.tsolve.identity.session.WorkspaceAccountPrincipal;

@Service
@ConditionalOnProperty(name = "tsolve.sql-workspace.enabled", havingValue = "true")
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
                || !(authentication.getPrincipal() instanceof WorkspaceAccountPrincipal principal)
                || context.currentWorkspaceId()
                        .filter(id -> id == principal.workspaceId())
                        .isEmpty()) {
            throw expired();
        }
    }

    @Override
    public Set<Role> requireAdmin(Authentication authentication) {
        validateSession(authentication);
        var principal = (WorkspaceAccountPrincipal) authentication.getPrincipal();
        var account = repository
                .accountAccess(principal.accountId())
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
