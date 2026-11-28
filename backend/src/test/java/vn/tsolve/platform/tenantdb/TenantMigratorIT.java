package vn.tsolve.platform.tenantdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import liquibase.Liquibase;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import vn.tsolve.platform.registry.PasswordCipher;
import vn.tsolve.platform.registry.RegistryRepository;
import vn.tsolve.support.PostgresTestBase;

/**
 * The real {@link TenantMigrator} on real PostgreSQL: its own Registry database and Workspace databases, each owned by a
 * non-superuser that is the row's {@code db_user}, on the shared server of {@link PostgresTestBase}.
 */
@ExtendWith(OutputCaptureExtension.class)
class TenantMigratorIT {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String MASTER = "db/changelog/db.changelog-master.xml";
    private static final String BOOT_MASTER = "classpath:" + MASTER;
    private static final String STALE = "stale-version";
    private static final String LAST_ID = "SELECT id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final PasswordCipher cipher =
            new PasswordCipher(new MockEnvironment().withProperty("tsolve.registry.secret-key", KEY));

    @Test
    @DisplayName("AC-008.4 AC-001.15 empty ACTIVE and SUSPENDED databases get the whole tenant changelog as their "
            + "owner, and schema_version holds the last changeset id")
    void migratesEmptyDatabasesAsTheirOwner_AC_008_4_AC_001_15() throws Exception {
        String registry = registry();
        Workspace active = workspace("a");
        Workspace suspended = workspace("b");
        insertRow(registry, active, "ACTIVE", active.password());
        insertRow(registry, suspended, "SUSPENDED", suspended.password());

        migrator(registry).applyAll();

        for (Workspace workspace : List.of(active, suspended)) {
            assertThat(scalar(workspace, "SELECT count(*) FROM databasechangelog"))
                    .isEqualTo(String.valueOf(masterChangeSets().size()));
            assertThat(column(workspace, "SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname = 'public'"))
                    .containsExactly(workspace.owner());
            assertThat(schemaVersion(registry, workspace)).isEqualTo(scalar(workspace, LAST_ID));
        }
    }

    @Test
    @DisplayName("AC-008.4 a database already migrated by Boot gets no changeset and its schema_version is corrected")
    void leavesAnAlreadyMigratedDatabaseUnchanged_AC_008_4() throws Exception {
        String registry = registry();
        Workspace migrated = workspace("a");
        migrateAsBootDid(migrated);
        insertRow(registry, migrated, "ACTIVE", migrated.password());
        List<String> before = changelogRows(migrated);
        assertThat(before).hasSize(masterChangeSets().size());

        migrator(registry).applyAll();

        assertThat(changelogRows(migrated)).containsExactlyInAnyOrderElementsOf(before);
        assertThat(schemaVersion(registry, migrated)).isNotEqualTo(STALE).isEqualTo(scalar(migrated, LAST_ID));
    }

    @Test
    @DisplayName("AC-008.9 edge 44 one unreachable database fails the run naming its site, leaves the others as they "
            + "are and leaks no secret")
    void realFailureNamesTheSiteLeavesOthersAndLeaksNoPassword_AC_008_9_edge_44(CapturedOutput output)
            throws Exception {
        String registry = registry();
        Workspace first = workspace("a");
        Workspace broken = workspace("b");
        Workspace third = workspace("c");
        String wrongPassword = "fake-wrong-password-" + UUID.randomUUID();
        // Inserted out of order: the run goes by site_name, not by insertion.
        List<String> stored = List.of(
                insertRow(registry, third, "ACTIVE", third.password()),
                insertRow(registry, broken, "ACTIVE", wrongPassword),
                insertRow(registry, first, "ACTIVE", first.password()));
        TenantMigrator migrator = migrator(registry);
        int outputBefore = output.getAll().length();

        Throwable failure = catchThrowable(migrator::applyAll);

        assertThat(failure).isInstanceOf(RuntimeException.class).hasMessageContaining(broken.site());
        assertThat(scalar(first, "SELECT count(*) FROM databasechangelog"))
                .isEqualTo(String.valueOf(masterChangeSets().size()));
        assertThat(schemaVersion(registry, first)).isEqualTo(scalar(first, LAST_ID));
        assertThat(scalar(third, "SELECT to_regclass('public.databasechangelog')"))
                .isNull();
        assertThat(schemaVersion(registry, broken)).isEqualTo(STALE);
        assertThat(schemaVersion(registry, third)).isEqualTo(STALE);

        var trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        List<String> secrets = new ArrayList<>(stored);
        secrets.addAll(List.of(
                first.password(),
                broken.password(),
                third.password(),
                wrongPassword,
                KEY,
                PostgresTestBase.REGISTRY_PASSWORD));
        for (String secret : secrets) {
            assertThat(trace.toString()).doesNotContain(secret);
            assertThat(output.getAll().substring(outputBefore)).doesNotContain(secret);
        }
    }

