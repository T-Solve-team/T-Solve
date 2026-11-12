package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/**
 * Jira gave no usable answer, or the connection is not configured: MSG50 (502). The reason holds only an HTTP status
 * or an error type — never a credential or issue content — because it is MSG50's {@code error_message} (AC-001.7).
 */
public class JiraUnavailableException extends MessageCodeException {

    private final String reason;

    public JiraUnavailableException(String reason) {
        super(HttpStatus.BAD_GATEWAY, "MSG50", Map.of("error_message", reason));
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
