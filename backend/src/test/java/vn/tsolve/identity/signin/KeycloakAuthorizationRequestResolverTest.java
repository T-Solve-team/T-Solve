package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/** Plain unit test: the resolver is built over the real {@code tsolve-web} registration, no Keycloak needed. */
class KeycloakAuthorizationRequestResolverTest {

    private final KeycloakAuthorizationRequestResolver resolver =
            new KeycloakAuthorizationRequestResolver(new KeycloakClientRegistrations(SignInTestFixtures.properties()));

    private static MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/sign-in");
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        request.setSecure("https".equals(scheme));
        return request;
    }

    private OAuth2AuthorizationRequest resolve(WorkspaceRef workspace) {
        return resolver.resolve(request("http", workspace.siteHost(), 8081), workspace, "/");
    }

    static Stream<Arguments> hints() {
        return Stream.of(
                Arguments.of(InMemoryWorkspaceLookup.ACME, "google-acme"),
                Arguments.of(InMemoryWorkspaceLookup.BETA, "google-beta"));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @MethodSource("hints")
    @DisplayName("AC-001.52 sets kc_idp_hint to google-<slug> of the Workspace")
    void setsKcIdpHintToGoogleSlug_AC_001_52(WorkspaceRef workspace, String hint) {
        String uri = resolve(workspace).getAuthorizationRequestUri();

        assertThat(AuthorizationUris.params(uri).get("kc_idp_hint")).containsExactly(hint);
    }

    @Test
    @DisplayName("AC-001.52 adds a PKCE S256 challenge and keeps the verifier and the nonce on the server")
    void addsPkceS256AndKeepsVerifierAndNonceOnTheServer_AC_001_52() {
        OAuth2AuthorizationRequest authorization = resolve(InMemoryWorkspaceLookup.ACME);
        String uri = authorization.getAuthorizationRequestUri();
        String verifier = (String) authorization.getAttributes().get(PkceParameterNames.CODE_VERIFIER);
        String rawNonce = (String) authorization.getAttributes().get(OidcParameterNames.NONCE);

        assertThat(verifier).isNotBlank();
        assertThat(rawNonce).isNotBlank();
        assertThat(AuthorizationUris.one(uri, "code_challenge_method")).isEqualTo("S256");
        assertThat(AuthorizationUris.one(uri, "code_challenge")).isEqualTo(AuthorizationUris.sha256Base64Url(verifier));
        assertThat(AuthorizationUris.one(uri, "nonce")).isEqualTo(AuthorizationUris.sha256Base64Url(rawNonce));
        assertThat(uri).doesNotContain(verifier).doesNotContain(rawNonce).doesNotContain("code_verifier");
    }

    @Test
    @DisplayName("AC-001.52 makes a new random state, nonce and verifier for each request")
    void makesNewRandomStateNonceAndVerifierEachTime_AC_001_52() {
        Set<String> states = new HashSet<>();
        Set<String> nonces = new HashSet<>();
        Set<String> verifiers = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            OAuth2AuthorizationRequest authorization = resolve(InMemoryWorkspaceLookup.ACME);
            states.add(authorization.getState());
            nonces.add((String) authorization.getAttributes().get(OidcParameterNames.NONCE));
            verifiers.add((String) authorization.getAttributes().get(PkceParameterNames.CODE_VERIFIER));
        }

        assertThat(states).hasSize(20).noneMatch(value -> value == null || value.isBlank());
        assertThat(nonces).hasSize(20).noneMatch(value -> value == null || value.isBlank());
        assertThat(verifiers).hasSize(20).noneMatch(value -> value == null || value.isBlank());
    }

    static Stream<Arguments> origins() {
        return Stream.of(
                Arguments.of("http", "acme.localhost", 8080, "http://acme.localhost:8080/api/v1/auth/callback"),
                Arguments.of("http", "acme.localhost", 5173, "http://acme.localhost:5173/api/v1/auth/callback"),
                Arguments.of("https", "acme.tsolve.io.vn", 443, "https://acme.tsolve.io.vn/api/v1/auth/callback"));
    }

    @ParameterizedTest(name = "[{index}] {0}://{1}:{2}")
    @MethodSource("origins")
    @DisplayName("AC-001.52 builds the redirect URI from the request's scheme, host and port")
    void buildsRedirectUriFromTheRequestOrigin_AC_001_52(String scheme, String host, int port, String expected) {
        MockHttpServletRequest request = request(scheme, host, port);
        request.setQueryString("redirect_uri=http://evil.example/&returnTo=/x");
        request.setParameter("redirect_uri", "http://evil.example/");
        request.setParameter("returnTo", "/x");

        OAuth2AuthorizationRequest authorization = resolver.resolve(request, InMemoryWorkspaceLookup.ACME, "/x");

        assertThat(authorization.getRedirectUri()).isEqualTo(expected);
        assertThat(AuthorizationUris.one(authorization.getAuthorizationRequestUri(), "redirect_uri"))
                .isEqualTo(expected);
        assertThat(expected).doesNotContain("?").doesNotContain("#").doesNotContain("*");
    }

    @Test
    @DisplayName("AC-001.53 puts the target, the Workspace id and the kept returnTo into the request, not into the URL")
    void putsTargetWorkspaceIdAndReturnToIntoTheRequestNotTheUrl_AC_001_53() {
        OAuth2AuthorizationRequest authorization =
                resolver.resolve(request("http", "acme.localhost", 8081), InMemoryWorkspaceLookup.ACME, "/tickets/42");

        assertThat(authorization.getAttributes().get(KeycloakAuthorizationRequestResolver.TARGET))
                .hasToString("WORKSPACE");
        assertThat(authorization.getAttributes().get(KeycloakAuthorizationRequestResolver.WORKSPACE_ID))
                .hasToString(InMemoryWorkspaceLookup.ACME.id().toString());
        assertThat(authorization.getAttributes().get(KeycloakAuthorizationRequestResolver.RETURN_TO))
                .isEqualTo("/tickets/42");
        assertThat(authorization.getAuthorizationRequestUri())
                .doesNotContain("/tickets/42")
                .doesNotContain("tickets")
                .doesNotContain("return_to");
    }
}
