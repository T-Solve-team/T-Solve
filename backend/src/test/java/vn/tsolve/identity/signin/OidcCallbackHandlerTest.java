package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * Task 23c: the end of the Keycloak code exchange. Plain unit tests on a Workspace host ({@code acme.localhost}); the
 * handler is built with a mock environment with or without the {@code dev} profile.
 */
@ExtendWith(OutputCaptureExtension.class)
class OidcCallbackHandlerTest {

    private static final String MSG141_LOCATION = "/login?error=MSG141";
    private static final String MSG06_LOCATION = "/login?error=MSG06";
    private static final String COOKIE = RedisAuthorizationRequestRepository.COOKIE_NAME;
    private static final String EMAIL = "alice@acme.test";
    private static final String SUB = "kc-sub-7f3a9c52";
    private static final String ID_TOKEN_VALUE = "id-token-value-should-never-appear";
    private static final Map<String, Object> ORGANIZATION = Map.of("acme", Map.of("id", "org-id-acme"));

    private static OidcCallbackHandler handler(boolean dev) {
        MockEnvironment environment = new MockEnvironment();
        if (dev) {
            environment.setActiveProfiles("dev");
        }
        return new OidcCallbackHandler(environment);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/callback");
        request.setServerName("acme.localhost");
        request.setServerPort(8080);
        request.setScheme("http");
        return request;
    }

    private static OAuth2AuthenticationException oauth2(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }

