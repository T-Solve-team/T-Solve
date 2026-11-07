package vn.tsolve.search.list.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class InvalidListQueryException extends MessageCodeException {
    private final String field;

    public InvalidListQueryException() {
        super(HttpStatus.BAD_REQUEST, "MSG06", Map.of());
        field = null;
    }

    public static InvalidListQueryException keywordTooLong() {
        return new InvalidListQueryException("keyword", 100);
    }

    private InvalidListQueryException(String field, int max) {
        super(HttpStatus.BAD_REQUEST, "MSG02", Map.of("field_name", field, "max_length", max));
        this.field = field;
    }

    @Override
    public String getField() {
        return field;
    }
}
