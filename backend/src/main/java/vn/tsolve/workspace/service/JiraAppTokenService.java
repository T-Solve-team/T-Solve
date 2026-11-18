package vn.tsolve.workspace.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.workspace.dto.JiraAppToken;

/** The Jira App's app token per Workspace (STORY-014, KAN-349). */
public interface JiraAppTokenService {

    /**
     * Holds the token the Jira App sent for the site of a verified FIT (AC-014.1..AC-014.5).
     *
     * @param cloudId the FIT's {@code context.cloudId}
     * @param apiBaseUrl the FIT's {@code app.apiBaseUrl}
     * @param token the {@code x-forge-oauth-system} header
     */
    void receive(String cloudId, String apiBaseUrl, String token);

    /** The held, unexpired token of the Workspace, if any. */
    Optional<JiraAppToken> current(UUID workspaceId);
}
