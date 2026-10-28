package vn.tsolve.common.exception;

/** A well-formed request that violates a domain rule (e.g. illegal solution status transition). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
