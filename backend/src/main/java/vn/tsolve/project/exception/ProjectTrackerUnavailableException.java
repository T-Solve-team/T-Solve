package vn.tsolve.project.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class ProjectTrackerUnavailableException extends MessageCodeException {
    public ProjectTrackerUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MSG06", Map.of());
    }
}
