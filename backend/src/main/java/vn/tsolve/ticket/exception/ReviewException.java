package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.ApiException;

public class ReviewException extends ApiException {
    public ReviewException(HttpStatus status, String code) {
        super(status, code);
    }

    public ReviewException(HttpStatus status, String code, String field, Map<String, Object> params) {
        super(status, code, field, params);
    }
}
