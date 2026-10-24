package vn.tsolve.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {
    public BusinessRuleException(String code) {
        super(HttpStatus.CONFLICT, code);
    }
}
