package vn.tsolve.jiraapp.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** The Jira account of the FIT is not linked to an active T-Solve user: 403 with MSG62 (AC-010.11). */
public class JiraAskerNotLinkedException extends MessageCodeException {

    public JiraAskerNotLinkedException() {
        super(HttpStatus.FORBIDDEN, "MSG62", Map.of());
    }
}
