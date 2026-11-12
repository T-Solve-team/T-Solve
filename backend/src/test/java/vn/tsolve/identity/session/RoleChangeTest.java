package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * AC-001.26: the Roles come from {@code user_role} of the session's Workspace at every request, so a change applies
 * at the next request of the same session. Sessions are made with {@link WebSessionStore#create}.
 */
@Import(WhoAmITestController.class)
class RoleChangeTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;
    private static final String MSG10_TEXT = "Your T-Solve account has been deactivated. Please contact your Admin.";

    @Autowired
    private WebSessionStore store;

    @Autowired
    private ObjectMapper json;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void cleanTenants() {
        WhoAmITestController.reset();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
    }

    // ---- helpers

    private static JdbcTemplate dbOf(WorkspaceRef workspace) {
        return workspace.id().equals(ACME.id()) ? TwoTenantDatabases.tenantA() : TwoTenantDatabases.tenantB();
    }

    private UUID seed(WorkspaceRef workspace, UUID id, String email) {
        dbOf(workspace)
                .update(
                        "INSERT INTO app_user (id, email, full_name, status, created_at)"
                                + " VALUES (?, ?, 'Someone', 'ACTIVE', now())",
                        id,
                        email);
        return id;
    }

    private void addRole(WorkspaceRef workspace, UUID account, String role) {
        dbOf(workspace)
                .update("INSERT INTO user_role (user_id, role, assigned_at) VALUES (?, ?, now())", account, role);
    }

    private String sessionOf(WorkspaceRef workspace, UUID account) {
        return store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                workspace.id(),
                account,
                null,
                workspace.siteHost(),
                "id-token-hint",
                clock.instant(),
                clock.instant()));
    }

    private MockHttpServletResponse whoami(WorkspaceRef workspace, String cookie) throws Exception {
        return perform(
                get(url(workspace.siteHost(), "/api/v1/test/whoami")).cookie(new Cookie(SESSION_COOKIE, cookie)));
    }

    private List<String> rolesOf(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as("status").isEqualTo(200);
        JsonNode roles = json.readTree(response.getContentAsString()).path("roles");
        List<String> names = new ArrayList<>();
        roles.forEach(node -> names.add(node.asText()));
        return names;
    }

    // ---- tests

    @Test
    @DisplayName("AC-001.26 the endpoint sees the Account's Roles from user_role plus the implicit Employee")
    void rolesFromUserRolePlusEmployee_AC_001_26() throws Exception {
        UUID admin = seed(ACME, UUID.randomUUID(), "admin@acme.example");
        addRole(ACME, admin, "ADMIN");
        addRole(ACME, admin, "KNOWLEDGE_MANAGER");
        UUID plain = seed(ACME, UUID.randomUUID(), "plain@acme.example");

        assertThat(rolesOf(whoami(ACME, sessionOf(ACME, admin))))
                .containsExactly("ADMIN", "EMPLOYEE", "KNOWLEDGE_MANAGER");
        assertThat(rolesOf(whoami(ACME, sessionOf(ACME, plain)))).containsExactly("EMPLOYEE");
    }

    @Test
    @DisplayName("AC-001.26 a Role added and a Role removed in the database apply at the next request of the same"
            + " session")
    void roleChangesApplyAtTheNextRequest_AC_001_26() throws Exception {
        UUID account = seed(ACME, UUID.randomUUID(), "a@acme.example");
        addRole(ACME, account, "ADMIN");
        String cookie = sessionOf(ACME, account);
        assertThat(rolesOf(whoami(ACME, cookie))).containsExactly("ADMIN", "EMPLOYEE");

        TwoTenantDatabases.tenantA().update("DELETE FROM user_role WHERE user_id = ? AND role = 'ADMIN'", account);
        addRole(ACME, account, "TICKET_RESOLVER");
        assertThat(rolesOf(whoami(ACME, cookie))).containsExactly("EMPLOYEE", "TICKET_RESOLVER");

        TwoTenantDatabases.tenantA().update("DELETE FROM user_role WHERE user_id = ?", account);
        assertThat(rolesOf(whoami(ACME, cookie))).containsExactly("EMPLOYEE");
        assertThat(redis.hasKey(keyOf(cookie))).isTrue();
    }

    @Test
    @DisplayName("AC-001.26 the Roles are read in the session's Workspace database")
    void rolesAreReadInTheSessionsWorkspace_AC_001_26() throws Exception {
        UUID shared = UUID.randomUUID();
        seed(ACME, shared, "same@acme.example");
        seed(BETA, shared, "same@beta.example");
        addRole(ACME, shared, "ADMIN");

        assertThat(rolesOf(whoami(ACME, sessionOf(ACME, shared)))).containsExactly("ADMIN", "EMPLOYEE");
        assertThat(rolesOf(whoami(BETA, sessionOf(BETA, shared)))).containsExactly("EMPLOYEE");
    }

    @Test
    @DisplayName("edge 32 an Account with ADMIN Deactivated between two requests: next request 401 MSG10, endpoint not"
            + " run, session ended")
    void deactivatedAdminIsRefused_edge32() throws Exception {
        UUID account = seed(ACME, UUID.randomUUID(), "admin@acme.example");
        addRole(ACME, account, "ADMIN");
        String cookie = sessionOf(ACME, account);
        assertThat(rolesOf(whoami(ACME, cookie))).containsExactly("ADMIN", "EMPLOYEE");
        int callsBefore = WhoAmITestController.calls();

        TwoTenantDatabases.tenantA().update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", account);
        MockHttpServletResponse response = whoami(ACME, cookie);

        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = json.readTree(response.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("MSG10");
        assertThat(body.path("detail").asText()).isEqualTo(MSG10_TEXT);
        assertThat(WhoAmITestController.calls()).isEqualTo(callsBefore);
        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE)).hasSize(1);
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE).get(0))
                .startsWith(SESSION_COOKIE + "=;");
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject("SELECT count(*) FROM user_role WHERE user_id = ?", Integer.class, account))
                .isEqualTo(1);
    }
}