    private static MockHttpServletResponse fail(AuthenticationException exception) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler(true).onAuthenticationFailure(request(), response, exception);
        return response;
    }

    private static OAuth2AuthenticationToken signedIn(Map<String, Object> claims) {
        Map<String, Object> all = new HashMap<>(claims);
        all.putIfAbsent("iss", "http://login.localhost:8080/realms/tsolve");
        all.putIfAbsent("aud", List.of("tsolve-web"));
        all.putIfAbsent("nonce", "nonce-value-should-never-appear");
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(ID_TOKEN_VALUE, now, now.plusSeconds(300), all);
        DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), KeycloakClientRegistrations.REGISTRATION_ID);
    }

    private static Map<String, Object> verifiedClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", SUB);
        claims.put("email", EMAIL);
        claims.put("email_verified", true);
        claims.put("organization", ORGANIZATION);
        claims.put("hd", "acme.test");
        return claims;
    }

    private static MockHttpServletResponse succeed(
            boolean dev, MockHttpServletRequest request, Map<String, Object> claims) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler(dev).onAuthenticationSuccess(request, response, signedIn(claims));
        return response;
    }

    private static void assertRedirectedTo(MockHttpServletResponse response, String location) throws Exception {
        assertThat(response.getStatus()).as("status").isEqualTo(HttpServletResponse.SC_FOUND);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).as("Location").isEqualTo(location);
        assertThat(response.getForwardedUrl()).as("forward").isNull();
        assertThat(response.getContentAsString()).as("body").isEmpty();
    }

    /** Exactly one Set-Cookie for the auth-request cookie, empty, Max-Age=0, with the __Host- attributes. */
    private static void assertExpiryCookie(MockHttpServletResponse response) {
        List<String> headers = SignInTestFixtures.setCookie(response, COOKIE);
        assertThat(headers).as("Set-Cookie of %s", COOKIE).hasSize(1);
        assertThat(headers.get(0)).startsWith(COOKIE + "=;");
        Map<String, String> attributes = SignInTestFixtures.attributes(headers.get(0));
        assertThat(attributes).containsEntry("max-age", "0").containsEntry("path", "/");
        assertThat(attributes).containsKeys("secure", "httponly");
        assertThat(attributes.get("samesite")).isEqualToIgnoringCase("Lax");
        assertThat(attributes).doesNotContainKey("domain");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "authorization_request_not_found",
                "invalid_state_parameter",
                "client_registration_not_found",
                "invalid_request"
            })
    @DisplayName("AC-001.6 answers MSG141 when the saved request is missing, unknown, used or does not match")
    void mapsAMissingOrUnmatchedRequestToMsg141_AC_001_6(String code) throws Exception {
        MockHttpServletResponse response = fail(oauth2(code));

        assertRedirectedTo(response, MSG141_LOCATION);
    }

    /**
     * The request attribute the saved-request check sets once the callback's state and Workspace match the saved
     * request (review finding 3). With it, {@code invalid_request} is an error Keycloak returned, not Spring's "neither
     * code nor error".
     */
    private static final String REQUEST_CHECKED_ATTRIBUTE = "vn.tsolve.identity.signin.requestChecked";

    @Test
    @DisplayName("AC-001.8 answers MSG06 to invalid_request that Keycloak returns for a matching saved request")
    void mapsInvalidRequestFromKeycloakWithAMatchingRequestToMsg06_AC_001_8() throws Exception {
        MockHttpServletRequest request = request();
        request.setAttribute(REQUEST_CHECKED_ATTRIBUTE, Boolean.TRUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler(true).onAuthenticationFailure(request, response, oauth2("invalid_request"));

        assertRedirectedTo(response, MSG06_LOCATION);
        assertExpiryCookie(response);
    }

    static Stream<Arguments> keycloakErrors() {
        return Stream.of(
                Arguments.of("access_denied", null),
                Arguments.of("login_required", null),
                Arguments.of("temporarily_unavailable", null),
                Arguments.of("server_error", null),
                Arguments.of("an_error_nobody_has_seen", null),
                Arguments.of("access_denied", "<script>alert(1)</script>\r\nSet-Cookie: stolen=1"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("keycloakErrors")
    @DisplayName("AC-001.8 answers MSG06 to an error Keycloak returns")
    void mapsAKeycloakErrorToMsg06_AC_001_8(String code, String description) throws Exception {
        MockHttpServletResponse response =
                fail(new OAuth2AuthenticationException(new OAuth2Error(code, description, null)));

        assertRedirectedTo(response, MSG06_LOCATION);
        assertThat(response.getHeaderNames()).as("headers").doesNotContain("stolen");
        response.getHeaderNames().forEach(name -> assertThat(response.getHeaders(name))
                .as("header %s", name)
                .noneMatch(value -> value.contains("<script>") || value.contains("stolen")));
    }

    static Stream<AuthenticationException> failedExchanges() {
        return Stream.of(
                oauth2("invalid_token_response"),
                oauth2("invalid_id_token"),
                oauth2("invalid_nonce"),
                oauth2("invalid_user_info_response"),
                new InternalAuthenticationServiceException("the token endpoint is down"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("failedExchanges")
    @DisplayName("AC-001.9 answers MSG06 when the code exchange or the ID token check fails")
    void mapsAFailedExchangeOrTokenCheckToMsg06_AC_001_9(AuthenticationException exception) throws Exception {
        MockHttpServletResponse response = fail(exception);

        assertRedirectedTo(response, MSG06_LOCATION);
    }

    static Stream<Arguments> unverifiedEmails() {
        Map<String, Object> missing = verifiedClaims();
        missing.remove("email_verified");
        Map<String, Object> notVerified = verifiedClaims();
        notVerified.put("email_verified", false);
        return Stream.of(
                Arguments.of("email_verified false", notVerified), Arguments.of("email_verified missing", missing));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("unverifiedEmails")
    @DisplayName("AC-001.9 refuses a token whose email_verified is false or missing")
    void refusesAnUnverifiedEmailWithMsg06_AC_001_9(String label, Map<String, Object> claims) throws Exception {
        MockHttpServletRequest request = request();

        MockHttpServletResponse response = succeed(true, request, claims);

        assertRedirectedTo(response, MSG06_LOCATION);
        assertThat(request.getAttribute(OidcCallbackHandler.CLAIMS_ATTRIBUTE))
                .as("claims attribute")
                .isNull();
    }

    enum Answer {
        MSG141_FAILURE,
        MSG06_FAILURE,
        DEV_SUCCESS,
        NON_DEV_SUCCESS,
        UNVERIFIED_EMAIL
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(Answer.class)
    @DisplayName("AC-001.53 expires __Host-TSOLVE_AUTHREQ in every answer of the callback")
    void expiresTheAuthRequestCookieInEveryAnswer_AC_001_53(Answer answer) throws Exception {
        MockHttpServletResponse response =
                switch (answer) {
                    case MSG141_FAILURE -> fail(oauth2("authorization_request_not_found"));
                    case MSG06_FAILURE -> fail(oauth2("access_denied"));
                    case DEV_SUCCESS -> succeed(true, request(), verifiedClaims());
                    case NON_DEV_SUCCESS -> succeed(false, request(), verifiedClaims());
                    case UNVERIFIED_EMAIL -> {
                        Map<String, Object> claims = verifiedClaims();
                        claims.put("email_verified", false);
                        yield succeed(true, request(), claims);
                    }
                };

        assertExpiryCookie(response);
    }

    @Test
    @DisplayName("edge 122 (Q2 A) forwards a dev sign-in in the same request to the claims page")
    void forwardsADevSignInToTheClaimsPageInTheSameRequest_edge122() throws Exception {
        MockHttpServletRequest request = request();

        MockHttpServletResponse response = succeed(true, request, verifiedClaims());

        assertThat(response.getForwardedUrl()).isEqualTo(OidcCallbackHandler.DEV_CLAIMS_PATH);
        assertThat(request.getAttribute(OidcCallbackHandler.CLAIMS_ATTRIBUTE))
                .isInstanceOfSatisfying(Map.class, claims -> {
                    assertThat(claims).containsOnlyKeys("sub", "email", "organization", "hd");
                    assertThat(claims)
                            .containsEntry("sub", SUB)
                            .containsEntry("email", EMAIL)
                            .containsEntry("organization", ORGANIZATION)
                            .containsEntry("hd", "acme.test");
                    assertThat(claims.toString())
                            .doesNotContain(ID_TOKEN_VALUE)
                            .doesNotContain("nonce-value-should-never-appear");
                });
        assertThat(response.getHeader(HttpHeaders.LOCATION)).as("Location").isNull();
        assertThat(response.getStatus() / 100).as("no 3xx").isNotEqualTo(3);
    }

    @Test
    @DisplayName("edge 122 (Q2 A) answers a success outside dev with MSG06 and one WARN")
    void answersASuccessOutsideDevWithMsg06AndOneWarn_edge122(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = request();

        MockHttpServletResponse response = succeed(false, request, verifiedClaims());

        assertRedirectedTo(response, MSG06_LOCATION);
        assertThat(request.getAttribute(OidcCallbackHandler.CLAIMS_ATTRIBUTE)).isNull();
        List<String> warnings =
                output.getAll().lines().filter(line -> line.contains("WARN")).toList();
        assertThat(warnings).as("WARN lines").hasSize(1);
        assertThat(warnings.get(0)).contains("session not built yet");
        assertThat(output.getAll()).as("log").doesNotContain(EMAIL).doesNotContain(SUB);
    }
}
