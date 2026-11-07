package vn.tsolve.search.scope.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.scope.service.VisibleScope;

/** Fails closed until identity/project integration implements VisibleScope; replace this bean then. */
@Component
@ConditionalOnExpression("'${SEARCH_DEV_SCOPE:}' != 'all'")
public class NoVisibleScope implements VisibleScope {
    @Override
    public Scope of(TSolveUser user) {
        return Scope.empty();
    }
}
