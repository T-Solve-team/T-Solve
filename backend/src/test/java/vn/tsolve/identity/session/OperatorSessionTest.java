package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.TenantScope;

/**
 * Task 29b: what an Operator session is allowed on {@code admin.} and refused elsewhere (AC-001.29, .32, .33, .34,
 * AC-002.1 and .2 on {@code admin.}). The sessions are made straight in Redis; the allow-list of {@link
 * AbstractSessionIT} is {@code ops@example.test}. The tests import no production type that is new in this task: the
 * probe reports the principal's class by its simple name.
 */
@Import({OperatorProbeTestController.class, SessionEndedRecorder.class})
class OperatorSessionTest extends AbstractSessionIT {

    private static final String ADMIN = "admin.localhost";
    private static final String ACME_HOST = InMemoryWorkspaceLookup.ACME.siteHost();
    private static final String BETA_HOST = InMemoryWorkspaceLookup.BETA.siteHost();
    private static final String PROBE = "/api/v1/test/operator";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String OPS = "ops@example.test";
    private static final String FORMER = "former@example.test";
    private static final String BACKGROUND = WebSessionFilter.BACKGROUND_HEADER;
    private static final String MSG123_TEXT = "Your session is not valid on this site. Please log in again.";
    private static final String MSG135_TEXT = "You do not have permission to access the Platform Console.";

    @Autowired
    private WebSessionStore store;

    @Autowired
    private ObjectMapper json;

    @SpyBean
    private TenantScope scope;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void cleanUp() {
        OperatorProbeTestController.reset();
        SessionEndedRecorder.reset();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
    }

    // ---- helpers

    private String operatorSession(String email, String hint) {
        return store.create(new WebSession(
                WebSession.Kind.OPERATOR, null, null, email, ADMIN, hint, clock.instant(), clock.instant()));
    }

    private MockHttpServletRequestBuilder probeRequest(String host, String cookie) {
        return get(url(host, PROBE)).cookie(new Cookie(SESSION_COOKIE, cookie));
    }

    private MockHttpServletResponse probe(String host, String cookie) throws Exception {
        return perform(probeRequest(host, cookie));
    }

    private MockHttpServletResponse backgroundProbe(String host, String cookie) throws Exception {
        return perform(probeRequest(host, cookie).header(BACKGROUND, "1"));
    }

