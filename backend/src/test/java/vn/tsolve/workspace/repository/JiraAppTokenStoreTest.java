package vn.tsolve.workspace.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import vn.tsolve.workspace.crypto.TokenCipher;
import vn.tsolve.workspace.dto.JiraAppToken;

class JiraAppTokenStoreTest {
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CLOUD = "1324a887-45db-1bf4-1e99-ef0ff456d421";
    private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");
    private static final String KEY = JiraAppTokenStore.KEY_PREFIX + WORKSPACE;

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final TokenCipher cipher = cipher(new byte[32]);
    private JiraAppTokenStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        store = new JiraAppTokenStore(redis, cipher, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TokenCipher cipher(byte[] key) {
        return new TokenCipher(new MockEnvironment()
                .withProperty("tsolve.workspace.secret-key", Base64.getEncoder().encodeToString(key)));
    }

    private static JiraAppToken token(String value, Instant expiresAt) {
        return new JiraAppToken(value, CLOUD, NOW, expiresAt);
    }

    @Test
    void holdsTheTokenEncryptedUntilItExpires_AC_014_1() {
        store.hold(WORKSPACE, token("app-token-1", NOW.plus(Duration.ofHours(3))));

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq(KEY), stored.capture(), eq(Duration.ofHours(3)));
        assertThat(stored.getValue()).doesNotContain("app-token-1", CLOUD);
        when(values.get(KEY)).thenReturn(stored.getValue());
        assertThat(store.current(WORKSPACE)).contains(token("app-token-1", NOW.plus(Duration.ofHours(3))));
    }

    @Test
    void keepsAHeldTokenThatExpiresLater_AC_014_5() {
        store.hold(WORKSPACE, token("later", NOW.plus(Duration.ofHours(4))));
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq(KEY), stored.capture(), any(Duration.class));
        when(values.get(KEY)).thenReturn(stored.getValue());

        store.hold(WORKSPACE, token("earlier", NOW.plus(Duration.ofHours(2))));

        verify(values, never()).set(eq(KEY), anyString(), eq(Duration.ofHours(2)));
        assertThat(store.current(WORKSPACE)).map(JiraAppToken::token).contains("later");
    }

    @Test
    void dropsAValueThatCannotBeDecryptedWithoutLoggingIt_AC_014_7() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        String foreign = cipher(otherKey).encrypt(WORKSPACE, "{\"token\":\"secret\"}");
        when(values.get(KEY)).thenReturn(foreign);
        Logger logger = (Logger) LoggerFactory.getLogger(JiraAppTokenStore.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertThat(store.current(WORKSPACE)).isEmpty();
        } finally {
            logger.detachAppender(logs);
        }
        verify(redis).delete(KEY);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage())
                    .contains(WORKSPACE.toString())
                    .doesNotContain(foreign, "secret");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void neverWritesAnExpiredToken_AC_014_3() {
        store.hold(WORKSPACE, token("old", NOW.minusSeconds(1)));

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void answersEmptyWhenNothingIsHeld_AC_014_1() {
        assertThat(store.current(WORKSPACE)).isEmpty();
    }
}
