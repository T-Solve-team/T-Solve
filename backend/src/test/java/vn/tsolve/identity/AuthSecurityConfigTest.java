package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.identity.signin.AuthHosts;
import vn.tsolve.identity.signin.KeycloakClientRegistrations;
import vn.tsolve.identity.signin.OidcCallbackHandler;
import vn.tsolve.identity.signin.RedisAuthorizationRequestRepository;
import vn.tsolve.identity.signin.SignInTestBeans;
import vn.tsolve.identity.signin.SignInTestFixtures;

/**
 * Adding the chain for the four redirect paths turns off Spring Boot's default chain; this shows the rest of the
 * API still needs a signed-in user (design row 43: 401, no form login, no HTTP Basic).
 */
@WebMvcTest(AuthSecurityConfigTest.ProbeController.class)
@Import({
    SecurityConfig.class,
    AuthSecurityConfig.class,
    AuthHosts.class,
    SignInTestBeans.class,
    KeycloakClientRegistrations.class,
    RedisAuthorizationRequestRepository.class,
    OidcCallbackHandler.class,
    AuthSecurityConfigTest.ProbeController.class
})
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "KEYCLOAK_WEB_CLIENT_SECRET=test-keycloak-secret",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0"
        })
class AuthSecurityConfigTest {

    /** Test-only endpoints outside the four redirect paths; counts the calls that got through. */
    @RestController
    static class ProbeController {

        static final AtomicInteger CALLS = new AtomicInteger();

        @GetMapping({"/api/v1/probe", "/api/v1/auth/sign-in/extra", "/api/v1/auth/callback"})
        String probe() {
            CALLS.incrementAndGet();
            return "reached";
        }
    }

    private static final String COOKIE = RedisAuthorizationRequestRepository.COOKIE_NAME;
    private static final String CALLBACK = "/api/v1/auth/callback";

    @Autowired
    private MockMvc mvc;

    /** The Redis the callback's repository reads; no test here talks to a real one. */
    @MockBean
    private StringRedisTemplate redis;

