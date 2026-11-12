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
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
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
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

/**
 * Task 29a: imports the committed {@code infra/keycloak/realm-tsolve-ops.json} into a real Keycloak and checks what
 * AC-001.66 asks of the realm {@code tsolve-ops}. The container gets the same file compose mounts. The fake Google
 * runs next to it (the mappings directory compose mounts), so a brokered sign-in can be tried. On Docker 29 run with
 * {@code -DargLine=-Dapi.version=1.44} (never committed).
 *
 * <p>The dev value of the Console redirect URI is the literal dev admin origin + {@code /api/v1/auth/callback}
 * (29-QA Q4 = A).
 */
class OpsRealmImportIT {

    private static final String REALM = "tsolve-ops";
    private static final String CLIENT_ID = "tsolve-console";
    private static final String CONSOLE_CLIENT_SECRET = "it-keycloak-console-client-secret";
    private static final String IDP_ALIAS = "google-operators";
    private static final String EXPECTED_REDIRECT_URI = "http://admin.localhost:8080/api/v1/auth/callback";
    private static final Path REALM_FILE =
            Path.of("../infra/keycloak/realm-tsolve-ops.json").toAbsolutePath().normalize();
    private static final Path FAKE_GOOGLE_DIR =
            Path.of("../infra/keycloak/fake-google").toAbsolutePath().normalize();
    private static final int EIGHT_HOURS = 8 * 60 * 60;
    private static final int SIXTY_MINUTES = 60 * 60;
    private static final String BROKER_LOGIN_PATH = "/realms/" + REALM + "/broker/" + IDP_ALIAS + "/login";
    private static final String REDIRECTOR = "identity-provider-redirector";

    private static GenericContainer<?> fakeGoogle;
    private static KeycloakContainer keycloak;
    private static HttpClient http;

