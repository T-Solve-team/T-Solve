package vn.tsolve.common.tenant;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import vn.tsolve.support.PostgresTestBase;

/**
 * Test-only fixture of the row guard (KAN-253, Task 5): one tenant database set up the way provisioning will do it
 * (decision R6), on the shared server of {@link PostgresTestBase}.
 *
 * <ul>
 *   <li>the database is owned by the app user, who also runs Liquibase (the owner, AC-001.27);
 *   <li>a runtime role with {@code LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE} may connect to it only, and
 *       gets SELECT, INSERT, UPDATE and DELETE on every table the owner creates, never TRUNCATE;
 *   <li>{@link #writeIdentity} inserts the {@code tenant_identity} row as the owner and then revokes the runtime
 *       role's INSERT, UPDATE, DELETE and TRUNCATE on it.
 * </ul>
 */
final class TenantGuardDatabase {

    static final String MASTER = "db/changelog/db.changelog-master.xml";
    static final String RUNTIME_PASSWORD = "fake-runtime-password";

    private static final AtomicInteger COUNTER = new AtomicInteger();

    final String database;
    final String runtimeRole;

    private TenantGuardDatabase(String database, String runtimeRole) {
        this.database = database;
        this.runtimeRole = runtimeRole;
    }

    /** A database, its runtime role and the default privileges; no changelog applied yet. */
    static TenantGuardDatabase create(String prefix) throws SQLException {
        int n = COUNTER.incrementAndGet();
        String suffix = prefix + "_" + n + "_" + Long.toString(System.nanoTime() % 100000, 36);
        String database = "guard_db_" + suffix;
        String role = "guard_rt_" + suffix;
        try (Connection superuser = PostgresTestBase.superuserConnection("postgres");
                Statement statement = superuser.createStatement()) {
            statement.execute("CREATE ROLE \"" + role + "\" LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE "
                    + "PASSWORD '" + RUNTIME_PASSWORD + "'");
            statement.execute("CREATE DATABASE \"" + database + "\" OWNER \"" + PostgresTestBase.APP_USER + "\"");
            statement.execute("REVOKE ALL ON DATABASE \"" + database + "\" FROM PUBLIC");
            statement.execute("GRANT CONNECT ON DATABASE \"" + database + "\" TO \"" + role + "\"");
        }
        // pgvector needs the superuser; the changelog's CREATE EXTENSION IF NOT EXISTS then does nothing
        try (Connection superuser = PostgresTestBase.superuserConnection(database);
                Statement statement = superuser.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
        }
        TenantGuardDatabase created = new TenantGuardDatabase(database, role);
        try (Connection owner = created.owner();
                Statement statement = owner.createStatement()) {
            statement.execute("GRANT USAGE ON SCHEMA public TO \"" + role + "\"");
            statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE \"" + PostgresTestBase.APP_USER
                    + "\" IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO \"" + role + "\"");
            statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE \"" + PostgresTestBase.APP_USER
                    + "\" IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO \"" + role + "\"");
        }
        return created;
    }

    /** Applies a changelog from the classpath as the owner. May be called again: runAlways changesets run again. */
    TenantGuardDatabase migrate(String changelog) throws Exception {
        try (Connection owner = owner()) {
            Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(owner));
            new Liquibase(changelog, new ClassLoaderResourceAccessor(), db).update("");
        }
        return this;
    }

    /** As provisioning does after the changelog: the identity row as the owner, then the runtime role loses writes. */
    TenantGuardDatabase writeIdentity(UUID tenantId) throws SQLException {
        try (Connection owner = owner()) {
            try (PreparedStatement insert =
                    owner.prepareStatement("INSERT INTO tenant_identity (tenant_id) VALUES (?)")) {
                insert.setObject(1, tenantId);
                insert.executeUpdate();
            }
            try (Statement statement = owner.createStatement()) {
                statement.execute(
                        "REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON tenant_identity FROM \"" + runtimeRole + "\"");
            }
        }
        return this;
    }

    /**
     * The test-only table {@code isolation_probes} with one row (note = 'probe'), then the changelog again so that the
     * runAlways installer puts the policy on the new table.
     */
    TenantGuardDatabase addProbeTable() throws Exception {
        try (Connection owner = owner();
                Statement statement = owner.createStatement()) {
            statement.execute("CREATE TABLE isolation_probes (isolation_probes_id UUID PRIMARY KEY DEFAULT "
                    + "gen_random_uuid(), note TEXT NOT NULL)");
            statement.execute("INSERT INTO isolation_probes (note) VALUES ('probe')");
        }
        return migrate(MASTER);
    }

    /** The whole fixture: changelog, identity row (when given), probe table with one row. */
    static TenantGuardDatabase ready(String prefix, UUID tenantId) throws Exception {
        TenantGuardDatabase created = create(prefix).migrate(MASTER);
        if (tenantId != null) {
            created.writeIdentity(tenantId);
        }
        return created.addProbeTable();
    }

    Connection owner() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestBase.url(database), PostgresTestBase.APP_USER, PostgresTestBase.APP_PASSWORD);
    }

    Connection runtime() throws SQLException {
        return DriverManager.getConnection(PostgresTestBase.url(database), runtimeRole, RUNTIME_PASSWORD);
    }

    Connection superuser() throws SQLException {
        return PostgresTestBase.superuserConnection(database);
    }
}
