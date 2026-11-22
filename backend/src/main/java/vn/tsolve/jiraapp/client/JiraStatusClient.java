package vn.tsolve.jiraapp.client;

import java.util.Optional;
import vn.tsolve.tracker.dto.JiraAccount;

/** Reads a Jira status with the Workspace's app token (jira-app AC-005.7, KAN-371). */
public interface JiraStatusClient {

    /** The status category key of the status ({@code done} for Done); empty when Jira cannot tell. Never throws. */
    Optional<String> categoryOf(JiraAccount account, String statusId);
}
