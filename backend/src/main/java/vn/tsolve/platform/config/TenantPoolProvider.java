package vn.tsolve.platform.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongFunction;
import vn.tsolve.common.tenant.TenantAccessException;
import vn.tsolve.platform.repository.TenantConnection;

public final class TenantPoolProvider implements LongFunction<HikariDataSource>, AutoCloseable {
    private final LongFunction<Optional<TenantConnection>> connections;
    private final Function<String, String> secrets;
    private final BiFunction<TenantConnection, String, HikariDataSource> factory;
    private final int maxPools;
    private final Map<Long, HikariDataSource> pools = new HashMap<>();
    private boolean closed;

    public TenantPoolProvider(
            LongFunction<Optional<TenantConnection>> connections,
            Function<String, String> secrets,
            BiFunction<TenantConnection, String, HikariDataSource> factory,
            int maxPools) {
        if (maxPools < 1) {
            throw new IllegalArgumentException("Tenant pool limit must be positive");
        }
        this.connections = connections;
        this.secrets = secrets;
        this.factory = factory;
        this.maxPools = maxPools;
    }

    @Override
    public synchronized HikariDataSource apply(long id) {
        if (closed || id <= 0) {
            throw new TenantAccessException();
        }
        HikariDataSource existing = pools.get(id);
        if (existing != null) {
            return existing;
        }
        if (pools.size() >= maxPools) {
            throw new TenantAccessException();
        }
        try {
            TenantConnection connection = connections.apply(id).orElseThrow(TenantAccessException::new);
            if (connection.workspaceId() != id
                    || blank(connection.host())
                    || blank(connection.database())
                    || blank(connection.username())
                    || blank(connection.passwordReference())
                    || connection.port() < 1
                    || connection.port() > 65535) {
                throw new TenantAccessException();
            }
            String password = secrets.apply(connection.passwordReference());
            if (password == null || password.isBlank()) {
                throw new TenantAccessException();
            }
            HikariDataSource pool = factory.apply(connection, password);
            pools.put(id, pool);
            return pool;
        } catch (RuntimeException failure) {
            throw new TenantAccessException();
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            pools.values().forEach(HikariDataSource::close);
            pools.clear();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
