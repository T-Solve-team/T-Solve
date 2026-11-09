package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.keycloak.admin.client.resource.RealmResource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import vn.tsolve.TSolveApplication;

/**
 * Test-only (Task 31): one stack for {@link KeycloakThemeIT} and {@link KeycloakFailurePlacementIT}, started once per
 * JVM on the first {@link #get()} and cleaned up by Testcontainers' Ryuk when the JVM ends. The setup is the one of
 * {@code KeycloakSignInEndToEndIT.startStack}. The T-Solve theme is mounted only if {@link #THEME_DIR} exists, so a run
 * before the theme is written fails on the missing theme and not on a container that cannot start.
 */
final class KeycloakItStack {

    static final String WEB_CLIENT_SECRET = "it-keycloak-web-client-secret";
    static final String REALM = "tsolve";
    /** {@code TSOLVE_WEB_BASE_URL} of the test Keycloak: the theme fallback ("Keycloak does not know the client"). */
    static final String FALLBACK_URL = "http://fallback.it.test/";
    /** {@code tsolve-web.baseUrl} after start-up: the dev realm value. */
    static final String CLIENT_URL = "http://localhost:5173/";

    static final Path REALM_FILE =
            Path.of("../infra/keycloak/realm-tsolve.json").toAbsolutePath().normalize();
    static final Path FAKE_GOOGLE_DIR =
            Path.of("../infra/keycloak/fake-google").toAbsolutePath().normalize();
    static final Path THEME_DIR =
            Path.of("../infra/keycloak/themes/tsolve").toAbsolutePath().normalize();
    static final String THEME_MOUNT = "/opt/keycloak/themes/tsolve";
    private static final int REDIS_PORT = 6379;

    private static KeycloakItStack instance;

    private final ObjectMapper json = new ObjectMapper();
    private final GenericContainer<?> fakeGoogle;
    private final KeycloakContainer keycloak;
    private final int backendPort;
    private final StringRedisTemplate redis;
    private final String loginOrigin;

