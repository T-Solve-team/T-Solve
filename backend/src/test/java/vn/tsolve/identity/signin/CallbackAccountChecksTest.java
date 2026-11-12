package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.tsolve.identity.account.AccountSignInService;
import vn.tsolve.identity.account.AccountSignInService.Result;
import vn.tsolve.identity.account.AccountSignInService.Status;
import vn.tsolve.identity.session.OperatorAllowList;
import vn.tsolve.identity.session.SessionCookie;
import vn.tsolve.identity.session.WebSessionStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.RecordingTenantScope;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Task 25b: the Workspace checks of the callback in the AC-001.14 order (Organization and hd, Workspace Active,
 * Account match, Account Active); only the first failure is reported. Plain unit tests: the handler gets an
 * in-memory Workspace lookup, a recording tenant scope and a mocked {@link AccountSignInService} that records which
 * Workspace was bound when it was called.
 */
@ExtendWith(OutputCaptureExtension.class)
class CallbackAccountChecksTest {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;
    private static final WorkspaceRef GAMMA = InMemoryWorkspaceLookup.GAMMA;
    private static final String COOKIE = RedisAuthorizationRequestRepository.COOKIE_NAME;
    private static final String SUB = "kc-sub-7f3a9c52";
    private static final String EMAIL = "alice@acme.example";
    private static final String NAME = "Alice Nguyen";
    private static final String MSG122_ACME = "/login?error=MSG122&google_domain=acme.example";

    private InMemoryWorkspaceLookup workspaces;
    private RecordingTenantScope scope;
    private AccountSignInService accounts;
    private OidcCallbackHandler handler;
    /** The Workspace bound when {@code signIn} was entered, one entry per call. */
    private final List<UUID> boundWhenCalled = new ArrayList<>();

    private Result stubbed = new Result(Status.SIGNED_IN, UUID.randomUUID());

    @BeforeEach
    void wire() {
        workspaces = new InMemoryWorkspaceLookup();
        scope = new RecordingTenantScope();
        accounts = mock(AccountSignInService.class);
        when(accounts.signIn(any(), any(), any())).thenAnswer(invocation -> {
            boundWhenCalled.add(scope.currentWorkspaceId());
            // Succeeds for every input, a null or blank email too (the real service does for an Account already
            // bound to the sub), so a test fails if the handler leans on signIn to refuse a missing email.
            return stubbed;
        });
        WebSessionStore sessions = mock(WebSessionStore.class);
        when(sessions.create(any())).thenReturn("session-cookie-value-for-the-unit-test-00");
        handler = new OidcCallbackHandler(
                workspaces,
                new AuthHosts(SignInTestFixtures.properties()),
                scope,
                accounts,
                sessions,
                new SessionCookie(),
                new IdentityClock(Clock.systemUTC()),
                new OperatorAllowList(""));
    }

    // ---- helpers

    private static MockHttpServletRequest request(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/callback");
        request.setServerName(host);
        request.setServerPort(8080);
        request.setScheme("http");
        request.setAttribute(OidcCallbackHandler.REQUEST_CHECKED_ATTRIBUTE, Boolean.TRUE);
        return request;
    }

