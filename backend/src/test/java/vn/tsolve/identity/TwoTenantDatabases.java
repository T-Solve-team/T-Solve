package vn.tsolve.identity;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.TenantScope;

/**
 * Test-only: one PostgreSQL server with the application's own (unbound) database plus two company databases,
 * {@code tenant_a} for ACME and {@code tenant_b} for BETA, each migrated with the application changelog. The primary
 * {@link DataSource} routes by the Workspace the test {@link TenantScope} has bound, so an IT shows which database a
 * query really reached.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TwoTenantDatabases {

    public static final UUID ACME = InMemoryWorkspaceLookup.ACME.id();
    public static final UUID BETA = InMemoryWorkspaceLookup.BETA.id();

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    // started once for the JVM and stopped by Testcontainers' Ryuk
    private static final PostgreSQLContainer<?> SERVER = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private static final HikariDataSource MAIN;
    private static final HikariDataSource TENANT_A;
    private static final HikariDataSource TENANT_B;

    static {
        SERVER.start();
        createDatabase("tenant_a");
        createDatabase("tenant_b");
        MAIN = pool(SERVER.getDatabaseName());
        TENANT_A = pool("tenant_a");
        TENANT_B = pool("tenant_b");
        migrate(TENANT_A);
        migrate(TENANT_B);
    }

    /** The application's own database (the one no Workspace is bound to); Spring's Liquibase migrates it. */
    public static JdbcTemplate main() {
        return new JdbcTemplate(MAIN);
    }

    public static JdbcTemplate tenantA() {
        return new JdbcTemplate(TENANT_A);
    }

    public static JdbcTemplate tenantB() {
        return new JdbcTemplate(TENANT_B);
    }

    private static String url(String database) {
        return "jdbc:postgresql://" + SERVER.getHost() + ":" + SERVER.getMappedPort(5432) + "/" + database;
    }

    private static void createDatabase(String name) {
        try (Connection connection = DriverManager.getConnection(
                        url(SERVER.getDatabaseName()), SERVER.getUsername(), SERVER.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HikariDataSource pool(String database) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url(database));
        config.setUsername(SERVER.getUsername());
        config.setPassword(SERVER.getPassword());
        config.setMaximumPoolSize(4);
        return new HikariDataSource(config);
    }

    private static void migrate(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database).update("");
        } catch (Exception e) {
            throw new IllegalStateException("could not migrate a tenant database", e);
        }
    }

    /** A per-thread binding that {@link RoutingDataSource} reads; unbound means the application's own database. */
    public static final class RoutingTenantScope implements TenantScope {

        private final ThreadLocal<UUID> bound = new ThreadLocal<>();

        @Override
        public UUID currentWorkspaceId() {
            UUID id = bound.get();
            if (id == null) {
                throw new IllegalStateException("no Workspace is bound to this thread");
            }
            return id;
        }

        @Override
        public <T> T runIn(UUID workspaceId, Supplier<T> work) {
            UUID previous = bound.get();
            bound.set(workspaceId);
            try {
                return work.get();
            } finally {
                if (previous == null) {
                    bound.remove();
                } else {
                    bound.set(previous);
                }
            }
        }

        UUID peek() {
            return bound.get();
        }
    }

    /** ACME goes to tenant_a, BETA to tenant_b, no binding to the main database; any other id is an error. */
    static final class RoutingDataSource extends AbstractRoutingDataSource {

        private final RoutingTenantScope scope;

        RoutingDataSource(RoutingTenantScope scope) {
            this.scope = scope;
            setTargetDataSources(Map.of(ACME, TENANT_A, BETA, TENANT_B));
            setDefaultTargetDataSource(MAIN);
            setLenientFallback(false);
        }

        @Override
        protected Object determineCurrentLookupKey() {
            return scope.peek();
        }
    }

    @Bean
    @Primary
    RoutingTenantScope routingTenantScope() {
        return new RoutingTenantScope();
    }

    @Bean
    @Primary
    DataSource routingDataSource(RoutingTenantScope scope) {
        return new RoutingDataSource(scope);
    }
}
