package vn.tsolve.ticket.service;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ReviewException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;
    private final Map<String, Object> params;

    public ReviewException(HttpStatus status, String code) {
        this(status, code, null, Map.of());
    }

    public ReviewException(HttpStatus status, String code, String field, Map<String, Object> params) {
        super(code);
        this.status = status;
        this.code = code;
        this.field = field;
        this.params = Map.copyOf(params);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
