package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import vn.tsolve.TSolveApplication;
import vn.tsolve.identity.dev.DevAccounts;

/**
 * Task 23c: the whole sign-in against a real Keycloak 26.4 with the committed realm, the fake Google, a real Redis and
 * Postgres, and the application in profile {@code dev}. A headless {@link TestBrowser} drives it with no automatic
 * redirects; it sends the public host and port as {@code X-Forwarded-*}, so the {@code redirect_uri} is the one the
 * realm registers (port 8080) while the backend listens on a random port. Chrome itself (the Secure {@code __Host-}
 * cookie on plain http, the chooser page) stays a manual check. On Docker 29 run with
 * {@code -DargLine=-Dapi.version=1.44} (never committed).
 */
class KeycloakSignInEndToEndIT {

    private static final String WEB_CLIENT_SECRET = "it-keycloak-web-client-secret";
    private static final Path REALM_FILE =
            Path.of("../infra/keycloak/realm-tsolve.json").toAbsolutePath().normalize();
    private static final Path FAKE_GOOGLE_DIR =
            Path.of("../infra/keycloak/fake-google").toAbsolutePath().normalize();
    private static final String REALM = "tsolve";
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = UUID.randomUUID().toString();
    private static final String COOKIE = RedisAuthorizationRequestRepository.COOKIE_NAME;
    private static final String MSG141 = "/login?error=MSG141";
    private static final String MSG06 = "/login?error=MSG06";

