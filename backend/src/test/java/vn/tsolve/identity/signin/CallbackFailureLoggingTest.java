package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.identity.AuthSecurityConfig;
import vn.tsolve.identity.signin.SignInTestFixtures.StateStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;

/**
 * The WARN lines of a failed callback. Kept apart from {@link CallbackControllerTest} because that class's
 * "logs no secrets" test reads the whole class output, so these tests must not share its captured log.
 */
@WebMvcTest({CallbackController.class, AuthorizeController.class})
@Import({
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
class CallbackFailureLoggingTest {

    private static final String CALLBACK_URL = "http://login.localhost:8081/api/v1/auth/callback";
    private static final String CODE = "4/0A-the-authorization-code";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StateStore store;

    @Autowired
    private SignInStateService states;

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

    private record Browser(String cookie, SignInState row, String state) {}

    private Browser authorized() throws Exception {
        String state = states.createForWorkspace(InMemoryWorkspaceLookup.ACME.id(), "/");
        SignInState row = states.check(state).orElseThrow();
        MockHttpServletResponse response = mvc.perform(
                        get("http://login.localhost:8081/api/v1/auth/authorize").param("state", state))
                .andReturn()
                .getResponse();
        String cookie = SignInTestFixtures.signInCookieValue(response);
        assertThat(cookie).as("the sign-in cookie that authorize sets").isNotNull();
        return new Browser(cookie, row, state);
    }

    private void callWithCode(Browser browser) throws Exception {
        mvc.perform(get(CALLBACK_URL)
                .param("state", browser.state())
                .param("code", CODE)
                .cookie(new Cookie(SignInTestFixtures.SIGN_IN_COOKIE, browser.cookie())));
    }

    @Test
    @DisplayName("edge 33 a refused code exchange logs one WARN with the status only")
    void aRefusedCodeExchangeLogsOneWarn_AC_001_9_edge33(CapturedOutput output) throws Exception {
        Browser browser = authorized();
        FakeGoogleIssuer.answerTokenStatus(400);

        callWithCode(browser);

        List<String> warns = output.getAll()
                .lines()
                .filter(line -> line.contains("WARN") && line.contains("Google token exchange failed"))
                .toList();
        assertThat(warns).hasSize(1);
        assertThat(warns.get(0)).contains("400").doesNotContain(CODE).doesNotContain("invalid_grant");
        assertThat(output.getAll()).doesNotContain(CODE).doesNotContain(browser.cookie());
    }

    @Test
    @DisplayName("edge 33 a rejected ID token logs one WARN with the failure kind only")
    void aRejectedIdTokenLogsOneWarn_edge33(CapturedOutput output) throws Exception {
        Browser browser = authorized();
        String idToken = FakeGoogleIssuer.signedWithOtherKey(browser.row().getNonce(), claims -> {});
        FakeGoogleIssuer.answerToken(idToken);

        callWithCode(browser);

        List<String> warns = output.getAll()
                .lines()
                .filter(line -> line.contains("WARN") && line.contains("ID token verification failed"))
                .toList();
        assertThat(warns).hasSize(1);
        assertThat(output.getAll()).doesNotContain(idToken).doesNotContain(CODE);
        assertThat(output.getAll()).doesNotContain("lan.tran@acme.example");
    }
}