    /** Moves the clock, then sends a foreground probe on admin. that must be answered 200. */
    private void stay(String cookie, long minutes) throws Exception {
        clock.advance(Duration.ofMinutes(minutes));
        assertThat(probe(ADMIN, cookie).getStatus())
                .as("after %d more minutes", minutes)
                .isEqualTo(200);
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private void assertProblem(MockHttpServletResponse response, int status, String code, String detail)
            throws Exception {
        assertThat(response.getStatus()).as("status").isEqualTo(status);
        assertThat(body(response).path("code").asText()).as("code").isEqualTo(code);
        if (detail != null) {
            assertThat(body(response).path("detail").asText()).as("detail").isEqualTo(detail);
        }
    }

    private static void assertSessionCookieExpired(MockHttpServletResponse response) {
        List<String> headers = SignInTestFixtures.setCookie(response, SESSION_COOKIE);
        assertThat(headers).as("Set-Cookie of %s", SESSION_COOKIE).hasSize(1);
        assertThat(headers.get(0)).startsWith(SESSION_COOKIE + "=;");
        Map<String, String> attributes = SignInTestFixtures.attributes(headers.get(0));
        assertThat(attributes).containsEntry("max-age", "0").containsEntry("path", "/");
        assertThat(attributes).containsKey("secure").doesNotContainKey("domain");
    }

    private boolean keyExists(String cookie) {
        return Boolean.TRUE.equals(redis.hasKey(keyOf(cookie)));
    }

    // ---- tests

    @ParameterizedTest(name = "[{index}] stored email {0}")
    @ValueSource(strings = {OPS, "OPS@EXAMPLE.TEST"})
    @DisplayName("AC-001.29 serves an Operator session on admin. with no Account, no Workspace and none of the five"
            + " Roles")
    void servesAnOperatorSessionWithNoAccountNoWorkspaceAndNoRole_AC_001_29(String email) throws Exception {
        String cookie = operatorSession(email, "ops-hint");

        MockHttpServletResponse response = probe(ADMIN, cookie);

        assertThat(response.getStatus()).as("status").isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.path("principalClass").asText()).isEqualTo("OperatorPrincipal");
        assertThat(body.path("name").asText()).isEqualTo(email);
        List<String> authorities = new ArrayList<>();
        body.path("authorities").forEach(node -> authorities.add(node.asText()));
        for (Role role : Role.values()) {
            assertThat(authorities).doesNotContain(role.name(), "ROLE_" + role.name());
        }
        assertThat(body.path("workspaceBound").asBoolean(true))
                .as("a Workspace is bound")
                .isFalse();
        verify(scope, never()).runIn(any(), any());
        assertThat(OperatorProbeTestController.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-001.32 edge 29 refuses an Operator session on both Workspace sites with MSG123")
    void refusesAnOperatorSessionOnBothWorkspaceSites_AC_001_32_edge29() throws Exception {
        String cookie = operatorSession(OPS, "ops-hint");
        String former = operatorSession(FORMER, "former-hint");

        for (String host : List.of(ACME_HOST, BETA_HOST)) {
            MockHttpServletResponse response = probe(host, cookie);

            assertProblem(response, 401, "MSG123", MSG123_TEXT);
            assertThat(keyExists(cookie)).as("session kept on " + host).isTrue();
            assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE)).isEmpty();
        }
        // 29-QA Q6: an email that is not on the list gets MSG123 on a Workspace site too, and its session is kept
        MockHttpServletResponse formerOnAcme = probe(ACME_HOST, former);
        assertProblem(formerOnAcme, 401, "MSG123", MSG123_TEXT);
        assertThat(keyExists(former)).isTrue();
        assertThat(SignInTestFixtures.setCookie(formerOnAcme, SESSION_COOKIE)).isEmpty();
        assertThat(OperatorProbeTestController.calls()).isZero();
        verify(scope, never()).runIn(any(), any());

        // the same cookie on admin. is not refused as a foreign site
        MockHttpServletResponse onAdmin = probe(ADMIN, cookie);
        assertThat(onAdmin.getStatus()).as("allow-listed cookie on admin.").isEqualTo(200);
        assertThat(body(onAdmin).path("name").asText()).isEqualTo(OPS);
    }

    @ParameterizedTest(name = "[{index}] background={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("AC-001.33 ends an Operator session whose email is no longer in the allow-list with MSG135")
    void endsASessionWhoseEmailLeftTheAllowList_AC_001_33(boolean background) throws Exception {
        String cookie = operatorSession(FORMER, "former-hint");

        MockHttpServletResponse response = background ? backgroundProbe(ADMIN, cookie) : probe(ADMIN, cookie);

        assertProblem(response, 401, "MSG135", MSG135_TEXT);
        assertThat(keyExists(cookie)).as("session key kept").isFalse();
        assertSessionCookieExpired(response);
        assertThat(OperatorProbeTestController.calls()).isZero();
        assertThat(SessionEndedRecorder.all()).as("SessionEnded events").isEmpty();

        MockHttpServletResponse next = probe(ADMIN, cookie);
        assertProblem(next, 401, "MSG07", null);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"foreground requests 59 minutes apart", "61 minutes idle", "background requests only"})
    @DisplayName("AC-001.34 applies the 60-minute idle limit to Operator sessions and does not count background"
            + " requests")
    void appliesTheIdleLimitToOperatorSessions_AC_001_34(String scenario) throws Exception {
        String cookie = operatorSession(OPS, "ops-hint");

        switch (scenario) {
            case "foreground requests 59 minutes apart" -> {
                stay(cookie, 59);
                stay(cookie, 59);
                stay(cookie, 59);
            }
            case "61 minutes idle" -> {
                clock.advance(Duration.ofMinutes(61));
                MockHttpServletResponse response = probe(ADMIN, cookie);
                assertProblem(response, 401, "MSG07", null);
                assertThat(keyExists(cookie)).isFalse();
                assertSessionCookieExpired(response);
            }
            default -> {
                clock.advance(Duration.ofMinutes(30));
                assertThat(backgroundProbe(ADMIN, cookie).getStatus()).isEqualTo(200);
                clock.advance(Duration.ofMinutes(29));
                assertThat(backgroundProbe(ADMIN, cookie).getStatus()).isEqualTo(200);
                clock.advance(Duration.ofMinutes(2)); // 61 minutes after the last foreground request
                MockHttpServletResponse response = backgroundProbe(ADMIN, cookie);
                assertProblem(response, 401, "MSG07", null);
                assertThat(keyExists(cookie)).isFalse();
                assertSessionCookieExpired(response);
            }
        }
    }

