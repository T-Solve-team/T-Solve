package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.testsupport.SharedPostgres;

/**
 * Task 23a: the app reaches a password-protected Redis only through REDIS_HOST, REDIS_PORT and REDIS_PASSWORD, so the
 * mapping in application.yml is what is under test. Redis starts with the same pinned tag as docker-compose.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
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
class RedisWiringIT {

    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = UUID.randomUUID().toString();

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

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private HealthEndpoint health;

    @Autowired
    private MockMvc mvc;

    private static String newKey() {
        return "tsolve:test:" + UUID.randomUUID();
    }

    private static String cliGet(String key) throws Exception {
        return REDIS.execInContainer("redis-cli", "-a", REDIS_PASSWORD, "--no-auth-warning", "GET", key)
                .getStdout()
                .trim();
    }

    @Test
    @DisplayName("edge 129 reaches Redis through REDIS_HOST, REDIS_PORT and REDIS_PASSWORD")
    void reachesRedisThroughTheThreeSettings_edge129() throws Exception {
        LettuceConnectionFactory lettuce = (LettuceConnectionFactory) connectionFactory;
        assertThat(lettuce.getHostName()).isEqualTo(REDIS.getHost());
        assertThat(lettuce.getPort()).isEqualTo(REDIS.getMappedPort(REDIS_PORT));

        String written = newKey();
        String read = newKey();
        try {
            redis.opsForValue().set(written, "from-app");
            assertThat(cliGet(written)).isEqualTo("from-app");

            REDIS.execInContainer("redis-cli", "-a", REDIS_PASSWORD, "--no-auth-warning", "SET", read, "from-cli");
            assertThat(redis.opsForValue().get(read)).isEqualTo("from-cli");
        } finally {
            redis.delete(List.of(written, read));
        }
    }

    @Test
    @DisplayName("edge 129 a key written with a time to live expires")
    void keyWithTimeToLiveExpires_edge129() throws Exception {
        String key = newKey();
        try {
            redis.opsForValue().set(key, "short-lived", Duration.ofSeconds(1));

            Long millis = redis.getExpire(key, TimeUnit.MILLISECONDS);
            assertThat(millis).isPositive().isLessThanOrEqualTo(1000L);

            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertThat(redis.hasKey(key)).isFalse();
            assertThat(redis.opsForValue().get(key)).isNull();
        } finally {
            redis.delete(key);
        }
    }

    @Test
    @DisplayName("edge 130 reports the Redis health UP")
    void reportsRedisHealthUp_edge130() throws Exception {
        assertThat(health.healthForPath("redis").getStatus()).isEqualTo(Status.UP);

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
