package vn.tsolve.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.tenant.NoTenantException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.registry.RegistryDataSourceConfig;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.support.PostgresTestBase;

/**
 * The {@link WorkspaceCatalog} over the Registry database (STORY-004), started the way the web process starts it:
 * {@link RegistryDataSourceConfig} on an {@link ApplicationContextRunner}. The context has no business DataSource, so
 * any read of a tenant table fails the test (AC-004.27: only catalog tables). Fixture rows are written with plain JDBC
 * as the Registry user.
 */
class WorkspaceCatalogIT {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static String registryUrl;

    @BeforeAll
    static void createRegistry() {
        registryUrl = PostgresTestBase.emptyDatabase("catalog");
    }

    @Test
    @DisplayName(
            "AC-004.17 findById returns the id, site name, status, Google domain, org id and Jira site; an unknown id is empty")
    void findByIdMapsTheRow_AC_004_17() {
        withCatalog((catalog, context) -> {
            UUID full;
            UUID bare;
            try (Connection connection = registryConnection()) {
                full = insertWorkspace(
                        connection,
                        1,
                        "bluefin",
                        "ACTIVE",
                        "acme.test",
                        "org-1",
                        "https://bluefin.atlassian.net",
                        "c-1");
                bare = insertWorkspace(connection, 2, "plain", "SUSPENDED", "plain.test", null, null, null);
            }

            WorkspaceSummary found = catalog.findById(full).orElseThrow();
            assertThat(found.id()).isEqualTo(full);
            assertThat(found.siteName()).isEqualTo("bluefin");
            assertThat(found.status()).isEqualTo(WorkspaceStatus.ACTIVE);
            assertThat(found.googleDomain()).isEqualTo("acme.test");
            assertThat(found.keycloakOrganizationId()).isEqualTo(Optional.of("org-1"));
            assertThat(found.jiraSiteUrl()).isEqualTo(Optional.of("https://bluefin.atlassian.net"));
            assertThat(found.jiraCloudId()).isEqualTo(Optional.of("c-1"));

            WorkspaceSummary unlinked = catalog.findById(bare).orElseThrow();
            assertThat(unlinked.status()).isEqualTo(WorkspaceStatus.SUSPENDED);
            assertThat(unlinked.keycloakOrganizationId()).isEmpty();
            assertThat(unlinked.jiraSiteUrl()).isEmpty();
            assertThat(unlinked.jiraCloudId()).isEmpty();

            assertThat(catalog.findById(UUID.randomUUID())).isEmpty();
        });
    }

    @Test
    @DisplayName("AC-004.17 findBySiteName ignores case; an unknown name is empty")
    void findBySiteNameIgnoresCase_AC_004_17() {
        withCatalog((catalog, context) -> {
            UUID id;
            try (Connection connection = registryConnection()) {
                id = insertWorkspace(connection, 1, "bluefin", "ACTIVE", "acme.test", null, null, null);
            }

            assertThat(catalog.findBySiteName("bluefin").map(WorkspaceSummary::id))
                    .contains(id);
            assertThat(catalog.findBySiteName("BlueFin").map(WorkspaceSummary::id))
                    .contains(id);
            assertThat(catalog.findBySiteName("BLUEFIN").map(WorkspaceSummary::id))
                    .contains(id);
            assertThat(catalog.findBySiteName("nosuch")).isEmpty();
        });
    }

    @Test
    @DisplayName("AC-004.17 findByCloudId finds the row; an unknown or null cloud id is empty")
    void findByCloudIdFindsTheRow_AC_004_17() {
        withCatalog((catalog, context) -> {
            UUID linked;
            try (Connection connection = registryConnection()) {
                linked = insertWorkspace(connection, 1, "linked", "ACTIVE", "a.test", null, null, "c-1");
                insertWorkspace(connection, 2, "unlinked", "ACTIVE", "b.test", null, null, null);
            }

            assertThat(catalog.findByCloudId("c-1").map(WorkspaceSummary::id)).contains(linked);
            assertThat(catalog.findByCloudId("c-2")).isEmpty();
            assertThat(catalog.findByCloudId(null)).isEmpty();
        });
    }

