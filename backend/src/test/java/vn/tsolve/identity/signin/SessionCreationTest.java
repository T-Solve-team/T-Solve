package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.callbackRequest;
import static vn.tsolve.identity.session.SessionTestFixtures.claimsOf;
import static vn.tsolve.identity.session.SessionTestFixtures.cookieValue;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.session.AbstractSessionIT;
import vn.tsolve.identity.session.SessionTestFixtures;
import vn.tsolve.identity.session.WebSession;
import vn.tsolve.identity.session.WebSessionStore;
import vn.tsolve.identity.session.WhoAmITestController;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Task 26a: a passing sign-in creates the session at once (AC-001.16 last bullet, AC-001.18). The test calls the
 * {@link OidcCallbackHandler} bean on {@code acme.localhost} with a signed-in token; the Account is seeded in tenant A.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(WhoAmITestController.class)
class SessionCreationTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;
    private static final WorkspaceRef GAMMA = InMemoryWorkspaceLookup.GAMMA;
    private static final String ACME_HOST = ACME.siteHost();
    private static final String EMAIL = "alice@acme.example";
    private static final String RETURN_TO = "/tickets/42?tab=history";
    private static final String SESSION_KEYS = "tsolve:session:*";

    @Autowired
    private OidcCallbackHandler handler;

    @Autowired
    private WebSessionStore store;

    @Autowired
    private ObjectMapper json;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void seedTheAccount() {
        WhoAmITestController.reset();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        SignInTestFixtures.seedAccount(TwoTenantDatabases.tenantA(), EMAIL, "Alice");
    }

    // ---- helpers

    private UUID accountId() {
        return TwoTenantDatabases.tenantA()
                .queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, EMAIL);
    }

    private MockHttpServletResponse callback(String host, Map<String, Object> claims, String returnTo, String cookie)
            throws Exception {
        MockHttpServletRequest request = callbackRequest(host);
        if (returnTo != null) {
            request.setAttribute(OidcCallbackHandler.RETURN_TO_ATTRIBUTE, returnTo);
        }
        if (cookie != null) {
            request.setCookies(new Cookie(SESSION_COOKIE, cookie));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, SignInTestFixtures.signedIn(claims));
        return response;
    }

    private MockHttpServletResponse signIn(String returnTo) throws Exception {
        return callback(ACME_HOST, claimsOf(ACME, EMAIL), returnTo, null);
    }

    private static URI locationOf(MockHttpServletResponse response, String host) {
        return URI.create(url(host, "/api/v1/auth/callback")).resolve(response.getHeader(HttpHeaders.LOCATION));
    }

    private Set<String> sessionKeys() {
        return redis.keys(SESSION_KEYS);
    }

    // ---- tests

    @Test
    @DisplayName("AC-001.16 a passing sign-in creates the session at once: one key, a 302 to returnTo, no code, and"
            + " the sign-in time recorded")
    void aPassingSignInCreatesTheSessionAtOnce_AC_001_16() throws Exception {
        MockHttpServletResponse response = signIn(RETURN_TO);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(locationOf(response, ACME_HOST)).isEqualTo(URI.create(url(ACME_HOST, RETURN_TO)));
        assertThat(response.getHeader(HttpHeaders.LOCATION))
                .doesNotContain("code=")
                .doesNotContain("complete");
        String cookie = cookieValue(response, SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(sessionKeys()).containsExactly(keyOf(cookie));
        Map<Object, Object> fields = redis.opsForHash().entries(keyOf(cookie));
        assertThat(fields).containsEntry("account_id", accountId().toString());
        assertThat(fields).containsEntry("workspace_id", ACME.id().toString());
        Object signedInAt = TwoTenantDatabases.tenantA()
                .queryForObject("SELECT last_sign_in_at FROM app_user WHERE email = ?", Object.class, EMAIL);
        assertThat(signedInAt).as("sign-in time").isNotNull();
    }

    @Test
    @DisplayName("AC-001.18 the session holds the Account, the Workspace, the site, the ID token and the clock time")
    void theSessionHoldsTheAccountWorkspaceAndIdToken_AC_001_18() throws Exception {
        MockHttpServletResponse response = signIn(null);

        String cookie = cookieValue(response, SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        Map<Object, Object> fields = redis.opsForHash().entries(keyOf(cookie));
        assertThat(fields).containsEntry("kind", "WORKSPACE");
        assertThat(fields).containsEntry("account_id", accountId().toString());
        assertThat(fields).containsEntry("workspace_id", ACME.id().toString());
        assertThat(fields).containsEntry("site_host", ACME_HOST);
        assertThat(fields).containsEntry("id_token_hint", SignInTestFixtures.ID_TOKEN_VALUE);
        Instant now = clock.instant();
        assertThat(Instant.parse(fields.get("created_at").toString())).isEqualTo(now);
        assertThat(Instant.parse(fields.get("last_activity_at").toString())).isEqualTo(now);
    }

    @Test
    @DisplayName("AC-001.18 each sign-in gets a new session id")
    void eachSignInGetsANewSessionId_AC_001_18() throws Exception {
        String first = cookieValue(signIn(null), SESSION_COOKIE);
        String second = cookieValue(signIn(null), SESSION_COOKIE);

        assertThat(first).isNotNull().matches("[A-Za-z0-9_-]{43}");
        assertThat(second).isNotNull().matches("[A-Za-z0-9_-]{43}").isNotEqualTo(first);
        assertThat(sessionKeys()).containsExactlyInAnyOrder(keyOf(first), keyOf(second));
    }

    @Test
    @DisplayName("AC-001.18 the callback sets the host-only session cookie, expires the auth-request cookie, and the"
            + " 302 is not cached")
    void setsTheSessionCookie_AC_001_18() throws Exception {
        MockHttpServletResponse response = signIn(null);

        List<String> headers = SignInTestFixtures.setCookie(response, SESSION_COOKIE);
        assertThat(headers).hasSize(1);
        String cookie = cookieValue(response, SESSION_COOKIE);
        assertThat(headers.get(0)).startsWith(SESSION_COOKIE + "=" + cookie);
        Map<String, String> attributes = SignInTestFixtures.attributes(headers.get(0));
        assertThat(attributes).containsEntry("path", "/").containsKeys("secure", "httponly");
        assertThat(attributes.get("samesite")).isEqualToIgnoringCase("Lax");
        assertThat(attributes).doesNotContainKeys("domain", "max-age", "expires");
        List<String> authRequest =
                SignInTestFixtures.setCookie(response, RedisAuthorizationRequestRepository.COOKIE_NAME);
        assertThat(authRequest).hasSize(1);
        assertThat(SignInTestFixtures.attributes(authRequest.get(0))).containsEntry("max-age", "0");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    @Test
    @DisplayName("AC-001.18 the callback redirects to the kept returnTo on the site")
    void redirectsToTheKeptReturnTo_AC_001_18() throws Exception {
        MockHttpServletResponse response = signIn(RETURN_TO);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(locationOf(response, ACME_HOST)).isEqualTo(URI.create(url(ACME_HOST, RETURN_TO)));
    }

    @Test
    @DisplayName("AC-001.18 the callback redirects to / when no returnTo was kept")
    void redirectsToTheRootWhenNoReturnToWasKept_AC_001_18() throws Exception {
        MockHttpServletResponse response = signIn(null);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(locationOf(response, ACME_HOST)).isEqualTo(URI.create(url(ACME_HOST, "/")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sameWorkspace", "otherWorkspace"})
    @DisplayName("edge 45 a browser that already holds a session cookie gets a new value, and the old key is deleted")
    void aBrowserWithASessionGetsANewOne_edge45(String old) throws Exception {
        WorkspaceRef oldWorkspace = "sameWorkspace".equals(old) ? ACME : BETA;
        String oldCookie = store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                oldWorkspace.id(),
                UUID.randomUUID(),
                null,
                oldWorkspace.siteHost(),
                "old-id-token",
                clock.instant(),
                clock.instant()));
        assertThat(sessionKeys()).containsExactly(keyOf(oldCookie));

        MockHttpServletResponse response = callback(ACME_HOST, claimsOf(ACME, EMAIL), null, oldCookie);

        String fresh = cookieValue(response, SESSION_COOKIE);
        assertThat(fresh).isNotNull().isNotEqualTo(oldCookie);
        assertThat(redis.hasKey(keyOf(oldCookie))).isFalse();
        assertThat(sessionKeys()).containsExactly(keyOf(fresh));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MSG122", "MSG120", "MSG09", "MSG10", "MSG06"})
    @DisplayName("edge 46 no failed check creates a session; the Location keeps its error code")
    void noFailedCheckCreatesASession_edge46(String code) throws Exception {
        TwoTenantDatabases.tenantA()
                .update("INSERT INTO app_user (email, full_name, status, created_at)"
                        + " VALUES ('dead@acme.example', 'Dead', 'DEACTIVATED', now())");
        String host = ACME_HOST;
        Map<String, Object> claims = claimsOf(ACME, EMAIL);
        switch (code) {
            case "MSG122" -> claims.put("hd", "other.example");
            case "MSG120" -> {
                host = GAMMA.siteHost();
                claims = claimsOf(GAMMA, EMAIL);
            }
            case "MSG09" -> claims = claimsOf(ACME, "nobody@acme.example");
            case "MSG10" -> claims = claimsOf(ACME, "dead@acme.example");
            default -> claims.remove("email");
        }

        MockHttpServletResponse response = callback(host, claims, null, null);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).startsWith("/login?error=" + code);
        assertThat(sessionKeys()).isEmpty();
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE)).isEmpty();
    }

    @Test
    @DisplayName("edge 47 the ID token never leaves the server: it is only in the Redis hash")
    void theIdTokenNeverLeavesTheServer_edge47(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = signIn(RETURN_TO);

        String cookie = cookieValue(response, SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        for (String name : response.getHeaderNames()) {
            assertThat(response.getHeaders(name))
                    .as("header %s", name)
                    .noneMatch(value -> value.contains(SignInTestFixtures.ID_TOKEN_VALUE));
        }
        assertThat(response.getContentAsString()).doesNotContain(SignInTestFixtures.ID_TOKEN_VALUE);
        assertThat(output.getAll()).doesNotContain(SignInTestFixtures.ID_TOKEN_VALUE);
        assertThat(redis.opsForHash().get(keyOf(cookie), "id_token_hint")).isEqualTo(SignInTestFixtures.ID_TOKEN_VALUE);
    }

    @Test
    @DisplayName("edge 48 the cookie from the callback on acme works on acme only: 200 there, 401 MSG123 on beta")
    void aCallbackSessionWorksOnItsSiteOnly_edge48() throws Exception {
        String cookie = cookieValue(signIn(null), SESSION_COOKIE);
        assertThat(cookie).isNotNull();

        MockHttpServletResponse onAcme =
                perform(get(url(ACME_HOST, "/api/v1/test/whoami")).cookie(new Cookie(SESSION_COOKIE, cookie)));
        MockHttpServletResponse onBeta =
                perform(get(url(BETA.siteHost(), "/api/v1/test/whoami")).cookie(new Cookie(SESSION_COOKIE, cookie)));

        assertThat(onAcme.getStatus()).isEqualTo(200);
        JsonNode user = json.readTree(onAcme.getContentAsString());
        assertThat(user.path("id").asText()).isEqualTo(accountId().toString());
        assertThat(onBeta.getStatus()).isEqualTo(401);
        assertThat(json.readTree(onBeta.getContentAsString()).path("code").asText())
                .isEqualTo("MSG123");
    }
}
