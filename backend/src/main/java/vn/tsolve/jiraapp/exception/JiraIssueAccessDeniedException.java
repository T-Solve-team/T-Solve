package vn.tsolve.jiraapp.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/**
 * The Jira App call may not reach this data: the site has no registered Workspace, or the user is not a member of the
 * issue's Project (403 MSG08, jira-app AC-001.2).
 */
public class JiraIssueAccessDeniedException extends MessageCodeException {
    public JiraIssueAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "MSG08", Map.of());
    }
}
