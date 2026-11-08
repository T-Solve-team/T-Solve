package vn.tsolve.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** A field error rendered by the existing coded-exception handler, with its own HTTP status. */
public class FieldMessageCodeException extends MessageCodeException {
    public FieldMessageCodeException(HttpStatus status, String code, Map<String, Object> params, String field) {
        super(status, code, params, field);
    }
}
