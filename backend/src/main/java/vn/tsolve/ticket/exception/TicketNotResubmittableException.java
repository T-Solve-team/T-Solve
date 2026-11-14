package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/**
 * A resubmission whose ticket is no longer the writer's Solution Form ticket in Changes requested (for example it
 * expired meanwhile): nothing changes (MSG06, 409, jira-app AC-012.5), as for any other refused status change.
 */
public class TicketNotResubmittableException extends MessageCodeException {
    public TicketNotResubmittableException() {
        super(HttpStatus.CONFLICT, "MSG06", Map.of());
    }
}
