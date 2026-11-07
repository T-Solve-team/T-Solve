package vn.tsolve.ai.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class AiRewriteUnavailableException extends MessageCodeException {

    private static final String CODE = "MSG68";

    public AiRewriteUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, CODE, Map.of());
    }
}
