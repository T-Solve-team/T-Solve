package vn.tsolve.common.exception;

import java.util.Map;

/** Answered with 404. */
public class NotFoundException extends CodedException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String code, Map<String, ?> params) {
        super(code, params, null);
    }
}
