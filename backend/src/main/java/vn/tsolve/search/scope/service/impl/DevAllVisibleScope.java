package vn.tsolve.search.scope.service.impl;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.scope.repository.ScopeTicketRepository;
import vn.tsolve.search.scope.service.VisibleScope;

/** Local demo data only; this supplies no signed-in user and must never be enabled in deployment. */
@Component
@ConditionalOnExpression("'${SEARCH_DEV_SCOPE:}' == 'all'")
public class DevAllVisibleScope implements VisibleScope {
    private static final Logger log = LoggerFactory.getLogger(DevAllVisibleScope.class);
    private final ScopeTicketRepository repository;

    public DevAllVisibleScope(ScopeTicketRepository repository) {
        this.repository = repository;
        log.warn("SEARCH_DEV_SCOPE=all: every user sees every Active Department — local demo only");
    }

    @Override
    public Scope of(TSolveUser user) {
        return new Scope(repository.activeDepartments(), repository.projects(), Optional.empty());
    }
}
