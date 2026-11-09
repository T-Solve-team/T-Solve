package vn.tsolve.identity.keycloak;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner.ProvisionedObjects;

/** The Admin API client and the adapter's lookups against a fake Keycloak (WireMock, no Docker). */
class KeycloakAdminClientTest {

    private static final String SECRET = "unit-test-provisioner-secret";
    private static final String TOKEN_PATH = "/realms/tsolve/protocol/openid-connect/token";
    private static final String ADMIN = "/admin/realms/tsolve";
    private static final UUID WORKSPACE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String WEB_CLIENT_JSON =
            """
            [{"id":"web-uuid","clientId":"tsolve-web",
              "redirectUris":["http://acme.localhost:8080/api/v1/auth/callback"],
              "attributes":{"post.logout.redirect.uris":"http://acme.localhost:8080/login"}}]
            """;
    private static final String ORGANIZATION_JSON =
            """
            {"id":"org-1","alias":"acme","enabled":true,
             "attributes":{"tsolve.workspace-id":["11111111-2222-3333-4444-555555555555"]},
             "domains":[{"name":"acme.test","verified":false}]}
            """;
    private static final String IDP_JSON =
            """
            {"alias":"google-acme","providerId":"google",
             "config":{"tsolve.workspace-id":"11111111-2222-3333-4444-555555555555"}}
            """;

    private static WireMockServer keycloak;

    @BeforeAll
    static void start() {
        keycloak = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        keycloak.start();
    }

    @AfterAll
    static void stop() {
        keycloak.stop();
    }

    @BeforeEach
    void reset() {
        keycloak.resetAll();
    }

    private static KeycloakProvisioningProperties properties() {
        return new KeycloakProvisioningProperties(
                keycloak.baseUrl(), "tsolve", SECRET, "google-id", "google-secret", "http://{slug}.localhost:8080");
    }

    private static KeycloakAdminClient client() {
        return new KeycloakAdminClient(properties());
    }

    private static KeycloakWorkspaceSignInProvisioner adapter() {
        // the lock gets a DataSource that accepts every statement: these tests are about the HTTP calls
        ProvisioningLock lock = new ProvisioningLock(mock(DataSource.class, RETURNS_DEEP_STUBS));
        return new KeycloakWorkspaceSignInProvisioner(client(), lock, properties());
    }

    private static void stubToken() {
        keycloak.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tok-1\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
    }

    private static void stubJson(MappingBuilder request, String body) {
        keycloak.stubFor(request.willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    @DisplayName(
            "AC-001.61 authenticates as tsolve-provisioner with client credentials on the realm tsolve and sends the token as a Bearer")
    void authenticatesAsProvisioner_AC_001_61() {
        stubToken();
        stubJson(get(urlPathMatching(ADMIN + "/clients.*")), WEB_CLIENT_JSON);

        KeycloakAdminClient client = client();
        client.readWebClientUris();
        client.readWebClientUris();

        String form = "grant_type=client_credentials&client_id=tsolve-provisioner&client_secret=" + SECRET;
        keycloak.verify(
                1,
                postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                        .withRequestBody(equalTo(form))
                        .withHeader("Content-Type", containing("application/x-www-form-urlencoded")));
        keycloak.verify(0, postRequestedFor(urlPathMatching("/realms/master/.*")));
        keycloak.verify(
                2,
                getRequestedFor(urlPathMatching(ADMIN + "/clients.*"))
                        .withHeader("Authorization", equalTo("Bearer tok-1")));

        // a 401 from the token endpoint: no secret and no response body in the message
        keycloak.resetAll();
        String refusal = "{\"error\":\"unauthorized_client\",\"error_description\":\"BODY-MARKER-" + SECRET + "\"}";
        keycloak.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody(refusal)));
        assertThatThrownBy(() -> client().readWebClientUris())
                .isInstanceOf(RuntimeException.class)
                .satisfies(e -> {
                    StringBuilder chain = new StringBuilder();
                    for (Throwable t = e; t != null; t = t.getCause()) {
                        chain.append(t.getMessage()).append('\n');
                    }
                    assertThat(chain.toString()).doesNotContain(SECRET).doesNotContain("BODY-MARKER");
                });
    }