    private static final Network NETWORK = Network.newNetwork();
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .withExposedPorts(REDIS_PORT);
    private static final GenericContainer<?> FAKE_GOOGLE = new GenericContainer<>(
                    new ImageFromDockerfile().withFileFromPath(".", FAKE_GOOGLE_DIR))
            .withNetwork(NETWORK)
            .withNetworkAliases("fake-google")
            .withExposedPorts(8080)
            .withCommand("--global-response-templating", "--extensions", "vn.tsolve.fakegoogle.IdTokenExtension")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(FAKE_GOOGLE_DIR.resolve("mappings")), "/home/wiremock/mappings")
            .waitingFor(Wait.forHttp("/__admin/mappings").forPort(8080));
    private static KeycloakContainer keycloak;

    private static ConfigurableApplicationContext backend;
    private static int backendPort;
    private static StringRedisTemplate redis;
    private static String loginOrigin;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void startStack() {
        DB.start();
        REDIS.start();
        FAKE_GOOGLE.start();
        keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4.7")
                .withNetwork(NETWORK)
                .withEnv("KEYCLOAK_WEB_CLIENT_SECRET", WEB_CLIENT_SECRET)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(REALM_FILE), "/opt/keycloak/data/import/realm-tsolve.json");
        if (java.nio.file.Files.isDirectory(KeycloakItStack.THEME_DIR)) { // Task 31: the realm sets loginTheme
            keycloak.withCopyFileToContainer(
                    MountableFile.forHostPath(KeycloakItStack.THEME_DIR), KeycloakItStack.THEME_MOUNT);
        }
        keycloak.start();
        loginOrigin = trimSlash(keycloak.getAuthServerUrl());
        backend = new SpringApplicationBuilder(TSolveApplication.class)
                .profiles("dev")
                .run(
                        "--SERVER_PORT=0",
                        "--TSOLVE_LOGIN_ORIGIN=" + loginOrigin,
                        "--TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8080",
                        "--TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
                        "--GOOGLE_CLIENT_ID=test-client-id",
                        "--GOOGLE_CLIENT_SECRET=test-secret",
                        "--PLATFORM_OPERATOR_EMAILS=ops@example.test",
                        "--KEYCLOAK_WEB_CLIENT_SECRET=" + WEB_CLIENT_SECRET,
                        "--CORS_ALLOWED_ORIGINS=http://localhost:5173",
                        "--DB_HOST=" + DB.getHost(),
                        "--DB_PORT=" + DB.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        "--DB_NAME=" + DB.getDatabaseName(),
                        "--DB_USER=" + DB.getUsername(),
                        "--DB_PASSWORD=" + DB.getPassword(),
                        "--REDIS_HOST=" + REDIS.getHost(),
                        "--REDIS_PORT=" + REDIS.getMappedPort(REDIS_PORT),
                        "--REDIS_PASSWORD=" + REDIS_PASSWORD,
                        "--spring.jmx.enabled=false");
        backendPort =
                ((ServletWebServerApplicationContext) backend).getWebServer().getPort();
        redis = backend.getBean(StringRedisTemplate.class);
        // Task 25b: the callback checks the Account; DevAccountSeeder only comes in Task 26b
        SignInTestFixtures.seedDevAccounts(db());
    }

    private static JdbcTemplate db() {
        return new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
    }

    @AfterAll
    static void stopStack() {
        if (backend != null) {
            backend.close();
        }
        if (keycloak != null) {
            keycloak.stop();
        }
        FAKE_GOOGLE.stop();
        REDIS.stop();
        DB.stop();
    }

    // ---- helpers

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static TestBrowser newBrowser() {
        return new TestBrowser(Map.of("fake-google.localhost:8181", "localhost:" + FAKE_GOOGLE.getMappedPort(8080)));
    }

    private static RealmResource realm() {
        return keycloak.getKeycloakAdminClient().realm(REALM);
    }

    private static String publicHost(String slug) {
        return slug + ".localhost";
    }

    private static String callbackBase(String slug) {
        return "http://" + publicHost(slug) + ":8080/api/v1/auth/callback";
    }

    private static TestBrowser.Reply start(TestBrowser browser, String slug) {
        TestBrowser.Reply reply =
                browser.backend("http://" + publicHost(slug) + ":8080/api/v1/auth/sign-in", backendPort);
        assertThat(reply.status()).as("start status").isEqualTo(302);
        return reply;
    }

    /** Runs the sign-in up to Keycloak's 302 to the callback. */
    private static TestBrowser.Reply toCallback(TestBrowser browser, String slug, TestBrowser.Reply start) {
        TestBrowser.Reply reply = browser.followUntilCallback(start, callbackBase(slug));
        assertThat(reply.location()).as("callback Location").startsWith(callbackBase(slug) + "?");
        return reply;
    }

    private static TestBrowser.Reply openCallback(TestBrowser browser, TestBrowser.Reply keycloakAnswer) {
        return browser.backend(keycloakAnswer.location(), backendPort);
    }

    private static void assertExpiryCookie(TestBrowser.Reply reply) {
        List<String> headers = reply.setCookies(COOKIE);
        assertThat(headers).as("Set-Cookie of %s", COOKIE).hasSize(1);
        assertThat(headers.get(0)).startsWith(COOKIE + "=;");
        Map<String, String> attributes = SignInTestFixtures.attributes(headers.get(0));
        assertThat(attributes).containsEntry("max-age", "0").containsEntry("path", "/");
        assertThat(attributes).containsKeys("secure", "httponly");
        assertThat(attributes.get("samesite")).isEqualToIgnoringCase("Lax");
        assertThat(attributes).doesNotContainKey("domain");
    }

    private static Set<String> authRequestKeys() {
        Set<String> keys = redis.keys(RedisAuthorizationRequestRepository.KEY_PREFIX + "*");
        return keys == null ? Set.of() : keys;
    }

    private static String keycloakUserId(String email) {
        List<UserRepresentation> users = realm().users().searchByEmail(email, true);
        assertThat(users).as("Keycloak users with email %s", email).hasSize(1);
        return users.get(0).getId();
    }

    // ---- tests

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
        "acme, alice@acme.test, 00000000-0000-4000-8000-0000000000c1",
        "beta, bob@beta.test, 00000000-0000-4000-8000-0000000000c2"
    })
    @DisplayName(
            "AC-001.53 AC-001.65 edge 122 signs in through Keycloak and the fake Google and shows the verified claims")
    void signsInThroughKeycloakAndShowsTheVerifiedClaims_AC_001_53_AC_001_65_edge122(
            String slug, String email, String organizationId) throws Exception {
        TestBrowser browser = newBrowser();

        TestBrowser.Reply start = start(browser, slug);
        assertThat(start.location()).contains("kc_idp_hint=google-" + slug).contains("code_challenge_method=S256");
        TestBrowser.Reply keycloakAnswer = toCallback(browser, slug, start);
        TestBrowser.Reply reply = openCallback(browser, keycloakAnswer);

        assertThat(keycloakAnswer
                        .location()
                        .substring(0, keycloakAnswer.location().indexOf('?')))
                .isEqualTo(callbackBase(slug));
        assertThat(reply.status()).as("callback status").isEqualTo(200);
        assertThat(reply.location()).as("Location").isNull();
        JsonNode claims = json.readTree(reply.body());
        Set<String> names = new HashSet<>();
        claims.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("sub", "email", "organization", "hd");
        assertThat(claims.path("sub").asText()).isEqualTo(keycloakUserId(email));
        assertThat(claims.path("email").asText()).isEqualTo(email);
        assertThat(claims.path("organization").path(slug).path("id").asText()).isEqualTo(organizationId);
        assertThat(claims.path("hd").asText()).isEqualTo(slug + ".test");
        assertExpiryCookie(reply);
        assertThat(authRequestKeys()).as("auth-request keys left in Redis").isEmpty();
        assertThat(browser.seenCookieNames()).as("cookies ever set").doesNotContain("JSESSIONID");
    }

    @Test
    @DisplayName("AC-001.6 edge 122 refuses a callback opened in a second browser")
    void refusesTheCallbackInASecondBrowserWithMsg141_AC_001_6_edge122() {
        TestBrowser first = newBrowser();
        TestBrowser second = newBrowser();
        TestBrowser.Reply keycloakAnswer = toCallback(first, "acme", start(first, "acme"));

        TestBrowser.Reply refused = openCallback(second, keycloakAnswer);
        TestBrowser.Reply accepted = openCallback(first, keycloakAnswer);

        assertThat(refused.status()).as("second browser status").isEqualTo(302);
        assertThat(refused.location()).isEqualTo(MSG141);
        assertExpiryCookie(refused);
        assertThat(accepted.status())
                .as("first browser status, so the refused callback exchanged nothing")
                .isEqualTo(200);
        assertThat(accepted.body()).contains("alice@acme.test");
    }

    @Test
    @DisplayName("AC-001.54 Keycloak refuses a sign-in whose redirect URI it does not know")
    void refusesASignInStartedOnAPortKeycloakDoesNotKnow_AC_001_54() {
        TestBrowser browser = newBrowser();
        TestBrowser.Reply start = browser.backend("http://acme.localhost:8082/api/v1/auth/sign-in", backendPort);
        assertThat(start.status()).as("start status").isEqualTo(302);

        TestBrowser.Reply reply = browser.get(start.location());

        assertThat(reply.status() / 100)
                .as("Keycloak shows an error page, no redirect")
                .isNotEqualTo(3);
        assertThat(reply.location()).isNull();
    }

    @Test
    @DisplayName("AC-001.8 answers MSG06 to an error that Keycloak returns to the callback")
    void answersMsg06ToAKeycloakErrorReturnedToTheCallback_AC_001_8() {
        TestBrowser browser = newBrowser();
        TestBrowser.Reply start = start(browser, "acme");
        // a fresh browser has no SSO session, so prompt=none makes Keycloak answer login_required
        TestBrowser.Reply keycloakAnswer =
                browser.followUntilCallback(browser.get(start.location() + "&prompt=none"), callbackBase("acme"));
        assertThat(TestBrowser.param(keycloakAnswer.location(), "error")).isEqualTo("login_required");

        TestBrowser.Reply reply = openCallback(browser, keycloakAnswer);

        assertThat(reply.status()).as("status").isEqualTo(302);
        assertThat(reply.location()).isEqualTo(MSG06);
        assertExpiryCookie(reply);
        assertThat(authRequestKeys()).as("auth-request keys left in Redis").isEmpty();
    }

    @Test
    @DisplayName("AC-001.64 edge 104 shows no company list and no password form without the hint")
    void showsNoCompanyListAndNoPasswordFormWithoutTheHint_AC_001_64_edge104() {
        TestBrowser browser = newBrowser();
        String withoutHint = UriComponentsBuilder.fromUriString(
                        start(browser, "acme").location())
                .replaceQueryParam("kc_idp_hint")
                .build(true)
                .toUriString();

        TestBrowser.Reply reply = browser.get(withoutHint);

        assertThat(reply.status() / 100).as("status is not 3xx").isNotEqualTo(3);
        String html = reply.body().toLowerCase();
        assertThat(html).doesNotContain("type=\"password\"").doesNotContain("name=\"username\"");
        assertThat(html).doesNotContain("google-acme").doesNotContain("google-beta");
    }

    @Test
    @DisplayName("AC-001.64 AC-001.65 signs a first-time Google account in with no review page and adds it to the "
            + "Organization")
    void createsAFirstTimeGoogleAccountWithNoReviewAndJoinsTheOrganization_AC_001_64_AC_001_65() throws Exception {
        String chooser = "<!doctype html><html><body><ul><li><a href=\"{{request.query.redirect_uri.[0]}}"
                + "?code=carol.{{request.query.nonce.[0]}}&amp;state={{request.query.state.[0]}}\">carol@acme.test"
                + "</a></li></ul></body></html>";
        String idToken = "{{idtoken iss='http://fake-google:8080' aud='fake-google-client' "
                + "sub='fake-google-sub-carol' email='carol@acme.test' hd='acme.test' body=request.body}}";
        String token = "{\"access_token\":\"fake-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
                + "\"id_token\":\"" + idToken + "\"}";
        List<String> stubIds = List.of(
                addFakeGoogleStub(Map.of(
                        "priority", 0,
                        "request",
                                Map.of(
                                        "method", "GET",
                                        "urlPath", "/authorize",
                                        "queryParameters", Map.of("hd", Map.of("equalTo", "acme.test"))),
                        "response",
                                Map.of(
                                        "status",
                                        200,
                                        "headers",
                                        Map.of("Content-Type", "text/html; charset=utf-8"),
                                        "transformers",
                                        List.of("response-template"),
                                        "body",
                                        chooser))),
                addFakeGoogleStub(Map.of(
                        "priority", 0,
                        "request",
                                Map.of(
                                        "method", "POST",
                                        "urlPath", "/token",
                                        "bodyPatterns", List.of(Map.of("matches", ".*code=carol\\..*"))),
                        "response",
                                Map.of(
                                        "status",
                                        200,
                                        "headers",
                                        Map.of("Content-Type", "application/json"),
                                        "transformers",
                                        List.of("response-template"),
                                        "body",
                                        token))));
        SignInTestFixtures.seedAccount(db(), "carol@acme.test", "Carol Acme");
        try {
            TestBrowser browser = newBrowser();

            TestBrowser.Reply keycloakAnswer = toCallback(browser, "acme", start(browser, "acme"));
            TestBrowser.Reply reply = openCallback(browser, keycloakAnswer);

            assertThat(browser.pagesShown())
                    .as("the only page shown is the chooser: no review or update-profile page")
                    .hasSize(1)
                    .allMatch(url -> url.startsWith("http://fake-google.localhost:8181/authorize"));
            assertThat(reply.status()).as("callback status").isEqualTo(200);
            JsonNode claims = json.readTree(reply.body());
            assertThat(claims.path("email").asText()).isEqualTo("carol@acme.test");
            assertThat(claims.path("organization").path("acme").path("id").asText())
                    .isEqualTo(DevAccounts.ACME_ORGANIZATION_ID);
            assertThat(claims.path("hd").asText()).isEqualTo("acme.test");
            assertThat(realm().organizations()
                            .get(DevAccounts.ACME_ORGANIZATION_ID)
                            .members()
                            .getAll())
                    .extracting(member -> member.getEmail())
                    .contains("carol@acme.test");
        } finally {
            for (String id : stubIds) {
                fakeGoogleAdmin("DELETE", "/__admin/mappings/" + id, null);
            }
            db().update("DELETE FROM app_user WHERE email = 'carol@acme.test'");
            realm().users()
                    .searchByEmail("carol@acme.test", true)
                    .forEach(user -> realm().users().delete(user.getId()).close());
        }
    }

    private String addFakeGoogleStub(Map<String, Object> mapping) throws Exception {
        String reply = fakeGoogleAdmin("POST", "/__admin/mappings", json.writeValueAsString(mapping));
        return json.readTree(reply).path("id").asText();
    }

    private static String fakeGoogleAdmin(String method, String path, String body) throws Exception {
        HttpRequest.BodyPublisher publisher =
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + FAKE_GOOGLE.getMappedPort(8080) + path))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode() / 100)
                .as("fake Google admin %s %s", method, path)
                .isEqualTo(2);
        return response.body();
    }
}
