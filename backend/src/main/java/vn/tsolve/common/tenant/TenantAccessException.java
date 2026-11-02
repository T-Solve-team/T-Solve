package vn.tsolve.common.tenant;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

/** Deliberately carries no connection details, credentials or query values. */
public final class TenantAccessException extends MessageCodeException {
    public TenantAccessException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "MSG06", Map.of());
    }
}