    @Test
    @DisplayName("AC-004.18 no lookup returns connection data")
    void noLookupReturnsConnectionData_AC_004_18() {
        withCatalog((catalog, context) -> {
            UUID first;
            UUID second;
            try (Connection connection = registryConnection()) {
                first = insertWorkspace(connection, 1, "bluefin", "ACTIVE", "a.test", "org-1", null, "c-1");
                second = insertWorkspace(connection, 2, "other", "SUSPENDED", "b.test", null, null, null);
            }

            List<String> results = new ArrayList<>();
            results.add(catalog.findById(first).orElseThrow().toString());
            results.add(catalog.findById(second).orElseThrow().toString());
            results.add(catalog.findBySiteName("bluefin").orElseThrow().toString());
            results.add(catalog.findByCloudId("c-1").orElseThrow().toString());
            catalog.findActive().forEach(summary -> results.add(summary.toString()));

            assertThat(results).hasSize(5);
            for (String result : results) {
                for (int n : new int[] {1, 2}) {
                    assertThat(result).doesNotContain("db_marker_" + n, "user_marker_" + n, "secret_marker_" + n);
                }
            }
            assertThat(Arrays.stream(WorkspaceSummary.class.getRecordComponents())
                            .map(RecordComponent::getName))
                    .containsExactly(
                            "id",
                            "siteName",
                            "status",
                            "googleDomain",
                            "keycloakOrganizationId",
                            "jiraSiteUrl",
                            "jiraCloudId");
        });
    }

    @Test
    @DisplayName("AC-004.29 findActive lists only the Active Workspaces and is empty when none is Active")
    void findActiveListsOnlyActive_AC_004_29() {
        withCatalog((catalog, context) -> {
            assertThat(catalog.findActive()).isNotNull().isEmpty();

            UUID first;
            UUID second;
            UUID suspended;
            try (Connection connection = registryConnection()) {
                first = insertWorkspace(connection, 1, "one", "ACTIVE", "a.test", "org-1", null, "c-1");
                second = insertWorkspace(connection, 2, "two", "ACTIVE", "b.test", null, null, null);
                suspended = insertWorkspace(connection, 3, "three", "SUSPENDED", "c.test", null, null, null);
            }

            List<WorkspaceSummary> active = catalog.findActive();
            assertThat(active).extracting(WorkspaceSummary::id).containsExactlyInAnyOrder(first, second);
            assertThat(active).extracting(WorkspaceSummary::id).doesNotContain(suspended);
            for (WorkspaceSummary summary : active) {
                assertThat(catalog.findById(summary.id())).contains(summary);
            }
        });
    }

    @Test
    @DisplayName("AC-004.29 findActive follows a status change on the next call")
    void findActiveFollowsAStatusChange_AC_004_29() {
        withCatalog((catalog, context) -> {
            UUID first;
            UUID second;
            try (Connection connection = registryConnection()) {
                first = insertWorkspace(connection, 1, "one", "ACTIVE", "a.test", null, null, null);
                second = insertWorkspace(connection, 2, "two", "ACTIVE", "b.test", null, null, null);
            }
            assertThat(catalog.findActive()).extracting(WorkspaceSummary::id).containsExactlyInAnyOrder(first, second);

            setStatus(second, "SUSPENDED");
            assertThat(catalog.findActive()).extracting(WorkspaceSummary::id).containsExactly(first);

            setStatus(second, "ACTIVE");
            assertThat(catalog.findActive()).extracting(WorkspaceSummary::id).containsExactlyInAnyOrder(first, second);
        });
    }

    @Test
    @DisplayName("AC-004.27 AC-004.29 the catalog never binds a Workspace")
    void theCatalogNeverBindsAWorkspace_AC_004_27_AC_004_29() {
        withCatalog((catalog, context) -> {
            TenantContext tenant = context.getBean(TenantContext.class);
            ThreadLocalTenantContext binder = new ThreadLocalTenantContext(id -> true);
            UUID a;
            try (Connection connection = registryConnection()) {
                a = insertWorkspace(connection, 1, "one", "ACTIVE", "a.test", null, null, "c-1");
            }

            catalog.findById(a);
            assertThat(tenant.currentWorkspaceId()).isEmpty();
            catalog.findBySiteName("one");
            assertThat(tenant.currentWorkspaceId()).isEmpty();
            catalog.findByCloudId("c-1");
            assertThat(tenant.currentWorkspaceId()).isEmpty();
            catalog.findActive();
            assertThat(tenant.currentWorkspaceId()).isEmpty();

            binder.runIn(a, () -> {
                catalog.findById(a);
                assertThat(tenant.currentWorkspaceId()).contains(a);
                catalog.findBySiteName("one");
                assertThat(tenant.currentWorkspaceId()).contains(a);
                catalog.findByCloudId("c-1");
                assertThat(tenant.currentWorkspaceId()).contains(a);
                catalog.findActive();
                assertThat(tenant.currentWorkspaceId()).contains(a);
                catalog.claimEmail("ann@x.com");
                assertThat(tenant.currentWorkspaceId()).contains(a);
            });
            assertThat(tenant.currentWorkspaceId()).isEmpty();
        });
    }

