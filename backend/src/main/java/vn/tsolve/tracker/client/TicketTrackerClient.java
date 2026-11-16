package vn.tsolve.tracker.client;

import java.util.List;
import java.util.Optional;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerStatus;

/**
 * Calls to the ticket tracker as the Workspace's service account. Failures are {@code JiraUnavailableException} (retry
 * may help) or {@code JiraRejectedException} (it will not); neither carries the tracker's body.
 */
public interface TicketTrackerClient {

    /** Adds a comment made of these paragraphs to the issue and returns the new comment's id. */
    String addComment(JiraAccount account, String issueKey, List<String> paragraphs);

    /**
     * The id of the newest comment on the issue written by this account whose text starts with {@code textPrefix},
     * or empty.
     */
    Optional<String> findComment(JiraAccount account, String issueKey, String textPrefix);

    /** Returns the canonical project key, or empty when Jira answers 404. */
    Optional<String> findProject(JiraAccount account, String projectKey);

    /** Returns statuses across the project's issue types in encounter order, including duplicates. */
    List<TrackerStatus> projectStatuses(JiraAccount account, String projectKey);
}
