package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.UUID;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.identity.AuthSecurityConfig;
import vn.tsolve.identity.signin.SignInTestFixtures.StateStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;

/** Anonymous on purpose: sign-in start is reached before anyone is signed in (no @WithMockUser here). */
@WebMvcTest(SignInStartController.class)
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
            "server.port=0"
        })
class SignInStartControllerTest {

    private static final String AUTHORIZE_URL = SignInTestFixtures.LOGIN_ORIGIN + "/api/v1/auth/authorize";

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

    @BeforeEach
    void reset() {
        store.reset();
        workspaces.reset();
        clock.reset();
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

    @Test
    @DisplayName("AC-001.1 starts a sign-in for the Workspace of the host and sends it to login.")
    void startsSignInForTheWorkspaceOfTheHost_AC_001_1() throws Exception {
        MockHttpServletResponse response = perform(start("acme.localhost"));

        String state = stateOfRedirectToAuthorize(response);
        SignInState saved = store.onlySaved();
        assertThat(saved.getTarget()).isEqualTo(SignInState.Target.WORKSPACE);
        assertThat(saved.getWorkspaceId()).isEqualTo(InMemoryWorkspaceLookup.ACME.id());
        assertThat(signedToken.verify(state)).contains(saved.getId().toString());
    }

    @Test
    @DisplayName("AC-001.1 resolves another Workspace from its own host")
    void resolvesAnotherWorkspaceFromItsOwnHost_AC_001_1() throws Exception {
        MockHttpServletResponse response = perform(start("beta.localhost"));

        stateOfRedirectToAuthorize(response);
        assertThat(store.onlySaved().getWorkspaceId()).isEqualTo(InMemoryWorkspaceLookup.BETA.id());
    }

    @Test
    @DisplayName("AC-001.1 takes the Workspace only from the host")
    void takesTheWorkspaceOnlyFromTheHost_AC_001_1() throws Exception {
        UUID betaId = InMemoryWorkspaceLookup.BETA.id();

        MockHttpServletResponse response = perform(
                start("acme.localhost").param("workspace", betaId.toString()).param("site", "beta.localhost"));

        stateOfRedirectToAuthorize(response);
        assertThat(store.onlySaved().getWorkspaceId()).isEqualTo(InMemoryWorkspaceLookup.ACME.id());
    }

    @Test
    @DisplayName("AC-001.2 keeps nothing in the browser but the state value")
    void keepsNothingInTheBrowserButTheState_AC_001_2() throws Exception {
        for (String host : List.of("acme.localhost", "admin.localhost")) {
            MockHttpServletResponse response = perform(start(host));

            stateOfRedirectToAuthorize(response);
            assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                    .as("Set-Cookie on %s", host)
                    .isEmpty();
            assertThat(response.getCookies()).as("cookies on %s", host).isEmpty();
        }
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({"/tickets/42, /tickets/42", "//evil.example, /", ", /"})
    @DisplayName("AC-001.3 passes returnTo through the filter")
    void passesReturnToThroughTheFilter_AC_001_3(String returnTo, String expected) throws Exception {
        MockHttpServletRequestBuilder request = start("acme.localhost");
        if (returnTo != null) {
            request.param("returnTo", returnTo);
        }

        stateOfRedirectToAuthorize(perform(request));
        assertThat(store.onlySaved().getReturnTo()).isEqualTo(expected);
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

        stateOfRedirectToAuthorize(response);
        SignInState saved = store.onlySaved();
        assertThat(saved.getTarget()).isEqualTo(SignInState.Target.WORKSPACE);
        assertThat(saved.getWorkspaceId()).isEqualTo(InMemoryWorkspaceLookup.GAMMA.id());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"nope.localhost", "login.localhost", "api.localhost"})
    @DisplayName("edge 44 answers 404 on a host that is no Workspace site and not the Platform Console")
    void answers404OnHostThatIsNoWorkspaceSite_edge44(String host) throws Exception {
        MockHttpServletResponse response = perform(start(host));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(store.calls("save")).isZero();
        assertThat(store.saved()).isEmpty();
    }
}
