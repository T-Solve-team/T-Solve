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

    public static InvalidSearchQueryException requiredQuery() {
        return new InvalidSearchQueryException("MSG01", Map.of("field_name", "q"));
    }

    public static InvalidSearchQueryException queryTooLong() {
        return new InvalidSearchQueryException("MSG02", Map.of("field_name", "q", "max_length", 200));
    }

    @Override
    public String getField() {
        return "MSG01".equals(getCode()) || "MSG02".equals(getCode()) ? "q" : null;
    }

    private InvalidSearchQueryException(String code) {
        this(code, Map.of());
    }

    private InvalidSearchQueryException(String code, Map<String, Object> params) {
        super(HttpStatus.BAD_REQUEST, code, params);
    }
}