    @ParameterizedTest(name = "[{index}] last request in the background={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("AC-001.34 applies the 8-hour limit to Operator sessions")
    void appliesTheEightHourLimitToOperatorSessions_AC_001_34(boolean background) throws Exception {
        String cookie = operatorSession(OPS, "ops-hint");
        for (int i = 0; i < 8; i++) {
            stay(cookie, 59);
        }
        stay(cookie, 8); // exactly 480 minutes
        clock.advance(Duration.ofMinutes(1)); // 481 minutes old, idle 1
        int callsBefore = OperatorProbeTestController.calls();

        MockHttpServletResponse response = background ? backgroundProbe(ADMIN, cookie) : probe(ADMIN, cookie);

        assertProblem(response, 401, "MSG07", null);
        assertThat(keyExists(cookie)).isFalse();
        assertThat(OperatorProbeTestController.calls()).isEqualTo(callsBefore);
    }

    @Test
    @DisplayName("AC-001.29 edge 141 gives an Operator session no Workspace data on admin.")
    void givesAnOperatorSessionNoWorkspaceData_AC_001_29_edge141() throws Exception {
        TwoTenantDatabases.tenantA()
                .update("INSERT INTO app_user (email, full_name, status, created_at)"
                        + " VALUES ('lan@acme.example', 'Acme Lan', 'ACTIVE', now())");
        TwoTenantDatabases.tenantB()
                .update("INSERT INTO app_user (email, full_name, status, created_at)"
                        + " VALUES ('lan@beta.example', 'Beta Lan', 'ACTIVE', now())");
        String cookie = operatorSession(OPS, "ops-hint");

        MockHttpServletResponse response =
                perform(get(url(ADMIN, "/api/v1/users")).cookie(new Cookie(SESSION_COOKIE, cookie)));

        assertThat(response.getStatus()).as("status").isEqualTo(403);
        assertThat(body(response).path("code").asText()).isEqualTo("MSG08");
        verify(scope, never()).runIn(any(), any());
        assertThat(response.getContentAsString())
                .doesNotContain("lan@acme.example")
                .doesNotContain("lan@beta.example");
    }

    @Test
    @DisplayName("AC-002.1 an Operator logs out on admin.")
    void anOperatorLogsOutOnAdmin_AC_002_1() throws Exception {
        String cookie = operatorSession(OPS, "ops-hint");

        MockHttpServletResponse response =
                perform(post(url(ADMIN, LOGOUT)).with(csrf()).cookie(new Cookie(SESSION_COOKIE, cookie)));

        assertThat(response.getStatus()).as("status").isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.fieldNames()).toIterable().containsExactly("logoutUrl");
        URI uri = URI.create(body.path("logoutUrl").asText());
        assertThat(uri.getScheme() + "://" + uri.getAuthority() + uri.getPath())
                .isEqualTo(SignInTestFixtures.LOGIN_ORIGIN + "/realms/tsolve-ops/protocol/openid-connect/logout");
        Map<String, String> query =
                UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap().entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, e -> URLDecoder.decode(e.getValue(), StandardCharsets.UTF_8)));
        assertThat(query)
                .containsOnlyKeys("client_id", "id_token_hint", "post_logout_redirect_uri")
                .containsEntry("client_id", "tsolve-console")
                .containsEntry("id_token_hint", "ops-hint")
                .containsEntry("post_logout_redirect_uri", SignInTestFixtures.ADMIN_ORIGIN + "/login");
        assertThat(keyExists(cookie)).as("session key kept").isFalse();
        assertSessionCookieExpired(response);
        assertThat(SessionEndedRecorder.all()).as("SessionEnded events").isEmpty();

        // AC-002.2: the old cookie is dead
        MockHttpServletResponse again = probe(ADMIN, cookie);
        assertProblem(again, 401, "MSG07", null);
        assertThat(OperatorProbeTestController.calls()).isZero();
    }
}
