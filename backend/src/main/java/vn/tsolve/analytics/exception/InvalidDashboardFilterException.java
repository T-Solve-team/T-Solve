package vn.tsolve.analytics.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public class InvalidDashboardFilterException extends MessageCodeException {
    public InvalidDashboardFilterException(String code) {
        super(HttpStatus.BAD_REQUEST, code, Map.of());
    }
}
