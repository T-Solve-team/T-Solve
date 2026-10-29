package vn.tsolve.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An error the user sees as an SRS message: the HTTP status, the message code (MSGxx) and the values for its
 * template. The text itself lives in the SRS and the web app's catalog, never here.
 */
public abstract class MessageCodeException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> params;

    protected MessageCodeException(HttpStatus status, String code, Map<String, Object> params) {
        super(code + " " + params);
        this.status = status;
        this.code = code;
        this.params = Map.copyOf(params);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
