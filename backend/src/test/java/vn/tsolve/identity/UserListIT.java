package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.session.WorkspaceAccountPrincipal;
import vn.tsolve.platform.SqlBootstrapIT;

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
class UserListIT {
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
    static void configuration(DynamicPropertyRegistry properties) {
        properties.add("tsolve.sql-workspace.catalog.host", DB::getHost);
        properties.add("tsolve.sql-workspace.catalog.port", DB::getFirstMappedPort);
        properties.add("tsolve.sql-workspace.catalog.database", () -> "users_catalog");
        properties.add("tsolve.sql-workspace.catalog.username", () -> "users_catalog_reader");
        properties.add("tsolve.sql-workspace.catalog.password", () -> "test-only-catalog");
        properties.add("USERS_A_PASSWORD", () -> "test-only-a");
        properties.add("USERS_B_PASSWORD", () -> "test-only-b");
    }

    @Autowired
    private MockMvc http;

    @Autowired
    private TenantContext context;

    @BeforeEach
    void seed() {
        for (String workspace : List.of("a", "b")) {
            var owner = owner(workspace);
            owner.execute("TRUNCATE app_user,department RESTART IDENTITY CASCADE");
            owner.update(
                    "INSERT INTO department(department_id,name,status) VALUES(1,'IT','ACTIVE'),(2,'Retained','INACTIVE')");
            owner.update(
                    "INSERT INTO app_user(user_id,department_id,full_name,company_email) VALUES(1,1,?,?),(2,NULL,?,?)",
                    workspace + " Admin",
                    workspace + "-admin@example.test",
                    workspace + " Employee",
                    workspace + "-employee@example.test");
            owner.update("INSERT INTO user_role(user_id,role) VALUES(1,'ADMIN')");
        }
        owner("a")
                .update("INSERT INTO app_user(user_id,department_id,full_name,company_email,account_status) VALUES"
                        + " (3,1,'Resolver Manager','resolver@example.test','ACTIVE'),"
                        + " (4,2,'Deactivated','deactivated@example.test','DEACTIVATED'),"
                        + " (9223372036854775807,2,'Large Id','large@example.test','ACTIVE')");
        owner("a").update("INSERT INTO user_role(user_id,role) VALUES(3,'TICKET_RESOLVER'),(3,'PROJECT_MANAGER')");
    }

