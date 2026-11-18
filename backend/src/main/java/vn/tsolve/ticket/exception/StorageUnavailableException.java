package vn.tsolve.ticket.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** Evidence storage is not configured or could not store the request's files (AC-006.21). */
public class StorageUnavailableException extends MessageCodeException {

    public StorageUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MSG06", Map.of());
    }
}
