package vn.tsolve.jiraapp.service;

import java.util.Optional;
import java.util.UUID;

/** The Workspace a Jira site belongs to, from the verified FIT cloudId only — never a value the caller chose. */
public interface JiraWorkspaceLookup {

    /** The registered Workspace of the site, or empty when the site is not registered. */
    Optional<UUID> workspaceOf(String cloudId);
}
