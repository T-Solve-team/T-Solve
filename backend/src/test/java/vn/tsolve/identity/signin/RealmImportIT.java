package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.testcontainers.utility.MountableFile;
import vn.tsolve.identity.dev.DevAccounts;

/**
 * Imports the committed {@code infra/keycloak/realm-tsolve.json} into a real Keycloak and checks what AC-001.54 and
 * AC-001.63 to .65 ask of the realm. The container gets the same file compose mounts, so the test and the dev
 * stack cannot drift. On Docker 29 run with {@code -DargLine=-Dapi.version=1.44} (never committed).
 */
class RealmImportIT {

    private static final String REALM = "tsolve";
    private static final String CLIENT_ID = "tsolve-web";
    private static final String WEB_CLIENT_SECRET = "it-keycloak-web-client-secret";
    private static final Path REALM_FILE =
            Path.of("../infra/keycloak/realm-tsolve.json").toAbsolutePath().normalize();
    private static final Pattern CALLBACK_URI =
            Pattern.compile("^http://(acme|beta)\\.localhost(:\\d+)?/api/v1/auth/callback$");
    private static final String BUILT_IN_FIRST_BROKER_FLOW = "first broker login";
    private static final int EIGHT_HOURS = 8 * 60 * 60;
    private static final int SIXTY_MINUTES = 60 * 60;

    private static KeycloakContainer keycloak;
    private static HttpClient http;

