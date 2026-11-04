package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.github.tomakehurst.wiremock.http.Fault;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.identity.AuthSecurityConfig;
import vn.tsolve.identity.signin.SignInTestFixtures.BadState;
import vn.tsolve.identity.signin.SignInTestFixtures.StateStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * The callback with the real {@code authorize} (so every good cookie is one the server made), the real state
 * service, and a fake Google (WireMock) for the token and JWKS endpoints. Only the repository is in memory.
 */
@WebMvcTest({CallbackController.class, AuthorizeController.class})
@Import({
    SecurityConfig.class,
    AuthSecurityConfig.class,
    AuthHosts.class,
    SignInStateService.class,
    SignedToken.class,
    GoogleOidcClient.class,
    SignInCookie.class,
    IdTokenVerifier.class,
    SignInTestBeans.class
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
            "server.port=0",
            "logging.level.vn.tsolve.identity=DEBUG"
        })
@ExtendWith(OutputCaptureExtension.class)
class CallbackControllerTest {

    private static final String CALLBACK_URL = "http://login.localhost:8081/api/v1/auth/callback";
    private static final String CODE = "4/0A-the-authorization-code";
    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StateStore store;

    @Autowired
    private SignInStateService states;

    @Autowired
    private SignedToken signedToken;

    @Autowired
    private MutableClock clock;

    @Autowired
    private InMemoryWorkspaceLookup workspaces;

    @DynamicPropertySource
    static void fakeGoogle(DynamicPropertyRegistry registry) {
        registry.add("tsolve.oidc.google.token-uri", FakeGoogleIssuer::tokenUri);
        registry.add("tsolve.oidc.google.jwk-set-uri", FakeGoogleIssuer::jwksUri);
        registry.add("tsolve.oidc.google.connect-timeout", () -> "200ms");
        registry.add("tsolve.oidc.google.read-timeout", () -> "200ms");
    }

    @BeforeEach
    void reset() {
        store.reset();
        clock.reset();
        workspaces.reset();
        FakeGoogleIssuer.reset();
    }

    // ---- helpers

    /** One sign-in attempt that went through the real {@code authorize}: the state, the cookie it set, its row. */
    private record Browser(String state, String cookie, SignInState row) {}

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private Browser authorized(WorkspaceRef workspace) throws Exception {
        return authorizeState(states.createForWorkspace(workspace.id(), "/"));
    }

    private Browser authorizedConsole() throws Exception {
        return authorizeState(states.createForPlatformConsole("/"));
    }

    private Browser authorizedJiraLink(WorkspaceRef workspace) throws Exception {
        return authorizeState(
                SignInTestFixtures.plantJiraLinkState(store, signedToken, clock, workspace.id(), "tracker-account-1"));
    }

    private Browser authorizeState(String state) throws Exception {
        SignInState row = states.check(state).orElseThrow();
        MockHttpServletResponse response =
                perform(get("http://login.localhost:8081/api/v1/auth/authorize").param("state", state));
        String cookie = SignInTestFixtures.signInCookieValue(response);
        assertThat(cookie).as("the sign-in cookie that authorize sets").isNotNull();
        return new Browser(state, cookie, row);
    }

    /** A state made but never sent through authorize. */
    private Browser notAuthorized(WorkspaceRef workspace) {
        String state = states.createForWorkspace(workspace.id(), "/");
        return new Browser(state, null, states.check(state).orElseThrow());
    }

    private static MockHttpServletRequestBuilder callback(String state, String cookie) {
        MockHttpServletRequestBuilder request = get(CALLBACK_URL);
        if (state != null) {
            request.param("state", state);
        }
        if (cookie != null) {
            request.cookie(new Cookie(SignInTestFixtures.SIGN_IN_COOKIE, cookie));
        }
        return request;
    }

    private MockHttpServletResponse callWithCode(Browser browser) throws Exception {
        return perform(callback(browser.state(), browser.cookie()).param("code", CODE));
    }

    private MockHttpServletResponse callWithCode(Browser browser, String cookie) throws Exception {
        return perform(callback(browser.state(), cookie).param("code", CODE));
    }

    private MockHttpServletResponse callWithError(Browser browser, String error) throws Exception {
        return perform(callback(browser.state(), browser.cookie()).param("error", error));
    }

