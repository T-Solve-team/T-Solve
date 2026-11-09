package vn.tsolve.identity.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.signin.port.ProvisioningWiring;

/**
 * The Keycloak Admin REST API, as the client {@code tsolve-provisioner} (AC-001.61). It knows the shapes of the
 * requests and nothing about the order of the steps; {@link KeycloakWorkspaceSignInProvisioner} owns that. Methods
 * that change Keycloak start with create, write, delete, update, add or remove. Messages carry the status and the
 * path, never a secret, a response body or an email.
 */
@Component
@Conditional(ProvisioningWiring.Configured.class)
public class KeycloakAdminClient {

    static final String WEB_CLIENT_ID = "tsolve-web";
    static final String WORKSPACE_TAG = "tsolve.workspace-id";
    static final String LOGOUT_URIS_ATTRIBUTE = "post.logout.redirect.uris";
    static final String IDP_ALIAS_PREFIX = "google-";

    private static final String PROVISIONER_CLIENT_ID = "tsolve-provisioner";
    private static final String FIRST_BROKER_LOGIN_FLOW = "tsolve first broker login";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Duration TOKEN_SKEW = Duration.ofSeconds(30);
    private static final int SCAN_PAGE = 100;
    private static final int MAX_SCAN_PAGES = 100;

    /** Both URI lists of {@code tsolve-web}. */
    public record ClientUris(List<String> redirectUris, List<String> postLogoutRedirectUris) {}

    /** An Organization as {@code provision} needs to compare it. */
    public record OrganizationRef(String id, String alias, List<String> domains) {}

    private final KeycloakProvisioningProperties properties;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private String token;
    private Instant tokenExpiry = Instant.MIN;

    public KeycloakAdminClient(KeycloakProvisioningProperties properties) {
        this.properties = properties;
    }

    // ---- the client tsolve-web ----

    public ClientUris readWebClientUris() {
        return urisOf(webClient());
    }

    public void writeWebClientUris(ClientUris uris) {
        ObjectNode client = webClient();
        ArrayNode redirect = client.putArray("redirectUris");
        uris.redirectUris().forEach(redirect::add);
        ObjectNode attributes =
                client.has("attributes") && client.get("attributes").isObject()
                        ? (ObjectNode) client.get("attributes")
                        : client.putObject("attributes");
        attributes.put(LOGOUT_URIS_ATTRIBUTE, String.join("##", uris.postLogoutRedirectUris()));
        send("PUT", admin("/clients/" + enc(client.get("id").asText())), client.toString(), 204);
    }

    private ObjectNode webClient() {
        JsonNode found = read(admin("/clients?clientId=" + enc(WEB_CLIENT_ID)));
        if (!found.isArray() || found.isEmpty() || !found.get(0).isObject()) {
            throw new IllegalStateException("The Keycloak client " + WEB_CLIENT_ID + " was not found");
        }
        return (ObjectNode) found.get(0);
    }

