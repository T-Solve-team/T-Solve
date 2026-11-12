package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static vn.tsolve.identity.session.SessionTestFixtures.cookieValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.identity.session.AbstractSessionIT;
import vn.tsolve.identity.session.SessionTestFixtures;

/**
 * Task 29a: the start of the Platform Console sign-in on {@code admin.} (AC-001.28). The whole application over a real
 * Redis; Keycloak is {@link FakeKeycloakRealm}, which also serves the discovery document of the realm
 * {@code tsolve-ops}, so the start-up check of both realms passes. The callback part comes with Task 29b.
 */
class OperatorSignInTest extends AbstractSessionIT {

    private static final FakeKeycloakRealm FAKE = new FakeKeycloakRealm();
    private static boolean fakeStarted;

    private static final String ADMIN_CALLBACK = SignInTestFixtures.ADMIN_ORIGIN + "/api/v1/auth/callback";
    private static final String OPS_AUTH_ENDPOINT = "/realms/tsolve-ops/protocol/openid-connect/auth";
    private static final String AUTHREQ_KEYS = RedisAuthorizationRequestRepository.KEY_PREFIX + "*";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
        synchronized (OperatorSignInTest.class) {
            if (!fakeStarted) {
                FAKE.start();
                fakeStarted = true;
            }
        }
        // wins over the fixed origin of AbstractSessionIT: both realms are served by the fake on this origin
        registry.add("TSOLVE_LOGIN_ORIGIN", FAKE::origin);
    }

    @AfterAll
    static void stopFake() {
        FAKE.stop();
        fakeStarted = false;
    }

    private final ObjectMapper json = new ObjectMapper();

    // ---- helpers

    private static MockHttpServletRequestBuilder start(String returnTo) {
        MockHttpServletRequestBuilder request = get(SignInTestFixtures.ADMIN_ORIGIN + "/api/v1/auth/sign-in");
        if (returnTo != null) {
            request.param("returnTo", returnTo);
        }
        return request;
    }

    /** The one saved authorization request, read from Redis by the key the cookie names. */
    private JsonNode savedRequest(MockHttpServletResponse response) throws Exception {
        String cookie = cookieValue(response, RedisAuthorizationRequestRepository.COOKIE_NAME);
        assertThat(cookie).as("auth-request cookie").isNotNull();
        Set<String> keys = redis.keys(AUTHREQ_KEYS);
        String key = RedisAuthorizationRequestRepository.KEY_PREFIX + AuthorizationUris.sha256Hex(cookie);
        assertThat(keys).as("authorization requests in Redis").containsExactly(key);
        return json.readTree(redis.opsForValue().get(key));
    }

    // ---- tests

    @Test
    @DisplayName("AC-001.28 starts a Console sign-in against the realm tsolve-ops for the client tsolve-console")
    void startsAConsoleSignInAgainstTheOpsRealm_AC_001_28() throws Exception {
        MockHttpServletResponse response = perform(start("/workspaces"));

        assertThat(response.getStatus()).as("status").isEqualTo(302);
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(location).as("Location").startsWith(FAKE.origin() + OPS_AUTH_ENDPOINT + "?");
        assertThat(AuthorizationUris.one(location, "client_id")).isEqualTo("tsolve-console");
        assertThat(AuthorizationUris.one(location, "response_type")).isEqualTo("code");
        assertThat(AuthorizationUris.one(location, "code_challenge_method")).isEqualTo("S256");
        assertThat(AuthorizationUris.one(location, "redirect_uri")).isEqualTo(ADMIN_CALLBACK);
        assertThat(AuthorizationUris.params(location)).as("no tenant hint").doesNotContainKey("kc_idp_hint");

        JsonNode saved = savedRequest(response);
        assertThat(AuthorizationUris.one(location, "state"))
                .isEqualTo(saved.path("state").asText());
        JsonNode attributes = saved.path("attributes");
        assertThat(AuthorizationUris.one(location, "code_challenge"))
                .isEqualTo(AuthorizationUris.sha256Base64Url(
                        attributes.path("code_verifier").asText()));
        assertThat(AuthorizationUris.one(location, "nonce"))
                .isEqualTo(AuthorizationUris.sha256Base64Url(
                        attributes.path("nonce").asText()));
        assertThat(attributes.path("target").asText()).isEqualTo("PLATFORM_CONSOLE");
        assertThat(attributes.has("workspace_id")).as("no workspace_id").isFalse();
        assertThat(attributes.path("return_to").asText()).isEqualTo("/workspaces");

        java.util.List<String> cookies =
                SignInTestFixtures.setCookie(response, RedisAuthorizationRequestRepository.COOKIE_NAME);
        assertThat(cookies).hasSize(1);
        assertThat(SignInTestFixtures.attributes(cookies.get(0)))
                .containsEntry("path", "/")
                .containsEntry("max-age", "600")
                .containsKeys("secure", "httponly")
                .doesNotContainKey("domain");
        assertThat(SignInTestFixtures.attributes(cookies.get(0)).get("samesite"))
                .isEqualToIgnoringCase("Lax");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    static Stream<Arguments> steeringInputs() {
        return Stream.of(
                // a proxy that keeps the admin host name but changes the port or the scheme
                Arguments.of("X-Forwarded-Host", "admin.localhost:9999", "/workspaces", "/workspaces"),
                Arguments.of("X-Forwarded-Proto", "https", "/workspaces", "/workspaces"),
                Arguments.of("X-Forwarded-Port", "9999", "/workspaces", "/workspaces"),
                Arguments.of(null, null, "//evil.example", "/"),
                Arguments.of(null, null, "https://evil.example", "/"));
    }

    @ParameterizedTest(name = "[{index}] {0} {1} returnTo={2}")
    @MethodSource("steeringInputs")
    @DisplayName("AC-001.28 edge 138 takes the redirect URI from TSOLVE_ADMIN_ORIGIN, not from the request")
    void takesTheRedirectUriFromTheConfiguredOrigin_AC_001_28_edge138(
            String header, String value, String returnTo, String keptReturnTo) throws Exception {
        MockHttpServletRequestBuilder request = start(returnTo);
        if (header != null) {
            request.header(header, value);
        }

        MockHttpServletResponse response = perform(request);

        assertThat(response.getStatus()).as("status").isEqualTo(302);
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(FAKE.origin() + OPS_AUTH_ENDPOINT + "?");
        assertThat(AuthorizationUris.one(location, "redirect_uri")).isEqualTo(ADMIN_CALLBACK);
        assertThat(savedRequest(response).path("attributes").path("return_to").asText())
                .isEqualTo(keptReturnTo);
    }
}
