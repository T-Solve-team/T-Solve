package vn.tsolve.identity.signin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * The start-up check that Keycloak's discovery document names the issuer the backend expects
 * ({@code TSOLVE_LOGIN_ORIGIN + /realms/tsolve}). Keycloak is an in-process WireMock; the context is
 * {@link SignInConfig} over the real application.yml, so nothing else starts.
 */
@ExtendWith(OutputCaptureExtension.class)
class DiscoveryCheckTest {

    private static final String DISCOVERY_PATH = "/realms/tsolve/.well-known/openid-configuration";
    private static final String OPS_DISCOVERY_PATH = "/realms/tsolve-ops/.well-known/openid-configuration";
    private static final String OTHER_ISSUER = "http://keycloak:8080/realms/tsolve";

    private static WireMockServer keycloak;

    @BeforeAll
    static void startKeycloak() {
        keycloak = new WireMockServer(options().dynamicPort());
        keycloak.start();
    }

    @AfterAll
    static void stopKeycloak() {
        keycloak.stop();
    }

    @BeforeEach
    void resetKeycloak() {
        keycloak.resetAll();
        // Task 29a: start-up also checks tsolve-ops; every test below serves a good one unless it says otherwise
        serveOpsDiscovery(baseUrl() + "/realms/tsolve-ops");
    }