    @Test
    @DisplayName("AC-001.58 N8 an Organization answer without a Location header is an error, not an empty id")
    void createOrganizationWithoutLocationThrows_AC_001_58_N8() {
        stubToken();
        keycloak.stubFor(post(urlPathEqualTo(ADMIN + "/organizations"))
                .willReturn(aResponse().withStatus(201)));

        assertThatThrownBy(() -> client().createOrganization(WORKSPACE, "acme", "acme.test"))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET));
    }

    // ---- S4: the IdP is found through the Organization, never by scanning every IdP ----

    private static void stubExistingWorkspace() {
        stubToken();
        stubJson(get(urlPathEqualTo(ADMIN + "/organizations")), "[{\"id\":\"org-1\",\"alias\":\"acme\"}]");
        stubJson(get(urlPathEqualTo(ADMIN + "/organizations/org-1")), ORGANIZATION_JSON);
        stubJson(get(urlPathEqualTo(ADMIN + "/identity-provider/instances/google-acme")), IDP_JSON);
        // the decoy: the unpaged list of every IdP. It answers correctly, so only the request count can fail the test
        stubJson(get(urlPathEqualTo(ADMIN + "/identity-provider/instances")), "[" + IDP_JSON + "]");
        stubJson(get(urlPathMatching(ADMIN + "/clients.*")), WEB_CLIENT_JSON);
    }

    private static void verifyNoUnpagedIdpList() {
        keycloak.verify(0, getRequestedFor(urlPathEqualTo(ADMIN + "/identity-provider/instances")));
        keycloak.verify(
                moreThanOrExactly(1),
                getRequestedFor(urlPathEqualTo(ADMIN + "/identity-provider/instances/google-acme")));
    }

    @Test
    @DisplayName(
            "AC-001.57 S4 check finds the IdP by google-<slug> of the Organization and makes no unpaged call of all IdPs")
    void checkLocatesIdpThroughOrganization_AC_001_57_S4() {
        stubExistingWorkspace();

        ProvisionedObjects objects = adapter().check(WORKSPACE);

        assertThat(objects).isEqualTo(new ProvisionedObjects(true, true, true, true));
        verifyNoUnpagedIdpList();
    }

    @Test
    @DisplayName(
            "AC-001.62 S4 remove finds the IdP by google-<slug> of the Organization and makes no unpaged call of all IdPs")
    void removeLocatesIdpThroughOrganization_AC_001_62_S4() {
        stubExistingWorkspace();
        keycloak.stubFor(delete(ADMIN + "/identity-provider/instances/google-acme")
                .willReturn(aResponse().withStatus(204)));
        keycloak.stubFor(put(ADMIN + "/clients/web-uuid").willReturn(aResponse().withStatus(204)));
        stubJson(get(urlPathEqualTo(ADMIN + "/organizations/org-1/members")), "[]");
        keycloak.stubFor(
                delete(ADMIN + "/organizations/org-1").willReturn(aResponse().withStatus(204)));

        adapter().remove(WORKSPACE);

        verifyNoUnpagedIdpList();
        keycloak.verify(1, deleteRequestedFor(urlPathEqualTo(ADMIN + "/identity-provider/instances/google-acme")));
        keycloak.verify(1, deleteRequestedFor(urlPathEqualTo(ADMIN + "/organizations/org-1")));
    }

    private static MappingBuilder delete(String path) {
        return com.github.tomakehurst.wiremock.client.WireMock.delete(urlPathEqualTo(path));
    }

    private static MappingBuilder put(String path) {
        return com.github.tomakehurst.wiremock.client.WireMock.put(urlPathEqualTo(path));
    }
}
