package vn.tsolve.common.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Base of the exceptions the global handler maps to a status. It carries the SRS message code the web app shows,
 * the values for its template and, when the message belongs below a form field, that field's name.
 */
public abstract class CodedException extends RuntimeException {

    private final String code;
    private final Map<String, Object> params;
    private final String field;

    /** No message code: the message goes to {@code detail} only. Kept for callers that predate the codes. */
    protected CodedException(String message) {
        super(message);
        this.code = null;
        this.params = Map.of();
        this.field = null;
    }

    protected CodedException(String code, Map<String, ?> params, String field) {
        super(code);
        this.code = Objects.requireNonNull(code, "code");
        this.params = Map.copyOf(params);
        this.field = field;
    }

    /** The SRS message code (MSGxx), or {@code null} for a message-only exception. */
    public String getCode() {
        return code;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    /** The request field the message belongs to, or {@code null}. */
    public String getField() {
        return field;
    }
}