    @Test
    @DisplayName("AC-004.25 a free email is recorded lower-case and trimmed for the bound Workspace")
    void aFreeEmailIsRecordedLowerCase_AC_004_25() {
        withCatalog((catalog, context) -> {
            ThreadLocalTenantContext binder = new ThreadLocalTenantContext(id -> true);
            UUID a;
            try (Connection connection = registryConnection()) {
                a = insertWorkspace(connection, 1, "one", "ACTIVE", "a.test", null, null, null);
            }

            binder.runIn(a, () -> catalog.claimEmail(" Ann@X.com "));

            assertThat(emailRows()).containsExactly("ann@x.com|" + a);
        });
    }

    @Test
    @DisplayName("AC-004.26 the same email in another case for the same Workspace succeeds and changes nothing")
    void sameEmailForTheSameWorkspaceChangesNothing_AC_004_26() {
        withCatalog((catalog, context) -> {
            ThreadLocalTenantContext binder = new ThreadLocalTenantContext(id -> true);
            UUID a;
            try (Connection connection = registryConnection()) {
                a = insertWorkspace(connection, 1, "one", "ACTIVE", "a.test", null, null, null);
            }

            binder.runIn(a, () -> catalog.claimEmail(" Ann@X.com "));
            binder.runIn(a, () -> catalog.claimEmail("ANN@x.COM"));

            assertThat(emailRows()).containsExactly("ann@x.com|" + a);
        });
    }

    @Test
    @DisplayName("AC-004.26 an email of another Workspace is refused with MSG155 on email and nothing is recorded")
    void anEmailOfAnotherWorkspaceIsRefused_AC_004_26() {
        withCatalog((catalog, context) -> {
            ThreadLocalTenantContext binder = new ThreadLocalTenantContext(id -> true);
            UUID a;
            UUID b;
            try (Connection connection = registryConnection()) {
                a = insertWorkspace(connection, 1, "one", "ACTIVE", "a.test", null, null, null);
                b = insertWorkspace(connection, 2, "two", "ACTIVE", "b.test", null, null, null);
            }
            binder.runIn(a, () -> catalog.claimEmail("ann@x.com"));

            assertThatThrownBy(() -> binder.runIn(b, () -> catalog.claimEmail("Ann@x.com")))
                    .isInstanceOfSatisfying(BusinessRuleException.class, exception -> {
                        assertThat(exception.getCode()).isEqualTo("MSG155");
                        assertThat(exception.getField()).isEqualTo("email");
                    });

            assertThat(emailRows()).containsExactly("ann@x.com|" + a);
        });
    }

