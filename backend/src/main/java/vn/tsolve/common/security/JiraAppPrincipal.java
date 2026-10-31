package vn.tsolve.common.security;

/**
 * Who a Jira App call comes from, read from its FIT. Which tenant database the site belongs to is discovery's
 * job, not this principal's.
 *
 * @param cloudId the Jira site's cloudId ({@code context.cloudId})
 * @param accountId the Atlassian user behind a UI call ({@code principal}); {@code null} for product events
 * @param apiBaseUrl where calls back to Jira go ({@code app.apiBaseUrl}); {@code null} when absent
 */
public record JiraAppPrincipal(String cloudId, String accountId, String apiBaseUrl) {}
