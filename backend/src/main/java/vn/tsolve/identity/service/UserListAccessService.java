package vn.tsolve.identity.service;

import java.util.Set;
import org.springframework.security.core.Authentication;
import vn.tsolve.common.security.Role;

public interface UserListAccessService {
    void validateSession(Authentication authentication);

    Set<Role> requireAdmin(Authentication authentication);
}
