package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.testsupport.SharedPostgres;

/**
 * Task 23b: the start stores the authorization request in a real Redis under a cookie. The login origin points at a
 * closed port, so every test here also shows that a start works while Keycloak is down (edge 110).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:9",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class RedisAuthorizationRequestRepositoryIT {

    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = UUID.randomUUID().toString();
    private static final String KEYCLOAK_ENDPOINT =
            "http://login.localhost:9/realms/tsolve/protocol/openid-connect/auth";
    private static final String COOKIE = RedisAuthorizationRequestRepository.COOKIE_NAME;
    private static final String KEY_PREFIX = RedisAuthorizationRequestRepository.KEY_PREFIX;

    private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        DB.register(registry);
        registry.add("REDIS_HOST", REDIS::getHost);
        registry.add("REDIS_PORT", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("REDIS_PASSWORD", () -> REDIS_PASSWORD);
    }

    /** Test-only: the Workspaces the start looks up. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Workspaces {

        @Bean
        @Primary
        InMemoryWorkspaceLookup inMemoryWorkspaceLookup() {
            return new InMemoryWorkspaceLookup();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedisAuthorizationRequestRepository repository;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void emptyRedis() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static MockHttpServletRequestBuilder start(String url) {
        return get(url);
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse startAcme() throws Exception {
        return perform(start("http://acme.localhost:8081/api/v1/auth/sign-in").param("returnTo", "/tickets/42"));
    }

    private static String cookieHeader(MockHttpServletResponse response) {
        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(headers).as("Set-Cookie").hasSize(1);
        assertThat(headers.get(0)).startsWith(COOKIE + "=");
        return headers.get(0);
    }

    private static String cookieValue(MockHttpServletResponse response) {
        String header = cookieHeader(response);
        int end = header.indexOf(';');
        return header.substring(COOKIE.length() + 1, end < 0 ? header.length() : end);
    }

    private static List<String> attributes(String header) {
        return Arrays.stream(header.split(";")).skip(1).map(String::trim).toList();
    }

    private Set<String> authRequestKeys() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        return keys == null ? Set.of() : keys;
    }

    private static MockHttpServletRequest requestWithCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/callback");
        if (value != null) {
            request.setCookies(new Cookie(COOKIE, value));
        }
        return request;
    }

    private static void leaves(JsonNode node, List<String> out) {
        if (node.isValueNode()) {
            out.add(node.asText());
        } else {
            node.forEach(child -> leaves(child, out));
        }
    }

    @Test
    @DisplayName(
            "AC-001.53 sets __Host-TSOLVE_AUTHREQ in the 302 with HttpOnly, Secure, SameSite=Lax, Path=/, no Domain, Max-Age=600 and Cache-Control no-store")
    void setsTheAuthRequestCookieWithItsAttributes_AC_001_53(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = startAcme();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).startsWith(KEYCLOAK_ENDPOINT + "?");
        String header = cookieHeader(response);
        assertThat(attributes(header)).contains("Path=/", "Max-Age=600", "Secure", "HttpOnly", "SameSite=Lax");
        assertThat(header).doesNotContain("Domain");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

        String value = cookieValue(response);
        assertThat(value).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(value)).hasSize(32);
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(value)
                .isNotEqualTo(AuthorizationUris.one(location, "state"))
                .isNotEqualTo(AuthorizationUris.one(location, "nonce"))
                .doesNotContain(".");
        assertThat(output.getAll()).doesNotContain(value);
    }

    @Test
    @DisplayName("AC-001.53 stores the request under the SHA-256 of the cookie value for 600 seconds")
    void storesTheRequestUnderTheHashOfTheCookieValue_AC_001_53() throws Exception {
        String value = cookieValue(startAcme());

        String expectedKey = KEY_PREFIX + AuthorizationUris.sha256Hex(value);
        assertThat(authRequestKeys()).containsExactly(expectedKey);
        Long ttl = redis.getExpire(expectedKey, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(590L).isLessThanOrEqualTo(600L);
        assertThat(expectedKey).doesNotContain(value);
        assertThat(redis.opsForValue().get(expectedKey)).isNotBlank().doesNotContain(value);
    }

    @Test
    @DisplayName(
            "AC-001.53 holds the target, the Workspace id, the state, the nonce, the PKCE verifier and the kept returnTo")
    void holdsTheSixValuesOfTheRequest_AC_001_53() throws Exception {
        MockHttpServletResponse response = startAcme();
        String location = response.getHeader(HttpHeaders.LOCATION);
        String value = cookieValue(response);

        String stored = redis.opsForValue().get(KEY_PREFIX + AuthorizationUris.sha256Hex(value));
        assertThat(stored).isNotNull();
        List<String> values = new ArrayList<>();
        leaves(json.readTree(stored), values);
        assertThat(values)
                .contains(
                        "WORKSPACE",
                        InMemoryWorkspaceLookup.ACME.id().toString(),
                        AuthorizationUris.one(location, "state"),
                        "/tickets/42");
        assertThat(values)
                .anyMatch(v -> AuthorizationUris.sha256Base64Url(v).equals(AuthorizationUris.one(location, "nonce")));
        assertThat(values).anyMatch(v -> AuthorizationUris.sha256Base64Url(v)
                .equals(AuthorizationUris.one(location, "code_challenge")));

        OAuth2AuthorizationRequest removed =
                repository.removeAuthorizationRequest(requestWithCookie(value), new MockHttpServletResponse());
        assertThat(removed).isNotNull();
        assertThat(removed.getAuthorizationRequestUri()).isEqualTo(location);
        assertThat(removed.getRedirectUri()).isEqualTo("http://acme.localhost:8081/api/v1/auth/callback");
        assertThat(removed.getState()).isEqualTo(AuthorizationUris.one(location, "state"));
        assertThat(removed.getAttributes().get(KeycloakAuthorizationRequestResolver.TARGET))
                .hasToString("WORKSPACE");
        assertThat(removed.getAttributes().get(KeycloakAuthorizationRequestResolver.WORKSPACE_ID))
                .hasToString(InMemoryWorkspaceLookup.ACME.id().toString());
        assertThat(removed.getAttributes().get(KeycloakAuthorizationRequestResolver.RETURN_TO))
                .isEqualTo("/tickets/42");
        assertThat(AuthorizationUris.sha256Base64Url(
                        (String) removed.getAttributes().get(PkceParameterNames.CODE_VERIFIER)))
                .isEqualTo(AuthorizationUris.one(location, "code_challenge"));
        assertThat(AuthorizationUris.sha256Base64Url(
                        (String) removed.getAttributes().get(OidcParameterNames.NONCE)))
                .isEqualTo(AuthorizationUris.one(location, "nonce"));
    }

    @Test
    @DisplayName("AC-001.53 reads and deletes the request in one step")
    void readsAndDeletesTheRequestInOneStep_AC_001_53() throws Exception {
        String value = cookieValue(startAcme());
        String key = KEY_PREFIX + AuthorizationUris.sha256Hex(value);

        OAuth2AuthorizationRequest first =
                repository.removeAuthorizationRequest(requestWithCookie(value), new MockHttpServletResponse());

        assertThat(first).isNotNull();
        assertThat(redis.hasKey(key)).isFalse();
        assertThat(repository.removeAuthorizationRequest(requestWithCookie(value), new MockHttpServletResponse()))
                .isNull();
    }

    @Test
    @DisplayName("AC-001.53 lets only one of 20 concurrent reads use a request")
    void letsOnlyOneOf20ConcurrentReadsUseARequest_AC_001_53() throws Exception {
        String value = cookieValue(startAcme());
        String key = KEY_PREFIX + AuthorizationUris.sha256Hex(value);
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<OAuth2AuthorizationRequest>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return repository.removeAuthorizationRequest(
                            requestWithCookie(value), new MockHttpServletResponse());
                }));
            }
            go.countDown();
            long used = 0;
            for (Future<OAuth2AuthorizationRequest> result : results) {
                if (result.get(30, TimeUnit.SECONDS) != null) {
                    used++;
                }
            }
            assertThat(used).isEqualTo(1);
            assertThat(redis.hasKey(key)).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    static Stream<Arguments> unusableCookies() {
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        return Stream.of(
                Arguments.of("no cookie", null),
                Arguments.of(
                        "unknown 32 random bytes",
                        Base64.getUrlEncoder().withoutPadding().encodeToString(random)),
                Arguments.of("empty", ""),
                Arguments.of("8 KB", "A".repeat(8 * 1024)),
                Arguments.of("not base64url", "not base64url !! @@ ++//=="),
                Arguments.of("the stored key's own hex", "HEX_OF_STORED_COOKIE"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("unusableCookies")
    @DisplayName("AC-001.53 finds no request for a missing, unknown or malformed cookie")
    void findsNoRequestForAMissingUnknownOrMalformedCookie_AC_001_53(String label, String cookie) throws Exception {
        String stored = cookieValue(startAcme());
        String key = KEY_PREFIX + AuthorizationUris.sha256Hex(stored);
        String sent = "HEX_OF_STORED_COOKIE".equals(cookie) ? AuthorizationUris.sha256Hex(stored) : cookie;

        OAuth2AuthorizationRequest found =
                repository.removeAuthorizationRequest(requestWithCookie(sent), new MockHttpServletResponse());

        assertThat(found).isNull();
        assertThat(authRequestKeys()).containsExactly(key);
    }

    @Test
    @DisplayName("AC-001.52 takes the redirect URI from the public origin behind the proxy")
    void takesTheRedirectUriFromThePublicOriginBehindTheProxy_AC_001_52() throws Exception {
        MockHttpServletResponse response = perform(start("http://10.0.0.5:8080/api/v1/auth/sign-in")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "acme.localhost")
                .header("X-Forwarded-Port", "443"));

        assertThat(response.getStatus()).isEqualTo(302);
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(KEYCLOAK_ENDPOINT + "?");
        assertThat(AuthorizationUris.one(location, "redirect_uri"))
                .isEqualTo("https://acme.localhost/api/v1/auth/callback");
        String stored = redis.opsForValue().get(KEY_PREFIX + AuthorizationUris.sha256Hex(cookieValue(response)));
        assertThat(stored).contains(InMemoryWorkspaceLookup.ACME.id().toString());
    }
}
