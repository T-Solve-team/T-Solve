package vn.tsolve.identity.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.tsolve.common.exception.MessageCodeException;

public final class UserListException extends MessageCodeException {
    private final String field;

    public UserListException(HttpStatus status, String code) {
        this(status, code, Map.of(), null);
    }

    public UserListException(HttpStatus status, String code, Map<String, Object> params, String field) {
        super(status, code, params);
        this.field = field;
    }

    @Override
    public String getField() {
        return field;
    }
}
