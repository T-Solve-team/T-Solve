package vn.tsolve.identity.service.impl;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.tsolve.identity.dto.UserListDepartmentRef;
import vn.tsolve.identity.dto.UserListItem;
import vn.tsolve.identity.dto.UserListQuery;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.UserListAccessService;
import vn.tsolve.identity.service.UserListService;

@Service
@ConditionalOnProperty(name = "tsolve.identity.user-list.enabled", havingValue = "true")
public class UserListServiceImpl implements UserListService {
    private final UserListTransaction database;
    private final UserListAccessService access;
    private final UserListRepository repository;

    public UserListServiceImpl(
            UserListTransaction database, UserListAccessService access, UserListRepository repository) {
        this.database = database;
        this.access = access;
        this.repository = repository;
    }

    @Override
    public Page<UserListItem> list(UserListQuery query, Authentication authentication) {
        access.validateSession(authentication);
        return database.read(() -> {
            // Recheck inside the same snapshot as count/page/roles, closing the gap after the security filter.
            access.requireAdmin(authentication);
            return repository.list(query);
        });
    }

    @Override
    public List<UserListDepartmentRef> departments(Authentication authentication) {
        access.validateSession(authentication);
        return database.read(() -> {
            access.requireAdmin(authentication);
            return repository.departments();
        });
    }
}