    @BeforeAll
    @SuppressWarnings("resource") // stopped in stopKeycloak
    static void startKeycloak() {
        assertThat(REALM_FILE).as("the committed realm file of tsolve-ops").exists();
        Network network = Network.newNetwork();
        fakeGoogle = new GenericContainer<>(new ImageFromDockerfile().withFileFromPath(".", FAKE_GOOGLE_DIR))
                .withNetwork(network)
                .withNetworkAliases("fake-google")
                .withExposedPorts(8080)
                .withCommand("--global-response-templating", "--extensions", "vn.tsolve.fakegoogle.IdTokenExtension")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(FAKE_GOOGLE_DIR.resolve("mappings")), "/home/wiremock/mappings")
                .waitingFor(Wait.forHttp("/__admin/mappings").forPort(8080));
        fakeGoogle.start();
        keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4.7")
                .withNetwork(network)
                .withEnv("KEYCLOAK_CONSOLE_CLIENT_SECRET", CONSOLE_CLIENT_SECRET)
                .withCopyFileToContainer(
                        // the container adds --import-realm itself, as it does for withRealmImportFile
                        MountableFile.forHostPath(REALM_FILE), "/opt/keycloak/data/import/realm-tsolve-ops.json");
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
        if (fakeGoogle != null) {
            fakeGoogle.stop();
        }
    }

    /** A test that finishes a sign-in leaves a user with a TOTP; the next test must meet a new user again. */
    @org.junit.jupiter.api.AfterEach
    void deleteTheBrokeredUsers() {
        for (org.keycloak.representations.idm.UserRepresentation user :
                realm().users().list()) {
            realm().users().delete(user.getId()).close();
        }
    }

    // ---- helpers

    private static RealmResource realm() {
        return keycloak.getKeycloakAdminClient().realm(REALM);
    }

    private static ClientResource consoleClient() {
        List<ClientRepresentation> found = realm().clients().findByClientId(CLIENT_ID);
        assertThat(found).as("the realm has the client " + CLIENT_ID).hasSize(1);
        return realm().clients().get(found.get(0).getId());
    }

    private static List<IdentityProviderRepresentation> identityProviders() {
        return realm().identityProviders().findAll();
    }

    private static IdentityProviderRepresentation operatorsIdp() {
        return identityProviders().stream()
                .filter(i -> IDP_ALIAS.equals(i.getAlias()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the realm has no identity provider " + IDP_ALIAS));
    }

    private static List<AuthenticationExecutionInfoRepresentation> executions(String flowAlias) {
        return realm().flows().getExecutions(flowAlias);
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

    private static Map<String, String> s256() {
        return Map.of("code_challenge", pkceChallenge(), "code_challenge_method", "S256");
    }

    private static String authorizeUrl(String clientId, String redirectUri, Map<String, String> extra) {
        List<String> query = new ArrayList<>();
        query.add("client_id=" + clientId);
        query.add("redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        query.add("response_type=code");
        query.add("scope=openid");
        query.add("state=s1");
        extra.forEach((k, v) -> query.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return keycloak.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/auth?"
                + String.join("&", query);
    }

    private static HttpResponse<String> authorize(String clientId, String redirectUri, Map<String, String> extra) {
        try {
            return http.send(
                    HttpRequest.newBuilder(URI.create(authorizeUrl(clientId, redirectUri, extra)))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * The Console start (client tsolve-console, the dev redirect URI, PKCE S256, no hint) is a 302 to the broker login
     * of google-operators, not a login page: the browser flow is the identity-provider redirector with a default
     * provider.
     */
    private static void assertRedirectsToTheBrokerLogin(String status, String location) {
        // Keycloak 26.4.7 answers the identity-provider redirector with 303 See Other; 302 is accepted too
        assertThat(status)
                .as("Console start status (302 or 303 to the broker login, no login page)")
                .isIn("302", "303");
        assertThat(location)
                .as("Console start Location (broker login of " + IDP_ALIAS + ")")
                .contains(BROKER_LOGIN_PATH);
        assertThat(location).as("no kc_idp_hint is needed").doesNotContain("kc_idp_hint");
    }

    private static TestBrowser.Reply consoleStart(TestBrowser browser) {
        consoleClient(); // proves the client exists, so a missing client's error page never passes for a redirect
        TestBrowser.Reply reply = browser.get(authorizeUrl(CLIENT_ID, EXPECTED_REDIRECT_URI, s256()));
        assertRedirectsToTheBrokerLogin(String.valueOf(reply.status()), String.valueOf(reply.location()));
        return reply;
    }

    private static TestBrowser newBrowser() {
        return new TestBrowser(Map.of("fake-google.localhost:8181", "localhost:" + fakeGoogle.getMappedPort(8080)));
    }

    /** The only enabled step of the browser flow that can send the user to an identity provider is the redirector. */
    private static void assertRedirectorIsTheOnlyIdpStep(String browserFlow) {
        Set<String> idpCapable =
                Set.of(REDIRECTOR, "auth-username-form", "auth-username-password-form", "organization");
        List<AuthenticationExecutionInfoRepresentation> enabled = executions(browserFlow).stream()
                .filter(e -> !"DISABLED".equals(e.getRequirement()))
                .toList();
        assertThat(enabled)
                .filteredOn(e -> idpCapable.contains(e.getProviderId()))
                .extracting(AuthenticationExecutionInfoRepresentation::getProviderId)
                .as("enabled IdP-capable steps of the browser flow")
                .containsExactly(REDIRECTOR);
        String configId = enabled.stream()
                .filter(e -> REDIRECTOR.equals(e.getProviderId()))
                .findFirst()
                .orElseThrow()
                .getAuthenticationConfig();
        assertThat(configId)
                .as("config of the identity-provider redirector exists")
                .isNotBlank();
        assertThat(realm().flows().getAuthenticatorConfig(configId).getConfig())
                .as("config of the identity-provider redirector: defaultProvider")
                .containsEntry("defaultProvider", IDP_ALIAS);
    }

    // ---- tests

    @Test
    @DisplayName("AC-001.66 edge 136 has one Google identity provider without hd and no tenant identity provider")
    void hasOneGoogleIdpWithoutHdAndNoTenantIdp_AC_001_66_edge136() {
        List<IdentityProviderRepresentation> idps = identityProviders();
        assertThat(idps).extracting(IdentityProviderRepresentation::getAlias).containsExactly(IDP_ALIAS);

        IdentityProviderRepresentation idp = operatorsIdp();
        Map<String, String> config = idp.getConfig();
        assertThat(config).doesNotContainKey("hostedDomain").doesNotContainKey("hd");
        assertThat(config.get("authorizationUrl")).as("authorizationUrl").isNotBlank();
        assertThat(URI.create(config.get("authorizationUrl")).getRawQuery())
                .as("query of the authorization URL")
                .satisfiesAnyOf(query -> assertThat(query).isNull(), query -> assertThat(query)
                        .doesNotContain("hd="));
        assertThat(idp.isHideOnLogin())
                .as("hideOnLogin: the Google button is shown")
                .isFalse();

        List<IdentityProviderMapperRepresentation> mappers =
                realm().identityProviders().get(IDP_ALIAS).getMappers();
        assertThat(mappers).noneSatisfy(m -> assertThat(m.getConfig()).containsEntry("claim", "hd"));
        RealmRepresentation realm = realm().toRepresentation();
        if (Boolean.TRUE.equals(realm.isOrganizationsEnabled())) {
            assertThat(realm().organizations().getAll()).as("Organizations").isEmpty();
        }

        // the Console start leads straight to the one identity provider, the Google one
        assertRedirectorIsTheOnlyIdpStep(realm.getBrowserFlow());
        consoleStart(newBrowser());
    }

    @Test
    @DisplayName("AC-001.66 allows no self-registration and no username or password form, has brute-force detection on"
            + " and limits the SSO session to 8 hours and 60 minutes idle")
    void allowsNoSelfRegistrationNoPasswordFormAndLimitsTheSession_AC_001_66() {
        RealmRepresentation realm = realm().toRepresentation();
        assertThat(realm.isRegistrationAllowed()).as("registrationAllowed").isFalse();
        assertThat(realm.isResetPasswordAllowed()).as("resetPasswordAllowed").isFalse();
        assertThat(realm.isBruteForceProtected()).as("bruteForceProtected").isTrue();
        assertThat(realm.getSsoSessionMaxLifespan()).as("ssoSessionMaxLifespan").isBetween(1, EIGHT_HOURS);
        assertThat(realm.getSsoSessionIdleTimeout()).as("ssoSessionIdleTimeout").isBetween(1, SIXTY_MINUTES);
        if (Boolean.TRUE.equals(realm.isRememberMe())) {
            assertThat(realm.getSsoSessionMaxLifespanRememberMe()).isBetween(1, EIGHT_HOURS);
            assertThat(realm.getSsoSessionIdleTimeoutRememberMe()).isBetween(1, SIXTY_MINUTES);
        }

        Set<String> forbidden = Set.of("auth-username-password-form", "auth-username-form");
        Set<String> enabled = executions(realm.getBrowserFlow()).stream()
                .filter(e -> !"DISABLED".equals(e.getRequirement()))
                .map(AuthenticationExecutionInfoRepresentation::getProviderId)
                .collect(Collectors.toSet());
        assertThat(enabled).as("enabled executions of the browser flow").doesNotContainAnyElementsOf(forbidden);

        assertRedirectorIsTheOnlyIdpStep(realm.getBrowserFlow());
        // no login page at all: the start is a 302, and the broker login is a 302 to the fake Google
        TestBrowser browser = newBrowser();
        TestBrowser.Reply start = consoleStart(browser);
        assertThat(start.body().toLowerCase())
                .doesNotContainPattern("<input[^>]*type=\"password\"")
                .doesNotContainPattern("<input[^>]*name=\"username\"")
                .doesNotContain("registration");
        TestBrowser.Reply broker = browser.get(start.location());
        assertThat(broker.status()).as("broker login status (302 or 303)").isIn(302, 303);
        assertThat(broker.location()).as("broker login Location").startsWith("http://fake-google.localhost:8181/");
    }

    @Test
    @DisplayName("AC-001.66 edge 137 requires a second factor for every user")
    void requiresASecondFactorForEveryUser_AC_001_66_edge137() {
        // static: the flow google-operators runs after each broker login has the OTP Form step as REQUIRED
        String flowAlias = operatorsIdp().getPostBrokerLoginFlowAlias();
        assertThat(flowAlias).as("post broker login flow of " + IDP_ALIAS).isNotBlank();
        assertThat(executions(flowAlias))
                .filteredOn(e -> "auth-otp-form".equals(e.getProviderId()))
                .extracting(AuthenticationExecutionInfoRepresentation::getRequirement)
                .as("auth-otp-form in " + flowAlias)
                .containsExactly("REQUIRED");
        assertThat(realm().flows().getRequiredActions())
                .filteredOn(a -> "CONFIGURE_TOTP".equals(a.getAlias()))
                .extracting(RequiredActionProviderRepresentation::isEnabled)
                .containsExactly(true);

        // dynamic: a brokered sign-in of ops@example.test through the fake Google ends on the TOTP setup page, and
        // no request reaches the callback URI (no code is issued)
        TestBrowser browser = newBrowser();
        TestBrowser.Reply start = consoleStart(browser);
        TestBrowser.Reply settled =
                browser.followUntilSettled(browser.get(start.location()), EXPECTED_REDIRECT_URI, true);

        assertThat(browser.lastUrl()).as("the last page is not the callback").doesNotStartWith(EXPECTED_REDIRECT_URI);
        assertThat(settled.location())
                .as("no redirect to the callback with a code")
                .isNull();
        assertThat(settled.status()).as("status of the last page").isEqualTo(200);
        assertThat(settled.body().toLowerCase()).as("TOTP setup page").containsPattern("<input[^>]*name=\"totp\"");
    }

    @Test
    @DisplayName("AC-001.66 review I1 lets only admin edit the email of a tsolve-ops user")
    void letsOnlyAdminEditTheEmail_AC_001_66_reviewI1() throws IOException {
        Object configuration = realm().users().userProfile().getConfiguration();
        String json = configuration instanceof String text
                ? text
                : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(configuration);
        com.fasterxml.jackson.databind.JsonNode attributes =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path("attributes");
        com.fasterxml.jackson.databind.JsonNode email = null;
        for (com.fasterxml.jackson.databind.JsonNode attribute : attributes) {
            if ("email".equals(attribute.path("name").asText())) {
                email = attribute;
            }
        }
        assertThat(email)
                .as("the user profile of " + REALM + " has an email attribute")
                .isNotNull();
        List<String> edit = new ArrayList<>();
        email.path("permissions").path("edit").forEach(n -> edit.add(n.asText()));
        assertThat(edit).as("who may edit the email").containsExactly("admin");
    }

    @Test
    @DisplayName("AC-001.66 review I2 has no auth-cookie step in the browser flow")
    void hasNoAuthCookieStepInTheBrowserFlow_AC_001_66_reviewI2() {
        assertThat(executions(realm().toRepresentation().getBrowserFlow()))
                .extracting(AuthenticationExecutionInfoRepresentation::getProviderId)
                .as("steps of the browser flow")
                .doesNotContain("auth-cookie");
    }

    @Test
    @DisplayName("AC-001.66 review I2 sends a second Console start with a live SSO cookie to Google again")
    void sendsASecondStartWithALiveSsoCookieToGoogleAgain_AC_001_66_reviewI2() throws Exception {
        TestBrowser browser = newBrowser();
        TestBrowser.Reply start = consoleStart(browser);
        TestBrowser.Reply setup =
                browser.followUntilSettled(browser.get(start.location()), EXPECTED_REDIRECT_URI, true);
        assertThat(setup.status())
                .as("first sign-in ends on the TOTP setup page")
                .isEqualTo(200);
        java.util.regex.Matcher action =
                java.util.regex.Pattern.compile("<form[^>]*action=\"([^\"]+)\"").matcher(setup.body());
        assertThat(action.find()).as("a form on the TOTP setup page").isTrue();
        String secret = java.util.regex.Pattern.compile(
                        "name=\"totpSecret\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"totpSecret\"")
                .matcher(setup.body())
                .results()
                .map(m -> m.group(1) != null ? m.group(1) : m.group(2))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no totpSecret on the TOTP setup page"));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8)
                .putLong(System.currentTimeMillis() / 1000 / 30)
                .array());
        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        String code = String.format("%06d", binary % 1_000_000);
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("totp", code);
        form.put("totpSecret", secret);
        form.put("userLabel", "it");
        TestBrowser.Reply done = browser.post(action.group(1).replace("&amp;", "&"), form);
        TestBrowser.Reply first = browser.followUntilSettled(done, EXPECTED_REDIRECT_URI, true);
        assertThat(first.location())
                .as("the first sign-in is complete: a code goes to the callback")
                .startsWith(EXPECTED_REDIRECT_URI);
        assertThat(first.location()).contains("code=");

        // a second start with the same cookie jar must go to the broker login, not return a code
        TestBrowser.Reply second = browser.get(authorizeUrl(CLIENT_ID, EXPECTED_REDIRECT_URI, s256()));
        assertThat(String.valueOf(second.location()))
                .as("second Console start Location (broker login, no code)")
                .doesNotStartWith(EXPECTED_REDIRECT_URI)
                .contains(BROKER_LOGIN_PATH);
        assertThat(second.status()).as("second Console start status").isIn(302, 303);
    }

    @Test
    @DisplayName("AC-001.66 makes tsolve-console confidential and requires PKCE S256")
    void makesTsolveConsoleConfidentialAndRequiresPkceS256_AC_001_66() {
        ClientRepresentation rep = consoleClient().toRepresentation();

        assertThat(rep.isPublicClient()).as("publicClient").isFalse();
        assertThat(rep.getClientAuthenticatorType()).isEqualTo("client-secret");
        assertThat(rep.getAttributes()).containsEntry("pkce.code.challenge.method", "S256");
        assertThat(rep.isStandardFlowEnabled()).as("standardFlowEnabled").isTrue();
        assertThat(rep.isImplicitFlowEnabled()).as("implicitFlowEnabled").isFalse();
        assertThat(rep.isDirectAccessGrantsEnabled())
                .as("directAccessGrantsEnabled")
                .isFalse();

        for (Map<String, String> pkce : List.of(
                Map.<String, String>of(),
                Map.of("code_challenge", pkceChallenge(), "code_challenge_method", "plain"))) {
            HttpResponse<String> response = authorize(CLIENT_ID, EXPECTED_REDIRECT_URI, pkce);
            String location = response.headers().firstValue("Location").orElse("");
            String context = "request with PKCE " + pkce + " is refused, got " + response.statusCode() + " " + location;
            assertThat(response.statusCode()).as(context).isEqualTo(302);
            assertThat(location).as(context).startsWith(EXPECTED_REDIRECT_URI);
            List<String> queryParams =
                    List.of(URI.create(location).getRawQuery().split("&"));
            assertThat(queryParams).as(context).contains("error=invalid_request");
            assertThat(queryParams).as(context).noneMatch(p -> p.startsWith("code="));
        }

        // the realms share no client: the tsolve-web client does not exist in tsolve-ops
        HttpResponse<String> other = authorize("tsolve-web", EXPECTED_REDIRECT_URI, s256());
        assertThat(other.statusCode()).as("status for tsolve-web").isGreaterThanOrEqualTo(400);
        assertThat(other.body().toLowerCase()).contains("client not found");
    }

    static Stream<Arguments> redirectUris() {
        return Stream.of(
                Arguments.of("the exact URI", true, (UnaryOperator<String>) u -> u),
                Arguments.of("trailing slash", false, (UnaryOperator<String>) u -> u + "/"),
                Arguments.of("extra path segment", false, (UnaryOperator<String>) u -> u + "/x"),
                Arguments.of("another port", false, (UnaryOperator<String>) u -> u.replace(":8080", ":8090")),
                Arguments.of("https instead of http", false, (UnaryOperator<String>)
                        u -> u.replaceFirst("^http:", "https:")),
                Arguments.of("the acme callback", false, (UnaryOperator<String>)
                        u -> "http://acme.localhost:8080/api/v1/auth/callback"),
                Arguments.of("a query string", false, (UnaryOperator<String>) u -> u + "?x=1"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("redirectUris")
    @DisplayName("AC-001.66 accepts only the exact redirect URI of TSOLVE_ADMIN_ORIGIN")
    void acceptsOnlyTheExactRedirectUriOfTheAdminOrigin_AC_001_66(
            String label, boolean accepted, UnaryOperator<String> mutate) {
        ClientRepresentation rep = consoleClient().toRepresentation();
        assertThat(rep.getRedirectUris()).as("redirect URIs").containsExactly(EXPECTED_REDIRECT_URI);
        assertThat(rep.getWebOrigins() == null ? List.<String>of() : rep.getWebOrigins())
                .noneMatch(o -> o.contains("*"));

        HttpResponse<String> response = authorize(CLIENT_ID, mutate.apply(EXPECTED_REDIRECT_URI), s256());

        if (accepted) {
            assertRedirectsToTheBrokerLogin(
                    String.valueOf(response.statusCode()),
                    response.headers().firstValue("Location").orElse("no Location, status " + response.statusCode()));
            assertThat(response.body()).doesNotContain("Invalid parameter: redirect_uri");
        } else {
            assertThat(response.statusCode()).as("status for " + label).isGreaterThanOrEqualTo(400);
            assertThat(response.headers().firstValue("Location")).isEmpty();
            assertThat(response.body()).contains("Invalid parameter: redirect_uri");
        }
    }
}
