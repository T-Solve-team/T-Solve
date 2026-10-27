package vn.tsolve.common.security;

/**
 * Who a Jira App call comes from, read from its FIT.
 *
 * @param cloudId the Jira site's cloudId ({@code context.cloudId})
 * @param workspaceId the Workspace of that site, or {@link WorkspaceContext#NONE} when no Workspace registered it
 * @param accountId the Atlassian user behind a UI call ({@code principal}); {@code null} for product events
 * @param apiBaseUrl where calls back to Jira go ({@code app.apiBaseUrl}); {@code null} when absent
 */
public record JiraAppPrincipal(String cloudId, long workspaceId, String accountId, String apiBaseUrl) {

    public boolean isRegisteredSite() {
        return workspaceId != WorkspaceContext.NONE;
    }
}
