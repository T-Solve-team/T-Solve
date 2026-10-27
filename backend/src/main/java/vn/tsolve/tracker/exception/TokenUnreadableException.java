package vn.tsolve.tracker.exception;

/**
 * A stored Jira token could not be decrypted (key changed or data damaged). The worker marks the event FAILED
 * with {@code TOKEN_UNREADABLE}; it never reaches an HTTP response, so it extends no common base.
 */
public class TokenUnreadableException extends RuntimeException {

    public static final String ERROR_TYPE = "TOKEN_UNREADABLE";

    public TokenUnreadableException(Throwable cause) {
        super(ERROR_TYPE, cause);
    }
}
