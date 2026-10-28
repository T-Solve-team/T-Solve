package vn.tsolve.common.exception;

import java.util.Map;

/** A well-formed request that violates a domain rule (e.g. illegal solution status transition); answered with 409. */
public class BusinessRuleException extends CodedException {

    public BusinessRuleException(String message) {
        super(message);
    }

    public BusinessRuleException(String code, Map<String, ?> params) {
        super(code, params, null);
    }

    /** The message is shown below {@code field} of the request form (e.g. MSG11 on {@code email}). */
    public BusinessRuleException(String code, Map<String, ?> params, String field) {
        super(code, params, field);
    }
}