    @Test
    void readsOnlySqlWorkspaceAndPreservesBigintStrings_AC_004_9_AC_004_10_AC_004_12_AC_004_13_AC_004_18()
            throws Exception {
        http.perform(get("/api/v1/users")
                        .header("Host", "one.localhost")
                        .session(session(1, 1, "EMPLOYEE"))
                        .header("X-Workspace-Id", "2")
                        .param("workspaceId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem("9223372036854775807")))
                .andExpect(jsonPath("$.content[0].lastSignInAt").doesNotExist());
        http.perform(get("/api/v1/users").header("Host", "two.localhost").session(session(2, 1, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath(
                        "$.content[*].fullName", org.hamcrest.Matchers.containsInAnyOrder("b Admin", "b Employee")));
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void filtersBeforePagingAndReturnsRetainedDepartments_AC_004_11_AC_004_13_AC_004_15() throws Exception {
        var session = session(1, 1, "ADMIN");
        http.perform(get("/api/v1/users")
                        .header("Host", "one.localhost")
                        .session(session)
                        .param("q", "  RESOLVER ")
                        .param("role", "PROJECT_MANAGER")
                        .param("departmentId", "1")
                        .param("status", "ACTIVE")
                        .param("size", "1")
                        .param("sort", "email,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath(
                        "$.content[0].roles",
                        org.hamcrest.Matchers.containsInAnyOrder("EMPLOYEE", "TICKET_RESOLVER", "PROJECT_MANAGER")));
        http.perform(get("/api/v1/users")
                        .header("Host", "one.localhost")
                        .session(session)
                        .param("role", "EMPLOYEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
        http.perform(get("/api/v1/users/departments")
                        .header("Host", "one.localhost")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].departmentId").value("2"))
                .andExpect(jsonPath("$[1].name").value("Retained"));
    }

    @Test
    void returnsCodedValidationErrorsAndKeepsBindingClean_AC_004_14_AC_004_18() throws Exception {
        var session = session(1, 1, "ADMIN");
        http.perform(get("/api/v1/users")
                        .header("Host", "one.localhost")
                        .session(session)
                        .param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"))
                .andExpect(jsonPath("$.fieldErrors[0].params.max_length").value(100));
        for (String[] pair : List.of(
                new String[] {"departmentId", "9223372036854775808"},
                new String[] {"sort", "lastSignInAt,desc"},
                new String[] {"size", "101"})) {
            http.perform(get("/api/v1/users")
                            .header("Host", "one.localhost")
                            .session(session)
                            .param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG06"));
            assertThat(context.currentWorkspaceId()).isEmpty();
        }
        http.perform(get("/api/v1/users")
                        .header("Host", "one.localhost")
                        .session(session)
                        .param("page", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(5));
    }

    @Test
    void rejectsMissingUntrustedAndForeignSessionsOnBothReads_AC_004_16_AC_004_18() throws Exception {
        for (String path : List.of("/api/v1/users", "/api/v1/users/departments")) {
            http.perform(get(path).header("Host", "one.localhost"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("MSG07"));
            http.perform(get(path).header("Host", "two.localhost").session(session(1, 1, "ADMIN")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("MSG07"));
            http.perform(get(path).header("Host", "one.localhost").session(session("untrusted", "ADMIN")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("MSG07"));
            http.perform(get(path).header("Host", "one.localhost").session(session(1, 2, "ADMIN")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MSG08"));
        }
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void appliesCurrentRoleAndStatusOnNextRequest_AC_004_16() throws Exception {
        var session = session(1, 1, "EMPLOYEE");
        http.perform(get("/api/v1/users").header("Host", "one.localhost").session(session))
                .andExpect(status().isOk());
        owner("a").update("DELETE FROM user_role WHERE user_id=1");
        http.perform(get("/api/v1/users").header("Host", "one.localhost").session(session))
                .andExpect(status().isForbidden());
        owner("a").update("INSERT INTO user_role(user_id,role) VALUES(1,'ADMIN')");
        http.perform(get("/api/v1/users").header("Host", "one.localhost").session(session))
                .andExpect(status().isOk());
        owner("a").update("UPDATE app_user SET account_status='DEACTIVATED' WHERE user_id=1");
        http.perform(get("/api/v1/users").header("Host", "one.localhost").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void preservesSqlAndDoesNotMutateAccounts_AC_004_19() throws Exception {
        assertThat(schemaHash("/db/changelog/catalog/01_catalog.sql"))
                .isEqualTo("20a5eed02997bc6fdac439bbe662fc704a40d488d94d1cc4d5b2cc69ef7b3c7c");
        assertThat(schemaHash("/db/changelog/tenant/02_tenant.sql"))
                .isEqualTo("f00e971af1402347ae33452e25fffa6d05c982bf015a47a054acb11e46e4d39d");
        var before = owner("a").queryForList("SELECT * FROM app_user ORDER BY user_id");
        var rolesBefore = owner("a").queryForList("SELECT * FROM user_role ORDER BY user_role_id");
        http.perform(get("/api/v1/users").header("Host", "one.localhost").session(session(1, 1, "ADMIN")))
                .andExpect(status().isOk());
        assertThat(owner("a").queryForList("SELECT * FROM app_user ORDER BY user_id"))
                .isEqualTo(before);
        assertThat(owner("a").queryForList("SELECT * FROM user_role ORDER BY user_role_id"))
                .isEqualTo(rolesBefore);
    }

    private static String schemaHash(String resource) throws Exception {
        try (var input = UserListIT.class.getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            String canonical = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static MockHttpSession session(long workspaceId, long accountId, String role) {
        return session(new WorkspaceAccountPrincipal(workspaceId, accountId), role);
    }

    private static MockHttpSession session(Object principal, String role) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private static String url(String database) {
        return DB.getJdbcUrl().replace("/" + DB.getDatabaseName(), "/" + database);
    }

    private static JdbcTemplate owner(String workspace) {
        return SqlBootstrapIT.jdbc(url("users_" + workspace), DB);
    }

    private static void provision() throws Exception {
        var owner = SqlBootstrapIT.jdbc(DB.getJdbcUrl(), DB);
        owner.execute("CREATE ROLE users_catalog_reader LOGIN PASSWORD 'test-only-catalog'");
        owner.execute("CREATE ROLE users_a_reader LOGIN PASSWORD 'test-only-a'");
        owner.execute("CREATE ROLE users_b_reader LOGIN PASSWORD 'test-only-b'");
        for (String database : List.of("users_catalog", "users_a", "users_b"))
            owner.execute("CREATE DATABASE " + database);
        SqlBootstrapIT.migrate(url("users_catalog"), DB, "catalog");
        for (String workspace : List.of("a", "b")) SqlBootstrapIT.migrate(url("users_" + workspace), DB, "tenant");
        for (String database : List.of("users_catalog", "users_a", "users_b")) {
            var jdbc = SqlBootstrapIT.jdbc(url(database), DB);
            jdbc.execute("REVOKE CONNECT ON DATABASE " + database + " FROM PUBLIC");
            jdbc.execute("GRANT CONNECT ON DATABASE " + database + " TO " + database + "_reader");
            jdbc.execute("GRANT USAGE ON SCHEMA public TO " + database + "_reader");
            jdbc.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO " + database + "_reader");
        }
        var catalog = SqlBootstrapIT.jdbc(url("users_catalog"), DB);
        for (String workspace : List.of("a", "b")) {
            catalog.update(
                    "INSERT INTO tenant_workspace(site_name,db_host,db_port,db_name,db_username,db_password_ref) VALUES(?,?,?,?,?,?)",
                    workspace.equals("a") ? "one" : "two",
                    DB.getHost(),
                    DB.getFirstMappedPort(),
                    "users_" + workspace,
                    "users_" + workspace + "_reader",
                    workspace.equals("a") ? "USERS_A_PASSWORD" : "USERS_B_PASSWORD");
        }
    }
}