    /** Claims that are valid for this Workspace. */
    private static Map<String, Object> validFor(WorkspaceRef workspace) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", SUB);
        claims.put("email", EMAIL);
        claims.put("email_verified", true);
        claims.put("name", NAME);
        Map<String, Object> entry =
                workspace.organizationId() == null ? Map.of() : Map.of("id", workspace.organizationId());
        claims.put("organization", Map.of(workspace.slug(), entry));
        claims.put("hd", workspace.googleDomain());
        return claims;
    }

    private MockHttpServletResponse callback(String host, Map<String, Object> claims) throws Exception {
        return callback(request(host), claims);
    }

    private MockHttpServletResponse callback(MockHttpServletRequest request, Map<String, Object> claims)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, SignInTestFixtures.signedIn(claims));
        return response;
    }

    /** Exactly one Set-Cookie for the sign-in cookie, empty and expired. */
    private static void assertCookieExpired(MockHttpServletResponse response) {
        List<String> headers = SignInTestFixtures.setCookie(response, COOKIE);
        assertThat(headers).as("Set-Cookie of %s", COOKIE).hasSize(1);
        assertThat(headers.get(0)).startsWith(COOKIE + "=;");
        assertThat(SignInTestFixtures.attributes(headers.get(0))).containsEntry("max-age", "0");
    }

    /** The common "refused" answer (R): one 302, no forward, the cookie expired, nothing leaked to the log. */
    private static void assertRefused(
            MockHttpServletRequest request, MockHttpServletResponse response, String location, CapturedOutput output) {
        assertThat(response.getStatus()).as("status").isEqualTo(302);
        assertThat(response.getHeaders(HttpHeaders.LOCATION)).as("Location").containsExactly(location);
        assertThat(SignInTestFixtures.setCookie(response, SessionCookie.NAME))
                .as("session cookie")
                .isEmpty();
        assertCookieExpired(response);
        assertThat(output.getAll()).as("log").doesNotContain(EMAIL).doesNotContain(SUB);
    }

    private void assertAccountStepNeverRan() {
        verifyNoInteractions(accounts);
        assertThat(scope.boundIds()).as("Workspaces bound").isEmpty();
    }

    // ---- tests

    static Stream<Arguments> badOrganizations() {
        Map<String, Object> noEntries = Map.of();
        return Stream.of(
                row("claim absent", "acme.localhost", claims -> claims.remove("organization")),
                row("claim null", "acme.localhost", claims -> claims.put("organization", null)),
                row("empty map", "acme.localhost", claims -> claims.put("organization", noEntries)),
                row(
                        "BETA's Organization",
                        "acme.localhost",
                        claims -> claims.put("organization", Map.of("beta", Map.of("id", BETA.organizationId())))),
                row("list form", "acme.localhost", claims -> claims.put("organization", List.of("acme"))),
                row("string form", "acme.localhost", claims -> claims.put("organization", "acme")),
                row(
                        "entry with no id",
                        "acme.localhost",
                        claims -> claims.put("organization", Map.of("acme", Map.of()))),
                row(
                        "Workspace with no Organization id, entry with no id (null never matches null)",
                        "noorg.localhost",
                        claims -> claims.put("organization", Map.of("noorg", Map.of()))),
                row(
                        "Workspace with a blank Organization id, entry with a blank id",
                        "blankorg.localhost",
                        claims -> claims.put("organization", Map.of("blankorg", Map.of("id", "  ")))));
    }

    private static Arguments row(String label, String host, Consumer<Map<String, Object>> edit) {
        return Arguments.of(label, host, edit);
    }

    private WorkspaceRef onHost(String host) {
        return workspaces.findBySiteHost(host).orElseThrow();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("badOrganizations")
    @DisplayName("AC-001.11 edge 103 refuses a missing or foreign organization claim with MSG122")
    void refusesAMissingOrForeignOrganizationWithMsg122_AC_001_11_edge103(
            String label, String host, Consumer<Map<String, Object>> edit, CapturedOutput output) throws Exception {
        WorkspaceRef workspace = onHost(host);
        Map<String, Object> claims = validFor(workspace);
        edit.accept(claims);
        MockHttpServletRequest request = request(host);

        MockHttpServletResponse response = callback(request, claims);

        assertRefused(request, response, "/login?error=MSG122&google_domain=" + workspace.googleDomain(), output);
        assertAccountStepNeverRan();
    }

    static Stream<Arguments> badHds() {
        return Stream.of(
                Arguments.of("hd absent", "-"),
                Arguments.of("hd null", null),
                Arguments.of("hd empty", ""),
                Arguments.of("BETA's domain", "beta.example"),
                Arguments.of("suffix attack", "acme.example.evil.test"),
                Arguments.of("subdomain", "sub.acme.example"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("badHds")
    @DisplayName("AC-001.11 edge 103 refuses a missing or different hd with MSG122")
    void refusesAMissingOrDifferentHdWithMsg122_AC_001_11_edge103(String label, String hd, CapturedOutput output)
            throws Exception {
        Map<String, Object> claims = validFor(ACME);
        if ("-".equals(hd)) {
            claims.remove("hd");
        } else {
            claims.put("hd", hd);
        }
        MockHttpServletRequest request = request("acme.localhost");

        MockHttpServletResponse response = callback(request, claims);

        assertRefused(request, response, MSG122_ACME, output);
        assertThat(response.getHeader(HttpHeaders.LOCATION))
                .doesNotContain("evil")
                .doesNotContain("beta");
        assertAccountStepNeverRan();
    }

    @Test
    @DisplayName("AC-001.11 accepts an hd that differs only in case and passes on to the Account step")
    void acceptsAnHdThatDiffersOnlyInCase_AC_001_11() throws Exception {
        Map<String, Object> claims = validFor(ACME);
        claims.put("hd", "ACME.Example");

        MockHttpServletResponse response = callback("acme.localhost", claims);

        verify(accounts).signIn(SUB, EMAIL, NAME);
        assertThat(boundWhenCalled).containsExactly(ACME.id());
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/");
    }

    @Test
    @DisplayName("AC-001.10 refuses a Workspace that is not Active with MSG120")
    void refusesAWorkspaceThatIsNotActiveWithMsg120_AC_001_10(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = request("gamma.localhost");

        MockHttpServletResponse response = callback(request, validFor(GAMMA));

        assertRefused(request, response, "/login?error=MSG120", output);
        assertAccountStepNeverRan();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = Status.class,
            names = {"NO_ACCOUNT", "SUB_CONFLICT", "DEACTIVATED"})
    @DisplayName("AC-001.14 maps the Account outcome to its message, inside the Workspace scope")
    void mapsTheAccountOutcomeToItsMessage_AC_001_14(Status status, CapturedOutput output) throws Exception {
        stubbed = new Result(status, null);
        MockHttpServletRequest request = request("acme.localhost");

        MockHttpServletResponse response = callback(request, validFor(ACME));

        String message = status == Status.DEACTIVATED ? "MSG10" : "MSG09";
        assertRefused(request, response, "/login?error=" + message, output);
        verify(accounts).signIn(SUB, EMAIL, NAME);
        assertThat(boundWhenCalled).as("Workspace bound in signIn").containsExactly(ACME.id());
        assertThat(scope.boundIds()).containsExactly(ACME.id());
    }

    static Stream<Arguments> twoFailures() {
        return Stream.of(
                Arguments.of(
                        "(a) email_verified false and BETA's Organization -> MSG06",
                        "acme.localhost",
                        (Consumer<Map<String, Object>>) claims -> {
                            claims.put("email_verified", false);
                            claims.put("organization", Map.of("beta", Map.of("id", BETA.organizationId())));
                        },
                        "/login?error=MSG06"),
                Arguments.of(
                        "(b) suspended Workspace and BETA's Organization -> MSG122",
                        "gamma.localhost",
                        (Consumer<Map<String, Object>>) claims ->
                                claims.put("organization", Map.of("beta", Map.of("id", BETA.organizationId()))),
                        "/login?error=MSG122&google_domain=gamma.example"),
                Arguments.of(
                        "(c) suspended Workspace and no hd -> MSG122",
                        "gamma.localhost",
                        (Consumer<Map<String, Object>>) claims -> claims.remove("hd"),
                        "/login?error=MSG122&google_domain=gamma.example"),
                Arguments.of(
                        "(d) suspended Workspace and a Deactivated Account -> MSG120",
                        "gamma.localhost",
                        (Consumer<Map<String, Object>>) claims -> {},
                        "/login?error=MSG120"),
                Arguments.of(
                        "(e) BETA's Organization and no Account -> MSG122",
                        "acme.localhost",
                        (Consumer<Map<String, Object>>) claims ->
                                claims.put("organization", Map.of("beta", Map.of("id", BETA.organizationId()))),
                        MSG122_ACME));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("twoFailures")
    @DisplayName("AC-001.14 reports only the first failure")
    void reportsOnlyTheFirstFailure_AC_001_14(
            String label, String host, Consumer<Map<String, Object>> edit, String location, CapturedOutput output)
            throws Exception {
        stubbed = new Result(label.startsWith("(d)") ? Status.DEACTIVATED : Status.NO_ACCOUNT, null);
        Map<String, Object> claims = validFor(onHost(host));
        edit.accept(claims);
        MockHttpServletRequest request = request(host);

        MockHttpServletResponse response = callback(request, claims);

        assertRefused(request, response, location, output);
        assertAccountStepNeverRan();
    }

    @Test
    @DisplayName("AC-001.7 takes the Workspace only from the host")
    void takesTheWorkspaceOnlyFromTheHost_AC_001_7() throws Exception {
        MockHttpServletRequest request = request("acme.localhost");
        request.setParameter("workspace", BETA.id().toString());
        request.setParameter("site", "beta.localhost");
        request.setParameter("kc_idp_hint", "google-beta");
        request.addHeader("X-TSolve-Workspace", BETA.id().toString());
        request.setCookies(new Cookie("TSOLVE_WORKSPACE", "beta"));

        MockHttpServletResponse response = callback(request, validFor(ACME));

        assertThat(scope.boundIds()).containsExactly(ACME.id());
        assertThat(boundWhenCalled).containsExactly(ACME.id());
        assertThat(workspaces.askedHosts()).containsOnly("acme.localhost");
        assertThat(workspaces.askedIds()).doesNotContain(BETA.id());
        verify(accounts).signIn(SUB, EMAIL, NAME);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/");
    }

    @Test
    @DisplayName("AC-001.6 answers MSG141 when the callback host resolves no Workspace")
    void answersMsg141WhenTheCallbackHostResolvesNoWorkspace_AC_001_6(CapturedOutput output) throws Exception {
        workspaces.remove(ACME.id());
        MockHttpServletRequest request = request("acme.localhost");

        MockHttpServletResponse response = callback(request, validFor(ACME));

        assertRefused(request, response, "/login?error=MSG141", output);
        assertAccountStepNeverRan();
    }

    enum AccountStepFailure {
        SIGN_IN_THROWS,
        DATABASE_DOWN,
        WRAPPED_ROOT_CAUSE
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(AccountStepFailure.class)
    @DisplayName("AC-001.74 edge 133 answers MSG06, expires the cookie and logs one ERROR when the Account step fails")
    void answersMsg06WhenTheAccountStepItselfFails_AC_001_74_edge133(AccountStepFailure failure, CapturedOutput output)
            throws Exception {
        Map<String, Object> claims = validFor(ACME);
        switch (failure) {
            case SIGN_IN_THROWS ->
                doThrow(new IllegalStateException("boom for " + EMAIL + " sub " + SUB + " name " + NAME))
                        .when(accounts)
                        .signIn(any(), any(), any());
            case DATABASE_DOWN ->
                doThrow(new DataAccessResourceFailureException("connection refused for " + EMAIL))
                        .when(accounts)
                        .signIn(any(), any(), any());
            case WRAPPED_ROOT_CAUSE ->
                doThrow(new IllegalStateException("wrapped for " + EMAIL, new java.sql.SQLException("root for " + SUB)))
                        .when(accounts)
                        .signIn(any(), any(), any());
        }
        MockHttpServletRequest request = request("acme.localhost");

        MockHttpServletResponse response = callback(request, claims);

        assertRefused(request, response, "/login?error=MSG06", output);
        assertThat(output.getAll()).as("log").doesNotContain(NAME);
        List<String> errors =
                output.getAll().lines().filter(line -> line.contains(" ERROR ")).toList();
        assertThat(errors).as("ERROR lines").hasSize(1);
        assertThat(errors.get(0)).contains(ACME.id().toString());
        // Minor 4: the full class name and the root cause's class name, never the message
        String thrown =
                switch (failure) {
                    case SIGN_IN_THROWS, WRAPPED_ROOT_CAUSE -> "java.lang.IllegalStateException";
                    case DATABASE_DOWN -> "org.springframework.dao.DataAccessResourceFailureException";
                };
        assertThat(errors.get(0)).as("exception class").contains(thrown);
        if (failure == AccountStepFailure.WRAPPED_ROOT_CAUSE) {
            assertThat(errors.get(0)).as("root cause class").contains("java.sql.SQLException");
        }
    }

    static Stream<Arguments> noEmailClaims() {
        return Stream.of(
                Arguments.of("email absent", "-"),
                Arguments.of("email null", null),
                Arguments.of("email empty", ""),
                Arguments.of("email blank", "   "));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("noEmailClaims")
    @DisplayName("AC-001.74 edge 133 answers MSG06 for a verified token with no email, even if signIn would succeed")
    void answersMsg06WhenTheVerifiedTokenHasNoEmail_AC_001_74_edge133(String label, String email, CapturedOutput output)
            throws Exception {
        Map<String, Object> claims = validFor(ACME);
        if ("-".equals(email)) {
            claims.remove("email");
        } else {
            claims.put("email", email);
        }
        MockHttpServletRequest request = request("acme.localhost");

        MockHttpServletResponse response = callback(request, claims);

        assertRefused(request, response, "/login?error=MSG06", output);
        verifyNoInteractions(accounts);
        assertThat(scope.boundIds()).as("Workspaces bound").isEmpty();
        List<String> errors =
                output.getAll().lines().filter(line -> line.contains(" ERROR ")).toList();
        assertThat(errors).as("ERROR lines").hasSize(1);
        assertThat(errors.get(0)).contains(ACME.id().toString());
        assertThat(output.getAll()).as("log").doesNotContain(NAME);
    }

    @Test
    @DisplayName("AC-001.11 refuses a blank Workspace Google domain with a bare MSG122 even if hd is blank too")
    void refusesABlankWorkspaceDomainWithABareMsg122_AC_001_11(CapturedOutput output) throws Exception {
        Map<String, Object> claims = validFor(InMemoryWorkspaceLookup.BLANK_DOMAIN);
        MockHttpServletRequest request = request("blankdomain.localhost");

        MockHttpServletResponse response = callback(request, claims);

        assertRefused(request, response, "/login?error=MSG122", output);
        assertAccountStepNeverRan();
    }

    @Test
    @DisplayName("AC-001.11 refuses a Workspace with no Google domain with a bare MSG122")
    void refusesAWorkspaceWithNoDomainWithABareMsg122_AC_001_11(CapturedOutput output) throws Exception {
        Map<String, Object> claims = validFor(InMemoryWorkspaceLookup.NO_DOMAIN);
        claims.put("hd", "acme.example");
        MockHttpServletRequest request = request("nodomain.localhost");

        MockHttpServletResponse response = callback(request, claims);

        assertRefused(request, response, "/login?error=MSG122", output);
        assertAccountStepNeverRan();
    }

    @Test
    @DisplayName("AC-001.11 refuses an organization entry whose id is not a string")
    void refusesAnOrganizationIdThatIsNotAString_AC_001_11(CapturedOutput output) throws Exception {
        Map<String, Object> claims = validFor(ACME);
        claims.put("organization", Map.of("acme", Map.of("id", 12345)));
        MockHttpServletRequest request = request("acme.localhost");

        MockHttpServletResponse response = callback(request, claims);

        assertRefused(request, response, MSG122_ACME, output);
        assertAccountStepNeverRan();
    }

    @Test
    @DisplayName("AC-001.11 accepts an organization claim with several entries when one names the Workspace")
    void acceptsSeveralOrganizationEntriesWhenOneMatches_AC_001_11() throws Exception {
        Map<String, Object> claims = validFor(ACME);
        Map<String, Object> entries = new java.util.LinkedHashMap<>();
        entries.put("beta", Map.of("id", BETA.organizationId()));
        entries.put("acme", Map.of("id", ACME.organizationId()));
        claims.put("organization", entries);

        MockHttpServletResponse response = callback("acme.localhost", claims);

        verify(accounts).signIn(SUB, EMAIL, NAME);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/");
    }
}
