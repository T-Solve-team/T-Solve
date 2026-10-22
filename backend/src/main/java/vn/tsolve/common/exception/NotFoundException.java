package vn.tsolve.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, code);
    }
}
