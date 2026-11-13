package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.TSolveApplication;
import vn.tsolve.identity.session.SessionCookie;

/**
 * Task 23c: two separately started instances of the application (own beans, own Tomcat, profile {@code dev}) share
 * only a Redis and a Postgres. A sign-in started on one is finished on the other; no HTTP session exists on either.
 * The token side is an in-process {@link FakeKeycloakRealm}; the headless {@link TestBrowser} sends the public host
 * and port in {@code X-Forwarded-*}, so the {@code redirect_uri} is the one the real realm registers. On Docker 29 run
 * with {@code -DargLine=-Dapi.version=1.44} (never committed).
 */
class TwoInstanceCallbackIT {

    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = UUID.randomUUID().toString();
    private static final String COOKIE = RedisAuthorizationRequestRepository.COOKIE_NAME;
    private static final String SESSION_COOKIE = SessionCookie.NAME;
    private static final String MSG141 = "/login?error=MSG141";
    private static final String MSG06 = "/login?error=MSG06";
    private static final String ACME = "acme.localhost";
    private static final String BETA = "beta.localhost";

    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .withExposedPorts(REDIS_PORT);
    private static final FakeKeycloakRealm FAKE = new FakeKeycloakRealm();

    private static ConfigurableApplicationContext instanceA;
    private static ConfigurableApplicationContext instanceB;
    private static int portA;
    private static int portB;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void startInstances() {
        DB.start();
        REDIS.start();
        FAKE.start();
        instanceA = startInstance();
        instanceB = startInstance();
        portA = portOf(instanceA);
        portB = portOf(instanceB);
        redis = instanceA.getBean(StringRedisTemplate.class);
        // Task 25b: the callback checks the Account; DevAccountSeeder only comes in Task 26b
        SignInTestFixtures.seedDevAccounts(
                new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())));
    }

    @AfterAll
    static void stopInstances() {
        for (ConfigurableApplicationContext ctx : new ConfigurableApplicationContext[] {instanceA, instanceB}) {
            if (ctx != null) {
                ctx.close();
            }
        }
        FAKE.stop();
        REDIS.stop();
        DB.stop();
    }

    @BeforeEach
    void cleanUp() {
        FAKE.reset();
        deleteAuthRequestKeys();
        // an earlier test may have left a client in the in-memory service; start each test without one
        for (ConfigurableApplicationContext ctx : new ConfigurableApplicationContext[] {instanceA, instanceB}) {
            ctx.getBean(OAuth2AuthorizedClientService.class)
                    .removeAuthorizedClient(FakeKeycloakRealm.CLIENT_ID, FakeKeycloakRealm.SUB);
        }
    }

    private static ConfigurableApplicationContext startInstance() {
        return new SpringApplicationBuilder(TSolveApplication.class)
                .profiles("dev")
                .run(
                        "--SERVER_PORT=0",
                        "--TSOLVE_LOGIN_ORIGIN=" + FAKE.origin(),
                        "--TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8080",
                        "--TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
                        "--GOOGLE_CLIENT_ID=test-client-id",
                        "--GOOGLE_CLIENT_SECRET=test-secret",
                        "--PLATFORM_OPERATOR_EMAILS=ops@example.test",
                        "--KEYCLOAK_WEB_CLIENT_SECRET=it-keycloak-web-client-secret",
                        "--KEYCLOAK_CONSOLE_CLIENT_SECRET=it-keycloak-console-client-secret",
                        "--CORS_ALLOWED_ORIGINS=http://localhost:5173",
                        "--DB_HOST=" + DB.getHost(),
                        "--DB_PORT=" + DB.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        "--DB_NAME=" + DB.getDatabaseName(),
                        "--DB_USER=" + DB.getUsername(),
                        "--DB_PASSWORD=" + DB.getPassword(),
                        "--REDIS_HOST=" + REDIS.getHost(),
                        "--REDIS_PORT=" + REDIS.getMappedPort(REDIS_PORT),
                        "--REDIS_PASSWORD=" + REDIS_PASSWORD,
                        "--spring.jmx.enabled=false");
    }

    private static int portOf(ConfigurableApplicationContext ctx) {
        return ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
    }

    private static int activeSessions(ConfigurableApplicationContext ctx) {
        TomcatWebServer tomcat = (TomcatWebServer) ((ServletWebServerApplicationContext) ctx).getWebServer();
        int total = 0;
        for (Container child : tomcat.getTomcat().getHost().findChildren()) {
            if (child instanceof Context context && context.getManager() != null) {
                total += context.getManager().getActiveSessions();
            }
        }
        return total;
    }

    // ---- helpers

    private static void deleteAuthRequestKeys() {
        Set<String> keys = redis.keys(RedisAuthorizationRequestRepository.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static Set<String> allKeys() {
        Set<String> keys = redis.keys("*");
        return keys == null ? Set.of() : new HashSet<>(keys);
    }

    /** What the start's 302 to Keycloak holds, and the cookie it set. */
    private record Started(String state, String nonce, String challenge, String redirectUri, String cookie) {}

    private static Started start(TestBrowser browser, int port, String host) {
        TestBrowser.Reply reply = browser.backend("http://" + host + ":8080/api/v1/auth/sign-in", port);
        assertThat(reply.status()).as("start status").isEqualTo(302);
        String location = reply.location();
        assertThat(location)
                .as("start Location")
                .startsWith(FAKE.origin() + "/realms/tsolve/protocol/openid-connect/auth?");
        return new Started(
                TestBrowser.param(location, "state"),
                TestBrowser.param(location, "nonce"),
                TestBrowser.param(location, "code_challenge"),
                TestBrowser.param(location, "redirect_uri"),
                browser.cookie(host, COOKIE));
    }

    private static TestBrowser.Reply callback(TestBrowser browser, int port, String host, String query) {
        return browser.backend("http://" + host + ":8080/api/v1/auth/callback?" + query, port);
    }

    private static String newCode() {
        return "code-" + UUID.randomUUID();
    }

    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static void assertExpiryCookie(TestBrowser.Reply reply) {
        List<String> headers = reply.setCookies(COOKIE);
        assertThat(headers).as("Set-Cookie of %s", COOKIE).hasSize(1);
        assertThat(headers.get(0)).startsWith(COOKIE + "=;");
        Map<String, String> attributes = SignInTestFixtures.attributes(headers.get(0));
        assertThat(attributes).containsEntry("max-age", "0").containsEntry("path", "/");
        assertThat(attributes).containsKeys("secure", "httponly");
        assertThat(attributes.get("samesite")).isEqualToIgnoringCase("Lax");
        assertThat(attributes).doesNotContainKey("domain");
    }

    private static void assertRefusal(TestBrowser.Reply reply, String location) {
        assertThat(reply.status()).as("status").isEqualTo(302);
        assertThat(reply.location()).as("Location").isEqualTo(location);
        assertExpiryCookie(reply);
        assertThat(reply.setCookies()).as("Set-Cookie").noneMatch(h -> h.contains("JSESSIONID"));
    }

    private static void assertNoSession(TestBrowser browser) {
        assertThat(browser.seenCookieNames()).as("cookies ever set").doesNotContain("JSESSIONID");
        assertThat(activeSessions(instanceA)).as("active sessions on A").isZero();
        assertThat(activeSessions(instanceB)).as("active sessions on B").isZero();
    }

    /**
     * Q2 A "nothing stored": after the exchange, Spring's login filter must keep no authorized client (access or
     * refresh token) on either instance. Reads the application's own {@code OAuth2AuthorizedClientService} bean.
     */
    private static void assertNoAuthorizedClient() {
        for (ConfigurableApplicationContext ctx : new ConfigurableApplicationContext[] {instanceA, instanceB}) {
            OAuth2AuthorizedClientService clients = ctx.getBean(OAuth2AuthorizedClientService.class);
            OAuth2AuthorizedClient kept =
                    clients.loadAuthorizedClient(FakeKeycloakRealm.CLIENT_ID, FakeKeycloakRealm.SUB);
            assertThat(kept).as("authorized client kept in memory").isNull();
        }
    }

    // ---- tests

    @Test
    @DisplayName("AC-001.55 edge 115 answers on instance B the callback of a sign-in started on instance A")
    void answersOnBTheCallbackOfASignInStartedOnA_AC_001_55_edge115() throws Exception {
        TestBrowser browser = new TestBrowser();
        Set<String> keysBefore = allKeys();
        Started started = start(browser, portA, ACME);
        String code = newCode();
        FAKE.answerCode(code, FAKE.idToken(started.nonce(), claims -> {}));

        TestBrowser.Reply reply = callback(browser, portB, ACME, "code=" + code + "&state=" + started.state());

        List<Map<String, String>> tokenRequests = FAKE.tokenRequests();
        assertThat(tokenRequests).as("token requests").hasSize(1);
        assertThat(s256(tokenRequests.get(0).get("code_verifier")))
                .as("PKCE verifier B sent")
                .isEqualTo(started.challenge());
        assertThat(tokenRequests.get(0))
                .containsEntry("redirect_uri", "http://acme.localhost:8080/api/v1/auth/callback");
        // Task 26a: a passing sign-in creates the session at once and redirects to /
        assertThat(reply.status()).as("status").isEqualTo(302);
        assertThat(reply.location()).as("Location").isEqualTo("/");
        assertThat(reply.setCookies(SESSION_COOKIE))
                .as("Set-Cookie of %s", SESSION_COOKIE)
                .hasSize(1);
        assertExpiryCookie(reply);
        Set<String> newKeys = allKeys();
        newKeys.removeAll(keysBefore);
        assertThat(newKeys).as("keys Redis gained").hasSize(1).allMatch(key -> key.startsWith("tsolve:session:"));
        assertNoSession(browser);
        assertNoAuthorizedClient();
    }

    enum Unmatched {
        NO_COOKIE,
        VICTIMS_COOKIE_ATTACKERS_STATE,
        REPLAY,
        KEY_GONE,
        OTHER_WORKSPACE
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(Unmatched.class)
    @DisplayName("AC-001.6 edge 122 refuses on the other instance a callback that matches no saved request")
    void refusesACallbackThatMatchesNoSavedRequest_AC_001_6_edge122(Unmatched row) throws Exception {
        TestBrowser browser = new TestBrowser();
        String code = newCode();
        final String host = ACME;
        TestBrowser.Reply reply;
        int tokenRequestsAllowed = 0;
        switch (row) {
            case NO_COOKIE ->
                reply = callback(browser, portB, host, "code=" + code + "&state=an-attackers-state-value");
            case VICTIMS_COOKIE_ATTACKERS_STATE -> {
                Started victim = start(browser, portA, host);
                FAKE.answerCode(code, FAKE.idToken(victim.nonce(), claims -> {}));
                reply = callback(browser, portB, host, "code=" + code + "&state=an-attackers-state-value");
                assertRefusal(reply, MSG141);
                // the victim's own request is used up now, so even the right state fails
                browser.setCookie(host, COOKIE, victim.cookie());
                reply = callback(browser, portB, host, "code=" + code + "&state=" + victim.state());
            }
            case REPLAY -> {
                Started started = start(browser, portA, host);
                FAKE.answerCode(code, FAKE.idToken(started.nonce(), claims -> {}));
                String query = "code=" + code + "&state=" + started.state();
                assertThat(callback(browser, portB, host, query).status()).isEqualTo(302);
                tokenRequestsAllowed = 1;
                browser.setCookie(host, COOKIE, started.cookie());
                reply = callback(browser, portA, host, query);
            }
            case KEY_GONE -> {
                Started started = start(browser, portA, host);
                deleteAuthRequestKeys();
                reply = callback(browser, portB, host, "code=" + code + "&state=" + started.state());
            }
            case OTHER_WORKSPACE -> {
                Started started = start(browser, portA, ACME);
                browser.setCookie(BETA, COOKIE, started.cookie());
                reply = callback(browser, portB, BETA, "code=" + code + "&state=" + started.state());
            }
            default -> throw new IllegalStateException();
        }

        assertRefusal(reply, MSG141);
        assertThat(FAKE.tokenRequests()).as("token requests").hasSize(tokenRequestsAllowed);
        assertNoSession(browser);
    }

    @ParameterizedTest(name = "[{index}] state matches: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("AC-001.8 answers MSG06 to a Keycloak error only when the saved request matches")
    void answersMsg06ToAKeycloakErrorOnlyWithAMatchingRequest_AC_001_8(boolean stateMatches) {
        TestBrowser browser = new TestBrowser();
        Started started = start(browser, portA, ACME);
        String state = stateMatches ? started.state() : "a-state-that-matches-nothing";

        TestBrowser.Reply reply =
                callback(browser, portB, ACME, "error=access_denied&error_description=Cancelled&state=" + state);

        assertRefusal(reply, stateMatches ? MSG06 : MSG141);
        assertThat(FAKE.tokenRequests()).as("token requests").isEmpty();
        // used up in both rows: the right state with the same cookie now fails
        browser.setCookie(ACME, COOKIE, started.cookie());
        TestBrowser.Reply again = callback(browser, portB, ACME, "error=access_denied&state=" + started.state());
        assertRefusal(again, MSG141);
        assertNoSession(browser);
    }

    @ParameterizedTest(name = "[{index}] state matches: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("AC-001.8 answers MSG06 to invalid_request from Keycloak only when the saved request matches")
    void answersMsg06ToInvalidRequestFromKeycloakOnlyWithAMatchingRequest_AC_001_8(boolean stateMatches) {
        TestBrowser browser = new TestBrowser();
        Started started = start(browser, portA, ACME);
        String state = stateMatches ? started.state() : "a-state-that-matches-nothing";

        TestBrowser.Reply reply = callback(browser, portB, ACME, "error=invalid_request&state=" + state);

        assertRefusal(reply, stateMatches ? MSG06 : MSG141);
        assertThat(FAKE.tokenRequests()).as("token requests").isEmpty();
        // used up in both rows: the right state with the same cookie now fails
        browser.setCookie(ACME, COOKIE, started.cookie());
        TestBrowser.Reply again = callback(browser, portB, ACME, "error=invalid_request&state=" + started.state());
        assertRefusal(again, MSG141);
        assertNoSession(browser);
    }

    enum Failure {
        TOKEN_400,
        TOKEN_500,
        CONNECTION_RESET,
        SIGNED_WITH_OTHER_KEY,
        ISSUER_OF_OPS_REALM,
        AUDIENCE_OF_CONSOLE,
        EXPIRED,
        NONCE_OF_ANOTHER_REQUEST,
        EMAIL_NOT_VERIFIED,
        EMAIL_VERIFIED_MISSING
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(Failure.class)
    @DisplayName("AC-001.9 answers MSG06 when the code exchange or the ID token check fails")
    void answersMsg06WhenTheExchangeOrTheIdTokenFails_AC_001_9(Failure row) {
        TestBrowser browser = new TestBrowser();
        Started started = start(browser, portA, ACME);
        String code = newCode();
        switch (row) {
            case TOKEN_400 -> FAKE.answerCodeStatus(code, 400, "{\"error\":\"invalid_grant\"}");
            case TOKEN_500 -> FAKE.answerCodeStatus(code, 500, "{\"error\":\"server_error\"}");
            case CONNECTION_RESET -> FAKE.answerCodeFault(code, Fault.CONNECTION_RESET_BY_PEER);
            case SIGNED_WITH_OTHER_KEY -> FAKE.answerCode(code, FAKE.signedWithOtherKey(started.nonce(), claims -> {}));
            case ISSUER_OF_OPS_REALM ->
                FAKE.answerCode(
                        code,
                        FAKE.idToken(
                                started.nonce(), claims -> claims.put("iss", FAKE.origin() + "/realms/tsolve-ops")));
            case AUDIENCE_OF_CONSOLE ->
                FAKE.answerCode(code, FAKE.idToken(started.nonce(), claims -> claims.put("aud", "tsolve-console")));
            case EXPIRED ->
                FAKE.answerCode(code, FAKE.idToken(started.nonce(), claims -> {
                    Instant past = Instant.now().minusSeconds(3600);
                    claims.put("iat", past.minusSeconds(300).getEpochSecond());
                    claims.put("exp", past.getEpochSecond());
                }));
            case NONCE_OF_ANOTHER_REQUEST ->
                FAKE.answerCode(code, FAKE.idToken("the-nonce-of-another-request", claims -> {}));
            case EMAIL_NOT_VERIFIED ->
                FAKE.answerCode(code, FAKE.idToken(started.nonce(), claims -> claims.put("email_verified", false)));
            case EMAIL_VERIFIED_MISSING ->
                FAKE.answerCode(code, FAKE.idToken(started.nonce(), claims -> claims.remove("email_verified")));
        }

        TestBrowser.Reply reply = callback(browser, portB, ACME, "code=" + code + "&state=" + started.state());

        assertRefusal(reply, MSG06);
        assertThat(FAKE.tokenRequests()).as("token requests").hasSize(1);
        assertNoAuthorizedClient();
        assertThat(reply.body()).as("body").doesNotContain("\"sub\"").doesNotContain(FakeKeycloakRealm.EMAIL);
        // the request is used up: a retry with the same cookie and state is refused as invalid
        browser.setCookie(ACME, COOKIE, started.cookie());
        TestBrowser.Reply retry = callback(browser, portA, ACME, "code=" + code + "&state=" + started.state());
        assertRefusal(retry, MSG141);
        assertNoSession(browser);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"unknown.localhost"})
    @DisplayName("AC-001.54 answers 404 to the callback on a host that is no Workspace")
    void answers404ToTheCallbackOnAHostWithNoWorkspace_AC_001_54(String host) {
        TestBrowser browser = new TestBrowser();
        Started started = start(browser, portA, ACME);
        browser.setCookie(host, COOKIE, started.cookie());

        TestBrowser.Reply reply = callback(browser, portB, host, "code=" + newCode() + "&state=" + started.state());

        assertThat(reply.status()).as("status").isEqualTo(404);
        assertThat(allKeys())
                .as("the saved request is still in Redis, so nothing read it")
                .anyMatch(key -> key.startsWith(RedisAuthorizationRequestRepository.KEY_PREFIX));
    }

    @Test
    @DisplayName("edge 122 serves no claims to a direct request for the dev claims page")
    void servesNoClaimsToADirectRequestForTheDevClaimsPage_edge122() {
        TestBrowser browser = new TestBrowser();

        TestBrowser.Reply reply = browser.backend("http://acme.localhost:8080/api/v1/auth/dev/claims", portA);

        assertThat(reply.status()).as("status").isNotEqualTo(200);
        assertThat(reply.body()).as("body").doesNotContain("\"sub\"").doesNotContain("\"email\"");
    }
}
