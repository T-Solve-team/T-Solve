package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.identity.signin.SignInTestFixtures;

/** Task 27a: the Keycloak end-session URL, built only from the session and the request's own origin (no Spring). */
class KeycloakLogoutUrlBuilderTest {

    private static final String LOGIN = SignInTestFixtures.LOGIN_ORIGIN;
    private static final Instant NOW = Instant.parse("2026-11-01T09:00:00Z");

    private final KeycloakLogoutUrlBuilder builder = new KeycloakLogoutUrlBuilder(SignInTestFixtures.properties());

    private static WebSession workspaceSession(String hint) {
        return new WebSession(
                WebSession.Kind.WORKSPACE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "acme.localhost",
                hint,
                NOW,
                NOW);
    }

    private static WebSession operatorSession(String hint) {
        return new WebSession(
                WebSession.Kind.OPERATOR, null, null, "ops@example.test", "admin.localhost", hint, NOW, NOW);
    }

    private static MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        return request;
    }

    /** The decoded parameters; each name must appear exactly once. */
    private static Map<String, String> params(URI url) {
        var query = UriComponentsBuilder.fromUri(url).build().getQueryParams();
        query.forEach(
                (name, values) -> assertThat(values).as("parameter " + name).hasSize(1));
        return query.toSingleValueMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, e -> URLDecoder.decode(e.getValue(), StandardCharsets.UTF_8)));
    }

    private static String endpointOf(URI url) {
        return url.getScheme() + "://" + url.getAuthority() + url.getPath();
    }

    @ParameterizedTest(name = "[{index}] {0}://{1}:{2} -> {3}")
    @CsvSource({
        "http, acme.localhost, 8080, http://acme.localhost:8080/login",
        "http, beta.localhost, 8080, http://beta.localhost:8080/login",
        "https, acme.example.com, 443, https://acme.example.com/login",
        "http, acme.localhost, 80, http://acme.localhost/login"
    })
    @DisplayName("AC-002.1 a Workspace site gets the tsolve end-session URL with the session's hint and its own /login")
    void workspaceSiteGetsTheTsolveUrl_AC_002_1(String scheme, String host, int port, String expectedRedirect) {
        URI url = builder.build(workspaceSession("the-hint"), request(scheme, host, port));

        assertThat(endpointOf(url)).isEqualTo(LOGIN + "/realms/tsolve/protocol/openid-connect/logout");
        assertThat(params(url))
                .containsOnlyKeys("client_id", "id_token_hint", "post_logout_redirect_uri")
                .containsEntry("client_id", "tsolve-web")
                .containsEntry("id_token_hint", "the-hint")
                .containsEntry("post_logout_redirect_uri", expectedRedirect);
    }

    @Test
    @DisplayName("AC-002.1 a session without an id_token_hint still gets client_id and no id_token_hint")
    void noHintStillGetsClientId_AC_002_1() {
        URI url = builder.build(workspaceSession(null), request("http", "acme.localhost", 8080));

        assertThat(endpointOf(url)).isEqualTo(LOGIN + "/realms/tsolve/protocol/openid-connect/logout");
        assertThat(params(url))
                .containsOnlyKeys("client_id", "post_logout_redirect_uri")
                .containsEntry("client_id", "tsolve-web")
                .containsEntry("post_logout_redirect_uri", "http://acme.localhost:8080/login");
    }

    @Test
    @DisplayName("AC-002.1 an Operator session on admin. gets the tsolve-ops end-session URL and the admin origin's"
            + " /login")
    void operatorSessionGetsTheOpsUrl_AC_002_1() {
        URI url = builder.build(operatorSession("ops-hint"), request("http", "admin.localhost", 8081));

        assertThat(endpointOf(url)).isEqualTo(LOGIN + "/realms/tsolve-ops/protocol/openid-connect/logout");
        assertThat(params(url))
                .containsOnlyKeys("client_id", "id_token_hint", "post_logout_redirect_uri")
                .containsEntry("client_id", "tsolve-console")
                .containsEntry("id_token_hint", "ops-hint")
                .containsEntry("post_logout_redirect_uri", SignInTestFixtures.ADMIN_ORIGIN + "/login");
    }

    @Test
    @DisplayName("edge 5 a hint with reserved characters is percent-encoded and adds no parameter")
    void reservedCharactersInTheHintAreEncoded_edge5() {
        String hostile = "a b&post_logout_redirect_uri=https://evil.example#x&client_id=evil+%26=";

        URI url = builder.build(workspaceSession(hostile), request("http", "acme.localhost", 8080));

        assertThat(url.getRawFragment()).isNull();
        assertThat(url.getRawQuery()).doesNotContain("evil.example#").doesNotContain(" ");
        assertThat(params(url))
                .containsOnlyKeys("client_id", "id_token_hint", "post_logout_redirect_uri")
                .containsEntry("id_token_hint", hostile)
                .containsEntry("client_id", "tsolve-web")
                .containsEntry("post_logout_redirect_uri", "http://acme.localhost:8080/login");
    }
}
