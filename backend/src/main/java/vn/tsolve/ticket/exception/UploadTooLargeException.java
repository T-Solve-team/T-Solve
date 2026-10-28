package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.error.MessageCodeException;

/** The uploaded file is over the import limit (MSG28). */
public class UploadTooLargeException extends MessageCodeException {

    public UploadTooLargeException() {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "MSG28", Map.of());
    }
}
