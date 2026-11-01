package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.identity.AuthSecurityConfig;
import vn.tsolve.identity.signin.SignInTestFixtures.BadState;
import vn.tsolve.identity.signin.SignInTestFixtures.StateStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;

/** States are made by the real SignInStateService; only the repository is in memory. */
@WebMvcTest(AuthorizeController.class)
@Import({
    AuthSecurityConfig.class,
    AuthHosts.class,
    SignInStateService.class,
    SignedToken.class,
    GoogleOidcClient.class,
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
class AuthorizeControllerTest {

    private static final String GOOGLE_AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String CALLBACK_URI = SignInTestFixtures.LOGIN_ORIGIN + "/api/v1/auth/callback";

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

    @BeforeEach
    void reset() {
        store.reset();
        clock.reset();
    }

    private static MockHttpServletRequestBuilder authorize(String host) {
        return get("http://" + host + ":8081/api/v1/auth/authorize");
    }

    private static MockHttpServletRequestBuilder authorizeOnLogin(String state) {
        return authorize("login.localhost").param("state", state);
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private static String redirectLocation(MockHttpServletResponse response) {
        assertThat(response.getStatus()).as("status").isEqualTo(302);
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(location).as("Location").isNotNull();
        return location;
    }

    private static String withoutQuery(String location) {
        return UriComponentsBuilder.fromUriString(location)
                .replaceQuery(null)
                .fragment(null)
                .build()
                .toUriString();
    }

    /** The decoded query parameters; each must appear exactly once. */
    private static Map<String, String> query(String location) {
        UriComponents uri = UriComponentsBuilder.fromUriString(location).build();
        Map<String, String> parameters = new LinkedHashMap<>();
        uri.getQueryParams().forEach((name, values) -> {
            assertThat(values).as("values of %s", name).hasSize(1);
            parameters.put(name, URLDecoder.decode(values.get(0), StandardCharsets.UTF_8));
        });
        return parameters;
    }

    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static void assertMsg141Page(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as("status").isEqualTo(400);
        assertThat(response.getContentType()).as("Content-Type").isNotNull();
        assertThat(MediaType.parseMediaType(response.getContentType()).isCompatibleWith(MediaType.TEXT_HTML))
                .as("Content-Type %s is text/html", response.getContentType())
                .isTrue();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains(SignInTestFixtures.MSG141_TEXT);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).as("Location").isNull();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).as("Set-Cookie").isEmpty();
    }

    @Test
    @DisplayName("AC-001.4 sends a Workspace sign-in to Google with scope, state, nonce, PKCE S256 and hd")
    void sendsWorkspaceSignInToGoogle_AC_001_4() throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");
        SignInState saved = store.onlySaved();

        String location = redirectLocation(perform(authorizeOnLogin(state)));

        assertThat(withoutQuery(location)).isEqualTo(GOOGLE_AUTHORIZATION_URI);
        assertThat(query(location))
                .containsEntry("response_type", "code")
                .containsEntry("client_id", "test-client-id")
                .containsEntry("scope", "openid email profile")
                .containsEntry("state", state)
                .containsEntry("nonce", saved.getNonce())
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("code_challenge", s256(saved.getPkceVerifier()))
                .containsEntry("redirect_uri", CALLBACK_URI)
                .containsEntry("hd", "acme.example");
    }

    @Test
    @DisplayName("AC-001.4 sets hd to the state's own Workspace domain")
    void setsHdToTheStatesOwnWorkspaceDomain_AC_001_4() throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.BETA.id(), "/");

        String location = redirectLocation(perform(authorizeOnLogin(state)));

        assertThat(query(location)).containsEntry("hd", "beta.example");
    }

    @Test
    @DisplayName("AC-001.28 sends a Platform Console sign-in to Google without hd")
    void sendsPlatformConsoleSignInWithoutHd_AC_001_28() throws Exception {
        String state = states.createForPlatformConsole("/");

        String location = redirectLocation(perform(authorizeOnLogin(state)));

        assertThat(withoutQuery(location)).isEqualTo(GOOGLE_AUTHORIZATION_URI);
        assertThat(query(location))
                .doesNotContainKey("hd")
                .containsEntry("redirect_uri", CALLBACK_URI)
                .containsEntry("scope", "openid email profile")
                .containsEntry("code_challenge_method", "S256");
    }

    @Test
    @DisplayName("AC-001.2 uses the nonce and verifier kept on the server")
    void usesTheNonceAndVerifierKeptOnTheServer_AC_001_2() throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");
        String verifier = store.onlySaved().getPkceVerifier();

        MockHttpServletResponse first = perform(authorizeOnLogin(state));
        MockHttpServletResponse second = perform(authorizeOnLogin(state));

        Map<String, String> firstQuery = query(redirectLocation(first));
        Map<String, String> secondQuery = query(redirectLocation(second));
        assertThat(secondQuery.get("nonce")).isNotNull().isEqualTo(firstQuery.get("nonce"));
        assertThat(secondQuery.get("code_challenge")).isNotNull().isEqualTo(firstQuery.get("code_challenge"));
        for (MockHttpServletResponse response : List.of(first, second)) {
            assertThat(response.getHeader(HttpHeaders.LOCATION)).doesNotContain(verifier);
            assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                    .as("Set-Cookie")
                    .isEmpty();
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"acme.localhost", "beta.localhost", "admin.localhost"})
    @DisplayName("AC-001.5 answers 404 for authorize off login.")
    void answers404ForAuthorizeOffLogin_AC_001_5(String host) throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");
        store.clearCalls();

        MockHttpServletResponse response = perform(authorize(host).param("state", state));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(store.calls("findById")).isZero();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"acme.localhost", "admin.localhost"})
    @DisplayName("AC-001.5 answers 404 for the callback off login.")
    void answers404ForCallbackOffLogin_AC_001_5(String host) throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");

        MockHttpServletResponse response = perform(get("http://" + host + ":8081/api/v1/auth/callback")
                .param("code", "x")
                .param("state", state));

        assertThat(response.getStatus()).isEqualTo(404);
    }

    /**
     * Positive control for the test above: the same callback request on {@code login.} is handled, so the 404 off
     * {@code login.} comes from the host check, not from a missing handler. The callback controller must be in this
     * slice ({@code @WebMvcTest}) for it to pass.
     */
    @Test
    @Disabled("T9: callback handler")
    @DisplayName("AC-001.5 does not answer 404 for the callback on login.")
    void handlesCallbackOnLogin_AC_001_5() throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");

        MockHttpServletResponse response = perform(get("http://login.localhost:8081/api/v1/auth/callback")
                .param("code", "x")
                .param("state", state));

        assertThat(response.getStatus()).as("status on login.").isNotEqualTo(404);
    }

    @Test
    @DisplayName("AC-001.6 answers 400 with the MSG141 page when the state is missing")
    void answersMsg141WhenStateIsMissing_AC_001_6() throws Exception {
        assertMsg141Page(perform(authorize("login.localhost")));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = BadState.class,
            names = {"PAYLOAD_EDITED", "MAC_EDITED", "BARE_ID", "SIGNED_WITH_OTHER_KEY", "EMPTY"})
    @DisplayName("AC-001.6 / edge 20 answers 400 MSG141 for an edited, unsigned or foreign state")
    void answersMsg141ForUntrustedState_AC_001_6_edge20(BadState bad) throws Exception {
        String valid = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");

        assertMsg141Page(perform(authorizeOnLogin(bad.make(valid, signedToken))));
    }

    @Test
    @DisplayName("AC-001.6 / edge 20 answers 400 MSG141 for an expired state")
    void answersMsg141ForExpiredState_AC_001_6_edge20() throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");
        clock.advance(SignInTestFixtures.STATE_LIFETIME.plusSeconds(1));

        assertMsg141Page(perform(authorizeOnLogin(state)));
    }

    @Test
    @DisplayName("AC-001.6 / edge 20 answers 400 MSG141 for a state already used")
    void answersMsg141ForUsedState_AC_001_6_edge20() throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");
        assertThat(states.consume(state)).as("the first use").isPresent();

        assertMsg141Page(perform(authorizeOnLogin(state)));
    }

    @Test
    @DisplayName("edge 33 logs no state value, nonce or verifier")
    void logsNoStateNonceOrVerifier_edge33(CapturedOutput output) throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");
        SignInState saved = store.onlySaved();

        redirectLocation(perform(authorizeOnLogin(state)));

        assertThat(output.getAll())
                .doesNotContain(state)
                .doesNotContain(saved.getNonce())
                .doesNotContain(saved.getPkceVerifier());
    }
}
