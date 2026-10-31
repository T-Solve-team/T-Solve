package vn.tsolve.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An error the user sees as an SRS message: the HTTP status, the message code (MSGxx) and the values for its
 * template. The text itself lives in the SRS and the web app's catalog, never here.
 */
public abstract class MessageCodeException extends CodedException {

    private final HttpStatus status;

    protected MessageCodeException(HttpStatus status, String code, Map<String, Object> params) {
        super(code, params, null);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
