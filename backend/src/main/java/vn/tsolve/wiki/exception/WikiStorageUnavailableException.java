package vn.tsolve.wiki.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** File storage is not configured, so a Wiki file cannot be stored or read (BUG-296). */
public class WikiStorageUnavailableException extends MessageCodeException {

    public WikiStorageUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MSG06", Map.of());
    }
}
