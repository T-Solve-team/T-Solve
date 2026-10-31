package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** The Project was removed or its Department deleted before Confirm; nothing is imported (MSG40). */
public class ProjectNotActiveException extends MessageCodeException {

    public ProjectNotActiveException() {
        super(HttpStatus.CONFLICT, "MSG40", Map.of());
    }
}
