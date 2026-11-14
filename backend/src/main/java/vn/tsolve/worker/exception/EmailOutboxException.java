package vn.tsolve.worker.exception;

public class EmailOutboxException extends RuntimeException {
    protected EmailOutboxException(String message) {
        super(message);
    }
}
