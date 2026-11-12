package vn.tsolve.identity.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.callbackRequest;
import static vn.tsolve.identity.session.SessionTestFixtures.claimsOf;
import static vn.tsolve.identity.session.SessionTestFixtures.cookieValue;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.dev.DevAccounts.DevAccount;
import vn.tsolve.identity.session.SessionTestFixtures;
import vn.tsolve.identity.session.WhoAmITestController;
import vn.tsolve.identity.signin.OidcCallbackHandler;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * AC-001.26 end to end in the dev profile (Q3 = A): a seeded dev Account signs in through the real callback handler
 * with test claims, and the session it gets shows the seeded Roles on the whoami endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TwoTenantDatabases.class, WhoAmITestController.class})
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class DevSessionWhoAmITest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OidcCallbackHandler handler;

    @Autowired
    private DevAccountSeeder seeder;

    @Autowired
    private WorkspaceLookup workspaces;

    @Autowired
    private ObjectMapper json;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void seedFresh() throws Exception {
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
        seeder.run(null);
    }

    @Test
    @DisplayName("AC-001.26 a seeded dev Account signed in through the callback shows its seeded Roles on whoami")
    void aSeededAccountShowsItsRoles_AC_001_26() throws Exception {
        DevAccount alice = DevAccounts.all().get(0);
        WorkspaceRef acme = workspaces.findById(alice.workspaceId()).orElseThrow();
        MockHttpServletRequest callback = callbackRequest(acme.siteHost());
        MockHttpServletResponse signedIn = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(callback, signedIn, SignInTestFixtures.signedIn(claimsOf(acme, alice.email())));

        String cookie = cookieValue(signedIn, SESSION_COOKIE);
        assertThat(cookie).as("session cookie of the sign-in").isNotNull();
        MockHttpServletResponse response = mvc.perform(
                        get(url(acme.siteHost(), "/api/v1/test/whoami")).cookie(new Cookie(SESSION_COOKIE, cookie)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode user = json.readTree(response.getContentAsString());
        assertThat(user.path("email").asText()).isEqualTo(alice.email());
        List<String> roles = new ArrayList<>();
        user.path("roles").forEach(node -> roles.add(node.asText()));
        Set<String> expected = new TreeSet<>(DevAccountSeederIT.seededRoles(alice));
        expected.add("EMPLOYEE");
        assertThat(roles).containsExactlyElementsOf(expected);
        assertThat(roles).as("alice is more than an Employee").hasSizeGreaterThan(1);
    }
}
