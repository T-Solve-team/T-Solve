package vn.tsolve.jiraapp.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;

/**
 * Stand-in until platform registers Jira sites per Workspace: {@code TSOLVE_STUB_JIRA_SITES=<cloudId>=<Workspace
 * uuid>;…}. A site that is not listed has no Workspace, so its calls are refused (user decision, 2026-11-12). Delete it
 * when platform's site registry arrives.
 */
@Service
public class ConfigJiraWorkspaceLookup implements JiraWorkspaceLookup {

    private static final String SETTING = "TSOLVE_STUB_JIRA_SITES";

    private final Map<String, UUID> sites;

    public ConfigJiraWorkspaceLookup(@Value("${TSOLVE_STUB_JIRA_SITES:}") String config) {
        this.sites = parse(config);
    }

    @Override
    public Optional<UUID> workspaceOf(String cloudId) {
        return cloudId == null ? Optional.empty() : Optional.ofNullable(sites.get(cloudId));
    }

    // A malformed entry stops the start-up: a silent "not registered" would look like a Jira problem.
    private static Map<String, UUID> parse(String config) {
        Map<String, UUID> sites = new HashMap<>();
        if (config == null) {
            return sites;
        }
        String[] entries = config.split(";");
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index].trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf('=');
            String cloudId = separator < 0 ? "" : entry.substring(0, separator).trim();
            String workspace =
                    separator < 0 ? "" : entry.substring(separator + 1).trim();
            if (cloudId.isEmpty() || workspace.isEmpty()) {
                throw new IllegalArgumentException(SETTING + ": entry " + (index + 1) + " is not <cloudId>=<uuid>");
            }
            try {
                sites.put(cloudId, UUID.fromString(workspace));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(SETTING + ": entry " + (index + 1) + " has no Workspace uuid");
            }
        }
        return Map.copyOf(sites);
    }
}
