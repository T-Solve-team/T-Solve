package vn.tsolve.workspace.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** The Jira App's token call cannot be used: no token, an unreadable or past expiry, or a foreign API base (400 MSG06). */
public class JiraAppTokenRejectedException extends MessageCodeException {
    public JiraAppTokenRejectedException() {
        super(HttpStatus.BAD_REQUEST, "MSG06", Map.of());
    }
}
