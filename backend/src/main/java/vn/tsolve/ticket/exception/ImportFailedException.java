package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/**
 * The whole import failed and nothing was imported (MSG33): 409 when a Ticket ID was taken since the preview, 500
 * when saving failed.
 */
public class ImportFailedException extends MessageCodeException {

    public ImportFailedException(HttpStatus status) {
        super(status, "MSG33", Map.of());
    }
}