    // ---- fixture ----

    private record Workspace(UUID id, String site, String database, String owner, String password) {}

    /** An empty database owned by its own non-superuser role; {@code vector} is created first, as provisioning does. */
    private static Workspace workspace(String name) throws SQLException {
        String suffix = name + "_" + COUNTER.incrementAndGet();
        var workspace = new Workspace(
                UUID.randomUUID(),
                "mig-" + name + "-" + COUNTER.get(),
                "mig_db_" + suffix,
                "mig_owner_" + suffix,
                "fake-owner-password-" + UUID.randomUUID());
        try (Connection superuser = PostgresTestBase.superuserConnection("postgres");
                Statement statement = superuser.createStatement()) {
            statement.execute("CREATE ROLE \"" + workspace.owner()
                    + "\" LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '" + workspace.password()
                    + "'");
            statement.execute("CREATE DATABASE \"" + workspace.database() + "\" OWNER \"" + workspace.owner() + "\"");
        }
        try (Connection superuser = PostgresTestBase.superuserConnection(workspace.database());
                Statement statement = superuser.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
        }
        return workspace;
    }

    /** A new Registry database with the catalog changelog, applied by its owner. Returns its JDBC URL. */
    private static String registry() throws Exception {
        String url = PostgresTestBase.emptyDatabase("migrator");
        try (Connection connection = registryConnection(url)) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new Liquibase("db/changelog/catalog/catalog-master.xml", new ClassLoaderResourceAccessor(), database)
                    .update("");
        }
        return url;
    }

    /** Inserts the row with {@code password} encrypted under the test key; returns the stored value. */
    private String insertRow(String registry, Workspace workspace, String status, String password) throws SQLException {
        String stored = cipher.encrypt(workspace.id(), password);
        try (Connection connection = registryConnection(registry);
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO tenant_workspace (workspace_id, site_name, status, db_name, db_user, "
                                + "db_password, schema_version, google_domain, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'x.test', now(), now())")) {
            insert.setObject(1, workspace.id());
            insert.setString(2, workspace.site());
            insert.setString(3, status);
            insert.setString(4, workspace.database());
            insert.setString(5, workspace.owner());
            insert.setString(6, stored);
            insert.setString(7, STALE);
            insert.executeUpdate();
        }
        return stored;
    }

    private TenantMigrator migrator(String registry) {
        var dataSource = new DriverManagerDataSource(
                registry, PostgresTestBase.REGISTRY_USER, PostgresTestBase.REGISTRY_PASSWORD);
        return new TenantMigrator(
                new RegistryRepository(JdbcClient.create(dataSource)),
                cipher,
                PostgresTestBase.instance().getHost(),
                PostgresTestBase.instance().getMappedPort(5432),
                BOOT_MASTER);
    }

    private static void migrateAsBootDid(Workspace workspace) throws Exception {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(new DriverManagerDataSource(
                PostgresTestBase.url(workspace.database()), workspace.owner(), workspace.password()));
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.setChangeLog(BOOT_MASTER);
        liquibase.afterPropertiesSet();
    }

    private static List<ChangeSet> masterChangeSets() throws Exception {
        try (var resources = new ClassLoaderResourceAccessor()) {
            return ChangeLogParserFactory.getInstance()
                    .getParser(MASTER, resources)
                    .parse(MASTER, new ChangeLogParameters(), resources)
                    .getChangeSets();
        }
    }

    /**
     * Every applied changeset with its checksum and position. Liquibase moves a {@code runAlways} changeset to the end
     * on every run, so its position is left out.
     */
    private static List<String> changelogRows(Workspace workspace) throws Exception {
        List<String> runAlways = masterChangeSets().stream()
                .filter(ChangeSet::isAlwaysRun)
                .map(ChangeSet::getId)
                .toList();
        String excluded = "'" + String.join("', '", runAlways) + "'";
        return column(
                workspace,
                "SELECT concat_ws('|', id, author, filename, md5sum, CASE WHEN id IN (" + excluded
                        + ") THEN 'runAlways' ELSE orderexecuted::text END) FROM databasechangelog");
    }

    private static String schemaVersion(String registry, Workspace workspace) throws SQLException {
        try (Connection connection = registryConnection(registry);
                PreparedStatement select = connection.prepareStatement(
                        "SELECT schema_version FROM tenant_workspace WHERE workspace_id = ?")) {
            select.setObject(1, workspace.id());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static Connection registryConnection(String url) throws SQLException {
        return DriverManager.getConnection(url, PostgresTestBase.REGISTRY_USER, PostgresTestBase.REGISTRY_PASSWORD);
    }

    private static String scalar(Workspace workspace, String sql) throws SQLException {
        List<String> values = column(workspace, sql);
        return values.isEmpty() ? null : values.get(0);
    }

    private static List<String> column(Workspace workspace, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = PostgresTestBase.superuserConnection(workspace.database());
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
