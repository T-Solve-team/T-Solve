package vn.tsolve.platform.registry;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.tsolve.common.tenant.TenantDataSources;

/**
 * One pool per Workspace database, opened on first use from the Workspace's Registry row. An idle pool keeps no
 * connection (AC-002.22).
 */
public class TenantPoolRegistry implements TenantDataSources, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TenantPoolRegistry.class);
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(10);

    private final Map<UUID, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final RegistryRepository registry;
    private final PasswordCipher cipher;
    private final String serverUrl;
    private final int poolMax;

    TenantPoolRegistry(RegistryRepository registry, PasswordCipher cipher, String host, int port, int poolMax) {
        this.registry = registry;
        this.cipher = cipher;
        this.serverUrl = "jdbc:postgresql://" + host + ":" + port + "/";
        this.poolMax = poolMax;
    }

    @Override
    public DataSource poolOf(UUID workspaceId) {
        // shortcut: the map holds its lock while a pool connects, so two first uses share one pool; move to a future
        // per Workspace if one slow database ever delays the first use of others.
        // A failure throws before anything is stored, so the next use tries again.
        return pools.computeIfAbsent(workspaceId, this::open);
    }

    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }

    private HikariDataSource open(UUID workspaceId) {
        var row = registry.findConnection(workspaceId)
                .orElseThrow(() -> new IllegalStateException("the Workspace has no Registry row"));
        try {
            var config = new HikariConfig();
            config.setPoolName("tenant-" + row.siteName());
            config.setJdbcUrl(serverUrl + row.dbName());
            config.setUsername(row.dbUser());
            config.setPassword(cipher.decrypt(workspaceId, row.dbPassword()));
            config.setMaximumPoolSize(poolMax);
            config.setMinimumIdle(0);
            config.setIdleTimeout(IDLE_TIMEOUT.toMillis());
            return new HikariDataSource(config);
        } catch (RuntimeException failure) {
            LOG.error(
                    "Could not open the database pool of Workspace {} ({})",
                    row.siteName(),
                    failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