    private static void serveOpsDiscovery(String issuer) {
        keycloak.stubFor(get(urlEqualTo(OPS_DISCOVERY_PATH))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"issuer\":\"" + issuer + "\"}")));
    }

    private static String baseUrl() {
        return "http://localhost:" + keycloak.port();
    }

    private static String discoveryUrl(String origin) {
        return origin + DISCOVERY_PATH;
    }

    private static void serveDiscovery(String issuer) {
        keycloak.stubFor(get(urlEqualTo(DISCOVERY_PATH))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"issuer\":\"" + issuer + "\"}")));
    }

    private static ApplicationContextRunner runner(String loginOrigin) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("TSOLVE_LOGIN_ORIGIN", loginOrigin);
        env.put("TSOLVE_ADMIN_ORIGIN", SignInTestFixtures.ADMIN_ORIGIN);
        env.put("TSOLVE_SIGNING_KEY", SignInTestFixtures.KEY);
        env.put("GOOGLE_CLIENT_ID", "test-client-id");
        env.put("GOOGLE_CLIENT_SECRET", "test-secret");
        env.put("PLATFORM_OPERATOR_EMAILS", "ops@example.test");
        env.put("KEYCLOAK_WEB_CLIENT_SECRET", "test-keycloak-secret");
        env.put("KEYCLOAK_CONSOLE_CLIENT_SECRET", "test-keycloak-console-secret");
        return new ApplicationContextRunner()
                .withInitializer(DiscoveryCheckTest::onlyApplicationYml)
                .withPropertyValues(env.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new))
                .withUserConfiguration(SignInConfig.class);
    }

    private static void onlyApplicationYml(ConfigurableApplicationContext context) {
        MutablePropertySources sources = context.getEnvironment().getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        try {
            for (PropertySource<?> source :
                    new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))) {
                sources.addLast(source);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String failureText(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            text.append(cause).append('\n');
        }
        return text.toString();
    }

    private static long warnLinesNaming(CapturedOutput output, String issuer) {
        // not followed by "-": the issuer of tsolve-ops starts with the issuer of tsolve
        java.util.regex.Pattern naming =
                java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(issuer) + "(?!-)");
        return Arrays.stream(output.getAll().split("\\R"))
                .filter(line -> line.contains("WARN") && naming.matcher(line).find())
                .count();
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "issuer equals origin + /realms/tsolve | false | ",
                "origin with a trailing slash, no double slash in the issuer | true | ",
                "issuer differs from the expected one | false | " + OTHER_ISSUER
            })
    @DisplayName("edge 111 starts when the discovery issuer equals TSOLVE_LOGIN_ORIGIN + /realms/tsolve, "
            + "and refuses to start naming both issuers when they differ")
    void comparesDiscoveryIssuerWithLoginOrigin_edge111(String label, boolean trailingSlash, String servedIssuer) {
        String expectedIssuer = baseUrl() + "/realms/tsolve";
        boolean matches = servedIssuer == null || servedIssuer.isBlank();
        serveDiscovery(matches ? expectedIssuer : servedIssuer);

        runner(trailingSlash ? baseUrl() + "/" : baseUrl()).run(context -> {
            keycloak.verify(1, getRequestedFor(urlEqualTo(DISCOVERY_PATH)));
            if (matches) {
                assertThat(context).hasNotFailed();
            } else {
                assertThat(context.getStartupFailure()).as("startup failure").isNotNull();
                assertThat(failureText(context.getStartupFailure()))
                        .contains(expectedIssuer)
                        .contains(servedIssuer)
                        .contains("TSOLVE_LOGIN_ORIGIN");
            }
        });
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"connection refused", "unknown host", "503", "500", "reply slower than 5 s"})
    @DisplayName("edge 110 starts with one WARN naming the issuer when Keycloak cannot be reached or answers 5xx")
    void startsWithWarnWhenKeycloakIsNotAvailable_edge110(String how, CapturedOutput output) {
        String origin;
        switch (how) {
            case "connection refused" -> origin = "http://localhost:" + closedPort();
            case "unknown host" -> origin = "http://nope.invalid";
            default -> {
                origin = baseUrl();
                switch (how) {
                    case "503" ->
                        keycloak.stubFor(get(urlEqualTo(DISCOVERY_PATH))
                                .willReturn(aResponse().withStatus(503)));
                    case "500" ->
                        keycloak.stubFor(get(urlEqualTo(DISCOVERY_PATH))
                                .willReturn(aResponse().withStatus(500)));
                    default ->
                        keycloak.stubFor(get(urlEqualTo(DISCOVERY_PATH))
                                .willReturn(aResponse()
                                        .withFixedDelay(7_000)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"issuer\":\"" + baseUrl() + "/realms/tsolve\"}")));
                }
            }
        }
        String issuer = origin + "/realms/tsolve";

        runner(origin).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnLinesNaming(output, issuer))
                    .as("WARN lines naming " + issuer)
                    .isEqualTo(1);
        });
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"404", "200 that is not JSON", "JSON with no issuer"})
    @DisplayName("edge 111 refuses a reachable answer that is not a discovery document and names the discovery URL")
    void refusesAnswerThatIsNotADiscoveryDocument_edge111(String how) {
        switch (how) {
            case "404" ->
                keycloak.stubFor(
                        get(urlEqualTo(DISCOVERY_PATH)).willReturn(aResponse().withStatus(404)));
            case "200 that is not JSON" ->
                keycloak.stubFor(get(urlEqualTo(DISCOVERY_PATH))
                        .willReturn(aResponse().withStatus(200).withBody("<html>Not Keycloak</html>")));
            default ->
                keycloak.stubFor(get(urlEqualTo(DISCOVERY_PATH))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"authorization_endpoint\":\"" + baseUrl() + "/x\"}")));
        }

        runner(baseUrl()).run(context -> {
            assertThat(context.getStartupFailure()).as("startup failure").isNotNull();
            assertThat(failureText(context.getStartupFailure())).contains(discoveryUrl(baseUrl()));
        });
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"tsolve-ops names another issuer", "tsolve-ops answers 503"})
    @DisplayName("AC-001.28 edge 111 refuses to start when the tsolve-ops discovery names another issuer, and starts "
            + "with one WARN when tsolve-ops cannot be reached")
    void checksTheOpsRealmDiscoveryIssuer_AC_001_28_edge111(String how, CapturedOutput output) {
        String expectedOps = baseUrl() + "/realms/tsolve-ops";
        serveDiscovery(baseUrl() + "/realms/tsolve");
        boolean wrongIssuer = how.endsWith("another issuer");
        if (wrongIssuer) {
            serveOpsDiscovery("http://keycloak:8080/realms/tsolve-ops");
        } else {
            keycloak.stubFor(
                    get(urlEqualTo(OPS_DISCOVERY_PATH)).willReturn(aResponse().withStatus(503)));
        }

        runner(baseUrl()).run(context -> {
            keycloak.verify(1, getRequestedFor(urlEqualTo(OPS_DISCOVERY_PATH)));
            if (wrongIssuer) {
                assertThat(context.getStartupFailure()).as("startup failure").isNotNull();
                assertThat(failureText(context.getStartupFailure()))
                        .contains(expectedOps)
                        .contains("http://keycloak:8080/realms/tsolve-ops")
                        .contains("TSOLVE_LOGIN_ORIGIN");
            } else {
                assertThat(context).hasNotFailed();
                long warns = Arrays.stream(output.getAll().split("\\R"))
                        .filter(line -> line.contains("WARN") && line.contains(expectedOps))
                        .count();
                assertThat(warns).as("WARN lines naming " + expectedOps).isEqualTo(1);
            }
        });
    }
}