    private ValueOperations<String, String> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetCalls() {
        ProbeController.CALLS.set(0);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"/api/v1/probe", "/api/v1/auth/sign-in/extra"})
    @DisplayName("edge 43 keeps every path outside the four redirect paths behind authentication")
    void keepsOtherPathsBehindAuthentication_edge43(String path) throws Exception {
        // a browser (text/html) must not be sent to a login form either
        for (String accept : List.of(MediaType.ALL_VALUE, MediaType.TEXT_HTML_VALUE)) {
            MockHttpServletResponse response =
                    mvc.perform(get(path).accept(accept)).andReturn().getResponse();

            assertThat(response.getStatus()).as("status for Accept %s", accept).isEqualTo(401);
            assertThat(response.getHeader(HttpHeaders.LOCATION))
                    .as("redirect to a login form for Accept %s", accept)
                    .isNull();
            assertThat(response.getHeaders(HttpHeaders.WWW_AUTHENTICATE))
                    .as("HTTP Basic challenge for Accept %s", accept)
                    .noneMatch(challenge -> challenge.toLowerCase(Locale.ROOT).startsWith("basic"));
        }
        assertThat(ProbeController.CALLS).as("controller calls").hasValue(0);
    }

    @Test
    @WithMockUser
    @DisplayName("edge 43 lets a signed-in user reach a path outside the four")
    void letsSignedInUserReachOtherPaths_edge43() throws Exception {
        MockHttpServletResponse response =
                mvc.perform(get("/api/v1/probe")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(ProbeController.CALLS).hasValue(1);
    }

    /** Exactly one expiring auth-request cookie with the __Host- attributes and no Domain. */
    private static void assertExpiryCookie(MockHttpServletResponse response) {
        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(COOKIE + "="))
                .toList();
        assertThat(headers).as("Set-Cookie of %s", COOKIE).hasSize(1);
        assertThat(headers.get(0)).startsWith(COOKIE + "=;");
        Map<String, String> attributes = SignInTestFixtures.attributes(headers.get(0));
        assertThat(attributes).containsEntry("max-age", "0").containsEntry("path", "/");
        assertThat(attributes).containsKeys("secure", "httponly");
        assertThat(attributes.get("samesite")).isEqualToIgnoringCase("Lax");
        assertThat(attributes).doesNotContainKey("domain");
    }

    private static void assertNoSession(MockHttpServletResponse response) {
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .as("Set-Cookie")
                .noneMatch(header -> header.contains("JSESSIONID"));
    }

    @Test
    @DisplayName("AC-001.55 answers a Workspace callback without an HTTP session")
    void answersAWorkspaceCallbackStatelessly_AC_001_55() throws Exception {
        var result = mvc.perform(get("http://acme.localhost" + CALLBACK + "?code=c&state=s"))
                .andReturn();
        MockHttpServletResponse response = result.getResponse();

        assertThat(response.getStatus()).as("status").isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/login?error=MSG141");
        assertExpiryCookie(response);
        assertThat(result.getRequest().getSession(false)).as("HTTP session").isNull();
        assertNoSession(response);
        assertThat(ProbeController.CALLS).as("controller calls").hasValue(0);
        verifyNoInteractions(redis);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
        "acme.localhost, true",
        "beta.localhost, true",
        "login.localhost, false",
        "admin.localhost, false",
        "unknown.localhost, false"
    })
    @DisplayName("AC-001.54 runs the Keycloak callback only on a Workspace site")
    void leavesTheCallbackOnLoginAdminAndUnknownHostsToTheOldChain_AC_001_54(String host, boolean workspace)
            throws Exception {
        MockHttpServletResponse response = mvc.perform(get("http://" + host + CALLBACK + "?code=c&state=s"))
                .andReturn()
                .getResponse();

        if (workspace) {
            assertThat(response.getStatus()).as("status").isEqualTo(302);
            assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/login?error=MSG141");
            assertThat(ProbeController.CALLS).as("controller calls").hasValue(0);
        } else {
            assertThat(response.getStatus()).as("status").isEqualTo(200);
            assertThat(ProbeController.CALLS).as("controller calls").hasValue(1);
            assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                    .as("Set-Cookie")
                    .noneMatch(header -> header.startsWith(COOKIE + "="));
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"/oauth2/authorization/tsolve-web", "/login/oauth2/code/tsolve-web"})
    @DisplayName("AC-001.54 serves no sign-in at Spring's default OAuth2 paths")
    void servesNoSignInAtSpringsDefaultPaths_AC_001_54(String path) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("http://acme.localhost" + path + "?code=c&state=s"))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).as("status").isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).as("Location").isNull();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .as("Set-Cookie")
                .noneMatch(header -> header.startsWith(COOKIE + "="));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("edge Q5 B answers MSG06, expires the cookie and logs one ERROR when Redis is down in the callback")
    void answersMsg06WithOneErrorWhenRedisIsDownDuringTheCallback_edgeQ5B(CapturedOutput output) throws Exception {
        when(values.getAndDelete(anyString())).thenThrow(new RedisConnectionFailureException("redis is down"));

        var result = mvc.perform(get("http://acme.localhost" + CALLBACK + "?code=c&state=s")
                        .cookie(new Cookie(COOKIE, "a-cookie-value")))
                .andReturn();
        MockHttpServletResponse response = result.getResponse();

        assertThat(response.getStatus()).as("status").isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/login?error=MSG06");
        assertExpiryCookie(response);
        assertThat(result.getRequest().getSession(false)).as("HTTP session").isNull();
        assertNoSession(response);
        assertThat(ProbeController.CALLS).as("controller calls").hasValue(0);
        List<String> errors =
                output.getAll().lines().filter(line -> line.contains(" ERROR ")).toList();
        assertThat(errors).as("ERROR log entries").hasSize(1);
    }

    /**
     * MockMvc does not forward a failed request to {@code /error}; the servlet container does, as an ERROR dispatch
     * with the original path and status in request attributes. This replays that dispatch: an exception (500) on
     * {@code sign-in}, and no handler (404) on {@code complete} and {@code callback}. The browser must get that
     * status, not a 401 from the chain that guards everything else (design row 43, review finding 2).
     */
    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({"/api/v1/auth/sign-in, 500", "/api/v1/auth/complete, 404", "/api/v1/auth/callback, 404"})
    @DisplayName("edge 43 keeps the error status of a redirect path instead of 401")
    void keepsErrorStatusOfRedirectPath_edge43(String path, int status) throws Exception {
        for (String accept : List.of(MediaType.ALL_VALUE, MediaType.TEXT_HTML_VALUE)) {
            MockHttpServletResponse response = mvc.perform(
                            get("/error").accept(accept).with(errorDispatchFor(path, status)))
                    .andReturn()
                    .getResponse();

            assertThat(response.getStatus()).as("status for Accept %s", accept).isEqualTo(status);
        }
    }

    private static RequestPostProcessor errorDispatchFor(String path, int status) {
        return request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
            request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, "dispatcherServlet");
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
            if (status == 500) {
                RuntimeException failure = new IllegalStateException("database down");
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, failure);
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, failure.getClass());
            }
            return request;
        };
    }
}
