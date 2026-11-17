package vn.tsolve.jiraapp.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** The Jira site of the call belongs to no Workspace (403 MSG124, jira-app AC-010.11). */
public class JiraSiteNotConnectedException extends MessageCodeException {
    public JiraSiteNotConnectedException() {
        super(HttpStatus.FORBIDDEN, "MSG124", Map.of("tracker_name", "Jira"));
    }
}
