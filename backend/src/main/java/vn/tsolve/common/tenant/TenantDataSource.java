package vn.tsolve.common.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.LongFunction;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

public final class TenantDataSource extends AbstractDataSource {
    private final TenantContext context;
    private final LongFunction<? extends DataSource> pools;

    public TenantDataSource(TenantContext context, LongFunction<? extends DataSource> pools) {
        this.context = context;
        this.pools = pools;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Long workspaceId = context.requireWorkspaceId();
        try {
            return pools.apply(workspaceId).getConnection();
        } catch (RuntimeException | SQLException failure) {
            // Driver errors can contain hosts and usernames; never propagate them to logs or API errors.
            throw new TenantAccessException();
        }
    }

    @Override
    public Connection getConnection(String username, String password) {
        throw new TenantAccessException();
    }
}
