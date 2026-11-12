package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static vn.tsolve.identity.session.SessionTestFixtures.PORT;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;
import static vn.tsolve.identity.session.SessionTestFixtures.randomCookieValue;
import static vn.tsolve.identity.session.SessionTestFixtures.sha256Hex;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Task 27a: {@code POST /api/v1/auth/logout} on the whole chain (session filter, CSRF filter, real Redis). It ends the
 * session, expires the cookie, publishes one LOGOUT and answers 200 {@code { logoutUrl }} (AC-002.1, .2, .3, .6, .8).
 * The expiry paths (EXPIRED, DEACTIVATED, sweep) belong to Task 27b.
 */
@Import({WhoAmITestController.class, SessionEndedRecorder.class})
class LogoutControllerTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String WHOAMI = "/api/v1/test/whoami";
    private static final String HINT = "id-token-hint-of-acme-session";
    private static final String MSG07_CODE = "MSG07";

    @Autowired
    private WebSessionStore store;

    @Autowired
    private ObjectMapper json;

    private UUID account;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void seedAccount() {
        WhoAmITestController.reset();
        SessionEndedRecorder.reset();
        account = UUID.randomUUID();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO app_user (id, email, full_name, status, created_at)"
                                + " VALUES (?, ?, 'Lan', 'ACTIVE', now())",
                        account,
                        "lan@acme.example");
    }

    // ---- helpers

    private String acmeSession() {
        return store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                ACME.id(),
                account,
                null,
                ACME.siteHost(),
                HINT,
                clock.instant(),
                clock.instant()));
    }

    private MockHttpServletRequestBuilder logout(String cookie) {
        MockHttpServletRequestBuilder request =
                post(url(ACME.siteHost(), LOGOUT)).with(csrf());
        return cookie == null ? request : request.cookie(new Cookie(SESSION_COOKIE, cookie));
    }

    private MockHttpServletResponse whoami(String cookie) throws Exception {
        return perform(get(url(ACME.siteHost(), WHOAMI)).cookie(new Cookie(SESSION_COOKIE, cookie)));
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private static String acmeLogin() {
        return "http://" + ACME.siteHost() + ":" + PORT + "/login";
    }

    /** The query parameters of the answered {@code logoutUrl}, each of which must appear exactly once. */
    private static Map<String, String> queryOf(URI logoutUrl) {
        var params = UriComponentsBuilder.fromUri(logoutUrl).build().getQueryParams();
        params.forEach(
                (name, values) -> assertThat(values).as("parameter " + name).hasSize(1));
        return params.toSingleValueMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> java.net.URLDecoder.decode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void assertTheAcmeLogoutUrl(String logoutUrl) {
        URI uri = URI.create(logoutUrl);
        assertThat(uri.getScheme() + "://" + uri.getAuthority() + uri.getPath())
                .isEqualTo(SignInTestFixtures.LOGIN_ORIGIN + "/realms/tsolve/protocol/openid-connect/logout");
        assertThat(queryOf(uri))
                .containsOnlyKeys("client_id", "id_token_hint", "post_logout_redirect_uri")
                .containsEntry("client_id", "tsolve-web")
                .containsEntry("id_token_hint", HINT)
                .containsEntry("post_logout_redirect_uri", acmeLogin());
    }

    // ---- tests

    @Test
    @DisplayName("AC-002.1 logout on acme: 200 { logoutUrl }, the Redis key is gone, the session cookie expired")
    void logoutOnAcme_AC_002_1() throws Exception {
        String cookie = acmeSession();
        assertThat(redis.hasKey(keyOf(cookie))).isTrue();

        MockHttpServletResponse response = perform(logout(cookie));

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.fieldNames()).toIterable().containsExactly("logoutUrl");
        assertTheAcmeLogoutUrl(body.path("logoutUrl").asText());
        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        List<String> cookies = SignInTestFixtures.setCookie(response, SESSION_COOKIE);
        assertThat(cookies).hasSize(1);
        Map<String, String> attributes = SignInTestFixtures.attributes(cookies.get(0));
        assertThat(attributes).containsEntry("max-age", "0").containsEntry("path", "/");
        assertThat(attributes).containsKey("secure").doesNotContainKey("domain");
    }

    @Test
    @DisplayName("AC-002.2 the cookie of a logged-out session answers 401 MSG07")
    void theOldCookieIsDead_AC_002_2() throws Exception {
        String cookie = acmeSession();
        assertThat(perform(logout(cookie)).getStatus()).isEqualTo(200);
        int callsBefore = WhoAmITestController.calls();

        MockHttpServletResponse again = whoami(cookie);
        MockHttpServletResponse secondLogout = perform(logout(cookie));

        assertThat(again.getStatus()).isEqualTo(401);
        assertThat(body(again).path("code").asText()).isEqualTo(MSG07_CODE);
        assertThat(WhoAmITestController.calls()).isEqualTo(callsBefore);
        assertThat(secondLogout.getStatus()).isEqualTo(401);
        assertThat(body(secondLogout).path("code").asText()).isEqualTo(MSG07_CODE);
    }

    @Test
    @DisplayName("AC-002.3 logout publishes one LOGOUT with the Account, Workspace and session ids after the key is"
            + " gone")
    void logoutPublishesOneLogout_AC_002_3() throws Exception {
        String cookie = acmeSession();

        perform(logout(cookie));

        List<SessionEndedRecorder.Received> received = SessionEndedRecorder.all();
        assertThat(received).hasSize(1);
        SessionEnded event = received.get(0).event();
        assertThat(event.reason()).isEqualTo(SessionEnded.Reason.LOGOUT);
        assertThat(event.accountId()).isEqualTo(account);
        assertThat(event.workspaceId()).isEqualTo(ACME.id());
        assertThat(event.sessionId()).isEqualTo(sha256Hex(cookie)).isNotEqualTo(cookie);
        assertThat(received.get(0).keyExistedAtPublish()).isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"no cookie", "unknown cookie"})
    @DisplayName("AC-002.6 logout without a valid session: 401 MSG07 and no event")
    void logoutWithoutASession_AC_002_6(String row) throws Exception {
        boolean unknown = row.equals("unknown cookie");

        MockHttpServletResponse response = perform(logout(unknown ? randomCookieValue() : null));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body(response).path("code").asText()).isEqualTo(MSG07_CODE);
        assertThat(SessionEndedRecorder.all()).isEmpty();
        List<String> cookies = SignInTestFixtures.setCookie(response, SESSION_COOKIE);
        if (unknown) {
            assertThat(cookies).hasSize(1);
            assertThat(SignInTestFixtures.attributes(cookies.get(0))).containsEntry("max-age", "0");
        } else {
            assertThat(redis.keys("*")).isEmpty();
        }
    }

    @Test
    @DisplayName("edge 1 logout with an expired session (61 min idle): 401 MSG07 and no LOGOUT event")
    void logoutWithAnExpiredSession_edge1() throws Exception {
        String cookie = acmeSession();
        clock.advance(Duration.ofMinutes(61));

        MockHttpServletResponse response = perform(logout(cookie));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body(response).path("code").asText()).isEqualTo(MSG07_CODE);
        assertThat(SessionEndedRecorder.ofReason(SessionEnded.Reason.LOGOUT)).isEmpty();
        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"no csrf token", "wrong csrf token"})
    @DisplayName("edge 2 logout without a CSRF token or with a wrong one is refused and the session stays")
    void logoutWithoutAValidCsrfToken_edge2(String row) throws Exception {
        String cookie = acmeSession();
        MockHttpServletRequestBuilder request =
                post(url(ACME.siteHost(), LOGOUT)).cookie(new Cookie(SESSION_COOKIE, cookie));
        if (row.startsWith("wrong")) {
            request.header("X-XSRF-TOKEN", "not-the-token").param("_csrf", "not-the-token");
        }

        MockHttpServletResponse response = perform(request);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body(response).path("code").asText()).isEqualTo(MSG07_CODE);
        assertThat(redis.hasKey(keyOf(cookie))).isTrue();
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE)).isEmpty();
        assertThat(SessionEndedRecorder.all()).isEmpty();
        assertThat(whoami(cookie).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("edge 3 two logouts with one cookie publish one LOGOUT")
    void twoLogoutsPublishOneLogout_edge3() throws Exception {
        String sequential = acmeSession();
        assertThat(perform(logout(sequential)).getStatus()).isEqualTo(200);
        MockHttpServletResponse second = perform(logout(sequential));
        assertThat(second.getStatus()).isEqualTo(401);
        assertThat(body(second).path("code").asText()).isEqualTo(MSG07_CODE);
        assertThat(SessionEndedRecorder.ofReason(SessionEnded.Reason.LOGOUT)).hasSize(1);

        SessionEndedRecorder.reset();
        String concurrent = acmeSession();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MockHttpServletResponse>> answers = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                answers.add(pool.submit(() -> {
                    go.await();
                    return perform(logout(concurrent));
                }));
            }
            go.countDown();
            Set<String> logoutUrls = new HashSet<>();
            for (Future<MockHttpServletResponse> answer : answers) {
                MockHttpServletResponse response = answer.get();
                // the loser either lost the claim (200, same logoutUrl) or reached the filter after the DEL (401 MSG07)
                if (response.getStatus() == 200) {
                    logoutUrls.add(body(response).path("logoutUrl").asText());
                } else {
                    assertThat(response.getStatus()).isEqualTo(401);
                    assertThat(body(response).path("code").asText()).isEqualTo(MSG07_CODE);
                }
            }
            assertThat(logoutUrls).hasSize(1);
            assertTheAcmeLogoutUrl(logoutUrls.iterator().next());
        } finally {
            pool.shutdownNow();
        }
        assertThat(SessionEndedRecorder.ofReason(SessionEnded.Reason.LOGOUT)).hasSize(1);
        assertThat(redis.hasKey(keyOf(concurrent))).isFalse();
    }

    @Test
    @DisplayName("AC-002.8 logoutUrl ignores id_token_hint, post_logout_redirect_uri and redirect values sent in the"
            + " request")
    void logoutUrlIgnoresRequestValues_AC_002_8() throws Exception {
        String cookie = acmeSession();
        String forged = "{\"id_token_hint\":\"forged\",\"post_logout_redirect_uri\":\"https://evil.example/\","
                + "\"redirect\":\"https://evil.example/\"}";

        MockHttpServletResponse response = perform(post(url(
                        ACME.siteHost(),
                        LOGOUT + "?post_logout_redirect_uri=https://evil.example/&id_token_hint=forged"
                                + "&redirect=https://evil.example/"))
                .with(csrf())
                .cookie(new Cookie(SESSION_COOKIE, cookie))
                .contentType("application/json")
                .content(forged));

        assertThat(response.getStatus()).isEqualTo(200);
        String logoutUrl = body(response).path("logoutUrl").asText();
        assertThat(logoutUrl).doesNotContain("evil.example").doesNotContain("forged");
        assertTheAcmeLogoutUrl(logoutUrl);
    }
}
