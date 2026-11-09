package vn.tsolve.jiraapp.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.account.service.AccountLookupService;
import vn.tsolve.jiraapp.service.JiraAskerLookup;

/**
 * Stand-in until identity links Jira accounts: {@code TSOLVE_STUB_JIRA_ASKERS=<Jira accountId>=<Account uuid>;…}.
 * The site is not checked — the stub serves one development site. Delete it when identity's lookup arrives.
 */
@Service
public class ConfigJiraAskerLookup implements JiraAskerLookup {

    private static final String SETTING = "TSOLVE_STUB_JIRA_ASKERS";

    private final Map<String, UUID> links;
    private final AccountLookupService accounts;

    public ConfigJiraAskerLookup(@Value("${TSOLVE_STUB_JIRA_ASKERS:}") String config, AccountLookupService accounts) {
        this.links = parse(config);
        this.accounts = accounts;
    }

    @Override
    public Optional<TSolveUser> find(String cloudId, String accountId) {
        UUID account = links.get(accountId);
        return account == null ? Optional.empty() : accounts.findActive(account);
    }

    // A malformed link stops the start-up: a silent "not linked" would look like an identity problem.
    private static Map<String, UUID> parse(String config) {
        Map<String, UUID> links = new HashMap<>();
        if (config == null) {
            return links;
        }
        String[] entries = config.split(";");
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index].trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf('=');
            String jiraAccount =
                    separator < 0 ? "" : entry.substring(0, separator).trim();
            String account = separator < 0 ? "" : entry.substring(separator + 1).trim();
            if (jiraAccount.isEmpty() || account.isEmpty()) {
                throw new IllegalArgumentException(SETTING + ": entry " + (index + 1) + " is not <accountId>=<uuid>");
            }
            try {
                links.put(jiraAccount, UUID.fromString(account));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(SETTING + ": entry " + (index + 1) + " has no Account uuid");
            }
        }
        return Map.copyOf(links);
    }
}
