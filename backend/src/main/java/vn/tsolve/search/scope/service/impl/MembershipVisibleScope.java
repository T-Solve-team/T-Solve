package vn.tsolve.search.scope.service.impl;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.scope.repository.ScopeMembershipRepository;
import vn.tsolve.search.scope.service.VisibleScope;

@Component
@ConditionalOnExpression("'${SEARCH_DEV_SCOPE:}' != 'all'")
public class MembershipVisibleScope implements VisibleScope {
    private final ScopeMembershipRepository repository;

    public MembershipVisibleScope(ScopeMembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Scope of(TSolveUser user) {
        return new Scope(
                repository.activeDepartments(user.id()), repository.managedProjects(user.id()), Optional.of(user.id()));
    }
}
