package vn.tsolve.platform.config;

import com.zaxxer.hikari.HikariDataSource;
import vn.tsolve.common.tenant.ScopedTenantContext;
import vn.tsolve.common.tenant.TenantDatabase;
import vn.tsolve.platform.service.WorkspaceCatalogService;
import vn.tsolve.platform.web.WorkspaceHost;

public record SqlWorkspaceResources(
        HikariDataSource catalogPool,
        TenantPoolProvider pools,
        ScopedTenantContext context,
        TenantDatabase database,
        WorkspaceCatalogService catalog,
        WorkspaceHost hosts)
        implements AutoCloseable {
    @Override
    public void close() {
        try {
            pools.close();
        } finally {
            catalogPool.close();
        }
    }
}
