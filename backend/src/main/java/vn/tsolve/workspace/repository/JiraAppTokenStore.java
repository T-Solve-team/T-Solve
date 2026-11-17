package vn.tsolve.workspace.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.workspace.crypto.TokenCipher;
import vn.tsolve.workspace.dto.JiraAppToken;

/**
 * Holds the Jira App's token per Workspace in Redis, encrypted with {@code WORKSPACE_SECRET_KEY} and the Workspace id
 * as associated data, expiring with the token (Non-Functional 4, AC-014.1). Redis is shared by the web and the worker,
 * and survives a restart of either.
 */
@Repository
public class JiraAppTokenStore {

    static final String KEY_PREFIX = "tsolve:jira-app-token:";

    private static final Logger log = LoggerFactory.getLogger(JiraAppTokenStore.class);

    private final StringRedisTemplate redis;
    private final TokenCipher cipher;
    private final ObjectMapper json;
    private final Clock clock;

    public JiraAppTokenStore(StringRedisTemplate redis, TokenCipher cipher, ObjectMapper json, Clock clock) {
        this.redis = redis;
        this.cipher = cipher;
        this.json = json;
        this.clock = clock;
    }

    /** Keeps the token unless a held one expires later (AC-014.5); an expired token is never written. */
    public void hold(UUID workspaceId, JiraAppToken token) {
        Optional<JiraAppToken> held = current(workspaceId);
        if (held.isPresent() && held.get().expiresAt().isAfter(token.expiresAt())) {
            return;
        }
        Duration ttl = Duration.between(clock.instant(), token.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + workspaceId, cipher.encrypt(workspaceId, serialise(token)), ttl);
    }

    /** The held, unexpired token; empty when none, or when the value cannot be decrypted (AC-014.7). */
    public Optional<JiraAppToken> current(UUID workspaceId) {
        String value = redis.opsForValue().get(KEY_PREFIX + workspaceId);
        if (value == null) {
            return Optional.empty();
        }
        try {
            JiraAppToken token = deserialise(cipher.decrypt(workspaceId, value));
            return token.expiresAt().isAfter(clock.instant()) ? Optional.of(token) : Optional.empty();
        } catch (IllegalStateException | JsonProcessingException | IllegalArgumentException failure) {
            // Never the value or the cause: they could carry the ciphertext (AC-014.6).
            log.error("Workspace {} Jira App token could not be read; it is dropped", workspaceId);
            redis.delete(KEY_PREFIX + workspaceId);
            return Optional.empty();
        }
    }

    private String serialise(JiraAppToken token) {
        ObjectNode node = json.createObjectNode()
                .put("token", token.token())
                .put("cloudId", token.cloudId())
                .put("receivedAt", token.receivedAt().toString())
                .put("expiresAt", token.expiresAt().toString());
        return node.toString();
    }

    private JiraAppToken deserialise(String value) throws JsonProcessingException {
        JsonNode node = json.readTree(value);
        return new JiraAppToken(
                node.path("token").asText(),
                node.path("cloudId").asText(),
                Instant.parse(node.path("receivedAt").asText()),
                Instant.parse(node.path("expiresAt").asText()));
    }
}
