package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** Confirm on a preview with no Valid row left (MSG30 with the number of Error rows). */
public class NothingToImportException extends MessageCodeException {

    public NothingToImportException(long errorCount) {
        super(HttpStatus.CONFLICT, "MSG30", Map.of("count", errorCount));
    }
}
