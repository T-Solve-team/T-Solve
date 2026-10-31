package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.identity.signin.AuthHosts;
import vn.tsolve.identity.signin.SignInTestBeans;
import vn.tsolve.identity.signin.SignInTestFixtures;

/**
 * Adding the chain for the four redirect paths turns off Spring Boot's default chain; this shows the rest of the
 * API still needs a signed-in user (design row 43: 401, no form login, no HTTP Basic).
 */
@WebMvcTest(AuthSecurityConfigTest.ProbeController.class)
@Import({AuthSecurityConfig.class, AuthHosts.class, SignInTestBeans.class, AuthSecurityConfigTest.ProbeController.class
})
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0"
        })
class AuthSecurityConfigTest {

    /** Test-only endpoints outside the four redirect paths; counts the calls that got through. */
    @RestController
    static class ProbeController {

        static final AtomicInteger CALLS = new AtomicInteger();

        @GetMapping({"/api/v1/probe", "/api/v1/auth/sign-in/extra"})
        String probe() {
            CALLS.incrementAndGet();
            return "reached";
        }
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void resetCalls() {
        ProbeController.CALLS.set(0);
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
