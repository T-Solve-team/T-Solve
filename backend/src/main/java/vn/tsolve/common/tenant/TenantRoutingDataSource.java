package vn.tsolve.common.tenant;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends every connection request to the pool of the bound Workspace, and refuses it when none is bound. The row guard's
 * setting {@code app.tenant_id} always comes from the bound id, never from the pool, so a misrouted connection matches
 * no row.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final TenantDataSources pools;

    public TenantRoutingDataSource(TenantDataSources pools) {
        this.pools = pools;
        setTargetDataSources(Map.of());
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantBinding.current();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        return pools.poolOf(TenantBinding.require());
    }

    @Override
    public Connection getConnection() throws SQLException {
        UUID bound = TenantBinding.require();
        Connection connection = pools.poolOf(bound).getConnection();
        // Inside a transaction TenantAwareJpaTransactionManager sets the value for the transaction only.
        return TransactionSynchronizationManager.isActualTransactionActive()
                ? connection
                : withTenant(connection, bound);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        // The inherited method would hand out a connection without the tenant setting.
        throw new SQLFeatureNotSupportedException("a tenant connection takes its user from the Registry");
    }

    /** Sets {@code app.tenant_id} for the session now and resets it when the connection is closed. */
    private static Connection withTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement set = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            set.setString(1, tenantId.toString());
            set.execute();
        } catch (SQLException | RuntimeException failure) {
            connection.close();
            throw failure;
        }
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "close":
                    resetAndClose(connection);
                    return null;
                default:
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
            }
        };
        return (Connection)
                Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private static void resetAndClose(Connection connection) throws SQLException {
        try (connection) {
            if (connection.isClosed()) {
                return;
            }
            try {
                // A RESET inside an uncommitted transaction would be undone by the pool's rollback.
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
                try (Statement reset = connection.createStatement()) {
                    reset.execute("RESET app.tenant_id");
                }
            } catch (SQLException failure) {
                // A connection that may still carry the value must not be reused.
                connection.abort(Runnable::run);
                throw failure;
            }
        }
    }
}
