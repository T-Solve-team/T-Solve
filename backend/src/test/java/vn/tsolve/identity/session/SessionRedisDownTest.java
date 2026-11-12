package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.callbackRequest;
import static vn.tsolve.identity.session.SessionTestFixtures.claimsOf;
import static vn.tsolve.identity.session.SessionTestFixtures.randomCookieValue;
import static vn.tsolve.identity.session.SessionTestFixtures.sha256Hex;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.OidcCallbackHandler;
import vn.tsolve.identity.signin.RedisAuthorizationRequestRepository;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;

/** Redis is on a closed port: fail closed (design row 90), at the callback and on a request. */
@ExtendWith(OutputCaptureExtension.class)
@Import(WhoAmITestController.class)
class SessionRedisDownTest extends AbstractSessionIT {

    private static final String ACME_HOST = InMemoryWorkspaceLookup.ACME.siteHost();
    private static final String EMAIL = "alice@acme.example";

    @Autowired
    private OidcCallbackHandler handler;

    @SpyBean
    private WebSessionStore store;

    @DynamicPropertySource
    static void deadRedis(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerDeadRedis(registry);
    }

    @BeforeEach
    void reset() {
        WhoAmITestController.reset();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
    }

    @Test
    @DisplayName("edge 6 Redis down at the callback: /login?error=MSG06, no session cookie, the auth-request cookie"
            + " expired, the sign-in time recorded, and the log names the failure without the ID token or any"
            + " cookie value")
    void redisDownAtCallback_edge6(CapturedOutput output) throws Exception {
        SignInTestFixtures.seedAccount(TwoTenantDatabases.tenantA(), EMAIL, "Alice");
        String authRequest = randomCookieValue();
        MockHttpServletRequest request = callbackRequest(ACME_HOST);
        request.setCookies(new Cookie(RedisAuthorizationRequestRepository.COOKIE_NAME, authRequest));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, SignInTestFixtures.signedIn(claimsOf(InMemoryWorkspaceLookup.ACME, EMAIL)));

        assertThat(response.getStatus()).isEqualTo(302);
        URI location =
                URI.create(url(ACME_HOST, "/api/v1/auth/callback")).resolve(response.getHeader(HttpHeaders.LOCATION));
        assertThat(location).isEqualTo(URI.create(url(ACME_HOST, "/login?error=MSG06")));
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE)).isEmpty();
        Map<String, String> expired = SignInTestFixtures.attributes(
                SignInTestFixtures.setCookie(response, RedisAuthorizationRequestRepository.COOKIE_NAME)
                        .get(0));
        assertThat(expired).containsEntry("max-age", "0");
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject("SELECT last_sign_in_at FROM app_user WHERE email = ?", Object.class, EMAIL))
                .as("sign-in time")
                .isNotNull();
        String log = output.getAll();
        assertThat(log.toLowerCase()).containsAnyOf("redis", "session store");
        assertThat(log)
                .doesNotContain(SignInTestFixtures.ID_TOKEN_VALUE)
                .doesNotContain(authRequest)
                .doesNotContain(sha256Hex(authRequest));
    }

    @Test
    @DisplayName("edge 15 Redis down on a request with a session cookie: endpoint not run, generic 500, not signed in,"
            + " no cookie value in the log")
    void redisDownOnARequest_edge15(CapturedOutput output) throws Exception {
        String cookie = randomCookieValue();

        MockHttpServletResponse response =
                perform(get(url(ACME_HOST, "/api/v1/test/whoami")).cookie(new Cookie(SESSION_COOKIE, cookie)));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("MSG06");
        assertThat(WhoAmITestController.calls()).isZero();
        assertThat(output.getAll()).doesNotContain(cookie).doesNotContain(sha256Hex(cookie));
    }

    @Test
    @DisplayName("edge 16 Redis down on a request without a cookie: 401 MSG07 and the filter does not call Redis")
    void redisDownWithoutACookie_edge16() throws Exception {
        MockHttpServletResponse response = perform(get(url(ACME_HOST, "/api/v1/test/whoami")));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MSG07");
        assertThat(WhoAmITestController.calls()).isZero();
        verifyNoInteractions(store);
    }
}
