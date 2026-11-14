package vn.tsolve.identity.session;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.identity.signin.IdentityClock;
import vn.tsolve.identity.signin.OidcCallbackHandler;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/** Test-only helpers for the Redis session tests (Task 26a): cookie values, hashes, URLs, Redis and the clock. */
public final class SessionTestFixtures {

    public static final String SESSION_COOKIE = "__Host-TSOLVE_SESSION";
    public static final String KEY_PREFIX = "tsolve:session:";
    /** The sorted set the expiry sweep reads (Task 27b); not a session key. */
    public static final String EXPIRY_INDEX = "tsolve:session:expiry";

    public static final String REDIS_PASSWORD = "test-redis-password";
    public static final int PORT = 8081;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static GenericContainer<?> redis;

    private SessionTestFixtures() {}

    /** A random cookie value: 32 random bytes as 43 base64url characters. */
    public static String randomCookieValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    public static String keyOf(String cookieValue) {
        return KEY_PREFIX + sha256Hex(cookieValue);
    }

    /** The member of the expiry index: {@code <sha256Hex(cookie)>|<workspaceId>|<accountId>}. */
    public static String memberOf(String cookieValue, UUID workspaceId, UUID accountId) {
        return sha256Hex(cookieValue) + "|" + workspaceId + "|" + accountId;
    }

    /** The index score of a deadline: epoch milliseconds. */
    public static double scoreOf(Instant deadline) {
        return deadline.toEpochMilli();
    }

    public static String url(String host, String path) {
        return "http://" + host + ":" + PORT + path;
    }

    /** The value of the non-empty cookie of this name the response sets, or null. */
    public static String cookieValue(MockHttpServletResponse response, String name) {
        for (String header : SignInTestFixtures.setCookie(response, name)) {
            int end = header.indexOf(';') < 0 ? header.length() : header.indexOf(';');
            String value = header.substring(name.length() + 1, end);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /** ID token claims of a verified sign-in on this Workspace (its Organization and Google domain). */
    public static Map<String, Object> claimsOf(WorkspaceRef workspace, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "kc-sub-" + email);
        claims.put("email", email);
        claims.put("email_verified", true);
        claims.put("name", "Signed In");
        claims.put("organization", Map.of(workspace.slug(), Map.of("id", workspace.organizationId())));
        claims.put("hd", workspace.googleDomain());
        return claims;
    }

    /** A callback request on this host, as if the saved-request check had passed. */
    public static MockHttpServletRequest callbackRequest(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/callback");
        request.setServerName(host);
        request.setServerPort(PORT);
        request.setScheme("http");
        request.setAttribute(OidcCallbackHandler.REQUEST_CHECKED_ATTRIBUTE, Boolean.TRUE);
        return request;
    }

    public static synchronized void registerRedis(DynamicPropertyRegistry registry) {
        if (redis == null) {
            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
                    .withExposedPorts(6379);
            redis.start();
        }
        GenericContainer<?> container = redis;
        registry.add("spring.data.redis.host", container::getHost);
        registry.add("spring.data.redis.port", () -> container.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
    }

    public static synchronized void registerDeadRedis(DynamicPropertyRegistry registry) {
        int closed = freePort();
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> closed);
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
        registry.add("spring.data.redis.timeout", () -> "500ms");
        registry.add("spring.data.redis.connect-timeout", () -> "500ms");
    }

    public static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A clock a test moves by hand (it was deleted from main by Task 24, so it lives here now). */
    public static final class MutableClock extends Clock {

        public static final Instant START = Instant.parse("2026-11-01T09:00:00Z");

        private volatile Instant now = START;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        public void advance(Duration amount) {
            now = now.plus(amount);
        }

        public void reset() {
            now = START;
        }
    }

    /** A movable clock, the identity clock over it, and the in-memory Workspaces (ACME, BETA, ...). */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Beans {

        @Bean
        @Primary
        MutableClock sessionTestMutableClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        IdentityClock sessionTestIdentityClock(MutableClock clock) {
            return new IdentityClock(clock);
        }

        @Bean
        @Primary
        InMemoryWorkspaceLookup sessionTestWorkspaceLookup() {
            return new InMemoryWorkspaceLookup();
        }
    }
}
