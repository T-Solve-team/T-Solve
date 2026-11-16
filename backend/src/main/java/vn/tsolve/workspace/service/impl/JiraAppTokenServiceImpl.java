package vn.tsolve.workspace.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.workspace.dto.JiraAppToken;
import vn.tsolve.workspace.exception.JiraAppSiteNotRegisteredException;
import vn.tsolve.workspace.exception.JiraAppTokenRejectedException;
import vn.tsolve.workspace.repository.JiraAppTokenStore;
import vn.tsolve.workspace.service.JiraAppTokenService;

@Service
public class JiraAppTokenServiceImpl implements JiraAppTokenService {

    private static final Logger log = LoggerFactory.getLogger(JiraAppTokenServiceImpl.class);
    private static final String GATEWAY = "https://api.atlassian.com/ex/jira/";
    private static final Pattern CLOUD_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final JiraWorkspaceLookup workspaces;
    private final TenantScope scope;
    private final JiraAppTokenStore store;
    private final ObjectMapper json;
    private final Clock clock;

    public JiraAppTokenServiceImpl(
            JiraWorkspaceLookup workspaces,
            TenantScope scope,
            JiraAppTokenStore store,
            ObjectMapper json,
            Clock clock) {
        this.workspaces = workspaces;
        this.scope = scope;
        this.store = store;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void receive(String cloudId, String apiBaseUrl, String token) {
        if (token == null || token.isBlank()) {
            throw new JiraAppTokenRejectedException();
        }
        // AC-012.4: the gateway of exactly this site, so a stored token can never send calls anywhere else
        if (cloudId == null || !CLOUD_ID.matcher(cloudId).matches() || !(GATEWAY + cloudId).equals(apiBaseUrl)) {
            throw new JiraAppTokenRejectedException();
        }
        Instant now = clock.instant();
        Instant expiresAt = expiry(token).filter(at -> at.isAfter(now)).orElseThrow(JiraAppTokenRejectedException::new);
        UUID workspaceId = workspaces.workspaceOf(cloudId).orElseThrow(JiraAppSiteNotRegisteredException::new);
        scope.runIn(workspaceId, () -> {
            store.hold(workspaceId, new JiraAppToken(token.strip(), cloudId, now, expiresAt));
            return null;
        });
        log.info("Jira App token received for Workspace {}", workspaceId);
    }

    @Override
    public Optional<JiraAppToken> current(UUID workspaceId) {
        return store.current(workspaceId);
    }

    /** The {@code exp} claim of the token's payload; the token is opaque otherwise and its signature is not ours. */
    private Optional<Instant> expiry(String token) {
        String[] parts = token.strip().split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            JsonNode payload =
                    json.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            JsonNode exp = payload.path("exp");
            return exp.canConvertToLong() ? Optional.of(Instant.ofEpochSecond(exp.asLong())) : Optional.empty();
        } catch (IOException | IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }
}
