package vn.tsolve.project.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class ProjectAccessDeniedException extends MessageCodeException {
    public ProjectAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "MSG08", Map.of());
    }
}
