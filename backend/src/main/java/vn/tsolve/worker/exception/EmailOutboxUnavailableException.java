package vn.tsolve.worker.exception;

public final class EmailOutboxUnavailableException extends EmailOutboxException {
    public EmailOutboxUnavailableException() {
        super("Email outbox unavailable");
    }
}