    @SuppressWarnings("resource") // the containers live until the JVM ends (Ryuk)
    private KeycloakItStack() {
        Network network = Network.newNetwork();
        String redisPassword = UUID.randomUUID().toString();
        PostgreSQLContainer<?> db = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
        GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withCommand("redis-server", "--requirepass", redisPassword)
                .withExposedPorts(REDIS_PORT);
        fakeGoogle = new GenericContainer<>(new ImageFromDockerfile().withFileFromPath(".", FAKE_GOOGLE_DIR))
                .withNetwork(network)
                .withNetworkAliases("fake-google")
                .withExposedPorts(8080)
                .withCommand("--global-response-templating", "--extensions", "vn.tsolve.fakegoogle.IdTokenExtension")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(FAKE_GOOGLE_DIR.resolve("mappings")), "/home/wiremock/mappings")
                .waitingFor(Wait.forHttp("/__admin/mappings").forPort(8080));
        db.start();
        redisContainer.start();
        fakeGoogle.start();
        KeycloakContainer kc = new KeycloakContainer("quay.io/keycloak/keycloak:26.4.7")
                .withNetwork(network)
                .withEnv("KEYCLOAK_WEB_CLIENT_SECRET", WEB_CLIENT_SECRET)
                .withEnv("TSOLVE_WEB_BASE_URL", FALLBACK_URL)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(REALM_FILE), "/opt/keycloak/data/import/realm-tsolve.json");
        if (Files.isDirectory(THEME_DIR)) {
            kc = kc.withCopyFileToContainer(MountableFile.forHostPath(THEME_DIR), THEME_MOUNT);
        }
        keycloak = kc;
        keycloak.start();
        // The env var feeds both the client baseUrl and the theme fallback. Give the client its dev value so the two
        // differ and a test can tell which one a page uses.
        var webClient = realm().clients().findByClientId("tsolve-web").get(0);
        webClient.setBaseUrl(CLIENT_URL);
        realm().clients().get(webClient.getId()).update(webClient);
        loginOrigin = trimSlash(keycloak.getAuthServerUrl());
        ConfigurableApplicationContext backend = new SpringApplicationBuilder(TSolveApplication.class)
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
                        "--DB_HOST=" + db.getHost(),
                        "--DB_PORT=" + db.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        "--DB_NAME=" + db.getDatabaseName(),
                        "--DB_USER=" + db.getUsername(),
                        "--DB_PASSWORD=" + db.getPassword(),
                        "--REDIS_HOST=" + redisContainer.getHost(),
                        "--REDIS_PORT=" + redisContainer.getMappedPort(REDIS_PORT),
                        "--REDIS_PASSWORD=" + redisPassword,
                        "--spring.jmx.enabled=false");
        backendPort =
                ((ServletWebServerApplicationContext) backend).getWebServer().getPort();
        redis = backend.getBean(StringRedisTemplate.class);
    }

    static synchronized KeycloakItStack get() {
        if (instance == null) {
            instance = new KeycloakItStack();
        }
        return instance;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ---- accessors

    /** Keycloak's origin, for example {@code http://localhost:49153}, with no trailing slash. */
    String loginOrigin() {
        return loginOrigin;
    }

    int backendPort() {
        return backendPort;
    }

    StringRedisTemplate redis() {
        return redis;
    }

    RealmResource realm() {
        return keycloak.getKeycloakAdminClient().realm(REALM);
    }

    TestBrowser newBrowser() {
        return new TestBrowser(Map.of("fake-google.localhost:8181", "localhost:" + fakeGoogle.getMappedPort(8080)));
    }

    String callbackBase(String slug) {
        return "http://" + slug + ".localhost:8080/api/v1/auth/callback";
    }

    /** Starts a real sign-in on {@code <slug>.localhost:8080} through the backend; returns its 302 to Keycloak. */
    TestBrowser.Reply start(TestBrowser browser, String slug) {
        TestBrowser.Reply reply =
                browser.backend("http://" + slug + ".localhost:8080/api/v1/auth/sign-in", backendPort);
        assertThat(reply.status()).as("start status").isEqualTo(302);
        return reply;
    }

    TestBrowser.Reply openCallback(TestBrowser browser, TestBrowser.Reply keycloakAnswer) {
        return browser.backend(keycloakAnswer.location(), backendPort);
    }

    // ---- fake Google stubs, added at runtime (the mappings files stay as they are)

    String addFakeGoogleStub(Map<String, Object> mapping) {
        try {
            String reply = fakeGoogleAdmin("POST", "/__admin/mappings", json.writeValueAsString(mapping));
            return json.readTree(reply).path("id").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    void removeFakeGoogleStubs(List<String> ids) {
        for (String id : ids) {
            fakeGoogleAdmin("DELETE", "/__admin/mappings/" + id, null);
        }
    }

    private String fakeGoogleAdmin(String method, String path, String body) {
        try {
            HttpRequest.BodyPublisher publisher =
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://localhost:" + fakeGoogle.getMappedPort(8080) + path))
                    .header("Content-Type", "application/json")
                    .method(method, publisher)
                    .build();
            HttpResponse<String> response =
                    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode() / 100)
                    .as("fake Google admin %s %s", method, path)
                    .isEqualTo(2);
            return response.body();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** A priority-0 answer to {@code GET /authorize?hd=acme.test}. */
    Map<String, Object> authorizeStub(int status, Map<String, String> headers, String body) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("status", status);
        response.put("headers", headers);
        response.put("transformers", List.of("response-template"));
        response.put("body", body);
        return Map.of(
                "priority",
                0,
                "request",
                Map.of(
                        "method",
                        "GET",
                        "urlPath",
                        "/authorize",
                        "queryParameters",
                        Map.of("hd", Map.of("equalTo", "acme.test"))),
                "response",
                response);
    }

    /** The chooser page with one link whose code starts with {@code codePrefix}. */
    Map<String, Object> chooserStub(String codePrefix) {
        String body = "<!doctype html><html><body><ul><li><a href=\"{{request.query.redirect_uri.[0]}}"
                + "?code=" + codePrefix + ".{{request.query.nonce.[0]}}&amp;state={{request.query.state.[0]}}\">"
                + codePrefix + "@acme.test</a></li></ul></body></html>";
        return authorizeStub(200, Map.of("Content-Type", "text/html; charset=utf-8"), body);
    }

    /** {@code POST /token} with a code that starts with {@code codePrefix}: answers with this status and body. */
    Map<String, Object> tokenStub(String codePrefix, int status) {
        return Map.of(
                "priority",
                0,
                "request",
                Map.of(
                        "method",
                        "POST",
                        "urlPath",
                        "/token",
                        "bodyPatterns",
                        List.of(Map.of("matches", ".*code=" + codePrefix + "\\..*"))),
                "response",
                Map.of(
                        "status",
                        status,
                        "headers",
                        Map.of("Content-Type", "application/json"),
                        "body",
                        "{\"error\":\"server_error\"}"));
    }
}
