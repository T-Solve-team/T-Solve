package vn.tsolve.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import vn.tsolve.common.tenant.KnownWorkspaces;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.identity.signin.SignInConfig;
import vn.tsolve.identity.signin.port.PlatformWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.jiraapp.service.impl.CatalogJiraWorkspaceLookup;
import vn.tsolve.platform.registry.RegistryDataSourceConfig;
import vn.tsolve.support.PostgresTestBase;

class CatalogLookupsIT {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static String registryUrl;

    @BeforeAll
    static void createRegistry() {
        registryUrl = PostgresTestBase.emptyDatabase("lookups");
    }

    @Test
    @DisplayName(
            "AC-004.17 WorkspaceLookup finds the Registry row by its site host in any case and with a port, and by id")
    void workspaceLookupReadsTheRegistry_AC_004_17() {
        withLookups(context -> {
            UUID bluefinId;
            try (Connection connection = registryConnection()) {
                bluefinId = insertWorkspace(
                        connection,
                        1,
                        "bluefin",
                        "ACTIVE",
                        "bluefin.example",
                        "org-b",
                        "https://bluefin.atlassian.net",
                        "c-bluefin");
            }

            WorkspaceLookup lookup = context.getBean(WorkspaceLookup.class);
            WorkspaceRef expected = new WorkspaceRef(
                    bluefinId,
                    "bluefin.tsolve.test",
                    "bluefin.example",
                    WorkspaceRef.Status.ACTIVE,
                    "bluefin",
                    "org-b");

            assertThat(lookup.findBySiteHost("BlueFin.tsolve.test:443")).contains(expected);
            assertThat(lookup.findById(bluefinId)).contains(expected);
            assertThat(lookup.findBySiteHost("nosuch.tsolve.test")).isEmpty();
            assertThat(lookup.findBySiteHost("x'or'1'='1.tsolve.test")).isEmpty();
        });
    }

    @Test
    @DisplayName("AC-004.17 JiraWorkspaceLookup.workspaceOf the row's cloud id gives its Workspace id")
    void jiraLookupReadsTheRegistry_AC_004_17() {
        withLookups(context -> {
            UUID bluefinId;
            try (Connection connection = registryConnection()) {
                bluefinId = insertWorkspace(
                        connection,
                        1,
                        "bluefin",
                        "ACTIVE",
                        "bluefin.example",
                        "org-b",
                        "https://bluefin.atlassian.net",
                        "c-bluefin");
            }

            JiraWorkspaceLookup jiraLookup = context.getBean(JiraWorkspaceLookup.class);
            assertThat(jiraLookup.workspaceOf("c-bluefin")).contains(bluefinId);
            assertThat(jiraLookup.workspaceOf("c-other")).isEmpty();
        });
    }

    @Test
    @DisplayName(
            "AC-002.14 AC-002.15 TenantContext.runIn accepts the Registry row's id, refuses another without running the action, and binds a Suspended one")
    void runInAcceptsOnlyARegistryWorkspace_AC_002_14_AC_002_15() {
        withLookups(context -> {
            UUID bluefinId;
            UUID pausedId;
            try (Connection connection = registryConnection()) {
                bluefinId = insertWorkspace(
                        connection,
                        1,
                        "bluefin",
                        "ACTIVE",
                        "bluefin.example",
                        "org-b",
                        "https://bluefin.atlassian.net",
                        "c-bluefin");
                pausedId = insertWorkspace(connection, 2, "paused", "SUSPENDED", "paused.example", null, null, null);
            }

            TenantContext tenant = context.getBean(TenantContext.class);

            tenant.runIn(
                    bluefinId, () -> assertThat(tenant.currentWorkspaceId()).contains(bluefinId));
            assertThat(tenant.currentWorkspaceId()).isEmpty();

            AtomicInteger ran = new AtomicInteger();
            UUID randomId = UUID.randomUUID();
            assertThatThrownBy(() -> tenant.runIn(randomId, ran::incrementAndGet))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(ran).hasValue(0);

            tenant.runIn(pausedId, () -> assertThat(tenant.currentWorkspaceId()).contains(pausedId));
            assertThat(tenant.currentWorkspaceId()).isEmpty();
        });
    }

    /** Not annotated: a component scan in another test must not pick this class up; the runner registers it. */
    static class LookupsConfig {
        @Bean
        KnownWorkspaces knownWorkspaces(WorkspaceLookup lookup) {
            return new SignInConfig().knownWorkspaces(lookup);
        }
    }

    private interface Body {
        void run(AssertableApplicationContext context) throws Exception;
    }

    private static void withLookups(Body body) {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            try (Connection connection = registryConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM workspace_email");
                statement.executeUpdate("DELETE FROM tenant_workspace");
            }
            body.run(context);
        });
    }

    private static ApplicationContextRunner runner() {
        Map<String, Object> variables = Map.of(
                "REGISTRY_DB_URL",
                registryUrl,
                "REGISTRY_DB_USER",
                PostgresTestBase.REGISTRY_USER,
                "REGISTRY_DB_PASSWORD",
                PostgresTestBase.REGISTRY_PASSWORD,
                "CATALOG_SECRET_KEY",
                KEY,
                "TSOLVE_SITE_ORIGIN_TEMPLATE",
                "https://{slug}.tsolve.test");
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
                .withUserConfiguration(
                        RegistryDataSourceConfig.class,
                        ThreadLocalTenantContext.class,
                        PlatformWorkspaceLookup.class,
                        CatalogJiraWorkspaceLookup.class,
                        LookupsConfig.class);
    }

    private static Connection registryConnection() throws SQLException {
        return DriverManager.getConnection(
                registryUrl, PostgresTestBase.REGISTRY_USER, PostgresTestBase.REGISTRY_PASSWORD);
    }

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
