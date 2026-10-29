package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.error.MessageCodeException;

/** The caller is not the Project Manager of the Active Project they chose (MSG08). */
public class ProjectAccessDeniedException extends MessageCodeException {

    public ProjectAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "MSG08", Map.of());
    }
}
