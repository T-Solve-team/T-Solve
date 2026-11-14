package vn.tsolve.platform.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class FileTooLargeException extends MessageCodeException {

    public FileTooLargeException() {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "MSG28", Map.of());
    }
}
