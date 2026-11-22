package vn.tsolve.platform.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import vn.tsolve.support.PostgresTestBase;

/**
 * The Registry (catalog) changelog, started the way the web process starts it: {@link RegistryDataSourceConfig} on an
 * {@link ApplicationContextRunner}, configured only through REGISTRY_DB_URL, REGISTRY_DB_USER, REGISTRY_DB_PASSWORD and
 * CATALOG_SECRET_KEY, with the main application.yml mapping them. The real init script creates the databases.
 */
class RegistryChangelogIT {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final Set<String> WORKSPACE_COLUMNS = Set.of(
            "workspace_id",
            "site_name",
            "status",
            "db_name",
            "db_user",
            "db_password",
            "schema_version",
            "jira_site_url",
            "jira_cloud_id",
            "google_domain",
            "keycloak_organization_id",
            "created_at",
            "updated_at");

    @Test
    @DisplayName("AC-001.15 workspace table: columns, types, nullability, keys, unique, status check")
    void createsWorkspaceTable_AC_001_15() throws Exception {
        String url = PostgresTestBase.emptyDatabase("ac15");
        webRunner(url).run(context -> assertThat(context).hasNotFailed());

        try (Connection connection = registryConnection(url)) {
            Map<String, String[]> columns = new LinkedHashMap<>();
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT column_name, data_type, is_nullable FROM "
                            + "information_schema.columns WHERE table_schema = 'public' "
                            + "AND table_name = 'tenant_workspace'")) {
                while (rows.next()) {
                    columns.put(rows.getString(1), new String[] {rows.getString(2), rows.getString(3)});
                }
            }
            assertThat(columns.keySet()).containsExactlyInAnyOrderElementsOf(WORKSPACE_COLUMNS);
            assertThat(columns.get("workspace_id")).containsExactly("uuid", "NO");
            assertThat(columns.get("created_at")).containsExactly("timestamp with time zone", "NO");
            assertThat(columns.get("updated_at")).containsExactly("timestamp with time zone", "NO");
            for (String required :
                    List.of("site_name", "status", "db_name", "db_user", "db_password", "schema_version")) {
                assertThat(columns.get(required)[1]).as(required).isEqualTo("NO");
            }
            assertThat(columns.get("google_domain")[1]).isEqualTo("NO");
            for (String nullable : List.of("jira_site_url", "jira_cloud_id", "keycloak_organization_id")) {
                assertThat(columns.get(nullable)[1]).as(nullable).isEqualTo("YES");
            }
            assertThat(primaryKeyColumns(connection, "tenant_workspace")).containsExactly("workspace_id");

