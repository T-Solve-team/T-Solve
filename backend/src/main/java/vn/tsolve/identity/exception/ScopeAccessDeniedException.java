package vn.tsolve.identity.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** A scope refusal, also thrown for an unknown id. */
public class ScopeAccessDeniedException extends MessageCodeException {
    public ScopeAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "MSG08", Map.of());
    }
}
