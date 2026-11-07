package vn.tsolve.tracker.dto;

/**
 * The Workspace's Jira service account (STORY-012): its site and the email + API token T-Solve signs in with. The
 * email and the token never reach a log line (Non-Functional 9), so {@link #toString()} leaves them out.
 */
public record JiraAccount(String siteUrl, String email, String apiToken) {

    @Override
    public String toString() {
        return "JiraAccount[siteUrl=" + siteUrl + "]";
    }
}
