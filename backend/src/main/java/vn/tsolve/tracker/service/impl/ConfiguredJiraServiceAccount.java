package vn.tsolve.tracker.service.impl;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;

/**
 * Stand-in for the Workspace's Jira service account until {@code workspace} stores it (UC-08/09); delete this class
 * then. Reads {@code JIRA_SITE_URL}, {@code JIRA_SERVICE_ACCOUNT_EMAIL} and {@code JIRA_SERVICE_ACCOUNT_TOKEN}; any of
 * them blank means no account, so no comment is posted (AC-012.8).
 */
@Service
public class ConfiguredJiraServiceAccount implements JiraServiceAccount {

    private final Optional<JiraAccount> account;

    public ConfiguredJiraServiceAccount(
            @Value("${tsolve.tracker.jira.site-url:}") String siteUrl,
            @Value("${tsolve.tracker.jira.service-account-email:}") String email,
            @Value("${tsolve.tracker.jira.service-account-token:}") String apiToken) {
        this.account = isBlank(siteUrl) || isBlank(email) || isBlank(apiToken)
                ? Optional.empty()
                : Optional.of(new JiraAccount(withoutTrailingSlash(siteUrl.strip()), email.strip(), apiToken.strip()));
    }

    @Override
    public Optional<JiraAccount> current() {
        return account;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
