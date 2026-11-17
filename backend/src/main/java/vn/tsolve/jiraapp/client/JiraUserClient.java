package vn.tsolve.jiraapp.client;

import java.util.Optional;
import vn.tsolve.tracker.dto.JiraAccount;

/** Reads a Jira user as the T-Solve Jira App (jira-app AC-014.6). */
public interface JiraUserClient {

    /** The user's display name; empty when Jira cannot be reached, refuses, or returns none. Never throws. */
    Optional<String> displayName(JiraAccount account, String accountId);
}
