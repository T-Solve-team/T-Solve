package vn.tsolve.jiraapp.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/**
 * A link request could not be made: Jira, the app token or the link request store is unavailable, or Jira gave no
 * display name (503 MSG06, jira-app AC-014.9).
 */
public class JiraAccountLinkUnavailableException extends MessageCodeException {
    public JiraAccountLinkUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MSG06", Map.of());
    }
}
