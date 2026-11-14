package vn.tsolve.worker.exception;

public final class InvalidOutboundEmailException extends EmailOutboxException {
    public InvalidOutboundEmailException() {
        super("Invalid outbound email");
    }
}
