package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class ReviewSourceNotEligibleException extends MessageCodeException {

    public ReviewSourceNotEligibleException() {
        super(HttpStatus.BAD_REQUEST, "MSG18", Map.of());
    }
}
