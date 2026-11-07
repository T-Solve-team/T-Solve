package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** Confirm of a file without Solutions that still has Needs Solution or Error rows (MSG_NEW_4, AC-014.1). */
public class RowsNotReadyException extends MessageCodeException {

    public RowsNotReadyException(long count) {
        super(HttpStatus.CONFLICT, "MSG_NEW_4", Map.of("count", count));
    }
}
