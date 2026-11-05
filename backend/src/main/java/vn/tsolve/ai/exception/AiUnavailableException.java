package vn.tsolve.ai.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.ProblemDetails;

/**
 * The AI server did not give a usable answer: timeout, connection failure, error status, busy, or an answer of the
 * wrong shape. Callers catch it and fall back (GBR-12); uncaught, it answers 503 with MSG06. It never carries a
 * request or response body, so it cannot leak content into a log.
 */
public class AiUnavailableException extends MessageCodeException {

    public AiUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetails.UNEXPECTED_ERROR, Map.of());
    }
}
