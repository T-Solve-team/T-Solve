package vn.tsolve.common.config;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;
import vn.tsolve.common.security.WorkspaceContext;

/** Gives Hibernate the current Workspace for every {@code @TenantId} field (architecture, Multi-tenancy). */
@Component
public class WorkspaceTenantResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        return WorkspaceContext.current();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(Long workspaceId) {
        return workspaceId == WorkspaceContext.ROOT;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
