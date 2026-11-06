package vn.tsolve.common.tenant;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Isolated test databases only; no application or developer database connection is used. */
final class TenantRowGuardDatabase {
    private static final PostgreSQLContainer<?> SERVER = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    final UUID workspaceId = UUID.randomUUID();
    final String databaseName;
    final String ownerName;
    final String runtimeName;
    final JdbcTemplate owner;
    final JdbcTemplate admin;
    private final String password = UUID.randomUUID().toString();

    private TenantRowGuardDatabase() throws Exception {
        if (!SERVER.isRunning()) SERVER.start();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        databaseName = "guard_" + suffix;
        ownerName = "owner_" + suffix;
        runtimeName = "runtime_" + suffix;
        JdbcTemplate serverAdmin = jdbc(SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword());
        serverAdmin.execute("CREATE ROLE " + ownerName + " LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '" + password + "'");
        serverAdmin.execute(
                "CREATE ROLE " + runtimeName + " LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '" + password + "'");
        serverAdmin.execute("CREATE DATABASE " + databaseName + " OWNER " + ownerName);
        admin = jdbc(url(), SERVER.getUsername(), SERVER.getPassword());
        admin.execute("CREATE EXTENSION IF NOT EXISTS vector");
        admin.execute("REVOKE ALL ON DATABASE " + databaseName + " FROM PUBLIC");
        admin.execute("GRANT CONNECT ON DATABASE " + databaseName + " TO " + runtimeName);
        owner = jdbc(url(), ownerName, password);
        owner.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
        owner.execute("GRANT USAGE ON SCHEMA public TO " + runtimeName);
        owner.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "
                + runtimeName);
        owner.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO " + runtimeName);
        migrate();
        owner.execute(
                "CREATE TABLE isolation_probes (probe_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), payload TEXT NOT NULL)");
        migrate();
        secureIdentity();
    }

    static TenantRowGuardDatabase create() throws Exception {
        return new TenantRowGuardDatabase();
    }

    void migrate() throws Exception {
        try (Connection connection = DriverManager.getConnection(url(), ownerName, password);
                var resources = new ClassLoaderResourceAccessor()) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase(CHANGELOG, resources, database)) {
                liquibase.update("");
            }
        }
    }

    void secureIdentity() {
        owner.execute("REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON tenant_identity FROM " + runtimeName);
    }

    void seed() {
        owner.update("DELETE FROM tenant_identity");
        owner.update("INSERT INTO tenant_identity (tenant_id) VALUES (?)", workspaceId);
        owner.update("DELETE FROM isolation_probes");
        owner.update("INSERT INTO isolation_probes (payload) VALUES (?)", databaseName);
    }

    Connection runtime(String workspace) throws SQLException {
        Connection connection = DriverManager.getConnection(url(), runtimeName, password);
        connection.setAutoCommit(false);
        if (workspace != null) {
            try (var statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                statement.setString(1, workspace);
                statement.execute();
            } catch (SQLException failure) {
                connection.close();
                throw failure;
            }
        }
        return connection;
    }

    private String url() {
        return "jdbc:postgresql://" + SERVER.getHost() + ":" + SERVER.getMappedPort(5432) + "/" + databaseName;
    }

    private static JdbcTemplate jdbc(String url, String username, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(url, username, password));
    }
}
