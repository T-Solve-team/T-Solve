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
import java.util.Set;
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
    /** Sorted set of Workspace sessions: member {@code <hash>|<workspaceId>|<accountId>}, score = deadline in epoch ms. */
    static final String EXPIRY_INDEX = KEY_PREFIX + "expiry";

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

    /**
     * Touches only a key that still exists, in one step, so a deleted session is never written back; the index member
     * (ARGV[3], empty for none) moves to the new deadline (ARGV[4]) in the same step.
     */
    private static final DefaultRedisScript<Long> TOUCH = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then"
                    + " redis.call('HSET', KEYS[1], 'last_activity_at', ARGV[1]);"
                    + " redis.call('EXPIRE', KEYS[1], ARGV[2]);"
                    + " if ARGV[3] ~= '' then redis.call('ZADD', KEYS[2], ARGV[4], ARGV[3]) end"
                    + " return 1 end return 0",
            Long.class);

    /**
     * The claim: DEL the session key and ZREM its member; 1 if either removed something. With ARGV[2] (now, ms) set it
     * claims only a member still in the index whose score is not past now.
     */
    private static final DefaultRedisScript<Long> CLAIM = new DefaultRedisScript<>(
            "if ARGV[2] ~= '' then local s = redis.call('ZSCORE', KEYS[2], ARGV[1]);"
                    + " if not s or tonumber(s) > tonumber(ARGV[2]) then return 0 end end"
                    + " local d = redis.call('DEL', KEYS[1]);"
                    + " local z = 0; if ARGV[1] ~= '' then z = redis.call('ZREM', KEYS[2], ARGV[1]) end"
                    + " if d + z > 0 then return 1 end return 0",
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
        String member = memberOf(key, session.workspaceId(), session.accountId());
        long deadline = deadlineMillis(session.createdAt(), session.lastActivityAt());
        // one MULTI/EXEC, so a key never exists without its time to live
        redis.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForHash().putAll(key, fields);
                operations.expire(key, ttl);
                if (!member.isEmpty()) {
                    operations.opsForZSet().add(EXPIRY_INDEX, member, deadline);
                }
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
        List<Object> fields = redis.opsForHash().multiGet(key, List.of(CREATED_AT, WORKSPACE_ID, ACCOUNT_ID));
        if (fields.get(0) == null) {
            return;
        }
        Instant created = Instant.parse(fields.get(0).toString());
        long seconds = timeToLive(created, now).toSeconds();
        String member = memberOf(key, uuidOf(fields.get(1)), uuidOf(fields.get(2)));
        redis.execute(
                TOUCH,
                List.of(key, EXPIRY_INDEX),
                now.toString(),
                Long.toString(seconds),
                member,
                Long.toString(deadlineMillis(created, now)));
    }

    /**
     * Deletes the key and removes its index member; a second call is a no-op. Returns true only to the caller whose DEL
     * or ZREM removed something (the claim): only that caller may publish the {@code SessionEnded} event.
     */
    public boolean end(String cookieValue) {
        String key = keyOf(cookieValue);
        // a session already gone has no ids to rebuild its member from; the sweep claims that one
        String member = find(cookieValue)
                .map(s -> memberOf(key, s.workspaceId(), s.accountId()))
                .orElse("");
        return claimed(key, member, "");
    }

    /** The sweep's claim of one index member; false if its score is past {@code nowMillis} or it was claimed already. */
    boolean claim(String member, long nowMillis) {
        return claimed(KEY_PREFIX + member.substring(0, member.indexOf('|')), member, Long.toString(nowMillis));
    }

    /** Removes an index member that cannot be parsed, so it stops coming back every tick. */
    void drop(String member) {
        redis.opsForZSet().remove(EXPIRY_INDEX, member);
    }

    private boolean claimed(String key, String member, String nowMillis) {
        return Long.valueOf(1).equals(redis.execute(CLAIM, List.of(key, EXPIRY_INDEX), member, nowMillis));
    }

    /** Members of the index whose deadline is before {@code nowMillis}. */
    Set<String> due(long nowMillis) {
        Set<String> due = redis.opsForZSet().rangeByScore(EXPIRY_INDEX, Double.NEGATIVE_INFINITY, nowMillis - 1);
        return due == null ? Set.of() : due;
    }

    /** The index member of a Workspace session, or empty text for an Operator session or one without an Account id (not indexed). */
    private static String memberOf(String key, UUID workspaceId, UUID accountId) {
        return workspaceId == null || accountId == null
                ? ""
                : key.substring(KEY_PREFIX.length()) + "|" + workspaceId + "|" + accountId;
    }

    private static long deadlineMillis(Instant createdAt, Instant lastActivityAt) {
        Instant idle = lastActivityAt.plus(IDLE_LIMIT);
        Instant absolute = createdAt.plus(ABSOLUTE_LIMIT);
        return (idle.isBefore(absolute) ? idle : absolute).toEpochMilli();
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
