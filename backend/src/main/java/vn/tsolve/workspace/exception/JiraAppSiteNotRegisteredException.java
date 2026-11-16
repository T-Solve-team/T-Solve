package vn.tsolve.workspace.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** The Jira site of the token call belongs to no Workspace (403 MSG08, AC-012.2). */
public class JiraAppSiteNotRegisteredException extends MessageCodeException {
    public JiraAppSiteNotRegisteredException() {
        super(HttpStatus.FORBIDDEN, "MSG08", Map.of());
    }
}
