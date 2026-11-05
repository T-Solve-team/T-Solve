package vn.tsolve.triage.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** The history was filtered by a Project the caller does not manage (MSG08); the list is never widened instead. */
public class ProjectNotManagedException extends MessageCodeException {

    public ProjectNotManagedException() {
        super(HttpStatus.FORBIDDEN, "MSG08", Map.of());
    }
}