    private static void answerValidToken(Browser browser) {
        FakeGoogleIssuer.answerToken(FakeGoogleIssuer.idToken(browser.row().getNonce(), claims -> {}));
    }

    private static void answerValidConsoleToken(Browser browser) {
        FakeGoogleIssuer.answerToken(FakeGoogleIssuer.idToken(browser.row().getNonce(), claims -> {
            claims.remove("hd");
            claims.put("email", "ops@example.test");
        }));
    }

    private static String site(WorkspaceRef workspace) {
        return "http://" + workspace.siteHost() + ":8081/login?error=MSG06";
    }

    private static void assertMsg06(MockHttpServletResponse response, WorkspaceRef workspace) {
        assertThat(response.getStatus()).as("status").isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).as("Location").isEqualTo(site(workspace));
        SignInTestFixtures.assertExpiredSignInCookie(response);
        SignInTestFixtures.assertNoSession(response);
    }

    private static void assertConsumed(Browser browser) {
        assertThat(browser.row().getUsedAt()).as("used_at").isEqualTo(MutableClock.START);
    }

    private static void assertNotConsumed(Browser browser) {
        assertThat(browser.row().getUsedAt()).as("used_at").isNull();
    }

    private static void assertNoGoogleCall() {
        assertThat(FakeGoogleIssuer.tokenRequests())
                .as("requests to Google's token endpoint")
                .isEmpty();
    }

    private static byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
    }

    // ---- State value checks (AC-001.6 at the callback, row 20)

    @Test
    @DisplayName("AC-001.6 answers the MSG141 page when the callback has no state")
    void answersMsg141WhenThereIsNoState_AC_001_6() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);

        MockHttpServletResponse response =
                perform(callback(null, browser.cookie()).param("code", CODE));

        SignInTestFixtures.assertCallbackMsg141Page(response);
        assertNoGoogleCall();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = BadState.class,
            names = {"PAYLOAD_EDITED", "MAC_EDITED", "BARE_ID", "SIGNED_WITH_OTHER_KEY", "EMPTY"})
    @DisplayName("AC-001.6 / edge 20 answers MSG141 for an edited, unsigned or foreign state")
    void answersMsg141ForUntrustedState_AC_001_6_edge20(BadState bad) throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);

        MockHttpServletResponse response = perform(callback(bad.make(browser.state(), signedToken), browser.cookie())
                .param("code", CODE));

        SignInTestFixtures.assertCallbackMsg141Page(response);
        assertNoGoogleCall();
        assertNotConsumed(browser);
    }

    @Test
    @DisplayName("AC-001.6 / edge 20 answers MSG141 for a state that expired at Google")
    void answersMsg141ForAStateThatExpired_AC_001_6_edge20() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser));

        assertNoGoogleCall();
    }

    @Test
    @DisplayName("AC-001.6 / edge 26 answers MSG141 when the same callback comes twice")
    void answersMsg141WhenTheSameCallbackComesTwice_AC_001_6_edge26() throws Exception {
        Browser browser = authorized(ACME);

        assertMsg06(callWithError(browser, "access_denied"), ACME);
        SignInTestFixtures.assertCallbackMsg141Page(callWithError(browser, "access_denied"));
    }

    // ---- Sign-in cookie (AC-001.46, row 45)

    @Test
    @DisplayName("AC-001.46 refuses a callback with no sign-in cookie and consumes the state")
    void refusesACallbackWithNoCookie_AC_001_46() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser, null));

        assertConsumed(browser);
        assertNoGoogleCall();
    }

    @Test
    @DisplayName("AC-001.46 refuses another browser's sign-in cookie")
    void refusesAnotherBrowsersCookie_AC_001_46() throws Exception {
        Browser first = authorized(ACME);
        Browser second = authorized(ACME);
        answerValidToken(first);

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(first, second.cookie()));

        assertConsumed(first);
        assertNoGoogleCall();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"no cookie", "a cookie from another state"})
    @DisplayName("AC-001.46 refuses a state that was never authorized, with or without a cookie")
    void refusesAStateThatWasNeverAuthorized_AC_001_46(String kind) throws Exception {
        Browser other = authorized(ACME);
        Browser browser = notAuthorized(ACME);
        answerValidToken(browser);
        String cookie = kind.equals("no cookie") ? null : other.cookie();

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser, cookie));

        assertConsumed(browser);
        assertThat(browser.row().getAuthorizedAt()).isNull();
        assertNoGoogleCall();
    }

    @Test
    @DisplayName("AC-001.46 refuses a cookie value changed by one character")
    void refusesACookieChangedByOneCharacter_AC_001_46() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);
        char first = browser.cookie().charAt(0);
        String changed = (first == 'A' ? "B" : "A") + browser.cookie().substring(1);

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser, changed));

        assertConsumed(browser);
        assertNoGoogleCall();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"TSOLVE_SIGNIN", "__Secure-TSOLVE_SIGNIN"})
    @DisplayName("AC-001.46 reads only the cookie named __Host-TSOLVE_SIGNIN")
    void readsOnlyTheHostCookie_AC_001_46(String name) throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);

        MockHttpServletResponse response = perform(get(CALLBACK_URL)
                .param("state", browser.state())
                .param("code", CODE)
                .cookie(new Cookie(name, browser.cookie())));

        SignInTestFixtures.assertCallbackMsg141Page(response);
        assertNoGoogleCall();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"", "%$<>", "café漢字"})
    @DisplayName("AC-001.46 / edge refuses malformed cookie values without an error")
    void refusesMalformedCookieValues_AC_001_46(String value) throws Exception {
        Browser browser = authorized(ACME);

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser, value));
    }

    @Test
    @DisplayName("AC-001.46 / edge refuses a cookie value of 8 KB without an error")
    void refusesAnEightKilobyteCookieValue_AC_001_46() throws Exception {
        Browser browser = authorized(ACME);

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser, "A".repeat(8192)));
    }

    @Test
    @DisplayName("AC-001.46 / AC-001.14 checks the cookie before Google's error")
    void checksTheCookieBeforeGoogleError_AC_001_46() throws Exception {
        Browser browser = authorized(ACME);

        MockHttpServletResponse response =
                perform(callback(browser.state(), null).param("error", "access_denied"));

        SignInTestFixtures.assertCallbackMsg141Page(response);
    }

    @Test
    @DisplayName("AC-001.46 refuses a Platform Console state without the cookie")
    void refusesAConsoleStateWithoutTheCookie_AC_001_46() throws Exception {
        Browser browser = authorizedConsole();
        answerValidConsoleToken(browser);

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser, null));

        assertConsumed(browser);
        assertNoGoogleCall();
    }

    @Test
    @DisplayName("AC-001.46 refuses a Jira-link state without the cookie")
    void refusesAJiraLinkStateWithoutTheCookie_AC_001_46() throws Exception {
        Browser browser = authorizedJiraLink(ACME);
        answerValidToken(browser);

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser, null));

        assertConsumed(browser);
        assertNoGoogleCall();
    }

    @Test
    @DisplayName("edge 45 a callback URL made by an attacker fails in the victim's browser")
    void aCallbackUrlMadeByAnAttackerFailsInTheVictimsBrowser_edge45() throws Exception {
        Browser attacker = authorized(ACME);
        FakeGoogleIssuer.answerToken(FakeGoogleIssuer.idToken(attacker.row().getNonce(), claims -> {}));

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(attacker, null));
        assertConsumed(attacker);

        Browser victim = authorized(ACME);
        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(attacker, victim.cookie()));
        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(attacker, attacker.cookie()));
        assertNoGoogleCall();
    }

    // ---- Expiring the cookie (AC-001.47)

    private interface Run {
        MockHttpServletResponse run(CallbackControllerTest test) throws Exception;
    }

    enum Outcome {
        NO_STATE(t -> {
            Browser b = t.authorized(ACME);
            return t.perform(callback(null, b.cookie()).param("code", CODE));
        }),
        BAD_STATE(t -> {
            Browser b = t.authorized(ACME);
            return t.perform(callback("not-a-state.value", b.cookie()).param("code", CODE));
        }),
        NO_COOKIE(t -> t.callWithCode(t.authorized(ACME), null)),
        WRONG_COOKIE(t -> t.callWithCode(t.authorized(ACME), "another-browsers-cookie")),
        WORKSPACE_GONE(t -> {
            Browser b = t.authorized(ACME);
            t.workspaces.remove(ACME.id());
            return t.callWithCode(b);
        }),
        NO_GOOGLE_DOMAIN(t -> {
            Browser b = t.authorized(ACME);
            t.workspaces.replace(new WorkspaceRef(ACME.id(), ACME.siteHost(), null, ACME.status()));
            return t.callWithCode(b);
        }),
        GOOGLE_ERROR(t -> t.callWithError(t.authorized(ACME), "access_denied")),
        TOKEN_EXCHANGE_FAILURE(t -> {
            Browser b = t.authorized(ACME);
            FakeGoogleIssuer.answerTokenStatus(500);
            return t.callWithCode(b);
        }),
        BAD_ID_TOKEN(t -> {
            Browser b = t.authorized(ACME);
            FakeGoogleIssuer.answerToken(
                    FakeGoogleIssuer.signedWithOtherKey(b.row().getNonce(), claims -> {}));
            return t.callWithCode(b);
        }),
        INTERIM_WORKSPACE_PAGE(t -> {
            Browser b = t.authorized(ACME);
            answerValidToken(b);
            return t.callWithCode(b);
        }),
        INTERIM_CONSOLE_PAGE(t -> {
            Browser b = t.authorizedConsole();
            answerValidConsoleToken(b);
            return t.callWithCode(b);
        }),
        INTERIM_JIRA_LINK_PAGE(t -> {
            Browser b = t.authorizedJiraLink(ACME);
            answerValidToken(b);
            return t.callWithCode(b);
        });

        private final Run run;

        Outcome(Run run) {
            this.run = run;
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(Outcome.class)
    @DisplayName("AC-001.47 expires the sign-in cookie in every callback answer")
    void expiresTheCookieInEveryAnswer_AC_001_47(Outcome outcome) throws Exception {
        MockHttpServletResponse response = outcome.run.run(this);

        SignInTestFixtures.assertExpiredSignInCookie(response);
    }

    @Test
    @DisplayName("AC-001.47 the expired cookie keeps the __Host- rules")
    void theExpiredCookieKeepsTheHostRules_AC_001_47() throws Exception {
        Browser browser = authorized(ACME);

        MockHttpServletResponse response = callWithError(browser, "access_denied");

        List<String> headers = SignInTestFixtures.setCookie(response, SignInTestFixtures.SIGN_IN_COOKIE);
        assertThat(headers).hasSize(1);
        assertThat(headers.get(0)).startsWith("__Host-TSOLVE_SIGNIN=;");
        Map<String, String> attributes = SignInTestFixtures.attributes(headers.get(0));
        assertThat(attributes)
                .containsEntry("max-age", "0")
                .containsEntry("path", "/")
                .containsKey("secure")
                .doesNotContainKey("domain");
        if (attributes.containsKey("expires")) {
            Instant expires = DateTimeFormatter.RFC_1123_DATE_TIME.parse(attributes.get("expires"), Instant::from);
            assertThat(expires).isBefore(Instant.now());
        }
    }

    // ---- Workspace of the state (AC-001.49, AC-001.50, rows 49, 50)

    @Test
    @DisplayName("AC-001.50 refuses a state whose Workspace no longer exists")
    void refusesAStateWhoseWorkspaceIsGone_AC_001_50() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);
        workspaces.remove(ACME.id());

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser));

        assertConsumed(browser);
        assertNoGoogleCall();
    }

    @Test
    @DisplayName("AC-001.50 refuses a Jira-link state whose Workspace no longer exists")
    void refusesAJiraLinkStateWhoseWorkspaceIsGone_AC_001_50() throws Exception {
        Browser browser = authorizedJiraLink(ACME);
        answerValidToken(browser);
        workspaces.remove(ACME.id());

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser));

        assertConsumed(browser);
        assertNoGoogleCall();
    }

    @ParameterizedTest(name = "[{index}] domain=[{0}]")
    @NullSource
    @ValueSource(strings = {"  "})
    @DisplayName("AC-001.49 refuses a state whose Workspace lost its Google domain")
    void refusesAStateWhoseWorkspaceLostItsDomain_AC_001_49(String domain) throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);
        workspaces.replace(new WorkspaceRef(ACME.id(), ACME.siteHost(), domain, ACME.status()));

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser));

        assertConsumed(browser);
        assertNoGoogleCall();
    }

    @Test
    @DisplayName("AC-001.49 refuses a Jira-link state whose Workspace has no Google domain")
    void refusesAJiraLinkStateWithNoDomain_AC_001_49() throws Exception {
        Browser browser = authorizedJiraLink(ACME);
        answerValidToken(browser);
        workspaces.replace(new WorkspaceRef(ACME.id(), ACME.siteHost(), null, ACME.status()));

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser));

        assertConsumed(browser);
        assertNoGoogleCall();
    }

    @Test
    @DisplayName("AC-001.14 checks the cookie before the Workspace")
    void checksTheCookieBeforeTheWorkspace_AC_001_14() throws Exception {
        Browser browser = authorized(ACME);
        workspaces.reset();
        workspaces.remove(ACME.id());

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser, null));

        assertThat(workspaces.askedIds()).as("Workspace lookups").isEmpty();
    }

    // ---- Google error (AC-001.8)

    @Test
    @DisplayName("AC-001.8 sends a Google cancel back to the Workspace Login with MSG06")
    void sendsAGoogleCancelBackWithMsg06_AC_001_8() throws Exception {
        Browser browser = authorized(ACME);

        MockHttpServletResponse response = callWithError(browser, "access_denied");

        assertMsg06(response, ACME);
        assertConsumed(browser);
        assertNoGoogleCall();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"server_error", "interaction_required", "consent_required", "something_unknown"})
    @DisplayName("AC-001.8 treats every Google error value the same")
    void treatsEveryGoogleErrorTheSame_AC_001_8(String error) throws Exception {
        Browser browser = authorized(ACME);

        assertMsg06(callWithError(browser, error), ACME);
        assertNoGoogleCall();
    }

    @Test
    @DisplayName("AC-001.8 treats an error that comes together with a code the same")
    void treatsAnErrorWithACodeTheSame_AC_001_8() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);

        MockHttpServletResponse response = perform(callback(browser.state(), browser.cookie())
                .param("error", "access_denied")
                .param("code", CODE));

        assertMsg06(response, ACME);
        assertNoGoogleCall();
    }

    // ---- Code exchange (AC-001.9, rows 23, 52)

    @Test
    @DisplayName("AC-001.9 exchanges the code with the state's PKCE verifier on the configured redirect URI")
    void exchangesTheCodeWithThePkceVerifier_AC_001_9() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);

        callWithCode(browser);

        List<Map<String, String>> requests = FakeGoogleIssuer.tokenRequests();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0))
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("code", CODE)
                .containsEntry("code_verifier", browser.row().getPkceVerifier())
                .containsEntry("client_id", "test-client-id")
                .containsEntry("client_secret", "test-secret")
                .containsEntry("redirect_uri", SignInTestFixtures.LOGIN_ORIGIN + "/api/v1/auth/callback");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(ints = {400, 401, 500, 503})
    @DisplayName("AC-001.9 a refused code exchange gives MSG06")
    void aRefusedCodeExchangeGivesMsg06_AC_001_9(int status) throws Exception {
        Browser browser = authorized(ACME);
        FakeGoogleIssuer.answerTokenStatus(status);

        assertMsg06(callWithCode(browser), ACME);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"{}", "{\"access_token\":\"x\"}", "not json", ""})
    @DisplayName("AC-001.9 a token answer without an id_token gives MSG06")
    void aTokenAnswerWithoutAnIdTokenGivesMsg06_AC_001_9(String body) throws Exception {
        Browser browser = authorized(ACME);
        FakeGoogleIssuer.answerTokenBody(body);

        assertMsg06(callWithCode(browser), ACME);
    }

    @Test
    @DisplayName("AC-001.9 / edge 23 a token endpoint slower than the read timeout gives MSG06")
    void aSlowTokenEndpointGivesMsg06_AC_001_9_edge23() throws Exception {
        Browser browser = authorized(ACME);
        FakeGoogleIssuer.delayToken(FakeGoogleIssuer.idToken(browser.row().getNonce(), claims -> {}), 1500);

        assertMsg06(callWithCode(browser), ACME);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = Fault.class,
            names = {"CONNECTION_RESET_BY_PEER", "EMPTY_RESPONSE"})
    @DisplayName("AC-001.9 / edge 23 an unreachable token endpoint gives MSG06")
    void anUnreachableTokenEndpointGivesMsg06_AC_001_9_edge23(Fault fault) throws Exception {
        Browser browser = authorized(ACME);
        FakeGoogleIssuer.faultToken(fault);

        assertMsg06(callWithCode(browser), ACME);
    }

    @Test
    @DisplayName("AC-001.9 / edge 52 a callback with neither code nor error gives MSG06")
    void aCallbackWithNeitherCodeNorErrorGivesMsg06_AC_001_9_edge52() throws Exception {
        Browser browser = authorized(ACME);

        assertMsg06(perform(callback(browser.state(), browser.cookie())), ACME);
        assertNoGoogleCall();
    }

    // ---- ID token (AC-001.9, rows 22, 23, 52): one end-to-end case each; the matrix is in IdTokenVerifierTest

    enum BadToken {
        OTHER_KEY {
            @Override
            String make(String nonce) {
                return FakeGoogleIssuer.signedWithOtherKey(nonce, claims -> {});
            }
        },
        UNKNOWN_KID {
            @Override
            String make(String nonce) {
                return FakeGoogleIssuer.withUnknownKid(nonce, claims -> {});
            }
        },
        ALG_NONE {
            @Override
            String make(String nonce) {
                return FakeGoogleIssuer.unsigned(nonce, claims -> {});
            }
        },
        HS256_WITH_CLIENT_SECRET {
            @Override
            String make(String nonce) {
                return FakeGoogleIssuer.hs256(FakeGoogleIssuer.CLIENT_SECRET, nonce, claims -> {});
            }
        },
        WRONG_ISSUER {
            @Override
            String make(String nonce) {
                return FakeGoogleIssuer.idToken(nonce, claims -> claims.put("iss", "https://evil.example"));
            }
        },
        WRONG_AUDIENCE {
            @Override
            String make(String nonce) {
                return FakeGoogleIssuer.idToken(nonce, claims -> claims.put("aud", "another-client"));
            }
        },
        EXPIRED {
            @Override
            String make(String nonce) {
                long past = MutableClock.START.minusSeconds(300).getEpochSecond();
                return FakeGoogleIssuer.idToken(nonce, claims -> claims.put("exp", past));
            }
        },
        NONCE_OF_ANOTHER_STATE {
            @Override
            String make(String nonce) {
                return FakeGoogleIssuer.idToken("nonce-of-another-state", claims -> {});
            }
        },
        EMAIL_NOT_VERIFIED {
            @Override
            String make(String nonce) {
                return FakeGoogleIssuer.idToken(nonce, claims -> claims.put("email_verified", Boolean.FALSE));
            }
        },
        EMAIL_VERIFIED_MISSING {
            @Override
            String make(String nonce) {
                return FakeGoogleIssuer.idToken(nonce, claims -> claims.remove("email_verified"));
            }
        };

        abstract String make(String nonce);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(BadToken.class)
    @DisplayName("AC-001.9 / edge 22 a failed ID token check gives MSG06")
    void aFailedIdTokenCheckGivesMsg06_AC_001_9_edge22(BadToken bad) throws Exception {
        Browser browser = authorized(ACME);
        FakeGoogleIssuer.answerToken(bad.make(browser.row().getNonce()));

        assertMsg06(callWithCode(browser), ACME);
    }

    @Test
    @DisplayName("AC-001.9 / edge 52 an ID token without an email claim gives MSG06")
    void anIdTokenWithoutAnEmailGivesMsg06_AC_001_9_edge52() throws Exception {
        Browser browser = authorized(ACME);
        Consumer<Map<String, Object>> noEmail = claims -> claims.remove("email");
        FakeGoogleIssuer.answerToken(FakeGoogleIssuer.idToken(browser.row().getNonce(), noEmail));

        assertMsg06(callWithCode(browser), ACME);
    }

    @Test
    @DisplayName("AC-001.9 / edge 23 an unreachable JWKS gives MSG06")
    void anUnreachableJwksGivesMsg06_AC_001_9_edge23() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);
        FakeGoogleIssuer.jwksDown();

        assertMsg06(callWithCode(browser), ACME);
    }

    // ---- Target only from the state (AC-001.7, row 21; two Workspaces, NFR 5)

    @Test
    @DisplayName("AC-001.7 takes the Workspace only from the state, not from parameters or cookies")
    void takesTheWorkspaceOnlyFromTheState_AC_001_7() throws Exception {
        Browser browser = authorized(ACME);
        workspaces.reset();

        MockHttpServletResponse response = perform(callback(browser.state(), browser.cookie())
                .param("error", "access_denied")
                .param("workspace", "beta")
                .param("site", "beta.localhost")
                .param("workspaceId", BETA.id().toString())
                .param("returnTo", "https://beta.localhost/")
                .cookie(new Cookie("TSOLVE_WORKSPACE", "beta"))
                .cookie(new Cookie(SignInTestFixtures.SESSION_COOKIE, "anything")));

        assertMsg06(response, ACME);
        assertThat(workspaces.askedIds()).isNotEmpty().containsOnly(ACME.id());
        assertThat(workspaces.askedHosts()).isEmpty();
    }

    @Test
    @DisplayName("AC-001.7 the same request for a BETA state goes to BETA")
    void theSameRequestForABetaStateGoesToBeta_AC_001_7() throws Exception {
        Browser browser = authorized(BETA);
        workspaces.reset();

        MockHttpServletResponse response = perform(callback(browser.state(), browser.cookie())
                .param("error", "access_denied")
                .param("workspace", "acme")
                .param("site", "acme.localhost")
                .param("workspaceId", ACME.id().toString())
                .cookie(new Cookie("TSOLVE_WORKSPACE", "acme")));

        assertMsg06(response, BETA);
        assertThat(workspaces.askedIds()).isNotEmpty().containsOnly(BETA.id());
    }

    @Test
    @DisplayName("AC-001.7 / edge 21 a spoofed X-Forwarded-Host never sends the user to that site")
    void aSpoofedForwardedHostNeverSendsTheUserThere_AC_001_7_edge21() throws Exception {
        Browser browser = authorized(ACME);
        workspaces.reset();

        MockHttpServletResponse response = perform(callback(browser.state(), browser.cookie())
                .param("error", "access_denied")
                .header("X-Forwarded-Host", "beta.localhost"));

        assertThat(response.getStatus()).as("status").isIn(302, 404);
        if (response.getStatus() == 302) {
            assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(site(ACME));
        }
        assertThat(workspaces.askedIds()).doesNotContain(BETA.id());
        assertThat(workspaces.askedHosts()).doesNotContain("beta.localhost");
    }

    @Test
    @DisplayName("AC-001.7 a valid token whose hd names BETA does not move the sign-in to BETA")
    void aTokenWhoseHdNamesBetaDoesNotMoveTheSignIn_AC_001_7() throws Exception {
        Browser browser = authorized(ACME);
        workspaces.reset();
        FakeGoogleIssuer.answerToken(
                FakeGoogleIssuer.idToken(browser.row().getNonce(), claims -> claims.put("hd", "beta.example")));

        MockHttpServletResponse response = callWithCode(browser);

        SignInTestFixtures.assertCallbackMsg141Page(response);
        assertThat(workspaces.askedIds()).isNotEmpty().containsOnly(ACME.id());
    }

    // ---- Interim answers (design "Sign-in and session rules", KAN-231 Q12; replaced in T9b / T11 / T12)

    @Test
    @DisplayName("interim Q12 a Platform Console state that passes every check gets the MSG141 page")
    void interimAConsoleStateThatPassesGetsMsg141_Q12() throws Exception {
        Browser browser = authorizedConsole();
        answerValidConsoleToken(browser);

        MockHttpServletResponse response = callWithCode(browser);

        SignInTestFixtures.assertCallbackMsg141Page(response);
        assertThat(FakeGoogleIssuer.tokenRequests()).hasSize(1);
    }

    @Test
    @DisplayName("interim T9b a Workspace state that passes the Google step gets the MSG141 page")
    void interimAWorkspaceStateThatPassesGetsMsg141_T9b() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);

        MockHttpServletResponse response = callWithCode(browser);

        SignInTestFixtures.assertCallbackMsg141Page(response);
        assertThat(FakeGoogleIssuer.tokenRequests()).hasSize(1);
        assertThat(FakeGoogleIssuer.jwksRequestCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"google error", "token failure", "bad id token"})
    @DisplayName("interim Q12 a Platform Console state with a Google error gets the MSG141 page, not MSG06")
    void interimAConsoleStateWithAGoogleErrorGetsMsg141_Q12(String kind) throws Exception {
        Browser browser = authorizedConsole();
        MockHttpServletResponse response;
        switch (kind) {
            case "google error" -> response = callWithError(browser, "access_denied");
            case "token failure" -> {
                FakeGoogleIssuer.answerTokenStatus(500);
                response = callWithCode(browser);
            }
            default -> {
                FakeGoogleIssuer.answerToken(
                        FakeGoogleIssuer.signedWithOtherKey(browser.row().getNonce(), c -> {}));
                response = callWithCode(browser);
            }
        }

        SignInTestFixtures.assertCallbackMsg141Page(response);
    }

    @Test
    @DisplayName("interim T12 a Jira-link state that passes the shared checks gets the MSG141 page")
    void interimAJiraLinkStateThatPassesGetsMsg141_T12() throws Exception {
        Browser browser = authorizedJiraLink(ACME);
        answerValidToken(browser);

        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(browser));
    }

    // ---- Other rows

    @Test
    @DisplayName("edge 47 two tabs: the later tab's callback first signs on, the earlier tab gets MSG141")
    void twoTabsTheLaterTabFirst_edge47() throws Exception {
        Browser first = authorized(ACME);
        Browser second = authorized(ACME);

        assertMsg06(callWithError(second, "access_denied"), ACME);
        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(first, null));
    }

    @Test
    @DisplayName("edge 47 two tabs: the earlier tab's callback first gets MSG141 and expires the cookie")
    void twoTabsTheEarlierTabFirst_edge47() throws Exception {
        Browser first = authorized(ACME);
        Browser second = authorized(ACME);

        // the jar holds only the later tab's cookie
        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(first, second.cookie()));
        SignInTestFixtures.assertCallbackMsg141Page(callWithCode(second, null));
    }

    @Test
    @DisplayName("edge 42 a database failure during the callback gives a 500 and no session")
    void aDatabaseFailureGivesA500_edge42() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);
        doThrow(new IllegalStateException("database is down"))
                .when(store.repository())
                .markUsed(any(), any());

        MockHttpServletResponse response = callWithCode(browser);

        assertThat(response.getStatus()).as("status").isEqualTo(500);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull();
        SignInTestFixtures.assertNoSession(response);
        SignInTestFixtures.assertExpiredSignInCookie(response);
    }

    @Test
    @DisplayName("edge 33 the callback logs no state, code, ID token, cookie value or its hash")
    void theCallbackLogsNoSecrets_edge33(CapturedOutput output) throws Exception {
        Browser valid = authorized(ACME);
        String idToken = FakeGoogleIssuer.idToken(valid.row().getNonce(), claims -> {});
        FakeGoogleIssuer.answerToken(idToken);
        callWithCode(valid);
        Browser cancelled = authorized(ACME);
        callWithError(cancelled, "access_denied");

        String log = output.getAll();
        assertThat(log).doesNotContain(CODE).doesNotContain(idToken);
        for (Browser browser : List.of(valid, cancelled)) {
            byte[] hash = sha256(browser.cookie());
            assertThat(log)
                    .doesNotContain(browser.state())
                    .doesNotContain(browser.cookie())
                    .doesNotContain(HexFormat.of().formatHex(hash))
                    .doesNotContain(Base64.getEncoder().encodeToString(hash))
                    .doesNotContain(Base64.getUrlEncoder().withoutPadding().encodeToString(hash));
        }
    }

    @Test
    @DisplayName("AC-001.9 / edge 23 a JWKS slower than the read timeout gives MSG06")
    void aSlowJwksGivesMsg06_AC_001_9_edge23_review() throws Exception {
        Browser browser = authorized(ACME);
        answerValidToken(browser);
        FakeGoogleIssuer.delayJwks(1500);

        assertMsg06(callWithCode(browser), ACME);
    }
}
