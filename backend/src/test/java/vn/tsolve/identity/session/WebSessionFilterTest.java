package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;
import static vn.tsolve.identity.session.SessionTestFixtures.randomCookieValue;
import static vn.tsolve.identity.session.SessionTestFixtures.sha256Hex;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.signin.RedisAuthorizationRequestRepository;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Every request is checked against its session (AC-001.20 to .25 and .27). Sessions are made with {@link
 * WebSessionStore#create}; time moves with the test clock, so the limits are tested whatever the real Redis time to
 * live. Roles are not asserted here: AC-001.26 is Task 26b.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(WhoAmITestController.class)
class WebSessionFilterTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;
    private static final String ACME_HOST = ACME.siteHost();
    private static final String BETA_HOST = BETA.siteHost();
    private static final String ADMIN_HOST = "admin.localhost";
    private static final String BACKGROUND = WebSessionFilter.BACKGROUND_HEADER;
    private static final String ID_TOKEN_HINT = "id-token-hint-value-that-must-stay-out-of-the-log";
    private static final String MSG07_TEXT = "Your session has expired. Please log in again.";
    private static final String MSG123_TEXT = "Your session is not valid on this site. Please log in again.";
    private static final String MSG10_TEXT = "Your T-Solve account has been deactivated. Please contact your Admin.";

    @Autowired
    private WebSessionStore store;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @SpyBean
    private AccountRepository accountRepository;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void cleanTenants() {
        WhoAmITestController.reset();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
    }

    // ---- helpers

    private static JdbcTemplate dbOf(WorkspaceRef workspace) {
        return workspace.id().equals(ACME.id()) ? TwoTenantDatabases.tenantA() : TwoTenantDatabases.tenantB();
    }

    private UUID seed(WorkspaceRef workspace, String email, String fullName, String status) {
        UUID id = UUID.randomUUID();
        seedWithId(workspace, id, email, fullName, status);
        return id;
    }

    private void seedWithId(WorkspaceRef workspace, UUID id, String email, String fullName, String status) {
        dbOf(workspace)
                .update(
                        "INSERT INTO app_user (id, email, full_name, status, created_at) VALUES (?, ?, ?, ?, now())",
                        id,
                        email,
                        fullName,
                        status);
    }

    private String sessionOf(WorkspaceRef workspace, UUID account) {
        return store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                workspace.id(),
                account,
                null,
                workspace.siteHost(),
                ID_TOKEN_HINT,
                clock.instant(),
                clock.instant()));
    }

    private MockHttpServletRequestBuilder request(String host, String cookieName, String cookieValue) {
        MockHttpServletRequestBuilder request = get(url(host, "/api/v1/test/whoami"));
        if (cookieValue != null) {
            request.cookie(new Cookie(cookieName, cookieValue));
        }
        return request;
    }

    private MockHttpServletResponse whoami(String host, String cookie) throws Exception {
        return perform(request(host, SESSION_COOKIE, cookie));
    }

    private MockHttpServletResponse background(String host, String cookie) throws Exception {
        return perform(request(host, SESSION_COOKIE, cookie).header(BACKGROUND, "1"));
    }

    /** Moves the clock, then sends a foreground request that must be answered 200 (it counts as activity). */
    private void stay(String host, String cookie, long minutes) throws Exception {
        clock.advance(Duration.ofMinutes(minutes));
        assertThat(whoami(host, cookie).getStatus())
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
        assertThat(body(response).path("detail").asText()).as("detail").isEqualTo(detail);
    }

    private void assertMsg07(MockHttpServletResponse response) throws Exception {
        assertProblem(response, 401, "MSG07", MSG07_TEXT);
    }

    private void assertMsg123(MockHttpServletResponse response) throws Exception {
        assertProblem(response, 401, "MSG123", MSG123_TEXT);
    }

    private static void assertSessionCookieCleared(MockHttpServletResponse response) {
        List<String> headers = SignInTestFixtures.setCookie(response, SESSION_COOKIE);
        assertThat(headers).as("Set-Cookie of %s", SESSION_COOKIE).hasSize(1);
        assertThat(headers.get(0)).startsWith(SESSION_COOKIE + "=;");
        Map<String, String> attributes = SignInTestFixtures.attributes(headers.get(0));
        assertThat(attributes).containsEntry("max-age", "0").containsEntry("path", "/");
        assertThat(attributes).containsKeys("secure", "httponly").doesNotContainKey("domain");
        assertThat(attributes.get("samesite")).isEqualToIgnoringCase("Lax");
    }

    private static void assertNoSessionCookieHeader(MockHttpServletResponse response) {
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE)).isEmpty();
    }

    private Object lastActivity(String cookie) {
        return redis.opsForHash().get(keyOf(cookie), "last_activity_at");
    }

    private boolean keyExists(String cookie) {
        return Boolean.TRUE.equals(redis.hasKey(keyOf(cookie)));
    }

    // ---- host and kind checks

    @Test
    @DisplayName("AC-001.20 acme's session on beta: 401 MSG123, endpoint not run, session kept")
    void acmeSessionOnBeta_AC_001_20() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        Object before = lastActivity(cookie);
        clock.advance(Duration.ofMinutes(5));

        MockHttpServletResponse response = whoami(BETA_HOST, cookie);

        assertMsg123(response);
        assertThat(WhoAmITestController.calls()).isZero();
        assertThat(keyExists(cookie)).isTrue();
        assertThat(lastActivity(cookie)).isEqualTo(before);
        assertNoSessionCookieHeader(response);
    }

    @Test
    @DisplayName("AC-001.20 beta's session on acme: 401 MSG123, endpoint not run, session kept")
    void betaSessionOnAcme_AC_001_20() throws Exception {
        String cookie = sessionOf(BETA, seed(BETA, "b@beta.example", "B", "ACTIVE"));
        Object before = lastActivity(cookie);
        clock.advance(Duration.ofMinutes(5));

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertMsg123(response);
        assertThat(WhoAmITestController.calls()).isZero();
        assertThat(keyExists(cookie)).isTrue();
        assertThat(lastActivity(cookie)).isEqualTo(before);
        assertNoSessionCookieHeader(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"acme", "beta"})
    @DisplayName("AC-001.21 a session of either Workspace on admin.: 401 MSG123, endpoint not run, session kept")
    void workspaceSessionOnAdmin_AC_001_21(String which) throws Exception {
        WorkspaceRef workspace = "acme".equals(which) ? ACME : BETA;
        String cookie = sessionOf(workspace, seed(workspace, "a@" + which + ".example", "A", "ACTIVE"));
        Object before = lastActivity(cookie);
        clock.advance(Duration.ofMinutes(5));

        MockHttpServletResponse response = whoami(ADMIN_HOST, cookie);

        assertMsg123(response);
        assertThat(WhoAmITestController.calls()).isZero();
        assertThat(keyExists(cookie)).isTrue();
        assertThat(lastActivity(cookie)).isEqualTo(before);
        assertNoSessionCookieHeader(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"login.localhost", "api.localhost"})
    @DisplayName("AC-001.22 a valid session cookie on api. or login. is no session")
    void sessionOnLoginOrApi_AC_001_22(String host) throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        Object before = lastActivity(cookie);
        clock.advance(Duration.ofMinutes(5));

        MockHttpServletResponse response = whoami(host, cookie);

        assertMsg07(response);
        assertThat(WhoAmITestController.calls()).isZero();
        assertThat(keyExists(cookie)).isTrue();
        assertThat(lastActivity(cookie)).isEqualTo(before);
    }

    @Test
    @DisplayName("edge 17 a valid session cookie on an unknown host is no session")
    void sessionOnUnknownHost_edge17() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        Object before = lastActivity(cookie);
        clock.advance(Duration.ofMinutes(5));

        MockHttpServletResponse response = whoami("evil.localhost", cookie);

        assertMsg07(response);
        assertThat(WhoAmITestController.calls()).isZero();
        assertThat(keyExists(cookie)).isTrue();
        assertThat(lastActivity(cookie)).isEqualTo(before);
    }

    // ---- the limits

    @Test
    @DisplayName("AC-001.23 a session created more than 8 hours ago is ended with MSG07")
    void olderThanEightHours_AC_001_23() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        for (int i = 0; i < 8; i++) {
            stay(ACME_HOST, cookie, 59); // activity every 59 minutes keeps the idle limit away: 472 minutes
        }
        clock.advance(Duration.ofMinutes(9)); // 481 minutes old, idle 9
        int callsBefore = WhoAmITestController.calls();

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertMsg07(response);
        assertSessionCookieCleared(response);
        assertThat(keyExists(cookie)).isFalse();
        assertThat(WhoAmITestController.calls()).isEqualTo(callsBefore);
    }

    @Test
    @DisplayName("AC-001.23 a session idle more than 60 minutes is ended with MSG07")
    void idleMoreThanSixtyMinutes_AC_001_23() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        clock.advance(Duration.ofMinutes(60).plusSeconds(1));

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertMsg07(response);
        assertSessionCookieCleared(response);
        assertThat(keyExists(cookie)).isFalse();
        assertThat(WhoAmITestController.calls()).isZero();
    }

    @Test
    @DisplayName("AC-001.23 a session idle 59 minutes and 7 h 59 min old is valid")
    void justInsideTheLimits_AC_001_23() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        stay(ACME_HOST, cookie, 59); // idle 59 minutes
        for (int i = 0; i < 7; i++) {
            stay(ACME_HOST, cookie, 59); // 472 minutes old
        }
        stay(ACME_HOST, cookie, 7); // 479 minutes = 7 h 59 min old, idle 7

        assertThat(keyExists(cookie)).isTrue();
    }

    @Test
    @DisplayName("edge 18 exactly 60 idle minutes and exactly 8 hours are still valid")
    void exactlyOnTheLimits_edge18() throws Exception {
        String idle = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        clock.advance(Duration.ofMinutes(60));
        assertThat(whoami(ACME_HOST, idle).getStatus())
                .as("idle exactly 60 minutes")
                .isEqualTo(200);

        String old = sessionOf(ACME, seed(ACME, "b@acme.example", "B", "ACTIVE"));
        for (int i = 0; i < 8; i++) {
            stay(ACME_HOST, old, 59);
        }
        stay(ACME_HOST, old, 8); // exactly 480 minutes after creation
        assertThat(keyExists(old)).isTrue();
    }

    // ---- activity

    @Test
    @DisplayName("AC-001.24 a foreground request restarts the 60-minute limit")
    void foregroundRestartsTheLimit_AC_001_24() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        Object created = lastActivity(cookie);

        stay(ACME_HOST, cookie, 59);
        assertThat(lastActivity(cookie)).isNotEqualTo(created);
        stay(ACME_HOST, cookie, 59); // 118 minutes after creation, still valid

        clock.advance(Duration.ofMinutes(61));
        assertMsg07(whoami(ACME_HOST, cookie));
    }

    @Test
    @DisplayName("AC-001.25 a background request is answered but is not activity")
    void backgroundIsNotActivity_AC_001_25() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        Object before = lastActivity(cookie);
        clock.advance(Duration.ofMinutes(10));

        MockHttpServletResponse response = background(ACME_HOST, cookie);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(WhoAmITestController.calls()).isEqualTo(1);
        assertThat(lastActivity(cookie)).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-001.25 background polling alone ends the session after 60 minutes")
    void pollingAloneEndsTheSession_AC_001_25() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        clock.advance(Duration.ofMinutes(30));
        assertThat(background(ACME_HOST, cookie).getStatus()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(31)); // 61 minutes since the last real activity

        MockHttpServletResponse response = background(ACME_HOST, cookie);

        assertMsg07(response);
        assertThat(keyExists(cookie)).isFalse();
    }

    @Test
    @DisplayName("AC-001.25 the 8-hour limit applies to a background request")
    void eightHourLimitAppliesToBackground_AC_001_25() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        for (int i = 0; i < 8; i++) {
            stay(ACME_HOST, cookie, 59);
        }
        stay(ACME_HOST, cookie, 8); // exactly 480 minutes
        clock.advance(Duration.ofMinutes(1)); // 481 minutes old, idle 1
        int callsBefore = WhoAmITestController.calls();

        MockHttpServletResponse response = background(ACME_HOST, cookie);

        assertMsg07(response);
        assertThat(keyExists(cookie)).isFalse();
        assertThat(WhoAmITestController.calls()).isEqualTo(callsBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "true", "2", "yes", ""})
    @DisplayName("edge 19 X-TSolve-Background with any value other than 1 counts as activity")
    void otherBackgroundValuesAreActivity_edge19(String value) throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        Object before = lastActivity(cookie);
        clock.advance(Duration.ofMinutes(10));

        MockHttpServletResponse response =
                perform(request(ACME_HOST, SESSION_COOKIE, cookie).header(BACKGROUND, value));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(lastActivity(cookie)).isNotEqualTo(before);
    }

    // ---- the Account re-read

    @Test
    @DisplayName("edge 32 the endpoint sees a TSolveUser with the Account's current id, email and full name")
    void endpointSeesTheAccount_edge32() throws Exception {
        UUID account = seed(ACME, "admin@acme.example", "Acme Admin", "ACTIVE");
        String cookie = sessionOf(ACME, account);

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode user = body(response);
        assertThat(user.path("id").asText()).isEqualTo(account.toString());
        assertThat(user.path("email").asText()).isEqualTo("admin@acme.example");
        assertThat(user.path("fullName").asText()).isEqualTo("Acme Admin");
    }

    @Test
    @DisplayName("edge 20 the Account is re-read inside TenantScope.runIn of the session's Workspace")
    void accountIsReadInTheSessionsWorkspace_edge20() throws Exception {
        UUID shared = UUID.randomUUID();
        seedWithId(ACME, shared, "same@acme.example", "Acme Person", "ACTIVE");
        seedWithId(BETA, shared, "same@beta.example", "Beta Person", "ACTIVE");
        String acme = sessionOf(ACME, shared);
        String beta = sessionOf(BETA, shared);

        JsonNode onAcme = body(whoami(ACME_HOST, acme));
        JsonNode onBeta = body(whoami(BETA_HOST, beta));

        assertThat(onAcme.path("fullName").asText()).isEqualTo("Acme Person");
        assertThat(onBeta.path("fullName").asText()).isEqualTo("Beta Person");
        assertThat(onBeta.path("email").asText()).isEqualTo("same@beta.example");
    }

    @Test
    @DisplayName("AC-001.27 a Deactivated Account: session ended, 401 MSG10, endpoint not run, and the next request"
            + " with that cookie is 401 MSG07")
    void deactivatedAccount_AC_001_27() throws Exception {
        UUID account = seed(ACME, "gone@acme.example", "Gone", "DEACTIVATED");
        String cookie = sessionOf(ACME, account);

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertProblem(response, 401, "MSG10", MSG10_TEXT);
        assertSessionCookieCleared(response);
        assertThat(keyExists(cookie)).isFalse();
        assertThat(WhoAmITestController.calls()).isZero();

        MockHttpServletResponse next = whoami(ACME_HOST, cookie);
        assertMsg07(next);
        assertThat(WhoAmITestController.calls()).isZero();
    }

    @Test
    @DisplayName("edge 21 order: an expired session presented on another Workspace site gets MSG123 and is kept")
    void expiredSessionOnOtherSite_edge21() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        clock.advance(Duration.ofMinutes(61));

        MockHttpServletResponse response = whoami(BETA_HOST, cookie);

        assertMsg123(response);
        assertThat(keyExists(cookie)).isTrue();
        assertNoSessionCookieHeader(response);
    }

    @Test
    @DisplayName("edge 22 order: an expired session of a Deactivated Account gets MSG07")
    void expiredSessionOfDeactivatedAccount_edge22() throws Exception {
        UUID account = seed(ACME, "gone@acme.example", "Gone", "DEACTIVATED");
        String cookie = sessionOf(ACME, account);
        clock.advance(Duration.ofMinutes(61));

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertMsg07(response);
        assertThat(keyExists(cookie)).isFalse();
        assertThat(WhoAmITestController.calls()).isZero();
    }

    // ---- dead cookies and failures

    @Test
    @DisplayName("edge 23 a lost key (Redis restart or eviction): 401 MSG07 on a protected endpoint and"
            + " __Host-TSOLVE_SESSION=; Max-Age=0")
    void lostKey_edge23() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        redis.delete(keyOf(cookie));

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertMsg07(response);
        assertSessionCookieCleared(response);
        assertThat(WhoAmITestController.calls()).isZero();
    }

    @Test
    @DisplayName("edge 24 a half-written key (only last_activity_at): 401 MSG07, endpoint not run, and the cookie"
            + " cleared with Max-Age=0")
    void halfWrittenKey_edge24() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        redis.opsForHash()
                .delete(
                        keyOf(cookie),
                        "kind",
                        "workspace_id",
                        "account_id",
                        "operator_email",
                        "site_host",
                        "id_token_hint",
                        "created_at");
        assertThat(redis.opsForHash().keys(keyOf(cookie))).containsExactly("last_activity_at");

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertMsg07(response);
        assertSessionCookieCleared(response);
        assertThat(WhoAmITestController.calls()).isZero();
    }

    @Test
    @DisplayName("edge 25 a cookie without the __Host- prefix is ignored, and a random __Host- value gets MSG07 and"
            + " is cleared with Max-Age=0")
    void unprefixedOrRandomCookie_edge25() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        Object before = lastActivity(cookie);

        MockHttpServletResponse unprefixed = perform(request(ACME_HOST, "TSOLVE_SESSION", cookie));
        assertMsg07(unprefixed);
        assertThat(keyExists(cookie)).isTrue();
        assertThat(lastActivity(cookie)).isEqualTo(before);

        MockHttpServletResponse random = whoami(ACME_HOST, randomCookieValue());
        assertMsg07(random);
        assertSessionCookieCleared(random);
        assertThat(WhoAmITestController.calls()).isZero();
    }

    @Test
    @DisplayName("edge 26 the database down at the Account re-read: 500 generic, endpoint not run")
    void databaseDownAtReRead_edge26() throws Exception {
        String cookie = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        doThrow(new DataAccessResourceFailureException("database is down"))
                .when(accountRepository)
                .findById(any(UUID.class));

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body(response).path("code").asText()).isEqualTo("MSG06");
        assertThat(WhoAmITestController.calls()).isZero();
    }

    @Test
    @DisplayName("edge 27 no cookie value, session key hash or ID token hint in the log for MSG07, MSG10 and MSG123")
    void nothingSecretInTheLog_edge27(CapturedOutput output) throws Exception {
        String deactivated = sessionOf(ACME, seed(ACME, "gone@acme.example", "Gone", "DEACTIVATED"));
        String mismatch = sessionOf(ACME, seed(ACME, "a@acme.example", "A", "ACTIVE"));
        String expired = sessionOf(ACME, seed(ACME, "b@acme.example", "B", "ACTIVE"));

        assertProblem(whoami(ACME_HOST, deactivated), 401, "MSG10", MSG10_TEXT);
        assertMsg123(whoami(BETA_HOST, mismatch));
        clock.advance(Duration.ofMinutes(61));
        assertMsg07(whoami(ACME_HOST, expired));

        String log = output.getAll();
        assertThat(log).doesNotContain(ID_TOKEN_HINT);
        for (String cookie : List.of(deactivated, mismatch, expired)) {
            assertThat(log).doesNotContain(cookie).doesNotContain(sha256Hex(cookie));
        }
    }

    @Test
    @DisplayName("edge 28 the Account row gone at the re-read: session ended, 401 MSG07, endpoint not run")
    void accountRowGone_edge28() throws Exception {
        UUID account = seed(ACME, "a@acme.example", "A", "ACTIVE");
        String cookie = sessionOf(ACME, account);
        TwoTenantDatabases.tenantA().update("DELETE FROM app_user WHERE id = ?", account);

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertMsg07(response);
        assertSessionCookieCleared(response);
        assertThat(keyExists(cookie)).isFalse();
        assertThat(WhoAmITestController.calls()).isZero();
    }

    @Test
    @DisplayName("edge 43 an Account whose full_name is null at the re-read: 200, TSolveUser.fullName is empty")
    void accountWithoutFullName_edge43() throws Exception {
        UUID account = seed(ACME, "noname@acme.example", null, "ACTIVE");
        String cookie = sessionOf(ACME, account);

        MockHttpServletResponse response = whoami(ACME_HOST, cookie);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).path("fullName").asText("missing")).isEmpty();
        assertThat(body(response).path("id").asText()).isEqualTo(account.toString());
    }

    @Test
    @DisplayName("edge 44 a dead session cookie on a public endpoint: the endpoint still runs and the answer clears"
            + " the cookie")
    void deadCookieOnPublicEndpoint_edge44() throws Exception {
        MockHttpServletResponse health = perform(
                get(url(ACME_HOST, "/actuator/health")).cookie(new Cookie(SESSION_COOKIE, randomCookieValue())));

        assertThat(health.getStatus()).isEqualTo(200);
        assertSessionCookieCleared(health);
    }

    @Test
    @DisplayName("edge 44 a dead session cookie on the sign-in start: its usual 302 with one auth-request cookie"
            + " (the filter is not on that chain)")
    void deadCookieOnSignInStart_edge44() throws Exception {
        MockHttpServletResponse start = perform(
                get(url(ACME_HOST, "/api/v1/auth/sign-in")).cookie(new Cookie(SESSION_COOKIE, randomCookieValue())));

        assertThat(start.getStatus()).isEqualTo(302);
        assertThat(SignInTestFixtures.setCookie(start, RedisAuthorizationRequestRepository.COOKIE_NAME))
                .hasSize(1);
        assertNoSessionCookieHeader(start);
    }

    @Test
    @DisplayName("edge 49 one session model: the web chain holds one WebSessionFilter, and the PR #119 classes are not"
            + " carried in")
    void oneSessionModel_edge49() {
        List<Filter> filters = filterChainProxy.getFilters("/api/v1/test/whoami");

        assertThat(filters.stream().filter(WebSessionFilter.class::isInstance)).hasSize(1);
        for (String name : List.of(
                "vn.tsolve.identity.session.UserListSessionFilter",
                "vn.tsolve.identity.session.WorkspaceSessionBinding",
                "vn.tsolve.common.tenant.WorkspaceSessionBinding")) {
            assertThatThrownBy(() -> Class.forName(name)).as(name).isInstanceOf(ClassNotFoundException.class);
        }
    }
}
