package vn.tsolve.identity.jiralink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Single-use Jira link requests (AC-001.69, AC-001.38, AC-001.72): a hash under {@code tsolve:jiralink:<SHA-256 hex of
 * the id>} for 10 minutes. The id is 32 random bytes in base64url; it is never stored, logged or part of a key.
 */
@Service
public class JiraLinkRequestService {

    static final String KEY_PREFIX = "tsolve:jiralink:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{43}");

    private static final String WORKSPACE_ID = "workspace_id";
    private static final String TRACKER_ACCOUNT_ID = "tracker_account_id";
    private static final String JIRA_USER_NAME = "jira_user_name";
    private static final String SITE_NAME = "site_name";

    /** Reads and deletes the key in one step, so two parallel confirms cannot both get the request. */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> TAKE = new DefaultRedisScript<>(
            "local v = redis.call('HGETALL', KEYS[1]); redis.call('DEL', KEYS[1]); return v", List.class);

    /** Writes the fields and the time to live in one step, so a key never exists without its expiry. */
    private static final DefaultRedisScript<Long> STORE = new DefaultRedisScript<>(
            "redis.call('HSET', KEYS[1], 'workspace_id', ARGV[1], 'tracker_account_id', ARGV[2],"
                    + " 'jira_user_name', ARGV[3], 'site_name', ARGV[4]);"
                    + " redis.call('EXPIRE', KEYS[1], ARGV[5]); return 1",
            Long.class);

    private final StringRedisTemplate redis;
    private final WorkspaceLookup workspaces;
    private final String siteOriginTemplate;
    private final SecureRandom random = new SecureRandom();

    public JiraLinkRequestService(
            StringRedisTemplate redis,
            WorkspaceLookup workspaces,
            @Value("${TSOLVE_SITE_ORIGIN_TEMPLATE:}") String siteOriginTemplate) {
        this.redis = redis;
        this.workspaces = workspaces;
        this.siteOriginTemplate = siteOriginTemplate;
    }

    /** Stores a request and returns the link {@code <site origin>/jira-link?request=<id>} of the Workspace's site. */
    public String create(UUID workspaceId, String trackerAccountId, String jiraUserName, String siteName) {
        if (trackerAccountId == null || trackerAccountId.isBlank() || trackerAccountId.length() > 128) {
            throw new IllegalArgumentException("The tracker account id must be 1 to 128 characters");
        }
        WorkspaceRef workspace = workspaces
                .findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Workspace " + workspaceId));
        if (!siteOriginTemplate.contains("{slug}")) {
            throw new IllegalStateException("TSOLVE_SITE_ORIGIN_TEMPLATE must be set and contain {slug}");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.execute(
                STORE,
                List.of(keyOf(id)),
                workspaceId.toString(),
                trackerAccountId,
                jiraUserName,
                siteName,
                Long.toString(TTL.toSeconds()));
        return siteOriginTemplate.replace("{slug}", workspace.slug()) + "/jira-link?request=" + id;
    }

    /** Reads the request without using it up; empty when it is unknown, expired, malformed or of another Workspace. */
    public Optional<JiraLinkRequest> peek(String requestId, UUID workspaceId) {
        if (requestId == null || !ID.matcher(requestId).matches()) {
            return Optional.empty();
        }
        return toRequest(redis.opsForHash().entries(keyOf(requestId)), workspaceId);
    }

    /** Reads and deletes the request; a request of another Workspace is deleted too and gives nothing. */
    @SuppressWarnings("unchecked")
    public Optional<JiraLinkRequest> consume(String requestId, UUID workspaceId) {
        if (requestId == null || !ID.matcher(requestId).matches()) {
            return Optional.empty();
        }
        List<Object> flat = redis.execute(TAKE, List.of(keyOf(requestId)));
        if (flat == null || flat.isEmpty()) {
            return Optional.empty();
        }
        Map<Object, Object> fields = new HashMap<>();
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            fields.put(flat.get(i), flat.get(i + 1));
        }
        return toRequest(fields, workspaceId);
    }

    private static Optional<JiraLinkRequest> toRequest(Map<Object, Object> fields, UUID workspaceId) {
        if (workspaceId == null || !workspaceId.toString().equals(fields.get(WORKSPACE_ID))) {
            return Optional.empty();
        }
        return Optional.of(new JiraLinkRequest(
                workspaceId, (String) fields.get(TRACKER_ACCOUNT_ID), (String) fields.get(JIRA_USER_NAME), (String)
                        fields.get(SITE_NAME)));
    }

    private static String keyOf(String id) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
