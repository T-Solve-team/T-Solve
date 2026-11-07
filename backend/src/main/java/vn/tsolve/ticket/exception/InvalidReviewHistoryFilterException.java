package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** A Review History filter the server refuses: MSG39 for a bad date range, MSG06 for any other value. */
public class InvalidReviewHistoryFilterException extends MessageCodeException {

    public InvalidReviewHistoryFilterException(String code) {
        super(HttpStatus.BAD_REQUEST, code, Map.of());
    }
}