    @BeforeAll
    static void startKeycloak() {
        keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4.7")
                .withEnv("KEYCLOAK_WEB_CLIENT_SECRET", WEB_CLIENT_SECRET)
                .withCopyFileToContainer(
                        // the container adds --import-realm itself, as it does for withRealmImportFile
                        MountableFile.forHostPath(REALM_FILE), "/opt/keycloak/data/import/realm-tsolve.json");
        keycloak.start();
        http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterAll
    static void stopKeycloak() {
        if (keycloak != null) {
            keycloak.stop();
        }
    }

    // ---- helpers ----

    private static RealmResource realm() {
        return keycloak.getKeycloakAdminClient().realm(REALM);
    }

    private static ClientResource webClient() {
        List<ClientRepresentation> found = realm().clients().findByClientId(CLIENT_ID);
        assertThat(found).as("the realm has the client " + CLIENT_ID).hasSize(1);
        return realm().clients().get(found.get(0).getId());
    }

    private static List<IdentityProviderRepresentation> identityProviders() {
        List<IdentityProviderRepresentation> all = realm().identityProviders().findAll();
        assertThat(all).as("the realm has identity providers").isNotEmpty();
        return all;
    }

    private static OrganizationRepresentation organization(String alias) {
        return realm().organizations().getAll().stream()
                .filter(o -> alias.equals(o.getAlias()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the realm has no Organization " + alias));
    }

    /** Flat list of a flow's executions, subflows included (Keycloak returns the tree flattened). */
    private static List<AuthenticationExecutionInfoRepresentation> executions(String flowAlias) {
        return realm().flows().getExecutions(flowAlias);
    }

    private static Set<String> enabledProviders(String flowAlias) {
        return executions(flowAlias).stream()
                .filter(e -> !"DISABLED".equals(e.getRequirement()))
                .map(AuthenticationExecutionInfoRepresentation::getProviderId)
                .collect(Collectors.toSet());
    }

    private static String acmeRedirectUri() {
        List<String> uris = webClient().toRepresentation().getRedirectUris();
        assertThat(uris).as("redirect URIs of " + CLIENT_ID).isNotNull().isNotEmpty();
        return uris.stream()
                .filter(u -> u.contains("//acme.localhost"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no acme redirect URI in " + uris));
    }

    private static String pkceChallenge() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest("a-verifier-of-at-least-43-characters-for-s256-test".getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpResponse<String> authorize(String redirectUri, Map<String, String> extra) {
        // proves the client exists, so a missing client's own error page can never pass for a refusal
        webClient();
        List<String> query = new ArrayList<>();
        query.add("client_id=" + CLIENT_ID);
        query.add("redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        query.add("response_type=code");
        query.add("scope=openid");
        query.add("state=s1");
        extra.forEach((k, v) -> query.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        URI uri = URI.create(keycloak.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/auth?"
                + String.join("&", query));
        try {
            return http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> s256() {
        return Map.of("code_challenge", pkceChallenge(), "code_challenge_method", "S256");
    }

    private static String emailDomain(String email) {
        return email.substring(email.indexOf('@') + 1);
    }

    // ---- tests ----

    @Test
    @DisplayName("AC-001.63 edge 114 limits the SSO session to 8 hours and 60 minutes idle")
    void limitsSsoSessionTo8HoursAnd60MinutesIdle_AC_001_63_edge114() {
        RealmRepresentation realm = realm().toRepresentation();

        assertThat(realm.getSsoSessionMaxLifespan()).as("ssoSessionMaxLifespan").isBetween(1, EIGHT_HOURS);
        assertThat(realm.getSsoSessionIdleTimeout()).as("ssoSessionIdleTimeout").isBetween(1, SIXTY_MINUTES);
        if (Boolean.TRUE.equals(realm.isRememberMe())) {
            assertThat(realm.getSsoSessionMaxLifespanRememberMe())
                    .as("remember-me max lifespan")
                    .isBetween(1, EIGHT_HOURS);
            assertThat(realm.getSsoSessionIdleTimeoutRememberMe())
                    .as("remember-me idle timeout")
                    .isBetween(1, SIXTY_MINUTES);
        }
    }

    @Test
    @DisplayName("AC-001.64 allows no self-registration, no username or password form, hides every IdP and has "
            + "brute-force detection on")
    void allowsNoSelfRegistrationNoPasswordFormAndHidesEveryIdp_AC_001_64() {
        RealmRepresentation realm = realm().toRepresentation();
        assertThat(realm.isBruteForceProtected()).as("bruteForceProtected").isTrue();
        assertThat(realm.isRegistrationAllowed()).as("registrationAllowed").isFalse();
        assertThat(realm.isResetPasswordAllowed()).as("resetPasswordAllowed").isFalse();

        assertThat(identityProviders()).allSatisfy(idp -> assertThat(idp.isHideOnLogin())
                .as("hideOnLogin of " + idp.getAlias())
                .isTrue());

        Set<String> forbidden = Set.of("auth-username-password-form", "auth-username-form", "organization");
        List<String> flowAliases = new ArrayList<>();
        flowAliases.add(realm.getBrowserFlow());
        Map<String, String> overrides = webClient().toRepresentation().getAuthenticationFlowBindingOverrides();
        if (overrides != null && overrides.get("browser") != null) {
            flowAliases.add(realm().flows().getFlow(overrides.get("browser")).getAlias());
        }
        for (String alias : flowAliases) {
            assertThat(enabledProviders(alias))
                    .as("enabled executions of the browser flow " + alias)
                    .doesNotContainAnyElementsOf(forbidden);
        }
    }

    @Test
    @DisplayName("AC-001.64 edge 105 uses a first-broker-login flow that creates the user with no review and no link "
            + "by email, and links google-acme and google-beta to the Organizations acme and beta")
    void usesCreateOnlyFirstBrokerFlowAndLinksIdpsToOrganizations_AC_001_64_edge105() {
        assertThat(realm().toRepresentation().isOrganizationsEnabled())
                .as("organizationsEnabled")
                .isTrue();

        List<IdentityProviderRepresentation> idps = identityProviders();
        assertThat(idps)
                .extracting(IdentityProviderRepresentation::getAlias)
                .containsExactlyInAnyOrder("google-acme", "google-beta");
        assertThat(idps).allSatisfy(idp -> {
            assertThat(idp.getProviderId())
                    .as("providerId of " + idp.getAlias())
                    .isEqualTo("oidc");
            assertThat(idp.getFirstBrokerLoginFlowAlias())
                    .as("first broker login flow of " + idp.getAlias())
                    .isNotBlank()
                    .isNotEqualTo(BUILT_IN_FIRST_BROKER_FLOW);
        });
        Set<String> flowAliases = idps.stream()
                .map(IdentityProviderRepresentation::getFirstBrokerLoginFlowAlias)
                .collect(Collectors.toSet());
        for (String alias : flowAliases) {
            Set<String> enabled = enabledProviders(alias);
            assertThat(executions(alias))
                    .filteredOn(e -> "idp-create-user-if-unique".equals(e.getProviderId()))
                    .extracting(AuthenticationExecutionInfoRepresentation::getRequirement)
                    .as("idp-create-user-if-unique in " + alias)
                    .containsExactly("REQUIRED");
            assertThat(enabled).as("enabled executions of " + alias).contains("idp-add-organization-member");
            assertThat(enabled)
                    .as("enabled executions of " + alias)
                    .doesNotContain(
                            "idp-review-profile",
                            "idp-detect-existing-broker-user",
                            "idp-confirm-link",
                            "idp-auto-link",
                            "idp-email-verification",
                            "idp-username-password-form");
        }

        Map<String, String> orgIds = Map.of(
                "google-acme", DevAccounts.ACME_ORGANIZATION_ID,
                "google-beta", DevAccounts.BETA_ORGANIZATION_ID);
        idps.forEach(idp -> assertThat(idp.getOrganizationId())
                .as("organization of " + idp.getAlias())
                .isEqualTo(orgIds.get(idp.getAlias())));
        assertThat(organization(DevAccounts.ACME_SLUG).getId()).isEqualTo(DevAccounts.ACME_ORGANIZATION_ID);
        assertThat(organization(DevAccounts.BETA_SLUG).getId()).isEqualTo(DevAccounts.BETA_ORGANIZATION_ID);
        assertThat(organization(DevAccounts.ACME_SLUG).getDomains())
                .extracting(d -> d.getName())
                .containsExactly("acme.test");
        assertThat(organization(DevAccounts.BETA_SLUG).getDomains())
                .extracting(d -> d.getName())
                .containsExactly("beta.test");

        // Q1 A: the dev accounts are imported, linked to their IdP and members of their Organization
        List<UserRepresentation> users = realm().users().list().stream()
                .filter(u -> u.getServiceAccountClientId() == null)
                .toList();
        assertThat(users)
                .extracting(UserRepresentation::getEmail)
                .containsExactlyInAnyOrderElementsOf(DevAccounts.all().stream()
                        .map(DevAccounts.DevAccount::email)
                        .toList());
        for (DevAccounts.DevAccount account : DevAccounts.all()) {
            String slug =
                    account.workspaceId().equals(DevAccounts.ACME_ID) ? DevAccounts.ACME_SLUG : DevAccounts.BETA_SLUG;
            UserRepresentation user = users.stream()
                    .filter(u -> account.email().equals(u.getEmail()))
                    .findFirst()
                    .orElseThrow();
            assertThat(realm().users().get(user.getId()).getFederatedIdentity())
                    .extracting(f -> f.getIdentityProvider())
                    .as("federated identities of " + account.email())
                    .containsExactly("google-" + slug);
            assertThat(realm().organizations()
                            .get(organization(slug).getId())
                            .members()
                            .getAll())
                    .extracting(m -> m.getId())
                    .as("members of " + slug)
                    .contains(user.getId());
            assertThat(emailDomain(account.email())).isEqualTo(slug + ".test");
        }
    }

    static Stream<Arguments> hints() {
        return Stream.of(
                Arguments.of("no hint", Map.<String, String>of()),
                Arguments.of("unknown hint", Map.of("kc_idp_hint", "google-nope")));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("hints")
    @DisplayName("AC-001.64 edge 104 shows no identity provider and no form when kc_idp_hint is missing or unknown")
    void showsNoIdpAndNoFormWithoutAKnownHint_AC_001_64_edge104(String label, Map<String, String> hint) {
        Map<String, String> params = new java.util.LinkedHashMap<>(s256());
        params.putAll(hint);

        HttpResponse<String> response = authorize(acmeRedirectUri(), params);

        assertThat(response.headers().firstValue("Location"))
                .as("no redirect to a default identity provider")
                .isEmpty();
        assertThat(response.statusCode() / 100).as("status is not 3xx").isNotEqualTo(3);

        String html = response.body().toLowerCase();
        assertThat(html).doesNotContainPattern("<input[^>]*type=\"password\"");
        assertThat(html).doesNotContainPattern("<input[^>]*name=\"username\"");
        assertThat(html).doesNotContain("/broker/");
        assertThat(html).doesNotContain("registration");
    }

    @Test
    @DisplayName("AC-001.65 makes tsolve-web confidential and requires PKCE S256")
    void makesTsolveWebConfidentialAndRequiresPkceS256_AC_001_65() throws IOException {
        ClientResource client = webClient();
        ClientRepresentation rep = client.toRepresentation();

        assertThat(rep.isPublicClient()).as("publicClient").isFalse();
        assertThat(rep.getClientAuthenticatorType()).isEqualTo("client-secret");
        assertThat(rep.getAttributes()).containsEntry("pkce.code.challenge.method", "S256");
        assertThat(rep.isStandardFlowEnabled()).as("standardFlowEnabled").isTrue();
        assertThat(rep.isImplicitFlowEnabled()).as("implicitFlowEnabled").isFalse();
        assertThat(rep.isDirectAccessGrantsEnabled())
                .as("directAccessGrantsEnabled")
                .isFalse();

        // Q6 A: the committed file holds the placeholder, the running client the value given to the container
        String file = Files.readString(REALM_FILE);
        assertThat(file).contains("${KEYCLOAK_WEB_CLIENT_SECRET}").doesNotContain(WEB_CLIENT_SECRET);
        assertThat(client.getSecret().getValue()).as("running client secret").isEqualTo(WEB_CLIENT_SECRET);

        String redirectUri = acmeRedirectUri();
        for (Map<String, String> pkce : List.of(
                Map.<String, String>of(),
                Map.of("code_challenge", pkceChallenge(), "code_challenge_method", "plain"))) {
            HttpResponse<String> response = authorize(redirectUri, pkce);
            String location = response.headers().firstValue("Location").orElse("");
            String context = "request with PKCE " + pkce + " is refused, got " + response.statusCode() + " " + location;
            assertThat(response.statusCode()).as(context).isEqualTo(302);
            assertThat(location).as(context).startsWith(redirectUri);
            List<String> queryParams =
                    List.of(java.net.URI.create(location).getRawQuery().split("&"));
            assertThat(queryParams).as(context).contains("error=invalid_request");
            assertThat(queryParams).as(context).noneMatch(p -> p.startsWith("code="));
            assertThat(response.body().toLowerCase()).doesNotContain("kc-form-login");
        }
    }

    @Test
    @DisplayName("AC-001.65 edge 103 maps the organization claim and the hd attribute into the ID token")
    void mapsOrganizationClaimAndHdAttributeIntoIdToken_AC_001_65_edge103() {
        // Q5 A: hd is declared (Keycloak 26 drops undeclared attributes) and only an admin edits it
        UPAttribute hd = realm().users().userProfile().getConfiguration().getAttribute("hd");
        assertThat(hd).as("the user profile declares hd").isNotNull();
        assertThat(hd.getPermissions().getEdit()).containsExactly("admin");
        assertThat(hd.getPermissions().getView()).contains("admin");
        // the email match rests on email_verified, so only an admin edits email (view may stay user and admin)
        UPAttribute email = realm().users().userProfile().getConfiguration().getAttribute("email");
        assertThat(email).as("the user profile declares email").isNotNull();
        assertThat(email.getPermissions().getEdit()).as("edit permission of email").containsExactly("admin");

        for (IdentityProviderRepresentation idp : identityProviders()) {
            List<IdentityProviderMapperRepresentation> mappers =
                    realm().identityProviders().get(idp.getAlias()).getMappers();
            assertThat(mappers)
                    .as("mapper from claim hd to attribute hd on " + idp.getAlias())
                    .anySatisfy(m -> {
                        assertThat(m.getIdentityProviderMapper()).isEqualTo("oidc-user-attribute-idp-mapper");
                        assertThat(m.getConfig()).containsEntry("claim", "hd").containsEntry("user.attribute", "hd");
                    });
        }

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserRepresentation carol = new UserRepresentation();
        carol.setUsername("carol-" + suffix + "@acme.test");
        carol.setEmail("carol-" + suffix + "@acme.test");
        carol.setEnabled(true);
        carol.singleAttribute("hd", "acme.test");
        String userId = null;
        try (jakarta.ws.rs.core.Response created = realm().users().create(carol)) {
            userId = created.getLocation().getPath().replaceAll(".*/", "");
            realm().organizations()
                    .get(DevAccounts.ACME_ORGANIZATION_ID)
                    .members()
                    .addMember(userId)
                    .close();

            IDToken token = webClient().clientScopesEvaluate().generateExampleIdToken("openid", userId, null);

            assertThat(token.getSubject()).as("sub claim (AC-001.12)").isNotBlank();
            assertThat(token.getOtherClaims()).containsEntry("hd", "acme.test");
            assertThat(token.getOtherClaims().get("organization"))
                    .as("organization claim")
                    .isEqualTo(Map.of(DevAccounts.ACME_SLUG, Map.of("id", DevAccounts.ACME_ORGANIZATION_ID)));
        } finally {
            if (userId != null) {
                realm().users().delete(userId).close();
            }
        }
    }

    @Test
    @DisplayName("AC-001.54 accepts only the exact callback URIs of acme and beta")
    void acceptsOnlyTheExactCallbackUrisOfAcmeAndBeta_AC_001_54() {
        ClientRepresentation rep = webClient().toRepresentation();
        List<String> uris = rep.getRedirectUris();

        assertThat(uris).as("redirect URIs").hasSize(2).allMatch(u -> CALLBACK_URI
                .matcher(u)
                .matches());
        assertThat(uris.stream().anyMatch(u -> u.contains("//acme.localhost"))).isTrue();
        assertThat(uris.stream().anyMatch(u -> u.contains("//beta.localhost"))).isTrue();
        assertThat(uris).noneMatch(u -> u.contains("*"));
        assertThat(rep.getWebOrigins() == null ? List.<String>of() : rep.getWebOrigins())
                .noneMatch(o -> o.contains("*"));

        HttpResponse<String> accepted = authorize(acmeRedirectUri(), s256());
        assertThat(accepted.body()).as("positive control").doesNotContain("Invalid parameter: redirect_uri");
    }

    static Stream<Arguments> refusedRedirectUris() {
        return Stream.of(
                Arguments.of("acme URI + /x", (UnaryOperator<String>) u -> u + "/x"),
                Arguments.of("acme URI + ?x=1", (UnaryOperator<String>) u -> u + "?x=1"),
                Arguments.of("acme URI with https", (UnaryOperator<String>) u -> u.replaceFirst("^http:", "https:")),
                Arguments.of(
                        "gamma host", (UnaryOperator<String>) u -> u.replace("//acme.localhost", "//gamma.localhost")),
                Arguments.of("evil host", (UnaryOperator<String>) u -> "http://evil.example/api/v1/auth/callback"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("refusedRedirectUris")
    @DisplayName("AC-001.54 refuses a redirect URI that is not an exact callback URI")
    void refusesRedirectUriThatIsNotAnExactCallbackUri_AC_001_54(String label, UnaryOperator<String> mutate) {
        HttpResponse<String> response = authorize(mutate.apply(acmeRedirectUri()), s256());

        assertThat(response.statusCode()).as("status for " + label).isGreaterThanOrEqualTo(400);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body()).contains("redirect_uri");
    }
}
