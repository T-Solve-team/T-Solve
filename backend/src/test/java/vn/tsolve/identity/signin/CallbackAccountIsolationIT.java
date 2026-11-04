package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * The callback against two real company databases: the Account lookup and the sign-in update reach only the
 * database of the state's Workspace (AC-001.15, edge 24). The fake Google is WireMock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TwoTenantDatabases.class, CallbackAccountIsolationIT.Workspaces.class})
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class CallbackAccountIsolationIT {

    private static final String EMAIL = "lan.tran@acme.example";
    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;

    @TestConfiguration(proxyBeanMethods = false)
    static class Workspaces {

        @Bean
        @Primary
        InMemoryWorkspaceLookup inMemoryWorkspaceLookup() {
            return new InMemoryWorkspaceLookup();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SignInStateService states;

    @DynamicPropertySource
    static void fakeGoogle(DynamicPropertyRegistry registry) {
        registry.add("tsolve.oidc.google.token-uri", FakeGoogleIssuer::tokenUri);
        registry.add("tsolve.oidc.google.jwk-set-uri", FakeGoogleIssuer::jwksUri);
    }

    @BeforeEach
    void reset() {
        FakeGoogleIssuer.reset();
        TwoTenantDatabases.tenantA().update("DELETE FROM app_user");
        TwoTenantDatabases.tenantB().update("DELETE FROM app_user");
        for (JdbcTemplate db : new JdbcTemplate[] {
            TwoTenantDatabases.main(), TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB()
        }) {
            db.update("DELETE FROM hand_off_code");
        }
    }

    private void seed(JdbcTemplate db) {
        db.update(
                "INSERT INTO app_user (email, full_name, status, created_at) VALUES (?, 'Old Name', 'ACTIVE', now())",
                EMAIL);
    }

    private MockHttpServletResponse signIn(WorkspaceRef workspace, String hd) throws Exception {
        String state = states.createForWorkspace(workspace.id(), "/");
        SignInState row = states.check(state).orElseThrow();
        MockHttpServletResponse authorize = mvc.perform(
                        get("http://login.localhost:8081/api/v1/auth/authorize").param("state", state))
                .andReturn()
                .getResponse();
        String cookie = SignInTestFixtures.signInCookieValue(authorize);
        assertThat(cookie).as("the sign-in cookie that authorize sets").isNotNull();
        FakeGoogleIssuer.answerToken(
                FakeGoogleIssuer.idToken(Instant.now(), row.getNonce(), claims -> claims.put("hd", hd)));
        return mvc.perform(get("http://login.localhost:8081/api/v1/auth/callback")
                        .param("state", state)
                        .param("code", "4/0A-the-authorization-code")
                        .cookie(new Cookie(SignInTestFixtures.SIGN_IN_COOKIE, cookie)))
                .andReturn()
                .getResponse();
    }

    private static int count(JdbcTemplate db, String table) {
        return db.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    @DisplayName(
            "AC-001.15 / edge 24 Workspace B's Account email signing in on Workspace A gets MSG09 and B's Account is untouched")
    void workspaceBsAccountEmailOnWorkspaceAGetsMsg09_AC_001_15_edge24() throws Exception {
        seed(TwoTenantDatabases.tenantB());

        MockHttpServletResponse response = signIn(ACME, "acme.example");

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("http://acme.localhost:8081/login?error=MSG09");
        Map<String, Object> inB = TwoTenantDatabases.tenantB()
                .queryForMap("SELECT full_name, first_sign_in_at, last_sign_in_at FROM app_user");
        assertThat(inB.get("full_name")).isEqualTo("Old Name");
        assertThat(inB.get("first_sign_in_at")).isNull();
        assertThat(inB.get("last_sign_in_at")).isNull();
        assertThat(count(TwoTenantDatabases.tenantA(), "app_user")).isZero();
        assertThat(count(TwoTenantDatabases.main(), "hand_off_code")).isZero();
        assertThat(count(TwoTenantDatabases.tenantA(), "hand_off_code")).isZero();
        assertThat(count(TwoTenantDatabases.tenantB(), "hand_off_code")).isZero();
    }

    @Test
    @DisplayName("AC-001.15 the same email signs in on its own Workspace B, written in B's database only")
    void theSameEmailSignsInOnItsOwnWorkspaceB_AC_001_15() throws Exception {
        seed(TwoTenantDatabases.tenantB());

        MockHttpServletResponse response = signIn(BETA, "beta.example");

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION))
                .startsWith("http://beta.localhost:8081/api/v1/auth/complete?code=");
        Map<String, Object> inB = TwoTenantDatabases.tenantB()
                .queryForMap("SELECT full_name, first_sign_in_at, last_sign_in_at FROM app_user");
        assertThat(inB.get("full_name")).isEqualTo("Lan Tran");
        assertThat(inB.get("first_sign_in_at")).isNotNull();
        assertThat(inB.get("last_sign_in_at")).isNotNull();
        assertThat(count(TwoTenantDatabases.tenantA(), "app_user")).isZero();
        // the code belongs to the registry stand-in (the application's own database), not to a company database
        assertThat(count(TwoTenantDatabases.main(), "hand_off_code")).isEqualTo(1);
        assertThat(count(TwoTenantDatabases.tenantA(), "hand_off_code")).isZero();
        assertThat(count(TwoTenantDatabases.tenantB(), "hand_off_code")).isZero();
    }
}
