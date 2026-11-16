package vn.tsolve.tracker.dto;

/**
 * The Workspace's Jira service account (STORY-012, KAN-344): its site (for issue links), the site's cloud id and the
 * scoped API token T-Solve sends as Bearer through the Atlassian gateway. The token never reaches a log line
 * (Non-Functional 9), so {@link #toString()} leaves it out.
 */
public record JiraAccount(String siteUrl, String cloudId, String apiToken) {

    @Override
    public String toString() {
        return "JiraAccount[siteUrl=" + siteUrl + "]";
    }
}
