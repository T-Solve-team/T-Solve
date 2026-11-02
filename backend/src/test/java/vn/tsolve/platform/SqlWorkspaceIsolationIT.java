package vn.tsolve.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.common.tenant.TenantAccessException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.TenantDatabase;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "tsolve.sql-workspace.enabled=true",
            "tsolve.sql-workspace.base-domain=localhost",
            "tsolve.cors.allowed-origins=http://localhost:5173",
            "server.port=0",
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class SqlWorkspaceIsolationIT {
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
        try {
            provision();
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("tsolve.sql-workspace.catalog.host", DB::getHost);
        properties.add("tsolve.sql-workspace.catalog.port", DB::getFirstMappedPort);
        properties.add("tsolve.sql-workspace.catalog.database", () -> "sql_catalog");
        properties.add("tsolve.sql-workspace.catalog.username", () -> "catalog_app");
        properties.add("tsolve.sql-workspace.catalog.password", () -> "test-only-catalog");
        properties.add("TENANT_A_PASSWORD", () -> "test-only-a");
        properties.add("TENANT_B_PASSWORD", () -> "test-only-b");
    }

    @Autowired
    private MockMvc http;

    @Autowired
    private TenantContext context;

    @Autowired
    @Qualifier("sqlTenantDatabase")
    private TenantDatabase tenant;

    @Autowired
    @Qualifier("dataSource")
    private DataSource legacy;

    @Test
    void routesRealRequestsAndLeavesLegacyJpaIntact_AC_002_18_AC_002_20_AC_002_24_AC_002_25() throws Exception {
        assertThat(new JdbcTemplate(legacy).queryForObject("SELECT count(*) FROM workspace", Long.class))
                .isEqualTo(4);
        for (String site : List.of("one", "two", "one")) {
            http.perform(get("/api/v1/users/tenant-probe")
                            .header("Host", site + ".localhost")
                            .header("X-Forwarded-Host", "two.localhost")
                            .header("X-Workspace-Id", "2")
                            .with(user("test-admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[\"" + site + "\"]"));
            assertThat(context.currentWorkspaceId()).isEmpty();
        }
        // The resolver must reject before Spring Security considers the anonymous caller.
        http.perform(get("/api/v1/users/tenant-probe").header("Host", "unknown.localhost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void maintainsTransactionBindingAndClearsAfterDatabaseFailure_AC_002_11_AC_002_21_AC_002_23_BUG_105() {
        context.runIn(1L, () -> {
            assertThat(tenant.jdbc().queryForObject("SELECT full_name FROM app_user WHERE user_id=1", String.class))
                    .isEqualTo("one");
            assertThatThrownBy(() -> context.runIn(2L, () -> "must not run")).isInstanceOf(TenantAccessException.class);
            assertThat(tenant.read(() -> tenant.jdbc().queryForObject("SHOW transaction_read_only", String.class)))
                    .isEqualTo("on");
        });
        assertThatThrownBy(() -> context.runIn(
                        1L, () -> tenant.jdbc().queryForObject("SELECT missing_column FROM app_user", String.class)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(context.currentWorkspaceId()).isEmpty();
        assertThatThrownBy(() -> tenant.jdbc().queryForObject("SELECT full_name FROM app_user", String.class))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    void databaseUsersCannotConnectAcrossBoundaries_AC_001_26_AC_002_27() {
        for (String database : List.of("sql_two", "sql_catalog", DB.getDatabaseName())) {
            assertThatThrownBy(() -> DriverManager.getConnection(url(database), "tenant_a", "test-only-a"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
        assertThatThrownBy(() -> DriverManager.getConnection(url("sql_one"), "catalog_app", "test-only-catalog"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void rereadsSuspensionEvenWithCachedPool_AC_002_7_AC_002_26() throws Exception {
        var catalog = SqlBootstrapIT.jdbc(url("sql_catalog"), DB);
        context.runIn(2L, () -> tenant.jdbc().queryForObject("SELECT count(*) FROM app_user", Long.class));
        catalog.update("UPDATE tenant_workspace SET status='SUSPENDED' WHERE workspace_id=2");
        try {
            http.perform(get("/api/v1/users/tenant-probe")
                            .header("Host", "two.localhost")
                            .with(user("test-admin").roles("ADMIN")))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("MSG150")));
            assertThat(context.currentWorkspaceId()).isEmpty();
        } finally {
            catalog.update("UPDATE tenant_workspace SET status='ACTIVE' WHERE workspace_id=2");
        }
    }

    @Test
    void refusesAmbiguousCaseInsensitiveSites_BUG_104() throws Exception {
        var catalog = SqlBootstrapIT.jdbc(url("sql_catalog"), DB);
        catalog.update("INSERT INTO tenant_workspace(site_name,db_host,db_name,db_username,db_password_ref)"
                + " VALUES('ONE','unused','case_ambiguity','unused','UNUSED_SECRET')");
        try {
            http.perform(get("/api/v1/users/tenant-probe")
                            .header("Host", "one.localhost")
                            .with(user("test-admin").roles("ADMIN")))
                    .andExpect(status().isInternalServerError());
        } finally {
            catalog.update("DELETE FROM tenant_workspace WHERE site_name='ONE'");
        }
    }

    private static String url(String database) {
        return DB.getJdbcUrl().replace("/" + DB.getDatabaseName(), "/" + database);
    }

    private static void provision() throws Exception {
        var owner = SqlBootstrapIT.jdbc(DB.getJdbcUrl(), DB);
        owner.execute("CREATE ROLE catalog_app LOGIN PASSWORD 'test-only-catalog'");
        owner.execute("CREATE ROLE tenant_a LOGIN PASSWORD 'test-only-a'");
        owner.execute("CREATE ROLE tenant_b LOGIN PASSWORD 'test-only-b'");
        for (String database : List.of("sql_catalog", "sql_one", "sql_two")) {
            owner.execute("CREATE DATABASE " + database);
        }
        SqlBootstrapIT.migrate(url("sql_catalog"), DB, "catalog");
        SqlBootstrapIT.migrate(url("sql_one"), DB, "tenant");
        SqlBootstrapIT.migrate(url("sql_two"), DB, "tenant");
        grantRuntime("sql_catalog", "catalog_app");
        grantRuntime("sql_one", "tenant_a");
        grantRuntime("sql_two", "tenant_b");
        owner.execute("REVOKE CONNECT ON DATABASE " + DB.getDatabaseName() + " FROM PUBLIC");
        var catalog = SqlBootstrapIT.jdbc(url("sql_catalog"), DB);
        for (int id = 1; id <= 2; id++) {
            String site = id == 1 ? "one" : "two";
            String role = id == 1 ? "tenant_a" : "tenant_b";
            String secret = id == 1 ? "TENANT_A_PASSWORD" : "TENANT_B_PASSWORD";
            catalog.update(
                    "INSERT INTO tenant_workspace(site_name,db_host,db_port,db_name,db_username,db_password_ref)"
                            + " VALUES(?,?,?,?,?,?)",
                    site,
                    DB.getHost(),
                    DB.getFirstMappedPort(),
                    "sql_" + site,
                    role,
                    secret);
            SqlBootstrapIT.jdbc(url("sql_" + site), DB)
                    .update(
                            "INSERT INTO app_user(user_id,full_name,company_email) VALUES(1,?,?)",
                            site,
                            site + "@example.test");
        }
    }

    private static void grantRuntime(String database, String role) {
        var owner = SqlBootstrapIT.jdbc(url(database), DB);
        owner.execute("REVOKE CONNECT ON DATABASE " + database + " FROM PUBLIC");
        owner.execute("GRANT CONNECT ON DATABASE " + database + " TO " + role);
        owner.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
        owner.execute("GRANT USAGE ON SCHEMA public TO " + role);
        owner.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO " + role);
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean
        ProbeController probeController(@Qualifier("sqlTenantDatabase") TenantDatabase database) {
            return new ProbeController(database);
        }
    }

    @RestController
    static class ProbeController {
        private final TenantDatabase database;

        ProbeController(TenantDatabase database) {
            this.database = database;
        }

        @GetMapping("/api/v1/users/tenant-probe")
        @PreAuthorize("hasRole('ADMIN')")
        List<String> probe() {
            return database.read(() ->
                    database.jdbc().queryForList("SELECT full_name FROM app_user ORDER BY user_id", String.class));
        }
    }
}