    @Test
    @DisplayName("edge 26 two Workspaces claiming one email at once: one row, the other MSG155")
    void twoWorkspacesClaimingOneEmailAtOnce_edge_26() {
        withCatalog((catalog, context) -> {
            UUID a;
            UUID b;
            try (Connection connection = registryConnection()) {
                a = insertWorkspace(connection, 1, "one", "ACTIVE", "a.test", null, null, null);
                b = insertWorkspace(connection, 2, "two", "ACTIVE", "b.test", null, null, null);
            }

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                for (int round = 0; round < 20; round++) {
                    String email = "race" + round + "@x.com";
                    CountDownLatch go = new CountDownLatch(1);
                    Future<Throwable> forA = pool.submit(claim(catalog, a, "Race" + round + "@X.com", go));
                    Future<Throwable> forB = pool.submit(claim(catalog, b, " race" + round + "@x.com ", go));
                    go.countDown();
                    Throwable outcomeA = forA.get();
                    Throwable outcomeB = forB.get();

                    Throwable loser;
                    UUID winner;
                    if (outcomeA == null) {
                        winner = a;
                        loser = outcomeB;
                    } else {
                        winner = b;
                        loser = outcomeA;
                        assertThat(outcomeB)
                                .as("round %d: one claim must succeed", round)
                                .isNull();
                    }
                    assertThat(loser)
                            .as("round %d: the other claim is refused", round)
                            .isInstanceOfSatisfying(BusinessRuleException.class, exception -> {
                                assertThat(exception.getCode()).isEqualTo("MSG155");
                                assertThat(exception.getField()).isEqualTo("email");
                            });
                    assertThat(emailRows()).contains(email + "|" + winner);
                    assertThat(emailRows().stream().filter(row -> row.startsWith(email + "|")))
                            .hasSize(1);
                }
            } catch (InterruptedException | ExecutionException exception) {
                throw new AssertionError(exception);
            } finally {
                pool.shutdownNow();
            }
        });
    }

    @Test
    @DisplayName("edge 76 AC-004.25 claimEmail with no Workspace bound throws NoTenantException and records nothing")
    void claimEmailWithNothingBound_edge_76_AC_004_25() {
        withCatalog((catalog, context) -> {
            assertThatThrownBy(() -> catalog.claimEmail("ann@x.com")).isInstanceOf(NoTenantException.class);

            assertThat(emailRows()).isEmpty();
        });
    }

    @Test
    @DisplayName("edge 80 a Registry failure is thrown, never answered as empty")
    void aRegistryFailureIsThrownNeverEmpty_edge_80() {
        withCatalog((catalog, context) -> {
            context.getBean("registryDataSource", HikariDataSource.class).close();

            assertThatThrownBy(() -> catalog.findById(UUID.randomUUID())).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> catalog.findBySiteName("bluefin")).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> catalog.findByCloudId("c-1")).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(catalog::findActive).isInstanceOf(DataAccessException.class);
        });
    }

    // ---- helpers ----

    private interface Body {
        void run(WorkspaceCatalog catalog, AssertableApplicationContext context) throws Exception;
    }

    /** Starts the web process's Registry context, empties both tables, and runs the test body on its catalog bean. */
    private static void withCatalog(Body body) {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            try (Connection connection = registryConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM workspace_email");
                statement.executeUpdate("DELETE FROM tenant_workspace");
            }
            body.run(context.getBean(WorkspaceCatalog.class), context);
        });
    }

    /** The catalog may only read the binding: any runIn by it fails the test (AC-004.27). */
    private static final class NoBindingTenantContext implements TenantContext {
        private final ThreadLocalTenantContext delegate = new ThreadLocalTenantContext(id -> true);

        @Override
        public Optional<UUID> currentWorkspaceId() {
            return delegate.currentWorkspaceId();
        }

        @Override
        public UUID requireWorkspaceId() {
            return delegate.requireWorkspaceId();
        }

        @Override
        public <T> T runIn(UUID workspaceId, Supplier<T> action) {
            throw new AssertionError("the catalog bound a Workspace");
        }

        @Override
        public void runIn(UUID workspaceId, Runnable action) {
            throw new AssertionError("the catalog bound a Workspace");
        }
    }

    /** Main application.yml only, no machine environment, plus exactly the Registry variables. */
    private static ApplicationContextRunner runner() {
        Map<String, Object> variables = Map.of(
                "REGISTRY_DB_URL",
                registryUrl,
                "REGISTRY_DB_USER",
                PostgresTestBase.REGISTRY_USER,
                "REGISTRY_DB_PASSWORD",
                PostgresTestBase.REGISTRY_PASSWORD,
                "CATALOG_SECRET_KEY",
                KEY);
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
                .withBean("tenantContext", TenantContext.class, NoBindingTenantContext::new)
                .withUserConfiguration(RegistryDataSourceConfig.class);
    }

    private static Callable<Throwable> claim(
            WorkspaceCatalog catalog, UUID workspaceId, String email, CountDownLatch go) {
        return () -> {
            go.await();
            try {
                new ThreadLocalTenantContext(id -> true).runIn(workspaceId, () -> catalog.claimEmail(email));
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        };
    }

    private static Connection registryConnection() throws SQLException {
        return DriverManager.getConnection(
                registryUrl, PostgresTestBase.REGISTRY_USER, PostgresTestBase.REGISTRY_PASSWORD);
    }

    /** One row per recorded email: {@code email|workspaceId}. */
    private static List<String> emailRows() throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = registryConnection();
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery("SELECT email || '|' || workspace_id FROM workspace_email ORDER BY 1")) {
            while (result.next()) {
                rows.add(result.getString(1));
            }
        }
        return rows;
    }

    private static void setStatus(UUID workspaceId, String status) throws SQLException {
        try (Connection connection = registryConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE tenant_workspace SET status = ?, updated_at = now() WHERE workspace_id = ?")) {
            statement.setString(1, status);
            statement.setObject(2, workspaceId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    /** Every column; the connection markers {@code db_marker_n}, {@code user_marker_n}, {@code secret_marker_n}. */
    private static UUID insertWorkspace(
            Connection connection,
            int n,
            String siteName,
            String status,
            String googleDomain,
            String organizationId,
            String jiraSiteUrl,
            String jiraCloudId)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tenant_workspace (workspace_id, site_name, status, db_name, db_user, db_password, "
                        + "schema_version, jira_site_url, jira_cloud_id, google_domain, keycloak_organization_id, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, '1', ?, ?, ?, ?, now(), now())")) {
            statement.setObject(1, id);
            statement.setString(2, siteName);
            statement.setString(3, status);
            statement.setString(4, "db_marker_" + n);
            statement.setString(5, "user_marker_" + n);
            statement.setString(6, "v1:secret_marker_" + n);
            statement.setString(7, jiraSiteUrl);
            statement.setString(8, jiraCloudId);
            statement.setString(9, googleDomain);
            statement.setString(10, organizationId);
            statement.executeUpdate();
        }
        return id;
    }
}
