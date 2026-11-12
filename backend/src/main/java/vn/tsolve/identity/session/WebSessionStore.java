package vn.tsolve.identity.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.signin.IdentityClock;

/**
 * The Redis store of web sessions. A session is a hash under {@code tsolve:session:<SHA-256 of the cookie value,
 * hex>}, so the cookie value itself is never stored. Its time to live is the idle limit (60 minutes) cut to the
 * absolute limit (8 hours from creation).
 */
@Component
public class WebSessionStore {

    static final String KEY_PREFIX = "tsolve:session:";
    static final Duration IDLE_LIMIT = Duration.ofMinutes(60);
    static final Duration ABSOLUTE_LIMIT = Duration.ofHours(8);

    private static final String KIND = "kind";
    private static final String WORKSPACE_ID = "workspace_id";
    private static final String ACCOUNT_ID = "account_id";
    private static final String OPERATOR_EMAIL = "operator_email";
    private static final String SITE_HOST = "site_host";
    private static final String ID_TOKEN_HINT = "id_token_hint";
    private static final String CREATED_AT = "created_at";
    private static final String LAST_ACTIVITY_AT = "last_activity_at";

    /** Touches only a key that still exists, in one step, so a deleted session is never written back. */
    private static final DefaultRedisScript<Long> TOUCH = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then"
                    + " redis.call('HSET', KEYS[1], 'last_activity_at', ARGV[1]);"
                    + " redis.call('EXPIRE', KEYS[1], ARGV[2]); return 1 end return 0",
            Long.class);

    private final StringRedisTemplate redis;
    private final SessionCookie cookies;
    private final IdentityClock clock;

    public WebSessionStore(StringRedisTemplate redis, SessionCookie cookies, IdentityClock clock) {
        this.redis = redis;
        this.cookies = cookies;
        this.clock = clock;
    }

    /** Stores the session under a new random id; returns the cookie value (43 base64url characters). */
    public String create(WebSession session) {
        String cookieValue = cookies.newValue();
        String key = keyOf(cookieValue);
        Map<String, String> fields = new HashMap<>();
        fields.put(KIND, session.kind().name());
        fields.put(CREATED_AT, session.createdAt().toString());
        fields.put(LAST_ACTIVITY_AT, session.lastActivityAt().toString());
        putIfPresent(fields, WORKSPACE_ID, session.workspaceId());
        putIfPresent(fields, ACCOUNT_ID, session.accountId());
        putIfPresent(fields, OPERATOR_EMAIL, session.operatorEmail());
        putIfPresent(fields, SITE_HOST, session.siteHost());
        putIfPresent(fields, ID_TOKEN_HINT, session.idTokenHint());
        Duration ttl = timeToLive(session.createdAt(), clock.now());
        // one MULTI/EXEC, so a key never exists without its time to live
        redis.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForHash().putAll(key, fields);
                operations.expire(key, ttl);
                return operations.exec();
            }
        });
        return cookieValue;
    }

    /** The session behind a cookie value, or empty for no key or a key without {@code kind} or {@code created_at}. */
    public Optional<WebSession> find(String cookieValue) {
        Map<Object, Object> fields = redis.opsForHash().entries(keyOf(cookieValue));
        Object kind = fields.get(KIND);
        Object createdAt = fields.get(CREATED_AT);
        if (kind == null || createdAt == null) {
            return Optional.empty();
        }
        Instant created = Instant.parse(createdAt.toString());
        Object lastActivity = fields.get(LAST_ACTIVITY_AT);
        return Optional.of(new WebSession(
                WebSession.Kind.valueOf(kind.toString()),
                uuidOf(fields.get(WORKSPACE_ID)),
                uuidOf(fields.get(ACCOUNT_ID)),
                textOf(fields.get(OPERATOR_EMAIL)),
                textOf(fields.get(SITE_HOST)),
                textOf(fields.get(ID_TOKEN_HINT)),
                created,
                lastActivity == null ? created : Instant.parse(lastActivity.toString())));
    }

    /** Sets {@code last_activity_at} and renews the TTL (never past created + 8 h), only if the key exists. */
    public void touch(String cookieValue, Instant now) {
        String key = keyOf(cookieValue);
        Object createdAt = redis.opsForHash().get(key, CREATED_AT);
        if (createdAt == null) {
            return;
        }
        long seconds = timeToLive(Instant.parse(createdAt.toString()), now).toSeconds();
        redis.execute(TOUCH, List.of(key), now.toString(), Long.toString(seconds));
    }

    /**
     * Deletes the key; a second call is a no-op. Returns true only to the caller whose DEL removed the key (the claim):
     * only that caller may publish the {@code SessionEnded} event.
     */
    public boolean end(String cookieValue) {
        return Boolean.TRUE.equals(redis.delete(keyOf(cookieValue)));
    }

    /** The SHA-256 hex of the cookie value: the session id carried by {@code SessionEnded}. */
    public String sessionIdOf(String cookieValue) {
        return keyOf(cookieValue).substring(KEY_PREFIX.length());
    }

    /** The Redis key {@code tsolve:session:<SHA-256 hex of the cookie value>}. */
    public String keyOf(String cookieValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cookieValue.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static Duration timeToLive(Instant createdAt, Instant now) {
        Duration untilAbsoluteLimit = Duration.between(now, createdAt.plus(ABSOLUTE_LIMIT));
        Duration ttl = untilAbsoluteLimit.compareTo(IDLE_LIMIT) < 0 ? untilAbsoluteLimit : IDLE_LIMIT;
        // Redis refuses a zero or negative expiry; one second keeps the key from living on
        return ttl.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : ttl;
    }

    private static void putIfPresent(Map<String, String> fields, String name, Object value) {
        if (value != null) {
            fields.put(name, value.toString());
        }
    }

    private static UUID uuidOf(Object value) {
        return value == null || value.toString().isEmpty() ? null : UUID.fromString(value.toString());
    }

    private static String textOf(Object value) {
        return value == null || value.toString().isEmpty() ? null : value.toString();
    }
}