    private static ClientUris urisOf(JsonNode client) {
        List<String> redirect = new ArrayList<>();
        client.path("redirectUris").forEach(uri -> redirect.add(uri.asText()));
        String raw = client.path("attributes").path(LOGOUT_URIS_ATTRIBUTE).asText("");
        List<String> logout = raw.isBlank() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(raw.split("##")));
        return new ClientUris(redirect, logout);
    }

    // ---- Organizations ----

    public Optional<OrganizationRef> findOrganization(UUID workspaceId) {
        JsonNode found = read(admin("/organizations?q=" + enc(WORKSPACE_TAG + ":" + workspaceId)));
        for (JsonNode listed : found) {
            // the list answer holds no attributes: read the Organization to confirm the tag
            JsonNode organization =
                    read(admin("/organizations/" + enc(listed.path("id").asText())));
            if (organization.path("attributes").path(WORKSPACE_TAG).toString().contains(workspaceId.toString())) {
                List<String> domains = new ArrayList<>();
                organization
                        .path("domains")
                        .forEach(d -> domains.add(d.path("name").asText()));
                return Optional.of(new OrganizationRef(
                        organization.path("id").asText(),
                        organization.path("alias").asText(),
                        domains));
            }
        }
        return Optional.empty();
    }

    public String createOrganization(UUID workspaceId, String slug, String domain) {
        ObjectNode body = JSON.createObjectNode();
        body.put("name", slug);
        body.put("alias", slug);
        body.put("enabled", true);
        body.putArray("domains").addObject().put("name", domain).put("verified", false);
        body.putObject("attributes").putArray(WORKSPACE_TAG).add(workspaceId.toString());
        HttpResponse<String> created = send("POST", admin("/organizations"), body.toString(), 201);
        String location = created.headers()
                .firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("Keycloak created the Organization without a Location"));
        return location.substring(location.lastIndexOf('/') + 1);
    }

    public void deleteOrganization(String organizationId) {
        deleteQuietly(admin("/organizations/" + enc(organizationId)));
    }

    /** Deletes the Keycloak users that are members of the Organization. */
    public void deleteOrganizationUsers(String organizationId) {
        for (int round = 0; round < 1000; round++) {
            JsonNode members = read(admin("/organizations/" + enc(organizationId) + "/members?first=0&max=100"));
            if (!members.isArray() || members.isEmpty()) {
                return;
            }
            for (JsonNode member : members) {
                deleteQuietly(admin("/users/" + enc(member.path("id").asText())));
            }
        }
    }

    // ---- identity providers ----

    /**
     * The alias of the identity provider tagged with the Workspace id. With the alias of the Workspace's Organization
     * it is one read of {@code google-<slug>}; only without an Organization the identity providers are scanned, page
     * by page.
     */
    public Optional<String> findIdentityProviderAlias(UUID workspaceId, Optional<String> organizationAlias) {
        if (organizationAlias.isPresent()) {
            String alias = IDP_ALIAS_PREFIX + organizationAlias.get();
            HttpResponse<String> response =
                    send("GET", admin("/identity-provider/instances/" + enc(alias)), null, 200, 404);
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            return isTagged(parse(response, "/identity-provider/instances"), workspaceId)
                    ? Optional.of(alias)
                    : Optional.empty();
        }
        for (int first = 0, round = 0; round < MAX_SCAN_PAGES; first += SCAN_PAGE, round++) {
            JsonNode page = read(admin(
                    "/identity-provider/instances?briefRepresentation=false&first=" + first + "&max=" + SCAN_PAGE));
            for (JsonNode idp : page) {
                if (isTagged(idp, workspaceId)) {
                    return Optional.of(idp.path("alias").asText());
                }
            }
            if (page.size() != SCAN_PAGE) {
                break; // the last page, or a server that ignores paging and has just sent everything
            }
        }
        return Optional.empty();
    }

    private static boolean isTagged(JsonNode idp, UUID workspaceId) {
        return workspaceId
                .toString()
                .equals(idp.path("config").path(WORKSPACE_TAG).asText());
    }

    /** Creates the Google identity provider, its {@code hd} mapper and its link to the Organization. */
    public void createGoogleIdentityProvider(String slug, UUID workspaceId, String domain, String organizationId) {
        String alias = IDP_ALIAS_PREFIX + slug;
        ObjectNode idp = JSON.createObjectNode();
        idp.put("alias", alias);
        idp.put("displayName", "Google (" + slug + ")");
        idp.put("providerId", "google");
        idp.put("enabled", true);
        idp.put("hideOnLogin", true);
        idp.put("trustEmail", true);
        idp.put("firstBrokerLoginFlowAlias", FIRST_BROKER_LOGIN_FLOW);
        ObjectNode config = idp.putObject("config");
        config.put("clientId", properties.googleClientId());
        config.put("clientSecret", properties.googleClientSecret());
        config.put("hostedDomain", domain);
        config.put("syncMode", "IMPORT");
        config.put("kc.org.domain", domain);
        config.put(WORKSPACE_TAG, workspaceId.toString());
        send("POST", admin("/identity-provider/instances"), idp.toString(), 201);

        ObjectNode mapper = JSON.createObjectNode();
        mapper.put("name", "hd");
        mapper.put("identityProviderAlias", alias);
        mapper.put("identityProviderMapper", "oidc-user-attribute-idp-mapper");
        ObjectNode mapperConfig = mapper.putObject("config");
        mapperConfig.put("claim", "hd");
        mapperConfig.put("user.attribute", "hd");
        mapperConfig.put("syncMode", "INHERIT");
        send("POST", admin("/identity-provider/instances/" + enc(alias) + "/mappers"), mapper.toString(), 201);

        send("POST", admin("/organizations/" + enc(organizationId) + "/identity-providers"), quote(alias), 204);
    }

    public void deleteIdentityProvider(String alias) {
        deleteQuietly(admin("/identity-provider/instances/" + enc(alias)));
    }

    // ---- HTTP ----

    private String admin(String path) {
        return trimmedBaseUrl() + "/admin/realms/" + enc(properties.realm()) + path;
    }

    private String trimmedBaseUrl() {
        String base = properties.baseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private JsonNode read(String url) {
        return parse(send("GET", url, null, 200), pathOf(url));
    }

    private static JsonNode parse(HttpResponse<String> response, String path) {
        try {
            return JSON.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Keycloak answered " + path + " with a body that is not JSON");
        }
    }

    private void deleteQuietly(String url) {
        send("DELETE", url, null, 204, 404);
    }

    private HttpResponse<String> send(String method, String url, String body, int... okStatuses) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token())
                .header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = execute(request.build());
        for (int ok : okStatuses) {
            if (response.statusCode() == ok) {
                return response;
            }
        }
        throw new IllegalStateException(
                "Keycloak Admin API " + method + " " + pathOf(url) + " answered " + response.statusCode());
    }

    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }
        String form = "grant_type=client_credentials&client_id=" + PROVISIONER_CLIENT_ID + "&client_secret="
                + URLEncoder.encode(properties.adminClientSecret(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        trimmedBaseUrl() + "/realms/" + enc(properties.realm()) + "/protocol/openid-connect/token"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = execute(request);
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Keycloak refused the " + PROVISIONER_CLIENT_ID + " token request (" + response.statusCode() + ")");
        }
        try {
            JsonNode answer = JSON.readTree(response.body());
            token = answer.get("access_token").asText();
            tokenExpiry = Instant.now()
                    .plusSeconds(answer.path("expires_in").asLong(60))
                    .minus(TOKEN_SKEW);
            return token;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Keycloak answered the token request without a usable token");
        }
    }

    private HttpResponse<String> execute(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Keycloak could not be reached (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Keycloak");
        }
    }

    private static String pathOf(String url) {
        int admin = url.indexOf("/admin/");
        return admin >= 0 ? url.substring(admin) : URI.create(url).getPath();
    }

    private static String enc(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String quote(String value) {
        return JSON.getNodeFactory().textNode(value).toString();
    }
}
