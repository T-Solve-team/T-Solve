package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** A Jira pull with a missing date (MSG01) or a range that is reversed or in the future (MSG39), checked before Jira. */
public class InvalidPullRangeException extends MessageCodeException {

    private InvalidPullRangeException(String code, Map<String, Object> params) {
        super(HttpStatus.BAD_REQUEST, code, params);
    }

    public static InvalidPullRangeException missing(String field) {
        return new InvalidPullRangeException("MSG01", Map.of("field_name", field));
    }

    public static InvalidPullRangeException invalid() {
        return new InvalidPullRangeException("MSG39", Map.of());
    }
}
