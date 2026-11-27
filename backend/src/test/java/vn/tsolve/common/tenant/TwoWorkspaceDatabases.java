package vn.tsolve.common.tenant;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.mock.env.MockEnvironment;
import vn.tsolve.platform.registry.PasswordCipher;
import vn.tsolve.support.PostgresTestBase;

/**
 * Two Workspace databases (A and B) set up like provisioning does ({@link TenantGuardDatabase}), each with the probe
 * rows {@code 'probe'} and {@code 'A'} or {@code 'B'}, and a Registry database holding their two rows with encrypted
 * passwords. A third, "unrouted" database holds an empty probe table: it is what {@code DB_*} points at, so a query
 * that bypasses the router can be told apart. Every instance has its own databases and roles.
 */
final class TwoWorkspaceDatabases {

    private static final String REGISTRY_CHANGELOG = "db/changelog/catalog/catalog-master.xml";

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final String key = randomKey();
    private final String otherKey = randomKey();
    private final Map<UUID, TenantGuardDatabase> databases = new HashMap<>();
    private final Map<UUID, String> siteNames = new HashMap<>();
    private final Map<UUID, String> storedPasswords = new HashMap<>();
    private String registryUrl;
    private String unroutedUrl;

    private TwoWorkspaceDatabases() {}

    static TwoWorkspaceDatabases create(String prefix) throws Exception {
        TwoWorkspaceDatabases created = new TwoWorkspaceDatabases();
        created.workspaceDatabase(prefix + "a", created.a, "A");
        created.workspaceDatabase(prefix + "b", created.b, "B");
        created.registry(prefix);
        created.unrouted(prefix);
        return created;
    }

    UUID a() {
        return a;
    }

    UUID b() {
        return b;
    }

    String siteName(UUID id) {
        return siteNames.get(id);
    }

    TenantGuardDatabase database(UUID id) {
        return databases.get(id);
    }

    /** Every value that must never appear in a log line or an exception. */
    String[] secrets() {
        return new String[] {
            TenantGuardDatabase.RUNTIME_PASSWORD,
            PostgresTestBase.REGISTRY_PASSWORD,
            storedPasswords.get(a),
            storedPasswords.get(b),
            key,
            otherKey
        };
    }

    /** What the web or worker process gets from its environment where routing is on. */
    Map<String, String> routingVariables() {
        var server = PostgresTestBase.instance();
        String host = server.getHost();
        String port = String.valueOf(server.getMappedPort(5432));
        Map<String, String> variables = new HashMap<>();
        variables.put("REGISTRY_DB_URL", registryUrl);
        variables.put("REGISTRY_DB_USER", PostgresTestBase.REGISTRY_USER);
        variables.put("REGISTRY_DB_PASSWORD", PostgresTestBase.REGISTRY_PASSWORD);
        variables.put("CATALOG_SECRET_KEY", key);
        variables.put("TENANT_DB_HOST", host);
        variables.put("TENANT_DB_PORT", port);
        variables.put("DB_HOST", host);
        variables.put("DB_PORT", port);
        variables.put("DB_NAME", databaseName(unroutedUrl));
        variables.put("DB_USER", PostgresTestBase.REGISTRY_USER);
        variables.put("DB_PASSWORD", PostgresTestBase.REGISTRY_PASSWORD);
        return variables;
    }

    /** The stored password of one Workspace is encrypted again with a key the backend does not have. */
    void reencryptWithOtherKey(UUID id) throws SQLException {
        String stored = cipher(otherKey).encrypt(id, TenantGuardDatabase.RUNTIME_PASSWORD);
        storedPasswords.put(id, stored);
        try (Connection registry = registryConnection();
                PreparedStatement update = registry.prepareStatement(
                        "UPDATE tenant_workspace SET db_password = ? WHERE workspace_id = ?")) {
            update.setString(1, stored);
            update.setObject(2, id);
            update.executeUpdate();
        }
    }

    void allowConnections(UUID id, boolean allowed) throws SQLException {
        allow(databases.get(id).database, allowed);
    }

    void allowRegistryConnections(boolean allowed) throws SQLException {
        allow(databaseName(registryUrl), allowed);
    }

    /** Server backends of the Workspace's runtime role. */
    int backends(UUID id) throws SQLException {
        try (Connection superuser = PostgresTestBase.superuserConnection("postgres");
                PreparedStatement select =
                        superuser.prepareStatement("SELECT count(*) FROM pg_stat_activity WHERE usename = ?")) {
            select.setString(1, databases.get(id).runtimeRole);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    /** Rows of the probe table, read as the owner (no row policy applies). */
    int probeRows(UUID id) throws SQLException {
        try (Connection owner = databases.get(id).owner();
                Statement statement = owner.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM isolation_probes")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private void workspaceDatabase(String prefix, UUID id, String note) throws Exception {
        TenantGuardDatabase database = TenantGuardDatabase.ready(prefix, id);
        try (Connection owner = database.owner();
                PreparedStatement insert = owner.prepareStatement("INSERT INTO isolation_probes (note) VALUES (?)")) {
            insert.setString(1, note);
            insert.executeUpdate();
        }
        databases.put(id, database);
        siteNames.put(id, "site-" + note.toLowerCase() + "-" + id.toString().substring(0, 8));
    }

    private void registry(String prefix) throws Exception {
        registryUrl = PostgresTestBase.emptyDatabase(prefix + "reg");
        try (Connection connection = registryConnection()) {
            Database db =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new Liquibase(REGISTRY_CHANGELOG, new ClassLoaderResourceAccessor(), db).update("");
        }
        try (Connection connection = registryConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO tenant_workspace (workspace_id, site_name, status, db_name, db_user, "
                                + "db_password, schema_version, google_domain, created_at, updated_at) "
                                + "VALUES (?, ?, 'ACTIVE', ?, ?, ?, '1', 'x.test', now(), now())")) {
            for (UUID id : new UUID[] {a, b}) {
                String stored = cipher(key).encrypt(id, TenantGuardDatabase.RUNTIME_PASSWORD);
                storedPasswords.put(id, stored);
                insert.setObject(1, id);
                insert.setString(2, siteNames.get(id));
                insert.setString(3, databases.get(id).database);
                insert.setString(4, databases.get(id).runtimeRole);
                insert.setString(5, stored);
                insert.executeUpdate();
            }
        }
    }

    private void unrouted(String prefix) throws SQLException {
        unroutedUrl = PostgresTestBase.emptyDatabase(prefix + "unrouted");
        try (Connection connection = DriverManager.getConnection(
                        unroutedUrl, PostgresTestBase.REGISTRY_USER, PostgresTestBase.REGISTRY_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE isolation_probes (isolation_probes_id UUID PRIMARY KEY DEFAULT "
                    + "gen_random_uuid(), note TEXT NOT NULL)");
        }
    }

    private Connection registryConnection() throws SQLException {
        return DriverManager.getConnection(
                registryUrl, PostgresTestBase.REGISTRY_USER, PostgresTestBase.REGISTRY_PASSWORD);
    }

    private static void allow(String database, boolean allowed) throws SQLException {
        try (Connection superuser = PostgresTestBase.superuserConnection("postgres");
                Statement statement = superuser.createStatement()) {
            statement.execute("ALTER DATABASE \"" + database + "\" ALLOW_CONNECTIONS " + allowed);
            if (!allowed) {
                statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + database
                        + "' AND pid <> pg_backend_pid()");
            }
        }
    }

    private static String databaseName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static PasswordCipher cipher(String encodedKey) {
        return new PasswordCipher(new MockEnvironment().withProperty("tsolve.registry.secret-key", encodedKey));
    }

    private static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
