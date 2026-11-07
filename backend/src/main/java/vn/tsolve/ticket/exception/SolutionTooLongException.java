package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** The five Solution sections together are longer than the limit (MSG02 for the Solution, AC-013.2). */
public class SolutionTooLongException extends MessageCodeException {

    public SolutionTooLongException(int maxLength) {
        super(HttpStatus.BAD_REQUEST, "MSG02", Map.of("field_name", "Solution", "max_length", maxLength));
    }
}
