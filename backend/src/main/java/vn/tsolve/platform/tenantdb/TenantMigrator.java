package vn.tsolve.platform.tenantdb;

import java.util.regex.Pattern;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.tsolve.platform.registry.PasswordCipher;
import vn.tsolve.platform.registry.RegistryRepository;
import vn.tsolve.platform.registry.RegistryRepository.WorkspaceDatabase;

/**
 * Applies the tenant changelog to every Workspace database in the Registry, as that database's user (AC-008.4). A bean
 * through {@link TenantMigratorConfig}.
 */
public class TenantMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(TenantMigrator.class);
    // The name goes into the JDBC URL, where '?', '/' or '%' would change what is connected to, and how.
    private static final Pattern DATABASE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    /** The only Liquibase code: applies the tenant changelog over one connection; returns the last changeset id. */
    interface ChangelogRunner {
        String apply(String url, String user, String password);
    }

    private final RegistryRepository registry;
    private final PasswordCipher cipher;
    private final String serverUrl;
    private final ChangelogRunner runner;

    public TenantMigrator(RegistryRepository registry, PasswordCipher cipher, String host, int port, String changeLog) {
        this(registry, cipher, host, port, (url, user, password) -> update(changeLog, url, user, password));
    }

    TenantMigrator(RegistryRepository registry, PasswordCipher cipher, String host, int port, ChangelogRunner runner) {
        // This runs before the routing DataSource and its own check of the host exist (AC-008.5).
        if (host.isBlank()) {
            throw new IllegalStateException("TENANT_DB_HOST must be set where tenant routing is on");
        }
        this.registry = registry;
        this.cipher = cipher;
        this.serverUrl = "jdbc:postgresql://" + host + ":" + port + "/";
        this.runner = runner;
    }

    /** Every row in {@code site_name} order. The first failure stops the run and names its Workspace (AC-008.9). */
    public void applyAll() {
        for (WorkspaceDatabase workspace : registry.findAllConnections()) {
            try {
                migrate(workspace);
            } catch (RuntimeException failure) {
                throw new IllegalStateException(
                        "Migration of the database of Workspace " + workspace.siteName() + " failed", failure);
            }
        }
    }

    private void migrate(WorkspaceDatabase workspace) {
        if (!DATABASE_NAME.matcher(workspace.dbName()).matches()) {
            throw new IllegalStateException("db_name is not a plain database name");
        }
        String version = runner.apply(
                serverUrl + workspace.dbName(),
                workspace.dbUser(),
                cipher.decrypt(workspace.workspaceId(), workspace.dbPassword()));
        registry.updateSchemaVersion(workspace.workspaceId(), version);
        LOG.info("The database of Workspace {} is at changeset {}", workspace.siteName(), version);
    }

    private static String update(String changeLog, String url, String user, String password) {
        var database = new DriverManagerDataSource(url, user, password);
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(database);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.setChangeLog(changeLog);
        try {
            liquibase.afterPropertiesSet();
        } catch (LiquibaseException failure) {
            throw new IllegalStateException("the tenant changelog failed", failure);
        }
        // The latest executed changeset, as infra/scripts/registry-adopt-workspace.sh reads it.
        return JdbcClient.create(database)
                .sql("SELECT id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1")
                .query(String.class)
                .single();
    }
}
