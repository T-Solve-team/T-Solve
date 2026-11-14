package vn.tsolve.tracker.client;

import java.util.List;
import java.util.Optional;
import vn.tsolve.tracker.dto.JiraAccount;

/**
 * Calls to the ticket tracker as the Workspace's service account. Failures are {@code JiraUnavailableException} (retry
 * may help) or {@code JiraRejectedException} (it will not); neither carries the tracker's body.
 */
public interface TicketTrackerClient {

    /** Checks that the account authenticates and has Browse projects, Create Issues and Modify Reporter. */
    void checkServiceAccount(JiraAccount account);

    /** Adds a comment made of these paragraphs to the issue and returns the new comment's id. */
    String addComment(JiraAccount account, String issueKey, List<String> paragraphs);

    /**
     * The id of the newest comment on the issue written by this account whose text starts with {@code textPrefix},
     * or empty.
     */
    Optional<String> findComment(JiraAccount account, String issueKey, String textPrefix);
}