            connection.setAutoCommit(false);
            try {
                insertWorkspace(connection, "site-a", "db_a", "cloud-a", "org-a", "ACTIVE");
                insertWorkspace(connection, "site-s", "db_s", null, null, "SUSPENDED");
                insertWorkspace(connection, "site-n1", "db_n1", null, null, "ACTIVE");
                insertWorkspace(connection, "site-n2", "db_n2", null, null, "ACTIVE");
                assertRefused(
                        connection, "23505", () -> insertWorkspace(connection, "site-a", "db_x", null, null, "ACTIVE"));
                assertRefused(
                        connection, "23505", () -> insertWorkspace(connection, "site-x", "db_a", null, null, "ACTIVE"));
                assertRefused(
                        connection,
                        "23505",
                        () -> insertWorkspace(connection, "site-y", "db_y", "cloud-a", null, "ACTIVE"));
                assertRefused(
                        connection,
                        "23505",
                        () -> insertWorkspace(connection, "site-z", "db_z", null, "org-a", "ACTIVE"));
                assertRefused(
                        connection,
                        "23514",
                        () -> insertWorkspace(connection, "site-d", "db_d", null, null, "DELETED"));
                assertRefused(
                        connection, "23502", () -> insertWorkspace(connection, null, "db_q", null, null, "ACTIVE"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("AC-001.16 email table: key email, lower case only, FK to the workspace table")
    void createsEmailTableLowerCaseWithForeignKey_AC_001_16() throws Exception {
        String url = PostgresTestBase.emptyDatabase("ac16");
        webRunner(url).run(context -> assertThat(context).hasNotFailed());

        try (Connection connection = registryConnection(url)) {
            assertThat(primaryKeyColumns(connection, "workspace_email")).containsExactly("email");
            assertThat(scalar(
                            connection,
                            "SELECT is_nullable FROM information_schema.columns WHERE table_schema = 'public' "
                                    + "AND table_name = 'workspace_email' AND column_name = 'workspace_id'"))
                    .isEqualTo("NO");
            assertThat(scalar(
                            connection,
                            "SELECT confrelid::regclass::text FROM pg_constraint WHERE contype = 'f' "
                                    + "AND conrelid = 'public.workspace_email'::regclass"))
                    .isEqualTo("tenant_workspace");

            connection.setAutoCommit(false);
            try {
                UUID first = insertWorkspace(connection, "site-a", "db_a", null, null, "ACTIVE");
                UUID second = insertWorkspace(connection, "site-b", "db_b", null, null, "ACTIVE");
                insertEmail(connection, "ann@x.test", first);
                assertRefused(connection, "23514", () -> insertEmail(connection, "Ann@X.test", first));
                assertRefused(connection, "23505", () -> insertEmail(connection, "ann@x.test", second));
                assertRefused(connection, "23503", () -> insertEmail(connection, "bob@x.test", UUID.randomUUID()));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("AC-001.17 Registry holds no table or column outside the allowed set")
    void holdsOnlyTheAllowedTables_AC_001_17() throws Exception {
        String url = PostgresTestBase.emptyDatabase("ac17");
        webRunner(url).run(context -> assertThat(context).hasNotFailed());

        try (Connection connection = registryConnection(url)) {
            assertThat(names(
                            connection,
                            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                                    + "AND table_type = 'BASE TABLE'"))
                    .containsExactlyInAnyOrder(
                            "tenant_workspace", "workspace_email", "databasechangelog", "databasechangeloglock");
            assertThat(names(
                            connection,
                            "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                                    + "WHERE n.nspname = 'public' AND c.relkind IN ('v', 'm', 'S')"))
                    .isEmpty();
            assertThat(names(
                            connection,
                            "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' "
                                    + "AND table_name = 'tenant_workspace'"))
                    .isSubsetOf(WORKSPACE_COLUMNS);
            assertThat(names(
                            connection,
                            "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' "
                                    + "AND table_name = 'workspace_email'"))
                    .containsExactlyInAnyOrder("email", "workspace_id");
        }
    }

    @Test
    @DisplayName("AC-001.19 applied to an empty Registry: no row; a second start changes nothing")
    void appliesToEmptyRegistryWithNoRowsTwice_AC_001_19() throws Exception {
        String url = PostgresTestBase.emptyDatabase("ac19");

        webRunner(url).run(context -> assertThat(context).hasNotFailed());
        List<String> afterFirst;
        try (Connection connection = registryConnection(url)) {
            assertThat(scalar(connection, "SELECT count(*) FROM tenant_workspace"))
                    .isEqualTo("0");
            assertThat(scalar(connection, "SELECT count(*) FROM workspace_email"))
                    .isEqualTo("0");
            afterFirst = names(connection, "SELECT id || '|' || author || '|' || filename FROM databasechangelog");
        }
        assertThat(afterFirst).anyMatch(row -> row.endsWith("C001-registry-tenant-workspace.sql"));
        assertThat(afterFirst).anyMatch(row -> row.endsWith("C002-registry-workspace-email.sql"));

        webRunner(url).run(context -> assertThat(context).hasNotFailed());
        try (Connection connection = registryConnection(url)) {
            assertThat(scalar(connection, "SELECT count(*) FROM tenant_workspace"))
                    .isEqualTo("0");
            assertThat(scalar(connection, "SELECT count(*) FROM workspace_email"))
                    .isEqualTo("0");
            assertThat(names(connection, "SELECT id || '|' || author || '|' || filename FROM databasechangelog"))
                    .containsExactlyInAnyOrderElementsOf(afterFirst);
        }
    }

    @Test
    @DisplayName("AC-001.33 the Registry changelog creates nothing in the Jobs database")
    void createsNothingInTheJobsDatabase_AC_001_33() throws Exception {
        List<String> before = jobsSnapshot();

        webRunner(PostgresTestBase.emptyDatabase("ac33"))
                .run(context -> assertThat(context).hasNotFailed());

        List<String> after = jobsSnapshot();
        assertThat(after).containsExactlyInAnyOrderElementsOf(before);
        assertThat(after).noneMatch(row -> row.contains("tenant_workspace") || row.contains("workspace_email"));
        assertThat(after).noneMatch(row -> row.contains("db/changelog/catalog/"));
    }

    @Test
    @DisplayName("AC-008.4 AC-001.18 AC-001.27 web process applies the catalog changelog at startup as the owner user")
    void webProcessAppliesCatalogChangelogAtStartupAsOwner_AC_008_4_AC_001_18_AC_001_27() throws Exception {
        String url = PostgresTestBase.emptyDatabase("ac84");

        webRunner(url).run(context -> assertThat(context).hasNotFailed());

        try (Connection connection = registryConnection(url)) {
            assertThat(names(
                            connection,
                            "SELECT tablename || ':' || tableowner FROM pg_tables WHERE schemaname = 'public' "
                                    + "AND tablename IN ('tenant_workspace', 'workspace_email')"))
                    .containsExactlyInAnyOrder(
                            "tenant_workspace:" + PostgresTestBase.REGISTRY_USER,
                            "workspace_email:" + PostgresTestBase.REGISTRY_USER);
            List<String> filenames = names(connection, "SELECT filename FROM databasechangelog");
            assertThat(filenames).isNotEmpty().allMatch(name -> name.contains("db/changelog/catalog/"));
        }
        String tenantMaster = new String(
                new ClassPathResource("db/changelog/db.changelog-master.xml")
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(tenantMaster).doesNotContain("catalog/");
        assertThat(new ClassPathResource("db/changelog/catalog/catalog-master.xml").exists())
                .isTrue();
    }

    @Test
    @DisplayName("edge 72 the Registry beans leave the primary DataSource and the tenant Liquibase in place")
    void keepsPrimaryDataSourceAndTenantLiquibase_edge_72() {
        String registry = PostgresTestBase.emptyDatabase("e72");

        webRunner(registry)
                .withConfiguration(
                        AutoConfigurations.of(DataSourceAutoConfiguration.class, LiquibaseAutoConfiguration.class))
                .withPropertyValues(
                        "spring.datasource.url=" + PostgresTestBase.url(PostgresTestBase.APP_DB),
                        "spring.datasource.username=" + PostgresTestBase.APP_USER,
                        "spring.datasource.password=" + PostgresTestBase.APP_PASSWORD)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasBean("registryDataSource")
                            .hasBean("registryTransactionManager")
                            .hasBean("registryJdbcClient");
                    try (Connection connection =
                            context.getBean(DataSource.class).getConnection()) {
                        assertThat(scalar(connection, "SELECT current_database()"))
                                .isEqualTo(PostgresTestBase.APP_DB);
                    }
                    assertThat(context).hasBean("liquibase");
                    assertThat(context.getBean("liquibase", SpringLiquibase.class)
                                    .getChangeLog())
                            .endsWith("db/changelog/db.changelog-master.xml");
                });
    }

    @Test
    @DisplayName("AC-008.4 the worker process never runs the catalog changelog")
    void workerNeverRunsRegistryLiquibase_AC_008_4() throws Exception {
        String url = PostgresTestBase.emptyDatabase("worker");

        webRunner(url).withPropertyValues("spring.profiles.active=worker").run(context -> {
            assertThat(context).hasNotFailed().hasBean("registryDataSource");
            try (Connection connection =
                    context.getBean("registryDataSource", DataSource.class).getConnection()) {
                assertThat(scalar(connection, "SELECT 1")).isEqualTo("1");
            }
        });

        try (Connection connection = registryConnection(url)) {
            assertThat(scalar(connection, "SELECT to_regclass('public.databasechangelog')"))
                    .isNull();
            assertThat(scalar(connection, "SELECT to_regclass('public.tenant_workspace')"))
                    .isNull();
        }
    }

    @Test
    @DisplayName(
            "AC-001.25 edge 13 missing CATALOG_SECRET_KEY refuses startup naming the variable; blank URL loads none")
    void missingKeyRefusesStartupNamingVariable_AC_001_25_edge_13() {
        String url = PostgresTestBase.emptyDatabase("edge13");

        runner(Map.of(
                        "REGISTRY_DB_URL", url,
                        "REGISTRY_DB_USER", PostgresTestBase.REGISTRY_USER,
                        "REGISTRY_DB_PASSWORD", PostgresTestBase.REGISTRY_PASSWORD))
                .run(context -> {
                    assertThat(context).hasFailed();
                    String failure = stackTrace(context.getStartupFailure());
                    assertThat(failure).contains("CATALOG_SECRET_KEY");
                    assertThat(failure).doesNotContain(PostgresTestBase.REGISTRY_PASSWORD, KEY);
                });

        runner(Map.of(
                        "REGISTRY_DB_URL",
                        "",
                        "REGISTRY_DB_USER",
                        PostgresTestBase.REGISTRY_USER,
                        "REGISTRY_DB_PASSWORD",
                        PostgresTestBase.REGISTRY_PASSWORD,
                        "CATALOG_SECRET_KEY",
                        KEY))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("registryDataSource");
                    assertThat(context).doesNotHaveBean("registryTransactionManager");
                    assertThat(context).doesNotHaveBean("registryJdbcClient");
                    assertThat(context).doesNotHaveBean(PasswordCipher.class);
                });
    }

    // ---- helpers ----

    private static ApplicationContextRunner webRunner(String registryUrl) {
        return runner(Map.of(
                "REGISTRY_DB_URL",
                registryUrl,
                "REGISTRY_DB_USER",
                PostgresTestBase.REGISTRY_USER,
                "REGISTRY_DB_PASSWORD",
                PostgresTestBase.REGISTRY_PASSWORD,
                "CATALOG_SECRET_KEY",
                KEY));
    }

    /** Main application.yml only, no machine environment, plus exactly the given variables. */
    private static ApplicationContextRunner runner(Map<String, Object> variables) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    try {
                        for (var source : new YamlPropertySourceLoader()
                                .load("application.yml", new ClassPathResource("application.yml"))) {
                            sources.addLast(source);
                        }
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                    sources.addFirst(new MapPropertySource("registryVariables", new LinkedHashMap<>(variables)));
                })
                .withUserConfiguration(RegistryDataSourceConfig.class);
    }

    private static Connection registryConnection(String url) throws SQLException {
        return DriverManager.getConnection(url, PostgresTestBase.REGISTRY_USER, PostgresTestBase.REGISTRY_PASSWORD);
    }

    private static List<String> jobsSnapshot() throws SQLException {
        try (Connection connection = PostgresTestBase.superuserConnection(PostgresTestBase.JOBS_DB)) {
            List<String> rows = new ArrayList<>(names(
                    connection,
                    "SELECT 'table:' || table_name FROM information_schema.tables WHERE table_schema = 'public'"));
            if (scalar(connection, "SELECT to_regclass('public.databasechangelog')") != null) {
                rows.addAll(names(connection, "SELECT 'changeset:' || id || '|' || filename FROM databasechangelog"));
            }
            return rows;
        }
    }

    private static UUID insertWorkspace(
            Connection connection, String site, String db, String cloudId, String organizationId, String status)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tenant_workspace (workspace_id, site_name, status, db_name, db_user, db_password, "
                        + "schema_version, jira_cloud_id, google_domain, keycloak_organization_id, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, 'u', 'v1:x', '1', ?, 'x.test', ?, now(), now())")) {
            statement.setObject(1, id);
            statement.setString(2, site);
            statement.setString(3, status);
            statement.setString(4, db);
            statement.setString(5, cloudId);
            statement.setString(6, organizationId);
            statement.executeUpdate();
        }
        return id;
    }

    private static void insertEmail(Connection connection, String email, UUID workspaceId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("INSERT INTO workspace_email (email, workspace_id) VALUES (?, ?)")) {
            statement.setString(1, email);
            statement.setObject(2, workspaceId);
            statement.executeUpdate();
        }
    }

    private interface Write {
        void run() throws SQLException;
    }

    /** Runs a write that PostgreSQL must refuse with this SQLSTATE; the transaction stays usable. */
    private static void assertRefused(Connection connection, String sqlState, Write write) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            write.run();
            throw new AssertionError("Expected PostgreSQL to refuse the write with SQLSTATE " + sqlState);
        } catch (SQLException expected) {
            assertThat(expected.getSQLState()).isEqualTo(sqlState);
            connection.rollback(savepoint);
        }
    }

    private static List<String> primaryKeyColumns(Connection connection, String table) throws SQLException {
        return names(
                connection,
                "SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid "
                        + "AND a.attnum = ANY (i.indkey) WHERE i.indisprimary AND i.indrelid = 'public." + table
                        + "'::regclass");
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private static List<String> names(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    private static String stackTrace(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
