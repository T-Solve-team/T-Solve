package vn.tsolve.workspace.dto;

import java.time.Instant;

/**
 * The T-Solve Jira App's app system token held for a Workspace (STORY-012, KAN-349): the token, the site it belongs to,
 * when it arrived and when it expires. {@link #toString()} leaves the token out (AC-012.6).
 */
public record JiraAppToken(String token, String cloudId, Instant receivedAt, Instant expiresAt) {

    @Override
    public String toString() {
        return "JiraAppToken[cloudId=" + cloudId + ", receivedAt=" + receivedAt + ", expiresAt=" + expiresAt + "]";
    }
}
