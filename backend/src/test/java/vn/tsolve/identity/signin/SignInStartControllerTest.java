package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.identity.AuthSecurityConfig;
import vn.tsolve.identity.signin.SignInTestFixtures.StateStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/** Anonymous on purpose: sign-in start is reached before anyone is signed in (no @WithMockUser here). */
@WebMvcTest(SignInStartController.class)
@Import({
    SecurityConfig.class,
    AuthSecurityConfig.class,
    AuthHosts.class,
    SignInStateService.class,
    SignedToken.class,
    GoogleOidcClient.class,
    SignInTestBeans.class,
    KeycloakAuthorizationRequestResolver.class,
    KeycloakClientRegistrations.class,
    RedisAuthorizationRequestRepository.class,
    OidcCallbackHandler.class,
    SignInStartControllerTest.RecordingRepositoryConfig.class
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
@ExtendWith(OutputCaptureExtension.class)
class SignInStartControllerTest {

    @MockBean
    private StringRedisTemplate redis;

    private static final String AUTHORIZE_URL = SignInTestFixtures.LOGIN_ORIGIN + "/api/v1/auth/authorize";

    private static final String KEYCLOAK_ENDPOINT =
            SignInTestFixtures.LOGIN_ORIGIN + "/realms/tsolve/protocol/openid-connect/auth";

    /** The slice scans no component, so the test supplies the repository double the controller needs. */
    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingRepositoryConfig {

        // Primary: the controller records into this one; the callback chain takes the Redis one by its own type.
        @Bean
        @Primary
        RecordingAuthorizationRequestRepository recordingAuthorizationRequestRepository() {
            return new RecordingAuthorizationRequestRepository();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StateStore store;

    @Autowired
    private InMemoryWorkspaceLookup workspaces;

    @Autowired
    private SignedToken signedToken;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecordingAuthorizationRequestRepository requests;

    @BeforeEach
    void reset() {
        store.reset();
        workspaces.reset();
        clock.reset();
        requests.reset();
    }

    private static MockHttpServletRequestBuilder start(String host) {
        return get("http://" + host + ":8081/api/v1/auth/sign-in");
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    /** Asserts a 302 to login.'s authorize with only a state parameter, and returns that state value. */
    private static String stateOfRedirectToAuthorize(MockHttpServletResponse response) {
        assertThat(response.getStatus()).as("status").isEqualTo(302);
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(location).as("Location").startsWith(AUTHORIZE_URL + "?state=");
        UriComponents uri = UriComponentsBuilder.fromUriString(location).build();
        assertThat(uri.getQueryParams().keySet()).as("query parameters").containsExactly("state");
        String state = uri.getQueryParams().getFirst("state");
        assertThat(location).isEqualTo(AUTHORIZE_URL + "?state=" + state);
        return state;
    }

    /** Asserts a 302 to Keycloak's authorization endpoint and returns the Location. */
    private static String redirectToKeycloak(MockHttpServletResponse response) {
        assertThat(response.getStatus()).as("status").isEqualTo(302);
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(location).as("Location").startsWith(KEYCLOAK_ENDPOINT + "?");
        return location;
    }

    static Stream<Arguments> hostsAndWorkspaces() {
        return Stream.of(
                Arguments.of("acme.localhost", InMemoryWorkspaceLookup.ACME),
                Arguments.of("beta.localhost", InMemoryWorkspaceLookup.BETA));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("hostsAndWorkspaces")
    @DisplayName("AC-001.1 starts a sign-in for the Workspace of the host and sends it to Keycloak")
    void startsSignInForTheWorkspaceOfTheHost_AC_001_1(String host, WorkspaceRef workspace) throws Exception {
        MockHttpServletResponse response = perform(start(host));

        String location = redirectToKeycloak(response);
        OAuth2AuthorizationRequest saved = requests.onlySaved();
        assertThat(location).isEqualTo(saved.getAuthorizationRequestUri());
        assertThat(saved.getAttributes().get(KeycloakAuthorizationRequestResolver.WORKSPACE_ID))
                .hasToString(workspace.id().toString());
        assertThat(store.calls("save")).isZero();
    }

    @Test
    @DisplayName("AC-001.1 takes the Workspace only from the host")
    void takesTheWorkspaceOnlyFromTheHost_AC_001_1() throws Exception {
        UUID betaId = InMemoryWorkspaceLookup.BETA.id();

        MockHttpServletResponse response = perform(start("acme.localhost")
                .param("workspace", betaId.toString())
                .param("site", "beta.localhost")
                .param("kc_idp_hint", "google-beta")
                .param("redirect_uri", "http://evil.example/"));

        String location = redirectToKeycloak(response);
        assertThat(requests.onlySaved().getAttributes().get(KeycloakAuthorizationRequestResolver.WORKSPACE_ID))
                .hasToString(InMemoryWorkspaceLookup.ACME.id().toString());
        assertThat(AuthorizationUris.params(location).get("kc_idp_hint")).containsExactly("google-acme");
        assertThat(AuthorizationUris.one(location, "redirect_uri"))
                .isEqualTo("http://acme.localhost:8081/api/v1/auth/callback");
    }

    static Stream<Arguments> returnTos() {
        return Stream.of(
                Arguments.of("/tickets/42", "/tickets/42"),
                Arguments.of("/tickets/42?tab=1", "/tickets/42?tab=1"),
                Arguments.of("//evil.example", "/"),
                Arguments.of("https://evil.example/x", "/"),
                Arguments.of("evil.example", "/"),
                Arguments.of("/\\evil.example", "/"),
                Arguments.of("", "/"),
                Arguments.of(null, "/"));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @MethodSource("returnTos")
    @DisplayName("AC-001.3 passes returnTo through the filter")
    void passesReturnToThroughTheFilter_AC_001_3(String returnTo, String expected) throws Exception {
        MockHttpServletRequestBuilder request = start("acme.localhost");
        if (returnTo != null) {
            request.param("returnTo", returnTo);
        }

        redirectToKeycloak(perform(request));
        assertThat(requests.onlySaved().getAttributes().get(KeycloakAuthorizationRequestResolver.RETURN_TO))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-001.52 sends the client tsolve-web with response type code and scope openid email profile")
    void sendsClientResponseTypeAndScope_AC_001_52() throws Exception {
        String location = redirectToKeycloak(perform(start("acme.localhost")));

        assertThat(AuthorizationUris.one(location, "client_id")).isEqualTo("tsolve-web");
        assertThat(AuthorizationUris.one(location, "response_type")).isEqualTo("code");
        assertThat(AuthorizationUris.one(location, "scope")).isEqualTo("openid email profile");
        assertThat(AuthorizationUris.one(location, "state"))
                .isEqualTo(requests.onlySaved().getState());
    }

    @Test
    @DisplayName("AC-001.28 starts a Platform Console sign-in on admin.")
    void startsPlatformConsoleSignInOnAdmin_AC_001_28() throws Exception {
        MockHttpServletResponse response = perform(start("admin.localhost"));

        String state = stateOfRedirectToAuthorize(response);
        SignInState saved = store.onlySaved();
        assertThat(saved.getTarget()).isEqualTo(SignInState.Target.PLATFORM_CONSOLE);
        assertThat(saved.getWorkspaceId()).isNull();
        assertThat(signedToken.verify(state)).contains(saved.getId().toString());
        assertThat(workspaces.askedHosts()).noneMatch(host -> host.startsWith("admin."));
    }

    @Test
    @DisplayName("AC-001.1 starts a sign-in for a Workspace that is not Active (checked at the callback, AC-001.10)")
    void startsSignInForWorkspaceThatIsNotActive_AC_001_1() throws Exception {
        MockHttpServletResponse response = perform(start("gamma.localhost"));

        redirectToKeycloak(response);
        assertThat(requests.onlySaved().getAttributes().get(KeycloakAuthorizationRequestResolver.WORKSPACE_ID))
                .hasToString(InMemoryWorkspaceLookup.GAMMA.id().toString());
    }

    private static WorkspaceRef noDomain(String host) {
        return host.startsWith("nodomain") ? InMemoryWorkspaceLookup.NO_DOMAIN : InMemoryWorkspaceLookup.BLANK_DOMAIN;
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"nodomain.localhost", "blankdomain.localhost"})
    @DisplayName("AC-001.48 refuses sign-in start for a Workspace with no Google domain")
    void refusesSignInStartForAWorkspaceWithNoGoogleDomain_AC_001_48(String host) throws Exception {
        MockHttpServletResponse response = perform(start(host));

        assertThat(response.getStatus()).as("status").isEqualTo(302);
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(location).as("Location").isNotNull();
        URI resolved =
                URI.create("http://" + host + ":8081/api/v1/auth/sign-in").resolve(location);
        assertThat(resolved.toString()).isEqualTo("http://" + host + ":8081/login?error=MSG141");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"nodomain.localhost", "blankdomain.localhost"})
    @DisplayName("AC-001.48 creates no authorization request and no cookie for a Workspace with no Google domain")
    void createsNoAuthorizationRequestForAWorkspaceWithNoGoogleDomain_AC_001_48(String host) throws Exception {
        MockHttpServletResponse response = perform(start(host));

        assertThat(requests.saved()).isEmpty();
        assertThat(store.calls("save")).isZero();
        assertThat(store.saved()).isEmpty();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    @DisplayName("AC-001.48 logs one WARN that names only the Workspace id")
    void logsOneWarnThatNamesOnlyTheWorkspaceId_AC_001_48(CapturedOutput output) throws Exception {
        perform(start("nodomain.localhost").param("returnTo", "/secret-return-path"));

        String id = noDomain("nodomain").id().toString();
        List<String> warnLines = output.getAll()
                .lines()
                .filter(line -> line.contains(" WARN "))
                .filter(line -> line.contains(id))
                .toList();
        assertThat(warnLines).hasSize(1);
        assertThat(warnLines.get(0)).doesNotContain("nodomain.localhost").doesNotContain("secret-return-path");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"nope.localhost", "login.localhost", "api.localhost"})
    @DisplayName("edge 44 answers 404 on a host that is no Workspace site and not the Platform Console")
    void answers404OnHostThatIsNoWorkspaceSite_edge44(String host) throws Exception {
        MockHttpServletResponse response = perform(start(host));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(store.calls("save")).isZero();
        assertThat(store.saved()).isEmpty();
        assertThat(requests.saved()).isEmpty();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }
}
