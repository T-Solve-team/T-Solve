package vn.tsolve.search.query.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class InvalidSearchQueryException extends MessageCodeException {
    public InvalidSearchQueryException() {
        super(HttpStatus.BAD_REQUEST, "MSG06", Map.of());
    }

    public static InvalidSearchQueryException reversedDates() {
        return new InvalidSearchQueryException("MSG39");
    }

    private InvalidSearchQueryException(String code) {
        super(HttpStatus.BAD_REQUEST, code, Map.of());
    }
}
