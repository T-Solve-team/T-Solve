package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static vn.tsolve.identity.session.SessionTestFixtures.cookieValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.Fault;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.session.AbstractSessionIT;
import vn.tsolve.identity.session.SessionTestFixtures;
import vn.tsolve.identity.session.WebSession;
import vn.tsolve.identity.session.WebSessionStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;

/**
 * Tasks 29a and 29b: the Platform Console sign-in on {@code admin.}. The start (AC-001.28) and the callback
 * (AC-001.29 to .31). The whole application over a real Redis; Keycloak is {@link FakeKeycloakRealm}, which serves
 * both realms (discovery, keys, and the token endpoint of each). The allow-list is {@link #ALLOW_LIST} (messy on
 * purpose: spaces, upper case, empty items). No test imports a production type that is new in 29b.
 */
@ExtendWith(OutputCaptureExtension.class)
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
        registry.add("PLATFORM_OPERATOR_EMAILS", () -> ALLOW_LIST);
    }

    private static final String ALLOW_LIST = " Ops@Example.Test ,second@example.test ,, ";
    private static final String ACME_ORIGIN = "http://acme.localhost:8081";
    private static final String CALLBACK_PATH = "/api/v1/auth/callback";
    private static final String AUTHREQ_COOKIE = RedisAuthorizationRequestRepository.COOKIE_NAME;
    private static final String SESSION_COOKIE = SessionTestFixtures.SESSION_COOKIE;
    private static final String OPS_EMAIL = "ops@example.test";
    private static final String MSG06 = "/login?error=MSG06";
    private static final String MSG135 = "/login?error=MSG135";
    private static final String MSG141 = "/login?error=MSG141";

    @Autowired
    private WebSessionStore store;

    @BeforeEach
    void resetFake() {
        FAKE.reset();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
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

    // ================= Task 29b: the callback =================

    // ---- helpers

    /** What a start left behind: the browser's state, the (hashed) nonce, the cookie and the saved PKCE verifier. */
    private record Started(String state, String nonce, String cookie, String verifier) {}

    private Started startAt(String origin, String returnTo) throws Exception {
        MockHttpServletRequestBuilder request = get(origin + "/api/v1/auth/sign-in");
        if (returnTo != null) {
            request.param("returnTo", returnTo);
        }
        MockHttpServletResponse response = perform(request);
        assertThat(response.getStatus()).as("start status").isEqualTo(302);
        String location = response.getHeader(HttpHeaders.LOCATION);
        String cookie = cookieValue(response, AUTHREQ_COOKIE);
        assertThat(cookie).as("auth-request cookie").isNotNull();
        String key = RedisAuthorizationRequestRepository.KEY_PREFIX + AuthorizationUris.sha256Hex(cookie);
        JsonNode saved = json.readTree(redis.opsForValue().get(key));
        return new Started(
                AuthorizationUris.one(location, "state"),
                AuthorizationUris.one(location, "nonce"),
                cookie,
                saved.path("attributes").path("code_verifier").asText());
    }

    private MockHttpServletResponse callbackAt(
            String origin, Map<String, String> query, String authCookie, Cookie... extra) throws Exception {
        MockHttpServletRequestBuilder request = get(origin + CALLBACK_PATH);
        query.forEach(request::param);
        if (authCookie != null) {
            request.cookie(new Cookie(AUTHREQ_COOKIE, authCookie));
        }
        if (extra.length > 0) {
            request.cookie(extra);
        }
        return perform(request);
    }

    private static Map<String, String> codeQuery(Started started, String code) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("code", code);
        query.put("state", started.state());
        return query;
    }

    private static String newCode() {
        return "code-" + UUID.randomUUID();
    }

    /** The result of a Console sign-in: the minted ID token, the code, the start and the callback's answer. */
    private record ConsoleSignIn(String idToken, String code, Started started, MockHttpServletResponse response) {}

    /** Starts on admin., mints the ID token for the nonce (by {@code tokenFor}), stubs a fresh code, calls back. */
    private ConsoleSignIn consoleSignIn(
            String returnTo, java.util.function.Function<String, String> tokenFor, Cookie... extra) throws Exception {
        Started started = startAt(SignInTestFixtures.ADMIN_ORIGIN, returnTo);
        String token = tokenFor.apply(started.nonce());
        String code = newCode();
        FAKE.answerOpsCode(code, token);
        MockHttpServletResponse response =
                callbackAt(SignInTestFixtures.ADMIN_ORIGIN, codeQuery(started, code), started.cookie(), extra);
        return new ConsoleSignIn(token, code, started, response);
    }

    private ConsoleSignIn consoleSignInOf(String email) throws Exception {
        return consoleSignIn(null, nonce -> FAKE.opsIdToken(nonce, email, claims -> {}));
    }

    private void assertAuthRequestCookieExpired(MockHttpServletResponse response) {
        List<String> headers = SignInTestFixtures.setCookie(response, AUTHREQ_COOKIE);
        assertThat(headers).as("Set-Cookie of %s", AUTHREQ_COOKIE).hasSize(1);
        assertThat(SignInTestFixtures.attributes(headers.get(0))).containsEntry("max-age", "0");
    }

    private Set<String> sessionKeys() {
        Set<String> keys = redis.keys(SessionTestFixtures.KEY_PREFIX + "*");
        return keys == null ? Set.of() : new HashSet<>(keys);
    }

    private void assertRefused(MockHttpServletResponse response, String location) {
        assertThat(response.getStatus()).as("status").isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).as("Location").isEqualTo(location);
        assertAuthRequestCookieExpired(response);
        assertThat(SessionTestFixtures.cookieValue(response, SESSION_COOKIE))
                .as("session cookie")
                .isNull();
        assertThat(sessionKeys()).as("session keys").isEmpty();
    }

    private WebSession sessionOf(MockHttpServletResponse response) {
        String cookie = cookieValue(response, SESSION_COOKIE);
        assertThat(cookie).as("session cookie").isNotNull();
        return store.find(cookie).orElseThrow(() -> new AssertionError("no session in Redis for the cookie"));
    }

    private List<Map<String, Object>> accountRows() {
        return TwoTenantDatabases.tenantA().queryForList("SELECT * FROM app_user ORDER BY email");
    }

    // ---- tests

    @ParameterizedTest(name = "[{index}] returnTo={0} oldCookie={1}")
    @CsvSource(
            value = {"/workspaces, true, /workspaces", "NONE, false, /"},
            nullValues = "NONE")
    @DisplayName("AC-001.29 edge 139 creates an Operator session at the callback on admin. for an allow-listed email")
    void createsAnOperatorSessionAtTheCallback_AC_001_29_edge139(
            String returnTo, boolean withOldCookie, String expectedLocation) throws Exception {
        String oldCookie = null;
        if (withOldCookie) {
            oldCookie = store.create(new WebSession(
                    WebSession.Kind.OPERATOR,
                    null,
                    null,
                    OPS_EMAIL,
                    "admin.localhost",
                    "old-hint",
                    clock.instant(),
                    clock.instant()));
        }
        Cookie[] extra = oldCookie == null ? new Cookie[0] : new Cookie[] {new Cookie(SESSION_COOKIE, oldCookie)};

        ConsoleSignIn signIn =
                consoleSignIn(returnTo, nonce -> FAKE.opsIdToken(nonce, "Ops@Example.Test", claims -> {}), extra);
        MockHttpServletResponse response = signIn.response();

        assertThat(response.getStatus()).as("status").isEqualTo(302);
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(location).as("Location").isEqualTo(expectedLocation).doesNotContain("code=");
        List<String> cookies = SignInTestFixtures.setCookie(response, SESSION_COOKIE);
        assertThat(cookies).as("Set-Cookie of the session").hasSize(1);
        Map<String, String> attributes = SignInTestFixtures.attributes(cookies.get(0));
        assertThat(attributes).containsEntry("path", "/").containsKeys("secure", "httponly");
        assertThat(attributes).doesNotContainKey("domain");
        assertThat(attributes.get("samesite")).isEqualToIgnoringCase("Lax");
        assertAuthRequestCookieExpired(response);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

        List<Map<String, String>> opsRequests = FAKE.opsTokenRequests();
        assertThat(opsRequests).as("requests to the tsolve-ops token endpoint").hasSize(1);
        assertThat(opsRequests.get(0))
                .containsEntry("code_verifier", signIn.started().verifier());
        String authorization = FAKE.opsTokenAuthorizations().get(0);
        boolean clientInBasic = authorization.startsWith("Basic ")
                && new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8)
                        .startsWith("tsolve-console:");
        assertThat(clientInBasic || "tsolve-console".equals(opsRequests.get(0).get("client_id")))
                .as("client tsolve-console named in the token request")
                .isTrue();
        assertThat(FAKE.tokenRequests())
                .as("requests to the tsolve token endpoint")
                .isEmpty();

        WebSession session = sessionOf(response);
        assertThat(session.kind()).isEqualTo(WebSession.Kind.OPERATOR);
        assertThat(session.workspaceId()).isNull();
        assertThat(session.accountId()).isNull();
        assertThat(session.operatorEmail()).isEqualToIgnoringCase(OPS_EMAIL);
        assertThat(session.siteHost()).isEqualTo("admin.localhost");
        assertThat(session.idTokenHint()).isEqualTo(signIn.idToken());
        assertThat(sessionKeys()).as("session keys").hasSize(1);
        if (oldCookie != null) {
            assertThat(redis.hasKey(SessionTestFixtures.keyOf(oldCookie)))
                    .as("old session key")
                    .isFalse();
            assertThat(cookieValue(response, SESSION_COOKIE)).isNotEqualTo(oldCookie);
        }
    }

    @ParameterizedTest(name = "[{index}] {0} allowed={1}")
    @CsvSource(
            value = {
                "ops@example.test, true",
                "SECOND@EXAMPLE.TEST, true",
                "ops@example.test.evil, false",
                "xops@example.test, false",
                "ops@example, false",
                "ops@example.tes, false",
                "EMPTY, false"
            },
            nullValues = "NONE")
    @DisplayName("AC-001.29 edge 35 matches the allow-list trimmed and ignoring case")
    void matchesTheAllowListTrimmedAndIgnoringCase_AC_001_29_edge35(String email, boolean allowed) throws Exception {
        String tokenEmail = "EMPTY".equals(email) ? "" : email;

        ConsoleSignIn signIn = consoleSignInOf(tokenEmail);

        if (allowed) {
            assertThat(signIn.response().getStatus()).isEqualTo(302);
            WebSession session = sessionOf(signIn.response());
            assertThat(session.kind()).isEqualTo(WebSession.Kind.OPERATOR);
            assertThat(session.operatorEmail()).isEqualToIgnoringCase(tokenEmail);
        } else {
            assertRefused(signIn.response(), MSG135);
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"intruder@example.test", "alice@acme.test"})
    @DisplayName("AC-001.30 refuses a verified email that is not in the allow-list with MSG135 on admin.")
    void refusesAnEmailThatIsNotInTheAllowList_AC_001_30(String email, CapturedOutput output) throws Exception {
        SignInTestFixtures.seedAccount(TwoTenantDatabases.tenantA(), "alice@acme.test", "Alice");
        List<Map<String, Object>> before = accountRows();

        ConsoleSignIn signIn = consoleSignInOf(email);

        assertRefused(signIn.response(), MSG135);
        assertThat(accountRows()).as("tenant A's Accounts").isEqualTo(before);
        assertThat(output.getAll())
                .as("captured log")
                .doesNotContain(signIn.idToken())
                .doesNotContain(signIn.started().cookie());
    }

    /** Arranges the fake Keycloak for one callback and answers the query the browser would send. */
    @FunctionalInterface
    interface Arrange {
        Map<String, String> apply(Started started, String code);
    }

    private static Arguments row(String label, Arrange arrange) {
        return Arguments.of(label, arrange);
    }

    private static Arrange tokenOf(java.util.function.BiFunction<String, String, String> idToken) {
        return (started, code) -> {
            FAKE.answerOpsCode(code, idToken.apply(started.nonce(), "intruder@example.test"));
            return codeQuery(started, code);
        };
    }

    private static Arrange goodTokenEdited(java.util.function.Consumer<Map<String, Object>> edit) {
        return (started, code) -> {
            FAKE.answerOpsCode(code, FAKE.opsIdToken(started.nonce(), OPS_EMAIL, edit));
            return codeQuery(started, code);
        };
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                row("error=access_denied", (s, c) -> Map.of("error", "access_denied", "state", s.state())),
                row("error=login_required", (s, c) -> Map.of("error", "login_required", "state", s.state())),
                row("token endpoint 400", (s, c) -> {
                    FAKE.answerOpsCodeStatus(c, 400, "{\"error\":\"invalid_grant\"}");
                    return codeQuery(s, c);
                }),
                row("token endpoint 500", (s, c) -> {
                    FAKE.answerOpsCodeStatus(c, 500, "{\"error\":\"server_error\"}");
                    return codeQuery(s, c);
                }),
                row("connection reset", (s, c) -> {
                    FAKE.answerOpsCodeFault(c, Fault.CONNECTION_RESET_BY_PEER);
                    return codeQuery(s, c);
                }),
                row("signed with another key", (s, c) -> {
                    FAKE.answerOpsCode(c, FAKE.opsSignedWithOtherKey(s.nonce(), OPS_EMAIL, claims -> {}));
                    return codeQuery(s, c);
                }),
                row("iss of the realm tsolve", goodTokenEdited(claims -> claims.put("iss", FAKE.issuer()))),
                row("aud tsolve-web", goodTokenEdited(claims -> claims.put("aud", "tsolve-web"))),
                row("expired", goodTokenEdited(claims -> {
                    long now = Instant.now().getEpochSecond();
                    claims.put("iat", now - 7200);
                    claims.put("exp", now - 3600);
                })),
                row("the nonce of another start", goodTokenEdited(claims -> claims.put("nonce", "another-start"))),
                row("email_verified false", goodTokenEdited(claims -> claims.put("email_verified", false))),
                row("email_verified missing", goodTokenEdited(claims -> claims.remove("email_verified"))),
                row("verified but no email", goodTokenEdited(claims -> claims.remove("email"))),
                row(
                        "not allow-listed and email_verified false: MSG06, not MSG135",
                        tokenOf((nonce, email) ->
                                FAKE.opsIdToken(nonce, email, claims -> claims.put("email_verified", false)))));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("failures")
    @DisplayName("AC-001.31 answers MSG06 on admin. when Keycloak returns an error or the exchange or the ID token"
            + " check fails")
    void answersMsg06WhenKeycloakOrTheTokenFails_AC_001_31(String label, Arrange arrange) throws Exception {
        Started started = startAt(SignInTestFixtures.ADMIN_ORIGIN, null);
        String code = newCode();
        Map<String, String> query = arrange.apply(started, code);

        MockHttpServletResponse response = callbackAt(SignInTestFixtures.ADMIN_ORIGIN, query, started.cookie());

        assertRefused(response, MSG06);
        // the request is used up
        MockHttpServletResponse again = callbackAt(SignInTestFixtures.ADMIN_ORIGIN, query, started.cookie());
        assertRefused(again, MSG06);
    }

    static Stream<Arguments> otherRequests() {
        return Stream.of(
                Arguments.of("an Acme request on admin.", "admin.localhost"),
                Arguments.of("a Console request on acme.", "acme.localhost"),
                Arguments.of("no cookie on admin.", "no-cookie"),
                Arguments.of("a state that does not match on admin.", "wrong-state"),
                Arguments.of("a request already used on admin.", "used"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("otherRequests")
    @DisplayName("AC-001.29 edge 122 edge 140 refuses a saved request of the other site kind or a missing one")
    void refusesASavedRequestOfTheOtherSiteKindOrAMissingOne_AC_001_29_edge122_edge140(String label, String scenario)
            throws Exception {
        String origin = SignInTestFixtures.ADMIN_ORIGIN;
        String expected = MSG06;
        Started started;
        Map<String, String> query;
        String cookie;
        switch (scenario) {
            case "admin.localhost" -> {
                started = startAt(ACME_ORIGIN, null);
                cookie = started.cookie();
                query = codeQuery(started, newCode());
            }
            case "acme.localhost" -> {
                started = startAt(SignInTestFixtures.ADMIN_ORIGIN, null);
                origin = ACME_ORIGIN;
                expected = MSG141;
                cookie = started.cookie();
                query = codeQuery(started, newCode());
            }
            case "no-cookie" -> {
                started = startAt(SignInTestFixtures.ADMIN_ORIGIN, null);
                cookie = null;
                query = codeQuery(started, newCode());
            }
            case "wrong-state" -> {
                started = startAt(SignInTestFixtures.ADMIN_ORIGIN, null);
                cookie = started.cookie();
                query = new LinkedHashMap<>(codeQuery(started, newCode()));
                query.put("state", "not-the-state-of-this-start");
            }
            default -> {
                started = startAt(SignInTestFixtures.ADMIN_ORIGIN, null);
                cookie = started.cookie();
                // a failing callback uses the request up
                callbackAt(origin, Map.of("error", "access_denied", "state", started.state()), cookie);
                query = codeQuery(started, newCode());
            }
        }
        FAKE.answerOpsCode(query.getOrDefault("code", ""), FAKE.opsIdToken(started.nonce(), OPS_EMAIL, claims -> {}));

        MockHttpServletResponse response = callbackAt(origin, query, cookie);

        assertRefused(response, expected);
        assertThat(FAKE.opsTokenRequests()).as("ops token requests").isEmpty();
        assertThat(FAKE.tokenRequests()).as("tsolve token requests").isEmpty();
    }

    @Test
    @DisplayName("AC-001.29 edge 36 gives an Operator with a Workspace Account only an Operator session on admin. and"
            + " only a Workspace session on acme.")
    void keepsTheOperatorAndTheWorkspaceSessionApart_AC_001_29_edge36() throws Exception {
        SignInTestFixtures.seedAccount(TwoTenantDatabases.tenantA(), OPS_EMAIL, "Ops At Acme");
        List<Map<String, Object>> before = accountRows();

        ConsoleSignIn console = consoleSignInOf(OPS_EMAIL);

        WebSession operator = sessionOf(console.response());
        assertThat(operator.kind()).isEqualTo(WebSession.Kind.OPERATOR);
        assertThat(operator.accountId()).isNull();
        assertThat(operator.workspaceId()).isNull();
        assertThat(accountRows())
                .as("tenant A's Accounts after the Console sign-in")
                .isEqualTo(before);

        Started started = startAt(ACME_ORIGIN, null);
        String code = newCode();
        FAKE.answerCode(
                code,
                FAKE.idToken(
                        started.nonce(),
                        claims ->
                                claims.putAll(SessionTestFixtures.claimsOf(InMemoryWorkspaceLookup.ACME, OPS_EMAIL))));
        MockHttpServletResponse response = callbackAt(ACME_ORIGIN, codeQuery(started, code), started.cookie());

        assertThat(response.getStatus()).isEqualTo(302);
        WebSession workspace = sessionOf(response);
        UUID accountId = TwoTenantDatabases.tenantA()
                .queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, OPS_EMAIL);
        assertThat(workspace.kind()).isEqualTo(WebSession.Kind.WORKSPACE);
        assertThat(workspace.accountId()).isEqualTo(accountId);
        assertThat(workspace.workspaceId()).isEqualTo(InMemoryWorkspaceLookup.ACME.id());
        assertThat(workspace.operatorEmail()).isNull();
    }
}
